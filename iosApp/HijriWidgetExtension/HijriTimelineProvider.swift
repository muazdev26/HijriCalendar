import WidgetKit
import WidgetCalendar

struct HijriEntry: TimelineEntry {
    let date: Date
    let anchorEpochDay: Int64
    let month: HijriMonthWidgetData?
    let today: TodayHijriWidgetData?
    let adjustmentDays: Int32
}

private extension Calendar {
    /// Gregorian calendar preserving the user's local wall-clock timezone. Local dates
    /// are always interpreted in Gregorian terms so `gregorianEpochDay` values coming
    /// from kotlinx's `toEpochDays()` match 1:1 (never divide time by 86400).
    static var localGregorian: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar
    }
}

struct HijriTimelineProvider: AppIntentTimelineProvider {
    private let calendar = Calendar.localGregorian
    private let adjustmentDays: Int32 = 0

    private func localEpochDay(for date: Date) -> Int64 {
        let startOfDay = calendar.startOfDay(for: date)
        let ref = calendar.date(from: DateComponents(year: 1970, month: 1, day: 1))!
        return Int64(calendar.dateComponents([.day], from: ref, to: startOfDay).day ?? 0)
    }

    private func todayProjection(at epochDay: Int64) -> TodayHijriWidgetData? {
        WidgetDataApiKt.todayHijriWidgetData(anchorEpochDay: epochDay, adjustmentDays: adjustmentDays)
    }

    func placeholder(in context: Context) -> HijriEntry {
        HijriEntry(date: Date(), anchorEpochDay: 0, month: nil, today: nil, adjustmentDays: adjustmentDays)
    }

    func snapshot(for configuration: HijriWidgetIntent, in context: Context) async -> HijriEntry {
        let now = Date()
        let epoch = localEpochDay(for: now)
        let month = monthGrid(containing: now)
        return HijriEntry(
            date: now,
            anchorEpochDay: epoch,
            month: month,
            today: todayProjection(at: epoch),
            adjustmentDays: adjustmentDays
        )
    }

    func timeline(for configuration: HijriWidgetIntent, in context: Context) async -> Timeline<HijriEntry> {
        let now = Date()
        let startOfDay = calendar.startOfDay(for: now)
        let grid = monthGrid(containing: now)
        let gridDayCount = grid?.days.count ?? 42

        // One entry per day: the grid stays the same for the whole month, but each
        // entry re-anchors "today" so cells highlight and the small view reports the
        // right Hijri date on every day of the timeline. When the grid's final cell
        // passes, reload (`.atEnd`) rebuilds the next month.
        let entries = (0..<gridDayCount).compactMap { offset -> HijriEntry? in
            guard let date = calendar.date(byAdding: .day, value: offset, to: startOfDay) else { return nil }
            let epoch = localEpochDay(for: date)
            return HijriEntry(
                date: date,
                anchorEpochDay: epoch,
                month: grid,
                today: todayProjection(at: epoch),
                adjustmentDays: adjustmentDays
            )
        }

        let endDate = calendar.date(byAdding: .day, value: 1, to: entries.last?.date ?? startOfDay) ?? startOfDay
        return Timeline(entries: entries, policy: .after(endDate))
    }

    private func monthGrid(containing date: Date) -> HijriMonthWidgetData? {
        guard let today = todayProjection(at: localEpochDay(for: date)) else { return nil }
        return WidgetDataApiKt.buildHijriMonthWidgetData(
            hijriYear: today.hijriYear,
            hijriMonth: today.hijriMonth,
            adjustmentDays: adjustmentDays,
            firstDayOfWeekIndex: 0,
            numeralStyle: .western,
            localizedHijriMonthNames: nil,
            localizedWeekdayNames: nil
        )
    }
}