# Google Play listing (Android)

**There is no Android app yet.** The repository contains only the iPhone app (Swift / SwiftUI), which cannot run on Android. This listing is ready for the day an Android version exists; nothing can be published on Google Play before that.

## Store listing

| Field | Value | Limit |
|---|---|---|
| App name | Atala: Running Coach | 30 |
| Short description | Your running plan and coach: 5K, 10K, half marathon. Guided by voice. | 80 |
| Category | Health & Fitness | |
| Tags | Running, Fitness, Training plans | |
| Content rating | Everyone (IARC questionnaire: no sensitive content) | |
| Package name (suggested) | com.atala.app | |

## Full description (4000)

Atala is the running coach in your pocket. Tell it your goal and the days you can run, and it builds a plan around your life, then guides you through every session.

A PLAN BUILT FOR YOU
• Choose your goal: 5K, 10K, half marathon, running regularly, or simply getting in shape
• Pick your running days and your long-run day
• Paces set from your level or a recent result
• Easy runs, intervals, tempo, long runs and race-pace sessions
• Move a session to another day in two taps

A COACH WHO ANSWERS
• Choose your coach and the tone you like
• Ask anything, any time: warm-ups, missed runs, busy weeks
• Your coach suggests changes to your plan. Nothing moves until you say yes

GUIDED ON EVERY RUN
• GPS tracking of distance, time and pace
• Voice guidance for each step of your session
• Works with your music

STRENGTH AND STRETCHING INCLUDED
• Short guided sessions, timed and spoken

SEE YOUR PROGRESS
• Route, pace per kilometre and elevation after every run
• Personal bests, weekly volume and weekly streaks

ATALA PRO
Free trial, then an auto-renewing yearly subscription. Manage or cancel at any time in Google Play.

Atala is a training tool, not a medical device. If you have a health condition or an injury, talk to a health professional before you start.

## Graphics needed

| Asset | Size |
|---|---|
| App icon | 512 × 512 px, PNG |
| Feature graphic | 1024 × 500 px |
| Phone screenshots | 2 to 8, 1080 × 1920 px or taller |

## Other Play Console forms

- **Data safety**: same answers as the App Store privacy table in `app-store.md`, except Health comes from Health Connect instead of Apple Health.
- **Privacy policy URL**: required.
- **Health apps declaration**: required for apps that read fitness data.
- **Location**: foreground location only, declared as "fitness tracking".
- **Testing**: a new personal developer account must run a closed test with at least 12 testers for 14 days before going to production.

## What an Android version needs

The iPhone code cannot be reused as is. The options are a native Android app (Kotlin, Jetpack Compose) or rebuilding both apps on a shared framework. Either way it is a new project of the same size as the iPhone app. The Supabase backend (accounts, sync, coach) works for both without changes.
