# FD-05: No era marker anywhere — years read as bare numbers

**Issue:** #10
**Severity:** Low
**Blocks:** —
**Blocked by:** None (can start immediately)
**Module:** `calendar-ui`, `calendar-widget-data`, `calendar-widget-glance`, `iosApp`
**Status:** Ready

---

## Problem

Nothing in the project says which calendar a year belongs to. The in-app header renders:

```
رمضان 1447
رمضان - شوال 1447
```

and the widget renders `۲۱ محرم ۱۴۴۸` with a bare year. The Gregorian side is the same story: `2026`,
`August`, `2026`.

The report asks for `AH` in English and `ھ` in Urdu alongside the Hijri year, and the equivalent
`AD` / `ئے` alongside the Gregorian year.

This matters more than it looks for a **bilingual** calendar. A Hijri calendar app in Urdu shows the
Gregorian date as a gloss under the Hijri one. `١٤٤٨` and `2026` are the same digits rotated; a
reader cannot tell from the header alone which is the calendar's own year, because there is no marker
to break the ambiguity. The era suffix is one glyph per year and it removes the whole ambiguity.

## Where the strings have to live

Two entirely different mechanisms, and neither is a string resource.

**In-app: `HijriCalendarLabels`.** `calendar-ui` has no `res/` and no `Res.x` — every user-visible
string goes through one `@Immutable data class` whose fields are function-valued. The default
`headerTitle` is:

```kotlin
// calendar-ui/.../HijriCalendarLabels.kt:51-53
val headerTitle: (monthName: String, year: Int) -> String = { month, year -> "$month $year" }
```

Two constraints, both load-bearing:

- **Every new field needs a default that reproduces today's output.** This type is public API on a
  published artifact with a binary-compat golden file; a field without a default breaks every
  consumer who constructs it by name.
- **The default must still render no era marker.** This is the one ticket where "default to the new
  behaviour" would be a silent break for every existing consumer, so the default stays bare and the
  *sample's* Urdu bundle opts in. `UrduCalendarLabels.kt` already localizes month and weekday names
  and is exactly where the Urdu `ھ` belongs.

The year is rendered by `headerTitle`, so the era suffix has to be **inside** that lambda — the label
owns the whole title line, which is what lets a locale control digit system and ordering at once.

**Widgets: `WidgetLocalization`.** Per WG-12, a widget's chrome is localized from `options.language`,
never from `res/values`, because Glance renders from the host's single `Context` with no per-widget
`Configuration`. `WidgetLocalization.ChromeLabels` already holds `nextMonth` / `previousMonth` /
`goToCurrentMonth` / `monthUnavailable` in Urdu and English; `hijriEra` and `gregorianEra` go there,
in the same shape.

## The strings

| | Hijri | Gregorian |
|---|---|---|
| English | `AH` | `AD` |
| Urdu | `ھ` | `ئے` |

Placement follows the language's own convention rather than being hardcoded either way: English puts
the era **after** the year (`1447 AH`), Urdu puts it after too (`١٤٤٧ ھ`) but with the digit system
the rest of the Urdu bundle uses. Do not build a "suffix" vs "prefix" flag into the schema for this —
it is a formatting decision, and it belongs in the label lambda where the locale already lives.

`AD` rather than `CE` is deliberate: the Urdu `ئے` and the Indian convention both use the AD lineage,
and mixing `CE` into a Hijri calendar's chrome is the kind of thing that reads as an import.

## Acceptance criteria

- [ ] `HijriCalendarLabels` gains era fields whose defaults reproduce today's output byte for byte —
      the existing `HijriCalendarRangeLabelTest` assertions pass untouched.
- [ ] A localized `headerTitle` renders `1447 AH` and `رمضان ١٤٤٧ ھ`.
- [ ] `UrduCalendarLabels.kt` opts in with the Urdu era forms, using the bundle's digit system.
- [ ] `WidgetLocalization.ChromeLabels` gains `hijriEra` / `gregorianEra` in Urdu and English,
      following `options.language`.
- [ ] All four Android widgets render the era on their year: the grid header, the today strip, and
      both 1×1 tiles (the tiles currently render **no year at all** — they need one added alongside
      the era, which is why this ticket is not as small as it looks).
- [ ] The in-app selected-date card shows both eras.
- [ ] iOS: `HijriWidgetView.swift` renders the same forms.
- [ ] `apiDump` regenerated; `CHANGELOG.md` records the additive change.

## Scope note

The 1×1 tiles are the awkward part: they are 40dp and after FD-01 they stack three lines. Adding a
year means a fourth, or replacing the month name with `محرم 1447 AH` on one line. **Replacing** is
the only way it fits. Decide this here, not during implementation — FD-01 shrinks the day figure for
exactly this reason, and undoing that workarounds a layout problem the two tickets create together.