# Subscriptions: App Store Connect + RevenueCat

State on 19 November 2026: the subscription exists in App Store Connect and in RevenueCat. It was created through RevenueCat's API, so both sides match.

| Where | Done | Still to do |
|---|---|---|
| App Store Connect | Three subscriptions in the group "Atala Pro" (yearly with a 3-day trial, monthly, yearly gift at half price), available in all 175 territories with prices by country (below). Status: Ready to Submit | Replace the blank review screenshot with a real one of the paywall, add a privacy policy URL, then submit it with a build |
| RevenueCat | Project "Atala", app "Atala (iOS)", App Store Connect and in-app purchase keys, the three products, entitlement `atala_pro`, offerings `default` and `gift` | Nothing |
| The app | Purchase code (`Atala/Services/PurchaseService.swift`), paywall wired to it | Add the RevenueCat package in Xcode (step 3) |

The default offering also holds the sample `$rc_lifetime` package RevenueCat created with the project. It only points to a Test Store product, so the iPhone app never sees it.

## Products and prices in place

Three products in the subscription group "Atala Pro". All unlock the entitlement `atala_pro`.

| Product | What it is | RevenueCat |
|---|---|---|
| `atala_pro_yearly` | 1 year, 3-day free trial: the main offer | `default` offering, `$rc_annual` |
| `atala_pro_monthly` | 1 month, no trial | `default` offering, `$rc_monthly` |
| `atala_pro_yearly_gift` | 1 year at half the yearly price, no trial: the welcome gift shown in the app to people who closed the paywall | `gift` offering, `$rc_annual` |

The gift is a separate product at half price, so the discount also applies at each renewal. A discount on the first year only would be an introductory offer, which has to be set by hand in App Store Connect.

Prices per country, in the storefront currency. Territories not listed keep Apple's own equivalent of the US price.

| Tier | Yearly | Monthly | Gift (yearly) | Territories |
|---|---|---|---|---|
| A | $49.99 · €49.99 · £42.99 | $9.99 · €9.99 · £8.49 | $24.99 · €24.99 · £21.49 | United States, euro storefronts, United Kingdom, Switzerland, Canada, Australia, Japan, Nordics, and every territory not listed below |
| B | €34.99 · 159.99 PLN · 899 CZK | €6.99 · 34.99 PLN · 179 CZK | €17.99 · 79.99 PLN · 449 CZK | Bulgaria, Croatia, Poland, Czechia, Hungary, Romania, Chile, Saudi Arabia, Qatar, Taiwan |
| C | 549 MXN · 169.90 BRL · 1,090 THB | 109 MXN · 32.90 BRL · 219 THB | 279 MXN · 84.90 BRL · 549 THB | Mexico, Brazil, South Africa, Malaysia, Thailand, Colombia, Peru |
| D | 999.99 TRY · 1,999 INR · $19.99 (Argentina) | 199.99 TRY · 399 INR · $3.99 | 499.99 TRY · 999 INR · $9.99 | Türkiye, India, Indonesia, Philippines, Vietnam, Egypt, Nigeria, Pakistan, Argentina, Kazakhstan |

About a hundred smaller storefronts are billed in US dollars and sit at the tier A dollar price, including low-income countries in Africa, Central Asia and Latin America. Lowering them is one more price change if wanted.

Each product still has a blank review screenshot: replace the three with real screenshots of the paywall and of the gift screen before submitting.

## What is left: the app

1. In App Store Connect → **Business**, check that the Paid Apps agreement is active: without it the product cannot be sold or tested.
1. In Xcode: File → Add Package Dependencies → `https://github.com/RevenueCat/purchases-ios-spm` → add the product **RevenueCat** to the Atala target. Commit and push the project file afterwards.
2. That is all: the purchase code is compiled only when the package is present. From then on the paywall shows the store's own price for the user's country and "Start my free trial" opens the real purchase sheet.
3. Still to decide: what stays free and what needs Pro. Nothing is locked today, so closing the paywall gives the whole app.
4. Still to add: "Manage subscription" in the profile, and real Terms and Privacy links on the paywall (Apple requires them next to the price).

## Test

1. Xcode → run on a real iPhone with a **Sandbox Apple Account** (App Store Connect → Users and Access → Sandbox).
2. Buy the yearly product: the trial should start and `pro` should become active in the RevenueCat dashboard within seconds.
3. Send a build to TestFlight: purchases there are free and renew on an accelerated schedule.

## Android

State on 19 November 2026: the store side is prepared, there is still no Android app.

| Where | Done | Still to do |
|---|---|---|
| Google Play Console (Vik Studios SAS) | App "Atala: AI Running Coach" created as a draft, package `com.atala.app`, free. Store listing saved as a draft in 25 languages (title, short and full description, from `fastlane/metadata/android`), with the icon and the feature graphic | Phone screenshots (Play wants 9:16, and they should show the Android app), the "App content" declarations, a build |
| RevenueCat | App "Atala (Android)" added to the project "Atala" with the package `com.atala.app` | Service account credentials, the products, the Android SDK key in the app |

What is left for subscriptions, in this order:

1. Upload a first Android build with the Play Billing library: Google Play does not let you create subscriptions before that.
2. In Play Console → Monetise → Subscriptions, create the same three products as on iOS: `atala_pro_yearly` (base plan yearly, 3-day free trial offer), `atala_pro_monthly` (base plan monthly), `atala_pro_yearly_gift` (base plan yearly at half price). Use the prices of the table above.
3. Create a Google Cloud service account, give it access in Play Console (Users and permissions), and upload its JSON key in RevenueCat → Apps → Atala (Android). RevenueCat needs it to validate purchases.
4. In RevenueCat, import the three Play products, attach them to the entitlement `atala_pro`, and add them to the existing packages: `$rc_annual` and `$rc_monthly` of the offering `default`, `$rc_annual` of the offering `gift`. The offerings then serve both stores.
5. In the Android app, configure the RevenueCat SDK with the public key of "Atala (Android)" (it starts with `goog_`), and use the same entitlement and offering identifiers as `PurchaseService.swift`.
