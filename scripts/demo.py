"""Run a real HTTP workflow. Python standard library only; no tokens are printed."""

import argparse
import json
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import uuid4


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8000")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    token = None

    def call(method: str, path: str, body: dict | None = None, expected: int = 200) -> dict:
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = Request(
            base + path, json.dumps(body).encode() if body else None, headers=headers, method=method
        )
        try:
            with urlopen(request, timeout=10) as response:
                status, data = response.status, response.read()
        except HTTPError as error:
            status, data = error.code, error.read()
        if status != expected:
            raise RuntimeError(f"{method} {path}: expected {expected}, got {status}")
        print(f"{status} {method} {path}")
        return json.loads(data) if data else {}

    credentials = {"email": f"demo-{uuid4().hex[:12]}@example.com", "password": "DemoPassword42!"}
    call("GET", "/health/ready")
    call("POST", "/api/v1/auth/register", credentials, 201)
    token = call("POST", "/api/v1/auth/token", credentials)["access_token"]
    project = call("POST", "/api/v1/projects", {"name": "Ship a reliable API"}, 201)
    tasks = f"/api/v1/projects/{project['id']}/tasks"
    task = call("POST", tasks, {"title": "Review the architecture"}, 201)
    path = f"{tasks}/{task['id']}"
    owner_token = token
    outsider = {
        "email": f"outsider-{uuid4().hex[:12]}@example.com",
        "password": "DemoPassword42!",
    }
    call("POST", "/api/v1/auth/register", outsider, 201)
    token = call("POST", "/api/v1/auth/token", outsider)["access_token"]
    call("GET", f"/api/v1/projects/{project['id']}", expected=404)
    call("GET", path, expected=404)
    token = owner_token
    call("PATCH", path, {"status": "done", "expected_version": 1}, 422)
    call("PATCH", path, {"status": "in_progress", "expected_version": 1})
    call("PATCH", path, {"title": "A stale edit", "expected_version": 1}, 409)
    call("PATCH", path, {"status": "done", "expected_version": 2})
    events = call("GET", path + "/events")
    assert [e["to_status"] for e in events["items"]] == ["todo", "in_progress", "done"]
    call("DELETE", f"/api/v1/projects/{project['id']}", expected=204)
    print("Workflow passed: auth, ownership, transitions, conflict detection, audit, and cleanup.")


if __name__ == "__main__":
    main()
