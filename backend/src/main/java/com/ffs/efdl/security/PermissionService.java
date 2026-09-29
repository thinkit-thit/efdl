package com.ffs.efdl.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ffs.efdl.model.UserGroup;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.UserGroupRepository;

/**
 * Règles d'accès :
 * <ul>
 *   <li>UserService.isAuthorizedPath : ADMIN → autorisé ; sinon une regex {@code allowedPaths}
 *       d'un des groupes de l'utilisateur doit correspondre à la route entière ({@code matches()}).</li>
 *   <li>SuperAdminFilter ({@code /admin/school/*}) : ADMIN uniquement.</li>
 *   <li>AdminFilter ({@code /admin/*}) : ADMIN, ou ADMINISTRATION avec isAuthorizedPath ; sinon refus.</li>
 * </ul>
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    private final UserGroupRepository userGroupRepository;
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public PermissionService(UserGroupRepository userGroupRepository) {
        this.userGroupRepository = userGroupRepository;
    }

    /** Équivalent de UserService.isAuthorizedPath(user, path). */
    public boolean isAuthorizedPath(EfdlUserPrincipal user, String path) {
        if (user == null || path == null) {
            return false;
        }
        if (user.type() == UserType.ADMIN) {
            return true;
        }
        Set<String> groupIds = user.userGroupIds();
        if (groupIds == null || groupIds.isEmpty()) {
            return false;
        }
        List<UserGroup> groups = userGroupRepository.findByExternalIdIn(groupIds);
        for (UserGroup group : groups) {
            if (group.isDeleted() || group.getAllowedPaths() == null) {
                continue;
            }
            for (String allowedPath : group.getAllowedPaths()) {
                Pattern pattern = compile(allowedPath);
                if (pattern != null && pattern.matcher(path).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Règles d'accès appliquées à une route d'écran. */
    public boolean canAccessRoute(EfdlUserPrincipal user, String routePath) {
        if (user == null) {
            return false;
        }
        if (routePath.startsWith("/admin/school/")) {
            return user.type() == UserType.ADMIN;
        }
        if (routePath.startsWith("/admin/") || routePath.equals("/admin")) {
            if (user.type() == UserType.ADMIN) {
                return true;
            }
            return user.type() == UserType.ADMINISTRATION && isAuthorizedPath(user, routePath);
        }
        return true;
    }

    private Pattern compile(String regex) {
        return patternCache.computeIfAbsent(regex, r -> {
            try {
                return Pattern.compile(r);
            } catch (PatternSyntaxException e) {
                // Une regex invalide est simplement ignorée.
                log.warn("Expression allowedPaths invalide ignorée : {}", r);
                return null;
            }
        });
    }
}
