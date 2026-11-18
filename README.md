# Atala

Running-coach app for iPhone (SwiftUI, iOS 17+).

## Open and run

1. Open `Atala.xcodeproj` in Xcode 16 or later.
2. Select the `Atala` target, tab *Signing & Capabilities*, and choose your team.
   Change the bundle identifier (`com.atala.app`) if it is already taken.
3. Pick an iPhone simulator or your phone and press Run.

There are no third-party dependencies.

## Layout

- `Atala/App` – app entry point and root view
- `Atala/Core` – design system, data models, plan engine, local store
- `Atala/Features` – onboarding, main tabs, run flow, coach chat, sign-in
- `Atala/Services` – GPS tracking, voice, Apple Health, Supabase
- `Atala/Resources` – coach animation frames
- `Config` – Info.plist additions and entitlements
- `supabase` – database migration and edge functions
