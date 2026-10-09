package com.muazdev.hijricalendar.widgetdata

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Which light/dark palette a widget renders with.
 *
 * The widget family already *follows the system* light/dark setting for free: its colours are
 * `@ColorRes` `ColorProvider`s, so the launcher re-resolves them against its own configuration at
 * bind time (FD-07). This option exists for the one case that mechanism cannot express — a host app
 * whose **own** theme setting disagrees with the system, or pins one regardless of it. A host that
 * stores "System / Light / Dark" and mirrors it here makes the widget match the app rather than the
 * phone.
 *
 * [SYSTEM] is the default and the behaviour of every release before this option existed, so a widget
 * stored without it keeps re-resolving for night mode exactly as it did.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public enum class WidgetTheme {
    /**
     * Follow the launcher's own light/dark configuration. The palette is built from the
     * configuration-qualified `widget_*` resources, so a night-mode switch re-resolves it inside the
     * existing view.
     */
    @JsonNames("AUTO", "FOLLOW_SYSTEM", "DEVICE")
    SYSTEM,

    /** Always render the light palette, even in system dark mode. */
    @JsonNames("DAY")
    LIGHT,

    /** Always render the dark palette, even in system light mode. */
    @JsonNames("NIGHT")
    DARK,
}
