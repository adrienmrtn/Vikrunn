# Subscriptions: App Store Connect + RevenueCat

Checked on 19 November 2026: the RevenueCat account has four projects (Nutria, Sophia, Micabo, UnSwap) and **none for Atala**. Nothing is configured yet, and the app does not contain the RevenueCat SDK: the paywall is a placeholder.

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

## Step 2: RevenueCat (you, in the dashboard)

The RevenueCat connection available to me in this session is read-only: I can check your setup, not create it.

1. **Create a project** named "Atala".
2. **Add an app** → App Store → bundle ID `com.atala.app` → upload the `.p8` key from step 1.5.
3. **Products** → import `atala_pro_yearly`.
4. **Entitlements** → create `pro` → attach the product.
5. **Offerings** → create `default`, mark it current → one package: `$rc_annual` → `atala_pro_yearly`.
6. **API keys** → copy the public Apple key (starts with `appl_`). It is safe to put in the app.

## Step 3: the app (me)

Once steps 1 and 2 exist:

1. Add the RevenueCat package in Xcode: File → Add Package Dependencies → `https://github.com/RevenueCat/purchases-ios-spm` → product "RevenueCat".
2. I write the purchase code: start the SDK with the `appl_` key, log the signed-in user in, load the `default` offering, show the real prices on the paywall, buy, restore, and read the `pro` entitlement.
3. I lock what Pro covers. To decide with you: which parts stay free (for example the first week of the plan, as Runna does) and which need Pro.
4. Add "Restore purchases" and "Manage subscription" in the profile, and real Terms and Privacy links on the paywall (Apple requires them next to the price).

## Step 4: test

1. Xcode → run on a real iPhone with a **Sandbox Apple Account** (App Store Connect → Users and Access → Sandbox).
2. Buy the yearly product: the trial should start and `pro` should become active in the RevenueCat dashboard within seconds.
3. Send a build to TestFlight: purchases there are free and renew on an accelerated schedule.

## Android

No Android app exists, so there is nothing to configure in Google Play Billing yet. When there is one, the same RevenueCat project takes a second app (Play Store) and the same `pro` entitlement.
