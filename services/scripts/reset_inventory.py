#!/usr/bin/env python3
import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, Optional


class ApiClient:
    def __init__(self, base_url: str, token: Optional[str] = None, timeout: float = 15.0):
        self.base_url = base_url.rstrip("/")
        self.token = token
        self.timeout = timeout

    def request(self, method: str, path: str, body: Optional[Dict[str, Any]] = None) -> Any:
        url = f"{self.base_url}{path}"
        headers = {"Accept": "application/json"}
        data = None
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                payload = resp.read().decode("utf-8")
                return json.loads(payload) if payload else None
        except urllib.error.HTTPError as exc:
            payload = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code} {method} {path}: {payload}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"{method} {path} failed: {exc.reason}") from exc


def authenticate(base_url: str, username: str, password: str, timeout: float) -> str:
    client = ApiClient(base_url, timeout=timeout)
    response = client.request("POST", "/api/v1/auth/signin", {"accountOrEmail": username, "password": password})
    token = response.get("accessToken") or response.get("access_token") or response.get("token")
    if not token:
        raise RuntimeError(f"Signin succeeded but no token was returned: {response}")
    return token


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Reset item inventory through the item API.")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--username", default="itest-user")
    parser.add_argument("--password", default="Passw0rd!")
    parser.add_argument("--sku", default="SKU-1008")
    parser.add_argument("--available", type=int, default=18, help="Target available quantity")
    parser.add_argument("--reserved", type=int, default=None, help="Target reserved quantity; defaults to current reserved quantity")
    parser.add_argument("--total", type=int, default=None, help="Target total quantity; defaults to available + reserved")
    parser.add_argument("--reorder-level", type=int, default=None, help="Target reorder level; defaults to current reorder level")
    parser.add_argument("--warehouse-code", default=None, help="Target warehouse code; defaults to current warehouse code")
    parser.add_argument("--timeout", type=float, default=15.0)
    return parser.parse_args(argv)


def main(argv) -> int:
    args = parse_args(argv)
    token = authenticate(args.base_url, args.username, args.password, args.timeout)
    api = ApiClient(args.base_url, token=token, timeout=args.timeout)

    item = api.request("GET", f"/api/v1/shopping/items/sku/{urllib.parse.quote(args.sku)}")
    inventory = item.get("inventory") or {}
    item_id = item.get("id")
    if not item_id:
        raise RuntimeError(f"Item {args.sku} has no id in API response")

    reserved = args.reserved if args.reserved is not None else inventory.get("reservedQuantity", 0)
    total = args.total if args.total is not None else args.available + reserved
    reorder_level = args.reorder_level if args.reorder_level is not None else inventory.get("reorderLevel", 5)
    warehouse_code = args.warehouse_code if args.warehouse_code is not None else inventory.get("warehouseCode", "WH1")

    payload = {
        "totalQuantity": total,
        "availableQuantity": args.available,
        "reservedQuantity": reserved,
        "reorderLevel": reorder_level,
        "warehouseCode": warehouse_code,
        "inStock": args.available > 0,
    }

    updated = api.request("PUT", f"/api/v1/shopping/items/{urllib.parse.quote(item_id)}/inventory", payload)

    print(f"Reset inventory for {args.sku} ({item.get('itemName')})")
    print(json.dumps(updated, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
