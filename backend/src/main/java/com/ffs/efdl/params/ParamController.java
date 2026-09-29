package com.ffs.efdl.params;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.dto.AdminWriteDtos.Created;
import com.ffs.efdl.model.School;
import com.ffs.efdl.params.ParamModel.ChildLink;
import com.ffs.efdl.params.ParamModel.ColumnDef;
import com.ffs.efdl.params.ParamModel.FieldDef;
import com.ffs.efdl.params.ParamModel.FormDef;
import com.ffs.efdl.params.ParamModel.ParamDef;
import com.ffs.efdl.params.ParamModel.ScopeDef;
import com.ffs.efdl.params.ParamService.Choice;
import com.ffs.efdl.params.ParamService.ScopeLabel;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.PermissionService;
import com.ffs.efdl.security.TenantResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Écrans génériques (paramètres, PTA…). Chaque appel est contrôlé sur la route de l'écran correspondant
 * (liste, edit/{id|new}, delete/{id}). Les parents portés par l'URL de
 * l'écran (ex. {@code ptaFinancementId}) sont passés en paramètres de requête.
 */
@RestController
@RequestMapping("/api/params/{key}")
public class ParamController {

    /** Description de l'écran, pour que le frontend l'affiche sans dupliquer les définitions. */
    public record Meta(String key, String title, String section, String entityLabel, boolean creatable,
                       List<ColumnDef> columns, List<FieldDef> fields, List<String> filters, List<String> scope,
                       List<String> cascade, List<ChildLink> children, FormDef form) {
        static Meta of(ParamDef d, List<ChildLink> children, FormDef form) {
            return new Meta(d.key(), d.title(), d.section(), d.entityLabel(), d.creatable(), d.columns(), d.fields(),
                    d.filters(), d.scope().stream().map(ScopeDef::var).toList(), d.cascade(), children, form);
        }
    }

    public record ListDto(Meta meta, List<ScopeLabel> parents, List<Map<String, Object>> rows,
                          Map<String, List<Choice>> refs, boolean canCreate, boolean canEdit, boolean canDelete) {
    }

    public record DetailDto(Meta meta, List<ScopeLabel> parents, Map<String, Object> row,
                            Map<String, List<Choice>> refs, boolean canDelete) {
    }

    private final ParamService service;
    private final ParamDefinitions definitions;
    private final PermissionService permissionService;
    private final TenantResolver tenantResolver;
    private final List<CreationListener> creationListeners;

    public ParamController(ParamService service, ParamDefinitions definitions, PermissionService permissionService,
                           TenantResolver tenantResolver, List<CreationListener> creationListeners) {
        this.service = service;
        this.definitions = definitions;
        this.permissionService = permissionService;
        this.tenantResolver = tenantResolver;
        this.creationListeners = creationListeners;
    }

    @GetMapping
    public ListDto list(@PathVariable String key, HttpServletRequest request) {
        ParamDef def = service.definition(key);
        Map<String, String> scope = scope(def, request);
        require(def.listRoute(scope));
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        String school = school(request);
        List<ScopeLabel> parents = service.checkScope(def, school, scope);
        // Droits d'action : isAuthorizedPath(user, '.../edit/') et '.../delete/'.
        return new ListDto(meta(def), parents, service.list(def, school, scope), service.refs(def, school),
                def.creatable() && permissionService.canAccessRoute(user, def.editRoute(scope, "new")),
                permissionService.isAuthorizedPath(user, def.editRoute(scope, "")),
                permissionService.isAuthorizedPath(user, def.deleteRoute(scope, "")));
    }

    @GetMapping("/{id}")
    public DetailDto get(@PathVariable String key, @PathVariable String id, HttpServletRequest request) {
        ParamDef def = service.definition(key);
        Map<String, String> scope = scope(def, request);
        require(def.editRoute(scope, id));
        String school = school(request);
        List<ScopeLabel> parents = service.checkScope(def, school, scope);
        boolean isNew = "new".equals(id);
        if (isNew && !def.creatable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Création non disponible pour cet écran");
        }
        // Création : fiche préremplie avec ce qui découle du parent de l'URL (ex. lot d'un contrat multiple).
        return new DetailDto(meta(def), parents, isNew ? service.blank(def, school, scope) : service.get(def, school, scope, id),
                service.refs(def, school),
                !isNew && permissionService.canAccessRoute(RoutePathInterceptor.currentUser(), def.deleteRoute(scope, id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Created create(@PathVariable String key, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        ParamDef def = service.definition(key);
        Map<String, String> scope = scope(def, request);
        require(def.editRoute(scope, "new"));
        String school = school(request);
        String id = service.create(def, school, scope, body, by());
        creationListeners.stream().filter(l -> l.key().equals(key)).forEach(l -> l.created(school, id));
        return new Created(id);
    }

    @PutMapping("/{id}")
    public DetailDto update(@PathVariable String key, @PathVariable String id, @RequestBody Map<String, Object> body,
                            HttpServletRequest request) {
        ParamDef def = service.definition(key);
        Map<String, String> scope = scope(def, request);
        require(def.editRoute(scope, id));
        service.update(def, school(request), scope, id, body, by());
        return get(key, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String key, @PathVariable String id, HttpServletRequest request) {
        ParamDef def = service.definition(key);
        Map<String, String> scope = scope(def, request);
        require(def.deleteRoute(scope, id));
        service.delete(def, school(request), scope, id, by());
    }

    private Meta meta(ParamDef def) {
        return Meta.of(def, definitions.children(def.key()), definitions.form(def.key()));
    }

    /** Parents de l'URL historique, passés en paramètres ({@code ?ptaFinancementId=…}). */
    private static Map<String, String> scope(ParamDef def, HttpServletRequest request) {
        Map<String, String> scope = new LinkedHashMap<>();
        for (ScopeDef s : def.scope()) {
            String value = request.getParameter(s.var());
            if (value == null || value.isBlank() || value.contains("/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paramètre manquant : " + s.var());
            }
            scope.put(s.var(), value);
        }
        return scope;
    }

    private void require(String routePath) {
        if (!permissionService.canAccessRoute(RoutePathInterceptor.currentUser(), routePath)) {
            throw new AccessDeniedException("Accès refusé");
        }
    }

    private String school(HttpServletRequest request) {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        return tenantResolver.resolve(request).map(School::getExternalId).orElse(user == null ? null : user.schoolExternalId());
    }

    private static String by() {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        return user == null ? null : user.externalId();
    }
}
