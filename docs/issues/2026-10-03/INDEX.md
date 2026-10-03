# Widget & calendar polish — 2026-10-03

**Date:** 2026-10-03
**Branch:** `feat/widget-and-calendar-polish`
**Status:** Ready — 9 tickets, 1 commit each
**Source:** a consumer review of the four Android widgets and the in-app calendar

> Nine tickets from one user-facing review. They arrived as eight items; three of them turned out to
> be narrower or wider than the report assumed, and those corrections are recorded below rather than
> buried in the individual tickets.

---

## The tickets

| # | Title | Blocked by | Area |
|---|---|---|---|
| [FD-01](FD-01-tile-day-name.md) | Day name on the 1×1 tiles | — | `widget-glance` |
| [FD-02](FD-02-hide-adjacent-days-by-default.md) | Adjacent-month days hidden by default | — | all four |
| [FD-03](FD-03-configurable-weekend-days.md) | Configurable weekend days | — | `widget-data`, `widget-glance`, iOS |
| [FD-04](FD-04-legible-cells-and-dividers.md) | Legible cells + optional dividers | FD-02 | `widget-glance`, `ui`, iOS |
| [FD-05](FD-05-era-markers.md) | Era markers (AH / ھ, AD / ئے) | — | `ui`, `widget-data`, iOS |
| [FD-06](FD-06-widget-header-both-gregorian-months.md) | Widget header names both Gregorian months | — | `widget-data` |
| [FD-07](FD-07-day-night-colour-reresolution.md) | Day/night colours re-resolve | — | `widget-glance` |
| [FD-08](FD-08-shared-hijri-events.md) | Shared Hijri events dataset | — | `core` |
| [FD-09](FD-09-widget-day-selection-and-event-footer.md) | Widget day selection + event footer | FD-08, FD-02, FD-01 | `widget-glance` |

**Frontier:** FD-01, FD-02, FD-03, FD-05, FD-06, FD-07, FD-08 can all start immediately.
FD-04 opens once FD-02 lands. FD-09 is last.

---

## Three things the investigation changed

### Item 7 was already fixed — in the app, but not on the widget

The report was "the calendar header is only showing one Gregorian month". True of the **widget**,
false of the **in-app** calendar, which already renders a range:

```
September 2026
September - October 2026          ← Hijri month straddling two
December 2026 - January 2027
```

asserted verbatim in `HijriCalendarRangeLabelTest`. So the real defect is not a missing feature —
it is that the widget and the app **disagree** for roughly half the calendar, which is worse than
either being wrong alone. FD-06 is therefore a one-line fix in the shared projection plus a test
that holds both surfaces to the same answer.

### Item 1 needed no schema change at all

`TodayHijriWidgetData.weekdayName` exists, is populated, is localized, and is **already rendered by
iOS** (`HijriWidgetView.swift:207`). All four Android widgets simply drop it. FD-01 is render-only:
no new field, no `WidgetOptions` change, no `apiDump` diff.

### Item 5's cause is a resource, not a lifecycle

"Switching dark mode restarts the widgets" is caused by `WidgetColors.from(context)` calling
`getColor()` at compose time and handing Glance a **literal int**. The launcher cannot re-resolve a
number, so a night-mode switch invalidates the whole `RemoteViews` and rebuilds it — which is the
restart. The fix is to pass the resource id so the launcher resolves it against its own
configuration. Adding a `uiMode` broadcast receiver would make the restart *faster* and would not
remove it. FD-07 explains why that is the wrong fix.

---

## Item 4, precisely

> 2 days are showing red, like Friday and Sunday; this needs investigation why is that?

They are **Friday and Saturday**, not Friday and Sunday — `WeekDay.WEEKEND_DAYS` is
`setOf(FRIDAY, SATURDAY)`, which is the correct weekend for the Pakistan calendar this library
supports. The red is `widget_weekend_text` (`#B3261E`).

The behaviour is right and the **configuration** is missing: the set is a hardcoded literal at one
call site (`HijriCalendarWidget.kt:285`), so nobody can change it. FD-03 makes it an option, keeping
Friday+Saturday as the default and adding a name-backed enum in `calendar-widget-data` rather than a
set of cross-module enum ordinals — the WD-05 hazard, twice.

`WeekDay.WEEKEND_DAYS` itself does not change. It is a `calendar-core` default used by consumers who
never see a widget.

---

## Sequencing notes

**Three of these add a `WidgetOptions` field** (FD-02, FD-03, FD-04). Per `AGENTS.md` that is never
a one-line change, and FD-02 writes the full checklist down:

- `apiDump` regenerated, break recorded in `CHANGELOG.md`
- **`iosSimulatorArm64Test` must cover the codec** — WD-08. `WidgetOptionsJson` is shared by both
  platforms and compiling it proves nothing. A blob written by 2.0.0 must decode into this build
  with every new field at its default.
- `check-widget-glance-surface.sh` for anything public in the Glance module — that module is in
  `apiValidation.ignoredProjects`, so the surface list is the only thing reviewing it
- the consumer-check file only if a new field names a type from a new dependency (a `Boolean` names
  nothing; `WeekendPattern` is local, so nothing is needed)

**FD-05 and FD-09 change existing defaults** and so are the two that will surprise someone:

- FD-02 flips `showAdjacentDays` to `false`, which is a **major** version bump.
- FD-09 changes what a tap on a widget day *means*, which is the kind of break a user discovers by
  tapping and finding nothing happens.

---

## Gates to run before review

```
./gradlew :calendar-core:desktopTest :calendar-ui:desktopTest \
          :calendar-widget-data:desktopTest :calendar-widget-glance:testDebugUnitTest
./gradlew :calendar-widget-data:iosSimulatorArm64Test
./gradlew apiCheck detekt
./.github/scripts/check-doc-versions.sh
./.github/scripts/check-widget-glance-surface.sh
./.github/scripts/check-consumer-resolution.sh
```

FD-07 additionally needs a device pass with `adb shell cmd uimode night yes|no` and a placed widget.
No desktop test can see the restart it fixes.