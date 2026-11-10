# com.ivy.data.category — category hierarchy

## Overview

`CategoryTree` shapes the flat `Category` list into the two levels the picker renders, and owns the
integrity work that a missing foreign key pushes above the data layer.

## Interface

- `class CategoryTree private constructor(...)` with `val roots: List<Category>`,
  `fun childrenOf(category: Category): List<Category>`, `fun rollupIds(...)`, and a companion `of(...)`
  that builds the tree from a category list.
- Private state: `childrenByParent`, `allByParent` (includes the `null` key),
  `rootIds`, `rollups: Map<CategoryId, Set<UUID>>`, `idsByValue: Map<UUID, CategoryId>`.

## Invariants

- **`Category.parentId` has no foreign key, deliberately.** A foreign key would force the schema to pick
  a delete policy for spending history, and both options are wrong (cascade silently eats a subtree;
  no-action strands children behind a screen that no longer exists). Integrity is therefore paid for
  here.
- **Orphans are representable and must be promoted.** A child whose parent row is gone has a `parentId`
  that resolves to nothing; `of` promotes it back to the top level so its transactions stay fileable.
- **Cycles are representable.** `parentId` has no constraint, so A can be B's parent and B A's parent.
  Every traversal is guarded by a visited set — nothing here recurses without one.
- **Display depth is capped at two levels**, and anything deeper is *promoted*, never hidden, so a third
  level can never become a category the user cannot file a transaction under.
- **Rollup is not capped**: `rollupIds` walks the whole subtree, so a deeper-than-displayed chain still
  has its money counted at the top level.
- A child promoted to `roots` is excluded from `childrenOf`, otherwise it renders twice.
- Sibling names are unique per scope, and the **top level is a real scope**: `allByParent` keeps the
  `null` key precisely because two roots may not share a name.

## Side Effects

- None: `CategoryTree` is a pure in-memory structure built from a list.

## Verification

- Run: `./gradlew :shared:data:core:testDebugUnitTest`
