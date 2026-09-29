package com.ffs.efdl.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ffs.efdl.dto.ApiDtos.CurrentUserDto;
import com.ffs.efdl.dto.ApiDtos.LoginRequest;
import com.ffs.efdl.dto.ApiDtos.PasswordChangeRequest;
import com.ffs.efdl.dto.ApiDtos.PasswordResetConfirm;
import com.ffs.efdl.dto.ApiDtos.PasswordResetRequest;
import com.ffs.efdl.dto.ApiDtos.SchoolDto;
import com.ffs.efdl.model.School;
import com.ffs.efdl.model.User;
import com.ffs.efdl.repository.SchoolRepository;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.ImportPermission;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.PasswordChangeInterceptor;
import com.ffs.efdl.security.TenantResolver;
import com.ffs.efdl.service.LoginAttemptLimiter;
import com.ffs.efdl.service.LoginHistoryService;
import com.ffs.efdl.service.LoginService;
import com.ffs.efdl.service.PasswordResetService;
import com.ffs.efdl.service.PasswordService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/** Connexion (/login), déconnexion (/logout) et changement de mot de passe (user_credential). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final LoginAttemptLimiter limiter;
    private final TenantResolver tenantResolver;
    private final SchoolRepository schoolRepository;
    private final SecurityContextRepository securityContextRepository;
    private final PasswordService passwordService;
    private final PasswordResetService passwordResetService;

    private final ImportPermission importPermission;
    private final LoginHistoryService loginHistory;

    public AuthController(LoginService loginService, LoginAttemptLimiter limiter, TenantResolver tenantResolver,
                          SchoolRepository schoolRepository, SecurityContextRepository securityContextRepository,
                          PasswordService passwordService, PasswordResetService passwordResetService,
                          ImportPermission importPermission, LoginHistoryService loginHistory) {
        this.loginHistory = loginHistory;
        this.importPermission = importPermission;
        this.passwordService = passwordService;
        this.passwordResetService = passwordResetService;
        this.loginService = loginService;
        this.limiter = limiter;
        this.tenantResolver = tenantResolver;
        this.schoolRepository = schoolRepository;
        this.securityContextRepository = securityContextRepository;
    }

    /** Initialise le cookie XSRF-TOKEN (à appeler avant le login). */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName());
    }

    @PostMapping("/login")
    public CurrentUserDto login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String key = body.identifier() + "|" + request.getRemoteAddr();
        if (limiter.isBlocked(key)) {
            throw new TooManyAttemptsException();
        }
        School school = tenantResolver.resolve(request).orElse(null);
        User user = loginService.authenticate(body.identifier(), body.password(),
                school == null ? null : school.getExternalId()).orElse(null);
        if (user == null) {
            limiter.recordFailure(key);
            throw new BadCredentialsException("Login et Mot de passe incorrect");
        }
        limiter.reset(key);
        // Historique des connexions (date de dernière connexion de la liste des utilisateurs).
        loginHistory.recordLogin(user, school == null ? user.getSchoolExternalId() : school.getExternalId(), request);

        EfdlUserPrincipal principal = EfdlUserPrincipal.of(user);
        request.getSession(true);
        request.changeSessionId(); // protection contre la fixation de session
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        boolean mustChange = passwordService.mustChange(user.getExternalId());
        if (mustChange) {
            request.getSession().setAttribute(PasswordChangeInterceptor.SESSION_ATTRIBUTE, Boolean.TRUE);
        }
        return CurrentUserDto.of(principal, SchoolDto.of(school), mustChange,
                importPermission.canImport(principal, school == null ? principal.schoolExternalId() : school.getExternalId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserDto me(HttpServletRequest request) {
        EfdlUserPrincipal principal = RoutePathInterceptor.currentUser();
        School school = tenantResolver.resolve(request)
                .or(() -> schoolRepository.findFirstByExternalId(principal.schoolExternalId()))
                .orElse(null);
        return CurrentUserDto.of(principal, SchoolDto.of(school), PasswordChangeInterceptor.isRequired(request),
                importPermission.canImport(principal, school == null ? principal.schoolExternalId() : school.getExternalId()));
    }

    /** Changement de son propre mot de passe (obligatoire après une réinitialisation par un administrateur). */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest body, HttpServletRequest request) {
        EfdlUserPrincipal principal = RoutePathInterceptor.currentUser();
        String key = "password|" + principal.externalId();
        if (limiter.isBlocked(key)) {
            throw new TooManyAttemptsException();
        }
        try {
            passwordService.changeOwn(principal, body.currentPassword(), body.newPassword());
        } catch (RuntimeException e) {
            limiter.recordFailure(key);
            throw e;
        }
        limiter.reset(key);
        request.getSession().removeAttribute(PasswordChangeInterceptor.SESSION_ATTRIBUTE);
        return ResponseEntity.noContent().build();
    }

    /**
     * Mot de passe oublié, étape 1 (/forgotPwd) : code envoyé par e-mail si le compte existe.
     * Réponse identique dans tous les cas ; demandes limitées par adresse IP.
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        String key = "reset|" + request.getRemoteAddr();
        if (limiter.isBlocked(key)) throw new TooManyAttemptsException();
        limiter.recordFailure(key); // chaque demande compte : 5 demandes par quart d'heure
        School school = tenantResolver.resolve(request).orElse(null);
        passwordResetService.request(body.identifier(), school == null ? null : school.getExternalId());
        return ResponseEntity.accepted().build();
    }

    /** Mot de passe oublié, étape 2 : code reçu et nouveau mot de passe. */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirm body, HttpServletRequest request) {
        String key = "reset-confirm|" + request.getRemoteAddr();
        if (limiter.isBlocked(key)) throw new TooManyAttemptsException();
        School school = tenantResolver.resolve(request).orElse(null);
        try {
            passwordResetService.confirm(body.identifier(), body.code(), body.newPassword(),
                    school == null ? null : school.getExternalId());
        } catch (RuntimeException e) {
            limiter.recordFailure(key);
            throw e;
        }
        limiter.reset(key);
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    static class TooManyAttemptsException extends RuntimeException {
        TooManyAttemptsException() {
            super("Trop de tentatives, réessayez plus tard");
        }
    }
}
