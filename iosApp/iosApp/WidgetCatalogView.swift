import SwiftUI
import WidgetKit
#if WIDGET_EXTENSION
import WidgetCalendar
#else
import calendar
#endif

/// The iOS counterpart of the Android sample's widget catalog and settings screens, in one
/// scrolling form.
///
/// The option *schema* is `calendar-widget-data`'s `WidgetOptions`: this view never declares a
/// field of its own, it keeps the currently-edited value of each option in `@State` and rebuilds
/// the shared options value through `createWidgetOptions` (a flat factory, because Swift cannot
/// call a Kotlin constructor with eight parameters and two nullables). Saving writes that value
/// into the app group via `WidgetOptionsJson`, which is the exact JSON the widget extension
/// decodes — so the screen and the four widgets are reading one definition.
///
/// The `@State` fields below are the only per-platform part, and they are unavoidable: SwiftUI
/// needs mutable bindings, and a Kotlin data class is immutable from Swift.
///
/// This is the app-wide equivalent of Android's "family options mirror": one set of values for
/// all widgets, changed here, applied by "Apply to widgets". Per-placed-widget configuration is
/// the one thing iOS cannot offer without `AppIntentConfiguration`, which does not resolve in
/// this runtime, so the grid's viewed month and the options are shared instead.
struct WidgetCatalogView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var language: WidgetLanguage
    @State private var monthNameLanguage: WidgetLanguage
    @State private var numeralStyle: NumeralStyle
    @State private var source: WidgetSource
    @State private var adjustmentDays: Int32
    @State private var firstDayOfWeekIndex: Int32
    @State private var pinsMonth: Bool
    @State private var pinnedYear: Int32
    @State private var pinnedMonth: Int32
    @State private var applied = false

    init() {
        let options = HijriShared.loadOptions()
        _language = State(initialValue: options.language)
        _monthNameLanguage = State(initialValue: options.effectiveMonthNameLanguage)
        _numeralStyle = State(initialValue: options.numeralStyle)
        _source = State(initialValue: options.source)
        _adjustmentDays = State(initialValue: options.adjustmentDays)
        _firstDayOfWeekIndex = State(initialValue: options.firstDayOfWeekIndex)
        _pinsMonth = State(initialValue: options.isPinned)
        _pinnedYear = State(initialValue: options.pinnedYear?.int32Value ?? 1447)
        _pinnedMonth = State(initialValue: options.pinnedMonth?.int32Value ?? 1)
    }

    /// The edited values as the shared options value the widgets will read.
    private var edited: WidgetOptions {
        WidgetOptionsKt.createWidgetOptions(
            language: language,
            monthNameLanguage: monthNameLanguage,
            source: source,
            adjustmentDays: adjustmentDays,
            numeralStyle: numeralStyle,
            firstDayOfWeekIndex: firstDayOfWeekIndex,
            pinsMonth: pinsMonth,
            pinnedYear: pinnedYear,
            pinnedMonth: pinnedMonth
        )
    }

    private static let weekdayNames = [
        "Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
    ]

    var body: some View {
        NavigationStack {
            Form {
                if !HijriShared.isAppGroupAvailable {
                    Section {
                        Label(
                            "The app group is unavailable, so these settings will not reach the " +
                            "widgets. Check that both targets point at the same App Group capability.",
                            systemImage: "exclamationmark.triangle.fill"
                        )
                        .font(.footnote)
                        .foregroundStyle(.orange)
                    }
                }

                Section {
                    Picker("Language", selection: $language) {
                        ForEach(Self.languages) { Text($0.title).tag($0.value) }
                    }
                    Picker("Month names", selection: $monthNameLanguage) {
                        ForEach(Self.languages) { Text($0.title).tag($0.value) }
                    }
                    Picker("Numerals", selection: $numeralStyle) {
                        ForEach(Self.numeralStyles) { Text($0.title).tag($0.value) }
                    }
                } header: {
                    Text("Language")
                } footer: {
                    Text("Month names are independent of the widget language, so you can show " +
                         "Urdu month names inside an English (left-to-right) widget.")
                }

                Section {
                    Picker("Calculation", selection: $source) {
                        ForEach(Self.sources) { Text($0.title).tag($0.value) }
                    }
                    Stepper(
                        "Moon-sighting adjustment: \(adjustmentDays) day\(adjustmentDays == 1 ? "" : "s")",
                        value: $adjustmentDays,
                        in: -2...2
                    )
                } header: {
                    Text("Calendar")
                } footer: {
                    Text("Widgets will show this as \"" +
                         WidgetLocalization.shared.sourceLabel(source: source, language: language) +
                         "\". Shift the Hijri date by a locally observed moon sighting, −2 to +2 days.")
                }

                Section {
                    Picker("First day of week", selection: $firstDayOfWeekIndex) {
                        ForEach(Array(Self.weekdayNames.enumerated()), id: \.offset) { index, name in
                            Text(name).tag(Int32(index))
                        }
                    }
                }

                Section {
                    Toggle("Pin the grid to a fixed month", isOn: $pinsMonth)
                    if pinsMonth {
                        Stepper("Year: \(pinnedYear)", value: $pinnedYear, in: 1440...1500)
                        Picker("Month", selection: $pinnedMonth) {
                            ForEach(1...12, id: \.self) { month in
                                // Straight from the shared projection, so the picker shows the exact
                                // string the widget header will show in the chosen month script.
                                Text(edited.hijriMonthName(month: Int32(month))).tag(Int32(month))
                            }
                        }
                    }
                } header: {
                    Text("Grid")
                } footer: {
                    Text("The grid normally follows today. Pin it to hold one month, or use the " +
                         "chevrons on a placed widget to step through months — tap the month " +
                         "title to snap back to today.")
                }

                Section {
                    ForEach(HijriWidgetKind.allCases) { kind in
                        HStack(spacing: 12) {
                            Image(systemName: kind.symbol)
                                .frame(width: 24)
                                .foregroundStyle(.tint)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(kind.displayName).font(.subheadline.weight(.medium))
                                Text(kind.blurb).font(.caption).foregroundStyle(.secondary)
                                Text(kind.sizes).font(.caption2).foregroundStyle(.tertiary)
                            }
                        }
                        .padding(.vertical, 2)
                    }
                } header: {
                    Text("Available widgets")
                } footer: {
                    Text("Long-press the home screen, tap +, search \"Hijri\", then pick a size.")
                }

                Section {
                    Button {
                        apply(edited)
                    } label: {
                        Label("Apply to widgets", systemImage: "arrow.triangle.2.circlepath")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)

                    Button {
                        apply(WidgetOptions.companion.DEFAULTS)
                    } label: {
                        Label("Reset to defaults", systemImage: "arrow.uturn.backward")
                            .frame(maxWidth: .infinity)
                    }
                } footer: {
                    Text(applied
                         ? "Applied. Every placed widget rebuilt its timeline with these values."
                         : "Changes are written to the shared app group and picked up on the next render.")
                }
            }
            .navigationTitle("Widget settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func apply(_ options: WidgetOptions) {
        HijriShared.saveOptions(options)
        // Keep the form in step with what was stored, so "Reset to defaults" visibly resets the
        // controls and not just the widgets.
        language = options.language
        monthNameLanguage = options.effectiveMonthNameLanguage
        numeralStyle = options.numeralStyle
        source = options.source
        adjustmentDays = options.adjustmentDays
        firstDayOfWeekIndex = options.firstDayOfWeekIndex
        pinsMonth = options.isPinned
        if let year = options.pinnedYear { pinnedYear = year.int32Value }
        if let month = options.pinnedMonth { pinnedMonth = month.int32Value }
        WidgetCenter.shared.reloadAllTimelines()
        applied = true
    }
}

// MARK: - Picker contents
//
// The option *values* are the shared enums; only the display labels and the list of cases are
// local, because SwiftUI needs a concrete collection of tags and human-readable titles.

fileprivate struct LanguageChoice: Identifiable {
    let value: WidgetLanguage
    let title: String
    var id: String { value.name }
}

fileprivate struct NumeralStyleChoice: Identifiable {
    let value: NumeralStyle
    let title: String
    var id: String { value.name }
}

fileprivate struct SourceChoice: Identifiable {
    let value: WidgetSource
    let title: String
    var id: String { value.name }
}

extension WidgetCatalogView {
    fileprivate static let languages = [
        LanguageChoice(value: .urdu, title: "Urdu"),
        LanguageChoice(value: .english, title: "English"),
    ]

    fileprivate static let numeralStyles = [
        NumeralStyleChoice(value: .arabicIndic, title: "Eastern Arabic-Indic (٠-٩)"),
        NumeralStyleChoice(value: .western, title: "Western (0-9)"),
    ]

    fileprivate static let sources = [
        SourceChoice(value: .calculation, title: "Umm al-Qura"),
        SourceChoice(value: .pakistan, title: "Pakistan (Ruet-e-Hilal)"),
    ]
}
