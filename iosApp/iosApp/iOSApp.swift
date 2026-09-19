import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    guard url.scheme == "hijricalendar", url.host == "today" else { return }
                    NotificationCenter.default.post(name: .hijriJumpToToday, object: nil)
                }
        }
    }
}

private extension Notification.Name {
    static let hijriJumpToToday = Notification.Name("hijriJumpToToday")
}
