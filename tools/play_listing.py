"""Writes the Google Play store listing (title, short and full description, and the phone
screenshots when asked) in every language of android/play/listings.json, through the Google Play
Developer API, with the service account in PLAY_SERVICE_ACCOUNT_JSON.

    python tools/play_listing.py android/play/listings.json           # shows what would change
    python tools/play_listing.py android/play/listings.json --apply   # saves it in Play Console
    python tools/play_listing.py android/play/listings.json --screenshots build/play-screenshots --apply

A language that already has a listing in Play Console keeps its texts and its screenshots, unless
--overwrite. --screenshots is the folder written by
tools/play_screenshots.py: <folder>/<language>/*.jpg, sent in the order of their names.

Saved with changesNotSentForReview: the listing is stored in Play Console and nothing is sent for
review or published; that stays a decision taken in Play Console. Google refuses that parameter
for an app that has never been published (a draft): the listing is then saved without it, and
still nothing is published, since a draft has no release in production.
"""
import hashlib
import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
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


def local_screenshots(folder, language):
    """The screenshots of one language: [(name, bytes, sha256)], in the order of their names."""
    path = os.path.join(folder, language)
    if not os.path.isdir(path):
        return []
    shots = []
    for name in sorted(os.listdir(path)):
        if name.lower().endswith((".jpg", ".jpeg", ".png")):
            with open(os.path.join(path, name), "rb") as f:
                data = f.read()
            shots.append((name, data, hashlib.sha256(data).hexdigest()))
    return shots


def main():
    listings = json.load(open(sys.argv[1]))
    apply = "--apply" in sys.argv
    overwrite = "--overwrite" in sys.argv
    folder = sys.argv[sys.argv.index("--screenshots") + 1] if "--screenshots" in sys.argv else None
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
        if old and not overwrite:
            print(f"{language}: texts already in Play Console, kept as they are")
        elif not diff:
            print(f"{language}: texts unchanged")
        else:
            changed += 1
            print(f"{language}: {', '.join(diff)} | {fields['title']} | {fields['shortDescription']}")
            if apply:
                body = {"language": language, **{key: fields[key] for key in LIMITS}}
                r = s.put(f"{base}/listings/{language}", json=body)
                if r.status_code != 200:
                    s.delete(base)
                    sys.exit(f"{language}: Google refused ({r.status_code}) {readable(r)}")

        shots = local_screenshots(folder, language) if folder else []
        if not shots or (old and not overwrite):
            continue  # a language written by hand in Play Console keeps its own screenshots too
        images = f"listings/{language}/phoneScreenshots"
        r = s.get(f"{base}/{images}") if old or apply else None
        online = [image.get("sha256") for image in r.json().get("images", [])] if r is not None and r.status_code == 200 else []
        if online == [sha for _, _, sha in shots]:
            print(f"{language}: screenshots unchanged")
        elif online and not overwrite:
            print(f"{language}: {len(online)} screenshot(s) already in Play Console, kept as they are")
        else:
            changed += 1
            print(f"{language}: {len(shots)} screenshot(s) to send, {len(online)} replaced")
            if apply:
                if online:
                    r = s.delete(f"{base}/{images}")
                    if r.status_code != 200:
                        s.delete(base)
                        sys.exit(f"{language}: screenshots not removed ({r.status_code}) {readable(r)}")
                for name, data, _ in shots:
                    kind = "image/png" if name.lower().endswith(".png") else "image/jpeg"
                    r = s.post(f"{UPLOAD}/{package}/edits/{edit}/{images}", params={"uploadType": "media"},
                               data=data, headers={"Content-Type": kind})
                    if r.status_code != 200:
                        s.delete(base)
                        sys.exit(f"{language}/{name}: Google refused ({r.status_code}) {readable(r)}")

    if not apply or changed == 0:
        s.delete(base)
        print(f"{changed} change(s) to make. Nothing saved.")
        return
    r = s.post(f"{base}:commit", params={"changesNotSentForReview": "true"})
    if r.status_code == 400 and "changesNotSentForReview must not be set" in r.text:
        # A draft app: Google has no review to hold back, the listing is only stored.
        r = s.post(f"{base}:commit")
    if r.status_code != 200:
        sys.exit(f"Saving refused ({r.status_code}) {readable(r)}")
    print(f"{changed} change(s) saved in Play Console. Nothing was published.")


if __name__ == "__main__":
    main()
