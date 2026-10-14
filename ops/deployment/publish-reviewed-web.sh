#!/usr/bin/env bash
# Publish reviewed static files only. Retain the previous web container for rollback.
set -euo pipefail
if docker container inspect emitta-web-rollback-invoice-docs >/dev/null 2>&1; then
    printf 'STOP: rollback container already exists; inspect current release first.\n' >&2
    exit 1
fi
test "$(docker inspect --format '{{.Config.Image}}' emitta-web)" = emitta-web:20261012-invoice-docs
docker image inspect emitta-web:20261012-panel1 >/dev/null
docker stop emitta-web >/dev/null
docker rename emitta-web emitta-web-rollback-invoice-docs
if ! docker run -d --name emitta-web --network nexorf_public --network-alias emitta-web \
    --user 101 --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --cap-drop ALL --security-opt no-new-privileges:true --memory 128m --cpus 0.5 \
    --restart unless-stopped --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
    emitta-web:20261012-panel1 >/dev/null; then
    printf 'New web container failed; previous container retained for recovery.\n' >&2
    exit 1
fi
docker exec emitta-web wget -q -O /dev/null http://127.0.0.1:8080/admin/
printf 'Static admin panel origin responds; previous release retained. Verify public HTTPS separately.\n'
