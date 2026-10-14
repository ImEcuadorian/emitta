#!/usr/bin/env bash
# Run only after local regression checks and transfer of the reviewed JAR. No fiscal processing.
set -euo pipefail
umask 077
cd "$HOME/emitta-deploy/api"
release="$HOME/emitta-deploy/api/releases/api-20261012-panel1"
test -s "$release/app.jar"
test -s "$release/compose.staging.yaml"
test -n "${EMITTA_TRUSTED_CLOUDFLARE_PEERS:-}"
peer=$(docker inspect --format '{{(index .NetworkSettings.Networks "nexorf_public").IPAddress}}' cloudflared)
test "$peer" = "$EMITTA_TRUSTED_CLOUDFLARE_PEERS"
database=$(docker exec emitta-staging-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "select current_database()"')
test "$database" = emitta_staging
mkdir -p backups
backup="backups/before-panel-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker exec emitta-staging-postgres-1 sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
test -s "$backup"
docker exec -i emitta-staging-postgres-1 pg_restore --list < "$backup" >/dev/null
cp compose.staging.yaml compose.before-panel.yaml
docker build -t emitta-api:20261012-panel1 "$release"
docker compose -f "$release/compose.staging.yaml" config --quiet
cp "$release/compose.staging.yaml" compose.staging.yaml
docker compose -f compose.staging.yaml up -d --no-deps api
printf 'Reviewed API image started. Staging backup created; restore has not been tested. Verify health and Flyway before use.\n'
