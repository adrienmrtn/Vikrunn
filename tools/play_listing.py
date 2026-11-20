"""Writes the Google Play store listing (title, short and full description) in every language of
android/play/listings.json, through the Google Play Developer API, with the service account in
PLAY_SERVICE_ACCOUNT_JSON.

    python tools/play_listing.py android/play/listings.json           # shows what would change
    python tools/play_listing.py android/play/listings.json --apply   # saves it in Play Console

Saved with changesNotSentForReview: the listing is stored in Play Console and nothing is sent for
review or published; that stays a decision taken in Play Console.
"""
import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
LIMITS = {"title": 30, "shortDescription": 80, "fullDescription": 4000}


def readable(response):
    """Google's error without JSON punctuation: GitHub masks the lines of the key file ("{", "}")."""
    try:
        error = response.json().get("error", {})
        text = f"{error.get('status', '')} {error.get('message', '')}"
    except ValueError:
        text = response.text
    return text.replace("{", "(").replace("}", ")")[:1500]


def session():
    info = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    creds = service_account.Credentials.from_service_account_info(info, scopes=[SCOPE])
    creds.refresh(Request())
    s = requests.Session()
    s.headers["Authorization"] = f"Bearer {creds.token}"
    return s


def main():
    listings = json.load(open(sys.argv[1]))
    apply = "--apply" in sys.argv
    package = "com.atala.app"
    for language, fields in listings.items():
        for key, limit in LIMITS.items():
            if len(fields[key]) > limit:
                sys.exit(f"{language} {key} is {len(fields[key])} characters, limit {limit}")

    s = session()
    r = s.post(f"{API}/{package}/edits")
    if r.status_code != 200:
        sys.exit(f"Cannot open an edit ({r.status_code}) {readable(r)}")
    edit = r.json()["id"]
    base = f"{API}/{package}/edits/{edit}"

    r = s.get(f"{base}/listings")
    current = {item["language"]: item for item in r.json().get("listings", [])} if r.status_code == 200 else {}
    print(f"Listings in Play Console now: {', '.join(sorted(current)) or 'none'}")

    changed = 0
    for language, fields in sorted(listings.items()):
        old = current.get(language, {})
        diff = [key for key in LIMITS if old.get(key) != fields[key]]
        if not diff:
            print(f"{language}: unchanged")
            continue
        changed += 1
        print(f"{language}: {', '.join(diff)} | {fields['title']} | {fields['shortDescription']}")
        if apply:
            body = {"language": language, **{key: fields[key] for key in LIMITS}}
            r = s.put(f"{base}/listings/{language}", json=body)
            if r.status_code != 200:
                s.delete(base)
                sys.exit(f"{language}: Google refused ({r.status_code}) {readable(r)}")

    if not apply or changed == 0:
        s.delete(base)
        print(f"{changed} language(s) to update. Nothing saved.")
        return
    r = s.post(f"{base}:commit", params={"changesNotSentForReview": "true"})
    if r.status_code != 200:
        sys.exit(f"Saving refused ({r.status_code}) {readable(r)}")
    print(f"{changed} language(s) saved in Play Console, not sent for review.")


if __name__ == "__main__":
    main()
