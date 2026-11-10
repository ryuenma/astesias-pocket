# com.ivy.ui.component — shared composables

## Overview

Reusable, stateless composables small enough to be shared by every screen. Currently a back button
and the open-source attribution card.

## Interface

- `@Composable fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier)` — a
  `Material3.IconButton` wrapping the auto-mirrored `ArrowBack` icon; hard-coded
  `contentDescription = "Back"`.
- `@Composable fun OpenSourceCard(modifier: Modifier = Modifier)` — an `ElevatedCard` that opens
  `IvyWalletGitHubRepoUrl` via `LocalUriHandler`.
- `const val IvyWalletGitHubRepoUrl = "https://github.com/Ivy-Apps/ivy-wallet"`.
- `private @Composable GitHubLogo(...)`, `private @Composable OpenSourceTexts(...)`.

## Invariants

- Both composables take `modifier: Modifier = Modifier` as their **second** parameter and apply it
  to their root: callers must pass layout modifiers in, never wrap in a `Box` to fake padding.
- `BackButton.contentDescription` is a literal `"Back"`, not a string resource — it is intentionally
  not localized yet. Changing it is a behaviour change for accessibility services.
- `OpenSourceCard` uses `com.ivy.ui.R` (this module's namespace) for `github_logo` and
  `ivy_wallet_open_source`; those resources live in this module's `res/`.
- `IvyWalletGitHubRepoUrl` is a `const val` used by `OpenSourceCard` and by its Paparazzi test.

## Side Effects

- `OpenSourceCard` launches an external browser/app through `LocalUriHandler.openUri` when tapped.

## Verification

- Run: `./gradlew :shared:ui:core:testDebugUnitTest`
- Screenshot gate: `./gradlew :shared:ui:core:verifyPaparazziDebug`
