#!/usr/bin/env bash
# Periodic pg_dump for docker-compose db-backup / middleware-db-backup services.
set -euo pipefail

: "${PGHOST:?}"
: "${PGPORT:?}"
: "${PGUSER:?}"
: "${PGPASSWORD:?}"
: "${PGDATABASE:?}"
: "${BACKUP_DIR:=/backups}"
: "${BACKUP_PREFIX:=postgres}"
: "${BACKUP_RETENTION_DAYS:=7}"

mkdir -p "${BACKUP_DIR}"
stamp="$(date -u +%Y%m%d-%H%M%S)"
out="${BACKUP_DIR}/${BACKUP_PREFIX}-${PGDATABASE}-${stamp}.dump"

echo "[backup] dumping ${PGDATABASE}@${PGHOST}:${PGPORT} -> ${out}"
pg_dump -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -Fc -f "${out}"
echo "[backup] done $(du -h "${out}" | cut -f1)"

if [[ "${BACKUP_RETENTION_DAYS}" =~ ^[0-9]+$ ]] && [[ "${BACKUP_RETENTION_DAYS}" -gt 0 ]]; then
  find "${BACKUP_DIR}" -name "${BACKUP_PREFIX}-${PGDATABASE}-*.dump" -type f -mtime "+${BACKUP_RETENTION_DAYS}" -delete
fi
