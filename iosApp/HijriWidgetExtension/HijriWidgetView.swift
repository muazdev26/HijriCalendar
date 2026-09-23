import AppIntents
import SwiftUI
import WidgetKit
import WidgetCalendar

private let accent = Color(red: 0.13, green: 0.45, blue: 0.35)
private let todayBackground = Color(red: 0.16, green: 0.55, blue: 0.42)
private let deepLink = URL(string: "hijricalendar://today")!

struct HijriCalendarWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: HijriEntry

    var body: some View {
        Group {
            switch family {
            case .systemSmall, .accessoryRectangular:
                TodayView(entry: entry)
            case .systemMedium:
                MonthGridView(entry: entry, rows: 4)
            default:
                MonthGridView(entry: entry, rows: 7)
            }
        }
        .containerBackground(for: .widget) { Color(.systemBackground) }
        .widgetURL(deepLink)
    }
}

private struct TodayView: View {
    let entry: HijriEntry

    var body: some View {
        if let today = entry.today {
            VStack(alignment: .leading, spacing: 6) {
                Text(today.hijriMonthName)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(accent)
                Text(today.hijriDayText)
                    .font(.system(size: 44, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)
                HStack {
                    Text(today.weekdayName)
                    Spacer()
                    Text(today.gregorianDate)
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        } else {
            Text("Hijri calendar unavailable")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

private struct MonthGridView: View {
    @Environment(\.layoutDirection) private var layoutDirection
    let entry: HijriEntry
    let rows: Int

    var body: some View {
        if let month = entry.month {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 4) {
                    MonthNavButton(
                        symbol: layoutDirection == .rightToLeft ? "chevron.right" : "chevron.left",
                        intent: HijriPrevMonthIntent()
                    )
                    // Tapping the month name returns the grid to following today.
                    Button(intent: HijriTodayResetIntent()) {
                        Text(month.hijriMonthName)
                            .font(.system(size: rows == 4 ? 15 : 13, weight: .semibold))
                            .foregroundStyle(accent)
                            .lineLimit(1)
                    }
                    .buttonStyle(.plain)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    MonthNavButton(
                        symbol: layoutDirection == .rightToLeft ? "chevron.left" : "chevron.right",
                        intent: HijriNextMonthIntent()
                    )
                }
                Text(month.gregorianRange)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text(month.weekdayHeaders.joined(separator: " "))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 7), spacing: 2) {
                    ForEach(Array(month.days.enumerated()), id: \.offset) { _, day in
                        DayCell(day: day, isToday: day.gregorianEpochDay == entry.anchorEpochDay)
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        } else {
            Text("Hijri calendar unavailable")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

/// One header chevron; tapping steps the grid one Hijri month in place.
private struct MonthNavButton<Intent: AppIntent>: View {
    let symbol: String
    let intent: Intent

    var body: some View {
        Button(intent: intent) {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(accent)
        }
        .buttonStyle(.plain)
    }
}

private struct DayCell: View {
    let day: HijriDayWidgetData
    let isToday: Bool

    var body: some View {
        ZStack {
            if isToday {
                Circle().fill(todayBackground)
            }
            Text(day.dayText)
                .font(.system(size: 12, weight: isToday ? .bold : .regular))
                .foregroundStyle(isToday ? .white : day.isCurrentMonth ? .primary : .secondary)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 24)
    }
}