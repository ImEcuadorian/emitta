"""Prepare only dedicated staging bucket using Doppler credentials; no fiscal payloads."""
import os,subprocess,json,hashlib,boto3,urllib.request,urllib.error
from botocore.exceptions import ClientError
n=json.loads(subprocess.check_output(['docker','inspect','--format','{{json .NetworkSettings.Networks}}','emitta-staging-minio-1'],text=True))
ip=n['emitta-staging_data']['IPAddress']
s=boto3.client('s3',endpoint_url='http://'+ip+':9000',region_name='us-east-1',aws_access_key_id=os.environ['EMITTA_ARTIFACT_STORAGE_ACCESS_KEY'],aws_secret_access_key=os.environ['EMITTA_ARTIFACT_STORAGE_SECRET_KEY'])
b='emitta-staging-artifacts'
try:s.head_bucket(Bucket=b)
except ClientError as e:
 if str(e.response['Error']['Code']) not in ['404','NoSuchBucket','NotFound']:raise
 s.create_bucket(Bucket=b)
try:
 s.get_bucket_policy(Bucket=b)
 raise SystemExit('STOP: bucket policy exists; review before storage activation')
except ClientError as e:
 if e.response['Error']['Code']!='NoSuchBucketPolicy':raise
s.put_bucket_versioning(Bucket=b,VersioningConfiguration={'Status':'Enabled'})
k='operational-checks/storage-sha256.txt';body=b'Emitta staging synthetic storage verification\n'
s.put_object(Bucket=b,Key=k,Body=body,ContentType='text/plain')
received=s.get_object(Bucket=b,Key=k)['Body'].read()
assert hashlib.sha256(received).digest()==hashlib.sha256(body).digest()
try:
 with urllib.request.urlopen('http://'+ip+':9000/'+b+'/'+k,timeout=10) as response:
  raise SystemExit('STOP: anonymous object access unexpectedly allowed')
except urllib.error.HTTPError as e:
 if e.code!=403:raise
print('PASS bucket='+b+' private(no bucket policy), versioning enabled, synthetic PUT/GET SHA256 matches')
