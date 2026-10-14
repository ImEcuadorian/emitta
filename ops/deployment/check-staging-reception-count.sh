#!/usr/bin/env bash
set -euo pipefail
document_id=$(python3 -c 'import json,pathlib,uuid; s=json.loads((pathlib.Path.home()/"emitta-deploy/operator/test-invoice-20261012.json").read_text()); assert not s["pending"]; print(uuid.UUID(s["response"]["id"]))')
docker exec -i emitta-staging-postgres-1 sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v document_id="$1"' sh "$document_id" <<'SQL'
BEGIN READ ONLY;
SELECT current_database() AS database;
SELECT count(*) AS reception_attempts FROM emitta.document_attempts WHERE document_id=:'document_id'::uuid AND operation='SRI_SUBMISSION';
SELECT artifact_type,content_type,size_bytes,sha256 FROM emitta.document_artifacts WHERE document_id=:'document_id'::uuid ORDER BY artifact_type;
COMMIT;
SQL
