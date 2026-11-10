# FRP ViewModel Helper

## Overview
The single helper the FRP layer offers ViewModels.

## Interface
- `MutableStateFlow<T>.readOnly(): StateFlow<T>` - exposes a mutable flow as read-only, returning the same instance.

## Invariants
- `@Deprecated("Legacy code. Don't use it, please.")` - it upcasts rather than wrapping, so the returned `StateFlow` is still the same object and a caller that casts it back can mutate it. It is a compile-time convenience, not encapsulation.

## Side Effects
- None.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`
