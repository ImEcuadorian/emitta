#!/usr/bin/env bash
# Update only dedicated staging after validation. Preserve backup and previous Compose/image.
set -euo pipefail
umask 077
cd "$HOME/emitta-deploy/api"
expected=${1:?Expected image required}
new=${2:?New image required}
[[ "$expected" =~ ^emitta-api:[a-zA-Z0-9-]+$ && "$new" =~ ^emitta-api:[a-zA-Z0-9-]+$ ]]
test "$(docker inspect --format '{{.Config.Image}}' emitta-staging-api-1)" = "$expected"
release="$HOME/emitta-deploy/api/releases/fiscal-administration"
test -s "$release/app.jar"
database=$(docker exec emitta-staging-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "select current_database()"')
test "$database" = emitta_staging
flags=$(docker inspect emitta-staging-api-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E '^EMITTA_(SIGNING|FISCAL_WORKER|OUTBOX_PUBLISHER|PROCESSING_JOBS|RIDE|SRI_RECEPTION|SRI_AUTHORIZATION)_ENABLED=')
test "$(printf '%s\n' "$flags" | wc -l)" = 7
test "$(printf '%s\n' "$flags" | grep -c '=false$')" = 7
mkdir -p backups
chmod 700 backups
backup="backups/before-fiscal-administration-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker exec emitta-staging-postgres-1 sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
test -s "$backup"
docker exec -i emitta-staging-postgres-1 pg_restore --list < "$backup" >/dev/null
previous="compose.before-fiscal-administration-$(date -u +%Y%m%dT%H%M%SZ).yaml"
cp compose.staging.yaml "$previous"
docker build -t "$new" "$release"
export EMITTA_TRUSTED_CLOUDFLARE_PEERS
EMITTA_TRUSTED_CLOUDFLARE_PEERS=$(docker inspect --format '{{(index .NetworkSettings.Networks "nexorf_public").IPAddress}}' cloudflared)
python3 - "$expected" "$new" <<'PY'
import pathlib,sys
path=pathlib.Path('compose.staging.yaml')
text=path.read_text()
needle='image: '+sys.argv[1]
assert text.count(needle)==1, 'Expected image must occur once'
path.write_text(text.replace(needle,'image: '+sys.argv[2]))
PY
restore_image() {
  cp "$previous" compose.staging.yaml
  doppler run -- docker compose -f compose.staging.yaml up -d --no-deps api
  printf 'Previous API image restored. Additive audit migration retained; private backup preserved.\n' >&2
}
trap restore_image ERR
doppler run -- docker compose -f compose.staging.yaml config --quiet
doppler run -- docker compose -f compose.staging.yaml up -d --no-deps api
trap - ERR
printf 'Reviewed staging API started. Private pre-migration backup preserved. Verify health, audit migration and flags.\n'
