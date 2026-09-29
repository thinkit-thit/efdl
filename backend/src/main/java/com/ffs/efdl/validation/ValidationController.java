package com.ffs.efdl.validation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ffs.efdl.model.School;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.RoutePath;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.TenantResolver;
import com.ffs.efdl.validation.ValidationService.Detail;
import com.ffs.efdl.validation.ValidationService.Row;

import jakarta.servlet.http.HttpServletRequest;

/**
 * « Validation Paiement » (admin/project/validationPaiement) : factures qui attendent l'utilisateur connecté,
 * fiche avec la note et la check-list du validateur, décisions VALIDER / REJETER.
 */
@RestController
@RequestMapping("/api/validations")
public class ValidationController {

    private final ValidationService service;
    private final TenantResolver tenantResolver;

    public ValidationController(ValidationService service, TenantResolver tenantResolver) {
        this.service = service;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping
    @RoutePath("/admin/project/validationPaiements")
    public List<Row> list(HttpServletRequest request) {
        return service.pendingFor(school(request), user().externalId());
    }

    @GetMapping("/{id}")
    @RoutePath("/admin/project/validationPaiement/edit/{id}")
    public Detail detail(@PathVariable String id, HttpServletRequest request) {
        return service.detail(school(request), id, user().externalId());
    }

    @PostMapping("/{id}/validate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/project/validationPaiement/edit/{id}")
    public void validate(@PathVariable String id, HttpServletRequest request) {
        service.validate(school(request), id, user().externalId());
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RoutePath("/admin/project/validationPaiement/edit/{id}")
    public void reject(@PathVariable String id, HttpServletRequest request) {
        service.reject(school(request), id, user().externalId());
    }

    private static EfdlUserPrincipal user() {
        return RoutePathInterceptor.currentUser();
    }

    private String school(HttpServletRequest request) {
        return tenantResolver.resolve(request).map(School::getExternalId).orElse(user().schoolExternalId());
    }
}
