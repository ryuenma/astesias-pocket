# Legacy Domain Models

## Overview
The deprecated entity set the legacy screens still pass around: `Account`, `Budget`, `Category`, `ExchangeRate`, `Loan`, `LoanRecord`, `PlannedPaymentRule`, `Settings`, plus `Transaction.toEntity()` for the one model that no longer has its own file.

## Interface
- `Account`, `Budget`, `Category`, `ExchangeRate`, `Loan`, `LoanRecord`, `PlannedPaymentRule`, `Settings` - all `@Deprecated("Legacy data model. Will be deleted")` and `@Immutable`.
- `Loan` carries `@Suppress("DataClassDefaultValues")`; the rest rely on plain defaults.
- `Transaction.push`-side helper: `Transaction.toEntity(): TransactionEntity` (`TransactionExt.kt`) - the legacy transaction → Room entity direction.
- Mappings to the legacy models from entities live one level down.

- [temp](./temp/EGM.md) - Bridge layer mapping Room entities (`*Entity`) to the legacy domain models and back.

## Invariants
- Every type here is annotated "will be deleted" yet is still the currency of the legacy screens - treat the annotation as a migration marker, not as permission to delete.
- `Settings.bufferAmount` is a `BigDecimal` while `SettingsEntity.bufferAmount` is a `Double`; `toEntity()` narrows, so precision beyond `Double` is lost on every write.
- `Settings.id` defaults to `UUID.randomUUID()`, so a `Settings` constructed without an id is a **different** row; only `toEntity()` of a loaded model keeps the original identity.
- `Transaction.toEntity()` is a one-way helper - the reverse lives in `./temp` because it needs a `TransactionMapper`.

## Side Effects
- `Settings.toEntity()` produces a Room entity; writing it is the caller's job.
- No I/O in this package itself.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`
