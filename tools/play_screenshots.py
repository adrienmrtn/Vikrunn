"""Renders the Google Play phone screenshots (1080 x 1920 JPEG) of every language from the App
Store pages in appstore/screenshots/src, into <out>/<Play language>/NN-Name.jpg.

    python tools/play_screenshots.py build/play-screenshots            # every language
    python tools/play_screenshots.py build/play-screenshots fr-FR ja-JP

The App Store pages are 1290 x 2796 (9:19.5). Google Play accepts 9:16 at most, so each page is
widened to 1573 x 2796 with its phone, rings and callouts moved to stay centred, the iPhone notch
becomes an Android camera dot, and the render is scaled down to 1080 x 1920.
Needs Chrome (CHROME = its path if it is not found) and Pillow.
"""
import os
import shutil
import subprocess
import sys
import tempfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "appstore", "screenshots", "src")

# Google Play language -> App Store locale of the page to render.
LANGUAGES = {
    "ar": "ar-SA", "cs-CZ": "cs", "da-DK": "da", "de-DE": "de-DE", "el-GR": "el", "en-US": "en-US",
    "es-ES": "es-ES", "fi-FI": "fi", "fr-FR": "fr-FR", "hr": "hr", "hu-HU": "hu", "it-IT": "it",
    "iw-IL": "he", "ja-JP": "ja", "nl-NL": "nl-NL", "no-NO": "no", "pl-PL": "pl", "pt-BR": "pt-BR",
    "pt-PT": "pt-PT", "ro": "ro", "ru-RU": "ru", "sk": "sk", "sl": "sl-SI", "sv-SE": "sv", "tr-TR": "tr",
}

WIDE = (1573, 2796)
FINAL = (1080, 1920)
NOTCH = "left: 132px; top: 11px; width: 126px; height: 37px; border-radius: 19px"
CAMERA = "left: 187px; top: 16px; width: 16px; height: 16px; border-radius: 8px"
MOVES = [
    ("1290px", "1573px"),
    ("position: absolute; left: -105px", "position: absolute; left: 36px"),
    ("position: absolute; left: -305px", "position: absolute; left: -164px"),
    ("position: absolute; left: -505px", "position: absolute; left: -364px"),
    ("position: absolute; left: 170px", "position: absolute; left: 311px"),
    (NOTCH, CAMERA),
    ("position: absolute; right: 34px", "position: absolute; right: 120px"),
    ("position: absolute; left: 34px", "position: absolute; left: 120px"),
]


def widen(html):
    for old, new in MOVES:
        html = html.replace(old, new)
    return html


def chrome():
    wanted = os.environ.get("CHROME")
    names = [wanted] if wanted else [
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "google-chrome", "chromium", "chromium-browser"]
    for name in names:
        if os.path.isfile(name) or shutil.which(name):
            return name
    sys.exit("Chrome not found. Set CHROME to its path.")


def main():
    out = os.path.abspath(sys.argv[1])
    languages = sys.argv[2:] or sorted(LANGUAGES)
    browser = chrome()
    count = 0
    with tempfile.TemporaryDirectory() as tmp:
        shutil.copytree(os.path.join(SRC, "assets"), os.path.join(tmp, "assets"))
        for language in languages:
            if language not in LANGUAGES:
                sys.exit(f"Unknown Google Play language: {language}")
            pages = os.path.join(SRC, LANGUAGES[language])
            work = os.path.join(tmp, language)
            os.makedirs(work)
            os.makedirs(os.path.join(out, language), exist_ok=True)
            for name in sorted(os.listdir(pages)):
                if not name.endswith(".html"):
                    continue
                with open(os.path.join(pages, name), encoding="utf-8") as f:
                    html = widen(f.read())
                if "1290px" in html or NOTCH in html or "1573px" not in html:
                    sys.exit(f"{language}/{name}: the page was not widened, its layout changed")
                page = os.path.join(work, name)
                with open(page, "w", encoding="utf-8") as f:
                    f.write(html)
                shot = os.path.join(tmp, "shot.png")
                subprocess.run(
                    [browser, "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
                     "--force-device-scale-factor=1", f"--window-size={WIDE[0]},{WIDE[1]}",
                     "--virtual-time-budget=8000", f"--user-data-dir={os.path.join(tmp, 'profile')}",
                     f"--screenshot={shot}", f"file://{page}"],
                    check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                if not os.path.isfile(shot):
                    sys.exit(f"Chrome produced no image for {language}/{name}")
                image = Image.open(shot).convert("RGB")
                if image.size != WIDE:
                    sys.exit(f"{language}/{name} is {image.size}, expected {WIDE}")
                image.resize(FINAL, Image.LANCZOS).save(
                    os.path.join(out, language, name[:-5] + ".jpg"), quality=92)
                os.remove(shot)
                count += 1
            print(f"rendered {language}")
    print(f"{count} screenshots in {out}")


if __name__ == "__main__":
    main()
