# Legacy Tag UI

## Overview
Tag editing and display for transactions: the add/view button pair, the modal that lists existing tags, and the modal that creates or renames one. Package is `com.ivy.legacy.ui.component.tags`.

## Interface
- `AddTagButton(...)` with private `ViewTagsButton`/`AddTagsButton` - the two states of the tag affordance.
- `ShowTagModal(...)` with private `TagList`/`ExistingTag` - lists and selects tags for a transaction.
- `AddOrEditTagModal(...)` - creates or renames a tag.

## Invariants
- `ShowTagModal` uses `ExperimentalFoundationApi` explicitly; callers do not need the opt-in.
- Both modals are `BoxWithConstraintsScope` extensions and rely on `IvyModal` chrome from `../../legacy/ui/theme/modal`.
- Tags are persisted by the caller (repository layer); these composables only collect input.

## Side Effects
- Transient Compose state (typed tag name, selection) only.

## Verification
- Run: `./gradlew :temp:legacy-code:testDebugUnitTest`
