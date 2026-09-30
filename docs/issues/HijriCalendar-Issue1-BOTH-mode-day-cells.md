# Issue 1: BOTH display mode not showing both dates inside day cells (consumer app)

**Library:** https://github.com/muazdev26/HijriCalendar
**Consumer app:** AlkhairJantari (`com.toshamaco.alkhairjantari.multan.prayer.times`)
**Library version:** `1.0.0-alpha03` (tag `bce171f`, identical to `main`)

---

## The Prompt

> I'm the owner of the HijriCalendar KMP library. A consumer app passing `dateDisplayMode = DateDisplayMode.BOTH` reports that day cells do NOT show both Hijri and Gregorian dates, while my sample app does show them.
>
> **What I already verified — treat these as confirmed facts, do not re-investigate them:**
>
> 1. `HijriCalendarDayCell.kt` on `main` (= alpha03 tag) correctly renders a `Column { hijriText; gregorianText }` for `DateDisplayMode.BOTH`, and `dateDisplayMode` is threaded through `HijriCalendar -> HijriCalendarGrid -> MonthGrid -> HijriWeekRow -> HijriCalendarDayCell`.
> 2. The **published JitPack artifacts contain this code**: I unzipped both `hijri-calendar-compose-android-1.0.0-alpha03.aar/classes.jar` and the `hijri-calendar-compose-iosarm64-1.0.0-alpha03.klib`, and both contain `GREGORIAN_ONLY` / `gregorianDayContentColor` symbols. So it is NOT a stale publication.
> 3. The consumer call-site is correct:
>
> ```kotlin
> HijriCalendar(
>     state = calendarState,
>     modifier = Modifier.fillMaxWidth(),
>     dateDisplayMode = dateDisplayMode,   // DateDisplayMode.BOTH
>     onDayClick = calendarState.defaultOnDayClick(),
> )
> ```
>
> **Investigate these remaining hypotheses and fix whatever is real:**
>
> 1. **Contrast/theme interaction.** The consumer wraps the calendar in its own dark glassy card and forces RTL + custom dark/light themes. Check whether `gregorianDayContentColor` (default from `HijriCalendarDefaults.colors()`) can become near-invisible on non-standard backgrounds. Consider deriving gregorian color with an alpha of `dayContentColor` instead of its own fixed default, or documenting contrast guarantees.
> 2. **Cell sizing in BOTH mode.** Cells are hard-coded 48.dp circles (`Modifier.calendarDayCell(size = 48.dp)`). Two stacked texts (bodySmall + labelSmall) may clip or ellipsize inside the circle on some font scales/densities. Consider: auto-sizing cells when `dateDisplayMode == BOTH` (e.g. 56.dp), or making the cell size a parameter on `HijriCalendar`/`HijriCalendarDefaults`.
> 3. **Font scale.** With system font scale > 1.0 the second text line may overflow the circle. Test at 1.0/1.3/1.5 font scales.
> 4. Add a debug aid if needed: log/inspect which branch of the `when (dateDisplayMode)` executes in a consumer build.
>
> Deliverables: reproduce the consumer symptom in the sample app by mimicking its setup (RTL CompositionLocal + dark glassy container + custom MaterialTheme), identify the root cause, fix in `calendar-ui`, release as `1.0.0-alpha04`.

---

## Verification checklist

- [ ] Sample app with `LayoutDirection.Rtl` + dark theme → BOTH mode shows two lines per cell
- [ ] Font scale 1.5 → no clipping in BOTH mode
- [ ] Default colors legible on arbitrary dark containers
