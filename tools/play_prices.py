"""Sets the zone B, C and D prices of the Android subscriptions directly through the Google Play
Developer API (androidpublisher v3), with the service account in PLAY_SERVICE_ACCOUNT_JSON.

    python tools/play_prices.py android/play/prices.json            # shows what would change
    python tools/play_prices.py android/play/prices.json --apply    # changes the prices

Only the listed regions of the listed base plans change; everything else (other countries, the
free trial, the base plans themselves) is sent back as Google returned it.
"""
import json
import os
import sys
from decimal import Decimal

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def session():
    info = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    creds = service_account.Credentials.from_service_account_info(info, scopes=[SCOPE])
    creds.refresh(Request())
    print(f"Service account: {info.get('client_email')}")
    s = requests.Session()
    s.headers["Authorization"] = f"Bearer {creds.token}"
    return s


def money(currency, amount):
    value = Decimal(str(amount))
    units = int(value)
    nanos = int(((value - units) * Decimal(1_000_000_000)).to_integral_value())
    return {"currencyCode": currency, "units": str(units), "nanos": nanos}


def show(price):
    if not price:
        return "-"
    return f"{Decimal(price.get('units', '0')) + Decimal(price.get('nanos', 0)) / Decimal(1_000_000_000)} {price.get('currencyCode')}"


def main():
    config = json.load(open(sys.argv[1]))
    apply = "--apply" in sys.argv
    package = config["package"]
    s = session()
    failed = False
    for product in config["products"]:
        pid, bp_id = product["productId"], product["basePlanId"]
        r = s.get(f"{API}/{package}/subscriptions/{pid}")
        if r.status_code != 200:
            print(f"{pid}: cannot read ({r.status_code}) {r.text[:500]}")
            failed = True
            continue
        sub = r.json()
        plan = next((b for b in sub.get("basePlans", []) if b.get("basePlanId") == bp_id), None)
        if plan is None:
            print(f"{pid}: base plan {bp_id} not found")
            failed = True
            continue
        configs = {c["regionCode"]: c for c in plan.get("regionalConfigs", [])}
        changes = 0
        for region, target in product["regions"].items():
            current = configs.get(region)
            if current is None:
                print(f"  {pid}/{bp_id} {region}: region not offered by Google Play, skipped")
                continue
            wanted = money(target["currency"], target["amount"])
            if current.get("price", {}).get("currencyCode") != target["currency"]:
                print(f"  {pid}/{bp_id} {region}: Google uses {current['price'].get('currencyCode')}, not {target['currency']}, skipped")
                continue
            if show(current.get("price")) != show(wanted):
                print(f"  {pid}/{bp_id} {region}: {show(current.get('price'))} -> {show(wanted)}")
                current["price"] = wanted
                changes += 1
        print(f"{pid}/{bp_id}: {changes} price(s) to change")
        if not apply or changes == 0:
            continue
        params = {
            "updateMask": "basePlans",
            "regionsVersion.version": "2022/02",
            "allowMissing": "false",
        }
        r = s.patch(f"{API}/{package}/subscriptions/{pid}", params=params, json=sub)
        if r.status_code == 200:
            print(f"{pid}: prices updated")
        else:
            print(f"{pid}: Google refused ({r.status_code}) {r.text[:1500]}")
            failed = True
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
