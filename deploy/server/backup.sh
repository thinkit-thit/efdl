#!/usr/bin/env bash
# Sauvegarde nocturne (cron /etc/cron.d/efdl-backup) : base MongoDB (archive compressée) et fichiers.
# Conservées 14 jours dans /home/backup/efdl. À COPIER AUSSI AILLEURS (PC, stockage Oracle) : un serveur gratuit
# n'a aucune garantie.
set -euo pipefail
DEST=/home/backup/efdl
DB=$(grep -E '^MONGODB_DATABASE=' /opt/efdl-modern/efdl.env | cut -d= -f2)
DB=${DB:-fdl2025}
STAMP=$(date +%Y%m%d-%H%M)
mkdir -p "$DEST"

docker exec mongo mongodump --quiet --db "$DB" --archive --gzip > "$DEST/mongo-$DB-$STAMP.archive.gz"
tar -czf "$DEST/fichiers-$STAMP.tgz" -C /home/data fdl fdl-modern

find "$DEST" -type f -mtime +14 -delete
echo "$(date '+%F %T') sauvegarde OK : $(du -sh "$DEST" | cut -f1) au total"
