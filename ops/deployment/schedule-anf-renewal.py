"""Install only a daily public-CRL renewal in the deployment user's crontab. No fiscal workers."""
import pathlib
import shlex
import subprocess
import datetime
import os

directory = pathlib.Path.home() / "emitta-deploy/api"
assert (directory / "renew-anf-trust.py").is_file()
result = subprocess.run(["crontab", "-l"], capture_output=True, text=True)
if result.returncode and "no crontab" not in result.stderr.lower():
    raise SystemExit("Unable to read existing crontab; no changes applied")
original = result.stdout
marker = "# emitta-anf-trust-renewal"
command = "17 3 * * * cd " + shlex.quote(str(directory)) + " && /usr/bin/python3 renew-anf-trust.py --directory certificate-trust --sources anf-trust-sources.json > crl-renewal.log 2>&1 " + marker
lines = [line for line in original.splitlines() if not line.endswith(marker)]
updated = "\n".join(lines + [command]) + "\n"
if updated != original:
    backups = directory / "backups"
    backups.mkdir(mode=0o700, exist_ok=True)
    backup = backups / ("crontab-before-crl-" + datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + ".txt")
    with backup.open("x") as output:
        os.chmod(backup, 0o600)
        output.write(original)
    subprocess.run(["crontab", "-"], input=updated, text=True, check=True, capture_output=True)
verified = subprocess.check_output(["crontab", "-l"], text=True)
assert sum(line.endswith(marker) for line in verified.splitlines()) == 1
assert command in verified.splitlines()
print("PASS daily public CRL renewal installed once; previous crontab privately preserved; no fiscal processes scheduled")
