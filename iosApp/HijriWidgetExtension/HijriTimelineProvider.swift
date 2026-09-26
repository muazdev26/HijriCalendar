import Foundation
import os
import WidgetKit
import WidgetCalendar

/// One rendered day of a widget's timeline. Shared by all four widget kinds: the grid reads
/// [month], the strip and the tiles read [today], and every kind reads [options] plus the
/// resolved [anchorEpochDay] so a cell can decide whether it is today.
///
/// [options] is `calendar-widget-data`'s own `WidgetOptions`, decoded from the app group, so the
/// settings screen and the renderer are provably reading the same schema.
struct HijriEntry: TimelineEntry {
    let date: Date
    let anchorEpochDay: Int64
    let options: WidgetOptions
    let kind: HijriWidgetKind
    /// The resolved Hijri month this entry renders; `nil` for the kinds that have no grid.
    let month: HijriMonthWidgetData?
    let gridYear: Int32
    let gridMonth: Int32
    let today: TodayHijriWidgetData?
}

extension Calendar {
    /// Gregorian calendar preserving the user's local wall-clock timezone. Local dates are
    /// always interpreted in Gregorian terms so `gregorianEpochDay` values coming from
    /// kotlinx's `toEpochDays()` match 1:1 (never divide time by 86400).
    static var localGregorian: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .current
        return calendar
    }
}

/// Builds every projection the four renderers share, so the grid, the strip and the tiles can
/// never disagree about what "today" is or which localization lists apply.
enum HijriWidgetProjection {
    static func localEpochDay(_ calendar: Calendar, for date: Date) -> Int64 {
        let startOfDay = calendar.startOfDay(for: date)
        let reference = calendar.date(from: DateComponents(year: 1970, month: 1, day: 1))!
        return Int64(calendar.dateComponents([.day], from: reference, to: startOfDay).day ?? 0)
    }

    static func todayEpochDay() -> Int64 {
        localEpochDay(.localGregorian, for: Date())
    }

    /// The "today" projection, driven entirely by [options] — the shared overload already applies
    /// the adjustment, the source, the numeral style and all three localized name lists, so this
    /// cannot drift from what the settings screen shows.
    static func today(at epochDay: Int64, options: WidgetOptions) -> TodayHijriWidgetData? {
        WidgetOptionsKt.todayHijriWidgetData(anchorEpochDay: epochDay, options: options)
    }

    static func monthGrid(
        year: Int32,
        month: Int32,
        options: WidgetOptions
    ) -> HijriMonthWidgetData? {
        // No `rightToLeft` override: on iOS the language is the whole story, and the platform
        // mirrors a `VStack`/`HStack` for us.
        WidgetOptionsKt.buildHijriMonthWidgetData(
            hijriYear: year,
            hijriMonth: month,
            options: options,
            weekendDays: Calendar_coreWeekDay.companion.WEEKEND_DAYS
        )
    }

    /// The month a grid renders: transient viewed month (on-widget navigation) wins over the
    /// configured pinned month, which in turn wins over the current Hijri month.
    static func resolveGridMonth(
        kind: HijriWidgetKind,
        options: WidgetOptions,
        today: TodayHijriWidgetData?
    ) -> (year: Int32, month: Int32) {
        if let viewed = HijriShared.viewedMonth(for: kind) { return (Int32(viewed.year), Int32(viewed.month)) }
        if options.isPinned, let year = options.pinnedYear, let month = options.pinnedMonth {
            return (year.int32Value, month.int32Value)
        }
        guard let today else { return (1447, 1) }
        return (today.hijriYear, today.hijriMonth)
    }
}

// MARK: - Month grid widget provider

struct HijriGridTimelineProvider: TimelineProvider {
    private let calendar = Calendar.localGregorian

    func placeholder(in context: Context) -> HijriEntry {
        HijriEntry(
            date: Date(),
            anchorEpochDay: 0,
            options: WidgetOptions.companion.DEFAULTS,
            kind: .calendar,
            month: nil,
            gridYear: 1447,
            gridMonth: 1,
            today: nil
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (HijriEntry) -> Void) {
        completion(entry(at: Date(), options: HijriShared.loadOptions()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HijriEntry>) -> Void) {
        let options = HijriShared.loadOptions()
        let startOfDay = calendar.startOfDay(for: Date())
        let first = entry(at: Date(), options: options)

        // One entry per day: the grid stays put for the whole month, but each entry re-anchors
        // "today" so the highlight moves and the small family reports the right Hijri date on
        // every day of the timeline. `.after(endOfLastEntry)` rebuilds the next month.
        let dayCount = first.month?.days.count ?? 42
        var entries: [HijriEntry] = [first]
        for offset in 1..<dayCount {
            guard let date = calendar.date(byAdding: .day, value: offset, to: startOfDay) else { continue }
            let epoch = HijriWidgetProjection.localEpochDay(calendar, for: date)
            entries.append(
                HijriEntry(
                    date: date,
                    anchorEpochDay: epoch,
                    options: options,
                    kind: .calendar,
                    month: first.month,
                    gridYear: first.gridYear,
                    gridMonth: first.gridMonth,
                    today: HijriWidgetProjection.today(at: epoch, options: options)
                )
            )
        }

        let endDate = calendar.date(byAdding: .day, value: 1, to: entries.last?.date ?? startOfDay) ?? startOfDay
        completion(Timeline(entries: entries, policy: .after(endDate)))
    }

    private func entry(at date: Date, options: WidgetOptions) -> HijriEntry {
        let epoch = HijriWidgetProjection.localEpochDay(calendar, for: date)
        let today = HijriWidgetProjection.today(at: epoch, options: options)
        let grid = HijriWidgetProjection.resolveGridMonth(kind: .calendar, options: options, today: today)
        Logger(subsystem: "com.muazdev.hijricalendar", category: "HijriWidgetOptions")
            .debug("render kind=calendar year=\(grid.year) month=\(grid.month) today=\(today?.hijriDayText ?? "nil")")
        return HijriEntry(
            date: date,
            anchorEpochDay: epoch,
            options: options,
            kind: .calendar,
            month: HijriWidgetProjection.monthGrid(year: grid.year, month: grid.month, options: options),
            gridYear: grid.year,
            gridMonth: grid.month,
            today: today
        )
    }
}

// MARK: - Today projection provider
//
// The strip and the two 1x1 tiles all render "today" and have no grid, so one provider serves
// them; only the tile that features the Gregorian date differs, and that choice lives in the view.

struct HijriTodayTimelineProvider: TimelineProvider {
    private let calendar = Calendar.localGregorian
    let kind: HijriWidgetKind

    init(kind: HijriWidgetKind) { self.kind = kind }

    func placeholder(in context: Context) -> HijriEntry {
        HijriEntry(
            date: Date(),
            anchorEpochDay: 0,
            options: WidgetOptions.companion.DEFAULTS,
            kind: kind,
            month: nil,
            gridYear: 0,
            gridMonth: 0,
            today: nil
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (HijriEntry) -> Void) {
        completion(entry(at: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HijriEntry>) -> Void) {
        let startOfDay = calendar.startOfDay(for: Date())
        // Seven entries keeps a week of "today" pre-computed so a widget that is not opened for
        // days still shows the right date the moment it is glanced at.
        let entries = (0..<7).compactMap { offset -> HijriEntry? in
            guard let date = calendar.date(byAdding: .day, value: offset, to: startOfDay) else { return nil }
            return entry(at: date)
        }
        let endDate = calendar.date(byAdding: .day, value: 1, to: entries.last?.date ?? startOfDay) ?? startOfDay
        completion(Timeline(entries: entries, policy: .after(endDate)))
    }

    private func entry(at date: Date) -> HijriEntry {
        let options = HijriShared.loadOptions()
        let epoch = HijriWidgetProjection.localEpochDay(calendar, for: date)
        Logger(subsystem: "com.muazdev.hijricalendar", category: "HijriWidgetOptions")
            .debug("render kind=\(kind.rawValue, privacy: .public) epochDay=\(epoch)")
        return HijriEntry(
            date: date,
            anchorEpochDay: epoch,
            options: options,
            kind: kind,
            month: nil,
            gridYear: 0,
            gridMonth: 0,
            today: HijriWidgetProjection.today(at: epoch, options: options)
        )
    }
}
