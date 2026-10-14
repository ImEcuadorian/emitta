#!/usr/bin/env bash
# One document, existing staging image/use cases. No SRI or automatic processors.
set -euo pipefail
cd "$HOME/emitta-deploy/api"
test "$(docker inspect --format '{{.Config.Image}}' emitta-staging-api-1)" = emitta-api:20261012-panel1
state="$HOME/emitta-deploy/operator/test-invoice-20261012.json"
test -f "$state"
document_id=$(python3 -c 'import json,sys; s=json.load(open(sys.argv[1])); assert not s["pending"]; print(s["response"]["id"])' "$state")
tenant_id=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["tenantId"])' "$state")
expected_ruc=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["taxpayer"]["ruc"])' "$HOME/emitta-deploy/operator/staging-onboarding-state.json")
report_dir="$HOME/emitta-deploy/operator/manual-reports"
mkdir -p "$report_dir"
chmod 700 "$report_dir"
test ! -e "$report_dir/signed-20261012.txt"
export EMITTA_TRUSTED_CLOUDFLARE_PEERS
EMITTA_TRUSTED_CLOUDFLARE_PEERS=$(docker inspect --format '{{(index .NetworkSettings.Networks "nexorf_public").IPAddress}}' cloudflared)
umask 077
doppler run -- docker compose -f compose.staging.yaml run --rm --no-deps --user "$(id -u):$(id -g)" \
  --name emitta-staging-manual-sign1 --entrypoint java \
  -v "$report_dir:/manual-reports" api -XX:MaxRAMPercentage=70 -jar /app/app.jar \
  --spring.profiles.active=manual-test-preparation --spring.main.web-application-type=none \
  --spring.flyway.enabled=false --emitta.signing.enabled=true \
  --emitta.fiscal.worker.enabled=false --emitta.outbox.publisher.enabled=false \
  --emitta.processing.jobs.enabled=false --emitta.ride.enabled=false \
  --emitta.sri.reception.enabled=false --emitta.sri.authorization.enabled=false \
  --emitta.artifacts.storage.enabled=true --emitta.manual.tenant-id="$tenant_id" \
  --emitta.manual.document-id="$document_id" --emitta.manual.expected-ruc="$expected_ruc" \
  --emitta.manual.acknowledge-provider-unresolved=true \
  --emitta.manual.report-file=/manual-reports/signed-20261012.txt \
  > "$report_dir/signing-20261012-attempt2.log" 2>&1
test -s "$report_dir/signed-20261012.txt"
printf 'One TEST document SIGNED; private verification report retained. No SRI calls.\n'
