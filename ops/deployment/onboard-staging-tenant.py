"""Explicit REST onboarding from a private input file. No fiscal transmission or secret output."""
import argparse,json,pathlib,os,urllib.request,urllib.error
p=argparse.ArgumentParser();p.add_argument('--input',required=True);p.add_argument('--operator-file',required=True);p.add_argument('--state',required=True);a=p.parse_args()
statepath=pathlib.Path(a.state);statepath.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
os.chmod(statepath.parent,0o700)
config=json.loads(pathlib.Path(a.input).read_text(encoding='utf-8-sig'))
base='https://api-emitta.nexorf.dev'
if statepath.exists():state=json.loads(statepath.read_text())
else:
 with os.fdopen(os.open(statepath,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as f:f.write('{}')
 state={}
if state.get('pending'):raise SystemExit('STOP: previous POST outcome uncertain; inspect persisted data before resuming')
def save():
 tmp=statepath.with_suffix('.tmp');fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as f:json.dump(state,f);f.flush();os.fsync(f.fileno())
 os.replace(tmp,statepath)
def request(path,payload,token=None):
 h={'Content-Type':'application/json','User-Agent':'Emitta-Administrative-Onboarding/1.0'}
 if token:h['Authorization']='Bearer '+token
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=json.dumps(payload).encode(),headers=h),timeout=30) as r:return json.load(r)
 except urllib.error.HTTPError as e:raise SystemExit('STOP HTTP '+str(e.code)+'; no automatic retry')
def post_once(key,path,payload,token):
 if key in state:return state[key]
 state['pending']=key;save()
 result=request(path,payload,token);state[key]=result;del state['pending'];save();return result
credentials=dict(x.split('=',1) for x in pathlib.Path(a.operator_file).read_text().splitlines())
op=request('/api/v1/auth/token',{'clientId':credentials['clientId'],'clientSecret':credentials['clientSecret']})['accessToken']
t=post_once('tenant','/api/v1/tenants',{'name':config['tenantName']},op)
c=post_once('onboardingClient','/api/v1/tenants/'+t['id']+'/api-clients',{'name':'Tenant onboarding','scopes':['taxpayers:write','establishments:write','points-of-issue:write','certificates:write','certificates:read','documents:read','artifacts:read','invoices:write']},op)
token=request('/api/v1/auth/token',{'clientId':c['clientId'],'clientSecret':c['clientSecret']})['accessToken']
tax=post_once('taxpayer','/api/v1/tenants/'+t['id']+'/taxpayers',config['taxpayer'],token)
est=post_once('establishment','/api/v1/taxpayers/'+tax['id']+'/establishments',config['establishment'],token)
point=post_once('pointOfIssue','/api/v1/establishments/'+est['id']+'/points-of-issue',config['pointOfIssue'],token)
post_once('integrationClient','/api/v1/tenants/'+t['id']+'/api-clients',{'name':'Invoice integration','scopes':['invoices:write','documents:read','artifacts:read']},op)
for k in ['tenant','taxpayer','establishment','pointOfIssue']:print(k+'Id='+state[k]['id'])
print('PASS: REST onboarding; credentials saved privately, TEST taxpayer only. No invoice or SRI call.')
