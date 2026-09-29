#!/usr/bin/env bash
# Installation d'eFDL sur un VPS Ubuntu LTS (24.04 / 26.04 ; x86 ou ARM).
# À lancer UNE fois, en root :  sudo bash install.sh www.e-fdl.mg e-fdl.mg   (1er domaine = adresse principale)
# Installe : Java 21 (ou 25), Docker + MongoDB 4.4 (local seulement), Nginx, Certbot, le service efdl-modern, les sauvegardes.
# Ne touche à aucune donnée existante ; peut être relancé sans risque (étapes déjà faites ignorées).
set -euo pipefail

[ $# -ge 1 ] || { echo "Usage : sudo bash install.sh <domaine principal> [autres domaines…]   (ex. www.e-fdl.mg e-fdl.mg)"; exit 1; }
DOMAIN="$1"                                   # adresse principale (liens des e-mails)
DOMAINS="$*"                                  # tous les noms servis par Nginx
ORIGINS=$(printf 'https://%s,' "$@"); ORIGINS=${ORIGINS%,}
CERTBOT_ARGS=$(printf -- '-d %s ' "$@")
APP=/opt/efdl-modern
WEB=/var/www/efdl-modern
DATA=/home/data
HERE="$(cd "$(dirname "$0")" && pwd)"

[ "$(id -u)" -eq 0 ] || { echo "À lancer en root (sudo)."; exit 1; }
echo "== eFDL : installation pour $DOMAINS =="

echo "-- Paquets (Java 21 ou 25, Nginx, Certbot, Docker)"
apt-get update -q
# Java 21 (version de développement) ; s'il n'est plus proposé (Ubuntu récent), Java 25, pris en charge par Spring Boot 4.
JAVA_PKG=openjdk-21-jre-headless
apt-cache show "$JAVA_PKG" >/dev/null 2>&1 || JAVA_PKG=openjdk-25-jre-headless
echo "   Java : $JAVA_PKG"
DEBIAN_FRONTEND=noninteractive apt-get install -y -q "$JAVA_PKG" nginx certbot python3-certbot-nginx \
  docker.io netfilter-persistent iptables-persistent curl
systemctl enable --now docker

echo "-- Utilisateur et dossiers"
id efdl >/dev/null 2>&1 || useradd --system --home "$APP" --shell /usr/sbin/nologin efdl
mkdir -p "$APP/bin" "$WEB" "$DATA/fdl/image/user" "$DATA/fdl-modern" /home/backup/efdl /var/lib/efdl-mongo
chown -R efdl:efdl "$DATA/fdl" "$DATA/fdl-modern"
chown root:efdl "$APP" && chmod 750 "$APP"   # le service (efdl) lit le .jar, les secrets restent protégés

echo "-- MongoDB 4.4 (Docker, accessible uniquement depuis la machine)"
if ! docker ps -a --format '{{.Names}}' | grep -qx mongo; then
  docker run -d --name mongo --restart unless-stopped \
    -p 127.0.0.1:27017:27017 -v /var/lib/efdl-mongo:/data/db \
    mongo:4.4 --wiredTigerCacheSizeGB 2
fi
for i in $(seq 1 30); do docker exec mongo mongo --quiet --eval 'db.runCommand({ping:1}).ok' 2>/dev/null | grep -q 1 && break; sleep 2; done
echo "   MongoDB : $(docker exec mongo mongo --quiet --eval 'db.version()')"

echo "-- Pare-feu de la machine : ports 80 et 443 (certaines images, ex. Oracle, bloquent tout sauf SSH)"
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q "Status: active"; then
  ufw allow OpenSSH >/dev/null
  ufw allow "Nginx Full" >/dev/null
  echo "   ufw : SSH, HTTP et HTTPS autorisés"
fi
for port in 80 443; do
  iptables -C INPUT -p tcp --dport "$port" -m conntrack --ctstate NEW -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 1 -p tcp --dport "$port" -m conntrack --ctstate NEW -j ACCEPT
done
netfilter-persistent save

echo "-- Configuration (efdl.env, mail.properties) : modèles si absents"
[ -f "$APP/efdl.env" ] || { sed "s#__ORIGINS__#$ORIGINS#g" "$HERE/efdl.env.example" > "$APP/efdl.env"; echo "   $APP/efdl.env créé : À COMPLÉTER"; }
[ -f "$APP/mail.properties" ] || { sed "s#^efdl.mail.app-url=.*#efdl.mail.app-url=https://$DOMAIN#" "$HERE/mail.properties.example" > "$APP/mail.properties"; echo "   $APP/mail.properties créé : À COMPLÉTER"; }
chown root:efdl "$APP/efdl.env" "$APP/mail.properties"
chmod 640 "$APP/efdl.env" "$APP/mail.properties"

echo "-- Scripts de mise à jour et de sauvegarde"
install -m 750 "$HERE/update.sh" "$APP/bin/update.sh"
install -m 750 "$HERE/backup.sh" "$APP/bin/backup.sh"
install -m 750 "$HERE/import-dump.sh" "$APP/bin/import-dump.sh"
cat > /etc/cron.d/efdl-backup <<'CRON'
# Sauvegarde eFDL chaque nuit à 02:30 (base MongoDB et fichiers), 14 jours conservés.
30 2 * * * root /opt/efdl-modern/bin/backup.sh >> /var/log/efdl-backup.log 2>&1
CRON

echo "-- Service systemd"
install -m 644 "$HERE/efdl-modern.service" /etc/systemd/system/efdl-modern.service
systemctl daemon-reload
systemctl enable efdl-modern >/dev/null

echo "-- Nginx ($DOMAINS)"
sed "s/__DOMAIN__/$DOMAINS/g" "$HERE/nginx-efdl.conf" > /etc/nginx/sites-available/efdl-modern
ln -sf /etc/nginx/sites-available/efdl-modern /etc/nginx/sites-enabled/efdl-modern
rm -f /etc/nginx/sites-enabled/default
[ -f "$WEB/index.html" ] || echo '<!doctype html><title>eFDL</title><p>eFDL : installation en cours.</p>' > "$WEB/index.html"
nginx -t && systemctl reload nginx

cat <<EOF

== Installation terminée ==
Étapes suivantes (voir deploy/README.md) :
  1. Compléter $APP/efdl.env et $APP/mail.properties
  2. Faire pointer $DOMAINS vers l'IP de cette machine, puis :  sudo certbot --nginx $CERTBOT_ARGS--redirect
  3. Importer les données :  sudo $APP/bin/import-dump.sh <archive>
  4. Livrer l'application depuis le PC :  deploy/pc/deploy.ps1
EOF
