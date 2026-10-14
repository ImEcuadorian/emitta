"""Check rejection paths against public CA/CRL data only; no certificate import or fiscal writes."""
import hashlib
import importlib.util
import json
import pathlib
import unittest
from unittest.mock import patch
import urllib.error

directory = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("renewal", directory / "renew-anf-trust.py")
renewal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renewal)
sources = json.loads((directory / "anf-trust-sources.json").read_text())
trust = directory / "certificate-trust"
public = {url: renewal.fetch(url) for name, url in sources.items() if name != "rootSha256"}

class PublicTrustRenewalTest(unittest.TestCase):
    def setUp(self):
        self.before = hashlib.sha256((trust / "revocation.pem").read_bytes()).digest()
    def tearDown(self):
        self.assertEqual(self.before, hashlib.sha256((trust / "revocation.pem").read_bytes()).digest())
    def test_valid_official_data(self):
        with patch.object(renewal, "fetch", side_effect=lambda url: public[url]):
            content, expires = renewal.validate(trust, sources)
            self.assertIn(b"BEGIN X509 CRL", content)
            self.assertGreater(expires, renewal.dt.datetime.now(renewal.dt.timezone.utc))
    def test_root_change_rejected(self):
        with patch.object(renewal, "fetch", side_effect=lambda url: public[url]):
            with self.assertRaises(ValueError):
                renewal.validate(trust, {**sources, "rootSha256": "0" * 64})
    def test_tampered_crl_rejected(self):
        altered = dict(public)
        original = altered[sources["crl"]]
        altered[sources["crl"]] = original[:-1] + bytes([original[-1] ^ 1])
        with patch.object(renewal, "fetch", side_effect=lambda url: altered[url]):
            with self.assertRaises(ValueError):
                renewal.validate(trust, sources)
    def test_download_failure_preserves_bundle(self):
        with patch.object(renewal, "fetch", side_effect=urllib.error.URLError("synthetic unavailable")):
            with self.assertRaises(urllib.error.URLError):
                renewal.validate(trust, sources)

unittest.main()
