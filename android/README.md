# Atala for Android

Kotlin port of the iPhone app. The specification is the Swift code in `../Atala/`: every screen is
ported from its Swift file, with the same wording, rules and order.

Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01 (Material 3), navigation-compose 2.8.5,
minSdk 26, targetSdk 35, Java 17.

## Build

Local (JDK 17 and the Android SDK):

```sh
cd android
export ANDROID_HOME=/opt/android-sdk   # or write sdk.dir in local.properties
./gradlew :app:assembleDebug --no-daemon -q
# APK: app/build/outputs/apk/debug/app-debug.apk
```

CI: `.github/workflows/android-build.yml` builds the debug APK on every push touching `android/**`
or `Atala/Resources/**` and keeps it 14 days as the `atala-debug-apk` artifact. Release builds are
signed when `ATALA_KEYSTORE_PATH`, `ATALA_KEYSTORE_PASSWORD`, `ATALA_KEY_ALIAS` and
`ATALA_KEY_PASSWORD` are set; `ATALA_VERSION_CODE` sets the version code.

A change is done when the command above finishes without errors.

## Layout

```
com.atala.app
├── AtalaApplication, MainActivity   process-wide store, language, edge-to-edge activity
├── core/                            no Compose here
│   ├── l10n/      AppLanguage, L10n + L(), Fmt
│   ├── model/     answers, plan, runs, chat, settings, coaches, ProAccess, streaks (JSON = iPhone's)
│   ├── plan/      PlanEngine
│   ├── exercise/  exercise catalog, guided prescriptions
│   └── store/     AppStore (single source of truth), AppState, PlanMoves, SampleData
└── ui/
    ├── theme/       Theme tokens, AtalaTheme, AtalaImages (iOS asset names -> drawables)
    ├── components/  Buttons, Cards, CoachViews, Haptics, ScreenPlaceholder (temporary)
    ├── root/        AtalaRoot (language cross-fade, RTL, splash, onboarding vs app), LocalAppStore
    ├── main/        MainScreen (NavHost + tab bar + "+" sheet), Routes, Navigation
    ├── onboarding/  OnboardingFlow, OnboardingNavigator, OnboardingStep, OnboardingScaffold, screens
    ├── today/       TodayScreen
    ├── plan/        PlanScreen, WorkoutDetailScreen
    ├── coach/       CoachScreen
    ├── progress/    ProgressScreen
    ├── profile/     ProfileScreen
    ├── run/         RunFlowScreen, RunSummaryScreen, ManualRunEntryScreen
    ├── guided/      GuidedSessionScreen
    └── paywall/     PaywallScreen
```

## Conventions

**Text.** Every visible sentence goes through `L("English key", values...)`, with the key copied
verbatim from the Swift `L("...")` call (punctuation, `%@`, `*emphasis*` markers included): that
is what makes the 24 translations in `Atala/Resources/Strings/strings_<code>.json` apply. A key
that differs by one character silently stays in English. Plurals pick the form from the first
`Int` value. Numbers, paces, durations and dates go through `Fmt`; a title inside a sentence uses
`.midSentence`. Never use `stringResource` for app text.

**Language.** `AppLanguage.choose(code)` switches the language: `AtalaRoot` cross-fades the whole
tree, rebuilt with the new texts, and sets the layout direction (RTL for `ar` and `he`). Nothing
needs to observe the language by hand. Use start/end, never left/right; directional icons come
from `Icons.AutoMirrored`.

**Look.** Colours, paddings, radii and fonts come from `Theme` (`Theme.ink`, `Theme.green`,
`Theme.screenPadding`, `Theme.font(16, FontWeight.SemiBold)`, `Theme.title(28)`), as in
Theme.swift. Reuse before writing: `PrimaryButton`, `SecondaryButton`, `BottomBar`,
`Modifier.card()`, `OptionCard`, `SelectionMark`, `VignetteImage`, `IconTile`, `CoachAvatar`,
`CoachPoseImage`, `CoachNote`, and `rememberHaptics()` with the `HapticKind` the Swift code plays.
Pictures by their asset-catalog name: `AtalaImages["illu_today"]`, `AtalaImages.coachPose(...)`.
The app is light only, white bars with dark icons.

**State.** `LocalAppStore.current` is the `AppStore` everywhere; `val state by collectAppState()`
observes it. Change it only through the store's actions (`updateAnswers`, `addRun`,
`updateWorkout`, `generatePlan`...) or `store.update { }`. Pro status:
`ProAccess.isPro.collectAsStateWithLifecycle()`.

**Insets.** Content is drawn edge to edge. Each screen pads itself, usually with
`Modifier.safeDrawingPadding()`; under the tab bar the shell has already consumed the navigation
bar, so tab screens use the same modifier. Onboarding screens use `OnboardingScaffold`, which
handles everything.

**Previews.** Wrap in `AtalaPreview { }` (theme + in-memory sample store) and pass
`Navigation.Preview`.

## Where features plug in

Each destination calls one public composable whose signature is fixed (`ui/main/MainScreen.kt`):

| Route | Composable |
| --- | --- |
| `TodayRoute` (tab) | `today.TodayScreen(nav: Navigation)` |
| `PlanRoute` (tab) | `plan.PlanScreen(nav)` |
| `CoachRoute` (tab) | `coach.CoachScreen(nav)` |
| `ProgressRoute` (tab) | `progress.ProgressScreen(nav)` |
| `WorkoutDetailRoute(workoutId)` | `plan.WorkoutDetailScreen(workoutId: UUID, nav)` |
| `RunFlowRoute(workoutId?)` | `run.RunFlowScreen(workoutId: UUID?, nav)` |
| `ManualRunRoute(workoutId?)` | `run.ManualRunEntryScreen(workoutId: UUID?, nav)` |
| `RunSummaryRoute(runId)` | `run.RunSummaryScreen(runId: UUID, nav)` |
| `GuidedSessionRoute(workoutId)` | `guided.GuidedSessionScreen(workoutId: UUID, nav)` |
| `ProfileRoute` | `profile.ProfileScreen(nav)` |
| `PaywallRoute` | `paywall.PaywallScreen(closeDelay: Double = 0.0, onFinished: () -> Unit)` |

Tabs, in the iPhone's order: Today, Plan, "+", Coach, Progress. The "+" opens a sheet with
"Start a free run" (`onStartRun(null)`) and "Add a past run" (`onAddPastRun(null)`). Profile has no
tab: Today and Plan open it from the avatar in their header (`nav.onOpenProfile()`).

**Navigation lambdas.** Screens never see the NavController. They receive `Navigation`
(`ui/main/Navigation.kt`), a data class of plain lambdas: `onOpenWorkout(UUID)`,
`onStartRun(UUID?)`, `onAddPastRun(UUID?)`, `onOpenRunSummary(UUID)` (from the run flow it
replaces the flow in the back stack), `onStartGuided(UUID)`, `onOpenProfile()`, `onOpenPaywall()`,
`onOpenCoach()` (closes what is open, shows the Coach tab), `onBack()`. Ids are the model's UUIDs.
Inside a feature, sub-screens (run countdown, live, pause, feeling...) are the feature's own
business: plain state in its package, not new routes. A new destination between features needs a
route and a lambda: ask the shell owner.

## Onboarding

`OnboardingStep` keeps the iPhone's raw values (saved as `AppState.onboardingStep`) and its `order`.
`OnboardingNavigator` ports the Swift one: skip rules (`isSkipped`), `restore`, `next`, `back`,
`saveNext`, `go`, `progress` / `barProgress`, and `isStarting`, `isNotRunningYet`,
`hasRecentResult` for the screens. `OnboardingStepHost` maps each step to its screen; screens live
in the file named after their Swift file (`OnboardingProfileScreens.kt`...).

A screen:

```kotlin
@Composable
fun OnboardingGoalScreen() {
    val store = LocalAppStore.current
    val state by collectAppState()
    OnboardingScaffold(title = L("What's your goal?"), canContinue = state.answers.goal != null) {
        GoalType.entries.forEach { goal ->
            OptionCard(goal.title, selected = state.answers.goal == goal,
                onClick = { store.updateAnswers { it.copy(goal = goal) } })
        }
    }
}
```

- Read answers from `collectAppState()`, write them with `store.updateAnswers { it.copy(...) }`.
- The scaffold's primary button calls `next()` by itself; pass `onContinue` to do something first,
  then call `LocalOnboardingNavigator.current.next()`. `back()` is the header chevron and system back.
- `OnboardingScaffold` = header space, title/subtitle, scrolling content, primary + optional
  secondary button, `tinted` background, `buttonsVisible` for staged reveals. Screens without it
  start with `OnboardingHeaderSpace()`.
- The flow owns the fixed header (back chevron + progress bar) and the step transition. Tap
  screens (`Hello`, `GoalIntro`, `CoachReply`) get no header and draw their own reveal and back.
- After the last step the navigator calls `store.finishOnboarding()` (plan generated, app shown).
- Language: save the next step with `saveNext()`, then `AppLanguage.choose(code)`; the rebuilt flow
  opens on that step (see `OnboardingLanguageScreen.kt`).

## Who owns what

Work only in your packages. `ui/root`, `ui/main`, `core/`, `ui/theme` and `ui/components` are
shared: change them in a separate, small pull request that everyone sees. A component used by one
feature stays in that feature's package.

1. **Onboarding**: `ui/onboarding` (the screens; the navigator and scaffold only with the shell owner).
2. **Today and Plan**: `ui/today`, `ui/plan` (including `WorkoutDetailScreen`).
3. **Run and guided sessions**: `ui/run`, `ui/guided` (GPS tracking, voice cues).
4. **Coach and Progress**: `ui/coach`, `ui/progress`.
5. **Profile and paywall**: `ui/profile`, `ui/paywall` (purchases, account).

Shell (`ui/root`, `ui/main`, routes, `Navigation`): one owner, the lead. `ScreenPlaceholder` goes
away when the last screen using it is ported.
