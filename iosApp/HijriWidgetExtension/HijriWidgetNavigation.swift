import Foundation
import WidgetKit
import WidgetCalendar

/// On-widget month navigation. iOS gives an extension no widget instance id and no `AppIntent`
/// round-trip, so the arrows write the viewed month into the app group and ask WidgetKit for a
/// fresh timeline; the next render reads the new month back.
///
/// The options that decide *what* to navigate are `WidgetCalendarWidgetOptions` from
/// `calendar-widget-data`, so nothing here needs to know the option schema.
enum HijriWidgetNavigation {
    static func step(kind: HijriWidgetKind, year: Int32, month: Int32, by delta: Int) {
        // `HijriYearMonth`'s fields are Kotlin `Int`, which reaches Swift as `Int32`. `delta` is a
        // SwiftUI-facing `Int`, so it is narrowed at the boundary rather than at the call site.
        HijriShared.stepViewedMonth(
            for: kind,
            from: HijriYearMonth(year: year, month: month),
            by: Int32(delta)
        )
        WidgetCenter.shared.reloadTimelines(ofKind: kind.rawValue)
    }

    static func resetToToday(kind: HijriWidgetKind) {
        HijriShared.clearViewedMonth(for: kind)
        WidgetCenter.shared.reloadTimelines(ofKind: kind.rawValue)
    }

    static func reloadAll() {
        WidgetCenter.shared.reloadAllTimelines()
    }
}
