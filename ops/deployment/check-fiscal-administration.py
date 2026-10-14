"""Read existing staging configuration/documents only. No new clients, fiscal data or SRI calls."""
import hashlib
import json
import pathlib
import urllib.error
import urllib.request

folder=pathlib.Path.home()/'emitta-deploy/operator'
credentials=dict(line.split('=',1) for line in (folder/'operator.credentials').read_text().splitlines())
state=json.loads((folder/'staging-onboarding-state.json').read_text())
invoice=json.loads((folder/'test-invoice-20261012.json').read_text())
base='https://api-emitta.nexorf.dev'
def request(path,token=None,payload=None):
    headers={'User-Agent':'Emitta-Fiscal-Administration-Review/1.0'}
    if token:headers['Authorization']='Bearer '+token
    if payload is not None:headers['Content-Type']='application/json'
    try:
        with urllib.request.urlopen(urllib.request.Request(base+path,headers=headers,
                data=None if payload is None else json.dumps(payload).encode()),timeout=30) as response:
            return response.status,response.read()
    except urllib.error.HTTPError as error:return error.code,b''
status,raw=request('/api/v1/auth/token',payload={'clientId':credentials['clientId'],'clientSecret':credentials['clientSecret']})
assert status==200,'Owner authentication failed'
token=json.loads(raw)['accessToken']
tenant=state['tenant']['id']
status,raw=request('/api/v1/tenants/'+tenant+'/fiscal-configuration',token)
assert status==200,'Fiscal metadata failed'
rows=json.loads(raw)
assert any(row['pointOfIssueId']==state['pointOfIssue']['id'] for row in rows),'Existing point not found'
assert any(row['certificateId'] for row in rows),'Existing certificate not found'
status,raw=request('/api/v1/tenants/'+tenant+'/documents',token)
assert status==200,'Document list failed'
document=next(row for row in json.loads(raw) if row['id']==invoice['response']['id'])
assert document['status']=='AUTHORIZED' and document['pdfAvailable'] and document['xmlAvailable']
status,pdf=request('/api/v1/tenants/'+tenant+'/documents/'+document['id']+'/artifacts/ride-pdf',token)
assert status==200 and pdf.startswith(b'%PDF-'),'PDF download failed'
report=dict(line.split('=',1) for line in (folder/'manual-reports/transmission-20261012.txt').read_text().splitlines())
assert hashlib.sha256(pdf).hexdigest()==report['pdfSha256'],'Original PDF digest differs'
client=state['integrationClient']
status,raw=request('/api/v1/auth/token',payload={'clientId':client['clientId'],'clientSecret':client['clientSecret']})
assert status==200
customer=json.loads(raw)['accessToken']
assert request('/api/v1/tenants/'+tenant+'/fiscal-configuration',customer)[0]==403
assert request('/api/v1/tenants/'+tenant+'/documents',customer)[0]==403
assert request('/api/v1/tenants/'+tenant+'/documents/'+document['id']+'/artifacts/ride-pdf')[0]==401
print('PASS owner: fiscal configuration, existing certificate metadata, document list and original PDF SHA-256')
print('PASS customer cannot use owner routes; anonymous PDF rejected. No fiscal writes or SRI calls.')
