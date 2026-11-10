# Compose Stability Model

## Overview
Report row types for the Compose stability check. `Main.kt` parses the Compose compiler's
`*_composables.csv` / `*_composables.txt` output into these types before rendering its text report.

## Interface
- `typealias FullyQualifiedName = String`
- `data class UnstableComposable(val fullyQualifiedName: FullyQualifiedName, val name: String, val skippable: Boolean, val restartable: Boolean, val unstableArguments: Set<ComposableArgument>)`
- `data class ComposableArgument(val name: String, val type: String)`

## Invariants
- A `UnstableComposable` present in a report has already been filtered to `!restartable || !skippable`
  by the parser; being in the model means the composable is non-skippable or non-restartable.
- `unstableArguments` is a `Set` — a parameter name/type pair appears at most once per composable.
- Equality is structural, so report de-duplication is a plain `Set`/`distinct` operation.

## Side Effects
- None. Immutable data holders.

## Verification
- Run: `./gradlew :ci-actions:compose-stability:run`
