#!/usr/bin/env bash
# Static web only. Preserve previous and failed containers for recovery.
set -euo pipefail
expected_image=${1:?Expected current image required}
new_image=${2:?New image required}
rollback_name=${3:?Rollback container name required}
[[ "$expected_image" =~ ^emitta-web:[a-zA-Z0-9-]+$ ]]
[[ "$new_image" =~ ^emitta-web:[a-zA-Z0-9-]+$ ]]
[[ "$rollback_name" =~ ^emitta-web-rollback-[a-zA-Z0-9-]+$ ]]
[[ "$expected_image" != "$new_image" ]]
test "$(docker inspect --format '{{.Config.Image}}' emitta-web)" = "$expected_image"
if docker container inspect "$rollback_name" >/dev/null 2>&1; then
  printf 'STOP: named rollback already exists.\n' >&2
  exit 1
fi
docker image inspect "$new_image" >/dev/null
docker stop emitta-web >/dev/null
docker rename emitta-web "$rollback_name"
recover() {
  if docker container inspect emitta-web >/dev/null 2>&1; then
    test "$(docker inspect --format '{{.Config.Image}}' emitta-web)" = "$new_image"
    docker stop emitta-web >/dev/null || true
    docker rename emitta-web "${rollback_name}-failed-new"
  fi
  docker rename "$rollback_name" emitta-web
  docker start emitta-web >/dev/null
  printf 'Previous web recovered; failed new container retained.\n' >&2
}
trap recover ERR
docker run -d --name emitta-web --network nexorf_public --network-alias emitta-web \
  --user 101 --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  --cap-drop ALL --security-opt no-new-privileges:true --memory 128m --cpus 0.5 \
  --restart unless-stopped --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
  "$new_image" >/dev/null
ok=false
for attempt in {1..10}; do
  if docker exec emitta-web wget -q -O /dev/null http://127.0.0.1:8080/admin/; then ok=true; break; fi
  sleep 1
done
$ok
trap - ERR
printf 'Static web published; previous container retained: %s\n' "$rollback_name"
