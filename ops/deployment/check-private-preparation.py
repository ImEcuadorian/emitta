"""Print only classified startup failures; never dump private process logs."""
import pathlib
import re
import argparse
parser = argparse.ArgumentParser()
parser.add_argument('--log', default='signing-20261012.log')
args = parser.parse_args()
directory = pathlib.Path.home() / 'emitta-deploy/operator/manual-reports'
path = (directory / args.log).resolve()
if not path.is_relative_to(directory.resolve()):
    raise SystemExit('Invalid log path')
text = path.read_text() if path.exists() else ''
print('Log exists=' + str(path.exists()) + '; lines=' + str(len(text.splitlines())))
for name, pattern in {
    'Missing compose': 'no configuration file', 'Doppler problem': 'Doppler Error',
    'Missing environment': 'required', 'Java started': 'Spring Boot',
    'Missing image': 'No such image', 'Permission problem': 'permission denied',
}.items():
    print(name + '=' + str(pattern.lower() in text.lower()))
for variable in sorted(set(re.findall(r'\b[A-Z][A-Z_]{3,}\b', text))):
    if variable.startswith(('EMITTA_', 'DB_', 'RABBITMQ_', 'SPRING_')):
        print('Referenced configuration key=' + variable)
for exception in sorted(set(re.findall(r'\b(?:[a-zA-Z]+\.)+[A-Za-z]+Exception\b', text))):
    print('Exception type=' + exception)
for message in (
    'Preparation did not reach SIGNED', 'Only this tenant', 'Taxpayer does not match',
    'Report requires', 'Manual preparation requires', 'Automatic processing and SRI',
    'Signing and durable artifact storage', 'PKIX path building failed',
    'Connection refused', 'AccessDenied', 'Signature verification',
):
    if message in text:
        print('Recognized failure=' + message)
