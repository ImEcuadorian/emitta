"""Explicit staging smoke test: creates an empty synthetic tenant/client, never fiscal data."""
import argparse
import json
import pathlib
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("--base-url", default="https://api-emitta.nexorf.dev")
parser.add_argument("--operator-file", required=True)
args = parser.parse_args()
if args.base_url != "https://api-emitta.nexorf.dev":
    raise SystemExit("This staging check is restricted to the approved API hostname")
credentials = dict(line.split("=", 1) for line in pathlib.Path(args.operator_file).read_text().splitlines())


def request(path, payload=None, token=None):
    headers = {"Content-Type": "application/json", "User-Agent": "Emitta-Operational-Check/1.0"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(args.base_url + path, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            body = response.read()
            return response.status, json.loads(body) if body else None, dict(response.headers)
    except urllib.error.HTTPError as error:
        # Never print request/response bodies, tokens or secret credentials.
        return error.code, None, dict(error.headers)


status, body, _ = request("/api/v1/auth/token", {"clientId": credentials["clientId"], "clientSecret": credentials["clientSecret"]})
assert status == 200, f"Operator authentication failed (HTTP {status})"
operator = body["accessToken"]
status, tenant, _ = request("/api/v1/tenants", {"name": "Synthetic administrative smoke " + str(uuid.uuid4())}, operator)
assert status == 201, "Tenant creation failed"
path = "/api/v1/tenants/" + tenant["id"] + "/api-clients"
status, customer, headers = request(path, {"name": "Synthetic staging smoke", "scopes": ["documents:read"]}, operator)
assert status == 201 and headers.get("Cache-Control") == "no-store", "Client issuance failed"
status, token, _ = request("/api/v1/auth/token", {"clientId": customer["clientId"], "clientSecret": customer["clientSecret"]})
assert status == 200, "Customer authentication failed"
status, _, _ = request("/api/v1/tenants", {"name": "Forbidden escalation"}, token["accessToken"])
assert status == 403, "Customer privilege isolation failed"
status, rotated, _ = request(path + "/" + customer["id"] + "/rotate-secret", {}, operator)
assert status == 200, "Rotation failed"
status, _, _ = request("/api/v1/auth/token", {"clientId": customer["clientId"], "clientSecret": customer["clientSecret"]})
assert status == 401, "Old secret still authenticates"
status, token, _ = request("/api/v1/auth/token", {"clientId": rotated["clientId"], "clientSecret": rotated["clientSecret"]})
assert status == 200, "Rotated secret failed"
status, _, _ = request(path + "/" + customer["id"] + "/revoke", {}, operator)
assert status == 204, "Revocation failed"
status, _, _ = request("/api/v1/documents/" + str(uuid.uuid4()), token=token["accessToken"])
assert status == 401, "Revoked JWT still accepted"
print("PASS: HTTPS operator/customer authentication, creation, no-store, privilege isolation, rotation and JWT revocation")
print("Synthetic tenant retained; synthetic customer revoked. No fiscal resources, certificates or SRI calls created.")
