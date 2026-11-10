# Legacy Transaction List UI

## Overview
The transaction list as the legacy screens render it: a `LazyListScope.transactions(...)` builder that emits upcoming/overdue sections, the history section, date dividers and one `TransactionCard` per row. Package `com.ivy.legacy.ui.component.transaction`.

## Interface
- `LazyListScope.transactions(...)` - the public entry point; private helpers `upcomingSection`, `overdueSection`, `trnItems`, `historySection`, `onTransactionClick` do the sectioning.
- `TransactionCard(...)` - one row; `TransactionTags(...)` renders its tags.
- `SectionDivider(...)` (`TransactionSectionDivider.kt`) and `HistoryDateDivider(...)` (`HistoryDateDivider.kt`), `TransactionsDividerLine(...)`.
- `category(...)` and `account(...)` in `Utils.kt` - small composables resolving a category/account for display.

## Invariants
- `TransactionCard.kt` is 917 lines and `Transactions.kt` is 372; both carry `@Suppress("CyclomaticComplexMethod", "LongMethod")` and this contract covers only their first 300 lines.
- Ordering is the caller's responsibility: the builder appends upcoming, overdue and history in that order and does not sort.
- `TransactionCard` expects the legacy `Transaction` model (not the shared `com.ivy.data.model.Transaction`); the `Legacy*`-suffixed mappers in `../../domain/datamodel/temp` exist to feed it.
- Divider composables carry no spacing of their own - spacing comes from the surrounding list item.

## Side Effects
- Pure rendering of the items it is given; taps are reported through `onTransactionClick`.
- No data loading: the lists arrive already computed by the actions in `../../domain/action/transaction`.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`
