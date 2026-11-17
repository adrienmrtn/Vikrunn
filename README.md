# Vicrun

Running-coach app for iPhone (SwiftUI, iOS 17+).

## Open and run

1. Open `Vicrun.xcodeproj` in Xcode 16 or later.
2. Select the `Vicrun` target, tab *Signing & Capabilities*, and choose your team.
   Change the bundle identifier (`com.vicrun.app`) if it is already taken.
3. Pick an iPhone simulator or your phone and press Run.

There are no third-party dependencies.

## Layout

- `Vicrun/App` – app entry point and root view
- `Vicrun/Core` – design system, data models, plan engine, local store
- `Vicrun/Features` – onboarding, main tabs, run flow, coach chat, sign-in
- `Vicrun/Services` – GPS tracking, voice, Apple Health, Supabase
- `Vicrun/Resources` – coach animation frames
- `Config` – Info.plist additions and entitlements
- `supabase` – database migration and edge functions
