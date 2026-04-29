#!/usr/bin/env python3
import argparse
import concurrent.futures
import json
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from typing import Any, Dict, List, Optional

MONEY_QUANT = Decimal("0.01")


@dataclass
class BotResult:
    bot_id: int
    attempt: int
    username: str
    order_number: Optional[str]
    status: str
    reason: str
    payment_reference: Optional[str] = None


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
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                payload = resp.read().decode("utf-8")
                if not payload:
                    return None
                return json.loads(payload)
        except urllib.error.HTTPError as exc:
            payload = exc.read().decode("utf-8", errors="replace")
            detail = payload
            try:
                parsed = json.loads(payload)
                detail = parsed.get("message") or parsed.get("error") or payload
            except Exception:
                pass
            raise RuntimeError(f"HTTP {exc.code} {method} {path}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"{method} {path} failed: {exc.reason}") from exc


class RaceRunner:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.public_api = ApiClient(args.base_url, timeout=args.timeout)
        self.usernames = [value.strip() for value in args.usernames.split(",") if value.strip()]
        if not self.usernames:
            raise RuntimeError("No usernames configured for the race.")
        self.item = self.bootstrap_item()
        self.sessions = self.bootstrap_sessions()
        self.unit_price = Decimal(str(self.item["unitPrice"]))
        self.shipping_amount = Decimal(str(args.shipping_amount))
        self.tax_amount = Decimal(str(args.tax_amount))
        self.discount_amount = Decimal(str(args.discount_amount))
        self.print_lock = threading.Lock()

    def bootstrap_item(self) -> Dict[str, Any]:
        bootstrap_token = self.authenticate(self.usernames[0], self.args.password)
        bootstrap_api = ApiClient(self.args.base_url, token=bootstrap_token, timeout=self.args.timeout)
        return bootstrap_api.request("GET", f"/api/v1/shopping/items/sku/{urllib.parse.quote(self.args.sku)}")

    def bootstrap_sessions(self) -> List[Dict[str, Any]]:
        sessions = []
        for username in self.usernames:
            token = self.authenticate(username, self.args.password)
            api = ApiClient(self.args.base_url, token=token, timeout=self.args.timeout)
            account = api.request("GET", "/api/v1/shopping/accounts/me")
            sessions.append({
                "username": username,
                "api": api,
                "account": account,
                "address": self.select_address(account),
                "payment_method": self.select_payment_method(account, self.args.payment_method),
            })
        return sessions

    def authenticate(self, username: str, password: str) -> str:
        payload = {"accountOrEmail": username, "password": password}
        response = self.public_api.request("POST", "/api/v1/auth/signin", payload)
        token = response.get("accessToken") or response.get("access_token") or response.get("token")
        if not token:
            raise RuntimeError(f"Signin succeeded but no token was returned: {response}")
        return token

    def select_address(self, account: Dict[str, Any]) -> Dict[str, Any]:
        addresses = account.get("addresses") or []
        if not addresses:
            raise RuntimeError("The signed-in account has no saved address.")
        default = next((a for a in addresses if a.get("defaultAddress")), addresses[0])
        return {
            "recipientName": default.get("recipientName") or account.get("fullName") or account.get("username"),
            "addressLine1": default.get("addressLine1"),
            "addressLine2": default.get("addressLine2"),
            "city": default.get("city"),
            "state": default.get("state"),
            "postalCode": default.get("postalCode"),
            "country": default.get("country") or "US",
            "phoneNumber": account.get("phoneNumber"),
        }

    def select_payment_method(self, account: Dict[str, Any], override: Optional[str]) -> str:
        if override:
            return override
        methods = account.get("paymentMethods") or []
        if not methods:
            return "CREDIT_CARD"
        default = next((m for m in methods if m.get("defaultMethod")), methods[0])
        return default.get("paymentMethodType") or "CREDIT_CARD"

    def session_for_bot(self, bot_id: int) -> Dict[str, Any]:
        return self.sessions[(bot_id - 1) % len(self.sessions)]

    def build_order_payload(self, bot_id: int, attempt: int, session: Dict[str, Any]) -> Dict[str, Any]:
        qty = self.args.quantity
        line_total = (self.unit_price * qty).quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)
        item_id = f"{self.args.sku}::{self.args.fulfillment}"
        shipping = self.shipping_amount if self.args.fulfillment == "SHIPPING" else Decimal("0.00")
        return {
            "customerId": session["account"]["id"],
            "currencyCode": self.item.get("currencyCode") or "USD",
            "taxAmount": f"{self.tax_amount.quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)}",
            "shippingAmount": f"{shipping.quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)}",
            "discountAmount": f"{self.discount_amount.quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)}",
            "shippingAddress": session["address"] if self.args.fulfillment == "SHIPPING" else None,
            "billingAddress": session["address"],
            "paymentMethod": session["payment_method"],
            "items": [{
                "itemId": item_id,
                "sku": self.args.sku,
                "itemName": self.item.get("itemName"),
                "upc": self.item.get("upc"),
                "quantity": qty,
                "unitPrice": f"{self.unit_price.quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)}",
                "lineTotal": f"{line_total}",
            }],
            "createRequestId": f"buy-race-{session['username']}-{bot_id}-try-{attempt}-{uuid.uuid4().hex}",
        }

    def submit_and_wait(self, bot_id: int, attempt: int) -> BotResult:
        session = self.session_for_bot(bot_id)
        try:
            order = session["api"].request("POST", "/api/v1/shopping/orders", self.build_order_payload(bot_id, attempt, session))
            order_number = order.get("orderNumber")
            deadline = time.time() + self.args.poll_timeout
            last_status = order.get("status", "UNKNOWN")
            payment_reference = order.get("paymentReference")
            while time.time() < deadline:
                current = session["api"].request("GET", f"/api/v1/shopping/orders/{urllib.parse.quote(order_number)}")
                last_status = current.get("status", last_status)
                payment_reference = current.get("paymentReference") or payment_reference
                if last_status in {"PAID", "FAILED"}:
                    reason = current.get("statusReason") or last_status
                    return BotResult(bot_id, attempt, session["username"], order_number, last_status, reason, payment_reference)
                time.sleep(self.args.poll_interval)
            return BotResult(bot_id, attempt, session["username"], order_number, "TIMEOUT", f"Timed out waiting for final status, last status={last_status}", payment_reference)
        except Exception as exc:
            return BotResult(bot_id, attempt, session["username"], None, "ERROR", str(exc))

    def run_bot(self, bot_id: int) -> List[BotResult]:
        results: List[BotResult] = []
        for attempt in range(1, self.args.attempts + 1):
            result = self.submit_and_wait(bot_id, attempt)
            results.append(result)
            if result.status in {"PAID", "FAILED"}:
                break
        return results

    def print_banner(self) -> None:
        inventory = (self.item.get("inventory") or {}).get("availableQuantity")
        print(f"Base URL      : {self.args.base_url}")
        print(f"Users         : {', '.join(self.usernames)}")
        print(f"SKU           : {self.args.sku}")
        print(f"Item          : {self.item.get('itemName')}")
        print(f"Available qty : {inventory}")
        print(f"Bots          : {self.args.bots}")
        print(f"Attempts/bot  : {self.args.attempts}")
        print(f"Workers       : {self.args.workers}")
        print(f"Quantity each : {self.args.quantity}")
        print(f"Fulfillment   : {self.args.fulfillment}")
        print(f"Payment method: per-user default")
        print()
        if inventory is not None and inventory >= 50:
            print("Warning: selected item has 50 or more units available. This run may not trigger stock exhaustion.")
            print()

    def run(self) -> int:
        self.print_banner()
        started = time.time()
        results: List[BotResult] = []
        completed_bots = 0
        paid_bots = 0
        failed_bots = 0
        timeout_bots = 0
        error_bots = 0
        with concurrent.futures.ThreadPoolExecutor(max_workers=self.args.workers) as executor:
            future_map = {executor.submit(self.run_bot, bot_id): bot_id for bot_id in range(1, self.args.bots + 1)}
            for future in concurrent.futures.as_completed(future_map):
                bot_results = future.result()
                results.extend(bot_results)
                completed_bots += 1
                final_result = bot_results[-1]
                if final_result.status == "PAID":
                    paid_bots += 1
                elif final_result.status == "FAILED":
                    failed_bots += 1
                elif final_result.status == "TIMEOUT":
                    timeout_bots += 1
                elif final_result.status == "ERROR":
                    error_bots += 1
                with self.print_lock:
                    for result in bot_results:
                        print(f"bot-{result.bot_id:03d}/try-{result.attempt} user={result.username:<12} -> {result.status:<7} order={result.order_number or '-':<14} payment={result.payment_reference or '-':<14} reason={result.reason}")
                    pending_bots = self.args.bots - completed_bots
                    print(
                        f"progress: completed={completed_bots}/{self.args.bots} "
                        f"paid={paid_bots} failed={failed_bots} timeout={timeout_bots} "
                        f"error={error_bots} pending={pending_bots}"
                    )
        elapsed = time.time() - started
        self.print_summary(results, elapsed)
        return 0

    def print_summary(self, results: List[BotResult], elapsed: float) -> None:
        successes = [r for r in results if r.status == "PAID"]
        failures = [r for r in results if r.status == "FAILED"]
        errors = [r for r in results if r.status == "ERROR"]
        timeouts = [r for r in results if r.status == "TIMEOUT"]

        print()
        print("Summary")
        print("-------")
        print(f"Elapsed seconds : {elapsed:.2f}")
        print(f"Succeeded       : {len(successes)}")
        print(f"Failed          : {len(failures)}")
        print(f"Timed out       : {len(timeouts)}")
        print(f"Errored         : {len(errors)}")
        print()

        def print_group(title: str, group: List[BotResult]) -> None:
            print(title)
            if not group:
                print("  none")
                return
            for result in sorted(group, key=lambda r: (r.bot_id, r.attempt)):
                print(f"  bot-{result.bot_id:03d}/try-{result.attempt} user={result.username} order={result.order_number or '-'} payment={result.payment_reference or '-'} reason={result.reason}")
            print()

        print_group("Succeeded bots", successes)
        print_group("Failed bots", failures)
        print_group("Timed out bots", timeouts)
        print_group("Errored bots", errors)


def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fire a high-concurrency purchase race against the shopping services.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Gateway base URL")
    parser.add_argument("--usernames", default="load-user-1,load-user-2,load-user-3,load-user-4,load-user-5,load-user-6,load-user-7,load-user-8,load-user-9,load-user-10", help="Comma-separated usernames rotated across bots")
    parser.add_argument("--password", default="Passw0rd!", help="Signin password")
    parser.add_argument("--sku", default="SKU-1008", help="Item SKU to buy; default has stock below 50")
    parser.add_argument("--bots", type=int, default=150, help="Number of bots to simulate")
    parser.add_argument("--attempts", type=int, default=2, help="Purchase attempts per bot")
    parser.add_argument("--workers", type=int, default=150, help="Maximum concurrent workers")
    parser.add_argument("--quantity", type=int, default=1, help="Quantity per order")
    parser.add_argument("--fulfillment", choices=["SHIPPING", "PICKUP"], default="SHIPPING")
    parser.add_argument("--payment-method", choices=["CREDIT_CARD", "DEBIT_CARD", "WALLET", "GIFT_CARD"], default=None)
    parser.add_argument("--shipping-amount", default="0.00")
    parser.add_argument("--tax-amount", default="0.00")
    parser.add_argument("--discount-amount", default="0.00")
    parser.add_argument("--poll-interval", type=float, default=0.5, help="Seconds between order status polls")
    parser.add_argument("--poll-timeout", type=float, default=120.0, help="Max seconds to wait for each order result")
    parser.add_argument("--timeout", type=float, default=15.0, help="HTTP timeout per request")
    return parser.parse_args(argv)


def main(argv: List[str]) -> int:
    args = parse_args(argv)
    try:
        return RaceRunner(args).run()
    except KeyboardInterrupt:
        print("Interrupted", file=sys.stderr)
        return 130
    except Exception as exc:
        print(f"Fatal: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
