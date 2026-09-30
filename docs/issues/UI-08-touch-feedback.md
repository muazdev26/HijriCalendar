# Issue 08: Day cells have no touch feedback

**Severity:** Medium
**Blocks:** —
**Blocked by:** —
**Module:** `calendar-ui`
**Status:** Shipped — with one honest gap. See "Not covered".

---

## Problem

```kotlin
// calendar-ui/.../util/ModifierExtensions.kt:22-29
): Modifier = if (enabled) {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },   // allocated…
        indication = null,                                            // …to feed a null
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick,
    )
} else {
    this
}
```

`indication = null` means **all 42 day cells render no ripple, no press state, no hover**. A tap
gives no visual feedback at all. `clickableIfEnabled` is the only click path for every day cell
(`HijriCalendarDayCell.kt:97-102`).

The cell does compute a full selected/today treatment — `background`, `border` and
`CircleShape` (`HijriCalendarDayCell.kt:94-96`) — so *state* is expressed, but *transient state*
is not. A user pressing a day sees nothing happen until the cell's selection state changes, which
is fast enough to read as "the tap did nothing".

## Why it matters

- **Material best practice.** Every tappable surface gets an indication. `indication = null` is for
  cases where the component draws its own press feedback — this one does not.
- **Accessibility.** There is no visible state feedback on press. Combined with
  [UI-04](UI-04-accessible-text-scaling.md) (no text scaling) and the selected-today treatment
  relying on colour alone, this is the third leg of the same gap.
- **It allocates anyway.** The `MutableInteractionSource` exists purely to be handed to a
  `clickable` that ignores it. One object per cell per composition.

## Why it was probably done

Composing a ripple inside a `48.dp` `CircleShape`-clipped cell inside a `Row` of `weight(1f)`
boxes, with the ripple bleeding past the cell bounds in `BOTH` mode, is genuinely fiddly. The fix
was to remove the ripple rather than bound it. Understandable; the result is a calendar with no
press feedback.

## Proposed change

**1. Give the indication back**, and bound it to the cell:

```kotlin
.clickable(
    interactionSource = interactionSource,
    indication = ripple(bounded = true, radius = cellSize / 2),
    ...
)
```

`ripple(bounded = true)` clips to the `CircleShape` already applied at
`HijriCalendarDayCell.kt:94`. If the `bounded` ripple leaks in `BOTH` mode, that is a real bug to
fix — a clipped ripple on a circular cell is the Material behaviour.

**2. Hoist the `MutableInteractionSource` out of the modifier.** Either pass one in from
`HijriCalendarDayCell` as a parameter, or (better) drop the helper entirely and use
`Modifier.clickable` directly in the cell. See below.

**3. Delete the two public helpers.** `calendarDayCell(size) = this.size(size)` is a no-op wrapper
around `Modifier.size` for one call site. `clickableIfEnabled` duplicates `Modifier.clickable`'s
`enabled` parameter, which already exists. Both become `internal` or vanish — they are in the
published ABI today (`ModifierExtensionsKt` in `calendar-ui.api:149-153`).

If a disabled cell genuinely must lose its semantics, `Modifier.clickable(enabled = false)` already
does exactly that, and `HijriCalendarDayCell.kt:92` already gates `role` on `enabled`.

**4. Consider a pressed-scale or alpha treatment** in addition to the ripple, so the feedback
survives `DateDisplayMode` and a themed ripple colour. Optional.

## Done when

- [x] `rg "indication = null" calendar-ui/src/commonMain` returns nothing
- [~] A test presses a day cell and asserts an interaction is emitted — **cannot be done.** See
      "Not covered".
- [x] A disabled day cell does not invoke `onDayClick` and exposes no `Role.Button`
- [x] `ModifierExtensionsKt` no longer appears in `calendar-ui/api/` (removed by UI-06)
- [x] `apiDump` run; `CHANGELOG.md` records the removal

## Shipped

Press feedback is back. `indication = null` is gone; every day cell gets the default Material
indication.

- **The ripple is bounded to the circle for free.** `clickable` is applied **after**
  `Modifier.clip(CircleShape)` in the cell's chain, so the indication draws inside the clip. No
  `ripple(bounded = true)` is needed, and no `radius` to get wrong.
- **The default indication is used, not a constructed `ripple()`.** That means it follows
  `LocalIndication.current` and therefore the ambient theme, rather than hardcoding a colour that
  reads poorly against a selected-day accent. This answers the ticket's own note about
  `HijriCalendarColors` needing a ripple colour: it does not, as long as the indication is themed.
- **The unused `MutableInteractionSource` is gone with the `remember`.** It existed only to be handed
  to a `clickable` that ignored it — one retained state object per cell per composition for nothing.
  `Modifier.clickable(onClickLabel, role, onClick)` builds its own.

### The ticket's premise about *why* was wrong, and so was the comment I wrote in UI-06

Both said the ripple was suppressed because *"42 simultaneous ripples are visual noise"*, or that
composing one inside a 48dp clipped circle inside a `Row` of `weight(1f)` boxes was fiddly. Neither
is a real constraint:

- A ripple only draws on the cell being pressed, and only one cell is pressed at a time. There are
  never 42 ripples.
- Bounding is free, as above.

I wrote the "42 ripples" reasoning into `clickableIfEnabled`'s KDoc during UI-06 and it survived into
the ticket. Both are now corrected, and the KDoc says what the actual reasons are. This is the third
time in this review that a plausible-sounding comment was the reason a decision was never revisited —
`drop(1)`, `fontScale = 1f`, and this.

### `Modifier.clickable(enabled = false)` is not a substitute — verified

The obvious simplification is to delete the helper and use the built-in flag. That **fails**:
with `enabled = false` this Compose version still registers an `OnClick` semantics action on the
node. `aDisabledCellIsNotClickable` asserts the opposite and goes red with
`OnClick is NOT defined` failing. So the helper is load-bearing, and its KDoc — written in UI-06 —
had claimed `Modifier.clickable` "already takes an `enabled` flag", which was wrong.

The helper now has the correct justification on it: omit the modifier entirely to remove the action.

## Not covered

**There is no test for the press indication, and adding one here is not possible.** Verified rather
than assumed: re-suppressing `indication = null` and running the whole suite leaves it **fully
green** — 0 failures.

The reason is that an indication is a draw-time effect with no semantics, and
`runComposeUiTest` on desktop does not reproduce Skia-drawn ripples. This is the one ticket in the
set where the desktop harness cannot reach the change, exactly as the ticket predicted.

What *is* covered, and is the semantic half of the same contract:

- `anEnabledCellOffersAClickAction` and `aDisabledCellIsNotClickable` together pin that the cell
  offers an action when live and not when inert.
- `pressingACellSelectsNothingUntilRelease` pins that a press has no side effects before release.
- `cellsOutsideTheDateWindow_areDisabledAndDoNotClick` pins that a disabled cell does not fire.

So the guard on this change is the KDoc, not a test. If the indication is ever suppressed again, CI
will be green and only the comment will disagree — which is worth knowing rather than discovering.
A screenshot or on-device test is the only way to close it properly.

## Notes



## Notes

- **Ripple colour.** `HijriCalendarColors` has no ripple/press colour, so the indication falls back
  to `LocalIndication.current`, which Material 3 does not theme the way Material 2 did. If the
  default ripple reads poorly against the selected-day accent, add
  `dayIndicationColor: Color` to `HijriCalendarColors` rather than suppressing the indication.
- Verify on a device, not in a desktop test — ripples are Skia-drawn and desktop
  `runComposeUiTest` does not reproduce them. This is the one ticket in the set where
  [UI-02](UI-02-compose-test-harness.md)'s harness cannot fully cover the change.
- Check the ripple does not fight the today *border* (`HijriCalendarDayCell.kt:96`) on a cell that
  is both today and pressed.