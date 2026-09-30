# Issue 08: Day cells have no touch feedback

**Severity:** Medium
**Blocks:** —
**Blocked by:** —
**Module:** `calendar-ui`

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

- [ ] `rg "indication = null" calendar-ui/src/commonMain` returns nothing
- [ ] A test presses a day cell and asserts an interaction is emitted (`MutableInteractionSource`
      emits `Interaction.Press` / `collectIsPressedAsState`)
- [ ] A disabled day cell does not invoke `onDayClick` and exposes no `Role.Button`
- [ ] `ModifierExtensionsKt` no longer appears in `calendar-ui/api/`
- [ ] `apiDump` run; `CHANGELOG.md` records the removal

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