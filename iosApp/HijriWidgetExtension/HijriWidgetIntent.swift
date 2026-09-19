import AppIntents
import WidgetKit

struct HijriWidgetIntent: AppIntent, WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Hijri Calendar"
    static var description: IntentDescription = "Hijri month grid and today's date."

    init() {}

    func perform() async throws -> some IntentResult {
        .result()
    }
}