# eFDL

Plateforme de gestion et de suivi des projets du FDL : **Spring Boot (API REST) + React (TypeScript)** sur une base **MongoDB**.

| Composant | Port (développement) |
|---|---|
| Backend (Spring Boot) | 8081 |
| Frontend (Vite) | 5173 |
| MongoDB | 27017 (base `testfdl2025` en développement, `fdl2025` en production) |

## Structure

```
backend/    API REST Spring Boot 4 (Java 21, Maven, Spring Security, Spring Data MongoDB)
frontend/   React + TypeScript (Vite), template Metrica
deploy/     mise en ligne sur le VPS (voir deploy/README.md)
docs/       architecture, MongoDB, API
```

## Démarrage (développement)

### Prérequis
- JDK 21 (ex. `C:\Users\mamich\.jdks\temurin-21`), Maven 3.9+
- Node 20+ / npm 10+
- MongoDB 4.4+ accessible

### Backend
1. Copier `backend/src/main/resources/application-local.yml.example` en `application-local.yml` (non versionné) et renseigner les valeurs, ou définir les variables d'environnement :
   `MONGODB_URI`, `MONGODB_DATABASE`, `EFDL_ADMIN_MASTER_PASSWORD_HASH` (hash BCrypt du mot de passe maître).
2. `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local`

#### Mot de passe maître (compte `admin`)
Le compte `admin` se connecte uniquement avec le mot de passe maître (`admin.pass`),
stocké sous forme de **hash BCrypt**. Pour générer ce hash, depuis un terminal **interactif** :

| Terminal | Commande |
|---|---|
| cmd / PowerShell | `backend\tools\hash-master-password.cmd` |
| Git Bash | `backend/tools/hash-master-password.sh` (utilise `winpty` si disponible) |

L'outil demande le mot de passe deux fois **sans l'afficher**, affiche le hash, puis propose de l'écrire dans
`backend/src/main/resources/application-local.yml` (non versionné). Le mot de passe n'est jamais passé en argument
ni écrit sur disque. Redémarrer ensuite le backend. JDK utilisé : `EFDL_JAVA_HOME`, sinon `%USERPROFILE%\.jdks\temurin-21`.

### Frontend
```
cd frontend
npm install
npm run dev
```
Le serveur de développement Vite redirige `/api` vers `http://localhost:8081`.

## Règles du projet
- Aucune suppression physique : un élément supprimé passe au statut `DELETED` ; les collections, documents et index ne sont ni supprimés ni renommés.
- Mots de passe : uniquement des hashs BCrypt, dans la collection `user_credential`.
- Les permissions sont contrôlées **côté backend** (`User.type` et `UserGroup.allowedPaths`, route déclarée par `@RoutePath`).
- Secrets hors Git (`application-local.yml`, `application-prod.yml`, variables d'environnement).

Voir `docs/` pour le détail.
