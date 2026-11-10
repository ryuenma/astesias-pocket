# ivy.automate.issue (create comment)

## Overview
Issue package of the create-comment action. Note the nesting: the issue handler lives one level deeper
in `create/`, so this directory is a pure namespace and no longer shares a package with the assign
action (`:ci-actions:issue-assign` has its own `ivy.automate.issue` package in a different module).

## Subdirectories & Boundaries
- `create`: `./create/EGM.md` — `ivy.automate.issue.create`: welcome comment entry point
