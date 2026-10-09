# FD-01: The 1×1 tiles show no day name — "۲۱ محرم" is not a date

**Issue:** #7
**Severity:** Medium
**Blocks:** FD-04, FD-09
**Blocked by:** None (can start immediately)
**Module:** `calendar-widget-glance`
**Status:** Ready

---

## Problem

The two 1×1 tiles — `HijriDateWidget` and `GregorianDateWidget` — render a day figure over a month
name and nothing else:

```kotlin
// calendar-widget-glance/src/main/.../HijriDateWidget.kt:184-204
Text(text = dayText,   style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, ...))
Text(text = monthText, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, ...))
```

A widget on a home screen is read in a glance, and the thing a glance needs most is *when it is*.
`21 محرم` does not answer that. `جمعرات ۲۱ محرم` answers it. The user's question on seeing the tile
is "what day is today?", and the tile currently refuses to say.

## Why it is only Android

This is the part worth recording, because it means the defect is narrower than it looks.

`TodayHijriWidgetData` **already carries the answer**:

```kotlin
// calendar-widget-data/src/commonMain/.../HijriWidgetModels.kt:130
val weekdayName: String
```

It is populated at `WidgetDataApi.kt:408` from `options.localizedWeekdayNames`, so it is already
localized, already follows `options.language`, and already honours `weekStart` indirectly (the name
is the *weekday's* name, not a column index — the caller does not need to know where it sits).

And iOS already renders it:

```swift
// iosApp/HijriWidgetExtension/HijriWidgetView.swift:207
Text(today.weekdayName)
```

So this is **not** a missing feature. It is a field that was projected, consumed on one platform,
and silently dropped on the other. All four Android widgets ignore it — `DateTileRoot`,
`HijriTodayRoot`, `TodayCard` and `MonthGrid` alike. No schema change, no wire-format change, no
`apiDump`. The field is already in the ABI.

## The layout squeeze

A 1×1 tile is 40dp minimum (`res/xml/hijri_date_widget_info.xml`, `minWidth/minHeight=40dp`,
`resizeMode="none"`). It currently stacks 34sp + 2dp padding + 12sp = 48sp of text into 40dp, which
already overflows and is why Glance clips it. Adding a third line makes it worse.

The resolution is to accept that **the day figure is not 34sp any more**. It is a glance target, not
a display type — 26sp still reads at arm's length in a 40dp tile and frees 8sp for the weekday line.
28sp is the ceiling; 30sp pushes the month name out of the tile.

## Proposed change

Three lines in `DateTileRoot`, in the order weekday → day → month:

```kotlin
Column(
    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    verticalAlignment = Alignment.Vertical.CenterVertically,
) {
    Text(text = today?.weekdayName.orEmpty(), style = TextStyle(fontSize = 10.sp, ...))
    Text(text = dayText,   style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, ...))
    Text(text = monthText, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, ...))
}
```

The **static Android 12–14 preview layouts** must change with it, or the picker will show one thing
and the home screen another. `res/layout/hijri_date_widget_preview_layout.xml` hardcodes
`android:text="۲۱"` at `34sp` — it is a hand-maintained mirror of the live tile, and every one of its
numbers has to be updated by hand here.

The `monthUnavailable` fallback keeps its place: when there is no readable date the tile says so and
shows no weekday line.

## Acceptance criteria

- [ ] Both 1×1 tiles show the localized weekday name above the day figure, in that order.
- [ ] The weekday name follows `options.language` — an Urdu widget says `جمعرات`, an English one
      says `Thursday`. It is not a `WeekDay.shortName` lookup in the Glance module.
- [ ] The day figure is 28sp and the month name 11sp, and all three lines fit inside 40dp without
      clipping at the tile's minimum size.
- [ ] `res/layout/hijri_date_widget_preview_layout.xml` and
      `res/layout/gregorian_date_widget_preview_layout.xml` match the live tile line for line.
- [ ] A test asserts the tile renders a weekday string, so this cannot regress back to being dropped.
- [ ] `WidgetOptions`, `WidgetOptionsJson` and `api/*` are **unchanged** — this ticket is render-only
      and must not produce an `apiDump` diff.

## Note for whoever picks this up

Do not "improve" this by adding a `weekdayName` field to `WidgetOptions`. The name is derived from
the date, not configured. Only `language` and `weekStart` affect it, and both already feed
`localizedWeekdayNames`.