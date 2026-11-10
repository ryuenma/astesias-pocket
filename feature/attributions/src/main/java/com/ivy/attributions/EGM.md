# Attributions Package (com.ivy.attributions)

## Overview
Static "open-source attributions" screen: lists third-party libraries grouped by
section (`AttributionItem.Divider` as section header, `AttributionItem.Attribution`
as a linked row). All content is hard-coded in `AttributionsProvider`; nothing is
fetched at runtime.

## Interface
- `AttributionItem` — sealed interface: `Attribution(name: String, link: String)`, `Divider(sectionName: String)`.
- `AttributionsProvider` — `@Inject` class with `provideAttributions(): ImmutableList<AttributionItem>`; the single source of the licence list.
- `AttributionsViewModel` — `@HiltViewModel`, `ComposeViewModel<AttributionsState, AttributionsEvent>`; builds state from `AttributionsProvider`, `onEvent` is a no-op.
- `AttributionsState` — `attributionItems: ImmutableList<AttributionItem>`.
- `AttributionsEvent` — empty sealed interface (screen is read-only).
- `AttributionsScreenImpl(viewModel: AttributionsViewModel = screenScopedViewModel())` — screen entry point.
- Private UI: `AttributionsUI`, `TopAppBarTitle`, `BackButton(nav: Navigation)`, `AttributionsContent`, `AttributionLayout`, `AttributionCard`, `AttributionsSectionDivider`.
- Previews: `AttributionsUIPreview`, `AttributionScreenUiTest`.

## Invariants
- The screen has no writable state: `AttributionsEvent` declares no members and `onEvent` does nothing.
- `AttributionItem.Divider` must appear before the `Attribution`s of its section — the LazyColumn renders items in list order from the provider.
- Every `Attribution.link` is a URL rendered as an external link; no local navigation happens for attributions.

## Side Effects
- None on the data layer. The only external interaction is opening `Attribution.link` in a browser when a card is tapped.
- `BackButton` navigation pops the current screen through `Navigation`.

## Verification
- Run: `./gradlew :feature:attributions:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.
