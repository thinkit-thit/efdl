#!/usr/bin/env bash
# Met en place une nouvelle version livrée par deploy/pc/deploy.ps1 (fichiers déposés dans /tmp/efdl-release).
# Garde la version précédente (retour arrière : sudo /opt/efdl-modern/bin/update.sh --rollback).
set -euo pipefail
APP=/opt/efdl-modern
WEB=/var/www/efdl-modern
REL=/tmp/efdl-release

if [ "${1:-}" = "--rollback" ]; then
  [ -f "$APP/efdl-backend.jar.prev" ] || { echo "Pas de version précédente."; exit 1; }
  mv "$APP/efdl-backend.jar.prev" "$APP/efdl-backend.jar"
  [ -d "$WEB.prev" ] && { rm -rf "$WEB"; mv "$WEB.prev" "$WEB"; }
  systemctl restart efdl-modern
  echo "Version précédente rétablie."
  exit 0
fi

[ -f "$REL/efdl-backend.jar" ] && [ -f "$REL/frontend.tgz" ] || { echo "Livraison introuvable dans $REL"; exit 1; }

echo "-- Backend"
[ -f "$APP/efdl-backend.jar" ] && cp "$APP/efdl-backend.jar" "$APP/efdl-backend.jar.prev"
install -o root -g efdl -m 640 "$REL/efdl-backend.jar" "$APP/efdl-backend.jar"

echo "-- Frontend"
rm -rf "$WEB.new" && mkdir -p "$WEB.new"
tar -xzf "$REL/frontend.tgz" -C "$WEB.new"
[ -f "$WEB.new/index.html" ] || { echo "Frontend incomplet (index.html absent)"; exit 1; }
rm -rf "$WEB.prev"; [ -d "$WEB" ] && mv "$WEB" "$WEB.prev"
mv "$WEB.new" "$WEB"

echo "-- Redémarrage"
systemctl restart efdl-modern
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8081/actuator/health || true)
  [ "$code" = "200" ] && break
  sleep 2
done
if [ "$code" = "200" ]; then
  echo "eFDL démarré (santé : OK)."
  rm -rf "$REL"
else
  echo "ATTENTION : le backend ne répond pas. Journal :  journalctl -u efdl-modern -n 100"
  echo "Retour arrière possible :  sudo $APP/bin/update.sh --rollback"
  exit 1
fi
