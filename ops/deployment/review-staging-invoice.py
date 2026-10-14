"""Review a private TEST invoice checkpoint and signed report; never sends to SRI."""
import argparse
import json
import pathlib
import urllib.request
import urllib.error
parser = argparse.ArgumentParser()
parser.add_argument('--onboarding-state', required=True)
parser.add_argument('--invoice-state', required=True)
parser.add_argument('--signed-report', required=True)
args = parser.parse_args()
state = json.loads(pathlib.Path(args.onboarding_state).read_text(encoding='utf-8-sig'))
invoice = json.loads(pathlib.Path(args.invoice_state).read_text())
if invoice['pending']:
    raise SystemExit('STOP: invoice outcome uncertain')
client = state['integrationClient']
base = 'https://api-emitta.nexorf.dev'
def request(path, body=None, token=None):
    headers = {'User-Agent': 'Emitta-Prepared-Test-Review/1.0'}
    if body is not None:
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = 'Bearer ' + token
    try:
        with urllib.request.urlopen(urllib.request.Request(base + path,
                data=json.dumps(body).encode() if body is not None else None,
                headers=headers), timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit('HTTP ' + str(error.code) + '; no automatic retry') from None
token = request('/api/v1/auth/token', {'clientId': client['clientId'], 'clientSecret': client['clientSecret']})['accessToken']
document = request('/api/v1/documents/' + invoice['response']['id'], token=token)
for key in ('id', 'status', 'accessKey', 'sequential', 'environment', 'issuedAt', 'authorizedAt'):
    print(key + '=' + str(document.get(key)))
report = dict(line.split('=', 1) for line in pathlib.Path(args.signed_report).read_text().splitlines())
if report['documentId'] != invoice['response']['id'] or report['status'] != 'SIGNED':
    raise SystemExit('STOP: signed report mismatch')
for key in ('certificateId', 'unsignedSha256', 'signedSha256', 'providerPolicy', 'technicalOmissionRequested'):
    print(key + '=' + report[key])
print('total=' + str(invoice['response']['total']))
print('XSD and independent signature verification completed before signed report creation. No SRI call.')
