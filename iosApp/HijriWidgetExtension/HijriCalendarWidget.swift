import WidgetKit
import SwiftUI

struct HijriCalendarWidget: Widget {
    let kind = "HijriCalendarWidget"

    var body: some WidgetConfiguration {
        AppIntentConfiguration(
            kind: kind,
            intent: HijriWidgetIntent.self,
            provider: HijriTimelineProvider()
        ) { entry in
            HijriCalendarWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Hijri Calendar")
        .description("Hijri month grid and today's date.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
        .contentMarginsDisabled()
    }
}