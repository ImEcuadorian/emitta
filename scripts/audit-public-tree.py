"""Offline publication check. Prints locations/categories, never matching secret values.

Scans the complete Git index and untracked, non-ignored files. This targeted check
does not prove the absence of every secret; review the staged diff as well.
"""
import re
import subprocess
from pathlib import Path


def git(*args):
    return subprocess.check_output(["git", *args])


root = Path(git("rev-parse", "--show-toplevel").decode().strip())
indexed = set(git("ls-files", "-z").decode().split("\0")) - {""}
untracked = set(git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0")) - {""}
rules = {
    "private-key-material": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"),
    "access-token": re.compile(rb"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|dp\.st\.[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16})\b"),
    "personal-path": re.compile(rb"(?:[A-Za-z]:[\\/](?:Users|EmittaSecrets)[\\/])", re.I),
    "hardcoded-runtime-secret": re.compile(
        rb"(?:password|clientSecret|secret-key|master-key-b64|private-key-b64)\s*[:=]\s*[\"']([A-Za-z0-9+/=_-]{12,})[\"']", re.I),
}
private_suffixes = {".p12", ".pfx", ".jks", ".keystore", ".pem", ".key", ".cer", ".crt", ".crl", ".der", ".dump", ".backup", ".hprof"}
failures = []
for name in sorted(indexed | untracked):
    path = Path(name)
    if path.suffix.lower() in private_suffixes or path.name == ".env":
        failures.append((name, "private-file"))
        continue
    data = git("show", ":" + name) if name in indexed else (root / path).read_bytes()
    for category, pattern in rules.items():
        # Synthetic credentials are allowed only in test source, not in product/examples.
        if category == "hardcoded-runtime-secret" and name.startswith("src/test/"):
            continue
        candidate = re.sub(rb"\bPASSWORD\s+:'[A-Za-z_][A-Za-z0-9_]*'", b"PASSWORD :variable", data) if category == "hardcoded-runtime-secret" else data
        if pattern.search(candidate):
            failures.append((name, category))
if failures:
    for name, category in failures:
        print(f"FAIL {category}: {name}")
    raise SystemExit(1)
print(f"Publication check passed: {len(indexed)} indexed files and {len(untracked)} untracked files; no configured patterns found.")
