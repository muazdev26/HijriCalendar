import AppIntents
import WidgetCalendar
import WidgetKit

/// On-widget navigation state shared by the timeline provider and the navigation intents.
///
/// The viewed month persists in the widget extension's own `UserDefaults` — the provider and the
/// intents both run inside the extension sandbox, so the state survives re-renders, app restarts
/// and device reboots without requiring an app group. A missing value means "following today".
enum HijriWidgetStore {
    static let viewedYearKey = "hijri_widget_viewed_year"
    static let viewedMonthKey = "hijri_widget_viewed_month"
    static let widgetKind = "HijriCalendarWidget"

    static func viewedMonth() -> (year: Int32, month: Int32)? {
        guard
            UserDefaults.standard.object(forKey: viewedYearKey) != nil,
            UserDefaults.standard.object(forKey: viewedMonthKey) != nil
        else { return nil }
        return (
            Int32(UserDefaults.standard.integer(forKey: viewedYearKey)),
            Int32(UserDefaults.standard.integer(forKey: viewedMonthKey))
        )
    }

    static func setViewedMonth(year: Int32, month: Int32) {
        UserDefaults.standard.set(Int(year), forKey: viewedYearKey)
        UserDefaults.standard.set(Int(month), forKey: viewedMonthKey)
    }

    static func clearViewedMonth() {
        UserDefaults.standard.removeObject(forKey: viewedYearKey)
        UserDefaults.standard.removeObject(forKey: viewedMonthKey)
    }

    /// Steps the grid one Hijri month per tap and reloads the timeline in place. At the
    /// supported-range edge `offsetHijriMonth` returns nil and the tap is a graceful no-op.
    static func step(by offset: Int32) {
        guard let today = WidgetDataApiKt.todayHijriWidgetData(
            anchorEpochDay: todayEpochDay(),
            adjustmentDays: 0,
            localizedHijriMonthNames: nil,
            localizedGregorianMonthNames: nil,
            localizedWeekdayNames: nil,
            numeralStyle: .western,
            pakistan: false
        ) else { return }
        let viewed = viewedMonth()
        let year = viewed?.year ?? today.hijriYear
        let month = viewed?.month ?? today.hijriMonth
        guard let next = WidgetDataApiKt.offsetHijriMonth(
            hijriYear: year, hijriMonth: month, offset: offset
        ) else { return }
        setViewedMonth(year: next.year, month: next.month.number)
        WidgetCenter.shared.reloadTimelines(ofKind: widgetKind)
    }

    /// Returns the grid to following the current month.
    static func resetToToday() {
        clearViewedMonth()
        WidgetCenter.shared.reloadTimelines(ofKind: widgetKind)
    }

    private static func todayEpochDay() -> Int64 {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        let startOfDay = calendar.startOfDay(for: Date())
        let ref = calendar.date(from: DateComponents(year: 1970, month: 1, day: 1))!
        return Int64(calendar.dateComponents([.day], from: ref, to: startOfDay).day ?? 0)
    }
}

struct HijriPrevMonthIntent: AppIntent {
    static var title: LocalizedStringResource = "Previous Hijri month"
    static var description = IntentDescription("Steps the widget grid back one Hijri month.")
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        HijriWidgetStore.step(by: -1)
        return .result()
    }
}

struct HijriNextMonthIntent: AppIntent {
    static var title: LocalizedStringResource = "Next Hijri month"
    static var description = IntentDescription("Steps the widget grid forward one Hijri month.")
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        HijriWidgetStore.step(by: 1)
        return .result()
    }
}

struct HijriTodayResetIntent: AppIntent {
    static var title: LocalizedStringResource = "Return to current month"
    static var description = IntentDescription("Returns the widget to following the current month.")
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        HijriWidgetStore.resetToToday()
        return .result()
    }
}

struct HijriWidgetIntent: AppIntent, WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Hijri Calendar"
    static var description: IntentDescription = "Hijri month grid and today's date."

    init() {}

    func perform() async throws -> some IntentResult {
        .result()
    }
}