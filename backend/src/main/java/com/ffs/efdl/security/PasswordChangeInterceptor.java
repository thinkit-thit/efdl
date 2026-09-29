package com.ffs.efdl.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Après une réinitialisation par un administrateur, seul le changement de mot de passe est permis
 * (contrôle côté serveur, pas seulement dans l'interface). Enregistré hors {@code /api/auth/**}.
 */
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    public static final String SESSION_ATTRIBUTE = "efdl.mustChangePassword";

    public static boolean isRequired(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_ATTRIBUTE));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (isRequired(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous devez changer votre mot de passe");
        }
        return true;
    }
}
