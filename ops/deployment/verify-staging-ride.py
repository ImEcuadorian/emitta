"""Verify authenticated download against a private manual report. Never transmits to SRI."""
import hashlib
import json
import os
import pathlib
import urllib.error
import urllib.request

folder = pathlib.Path.home() / 'emitta-deploy/operator'
state = json.loads((folder / 'staging-onboarding-state.json').read_text())
report = dict(line.split('=', 1) for line in (folder / 'manual-reports/transmission-20261012.txt').read_text().splitlines())
base = 'https://api-emitta.nexorf.dev'
client = state['integrationClient']
def request(path, token=None, body=None):
    headers = {'User-Agent': 'Emitta-Verified-Ride/1.0'}
    if token: headers['Authorization'] = 'Bearer ' + token
    if body is not None: headers['Content-Type'] = 'application/json'
    try:
        with urllib.request.urlopen(urllib.request.Request(base + path,
                data=None if body is None else json.dumps(body).encode(), headers=headers), timeout=30) as response:
            return response.status, response.headers.get('Content-Type'), response.read()
    except urllib.error.HTTPError as failure:
        return failure.code, None, b''

status, _, raw = request('/api/v1/auth/token', body={'clientId': client['clientId'], 'clientSecret': client['clientSecret']})
if status != 200: raise SystemExit('Authentication failed: HTTP ' + str(status))
token = json.loads(raw)['accessToken']
document_id = report['documentId']
status, _, raw = request('/api/v1/documents/' + document_id, token=token)
if status != 200 or json.loads(raw)['status'] != 'AUTHORIZED': raise SystemExit('Authorized document verification failed')
path = '/api/v1/documents/' + document_id + '/artifacts/ride-pdf'
status, content_type, raw = request(path, token=token)
if status != 200 or content_type != 'application/pdf' or not raw.startswith(b'%PDF-'):
    raise SystemExit('Protected PDF download failed: HTTP ' + str(status))
sha = hashlib.sha256(raw).hexdigest()
if sha != report['pdfSha256']: raise SystemExit('PDF digest differs from durable artifact')
target = folder / 'manual-reports/api-downloaded-ride.pdf'
with os.fdopen(os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as output:
    output.write(raw)
anonymous, _, _ = request(path)
if anonymous not in (401, 403): raise SystemExit('Anonymous artifact access must be rejected')
print('Document=AUTHORIZED; protected PDF download=HTTP 200; contentType=' + content_type)
print('PDF bytes=' + str(len(raw)) + '; sha256=' + sha)
print('Anonymous PDF request=HTTP ' + str(anonymous) + '; no SRI calls')
