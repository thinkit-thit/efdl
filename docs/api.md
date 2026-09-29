# API REST

Base : `/api`. Format JSON. Erreurs : `{ "status": 403, "error": "FORBIDDEN", "message": "…" }` (jamais de trace ni de détail interne).

## Authentification et session

| Méthode | Chemin | Accès | Description | Route contrôlée |
|---|---|---|---|---|
| GET | `/api/auth/csrf` | public | initialise le cookie `XSRF-TOKEN` | — |
| POST | `/api/auth/login` | public | `{ identifier, password }` → utilisateur connecté (session) | `/login?login` |
| POST | `/api/auth/logout` | connecté | ferme la session | `/logout` |
| GET | `/api/auth/me` | connecté | utilisateur courant + école courante | — |
| GET | `/api/menus/me` | connecté | menu (3 niveaux) filtré par les droits de l'utilisateur | — |
| GET | `/api/tenant` | public | école (application) résolue pour la requête | — |
| GET | `/api/health` | public | état de l'API | — |

## Administration

Chaque endpoint déclare la route de l'écran qu'il sert (`@RoutePath`) : les droits de cette route (`UserGroup.allowedPaths`) s'appliquent.

| Méthode | Chemin | Route contrôlée | Description |
|---|---|---|---|
| GET | `/api/admin/groups` | `/admin/user/group/list` | groupes de l'école (nom, chemins autorisés) |
| GET | `/api/admin/groups/{id}` | `/admin/user/group/edit/{id}` | fiche groupe (par externalId, sans filtre d'école) |
| GET | `/api/admin/menus` | `/admin/menus` | menus de l'école triés par `order`, avec titre du parent |
| GET | `/api/admin/menus/{id}` | `/admin/menu/edit/{id}` | fiche menu |
| GET | `/api/admin/schools` | `/admin/school/list` | toutes les applications — **ADMIN uniquement** |
| GET | `/api/admin/schools/{id}` | `/admin/school/edit/{id}` | fiche application — ADMIN uniquement |
| GET | `/api/admin/users` | `/admin/user/administration/list` | utilisateurs ADMINISTRATION (hors contrats de fin/autre), tri par date de création ; `canEdit` = droit sur `/admin/user/administration/edit/` |
| GET | `/api/admin/menus/{id}/parents` | `/admin/menu/edit/{id}` | choix du menu parent (tous les menus de l'école sauf lui-même ; `id` = `new` en création) |
| GET | `/api/admin/users/new` | `/admin/user/administration/edit/new` | fiche vierge + groupes de l'école (création) |
| GET | `/api/admin/users/{id}` | `/admin/user/administration/edit/{id}` | fiche de renseignements (sans mot de passe), groupes de l'école cochés |

Vérifié sur `testfdl2025` par `AdminReadIT` (`mvn test -Defdl.it=true -Dtest=AdminReadIT`).

### Écritures — aucune suppression physique

Route de l'écran d'édition : `edit/new` en création, `edit/{id}` en modification.
Jeton CSRF obligatoire. Création : `201 {"externalId": "..."}`. Modification : la fiche relue.
Erreurs : `400` (champ invalide, parent/groupe inconnu), `403`, `404`, `409` (identifiant déjà pris).

| Méthode | Chemin | Route contrôlée | Corps |
|---|---|---|---|
| POST / PUT | `/api/admin/groups[/{id}]` | `/admin/user/group/edit/{new|id}` | `name`*, `allowedPaths[]` (vides ignorés, doublons retirés) |
| POST / PUT | `/api/admin/menus[/{id}]` | `/admin/menu/edit/{new|id}` | `title`*, `icon`, `parentId`, `order`, `url`, `path` |
| POST / PUT | `/api/admin/schools[/{id}]` | `/admin/school/edit/{new|id}` (ADMIN) | `name`*, `domainName`, `years`, `address`, `description` ; externalId = nom normalisé, 409 s'il existe |
| POST / PUT | `/api/admin/users[/{id}]` | `/admin/user/administration/edit/{new|id}` | `username`*, `lastName`, `firstName`, `gender` (MALE/FEMALE), `phone`, `email`, `address`, `birthDate`, `birthPlace`, `cinNumber`, `cinDate`, `cinPlace`, `numeroCnaps`, `inDate` (dates `yyyy-MM-dd`), `userGroupIds[]` ; jamais de mot de passe |

Format des documents : voir [mongodb.md](mongodb.md#écritures). Test : `AdminWriteIT` sur la base jetable
`efdl_it` (`mvn test -Defdl.it=true -Dtest=AdminWriteIT`).

### Suppressions logiques et mots de passe

| Méthode | Chemin | Route contrôlée | Effet |
|---|---|---|---|
| DELETE | `/api/admin/menus/{id}` | `/admin/menu/delete/{id}` | `status=DELETED` ; 409 s'il reste des sous-menus |
| DELETE | `/api/admin/schools/{id}` | `/admin/school/delete/{id}` (ADMIN) | `status=DELETED` ; 409 pour l'application en cours |
| DELETE | `/api/admin/users/{id}` | `/admin/user/administration/edit/{id}` | `status=DELETED` ; 409 pour son propre compte |
| POST | `/api/admin/users/{id}/password` | `/admin/user/administration/edit/{id}` | `{password}` provisoire → `user_credential`, changement obligatoire |
| POST | `/api/auth/password` | — (connecté) | `{currentPassword, newPassword}` ; 400 si l'actuel est faux ; limité à 5 échecs / 15 min |

Règles du mot de passe : 8 caractères minimum, au moins une lettre et un chiffre, 72 octets maximum (limite BCrypt).
`GET /api/auth/me` et la réponse du login contiennent `mustChangePassword` ; tant qu'il est vrai, toute autre route
`/api/**` (hors `/api/auth/**` et `/api/tenant`) répond 403 « Vous devez changer votre mot de passe ».
Pas d'endpoint de suppression de groupe.

## Écrans « Paramètre » (`/api/params/{key}`)

Moteur générique décrit par `ParamDefinitions` (collections, champs en liste blanche, colonnes et libellés).
Droits contrôlés sur la route de l'écran de chaque action :

| Méthode | Chemin | Route contrôlée |
|---|---|---|
| GET | `/api/params/{key}` | `{base}/{key}` (liste) — renvoie `meta`, `rows`, `refs`, `canCreate`, `canEdit`, `canDelete` |
| GET | `/api/params/{key}/{id}` | `{base}/{singular}/edit/{id}` (`new` pour une création) |
| POST | `/api/params/{key}` | `{base}/{singular}/edit/new` |
| PUT | `/api/params/{key}/{id}` | `{base}/{singular}/edit/{id}` |
| DELETE | `/api/params/{key}/{id}` | `{base}/{singular}/delete/{id}` — suppression logique ; 409 si des éléments rattachés existent |

| Section | key | Collection | Création |
|---|---|---|---|
| Projets (`/admin/project/parametre`) | categories, secteurs, infrastructures, typeInfrastructures, typeTravaux, sousProjets, avancements, questions | Categorie, Secteur, Infrastructure, TypeInfrastructure, TypeTravaux, SousProjet, Avancement, Question | oui |
| Validateurs paiement | validateurs | Validateur | oui |
| Indicateurs KPI | indicateurs | Indicateur | oui |
| Localisation (`/admin/dashboard/election`) | provinces, regions, districts, communes, fokontanies | Province, Region, District, Commune, Fokontany | oui, en cascade |

Création d'une localisation : champs `createOnly` (parents, `code`, `categorie` d'une commune) acceptés seulement au POST ;
seul le parent direct est obligatoire (ex. `districtId` pour une commune), les ancêtres sont recopiés depuis lui
(400 si ceux envoyés ne correspondent pas) ; `code` vide = calculé, sinon vérifié (400 format, 409 déjà utilisé).

`canEdit` / `canDelete` = droits d'action (`isAuthorizedPath(user, '.../edit/')` et `'.../delete/'`).
Entiers et décimaux écrits en Int32 / Double (0 par défaut).
Tests : `ParamReadIT` (lecture seule sur `testfdl2025` : 15 écrans, nombres identiques, 0 référence non résolue) ;
écritures : `ParamIT` (base jetable `efdl_it`).

### FDL PTA

| key | Routes contrôlées | Parents (paramètres de l'API) |
|---|---|---|
| ptaFinancements | `/admin/pta/ptaFinancements`, `/admin/pta/ptaFinancement/edit|delete/{id}` | — |
| ptaActivites | `/admin/pta/ptaFinancement/{ptaFinancementId}/ptaActivites`, `…/{ptaFinancementId}/edit|delete/{id}` | `ptaFinancementId` |
| ptaSousActivites | `…/{ptaFinancementId}/ptaActivite/{ptaActiviteId}/ptaSousActivites`, `…/ptaActivite/{ptaActiviteId}/edit|delete/{id}` | `ptaFinancementId`, `ptaActiviteId` |

Les parents sont vérifiés (existants, de l'école, rattachés entre eux) ; la création les enregistre depuis l'URL,
une modification ne déplace jamais l'élément. Réponses : `parents` (libellés pour le fil d'Ariane),
`meta.children` (liens « Activités », « Sous-activités »), `meta.cascade` (région → district → communes, vérifié côté serveur).
Dates : `yyyy-MM-dd`, stockées à minuit (fuseau du serveur).

### FDL PROJET — Programmes, projets, prestataires

| key | Routes contrôlées | Parents |
|---|---|---|
| programmes | `/admin/project/programmes`, `/admin/project/programme/edit|delete/{id}` | — |
| subProgrammes | `/admin/project/programme/{programmeId}/subProgrammes`, `…/{programmeId}/edit|delete/{id}` | `programmeId` |
| prestataires | `/admin/project/prestataires`, `/admin/project/prestataire/edit|delete/{id}` (collection `provider`) | — |

`meta.children` peut contenir un lien vers un écran dédié (`path`, ex. tableau de bord).
Prestataire : `number` = dernier numéro de l'école + 1 à la création, jamais modifié ensuite.

### FDL PROJET — Sous-projets, collection `Project`

| key | Routes contrôlées | Parents | Création |
|---|---|---|---|
| projects | `/admin/project/programme/{programmeId}/subProgramme/{subProgrammeId}/projects`, `…/edit|delete/{id}` | `programmeId`, `subProgrammeId` | oui |
| allProjects | `/admin/project/projects`, `/admin/project/project/edit|delete/{id}` | — | non |
| regionProjects | `/admin/project/{regionId}/projects`, `/admin/project/{regionId}/project/edit/{id}` | `regionId` | non |

Fiche en 7 étapes (`meta.fields[].section`) : une modification n'enregistre que les champs envoyés (PUT partiel).
Dérivations (`ParamDefinitions.derivations`) : commune → `regionId`, `districtId` ;
infrastructure → `indicateurExternalId` (indicateur KPI de l'infrastructure). Colonnes combinées : `with` + `op`
(« join » pour la localisation, « sum » pour le montant BE + Ese).

### FDL PROJET — Contrats, paiements

| key | Routes contrôlées | Particularités |
|---|---|---|
| contrats | `/admin/project/contrats`, `/admin/project/contrat/edit|delete/{id}` | jumeau `GroupeContrat` (même externalId, `multiple=false`) créé / mis à jour / supprimé avec le contrat simple |
| groupeContrats | `/admin/project/groupeContrats`, `/admin/project/groupeContrat/edit|delete/{id}` | seulement `multiple=true` |
| groupeContratLots | `…/groupeContrat/{groupeContratId}/edit/{id}` (fiche) ; liste et suppression contrôlées sur `…/groupeContrat/edit/{groupeContratId}` | titre, type, prestataire, dates hérités du contrat multiple |
| paiements | `/admin/project/paiements`, `/admin/project/paiement/edit|delete/{id}` | contrat choisi parmi ceux du prestataire ; `status=PENDING` à la création ; modification / suppression refusées (409) hors PENDING |

Valeurs calculées (jamais écrites) : `montantPayeValide` = somme des paiements VALIDATE du contrat,
`montantContratGroupe` = montant du contrat choisi.

### FDL PROJET — Validation des paiements

| Méthode | Chemin | Route contrôlée | Effet |
|---|---|---|---|
| GET | `/api/validations` | `/admin/project/validationPaiements` | factures PENDING qui attendent l'utilisateur connecté |
| GET | `/api/validations/{id}` | `/admin/project/validationPaiement/edit/{id}` | fiche : facture, montants, étape (note, check-list), historique, `canValidate` |
| POST | `/api/validations/{id}/validate` | idem | niveau + 1 ; VALIDATE après le dernier validateur ; sinon e-mail au validateur suivant |
| POST | `/api/validations/{id}/reject` | idem | REJECTED |

Création d'une facture à l'écran (`POST /api/params/paiements`) : e-mail « VALIDATION DE PAIEMENT » au 1er validateur du programme (adresse relue dans `user.email` à l'envoi).

Validateurs d'un programme : écran générique `programmeValidateurs`
(`/admin/project/programme/validateur/{programmeId}/validateurs`, `…/edit|delete/{id}`), ordre unique dans le programme.
Circuit = validateurs actifs du programme triés par `ordre` ; une facture de niveau `n` attend le validateur de rang `n`.
Seul ce validateur peut décider (403 sinon), mise à jour conditionnelle (409 si déjà traitée), trace dans
`statusUserId` / `dateValidationId`. Liste des factures : colonnes calculées
« Projet » et « Prochain validateur » (`RowEnricher`).

### FDL PROJET — Avancement, tableaux de bord

| Méthode | Chemin | Route contrôlée |
|---|---|---|
| GET / PUT | `/api/progress/programme/{id}` | `/admin/project/programme/avancement/{id}` |
| GET / PUT | `/api/progress/programme/{programmeId}/subProgramme/{id}` | `/admin/project/programme/{programmeId}/avancement/{id}` |
| GET / PUT | `/api/progress/programme/{programmeId}/subProgramme/{subProgrammeId}/project/{id}` | `/admin/project/programme/{programmeId}/subProgramme/{subProgrammeId}/avancement/{id}` |
| GET | `/api/dashboards/programme/{id}` | `/admin/project/programme/dashboard/{id}` |
| GET | `/api/dashboards/programme/{programmeId}/subProgramme/{id}` | `/admin/project/programme/{programmeId}/dashboard/{id}` |
| GET | `/api/dashboards/home?programmeId=…` | `/home` |

Avancement : jalons `Avancement` du type du niveau (PROGRAMME, PROJET, SOUS_PROJET), valeurs dans `avancementMap`
(clé = externalId du jalon ; entrée AvancementProjet embarquée : prevision, reel, evidence, titre, statutAvancement,
userId, externalId et creationDate conservés). PUT : `{"jalons":[{avancementId, prevision, reel, evidence}]}`,
seules les lignes envoyées sont modifiées. Pourcentage = somme des pourcentages des jalons ayant une date réelle
(colonne « Avancement (%) » des listes de sous-projets). Écran générique `districtProjects`
(`/admin/project/district/{districtCode}/projects`, district désigné par son code).

### Imports Excel

`POST` multipart (`file`), `apply=false` = analyse sans écriture, `apply=true` = import. Réponse : compteurs
(`lignes`, `creations`, `misesAJour`, `ignorees`, `erreurs`) et `details` ligne par ligne. Écritures par
`ParamService` (mêmes règles que la saisie). Fichier .xlsx / .xls, 10 Mo maximum.

| Chemin | Route contrôlée | Contenu |
|---|---|---|
| `/api/imports/prestataires` | `/admin/project/prestataires` | N°, Dénomination, NIF, STAT, Adresse, RCS, Contact ; mise à jour par N° |
| `/api/imports/paiements?historique=true|false` | `/admin/project/paiement/edit/new` | N° contrat, N° facture, Intitulé, N° / Intitulé attachement, Montant ; doublons ignorés ; historique = VALIDATE, sinon PENDING |
| `/api/imports/programme/{programmeId}/subProgramme/{subProgrammeId}/projects` | `…/subProgramme/{subProgrammeId}/upload` | fiche de suivi (colonnes repérées par en-tête, modèles de 111 et 47 colonnes) |

### Mot de passe oublié (public)

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| POST | `/api/auth/password-reset/request` | `{identifier}` (nom d'utilisateur, e-mail ou téléphone) | 202 dans tous les cas ; 503 si l'envoi d'e-mails n'est pas configuré ; 429 au-delà de 5 demandes / 15 min par adresse IP |
| POST | `/api/auth/password-reset/confirm` | `{identifier, code, newPassword}` | 204 ; 400 « Code invalide ou expiré » ; 429 après 5 échecs |

Code à 6 chiffres envoyé par e-mail (SMTP Gmail : `spring.mail.*` et `efdl.mail.from` dans `backend/config/mail.properties`, non versionné — modèle `mail.properties.example`), conservé haché dans la
collection `password_reset`, valable 15 minutes, usage unique, 5 essais ; une nouvelle demande annule la précédente.
Compte `admin` exclu (mot de passe maître). Page : `/forgotPwd`.

### Localisations (tableau de bord et import)

| Méthode | Chemin | Route contrôlée | Effet |
|---|---|---|---|
| GET | `/api/dashboards/localisation` | `/admin/dashboard/election/tbs` | nombres de provinces, régions, districts, communes, fokontany (hors supprimés) |
| GET | `/api/dashboards/localisation/districts` | idem | par district : communes bénéficiaires des projets FDL, population, population de 18 ans et plus |
| POST | `/api/imports/localisations?apply=false|true` | idem (+ droit d'import) | fichier Excel des localisations : mise à jour par code, ajout des manquants, population des fokontany ; aucune suppression |

Population des régions, districts et communes : somme de celle de leurs fokontany (calculée à la lecture).
