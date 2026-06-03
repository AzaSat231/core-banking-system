# PostgreSQL backup and restore

Core Banking and middleware each have a dedicated Postgres container in `docker-compose.yml`.
Backup sidecars (`db-backup`, `middleware-db-backup`) run `scripts/backup_postgres.sh` on a schedule.

## Defaults

| Setting | Default |
|---------|---------|
| Interval | 24h (`BACKUP_INTERVAL_SECONDS=86400`) |
| Retention | 7 days (`BACKUP_RETENTION_DAYS=7`) |
| Core Banking dumps | `./backups/core-banking/` |
| Middleware dumps | `./backups/middleware/` |

## Start backups

```bash
cd core-banking-system
docker compose up -d db middleware-db db-backup middleware-db-backup
```

## Manual dump (host)

```bash
PGPASSWORD=pass123 pg_dump -h localhost -p 5332 -U dbv1 -d dbv1 -Fc -f dbv1-manual.dump
PGPASSWORD=mwpass pg_dump -h localhost -p 5433 -U mwuser -d mwdb -Fc -f mwdb-manual.dump
```

## Restore Core Banking (`dbv1`)

Stop the app first. This overwrites the current database.

```bash
docker compose stop db-backup
docker compose exec db psql -U dbv1 -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'dbv1' AND pid <> pg_backend_pid();"
docker compose exec db dropdb -U dbv1 dbv1
docker compose exec db createdb -U dbv1 dbv1
pg_restore -h localhost -p 5332 -U dbv1 -d dbv1 --no-owner --role=dbv1 ./backups/core-banking/postgres-dbv1-YYYYMMDD-HHMMSS.dump
docker compose start db-backup
```

## Restore middleware (`mwdb`)

Same pattern on port `5433`, user `mwuser`, database `mwdb`, backup dir `./backups/middleware/`.

## DR notes

- Copy `./backups/` off the machine (USB, object storage) — Docker volumes alone are not off-site DR.
- Test restore at least once per release.
- Production: prefer managed Postgres with automated snapshots and PITR.
