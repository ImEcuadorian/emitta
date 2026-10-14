"""Read-only HTTPS panel/API check; secrets stay in memory, output only status/counts."""
import json
import pathlib
import urllib.request
import urllib.error

BASE = "https://api-emitta.nexorf.dev"
credentials = dict(line.split("=", 1) for line in
                   (pathlib.Path.home() / "emitta-deploy/operator/operator.credentials").read_text().splitlines())

def request(path, method="GET", body=None, token=None, origin=None):
    headers = {}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if origin:
        headers["Origin"] = origin
        if method == "OPTIONS":
            headers["Access-Control-Request-Method"] = "POST"
            headers["Access-Control-Request-Headers"] = "authorization,content-type"
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                                 method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        return error.code, b"", error.headers

status, body, _ = request("/api/v1/auth/token", "POST", {"clientId": credentials["clientId"], "clientSecret": credentials["clientSecret"]})
assert status == 200, f"Operator authentication failed (HTTP {status})"
token = json.loads(body)["accessToken"]
status, body, headers = request("/api/v1/tenants?limit=25", token=token)
assert status == 200 and headers.get("Cache-Control") == "no-store"
tenants = json.loads(body)
for tenant in tenants:
    status, body, headers = request("/api/v1/tenants/" + tenant["id"] + "/api-clients?limit=100", token=token)
    assert status == 200 and headers.get("Cache-Control") == "no-store"
    for client in json.loads(body):
        assert set(client) == {"id", "tenantId", "clientId", "name", "status", "scopes"}
assert request("/api/v1/tenants")[0] == 401
status, _, headers = request("/api/v1/auth/token", "OPTIONS", origin="https://emitta.nexorf.dev")
assert status == 200 and headers.get("Access-Control-Allow-Origin") == "https://emitta.nexorf.dev"
assert headers.get("Access-Control-Allow-Credentials") is None
assert request("/api/v1/auth/token", "OPTIONS", origin="https://untrusted.example")[0] == 403
print("PASS HTTPS: operator lists metadata only; anonymous 401; exact web origin allowed; hostile origin 403; no cookies")
print("Tenant page count=" + str(len(tenants)) + "; no fiscal writes or SRI calls")
