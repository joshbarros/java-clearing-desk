#!/usr/bin/env python3
"""Screen, post, hold, return, and contend for one balance."""

import base64
import json
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BOOK = "http://localhost:8131"
SETTLE = "http://localhost:8133"
MGMT = "http://localhost:15678"
AUTH = "platform:platform"
failures = []


def check(name, ok, detail=""):
    if ok:
        print(f"PASS  {name}")
    else:
        print(f"FAIL  {name} {detail}")
        failures.append(f"{name}: {detail}")


def call(method, url, body=None, auth=None, timeout=15):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        request.add_header("content-type", "application/json")
    if auth:
        request.add_header("Authorization", "Basic " + base64.b64encode(auth.encode()).decode())
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as ex:
        raw = ex.read().decode()
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return ex.code, parsed
    except Exception as ex:
        return 0, str(ex)


def wait_ok(url, seconds=180, auth=None):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if call("GET", url, auth=auth)[0] == 200:
            return True
        time.sleep(1)
    return False


def until(label, fn, seconds=50):
    deadline = time.time() + seconds
    last = None
    while time.time() < deadline:
        last = fn()
        if last is not None:
            return last
        time.sleep(0.4)
    check(label, False, f"timed out last={last!r}")
    return None


def depth(name):
    status, body = call("GET", f"{MGMT}/api/queues/%2F/{name}", auth=AUTH)
    if status != 200 or not isinstance(body, dict):
        return None
    return body.get("messages", 0)


def grew(name, before, amount=1):
    def ready():
        now = depth(name)
        if now is not None and now >= before + amount:
            return now
        return None
    return until(f"dlq {name}", ready, 20)


def account(name, balance):
    status, body = call("POST", f"{BOOK}/accounts", {"name": name, "balanceCents": balance})
    if status != 201:
        check(f"open {name}", False, (status, body))
        return None
    return body["id"]


def pay(source, dest, amount, reference):
    return call("POST", f"{BOOK}/transfers", {
        "fromAccountId": source, "toAccountId": dest, "amountCents": amount, "reference": reference,
    })


def await_status(transfer_id, status):
    def ready():
        code, body = call("GET", f"{BOOK}/transfers/{transfer_id}")
        if code == 200 and isinstance(body, dict) and body.get("status") == status:
            return body
        return None
    return until(f"{transfer_id[:8]} {status}", ready)


def main():
    check("book health", wait_ok(f"{BOOK}/actuator/health"))
    check("screening health", wait_ok("http://localhost:8132/actuator/health"))
    check("settlement health", wait_ok(f"{SETTLE}/actuator/health"))
    check("rabbit health", wait_ok(f"{MGMT}/api/overview", auth=AUTH))

    ana = account("Ana", 10000)
    bea = account("Bea", 0)
    status, _ = pay(ana, bea, 0, "invoice")
    check("zero amount is 400", status == 400, status)
    status, _ = pay(ana, ana, 100, "invoice")
    check("same account is 400", status == 400, status)
    status, _ = pay("missing", bea, 100, "invoice")
    check("unknown account is 404", status == 404, status)
    status, denied = pay(ana, bea, 20000, "invoice")
    check("insufficient balance is 409", status == 409, denied)
    _, ana_now = call("GET", f"{BOOK}/accounts/{ana}")
    check("409 leaves the balance", ana_now.get("balanceCents") == 10000, ana_now)

    status, posted = pay(ana, bea, 4000, "invoice")
    check("normal transfer accepted", status == 201, posted)
    check("normal transfer posts", await_status(posted["id"], "POSTED") is not None)
    _, ana_now = call("GET", f"{BOOK}/accounts/{ana}")
    _, bea_now = call("GET", f"{BOOK}/accounts/{bea}")
    code, posting = call("GET", f"{SETTLE}/postings/{posted['id']}")
    check("balances move by 4000", ana_now.get("balanceCents") == 6000 and bea_now.get("balanceCents") == 4000, (ana_now, bea_now))
    check("posting exists", code == 200 and posting.get("status") == "POSTED", posting)

    screen_dlq = depth("clearing.screen.dlq") or 0
    status, held = pay(ana, bea, 100, "please review this")
    check("review transfer accepted", status == 201, held)
    check("review transfer is held", await_status(held["id"], "HELD") is not None)
    _, ana_now = call("GET", f"{BOOK}/accounts/{ana}")
    _, bea_now = call("GET", f"{BOOK}/accounts/{bea}")
    missing, _ = call("GET", f"{SETTLE}/postings/{held['id']}")
    check("held debit stays and destination is unchanged", ana_now.get("balanceCents") == 5900 and bea_now.get("balanceCents") == 4000, (ana_now, bea_now))
    check("held transfer has no posting", missing == 404, missing)
    check("held transfer dead-letters screening", grew("clearing.screen.dlq", screen_dlq) == screen_dlq + 1)

    frozen = account("FROZEN", 0)
    payer = account("Payer", 500)
    settle_dlq = depth("clearing.settle.dlq") or 0
    status, returned = pay(payer, frozen, 100, "invoice")
    check("frozen transfer accepted", status == 201, returned)
    check("frozen transfer returns", await_status(returned["id"], "RETURNED") is not None)
    _, payer_now = call("GET", f"{BOOK}/accounts/{payer}")
    _, frozen_now = call("GET", f"{BOOK}/accounts/{frozen}")
    missing, _ = call("GET", f"{SETTLE}/postings/{returned['id']}")
    check("returned funds are restored", payer_now.get("balanceCents") == 500 and frozen_now.get("balanceCents") == 0, (payer_now, frozen_now))
    check("returned transfer has no posting", missing == 404, missing)
    check("frozen destination dead-letters settlement", grew("clearing.settle.dlq", settle_dlq) == settle_dlq + 1)

    lower = account("frozen", 0)
    sender = account("Sender", 500)
    status, ordinary = pay(sender, lower, 100, "invoice")
    check("lowercase frozen name posts", await_status(ordinary["id"], "POSTED") is not None)
    _, lower_now = call("GET", f"{BOOK}/accounts/{lower}")
    check("lowercase account received the credit", lower_now.get("balanceCents") == 100, lower_now)

    rich = account("Rich", 2_000_000)
    sink = account("Sink", 0)
    status, line = pay(rich, sink, 500_000, "invoice")
    check("500000 cents posts", await_status(line["id"], "POSTED") is not None)
    screen_dlq = depth("clearing.screen.dlq") or 0
    status, over = pay(rich, sink, 500_001, "invoice")
    check("500001 cents is held", await_status(over["id"], "HELD") is not None)
    _, sink_now = call("GET", f"{BOOK}/accounts/{sink}")
    check("held boundary does not credit the destination", sink_now.get("balanceCents") == 500_000, sink_now)
    check("boundary hold dead-letters", grew("clearing.screen.dlq", screen_dlq) == screen_dlq + 1)

    racer = account("Racer", 50)
    pool = account("Pool", 0)

    def one(_):
        return pay(racer, pool, 10, "invoice")

    with ThreadPoolExecutor(max_workers=10) as pool_exec:
        raced = list(pool_exec.map(one, range(10)))
    accepted = [body["id"] for code, body in raced if code == 201 and isinstance(body, dict)]
    rejected = [code for code, _ in raced].count(409)
    check("exactly five overlapping transfers are accepted", len(accepted) == 5 and rejected == 5, [code for code, _ in raced])
    posted_n = 0
    for transfer_id in accepted:
        if await_status(transfer_id, "POSTED"):
            posted_n += 1
    _, racer_now = call("GET", f"{BOOK}/accounts/{racer}")
    _, pool_now = call("GET", f"{BOOK}/accounts/{pool}")
    check("overlapping transfers post five and spend fifty", posted_n == 5 and racer_now.get("balanceCents") == 0 and pool_now.get("balanceCents") == 50, (posted_n, racer_now, pool_now))

    print("\n== summary ==")
    if failures:
        print(f"{len(failures)} failed")
        for item in failures:
            print(f" - {item}")
        sys.exit(1)
    print("clearing desk smoke passed")


if __name__ == "__main__":
    main()
