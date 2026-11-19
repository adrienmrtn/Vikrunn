# Subscriptions: App Store Connect + RevenueCat

State on 19 November 2026: the subscription exists in App Store Connect and in RevenueCat. It was created through RevenueCat's API, so both sides match.

| Where | Done | Still to do |
|---|---|---|
| App Store Connect | Subscription `atala_pro_yearly` in the group "Atala Pro": 1 year, 3-day free trial in all 175 territories, prices by country (below). Status: Ready to Submit | Replace the blank review screenshot with a real one of the paywall, add a privacy policy URL, then submit it with a build |
| RevenueCat | Project "Atala", app "Atala (iOS)", App Store Connect and in-app purchase keys, product, entitlement `atala_pro`, `$rc_annual` package of the `default` offering | Nothing |
| The app | Purchase code (`Atala/Services/PurchaseService.swift`), paywall wired to it | Add the RevenueCat package in Xcode (step 3) |

The default offering also holds the sample `$rc_monthly` and `$rc_lifetime` packages RevenueCat created with the project. They only point to Test Store products, so the iPhone app never sees them.

## Prices in place

One yearly product. Territories not listed keep Apple's own equivalent of the US price.

| Tier | Price per year | Territories |
|---|---|---|
| A | $49.99 · €49.99 · £42.99 · 549 SEK · 369 DKK, Apple's equivalent elsewhere (CHF 40, CA$69.99, A$79.99, ¥8,000…) | United States, eurozone and other euro storefronts, United Kingdom, Switzerland, Canada, Australia, Japan, Nordics, and every territory not listed below |
| B | €34.99 · 159.99 PLN · 899 CZK · 15,990 HUF · 199.99 RON · 41,990 CLP · 139.99 SAR · 139.99 QAR · 990 TWD | Bulgaria, Croatia, Poland, Czechia, Hungary, Romania, Chile, Saudi Arabia, Qatar, Taiwan |
| C | 549 MXN · 169.90 BRL · 549.99 ZAR · 129.90 MYR · 1,090 THB · 129,900 COP · 129.90 PEN | Mexico, Brazil, South Africa, Malaysia, Thailand, Colombia, Peru |
| D | 999.99 TRY · 1,999 INR · 399,000 IDR · 1,190 PHP · 599,000 VND · 999.99 EGP · 31,900 NGN · 4,900 PKR · $19.99 (Argentina) · 11,990 KZT | Türkiye, India, Indonesia, Philippines, Vietnam, Egypt, Nigeria, Pakistan, Argentina, Kazakhstan |

About a hundred smaller storefronts are billed in US dollars and sit at $49.99, including low-income countries in Africa, Central Asia and Latin America. Lowering them is one more price change if wanted.

To change a price later: App Store Connect → Atala → Subscriptions → Atala Pro Yearly → Subscription Prices, or ask for it through RevenueCat as these were set.

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

No Android app exists, so there is nothing to configure in Google Play Billing yet. When there is one, the same RevenueCat project takes a second app (Play Store) and the same `pro` entitlement.
