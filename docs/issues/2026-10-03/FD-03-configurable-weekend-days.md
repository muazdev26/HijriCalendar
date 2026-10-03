# FD-03: Two columns are red because the weekend set is hardcoded — make it an option

**Issue:** #9
**Severity:** Medium
**Blocks:** —
**Blocked by:** None (can start immediately)
**Module:** `calendar-widget-data`, `calendar-widget-glance`, `sample-android-app`, `iosApp`
**Status:** Ready

---

## Problem

The report is "two days are showing red, like Friday and Sunday — every calendar only shows one."

The mechanism is one line, hardcoded at the only Android call site that builds a month projection:

```kotlin
// calendar-widget-glance/src/main/.../HijriCalendarWidget.kt:274-290
internal fun buildMonthData(
    options: WidgetOptions, viewedMonth: HijriYearMonth?,
    todayHijri: TodayHijriWidgetData?, layoutRtl: Boolean,
): HijriMonthWidgetData? {
    val (year, month) = resolveGridMonth(options, viewedMonth, todayHijri) ?: return null
    return buildHijriMonthWidgetData(
        hijriYear = year, hijriMonth = month, options = options,
        weekendDays = WeekDay.WEEKEND_DAYS,   // ← setOf(FRIDAY, SATURDAY)
        rightToLeft = layoutRtl,
    )
}
```

and `WeekDay.WEEKEND_DAYS` is:

```kotlin
// calendar-core/src/commonMain/.../WeekDay.kt:45-46
public val WEEKEND_DAYS: Set<WeekDay> = setOf(FRIDAY, SATURDAY)
```

So Friday **and Saturday** paint in `R.color.widget_weekend_text` (`#B3261E` light, `#F2B8B5`
night). Not Friday and Sunday — Friday and Saturday, which is the correct weekend for Pakistan and
is what the "Calculate / Pakistan" mode exists to serve. The user read it as a bug because the
nearest comparison in their head was Google Calendar, where Sunday is the only red column.

**The bug is not the colour. The bug is that there is nothing to configure.** The set is a literal
at one call site, and a user whose weekend is Saturday–Sunday, or Sunday only, or who follows a
work week with no religious day off, cannot change it. They can only choose between "two red columns
that are right for Pakistan" and "two red columns that are right for somewhere else" — neither of
which is theirs.

## Why this is not a boolean

The obvious fix is a `weekendDays: Set<WeekDay>` field on `WidgetOptions`. **Do not do that.** It is
precisely the WD-05 hazard the schema already removed once:

> `firstDayOfWeekIndex` is gone from the schema (WD-05): it was a bare ordinal into `WeekDay`,
> another module's enum, and the wire format exists to avoid exactly that.

A set of cross-module enum names serializes fine *until* someone reorders `WeekDay`, at which point
every stored widget silently changes meaning. The replacement must be name-backed and owned by
`calendar-widget-data`, exactly like `WeekStart` already is:

```kotlin
@Serializable
public enum class WeekendPattern {
    FRIDAY_SATURDAY,   // Pakistan, Gulf — the current behaviour
    SUNDAY,
    FRIDAY_ONLY,
    NONE,
}
```

The four patterns cover every convention with a plausible audience. A `Set<WeekDay>` supports
combinations nobody wants and every combination nobody wants.

Every entry needs an `@JsonNames` alias, as AGENTS.md requires — including `"UM_AL_QURA"`-style
accurate spellings — and the decoder's `LEGACY_ENUM_KEYS` set in `HijriWidgetConfig` gains
`"weekendPattern"` so a blob that predates this field still routes correctly through
`hasLegacyOrdinalEnum`. A blob with no such key routes to the shared decoder, which fills the
default. That is the whole compatibility story, and it is the reason this is not a five-line change.

## The in-app side is already done

`HijriCalendarState.weekendDays: Set<WeekDay>` is a real constructor parameter, threaded into
`toCalendarMonth` and into every cell's `isWeekend` across all three calendar spaces. The sample just
never sets it. FD-03 wires the new option through to it rather than adding anything:

```kotlin
// sample-shared/.../CalendarScreen.kt:83-89
rememberHijriCalendarState(..., weekendDays = options.weekendPattern.toWeekDays())
```

with `toWeekDays()` living in `calendar-widget-data` next to the enum, so Android and iOS resolve
the pattern identically instead of each hand-rolling a `when`.

## Acceptance criteria

- [ ] `WeekendPattern` lives in `calendar-widget-data`, is `@Serializable`, name-backed, and every
      entry carries `@JsonNames` aliases.
- [ ] `WidgetOptions.weekendPattern` defaults to `FRIDAY_SATURDAY`, so an existing widget is
      unchanged after upgrade.
- [ ] The hardcoded `WeekDay.WEEKEND_DAYS` at `HijriCalendarWidget.kt:285` is gone; `buildMonthData`
      reads the option.
- [ ] `WeekendPattern.toWeekDays()` is public in `calendar-widget-data` and is the single place the
      mapping exists.
- [ ] The in-app calendar's red days follow the same option, through
      `HijriCalendarState.weekendDays`.
- [ ] The static Android 12–14 preview layout is reconciled: it hardcodes four `widget_weekend_text`
      cells in the Friday+Saturday columns, so it must follow the default rather than drift.
- [ ] The sample settings screen has a radio row over the four patterns.
- [ ] A test asserts a non-default pattern round-trips through `WidgetOptionsJson` **by name**, and
      that a blob containing `"SUNDAY"` decodes to `SUNDAY` and not to an ordinal.
- [ ] iOS: the widget extension honours the option, and `WidgetCatalogView.swift` exposes it.
- [ ] `apiDump` regenerated; `CHANGELOG.md` records the addition.

## Do not

Do not change `WeekDay.WEEKEND_DAYS`. It is a `calendar-core` default used by consumers who never
see a widget, and silently changing it would break every in-app calendar in the wild for a reason
that has nothing to do with them. The default moves in the *widget schema*, not in core.