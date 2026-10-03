# FD-07: Switching dark mode tears the widgets down and rebuilds them

**Issue:** #12
**Severity:** Medium
**Blocks:** —
**Blocked by:** None (can start immediately)
**Module:** `calendar-widget-glance`
**Status:** Ready

---

## Problem

Toggling the device between light and dark mode makes every placed widget visibly restart: it goes
blank, or drops to a placeholder, and comes back. The report is "dark/light mode immediately restarts
the widgets, and it is reflected in the widget appearance" — the reflection is correct, the restart is
the defect.

## Why it happens

Widget colours are resolved to literal ints at compose time:

```kotlin
// calendar-widget-glance/src/main/.../HijriCalendarWidget.kt:478-506
internal class WidgetColors(
    val background: Color, val accent: Color, /* … */
) {
    companion object {
        fun from(context: Context) = WidgetColors(
            background = context.widgetColor(R.color.widget_background), …
        )
    }
}
private fun Context.widgetColor(@ColorRes resId: Int): Color = Color(getColor(resId))
```

`context.getColor(R.color.widget_background)` runs **in this process, right now**, and returns
whichever value today's configuration says. `#FBF7F1` by day, `#1C1B1F` by night, chosen by the
`values-night` qualifier.

The result is then handed to Glance as `ColorProvider(Color(0xFFFBF7F1))` — a **literal**. Glance
serialises that int into the `RemoteViews` it sends to the launcher. The launcher has no idea which
resource it came from and cannot re-resolve it, because by then it is just a number.

So a night-mode switch does not re-resolve six colours. It invalidates the whole `RemoteViews`
remote, the launcher falls back to its default/placeholder view while the new one is composed, and
the user watches the widget tear itself down and rebuild. That is the restart.

## Why the module has no `uiMode` listener — and why adding one is the wrong fix

There is no `ACTION_UI_MODE_CHANGED` receiver, and the code comments are right that one is not
needed: the colours are already day/night aware *in principle*, and `updatePeriodMillis=1800000`
sweeps every 30 minutes regardless.

Adding a broadcast receiver would make the widget re-render faster. It would not remove the restart —
the re-render *is* the restart. It would also make it worse in the ways AGENTS.md already documents:
a background render with the Glance main-thread stall the foreground gate exists to avoid, plus one
more receiver to route through `HijriWidgetRenderQueue`. **Faster rebuild is not the goal; no rebuild
is.**

## The fix

Hand Glance the **resource id**, not the resolved int:

```kotlin
ColorProvider(R.color.widget_background)   // resolved by the launcher, per its configuration
```

Glance's `ColorProvider` has a resource-int factory precisely for this: it serialises the id, and the
launcher resolves it against *its own* current configuration at bind time. A night-mode switch then
re-resolves six colours inside the existing `RemoteViews`. **No re-compose, no invalidation, no
placeholder, no restart.** The widget is still there, and it is simply the right colour.

`WidgetColors` stops being a `class` holding `Color`s and becomes a holder of `@ColorRes Int`s — or
disappears entirely, with the renderers reading `R.color.…` inline. The `values-night` files stay
exactly as they are; they are the mechanism, not the problem.

## Two consequences

**The `previewLayout` layouts are unaffected** — the framework inflates those itself and applies the
qualifier natively, which is why they were never wrong.

**The two alpha-only colours need care.** `values/colors.xml` has a comment explaining that
`widget_text_muted` and `widget_text_faint` exist *only* as flattened alphas for `previewLayout`,
because the framework cannot apply alpha to a colour resource. Those two cannot become
resource-resolved ids in Glance — `ColorProvider` has no alpha parameter. Keep them flattened, keep
them in `values/` only, and say why in the code. Losing the night variant for two dimmed text colours
is a fair trade for not tearing down every widget; silently getting it wrong is not.

## Acceptance criteria

- [ ] No `ColorProvider` in the module wraps a literal resolved int from `getColor()`.
- [ ] Toggling night mode repaints every placed widget **without** the widget being invalidated,
      restarted or replaced by a placeholder — no flicker, no re-compose.
- [ ] `values-night/colors.xml` remains the source of the night palette; no colour is hardcoded in
      Kotlin.
- [ ] The alpha-only `previewLayout` colours keep their flattened values, with the reason recorded in
      code, and their absence from `values-night` is no longer surprising to a reader.
- [ ] `WidgetColors` is either deleted or reduced to `@ColorRes` holders, and its KDoc no longer claims
      to solve a problem it cannot.
- [ ] No new receiver, no new intent filter, and `HijriWidgetUpdateReceiver` is untouched.
- [ ] Light and night are asserted by a test that resolves each colour under both configurations, so
      the night palette cannot silently stop existing.

## Verification

This one cannot be verified by a desktop test or by `apiCheck`. It needs a device or emulator with the
widget placed, `adb shell cmd uimode night yes` / `no`, and an eye on it. Say so in the PR rather than
claiming it is tested — and keep the automated assertion to what *is* automatable: that both
configurations resolve to different values for every day/night pair.