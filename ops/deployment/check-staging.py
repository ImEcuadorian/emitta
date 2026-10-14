"""Read-only staging checks; print status and counts, never credentials/payloads."""
import json
import subprocess
import urllib.request
import urllib.error
import sys

def docker(*args):
    return subprocess.check_output(['docker', *args], text=True).strip()

networks = json.loads(docker('inspect', '--format', '{{json .NetworkSettings.Networks}}', 'emitta-staging-api-1'))
address = networks['emitta-staging_data']['IPAddress']
failed = False
for path in ['/actuator/health', '/openapi/emitta-openapi.yaml', '/v3/api-docs', '/api/v1/documents/00000000-0000-0000-0000-000000000000', '/actuator/prometheus']:
    expected = 401 if path.startswith('/api/') or path == '/actuator/prometheus' else 200
    try:
        with urllib.request.urlopen(f'http://{address}:8080{path}', timeout=10) as response:
            print(f'{path}: HTTP {response.status}')
            failed = failed or response.status != expected
            if path == '/actuator/health':
                health = json.load(response)['status']
                print('HEALTH=' + health)
                failed = failed or health != 'UP'
    except urllib.error.HTTPError as failure:
        print(f'{path}: HTTP {failure.code}')
        failed = failed or failure.code != expected
    except urllib.error.URLError:
        print(f'{path}: UNAVAILABLE')
        failed = True

query = '''SELECT 'flyway='||max(version::numeric) FROM emitta.flyway_schema_history WHERE success;
SELECT 'documents='||count(*) FROM emitta.documents;
SELECT 'outbox='||count(*) FROM emitta.outbox_events;
SELECT 'jobs='||count(*) FROM emitta.processing_jobs;
SELECT 'certificates='||count(*) FROM emitta.certificates;'''
query += "\nSELECT 'administrative_audit_events='||count(*) FROM emitta.administrative_audit_events;"
query += "\nSELECT 'audit_actor_recorded='||count(*) FROM emitta.administrative_audit_events WHERE actor_client_id IS NOT NULL;"
command = 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "$1"'
print(docker('exec', 'emitta-staging-postgres-1', 'sh', '-c', command, 'check', query))
for service in ['api', 'postgres', 'redis', 'rabbitmq', 'minio']:
    print(docker('inspect', '--format', '{{.Name}} state={{.State.Status}} ports={{json .HostConfig.PortBindings}}', f'emitta-staging-{service}-1'))
flags = ['EMITTA_SIGNING_ENABLED', 'EMITTA_FISCAL_WORKER_ENABLED', 'EMITTA_OUTBOX_PUBLISHER_ENABLED',
         'EMITTA_PROCESSING_JOBS_ENABLED', 'EMITTA_RIDE_ENABLED', 'EMITTA_SRI_RECEPTION_ENABLED', 'EMITTA_SRI_AUTHORIZATION_ENABLED']
disabled = all(docker('exec', 'emitta-staging-api-1', 'printenv', flag) == 'false' for flag in flags)
print('AUTOMATIC_FISCAL_AND_SRI_DISABLED=' + str(disabled))
failed = failed or not disabled
tunnel_peer = docker('inspect', '--format', '{{(index .NetworkSettings.Networks "nexorf_public").IPAddress}}', 'cloudflared')
matched = bool(tunnel_peer) and docker('exec', 'emitta-staging-api-1', 'printenv', 'EMITTA_TRUSTED_CLOUDFLARE_PEERS') == tunnel_peer
print('TRUSTED_TUNNEL_PEER_MATCH=' + str(matched))
failed = failed or not matched
if '--traffic' in sys.argv:
    from collections import Counter
    outcomes = Counter()
    limited_with_retry = False
    body = json.dumps({'clientId': 'staging-invalid-probe', 'clientSecret': 'synthetic-invalid-value'}).encode()
    for _ in range(25):
        request = urllib.request.Request(f'http://{address}:8080/api/v1/auth/token', data=body,
                                        headers={'Content-Type': 'application/json'}, method='POST')
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                outcomes[response.status] += 1
        except urllib.error.HTTPError as response:
            outcomes[response.code] += 1
            limited_with_retry |= response.code == 429 and response.headers.get('Retry-After') is not None
    print('INVALID_AUTH_PROBE=' + str(dict(outcomes)))
    print('LIMITED_WITH_RETRY_AFTER=' + str(limited_with_retry))
    failed = failed or not limited_with_retry or 200 in outcomes
sys.exit(1 if failed else 0)
