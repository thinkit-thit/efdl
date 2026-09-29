# Mise en ligne d'eFDL sur le VPS OVH

eFDL est hébergé sur un **VPS OVH** (2 vCores, 8 Go de RAM, 75 Go SSD, **Ubuntu 26.04 LTS**), avec sa base MongoDB.
Le kit fonctionne aussi sur tout autre VPS Ubuntu LTS.

```
Internet ──HTTPS──► Nginx (80/443) ─┬─ /          fichiers du frontend (/var/www/efdl-modern)
   www.e-fdl.mg / e-fdl.mg          └─ /api/      backend Java 21/25 (127.0.0.1:8081, service efdl-modern)
                                                        │
                                                        ▼
                                              MongoDB 4.4 (Docker, 127.0.0.1:27017) — base fdl2025
                                              Fichiers : /home/data/fdl (photos), /home/data/fdl-modern
```

| Fichier | Rôle |
|---|---|
| `server/install.sh` | installation complète du VPS (une fois) |
| `server/efdl.env.example` | modèle de configuration de production (secrets : jamais dans Git) |
| `server/mail.properties.example` | modèle des paramètres Gmail |
| `server/efdl-modern.service` | service systemd du backend |
| `server/nginx-efdl.conf` | site Nginx (HTTPS ajouté par Certbot) |
| `server/update.sh` | mise en place d'une nouvelle version, retour arrière (`--rollback`) |
| `server/backup.sh` | sauvegarde nocturne (base + fichiers, 14 jours) |
| `server/import-dump.sh` | chargement d'une archive de données (base + fichiers) |
| `pc/deploy.ps1` | construction et livraison depuis le PC Windows |

---

## 1. Premier accès au VPS OVH

> **Port SSH du VPS : 2123** (depuis le 28/09/2026, au lieu de 22). Toutes les commandes le précisent :
> `ssh -p 2123`, `scp -P 2123` (P majuscule pour scp) ; `deploy.ps1` l'utilise par défaut (`-Port` pour un autre port).

1. Dans l'**espace client OVH** (*Bare Metal Cloud › VPS*), noter l'**adresse IPv4** du VPS. L'e-mail de livraison
   donne l'utilisateur (`ubuntu`) et son mot de passe (ou il se définit dans l'espace client).
2. Depuis le PC (PowerShell), se connecter une première fois :
   ```powershell
   ssh -p 2123 ubuntu@IP
   ```
   Le système demande de changer le mot de passe à la première connexion : choisir un mot de passe fort.
3. **Conseillé : clé SSH** (plus sûr, plus de mot de passe à taper à chaque livraison) :
   ```powershell
   ssh-keygen -t ed25519 -f C:\Users\mamich\.ssh\efdl-vps
   type C:\Users\mamich\.ssh\efdl-vps.pub | ssh -p 2123 ubuntu@IP "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
   ```
   Ensuite : `ssh -p 2123 -i C:\Users\mamich\.ssh\efdl-vps ubuntu@IP` (et `-Key` pour `deploy.ps1`).
4. Mettre le système à jour :
   ```bash
   sudo apt update && sudo apt full-upgrade -y && sudo reboot
   ```
5. Pare-feu OVH (*Network › Firewall*, désactivé par défaut) : s'il est activé, autoriser TCP **2123** (SSH), **80**, **443**. Sur le VPS, si ufw est actif : `sudo ufw allow 2123/tcp`.

## 2. Installer le VPS

Depuis le PC (PowerShell) :

```powershell
scp -P 2123 -i C:\Users\mamich\.ssh\efdl-vps -r C:\Users\mamich\IdeaProjects\efdl-modern\deploy\server ubuntu@IP:/tmp/efdl-install
ssh -p 2123 -i C:\Users\mamich\.ssh\efdl-vps ubuntu@IP
```

Sur le VPS :

```bash
sudo bash /tmp/efdl-install/install.sh www.e-fdl.mg e-fdl.mg
```

Puis compléter la configuration (éditeur `nano`) :

```bash
sudo nano /opt/efdl-modern/efdl.env          # hash du mot de passe maître (voir ci-dessous)
sudo nano /opt/efdl-modern/mail.properties   # recopier les valeurs de backend/config/mail.properties du PC
```

- **Hash du mot de passe maître** : sur le PC, `backend\tools\hash-master-password.cmd`, puis coller le résultat entre
  apostrophes dans `EFDL_ADMIN_MASTER_PASSWORD_HASH`.
- **mail.properties** : mêmes valeurs Gmail qu'en développement (`efdl.mail.app-url=https://www.e-fdl.mg` est déjà rempli).

## 3. Charger les données

Archive `.tgz` contenant le dossier `dump/fdl2025` (créé par `mongodump`) et, si besoin, le dossier `fdl/` des
fichiers (photos de profil). La transférer sur le VPS (`/tmp/`, avec WinSCP ou `scp -P 2123`), puis :

```bash
sudo /opt/efdl-modern/bin/import-dump.sh /tmp/efdl-export-AAAAMMJJ-HHMM.tgz
```

Sur une base déjà remplie, ajouter `--remplacer` (une sauvegarde est faite avant).

## 4. Livrer l'application

Depuis le PC, à la racine du projet :

```powershell
.\deploy\pc\deploy.ps1 -Server IP                                   # ou -Key C:\Users\mamich\.ssh\efdl-vps
```

Le script lance les tests, construit, envoie, redémarre et vérifie que le backend répond. Chaque nouvelle version
se livre de la même façon. Retour à la version précédente : `sudo /opt/efdl-modern/bin/update.sh --rollback`.

## 5. Nom de domaine et HTTPS

1. DNS (chez le gestionnaire du domaine e-fdl.mg) : enregistrements **A** de `e-fdl.mg` et `www` → **IP du VPS**.
   Attendre la propagation (`ping www.e-fdl.mg` doit répondre l'IP du VPS ; de quelques minutes à quelques heures).
2. HTTPS et cookie sécurisé, sur le VPS :
   ```bash
   sudo certbot --nginx -d www.e-fdl.mg -d e-fdl.mg --redirect -m VOTRE_EMAIL --agree-tos -n
   sudo sed -i 's/^SERVER_SERVLET_SESSION_COOKIE_SECURE=false/SERVER_SERVLET_SESSION_COOKIE_SECURE=true/' /opt/efdl-modern/efdl.env
   sudo systemctl restart efdl-modern
   ```
   Le certificat se renouvelle tout seul.

## 6. Vérifications après livraison

Sur **https://www.e-fdl.mg** :

- [ ] Connexion (compte `admin` avec le mot de passe maître), puis mots de passe des utilisateurs
      (fiche utilisateur ou « Mot de passe oublié » par e-mail)
- [ ] Menus et droits d'un utilisateur ADMINISTRATION
- [ ] Programmes, projets, sous-projets (fiche, onglets, avancement, documents)
- [ ] Contrats, factures, circuit de validation, e-mails et notifications
- [ ] Tableaux de bord, carte, exports PDF / Word
- [ ] Imports Excel (validateur info / admin)
- [ ] Photos de profil affichées
- [ ] Historique des modifications

## Exploitation

| Besoin | Commande (sur le VPS) |
|---|---|
| État du service | `systemctl status efdl-modern` |
| Journal | `journalctl -u efdl-modern -f` |
| Redémarrer | `sudo systemctl restart efdl-modern` |
| Sauvegarde immédiate | `sudo /opt/efdl-modern/bin/backup.sh` |
| Sauvegardes | `/home/backup/efdl` (14 jours) — **en copier régulièrement une sur le PC** (WinSCP) |
| Base MongoDB (console) | `sudo docker exec -it mongo mongo fdl2025` |

**Mémoire** : MongoDB est limité à 2 Go de cache, Java à 1 Go (`JAVA_OPTS`) ; à l'aise dans les 8 Go du VPS.

**Sauvegardes** : `/home/backup/efdl` est sur le même disque que les données. En copier régulièrement une sur le PC
(WinSCP), ou activer l'option de sauvegarde automatique du VPS dans l'espace client OVH.
