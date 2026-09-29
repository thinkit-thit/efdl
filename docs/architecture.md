# Architecture

```
 React (Vite, TypeScript, template Metrica)
        |  HTTP / REST / JSON  (cookie de session HttpOnly + jeton CSRF)
        v
 Spring Boot 4 (Java 21) — /api/**
        |  Spring Data MongoDB
        v
 MongoDB (base fdl2025)
```

Le frontend n'accède jamais à MongoDB. Toute règle de sécurité est appliquée par le backend.

## Backend (`backend/`)

| Paquet `com.ffs.efdl` | Rôle |
|---|---|
| `config` | Mongo (sans champ `_class`), CORS, Jackson |
| `security` | Spring Security, encodage des mots de passe, résolution du tenant, autorisation par route d'écran (`@RoutePath`) |
| `controller` | API REST (`/api/**`) |
| `service` | règles métier |
| `repository` | Spring Data MongoDB (noms de collections explicites) |
| `model` | `@Document` des collections MongoDB |
| `dto`, `mapper` | objets exposés par l'API (jamais les documents bruts : pas de mot de passe, pas de `contactPassword`) |
| `exception` | gestion centralisée des erreurs (réponses JSON sans information sensible) |

### Session et sécurité
- Session serveur Spring Security, cookie `HttpOnly` + `SameSite=Lax`, protection CSRF (`XSRF-TOKEN` lu par le client).
- Mot de passe : BCrypt dans la collection `user_credential`, seule référence (voir `mongodb.md`) ;
  après réinitialisation par un administrateur, `PasswordChangeInterceptor` bloque toute l'API sauf `/api/auth/**`
  jusqu'au changement.
- Autorisation : `User.type` + `UserGroup.allowedPaths`, appliquée sur la route d'écran déclarée par chaque endpoint (`@RoutePath`).

### Tenant (« application » = `School`)
Résolu par l'en-tête `Host` → `School.domainName`, sinon paramètre `schoolId`, puis mémorisé dans la session. Configurable par `efdl.tenant.default-school-id` pour le développement local.

## Frontend (`frontend/`)

| Dossier | Rôle |
|---|---|
| `assets/` | CSS, polices, images du template Metrica  |
| `layouts/` | `AppLayout` (leftbar-tab-menu, topbar, titre de page, footer), `AuthLayout` |
| `components/` | composants UI réutilisables aux classes Metrica |
| `pages/` | écrans par module |
| `routes/` | routage (React Router) et routes protégées |
| `services/` | client HTTP centralisé et appels API par module |
| `contexts/` | authentification, utilisateur, menu |
| `types/`, `hooks/`, `utils/` | types TypeScript, hooks, utilitaires |

Le comportement de `assets/js/app.js` du template (onglets du menu, collapse, tooltips) est réécrit en React ; seules les feuilles de style et polices du template sont réutilisées telles quelles.
