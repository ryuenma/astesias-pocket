# Legacy Line Chart

## Overview
Single-file Canvas chart used by the legacy reports and wallet balance screens. `IvyLineChart` draws one or more `Function`s (each a list of `FunctionPoint`s) plus an optional tapped point, all inside a hand-rolled `Canvas` rather than a charting library. The package is `com.ivy.wallet.ui.theme.components.charts.linechart` even though the directory lives under `com/ivy/legacy/...`.

## Interface
- `IvyLineChart(...)` - the only public composable; draws the chart and reports taps.
- `Value`, `Function`, `TapEvent`, `FunctionPoint` - data types describing the series and tap events, declared in `IvyLineChart.kt`.
- Private `DrawScope` extensions (`drawFunctions`, `drawFunction`, `drawTappedPoint`) and private coordinate helpers do all drawing.
- `Offset.distance(...)` - private geometry helper used for tap hit-testing.

## Invariants
- The whole file is annotated `@Deprecated("Old design system. Use `:ivy-design` and Material3")`; it exists because legacy screens still call it.
- Coordinates are computed from the series' min/max value range, so an empty or single-point series must be handled by the caller before drawing.
- `IvyLineChart.kt` is 695 lines; this contract enumerates declarations from the first 300 lines only (read cap).

## Side Effects
- No storage, network or logging: pure Canvas drawing inside the composition.
- Tap handling is reported through the callback passed by the caller; the chart holds no state of its own.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`
