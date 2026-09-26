import SwiftUI
import WidgetKit

/// The resizable month grid (medium/large) with a compact today card at the small size — the
/// direct counterpart of Android's `HijriCalendarWidget`. Options come from the app group the
/// host app edits, and the transient viewed month survives re-renders.
///
/// All four widgets use `StaticConfiguration`: options are app-wide (edited in the sample app's
/// widget settings screen) rather than per placed widget, because `AppIntentConfiguration` does
/// not resolve in this runtime. See `WidgetSettingsView` for the editor.
struct HijriCalendarWidget: Widget {
    static let kind = HijriWidgetKind.calendar.rawValue

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: HijriGridTimelineProvider()) { entry in
            HijriGridEntryView(entry: entry)
                .containerBackground(for: .widget) { Color(.systemBackground) }
        }
        .configurationDisplayName(HijriWidgetKind.calendar.displayName)
        .description(HijriWidgetKind.calendar.blurb)
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}

/// The fixed today strip: one line carrying the Gregorian and Hijri dates side by side.
struct HijriTodayWidget: Widget {
    static let kind = HijriWidgetKind.today.rawValue

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: HijriTodayTimelineProvider(kind: .today)) { entry in
            HijriTodayStripEntryView(entry: entry)
                .containerBackground(for: .widget) { Color(.systemBackground) }
        }
        .configurationDisplayName(HijriWidgetKind.today.displayName)
        .description(HijriWidgetKind.today.blurb)
        .supportedFamilies([.systemSmall, .systemMedium])
        .contentMarginsDisabled()
    }
}

/// A fixed square tile carrying today's Hijri day and month.
struct HijriDateWidget: Widget {
    static let kind = HijriWidgetKind.hijriDate.rawValue

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: HijriTodayTimelineProvider(kind: .hijriDate)) { entry in
            HijriDateTileEntryView(entry: entry)
                .containerBackground(for: .widget) { Color(.systemBackground) }
        }
        .configurationDisplayName(HijriWidgetKind.hijriDate.displayName)
        .description(HijriWidgetKind.hijriDate.blurb)
        .supportedFamilies([.systemSmall, .accessoryCircular])
        .contentMarginsDisabled()
    }
}

/// A fixed square tile carrying today's Gregorian day and month on the same local-calendar
/// anchor the Hijri number was produced from.
struct GregorianDateWidget: Widget {
    static let kind = HijriWidgetKind.gregorianDate.rawValue

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: HijriTodayTimelineProvider(kind: .gregorianDate)) { entry in
            GregorianDateTileEntryView(entry: entry)
                .containerBackground(for: .widget) { Color(.systemBackground) }
        }
        .configurationDisplayName(HijriWidgetKind.gregorianDate.displayName)
        .description(HijriWidgetKind.gregorianDate.blurb)
        .supportedFamilies([.systemSmall, .accessoryCircular])
        .contentMarginsDisabled()
    }
}
