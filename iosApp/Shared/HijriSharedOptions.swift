import Foundation
import os

// The shared schema module arrives under a different framework name per target: the extension links
// only `WidgetCalendar`, while the app links only `calendar` (the Compose framework re-exports
// calendar-widget-data). Linking both into the app would inject the Kotlin/Native runtime twice, so
// each target imports exactly one. `WIDGET_EXTENSION` is set on the extension target only.
#if WIDGET_EXTENSION
import WidgetCalendar
#else
import calendar
#endif

/// Identifies the four widget kinds the iOS extension registers. This is the renderer's own
/// vocabulary (an iOS widget extension has no cross-platform "widget class" to share) and is the
/// only thing that is not defined in `calendar-widget-data`.
enum HijriWidgetKind: String, CaseIterable, Identifiable {
    case calendar = "HijriCalendarWidget"
    case today = "HijriTodayWidget"
    case hijriDate = "HijriDateWidget"
    case gregorianDate = "GregorianDateWidget"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .calendar: "Hijri Calendar"
        case .today: "Hijri Today"
        case .hijriDate: "Hijri Date"
        case .gregorianDate: "Gregorian Date"
        }
    }

    var blurb: String {
        switch self {
        case .calendar: "Month grid with today's cell highlighted"
        case .today: "Gregorian and Hijri dates on one line"
        case .hijriDate: "Today's Hijri day and month as a tile"
        case .gregorianDate: "Today's Gregorian day and month as a tile"
        }
    }

    var symbol: String {
        switch self {
        case .calendar: "calendar"
        case .today: "text.alignleft"
        case .hijriDate: "calendar.badge.clock"
        case .gregorianDate: "globe.asia.australia"
        }
    }

    var sizes: String {
        switch self {
        case .calendar: "Small, Medium, Large"
        case .today: "Small, Medium"
        case .hijriDate, .gregorianDate: "Small, Lock Screen circular"
        }
    }
}

/// Persistence for the widget options and the transient "viewed month", shared between the host
/// app (which edits the options) and the widget extension (which renders them).
///
/// The options themselves are **not** declared here: [WidgetOptions] comes straight
/// from `calendar-widget-data`, so the schema has exactly one definition. This type only decides
/// *where* the JSON that `WidgetOptionsJson` produces is kept — the app group's `UserDefaults` — so
/// a change made in the app reaches every placed widget on the next `WidgetCenter.reloadTimelines`.
///
/// With a static widget configuration there is no per-instance `WidgetConfigurationIntent` to write
/// into, so the options are app-wide rather than per placed widget: the iOS counterpart of the
/// Android "family options mirror" key.
///
/// This file is a member of BOTH the `iosApp` and `HijriWidgetExtension` targets.
enum HijriShared {
    static let suiteName = "group.com.muazdev.hijricalendar"
    private static let optionsKey = "hijri_widget_options_v1"
    private static let viewedPrefix = "hijri_widget_viewed_v1_"

    static var defaults: UserDefaults { UserDefaults(suiteName: suiteName) ?? .standard }

    /// `false` only when the app-group entitlement is missing, which makes the options local to
    /// each process instead of shared. The widgets still render; they just stop following the app.
    static var isAppGroupAvailable: Bool { UserDefaults(suiteName: suiteName) != nil }

    // MARK: Options

    private static let log = Logger(subsystem: "com.muazdev.hijricalendar", category: "HijriWidgetOptions")

    /// Summarises the options the same way the Android library logs them, so a device log answers
    /// "which options is this widget actually rendering" without a debugger attached. Only on the
    /// decode path: the extension renders on a background thread and must stay quiet otherwise.
    private static func describe(_ options: WidgetOptions) -> String {
        "language=\(options.language.name) monthNames=\(options.effectiveMonthNameLanguage.name) "
            + "source=\(options.source.name) numerals=\(options.numeralStyle.name) "
            + "adjustment=\(options.adjustmentDays) firstDay=\(options.firstDayOfWeekIndex) "
            + "pinned=\(options.isPinned)"
    }

    static func loadOptions() -> WidgetOptions {
        let raw = defaults.string(forKey: optionsKey)
        // The shared format is the only one this app writes. A value left by the pre-1.0 Swift
        // schema is read once, then immediately rewritten in the shared format (see
        // `migrateLegacySwiftFormat`).
        if let options = WidgetOptionsJson.shared.decodeOrNull(text: raw) {
            log.debug("load appGroup=\(self.isAppGroupAvailable, privacy: .public) \(self.describe(options), privacy: .public)")
            return options
        }
        let migrated = migrateLegacySwiftFormat(raw)
        let options = migrated ?? WidgetOptions.companion.DEFAULTS
        log.debug("load legacy=\(migrated != nil, privacy: .public) \(self.describe(options), privacy: .public)")
        if migrated != nil { saveOptions(options) }
        return options
    }

    static func saveOptions(_ options: WidgetOptions) {
        let json = WidgetOptionsJson.shared.encode(options: options)
        defaults.set(json, forKey: optionsKey)
        log.debug("save \(describe(options), privacy: .public)")
    }

    /// One-time reader for the option JSON written by the pre-1.0 Swift schema, which used the
    /// same field names but *lowercase* enum values and a `pinsMonth` flag instead of a nullable
    /// pinned pair. Returns `nil` when [raw] is not in that shape.
    ///
    /// This is a migration, not a second schema: it knows only how the old bytes were spelled, and
    /// the value it produces is immediately re-encoded by `WidgetOptionsJson`, so it can be deleted
    /// once no installed build is pre-1.0. Do not add new options here.
    private static func migrateLegacySwiftFormat(_ raw: String?) -> WidgetOptions? {
        guard let raw,
              let data = raw.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }

        // The shared decoder is strict about enum casing, so a lowercase value is the tell-tale.
        func language(_ key: String) -> WidgetLanguage? {
            (object[key] as? String).flatMap { $0.lowercased() == "urdu" ? .urdu : .english }
        }
        guard object["language"] is String, object["numeralStyle"] is String else { return nil }
        let widgetLanguage = language("language") ?? .urdu

        return WidgetOptionsKt.createWidgetOptions(
            language: widgetLanguage,
            monthNameLanguage: language("monthNameLanguage") ?? widgetLanguage,
            source: (object["source"] as? String).flatMap { $0.lowercased() == "pakistan" ? .pakistan : .calculation } ?? .calculation,
            adjustmentDays: object["adjustmentDays"] as? Int32 ?? 0,
            numeralStyle: (object["numeralStyle"] as? String)
                .flatMap { $0.lowercased() == "western" ? .western : .arabicIndic } ?? .arabicIndic,
            firstDayOfWeekIndex: object["firstDayOfWeekIndex"] as? Int32 ?? 0,
            pinsMonth: (object["pinsMonth"] as? Bool) ?? false,
            pinnedYear: (object["pinnedYear"] as? Int32) ?? 0,
            pinnedMonth: (object["pinnedMonth"] as? Int32) ?? 1
        )
    }

    // MARK: Viewed month (on-widget navigation)
    //
    // iOS hands an extension no widget instance id, so the viewed month is keyed by widget kind
    // and lives in the same app-group store the app reads.

    static func viewedMonth(for kind: HijriWidgetKind) -> (year: Int, month: Int)? {
        guard let raw = defaults.string(forKey: viewedPrefix + kind.rawValue) else { return nil }
        let parts = raw.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return (parts[0], parts[1])
    }

    static func setViewedMonth(_ viewed: (year: Int, month: Int)?, for kind: HijriWidgetKind) {
        let key = viewedPrefix + kind.rawValue
        if let viewed {
            defaults.set("\(viewed.year):\(viewed.month)", forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    /// Steps the viewed month, wrapping across the 12-month year the way the grid header does.
    static func stepViewedMonth(for kind: HijriWidgetKind, from base: (year: Int, month: Int), by delta: Int) {
        var year = base.year
        var month = base.month + delta
        while month < 1 {
            month += 12
            year -= 1
        }
        while month > 12 {
            month -= 12
            year += 1
        }
        setViewedMonth((year, month), for: kind)
    }

    static func clearViewedMonth(for kind: HijriWidgetKind) {
        setViewedMonth(nil, for: kind)
    }
}
