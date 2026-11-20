"""Sends a signed Android App Bundle to a Google Play testing track, through the Google Play
Developer API, with the service account in PLAY_SERVICE_ACCOUNT_JSON.

    python tools/play_upload.py Atala-3.aab internal           # uploads and checks, saves nothing
    python tools/play_upload.py Atala-3.aab internal --apply   # rolls it out to the track's testers

Only testing tracks (internal, alpha = closed testing): production stays a decision taken in Play
Console. The track's previous release is replaced by this bundle.
"""
import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
PACKAGE = "com.atala.app"
TESTING_TRACKS = {"internal", "alpha"}


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
    bundle, track = sys.argv[1], sys.argv[2]
    apply = "--apply" in sys.argv
    if track not in TESTING_TRACKS:
        sys.exit(f"Track {track} refused: only {sorted(TESTING_TRACKS)}")

    s = session()
    r = s.post(f"{API}/{PACKAGE}/edits")
    if r.status_code != 200:
        sys.exit(f"Cannot open an edit ({r.status_code}) {readable(r)}")
    edit = r.json()["id"]
    base = f"{API}/{PACKAGE}/edits/{edit}"

    def give_up(message):
        s.delete(base)
        sys.exit(message)

    print(f"Uploading {bundle} ({os.path.getsize(bundle) / 1e6:.1f} MB)")
    with open(bundle, "rb") as f:
        r = s.post(
            f"{UPLOAD}/{PACKAGE}/edits/{edit}/bundles",
            params={"uploadType": "media"},
            headers={"Content-Type": "application/octet-stream"},
            data=f,
            timeout=900,
        )
    if r.status_code != 200:
        give_up(f"Upload refused ({r.status_code}) {readable(r)}")
    version = r.json()["versionCode"]
    print(f"Bundle accepted: version code {version}")

    r = s.get(f"{base}/tracks/{track}")
    if r.status_code == 200:
        for release in r.json().get("releases", []):
            print(f"Now on {track}: {release.get('versionCodes')} {release.get('status')}")

    body = {"track": track, "releases": [{"versionCodes": [str(version)], "status": "completed"}]}
    r = s.put(f"{base}/tracks/{track}", json=body)
    if r.status_code != 200:
        give_up(f"Track {track} refused ({r.status_code}) {readable(r)}")

    r = s.post(f"{base}:validate")
    if r.status_code != 200:
        give_up(f"Google refuses this release ({r.status_code}) {readable(r)}")
    print(f"Google accepts version {version} on {track}")

    if not apply:
        s.delete(base)
        print("Check only: nothing saved. Run again with apply to send it to the testers.")
        return

    r = s.post(f"{base}:commit")
    if r.status_code != 200:
        sys.exit(f"Not saved ({r.status_code}) {readable(r)}")
    print(f"Done: version {version} is on {track}, rolled out to its testers.")


if __name__ == "__main__":
    main()
