import WidgetKit
import SwiftUI

@main
struct HijriCalendarWidgetBundle: WidgetBundle {
    var body: some Widget {
        HijriCalendarWidget()
        HijriTodayWidget()
        HijriDateWidget()
        GregorianDateWidget()
    }
}
