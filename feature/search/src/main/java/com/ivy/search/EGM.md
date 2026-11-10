# Search Package (com.ivy.search)

## Overview
Transaction search screen: takes a query, filters transactions by title/description,
and renders the matching history with the same rows used by the transactions list.
Filtering happens in-memory over what the injected use-cases return.

## Interface
- `SearchEvent` — sealed interface: `Search(query: String)`.
- `SearchState` — `searchQuery`, `transactions: ImmutableList<TransactionHistoryItem>`, `baseCurrency`, `accounts`, `categories`, `shouldShowAccountSpecificColorInTransactions`.
- `SearchViewModel` — `@HiltViewModel`, `ComposeViewModel<SearchState, SearchEvent>`; injects `TrnsWithDateDivsAct`, `AccountsAct`, `CategoryRepository`, `BaseCurrencyAct`, `AllTrnsAct`, `Features`.
- `SearchScreen(screen: SearchScreen)` — screen entry point; `SearchUi(uiState, onEvent)` renderer; previews `Preview`, `SearchUiTest`.

## Invariants
- Every keystroke produces a `Search(query)` event; the query is lowercased and trimmed once, then reused for both fields.
- Matching is case-insensitive substring matching over title and description. A blank query matches everything, because `matchesQuery` is implemented as `contains("")`.
- A transaction is included when either title or description matches; the two are OR-ed, not AND-ed.
- `shouldShowAccountSpecificColorInTransactions` is a feature flag (`Features`) and is read through the VM, not in the composable.

## Side Effects
- Reads transactions, accounts and categories from the domain use-cases on each search (no debounce beyond the query string itself).
- No writes to storage.

## Verification
- Run: `./gradlew :feature:search:testDebugUnitTest` (unit tests in `src/test`); Paparazzi snapshots through the `ivy.paparazzi` plugin tasks.
