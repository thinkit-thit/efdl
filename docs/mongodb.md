# MongoDB

## Bases
| Environnement | Base |
|---|---|
| Production (VPS) | `fdl2025` (MongoDB 4.4, Docker, `127.0.0.1:27017`) |
| Développement | `testfdl2025` |
| Tests d'intégration | `efdl_it`, `efdl_loc` (bases jetables, vidées par les tests ; garde-fou sur le nom) |

- La sonde de santé Actuator utilise la commande `ping` (`MongoHealthConfig`), compatible avec MongoDB 4.2 et plus.
- Applications (collection `school`) : `admin` (sans domaine) et `sdi-fdl`. En local, le backend utilise
  `efdl.tenant.default-school-id = sdi-fdl`.

## Configuration du backend
Variables : `MONGODB_URI` (ex. `mongodb://127.0.0.1:27017`), `MONGODB_DATABASE` (ex. `testfdl2025`), éventuellement
`MONGODB_USERNAME` / `MONGODB_PASSWORD` si l'authentification est activée. Aucune valeur secrète dans Git.
Les index ne sont jamais créés automatiquement (`spring.data.mongodb.auto-index-creation=false`).

## Format des documents

| Élément | Règle |
|---|---|
| `_id` | `ObjectId` (`@Id ObjectId objectId`) |
| Identifiant métier | `externalId` (UUID, index unique), jamais régénéré pour un document existant |
| Tenant | `schoolExternalId`, filtré sur chaque requête |
| Relations | chaînes `xxxExternalId` / `xxxId`, objets imbriqués ; **aucune DBRef** |
| Collection | `@Document(collection = "…")` explicite (sensible à la casse : `Project`, `Menu`, `Bv`…) |
| Type | champ `className` ; **pas de `_class`** Spring (désactivé) |
| Enums | nom (String) |
| Dates | `creationDate`, `lastUpdateDate` |

## Écritures
Toutes les écritures des écrans passent par `DocumentWriter` (jamais par `save()` de Spring Data).
- **Création** : `_id` ObjectId, `className`, champs non nuls seulement, `externalId` (UUID ;
  pour `school` : identifiant normalisé du nom), `schoolExternalId` + `orderPlace: 0` (sauf `school`), `creationDate`,
  `lastUpdateDate`, `lastUpdateUserId`. Champs numériques et booléens toujours écrits (`signupActive`, `siege`, `voix`, `order`, `amount`…).
- **Modification** : `$set` des champs de l'écran, `$unset` des champs vidés ; filtre `externalId` (+ `schoolExternalId`).
  Les autres champs (et `user.password`) ne sont jamais touchés.
- **Aucune suppression** physique : « supprimer » = `$set status: "DELETED"`.
  Toute lecture ou écriture ignore les documents `DELETED`.
- Aucune modification en masse (une modification = un document) ; les imports Excel passent ligne à ligne par les
  mêmes règles que la saisie.
- Historique : chaque écriture est tracée dans la collection `audit_log` (qui, quoi, quand, valeurs avant / après).

## Mots de passe
Collection `user_credential`, seule référence des mots de passe :
`{ userExternalId, schoolExternalId, passwordHash (BCrypt), algorithm, mustChangePassword, updatedBy, createdAt, updatedAt }`.
Écrite par une réinitialisation administrateur, un changement personnel ou « Mot de passe oublié » (`password_reset`).
Le champ `user.password` n'est jamais lu (sauf le marqueur `admin`) ni écrit.

## Données sensibles à ne jamais exposer par l'API
`user.password`, `District/Commune/Fokontany.contactPassword`, secrets de configuration.
