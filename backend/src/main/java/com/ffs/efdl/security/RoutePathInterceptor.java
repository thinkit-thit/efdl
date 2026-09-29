package com.ffs.efdl.security;

import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Contrôle d'accès des endpoints annotés {@link RoutePath} (côté backend).
 */
@Component
public class RoutePathInterceptor implements HandlerInterceptor {

    private final PermissionService permissionService;

    public RoutePathInterceptor(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RoutePath routePath = method.getMethodAnnotation(RoutePath.class);
        if (routePath == null) {
            routePath = method.getBeanType().getAnnotation(RoutePath.class);
        }
        if (routePath == null) {
            return true;
        }
        String path = expand(routePath.value(), request);
        if (!permissionService.canAccessRoute(currentUser(), path)) {
            throw new AccessDeniedException("Accès refusé");
        }
        return true;
    }

    public static EfdlUserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof EfdlUserPrincipal p ? p : null;
    }

    @SuppressWarnings("unchecked")
    private static String expand(String template, HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String path = template;
        if (vars instanceof Map<?, ?> map) {
            for (Map.Entry<String, String> e : ((Map<String, String>) map).entrySet()) {
                path = path.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return path;
    }
}
