"""Renew public ANF revocation lists only. Never changes the reviewed root or touches PKCS#12 material."""
import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import tempfile
import urllib.request
import urllib.parse
from cryptography import x509
from cryptography.hazmat.primitives import serialization

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Trust downloads cannot redirect")

def fetch(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != "crl.anf.es" or parsed.port not in (None, 443) or parsed.username or parsed.query or parsed.fragment:
        raise ValueError("Only reviewed official ANF HTTPS URLs are permitted")
    with urllib.request.build_opener(NoRedirect()).open(url, timeout=30) as response:
        data = response.read(4 * 1024 * 1024 + 1)
        if not data or len(data) > 4 * 1024 * 1024:
            raise ValueError("Invalid public trust file size")
        return data

def validate(directory, sources):
    now = dt.datetime.now(dt.timezone.utc)
    root = x509.load_der_x509_certificate(fetch(sources["root"]))
    root_der = root.public_bytes(serialization.Encoding.DER)
    if hashlib.sha256(root_der).hexdigest() != sources["rootSha256"]:
        raise ValueError("Root fingerprint changed; manual review required")
    current_root = x509.load_pem_x509_certificate((directory / "root.pem").read_bytes())
    if current_root.public_bytes(serialization.Encoding.DER) != root_der:
        raise ValueError("Configured root differs from reviewed trust; no automatic replacement")
    intermediate = x509.load_der_x509_certificate(fetch(sources["intermediate"]))
    root.verify_directly_issued_by(root)
    intermediate.verify_directly_issued_by(root)
    for certificate in (root, intermediate):
        if not certificate.extensions.get_extension_for_class(x509.BasicConstraints).value.ca or not certificate.not_valid_before_utc <= now < certificate.not_valid_after_utc:
            raise ValueError("CA is not currently valid")
    arl = x509.load_der_x509_crl(fetch(sources["arl"]))
    crl = x509.load_der_x509_crl(fetch(sources["crl"]))
    previous = [x509.load_pem_x509_crl(part + b"-----END X509 CRL-----\n")
                for part in (directory / "revocation.pem").read_bytes().split(b"-----END X509 CRL-----") if part.strip()]
    if {item.issuer for item in previous} != {arl.issuer, crl.issuer}:
        raise ValueError("Revocation issuers changed; manual review required")
    for current, issuer in ((arl, root), (crl, intermediate)):
        if current.issuer != issuer.subject or not current.is_signature_valid(issuer.public_key()):
            raise ValueError("Revocation signature or issuer invalid")
        if current.next_update_utc is None or not current.last_update_utc <= now < current.next_update_utc - dt.timedelta(hours=24):
            raise ValueError("Revocation list missing, premature or too close to expiry")
        old = next(item for item in previous if item.issuer == current.issuer)
        if current.last_update_utc < old.last_update_utc:
            raise ValueError("Refusing revocation rollback")
        if current.extensions.get_extension_for_class(x509.CRLNumber).value.crl_number < old.extensions.get_extension_for_class(x509.CRLNumber).value.crl_number:
            raise ValueError("Refusing revocation number rollback")
    if arl.get_revoked_certificate_by_serial_number(intermediate.serial_number) is not None:
        raise ValueError("Intermediate CA revoked")
    return arl.public_bytes(serialization.Encoding.PEM) + crl.public_bytes(serialization.Encoding.PEM), min(arl.next_update_utc, crl.next_update_utc)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=pathlib.Path, required=True)
    parser.add_argument("--sources", type=pathlib.Path, required=True)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    directory = args.directory.resolve(strict=True)
    content, expires = validate(directory, json.loads(args.sources.read_text()))
    if not args.check_only and content != (directory / "revocation.pem").read_bytes():
        archive = directory / "history"
        archive.mkdir(mode=0o700, exist_ok=True)
        original = (directory / "revocation.pem").read_bytes()
        backup = archive / (hashlib.sha256(original).hexdigest() + ".pem")
        if not backup.exists():
            with backup.open("xb") as output:
                output.write(original)
        descriptor, temporary = tempfile.mkstemp(dir=directory, prefix=".revocation-")
        try:
            with os.fdopen(descriptor, "wb") as output:
                output.write(content)
                output.flush()
                os.fsync(output.fileno())
            os.chmod(temporary, 0o644)  # Public CRLs; application is an unprivileged UID.
            os.replace(temporary, directory / "revocation.pem")
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
    print("PASS public ANF trust/CRL validation; checkOnly=" + str(args.check_only) + "; nextUpdate=" + expires.isoformat())

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        raise SystemExit("Trust renewal failed safely: " + type(error).__name__)
