# App Store listing (iOS)

Paste each field into App Store Connect → Atala → App Information / version page.
Language: English (U.S.). The app itself is in English only for now.

## Identity

| Field | Value | Limit |
|---|---|---|
| Name | Atala: Running Coach | 30 |
| Subtitle | 5K, 10K & half marathon plans | 30 |
| Bundle ID | com.atala.app | |
| SKU | atala-ios | |
| Primary category | Health & Fitness | |
| Secondary category | Sports | |
| Age rating | 4+ (answer "None" to every content question) | |
| Copyright | 2026 Vik Studios | |

## Promotional text (170)

Your plan, your coach, your pace. Tell Atala your goal and the days you can run: it builds your weeks, guides every session by voice and answers your questions 24/7.

## Keywords (100, comma-separated, no spaces)

run,runner,training,plan,jogging,pace,tracker,gps,interval,strength,stretching,race,beginner,fitness

## Description (4000)

Atala is the running coach in your pocket. Tell it your goal and the days you can run, and it builds a plan around your life, then guides you through every session.

A PLAN BUILT FOR YOU
• Choose your goal: 5K, 10K, half marathon, running regularly, or simply getting in shape
• Pick your running days and your long-run day
• Paces set from your level or a recent result
• Easy runs, intervals, tempo, long runs, race-pace sessions and a taper before race day
• Move a session to another day in two taps when life gets in the way

A COACH WHO ANSWERS
• Choose your coach and the tone you like: kind, direct or playful
• Ask anything, any time: how to warm up, what to do after a missed run, how to handle a busy week
• Your coach can suggest changes to your plan. Nothing moves until you say yes

GUIDED ON EVERY RUN
• GPS tracking of distance, time and pace
• Voice guidance for each step of your session
• Your coach tells you when you are too fast or too slow
• Works with your music

STRENGTH AND STRETCHING INCLUDED
• Short guided strength and stretching sessions, timed and spoken
• Placed in your week around your runs

SEE YOUR PROGRESS
• Run summary with your route, pace per kilometre, elevation and heart rate
• Personal bests and weekly volume
• Weekly streaks that reward showing up, not running every day

WORKS WITH APPLE HEALTH
• Import the runs recorded by your iPhone or your watch
• Runs from your watch count in your plan

ATALA PRO
Atala Pro unlocks your full plan, your coach, voice guidance and guided sessions.
• Free trial, then an auto-renewing yearly subscription
• Payment is charged to your Apple Account at confirmation of purchase
• Your subscription renews automatically unless it is cancelled at least 24 hours before the end of the current period
• Manage or cancel at any time in your App Store account settings

Atala is a training tool, not a medical device. If you have a health condition or an injury, talk to a health professional before you start.

Terms of Use: https://vikstudios.com/atala/terms
Privacy Policy: https://vikstudios.com/atala

## What's New (version 0.1.0)

First release of Atala: your running plan, your coach and guided sessions.

## URLs

| Field | Value |
|---|---|
| Privacy Policy URL | https://vikstudios.com/atala |
| Terms of Use (EULA) | https://vikstudios.com/atala/terms (also paste it in the "License Agreement" field, or keep Apple's standard one and leave the link in the description) |
| Support URL | **Missing** (a simple page with a contact e-mail is enough) |
| Marketing URL | Optional |

Also set the privacy policy URL on each of the three subscriptions (App Store Connect → Subscriptions → the product → Privacy Policy).

## App Privacy (the "nutrition label")

Declare what the app really collects. From the current code:

| Data | Collected | Linked to the user | Used for tracking |
|---|---|---|---|
| Email address (sign-in) | Yes | Yes | No |
| Name (first name) | Yes | Yes | No |
| Precise location (routes of runs) | Yes | Yes | No |
| Fitness (runs, paces, plan) | Yes | Yes | No |
| Health (heart rate read from Apple Health) | Yes | Yes | No |
| User content (messages to the coach) | Yes | Yes | No |
| Purchases (once subscriptions are live) | Yes | Yes | No |

Purpose for all of them: App Functionality. No advertising, no tracking, no third-party analytics in the code today.
Coach messages are sent to an AI provider through your server to produce the answers: mention it in the privacy policy.

## Screenshots

Required: iPhone 6.9" (1320 × 2868 px). One set is enough; Apple scales it for smaller phones.
Suggested order and caption for each:

1. Today screen: "Your plan, built around your life"
2. Coach chat: "A coach who answers, 24/7"
3. Live run: "Guided by voice on every run"
4. Run summary: "Every run, explained"
5. Plan tab: "From first week to race day"
6. Strength session: "Strength and stretching included"

Take them in the simulator with Cmd+S (iPhone 17 Pro Max gives the right size).

## App Review notes (paste in "Notes")

Atala builds a running plan from a short questionnaire. To test without creating an account, choose "Continue without an account" on the sign-in sheet.
Location is used only while a run is being recorded, to measure distance and pace.
Apple Health is optional and read-only: it imports past runs and heart rate.
The coach chat uses an AI model; answers are generated and may be imperfect.

## Before you submit

Done in the app:
- Real subscriptions (yearly, monthly, welcome gift) through RevenueCat, with Restore purchases and the Terms and Privacy links on the paywall and the sign-in sheet.
- Session reminders are real notifications. Nothing promises a reminder before the end of the free trial any more.
- The Health permission text matches what the app does (read only).

Still open:
- "10,000 runners coached every day" is shown in the onboarding. Apple can ask for proof of figures like this one.
- The three reviews in the onboarding must come from real users.
- A Support URL.
- Screenshots for the listing, and for each of the three subscriptions (blank placeholders were uploaded).
- The App Privacy questionnaire in App Store Connect (answers in the table above; add "Purchases").
