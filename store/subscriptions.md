# Subscriptions: App Store Connect + RevenueCat

State on 19 November 2026:

| Where | Done | Still to do |
|---|---|---|
| RevenueCat | Project "Atala", app "Atala (iOS)" for `com.atala.app`, product `atala_pro_yearly`, attached to the entitlement `atala_pro` and to the `$rc_annual` package of the `default` offering | Upload the App Store Connect in-app purchase key (`.p8`) on the app |
| App Store Connect | Nothing yet | Create the subscription `atala_pro_yearly`, its prices by country and the 3-day trial |
| The app | Purchase code written (`Atala/Services/PurchaseService.swift`), paywall wired to it | Add the RevenueCat package in Xcode (step 3) |

The default offering also holds the sample `$rc_monthly` and `$rc_lifetime` packages RevenueCat created with the project. They only point to Test Store products, so the iPhone app never sees them.

## The offer

One product: a yearly subscription, priced by country.

| | Yearly |
|---|---|
| Product ID | `atala_pro_yearly` |
| Reference name | Atala Pro Yearly |
| Duration | 1 year |
| Free trial | 3 days |
| Display name | Atala Pro |
| Description | Full plan, coach and guided sessions |

Subscription group: **Atala Pro**.

### Prices by country

The ceiling is €49.99 a year, the floor is about €19.99 a year. Apple only accepts its own price points, so each line gives the target and you pick the closest point offered in that currency.

| Tier | Target (per year) | Countries |
|---|---|---|
| A | €49.99 / $49.99 / £44.99 / CHF 49.90 | Eurozone, United States, United Kingdom, Switzerland, Canada, Australia, New Zealand, Nordics, Japan, Singapore, Hong Kong, UAE, Israel, South Korea |
| B | ≈ €34.99 | Poland, Czechia, Hungary, Romania, Bulgaria, Croatia, Chile, Saudi Arabia, Qatar, Taiwan |
| C | ≈ €27.99 | Mexico, Brazil, South Africa, Malaysia, Thailand, Colombia, Peru |
| D | ≈ €19.99 | Türkiye, India, Indonesia, Philippines, Vietnam, Egypt, Nigeria, Pakistan, Argentina, Kazakhstan |

How to enter it: set the base price in the United States ($49.99), let Apple fill every country, then open the countries of tiers B, C and D and lower them by hand.
Countries not listed keep Apple's automatic equivalent of tier A; move any of them down if you see fit.
The app shows the price the store returns for the user's country, so nothing has to change in the code per country.

## Step 1: App Store Connect (you)

1. **Agreements, Tax, and Banking**: the Paid Apps agreement must be active, or no product can be sold.
2. Atala → **Subscriptions** → create the group "Atala Pro".
3. Create the product with the ID above: duration, prices by country (table above), localisation (display name and description), and a screenshot of the paywall for review.
4. On the yearly product → **Subscription Prices** → **Introductory Offers** → free trial, 3 days, all territories.
5. **Users and Access → Integrations → In-App Purchase**: generate an In-App Purchase key (`.p8`). RevenueCat needs it, with its Key ID and your Issuer ID.

## Step 2: RevenueCat (one thing left)

Everything is created except the Apple key: Project Atala → Apps → Atala (iOS) → **In-app purchase key configuration** → upload the `.p8` from step 1.5 with its Key ID and your Issuer ID. Without it RevenueCat cannot validate purchases.

## Step 3: the app

1. In Xcode: File → Add Package Dependencies → `https://github.com/RevenueCat/purchases-ios-spm` → add the product **RevenueCat** to the Atala target. Commit and push the project file afterwards.
2. That is all: the purchase code is compiled only when the package is present. From then on the paywall shows the store's own price for the user's country and "Start my free trial" opens the real purchase sheet.
3. Still to decide: what stays free and what needs Pro. Nothing is locked today, so closing the paywall gives the whole app.
4. Still to add: "Manage subscription" in the profile, and real Terms and Privacy links on the paywall (Apple requires them next to the price).

## Step 4: test

1. Xcode → run on a real iPhone with a **Sandbox Apple Account** (App Store Connect → Users and Access → Sandbox).
2. Buy the yearly product: the trial should start and `pro` should become active in the RevenueCat dashboard within seconds.
3. Send a build to TestFlight: purchases there are free and renew on an accelerated schedule.

## Android

No Android app exists, so there is nothing to configure in Google Play Billing yet. When there is one, the same RevenueCat project takes a second app (Play Store) and the same `pro` entitlement.
