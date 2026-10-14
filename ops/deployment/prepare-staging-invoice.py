"""Create one TEST invoice via existing REST, recording the intent before the POST.

Private state contains credentials and stays outside the repository. No SRI calls.
"""
import argparse
import datetime
import json
import os
import pathlib
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--onboarding-state', required=True)
parser.add_argument('--invoice-state', required=True)
parser.add_argument('--expected-ruc', required=True)
parser.add_argument('--certificate-id', required=True)
args = parser.parse_args()
state = json.loads(pathlib.Path(args.onboarding_state).read_text(encoding='utf-8-sig'))
path = pathlib.Path(args.invoice_state)
if not path.parent.is_dir() or path.parent.stat().st_mode & 0o077:
    raise SystemExit('STOP: existing private directory mode 0700 required')
if path.exists():
    raise SystemExit('STOP: review the existing invoice checkpoint; no automatic repeat')
if state['taxpayer']['ruc'] != args.expected_ruc:
    raise SystemExit('STOP: taxpayer identity mismatch')
base = 'https://api-emitta.nexorf.dev'
def request(endpoint, body=None, token=None, idempotency=None):
    headers = {'User-Agent': 'Emitta-One-Test-Invoice/1.0'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if idempotency:
        headers['Idempotency-Key'] = idempotency
    if body is not None:
        headers['Content-Type'] = 'application/json'
    try:
        with urllib.request.urlopen(urllib.request.Request(base + endpoint,
                data=json.dumps(body).encode() if body is not None else None,
                headers=headers), timeout=30) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit('HTTP ' + str(error.code) + '; no automatic retry; preserve checkpoint') from None
    except (OSError, ValueError):
        raise SystemExit('Unknown request outcome; inspect checkpoint and reconcile; no automatic retry') from None

client = state['onboardingClient']
_, admin = request('/api/v1/auth/token', {'clientId': client['clientId'], 'clientSecret': client['clientSecret']})
_, certificates = request('/api/v1/taxpayers/' + state['taxpayer']['id'] + '/certificates', token=admin['accessToken'])
expected = next((c for c in certificates if c['id'] == args.certificate_id and c['status'] == 'ACTIVE'), None)
if expected is None:
    raise SystemExit('STOP: reviewed active certificate not found')
client = state['integrationClient']
_, auth = request('/api/v1/auth/token', {'clientId': client['clientId'], 'clientSecret': client['clientSecret']})
payload = {
    'pointOfIssueId': state['pointOfIssue']['id'], 'environment': 'TEST',
    'issuedAt': datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace('+00:00', 'Z'),
    'buyer': {'identificationType': '07', 'identification': '9999999999999', 'name': 'CONSUMIDOR FINAL'},
    'items': [{'sku': 'TEST-INTEGRATION', 'description': 'Servicio de prueba de integracion - ambiente TEST',
        'quantity': 2, 'unitPrice': 10, 'discount': 2,
        'taxes': [{'taxCode': '2', 'percentageCode': '4', 'rate': 15}]}],
    'payments': [{'paymentMethod': '01', 'total': 20.70}], 'expectedTotal': 20.70,
}
checkpoint = {'pending': True, 'tenantId': state['tenant']['id'], 'certificateId': args.certificate_id,
              'idempotencyKey': 'staging-test-' + str(uuid.uuid4()), 'request': payload}
def save(value, initial=False):
    destination = path if initial else path.with_suffix('.tmp')
    with os.fdopen(os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as file:
        json.dump(value, file); file.flush(); os.fsync(file.fileno())
    if not initial:
        os.replace(destination, path)
save(checkpoint, initial=True)
status, created = request('/api/v1/invoices', payload, auth['accessToken'], checkpoint['idempotencyKey'])
checkpoint['response'] = created; checkpoint['httpStatus'] = status; checkpoint['pending'] = False
save(checkpoint)
print('HTTP=' + str(status))
for key in ('id', 'status', 'total', 'created'):
    print(key + '=' + str(created.get(key)))
print('Private checkpoint retained. One REST invoice creation; no SRI call.')
