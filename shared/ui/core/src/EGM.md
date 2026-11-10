# src — shared/ui/core source sets

## Overview

Source-set container for `:shared:ui:core`. `main/` holds production code and Android resources;
`test/` holds JVM unit tests and Paparazzi snapshot tests.

## Interface

- `main/` → [./main/EGM.md](./main/EGM.md)
- `test/java/com/ivy/ui/` — `FormatMoneyUseCaseTest`, `PaparazziScreenshotTest`,
  `component/OpenSourceCardPaparazziTest`, `time/impl/AndroidDateTimePickerTest`,
  `time/impl/IvyTimeFormatterTest`, plus `snapshots/images/*.png`. Test-only; no contract file.

## Subdirectories & Boundaries

- [main/](./main/EGM.md) — Production source set of :shared:ui:core with a Kotlin root java/ and an Android resource root res/.

## Invariants

- No `androidTest/` source set: this module is verified with JVM tests and Paparazzi, not on a device.

## Side Effects

- None.
