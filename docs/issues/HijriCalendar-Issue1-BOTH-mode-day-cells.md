# Issue 1: BOTH display mode not showing both dates inside day cells (consumer app)

**Status:** Resolved — not reproducible as filed; all three hypotheses were addressed anyway.
**Filed against:** `1.0.0-alpha03` (tag `bce171f`)

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

- [x] Sample app with `LayoutDirection.Rtl` + dark theme → BOTH mode shows two lines per cell
- [x] Font scale 1.5 → no clipping in BOTH mode
- [x] Default colors legible on arbitrary dark containers

## Resolution

**The reported symptom was never reproducible.** The prompt's own confirmed facts already ruled out
the two obvious causes: `BOTH` renders a two-line `Column` at `HijriCalendarDayCell.kt:245`, and
`dateDisplayMode` is threaded unbroken through all five layers. The consumer also unzipped both
published artifacts and found the `GREGORIAN_ONLY` symbols present, so this was never a stale
publication. Since the sample app renders `BOTH` correctly and the consumer's call site is correct,
the fault — if it existed — had to be in the *environment*, not the branch.

**All three hypotheses were investigated and none was the cause, but all three produced real
improvements**, which is the more useful outcome:

1. **Contrast/theme interaction — real risk, now structurally mitigated.** `gregorianDayContentColor`
   is not an independent colour: `HijriCalendarDefaults.kt:79` derives it as
   `dayContentColor.copy(alpha = 0.6f)`. A consumer overriding `dayContentColor` for a dark glassy
   card therefore moves the secondary line with it, instead of inheriting a fixed default tuned for
   the library's own surface. `HijriCalendarColors`' KDoc names the derivation so it is not
   mistaken for a free-standing value.
2. **Cell sizing — now a parameter.** `dayCellSize` on `HijriCalendar` (`HijriCalendar.kt:70`)
   defaults to `null`, which resolves to the platform default rather than a hard-coded 48dp circle.
   A consumer on a dense screen or with large type can size the cells without forking.
3. **Font scale — fixed, and this was a genuine defect.** The calendar used to pin
   `Density(fontScale = 1f)` around its whole subtree, silently disabling accessibility text
   scaling. Restored via [UI-04](UI-04-accessible-text-scaling.md); `ignoreFontScale` (default
   `false`) reproduces the old flat behaviour for anyone who needs a fixed-height cell.

**What remains is not a code defect.** If the consumer still sees this on `1.0.0-alpha03`, the next
step is a screenshot from their build rather than another source review — the branch, the threading
and the artifacts are all accounted for, and the prompt's own hypothesis 4 (log which `when` branch
executes) is now unnecessary because every hypothesis above is closed.
