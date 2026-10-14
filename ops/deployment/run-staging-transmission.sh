#!/usr/bin/env bash
# One approved reception, followed by one authorization query. Never restart this command to resend.
set -euo pipefail
cd "$HOME/emitta-deploy/api"
test "$(docker inspect --format '{{.Config.Image}}' emitta-staging-api-1)" = emitta-api:20261012-panel1
report_dir="$HOME/emitta-deploy/operator/manual-reports"
test "$(stat -c %a "$report_dir")" = 700
signed_report="$report_dir/signed-20261012.txt"
read_field() { python3 -c 'import sys; s=dict(line.rstrip("\n").split("=",1) for line in open(sys.argv[1]) if "=" in line); print(s[sys.argv[2]])' "$signed_report" "$1"; }
document_id=$(read_field documentId)
access_key=$(read_field accessKey)
signed_sha=$(read_field signedSha256)
certificate_id=$(read_field certificateId)
tenant_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["tenantId"])' "$HOME/emitta-deploy/operator/test-invoice-20261012.json")
expected_ruc=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["taxpayer"]["ruc"])' "$HOME/emitta-deploy/operator/staging-onboarding-state.json")
mode=${1:-submit}
if [[ "$mode" == submit ]]; then
  query_only=false
  report_name=transmission-20261012.txt
elif [[ "$mode" == query ]]; then
  query_only=true
  report_name="authorization-query-$(date -u +%Y%m%dT%H%M%SZ).txt"
else exit 2; fi
test ! -e "$report_dir/$report_name"
export EMITTA_TRUSTED_CLOUDFLARE_PEERS
EMITTA_TRUSTED_CLOUDFLARE_PEERS=$(docker inspect --format '{{(index .NetworkSettings.Networks "nexorf_public").IPAddress}}' cloudflared)
umask 077
override="$report_dir/manual-image.yaml"
printf 'services:\n  api:\n    image: emitta-api:20261012-manualtest\n' > "$override"
doppler run -- docker compose -f compose.staging.yaml -f "$override" run --rm --no-deps \
  --user "$(id -u):$(id -g)" --name emitta-staging-manual-test --entrypoint java \
  -v "$report_dir:/manual-reports" api -XX:MaxRAMPercentage=70 -jar /app/app.jar \
  --spring.profiles.active=manual-test-transmission --spring.main.web-application-type=none \
  --spring.flyway.enabled=false --emitta.signing.enabled=false \
  --emitta.fiscal.worker.enabled=false --emitta.outbox.publisher.enabled=false \
  --emitta.processing.jobs.enabled=false --emitta.ride.enabled=true \
  --emitta.sri.reception.enabled=true --emitta.sri.authorization.enabled=true \
  --emitta.sri.resilience.reception.max-attempts=1 --emitta.sri.resilience.authorization.max-attempts=1 \
  --emitta.sri.reception.test-endpoint=https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline \
  --emitta.sri.reception.production-endpoint=https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline \
  --emitta.sri.authorization.test-endpoint=https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline \
  --emitta.sri.authorization.production-endpoint=https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline \
  --emitta.manual.confirm-one-test-operation=true --emitta.manual.query-only="$query_only" \
  --emitta.manual.tenant-id="$tenant_id" --emitta.manual.document-id="$document_id" \
  --emitta.manual.expected-ruc="$expected_ruc" --emitta.manual.expected-access-key="$access_key" \
  --emitta.manual.expected-signed-sha256="$signed_sha" --emitta.manual.expected-certificate-id="$certificate_id" \
  --emitta.manual.report-file="/manual-reports/$report_name" \
  > "$report_dir/$report_name.log" 2>&1
printf 'Manual TEST operation completed. Private report: %s\n' "$report_name"
