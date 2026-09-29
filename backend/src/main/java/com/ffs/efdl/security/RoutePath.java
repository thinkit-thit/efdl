package com.ffs.efdl.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Route de l'écran servi par cet endpoint (ex. {@code "/admin/project/programme/edit/{id}"}).
 * <p>
 * L'autorisation applique les règles d'accès sur cette route
 * (AdminFilter, SuperAdminFilter, UserGroup.allowedPaths) : les groupes et regex existants
 * restent donc valables sans modification des données.
 * Les variables {@code {x}} sont remplacées par les valeurs de l'URL de l'API.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RoutePath {
    String value();
}
