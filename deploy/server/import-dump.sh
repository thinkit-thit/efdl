#!/usr/bin/env bash
# Importe une archive de données (dossier dump/ créé par mongodump et fichiers fdl/) :
# base MongoDB 4.4 et fichiers /home/data/fdl.
#   sudo /opt/efdl-modern/bin/import-dump.sh /tmp/efdl-export-AAAAMMJJ-HHMM.tgz            (1re fois : base vide)
#   sudo /opt/efdl-modern/bin/import-dump.sh /tmp/efdl-export-...tgz --remplacer            (remplace la base existante)
set -euo pipefail
ARCHIVE="${1:?Usage : import-dump.sh <archive.tgz> [--remplacer]}"
REPLACE="${2:-}"
DB=$(grep -E '^MONGODB_DATABASE=' /opt/efdl-modern/efdl.env | cut -d= -f2)
DB=${DB:-fdl2025}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

tar -xzf "$ARCHIVE" -C "$WORK"
[ -d "$WORK/dump/$DB" ] || { echo "Base « $DB » absente de l'archive (dossiers : $(ls "$WORK/dump" 2>/dev/null))"; exit 1; }

EXISTING=$(docker exec mongo mongo --quiet "$DB" --eval 'db.getCollectionNames().length')
if [ "$EXISTING" != "0" ]; then
  if [ "$REPLACE" != "--remplacer" ]; then
    echo "La base « $DB » contient déjà $EXISTING collection(s). Pour la remplacer : ajoutez --remplacer."
    exit 1
  fi
  echo "ATTENTION : la base « $DB » de CETTE machine va être remplacée par l'archive (une sauvegarde est faite avant)."
  read -r -p "Tapez REMPLACER pour continuer : " ok
  [ "$ok" = "REMPLACER" ] || { echo "Abandon."; exit 1; }
  systemctl stop efdl-modern || true
  /opt/efdl-modern/bin/backup.sh
fi

echo "-- Base MongoDB"
docker cp "$WORK/dump/$DB" mongo:/tmp/restore-$DB
docker exec mongo mongorestore --quiet --db "$DB" --drop /tmp/restore-$DB
docker exec mongo rm -rf /tmp/restore-$DB
echo "   $(docker exec mongo mongo --quiet "$DB" --eval 'db.getCollectionNames().length') collections importées"

echo "-- Fichiers"
if [ -d "$WORK/fdl" ]; then
  mkdir -p /home/data/fdl
  cp -a "$WORK/fdl/." /home/data/fdl/
  chown -R efdl:efdl /home/data/fdl
  echo "   $(du -sh /home/data/fdl | cut -f1) de fichiers"
fi

systemctl start efdl-modern 2>/dev/null || true
echo "Import terminé."
