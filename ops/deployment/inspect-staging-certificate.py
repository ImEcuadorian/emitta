"""Read certificate metadata using an existing privately held onboarding state."""
import argparse
import json
import pathlib
import urllib.request
import urllib.error

parser = argparse.ArgumentParser()
parser.add_argument('--state', required=True)
args = parser.parse_args()
state = json.loads(pathlib.Path(args.state).read_text(encoding='utf-8-sig'))
base = 'https://api-emitta.nexorf.dev'
client = state['onboardingClient']
def request(path, body=None, token=None):
    headers = {'User-Agent': 'Emitta-Controlled-Staging-Review/1.0'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if body is not None:
        headers['Content-Type'] = 'application/json'
    try:
        with urllib.request.urlopen(urllib.request.Request(base + path,
                data=json.dumps(body).encode() if body is not None else None,
                headers=headers), timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit('HTTP ' + str(error.code) + '; no automatic retry') from None

token = request('/api/v1/auth/token', {
    'clientId': client['clientId'], 'clientSecret': client['clientSecret']})['accessToken']
metadata = request('/api/v1/taxpayers/' + state['taxpayer']['id'] + '/certificates', token=token)
print('tenantId=' + state['tenant']['id'])
print('taxpayerId=' + state['taxpayer']['id'])
print('pointOfIssueId=' + state['pointOfIssue']['id'])
for certificate in metadata:
    print(json.dumps({key: certificate.get(key) for key in
        ('id', 'status', 'validUntil', 'fingerprint')}, ensure_ascii=True))
print('Certificate count=' + str(len(metadata)) + '; metadata only; no password or key decrypted.')
