# FD-09: Tapping a widget day opens the app — make it select the day and name the event

**Issue:** #15
**Severity:** High
**Blocks:** —
**Blocked by:** #13, #8, #7
**Module:** `calendar-widget-glance`, `sample-android-app`
**Status:** Ready (once the blockers land)

---

## Problem

Every tappable region on a widget today does the same thing: it opens the app.

```kotlin
// calendar-widget-glance/src/main/.../HijriCalendarWidget.kt — HijriWidgetRoot / WidgetActions
openAction = actionStartActivity(Intent(context, MainActivity::class.java))
```

That is a launcher-style shortcut wearing a calendar's clothes. The widget shows a month grid with
42 tappable day cells, and **not one of them does anything a widget cell can do** — select the day,
show what it is. The user has to open an app, wait for it, then hunt for the date they just tapped.

The request is that tapping a date behaves as it does in the in-app calendar, and that the widget
then names the event for that day.

## Why the tap cannot stay a shortcut

The in-app calendar's tap is already correct and already the right model:

```kotlin
// calendar-core/.../HijriCalendarState.kt:284-305
public fun selectDay(day: CalendarDay) { /* Pakistan → observed → UAQ */ }
public fun selectDate(hijriDate: HijrahDate) {
    if (date.yearMonth != _currentMonth) _currentMonth = date.yearMonth
}
```

So the in-app equivalent of "tap a day" is *select it, and make the grid show it*. The widget has
two of the three pieces already and is missing the third:

| | in-app | widget |
|---|---|---|
| Persisted per-widget state | `HijriCalendarState` in `rememberSaveable` | viewed month, in Glance's preferences store |
| Persisted selection | `_selectedDate` / `_selectedPakistanDate` / `_selectedObservedDate` | **missing** |
| Tap → state | `selectDay(day)` | opens the app |

The selection is the gap. Once it exists, the tap becomes a two-line `ActionCallback`.

## Persisting the selection

It goes beside the viewed month, in the same Glance preferences store, through the same
suspend API — **not** in the `WidgetOptions` JSON:

```kotlin
internal suspend fun setSelectedDate(context, glanceId, year, month, day)
internal suspend fun clearSelectedDate(context, glanceId)
internal suspend fun loadSelectedDate(context, glanceId): HijriYearMonth?
```

`WidgetOptions` is the *user's configuration*; a selection is *the widget's current position*, and
mixing them would put a transient value into the family mirror and into every settings screen. The
existing `HijriWidgetConfig` KDoc already draws this line for the viewed month. Follow it.

The reactive read is what makes this safe. `provideGlance` already composes against
`currentState<Preferences>()`, so writing the selection and then calling `update()` recomposes with
the newest value — the same mechanism AGENTS.md describes as making a "stale render stomps a tap
back" race impossible. Do not add a mutex; that pattern was removed on purpose.

**Write the selection before calling `update()`**, for the same reason navigation does. If the order
flips, a fast double-tap can settle on the first tap's selection.

## The precedence, and one decision not to skip

The grid month resolves as:

```
viewed > pinned > today
```

(WD-03, via the single `resolveGridMonth`). The selection must **not** join that chain. It is not a
month preference — it is a mark on a grid. A widget pinned to Safar 1448 that remembers a tap on a
day in the displayed month should still show Safar 1448.

The one exception worth deciding explicitly: does tapping a leading/trailing cell (FD-02's adjacent
days, visible when `showAdjacentDays = true`) move the grid to that day, as the in-app calendar does?
**Recommendation: yes**, and it must be decided here rather than left to whichever implementation
runs first, or the two surfaces diverge on the one case where they are compared directly.

## The event footer

Below the grid, one line naming the selected day's event, per FD-08:

```
جمعرات ۱۴ محرم ۱۴۴۸ ھ
عید الفطر
```

Footer behaviour:

- **Only when the selection has an event.** A footer reading `null` or a blank row is worse than no
  footer, and a grid whose height changes because *some* days have events is unusable.
- **Height is constant regardless**, so the grid does not jump between days. Reserve the line; fill
  it with an event name or leave it empty.
- **Localized from `options.language`**, per WG-12 — never `getString`.
- **Cleared by the "today" reset** and by navigating to a different month, because a selection from a
  month you have left is not an answer about the month you are looking at.

On a compact widget (`size.width < 180.dp`) there is no room and the footer is omitted. That is
already the `useCompact` branch — do not add a third size tier for it.

## Replacing the open-app action

The whole widget is currently a tap target, and so is the header (tapping it resets to today). Two
decisions:

- **The grid background and the arrows keep their behaviour.** Month navigation must not also mean
  "select".
- **"Open app" must survive somewhere.** A widget that can no longer launch its own app is a
  regression for anyone using it as a shortcut. Give it a dedicated target — the header title tap
  currently resets to today, which is close but not the same intent. A long-press to open the app is
  the platform-idiomatic answer and keeps single-tap unambiguous.

## Acceptance criteria

- [ ] Tapping a day in the widget grid selects it; the selected cell renders with the same
      `widget_today_background` treatment as today, or a visually distinct one. **Decide which** —
      reusing the today fill means a day cannot be both today and selected, which is a real case.
- [ ] The selection is persisted per widget in the Glance preferences store and survives a host
      restart, a reboot and a re-render.
- [ ] The selection is **not** written to `WidgetOptions`, the family mirror, or any settings screen.
- [ ] A tap on an adjacent-month cell moves the grid to that day, matching the in-app calendar.
- [ ] The footer names the selected day's event, localized from `options.language`, and is omitted
      when the day has none.
- [ ] The footer's presence does not change the widget's height.
- [ ] Tapping the header reset clears the selection as well as the viewed month.
- [ ] The compact (today-card) layout has no footer and no day taps.
- [ ] The widget can still be opened from the app, by a mechanism that does not collide with the day
      tap.
- [ ] A double-tap settles on the second tap's selection — the render queue coalesces into one
      `update()` and sees the newest value.
- [ ] The Android 12–14 static preview layouts are unaffected (no interaction there).
- [ ] `apiDump` regenerated; `check-widget-glance-surface.sh` updated — this adds public
      `HijriWidgetConfig` members, and that module is in `apiValidation.ignoredProjects`, so the
      surface list is the only thing reviewing them.
- [ ] `CHANGELOG.md` records the tap-behaviour change prominently; it is the kind of break a user
      discovers by tapping and finding nothing happens.

## Why this is last

It is the only ticket that changes what a tap *means*, it depends on the dataset from FD-08, it
interacts with FD-02's cell layout, and it has to coexist with FD-01's tile changes. Everything it
touches has already moved by the time it lands.