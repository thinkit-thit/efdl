package com.ffs.efdl.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserType;

/**
 * Utilisateur connecté, conservé en session. Ne contient aucun mot de passe.
 * {@code userGroupIds} est figé à la connexion ;
 * le contenu des groupes ({@code allowedPaths}) est en revanche relu à chaque contrôle d'accès.
 */
public record EfdlUserPrincipal(
        String externalId,
        String username,
        String displayName,
        UserType type,
        String schoolExternalId,
        Set<String> userGroupIds,
        String nationality) implements Serializable {

    public static EfdlUserPrincipal of(User user) {
        return new EfdlUserPrincipal(user.getExternalId(), user.getUsername(), user.getDisplayName(), user.getType(),
                user.getSchoolExternalId(), user.getUserGroupIds() == null ? Set.of() : Set.copyOf(user.getUserGroupIds()),
                user.getNationality());
    }

    public Collection<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + (type == null ? "UNKNOWN" : type.name())));
    }

    public boolean isAdmin() {
        return type == UserType.ADMIN;
    }
}
