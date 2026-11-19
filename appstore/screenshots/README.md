# App Store screenshots

Six screenshots (iPhone 6.9", 1290 x 2796) in 25 App Store languages.

- `src/<locale>/NN-Name.html`: one page per screenshot, named with the App Store Connect locale code.
- `src/assets/`: the logo and photos the pages use (copies of the asset catalog).
- `render.sh`: renders the pages with headless Chrome to `fastlane/screenshots/<locale>/*.jpg` (not committed).
- `fastlane/Fastfile`, lane `screenshots`: uploads them with `deliver`. It replaces the screenshots of the
  version being prepared, adds the missing languages, and neither uploads a build nor submits for review.

## Sending them

Actions > **App Store screenshots** > Run workflow. Untick "Upload" to only render: the images are then
attached to the run.

Three repository secrets are needed (App Store Connect > Users and Access > Integrations > App Store Connect API,
a key with the App Manager role):

| Secret | Value |
| --- | --- |
| `ASC_KEY_ID` | Key ID |
| `ASC_ISSUER_ID` | Issuer ID |
| `ASC_KEY_P8` | Whole content of the `.p8` file |

The app `com.atala.app` must exist in App Store Connect with a version that can still be edited.

## Notes

- The screens inside the phones are drawn from the SwiftUI code with sample data, they are not captures of the app.
- The phone interface stays in English, like the app; titles, subtitles and callouts are translated.
- Latvian and Serbian are not App Store languages and are left out.
- Locally: `appstore/screenshots/render.sh fr-FR` renders one language (Chrome needed; outside macOS the font differs).
