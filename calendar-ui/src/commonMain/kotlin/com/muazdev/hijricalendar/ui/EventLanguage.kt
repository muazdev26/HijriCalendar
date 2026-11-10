package com.muazdev.hijricalendar.ui

import com.muazdev.hijricalendar.core.HijriEventLanguage

/**
 * [labels] with every notable date's name rendered in [eventLanguage].
 *
 * ## Why this is a function rather than another pair of fields
 *
 * The event *names* are already lambdas ([HijriCalendarLabels.eventName] and
 * [HijriCalendarLabels.pakistanEventName]), so a host can localise them individually — but a host
 * cannot say "this Urdu calendar should name its events in English" without rewriting all four
 * lambdas by hand, and every one of those rewrites is a place for the two banner slots to disagree
 * with the two single-name slots. That is exactly how a sample app shipped an Urdu calendar whose
 * Pakistan-mode months printed "Iqbal Day" in Latin script: the national-day slot had been added
 * after the bundle was written, and only half the slots were overridden.
 *
 * This is the one place that answer lives. It rebuilds **all four** event lambdas from
 * `CalendarEvent.name(eventLanguage)`, so there is no slot left to override separately, and it takes
 * the separator from [HijriCalendarLabels.eventSeparator] rather than hard-coding one — so the bundle
 * keeps its own punctuation and only the language of the names changes.
 *
 * ## Scope: names only
 *
 * Everything else in [labels] is left untouched on purpose. The month names, weekday names, era
 * markers, the "no event" line and the hint line are the *host's* language, not a name from the
 * events table — a setting that also restyled the chrome would be a second `language` under a
 * misleading name. The widget schema's `WidgetOptions.eventLanguage` documents the same rule for
 * the same reason.
 *
 * ## It replaces, rather than composes with, a custom event lambda
 *
 * A host that supplied its own [HijriCalendarLabels.eventName] (say, a religious title rather than
 * the shipped name) will lose it by calling this, because there is no way to know what that lambda
 * would answer in another language. Call it on the **base** bundle and apply a custom name lambda
 * afterwards if one is needed:
 *
 * ```kotlin
 * val labels = HijriCalendarLabels(...).withEventLanguage(URDU)
 *     .copy(eventName = { event -> myTitle(event.key) })
 * ```
 *
 * @param eventLanguage The language the names render in. The library's two shipped tables answer
 *   both; a language the library does not have is not representable here, which is why this is a
 *   `HijriEventLanguage` rather than a `String`.
 */
public fun HijriCalendarLabels.withEventLanguage(
    eventLanguage: HijriEventLanguage,
): HijriCalendarLabels = copy(
    eventName = { it.name(eventLanguage) },
    pakistanEventName = { it.name(eventLanguage) },
    eventBanner = { event, _ -> event.name(eventLanguage) },
    monthEventBanner = { events -> events.joinToString(eventSeparator) { it.name(eventLanguage) } },
    monthEventBannerWithPakistan = { events ->
        events.joinToString(eventSeparator) { it.name(eventLanguage) }
    },
)
