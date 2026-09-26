import SwiftUI
import WidgetKit
import WidgetCalendar

private let accent = Color(red: 0.13, green: 0.45, blue: 0.35)
private let todayBackground = Color(red: 0.16, green: 0.55, blue: 0.42)
private let deepLink = URL(string: "hijricalendar://today")!
/// Content inset, in points, applied *inside* the measured box so the type is sized from the
/// padded area and can never overflow it.
private let contentPadding: CGFloat = 20

// MARK: - Month grid

struct HijriGridEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: HijriEntry

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                HijriTodayCard(entry: entry)
                    .widgetURL(deepLink)
            case .systemMedium:
                grid(rows: 4)
            default:
                grid(rows: 6)
            }
        }
        // No `.widgetURL` anywhere on the grid: it makes the entire widget a single tap target and
        // the arrow buttons stop responding. The app-opening regions use `Link` instead, which is
        // a distinct tap region that coexists with `Button` the way the header needs.
    }

    private func grid(rows: Int) -> some View {
        Group {
            if let month = entry.month {
                VStack(alignment: .leading, spacing: rows == 4 ? 3 : 5) {
                    header(month: month, compact: rows == 4)
                    Link(destination: deepLink) {
                        weekdayRow(month: month)
                    }
                    Link(destination: deepLink) {
                        LazyVGrid(
                            columns: Array(repeating: GridItem(.flexible(), spacing: 2), count: 7),
                            spacing: rows == 4 ? 1 : 3
                        ) {
                            ForEach(Array(month.days.enumerated()), id: \.offset) { _, day in
                                DayCell(
                                    day: day,
                                    isToday: day.gregorianEpochDay == entry.anchorEpochDay,
                                    compact: rows == 4
                                )
                            }
                        }
                    }
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                unavailable
            }
        }
    }

    /// Two 44pt touch targets around one centred combined title. The Hijri month + year and the
    /// Gregorian month + year sit in an inner row inside a default-weight box, so the *pair*
    /// centres as a block between the arrows in either reading direction.
    private func header(month: HijriMonthWidgetData, compact: Bool) -> some View {
        // The chevrons keep a fixed physical arrangement — pointing outwards from the title, left
        // glyph on the left and right glyph on the right — and what each side *does* follows the
        // widget's reading direction. WidgetKit lays the row out in the device's direction, so
        // mirroring the glyphs alone (without swapping the actions) puts "previous" on the wrong
        // side for a right-to-left widget. This is the same result Android's RTL `LinearLayout`
        // plus `autoMirrored` arrows produce: in RTL, previous sits on the right and next on the
        // left.
        HStack(spacing: 2) {
            MonthNavButton(
                symbol: "chevron.left",
                delta: entry.options.language.isRtl ? 1 : -1,
                entry: entry
            )
            // Hijri and Gregorian month + year share one centred line rather than stacking, so the
            // header stays a single compact row between the arrows in both reading directions.
            Button {
                HijriWidgetNavigation.resetToToday(kind: .calendar)
            } label: {
                HStack(spacing: 4) {
                    Text(month.hijriMonthName)
                    Text("\(month.hijriYear)")
                    Text("·")
                        .foregroundStyle(.tertiary)
                    Text(month.gregorianMonthTitle)
                        .foregroundStyle(.secondary)
                }
                .font(.system(size: compact ? 12 : 14, weight: .semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.55)
            }
            .buttonStyle(.plain)
            .frame(maxWidth: .infinity)
            MonthNavButton(
                symbol: "chevron.right",
                delta: entry.options.language.isRtl ? -1 : 1,
                entry: entry
            )
        }
    }

    private func weekdayRow(month: HijriMonthWidgetData) -> some View {
        HStack(spacing: 2) {
            ForEach(Array(month.weekdayHeaders.enumerated()), id: \.offset) { _, header in
                Text(header)
                    .font(.system(size: 9, weight: .medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var unavailable: some View {
        Text("Hijri calendar unavailable")
            .font(.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// One header chevron; tapping steps the grid one Hijri month in place. A plain `Button` rather
/// than `Button(intent:)` because the viewed month lives in the shared app-group store, which this
/// process can write directly -- no intent round-trip (and no dependence on the system's
/// intent-to-widget configuration bridge) is needed.
private struct MonthNavButton: View {
    let symbol: String
    let delta: Int
    let entry: HijriEntry

    var body: some View {
        Button {
            HijriWidgetNavigation.step(
                kind: .calendar,
                year: entry.gridYear,
                month: entry.gridMonth,
                by: delta
            )
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(accent)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct DayCell: View {
    let day: HijriDayWidgetData
    let isToday: Bool
    let compact: Bool

    var body: some View {
        ZStack {
            if isToday {
                Circle().fill(todayBackground)
            }
            Text(day.dayText)
                .font(.system(size: compact ? 11 : 13, weight: isToday ? .bold : .regular))
                .foregroundStyle(isToday ? .white : day.isCurrentMonth ? .primary : .secondary)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: compact ? 17 : 22)
    }
}

// MARK: - Today card (small family of the grid widget)

struct HijriTodayCard: View {
    let entry: HijriEntry

    var body: some View {
        GeometryReader { geo in
            let height = max(8, geo.size.height - contentPadding * 2)
            if let today = entry.today {
                // The four lines are sized as shares of the granted height so the day figure
                // absorbs whatever is left over, rather than leaving a fixed band of empty space.
                VStack(spacing: 0) {
                    Text(today.hijriMonthName)
                        .font(.system(size: height * 0.115, weight: .semibold))
                        .foregroundStyle(accent)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                    Text(today.hijriDayText)
                        .font(.system(size: height * 0.50, weight: .heavy, design: .rounded))
                        .lineLimit(1)
                        .minimumScaleFactor(0.3)
                    Text("\(today.hijriYear)")
                        .font(.system(size: height * 0.115, weight: .semibold))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                    HStack(spacing: 4) {
                        Text(today.weekdayName)
                        Text("·")
                            .foregroundStyle(.tertiary)
                        Text(today.gregorianDate)
                    }
                    .font(.system(size: height * 0.09))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                }
                .multilineTextAlignment(.center)
                .frame(width: geo.size.width, height: height)
                .padding(contentPadding)
                .frame(width: geo.size.width, height: geo.size.height)
            } else {
                Text("Hijri calendar unavailable")
                    .font(.system(size: height * 0.1))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                    .multilineTextAlignment(.center)
                    .frame(width: geo.size.width, height: height)
            }
        }
    }

    private var unavailable: some View {
        Text("Hijri calendar unavailable")
            .font(.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Today strip
//
// One line, Gregorian and Hijri side by side, with the Hijri half always on the right in either
// reading direction (the strip orders its halves from `isRtl` rather than from the device locale,
// matching the Android widget).

struct HijriTodayStripEntryView: View {
    let entry: HijriEntry

    var body: some View {
        Group {
            if let today = entry.today {
                let gregorian = DateHalf(
                    dayText: today.gregorianDayText,
                    monthAndYear: "\(today.gregorianMonthName) \(today.gregorianYear)"
                )
                let hijri = DateHalf(
                    dayText: today.hijriDayText,
                    monthAndYear: "\(today.hijriMonthName) \(today.hijriYear)"
                )
                HStack(alignment: .center, spacing: 0) {
                    if entry.options.language.isRtl {
                        hijri.half
                        DateDivider()
                        gregorian.half
                    } else {
                        gregorian.half
                        DateDivider()
                        hijri.half
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                unavailable
            }
        }
        .widgetURL(deepLink)
    }

    private var unavailable: some View {
        Text("Hijri calendar unavailable")
            .font(.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// A hairline rule between the Gregorian and Hijri halves of the strip. Inset from the top and
/// bottom so it reads as a separator between the two dates rather than a full-height divider.
private struct DateDivider: View {
    var body: some View {
        Rectangle()
            .fill(Color.secondary.opacity(0.4))
            .frame(width: 1)
            .padding(.vertical, contentPadding)
    }
}

private struct DateHalf {
    let dayText: String
    let monthAndYear: String

    /// Sizes both lines off the granted height so the pair fills the strip instead of leaving
    /// empty bands, whichever family width it lands in.
    var half: some View {
        GeometryReader { geo in
            let height = max(8, geo.size.height - contentPadding * 2)
            VStack(spacing: 0) {
                Text(dayText)
                    .font(.system(size: height * 0.58, weight: .heavy, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.3)
                Text(monthAndYear)
                    .font(.system(size: height * 0.20))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.4)
            }
            .multilineTextAlignment(.center)
            .frame(width: geo.size.width, height: height)
            .padding(contentPadding)
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }
}

// MARK: - 1x1 date tiles

struct HijriDateTileEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: HijriEntry

    var body: some View {
        tile(dayText: entry.today?.hijriDayText, caption: caption, circular: family == .accessoryCircular)
            .widgetURL(deepLink)
    }

    private var caption: String? {
        guard let today = entry.today else { return nil }
        return "\(today.hijriMonthName) \(today.hijriYear)"
    }
}

struct GregorianDateTileEntryView: View {
    @Environment(\.widgetFamily) private var family
    let entry: HijriEntry

    var body: some View {
        tile(dayText: entry.today?.gregorianDayText, caption: caption, circular: family == .accessoryCircular)
            .widgetURL(deepLink)
    }

    private var caption: String? {
        guard let today = entry.today else { return nil }
        return "\(today.gregorianMonthName) \(today.gregorianYear)"
    }
}

/// A fixed square tile: a heavy day figure over its month and year, the iOS counterpart of the
/// Android 1x1 date tiles.
///
/// The type sizes are derived from the height the widget actually granted, so the two lines fill
/// whatever space the family hands over instead of sitting in the middle of it with empty bands
/// above and below. A fixed point size can only ever be right for one widget size.
private func tile(dayText: String?, caption: String?, circular: Bool) -> some View {
    GeometryReader { geo in
        let height = max(8, geo.size.height - contentPadding * 2)
        let width = max(8, geo.size.width - contentPadding * 2)
        VStack(spacing: 0) {
            if let dayText, let caption {
                Text(dayText)
                    .font(.system(size: min(height * 0.60, width * 0.78), weight: .heavy, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.3)
                Text(caption)
                    .font(.system(size: min(height * 0.16, width * 0.24)))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.4)
            } else {
                Text("Unavailable")
                    .font(.system(size: height * 0.13))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
            }
        }
        .multilineTextAlignment(.center)
        .frame(width: width, height: height)
        .padding(contentPadding)
        .frame(width: geo.size.width, height: geo.size.height)
    }
}
