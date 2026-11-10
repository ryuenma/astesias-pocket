# Entity ↔ Legacy Bridge

## Overview
The mapping layer between the Room entities (`*Entity`) and the deprecated legacy domain models. Everything that still parses a legacy model in this module goes through these extensions, and the `Transaction` pair (`toLegacy`/`toDomain`) is the only supported way to move between the shared `com.ivy.data.model.Transaction` and `com.ivy.base.legacy.LegacyTransaction`.

## Interface
- `AccountEntity.toLegacyDomain()`, `BudgetEntity.toLegacyDomain()`, `CategoryEntity.toLegacyDomain()`, `ExchangeRateEntity.toLegacyDomain()`, `LoanEntity.toLegacyDomain()`, `LoanRecordEntity.toLegacyDomain()`, `PlannedPaymentRuleEntity.toLegacyDomain()`, `SettingsEntity.toLegacyDomain()`.
- `Transaction.toLegacy(mapper: TransactionMapper): LegacyTransaction` and `suspend fun LegacyTransaction.toDomain(mapper): Transaction?` - the round trip.
- `TransactionEntity.toLegacyDomain(tags: ImmutableList<LegacyTag> = persistentListOf())` - entity to legacy model, with tags supplied separately.
- `Tag.toLegacyTag()`, `List<Tag>.toImmutableLegacyTags()` - the tag bridge.
- `TransactionEntity.isIdenticalWith(other)` - equality ignoring sync flags.
- Budget string plumbing: `serialize(ids)`, `parseCategoryIds()`, `parseAccountIds()`, `budgetType(categoriesCount)`, `validate()`; `LoanEntity.humanReadableType()`.

## Invariants
- The round trip is lossy at one end: `LegacyTransaction.toDomain` returns `Transaction?` because `TransactionEntity.toDomain()` can fail validation, so a legacy transaction that came from an invalid entity disappears rather than throwing.
- `toLegacy` goes through the entity (`toEntity().toLegacyDomain()`), so it inherits every conversion the entity performs - notably `amount` and `toAmount` become `Double`-backed and are re-widened with `toBigDecimal()`.
- `toLegacyDomain` defaults `tags` to an empty list: a caller that forgets to pass the tags gets a transaction with no tags, silently.
- `isIdenticalWith` deliberately clears `isSynced` and `isDeleted` before comparing - that is what makes it usable for change detection, and it means the two flags can never be part of an equality result.
- Budget category/account ids live in the entity as serialised strings; `parseCategoryIds()`/`parseAccountIds()` are the only sanctioned readers of that format.

## Side Effects
- Reads the mapper (`TransactionMapper`) and DB entities; writes nothing.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`
