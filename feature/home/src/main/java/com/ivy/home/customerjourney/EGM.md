# Customer Journey (com.ivy.home.customerjourney)

## Overview
The "customer journey" cards on the home tab: contextual nudges (adjust balance, add
a planned payment, discover the expenses pie chart) shown once each, until
dismissed. Each card is a `CustomerJourneyCardModel` with a suspend predicate over
the data the home tab already loaded.

## Interface
- `CustomerJourneyCardModel(id: String, condition: suspend (trnCount, plannedPaymentsCount, ivyContext, deps) -> Boolean, ...)` — card definition; `CustomerJourneyDeps(timeProvider)` carries extra dependencies.
- `CustomerJourneyCardsProvider` — `@Inject` class; `adjustBalanceCard()`, `addPlannedPaymentCard()`, `didYouKnow_expensesPieChart()`, `isCardDismissed(cardData)`, `dismissCard(cardData)`.
- `CustomerJourney(customerJourneyCards, modifier, onDismiss, ...)`, `CustomerJourneyCard(cardData, onDismiss, ...)` — composables.
- `CustomerJourneyDeps(timeProvider: TimeProvider)`.

## Invariants
- Dismissal is remembered per card id in `SharedPrefs` under `sharedPrefsKey(cardData)`; a dismissed card stays hidden across launches.
- A card is only shown when its `condition` returns true **and** it is not dismissed.
- Card ids are stable strings (`"adjust_balance"`, `"add_planned_payment"`, `"expenses_pie_chart"`) — they are the persisted keys, so renaming one resurrects the card.
- Conditions are `suspend` because they query repositories (`transactionRepository.countHappenedTransactions()`, `plannedPaymentRuleDao.countPlannedPayments()`).

## Side Effects
- Reads: transaction count and planned-payment count on each evaluation.
- Writes: `SharedPrefs` boolean per dismissed card.
- Actions navigate/switch tabs: `ivyContext.selectMainTab(MainTab.ACCOUNTS)`, `navigation.navigateTo(PieChartStatisticScreen(type = TransactionType.EXPENSE))`.

## Verification
- Run: `./gradlew :feature:home:testDebugUnitTest` (unit tests in `src/test`).
