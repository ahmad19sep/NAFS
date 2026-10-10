# NAFS Android design system

The native app's look is defined in code, in `app/src/main/java/com/ahmad/nafs/ui/theme/` (tokens) and
`ui/components/` (building blocks). Screens should be assembled from these pieces, not from raw Material
widgets with one-off colours, so every part of the app feels like one product.

## Principles

1. **Calm, then clear.** Deep evergreen at night, warm paper by day. One hero moment per screen; the rest is
   quiet cards on the background.
2. **Gold is earned.** Gold marks scores, streaks, levels and the Quick Add button. Emerald (primary) is for
   actions and selection. Don't use gold for ordinary buttons.
3. **Every area has a colour.** Tasks sky, habits emerald, goals gold, challenges coral, health rose, Deen
   lilac, coach cyan, reflection amber (`Nafs.colors.tasks` … `Nafs.colors.reflect`). Use it for the area's
   icon badges and progress, never for body text.
4. **Honest states.** Loading, empty, offline, error and saving are designed states, not afterthoughts. Say
   what happened and what to do next in one sentence.
5. **Thumb first.** Primary actions sit low and wide (54 dp buttons); tap targets are at least 48 dp; forms go
   in bottom sheets or dialogs that keep the keyboard in mind (`imePadding`).

## Tokens

| Token | Where | Notes |
|---|---|---|
| `MaterialTheme.colorScheme` | `Theme.kt` | Full M3 roles for dark and light, including surface containers |
| `Nafs.colors` | `Theme.kt` | success / warning containers, gold, hero gradient, card border, feature accents |
| Type | `Type.kt` | Plus Jakarta Sans for everything; Amiri for Arabic (`AmiriArabic`) |
| Shapes | `Theme.kt` | 8 / 12 / 18 / 24 / 30 dp |
| Spacing | `NafsSpace` | screen gutter 20, between sections 28, between items 12, card padding 18 |

Surface layering: page `background` → cards `surfaceContainer` (+ 1 dp `Nafs.colors.cardBorder`) → inner
wells `surfaceContainerHigh`. No drop shadows on cards.

## Components (`ui/components`)

| Need | Use |
|---|---|
| Tab screen title | `NafsPageHeader(title, eyebrow, subtitle, actions)` as the first list item |
| Pushed screen bar | `NafsTopBar(title, onBack, subtitle, actions)` inside `statusBarsPadding()` |
| Section heading | `NafsSectionTitle(title, supporting, actionLabel, onAction)` or `NafsEyebrow` |
| Card | `NafsCard(onClick?, color, contentPadding) { … }` |
| Hero | `NafsHeroSurface { … }` / `NafsHeroCard(eyebrow, title, body)` — text uses `Nafs.colors.onHero*` |
| Icon / emoji tile | `NafsIconBadge(icon, tint)` / `NafsEmojiBadge(emoji, tint)` |
| Avatar | `NafsAvatar(name, photoUrl, size)` |
| Labels | `NafsPill(text, color, icon)`, `NafsStatusPill` |
| Choices | `NafsChoiceChip` (multi/single chips), `NafsSegmented` (2–4 views) |
| Progress | `NafsProgressBar`, `NafsProgressRing { centre }`, `NafsStat` |
| Rows | `NafsListRow(title, subtitle, icon, trailing, onClick)` inside `NafsGroup(title) { … }` with `NafsDivider()` |
| Buttons | `NafsPrimaryButton(text, onClick, loading, icon)`, `NafsSecondaryButton` |
| Fields | `NafsTextField(value, onValueChange, label, leadingIcon, supportingText, isError …)` |
| Messages | `NafsBanner(message, kind = INFO/SUCCESS/WARNING/ERROR/OFFLINE, actionLabel, onDismiss)` |
| States | `NafsEmptyState`, `NafsErrorState`, `NafsLoadingState` |
| Brand | `NafsBrandMark`, `NafsBrandLockup` |

## Screen anatomy

```
statusBarsPadding
NafsPageHeader / NafsTopBar
[NafsBanner for offline or errors]
Hero (optional, one per screen)
Section title → cards …
Section title → cards …
bottom spacer (the tab bar already pads the content)
```

## Screenshots

`app/src/test/java/com/ahmad/nafs/screenshots/` renders every screen with sample state in dark and light — about
60 screens and states. Test classes extend `ScreenshotTestBase` and call `captureScreen(name) { … }` once per
test; shared sample records live in `SampleData`. `./gradlew :app:recordRoborazziDebug` writes the PNGs to
`app/build/outputs/roborazzi/`, and CI publishes them as the `screenshots` artifact.

To be renderable, each screen is split into a `…Route` (ViewModel wiring, permission launchers, effects) and a
stateless `internal` screen that takes a state object and an actions class with no-op defaults.
