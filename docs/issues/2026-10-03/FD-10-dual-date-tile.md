# FD-10: A strict 1×1 tile showing the Hijri and Gregorian date together

**Issue:** —
**Severity:** Medium (new widget)
**Blocks:** —
**Blocked by:** Decision D1 (resolved — see "D1: how the tile gets glyph-tight text")
**Module:** `calendar-widget-data`, `calendar-widget-glance`, `sample-android-app`
**Status:** Implemented — layout settled, and the rendering route is now recorded rather than open

---

## Problem

The library had two 1×1 tiles and each answered half the question:

- `HijriDateWidget` — weekday / Hijri day / Hijri month
- `GregorianDateWidget` — weekday / Gregorian day / Gregorian month

The only widget showing both calendars at once was `HijriTodayWidget`, the resizable strip, which
cannot be placed in a single cell. A user wanting both dates in one home-screen cell had no widget.

This ticket adds `HijriDualDateWidget`, showing **both dates in a strict 1×1 cell**, laid out like a
tear-off calendar page. It is not resizable and it is not a smaller strip.

## Reference

The reference is a calendar-page tile (rounded square, blue header band), read top to bottom:

| Zone | Content | Role |
|---|---|---|
| Header band (accent, white bold) | `ربيع ٢` | **Hijri month**, short form |
| Sub-row ends, small | `سبتمبر` / `30` | **Gregorian month** / **day** |
| Centre, very large, bold | `١٧` | **Hijri day** — the figure the tile exists to show |
| Bottom, small | `الأربعاء` | **Weekday** |

```
┌────────────────────────┐
│  HEADER BAND   ~27%    │  Hijri month, white on accent
├────────────────────────┤
│  Greg month ·  Greg day│  ~12%   small row, two ends
│       HIJRI DAY        │  ~31%   dominant figure
│        weekday         │  ~10%
└────────────────────────┘     remaining ~20% is padding + slack
```

Four things the image does that the spec kept:

1. **The Hijri day is the dominant figure**; the Gregorian day is subordinate.
2. **The Hijri month sits in a filled header band**, not a text line.
3. **Gregorian month and day share one row at opposite ends** — one date split by the page fold.
4. **The weekday is the last line**, which is the opposite of the two existing tiles; the band takes
   the top.

The reference mixes digit systems (`٢`, `١٧`, but `30`). That is a mock-up inconsistency, not a
requirement: **every numeral follows `options.numeralStyle`**, as on every other tile.

## Data

Everything but one string was already in `TodayHijriWidgetData`: `hijriDayText`,
`gregorianDayText`, `gregorianMonthName`, `weekdayName`. The missing piece was the **short** Hijri
month name — the band shows `ربيع ٢`, not `ربیع الثانی` or `Jumada al-akhirah`.

**`TodayHijriWidgetData.hijriMonthShortName`** was added, populated in `todayHijriWidgetData` from
`options.effectiveMonthNameLanguage`. Built in the shared projection rather than in the renderer
(WG-12/FD-05): a month name is text in the *widget's* language, which is a `WidgetOptions` field and
not a resource-configuration value, so a renderer cannot answer for it. Building it in the projection
is also what lets iOS pick it up with no Swift edit.

**The rule (D3, as proposed).** The four months whose name does not distinguish them from a sibling —
Rabi' I/II and Jumada I/II — render the **shared base name** plus an ordinal digit in the widget's
numeral style: `ربیع ۱`, `ربیع ۲`, `جمادی ۱`, `جمادی ۲`. The other eight render their ordinary name,
unchanged.

The base is the *shared* name, not the full one: `ربیع الثانی ۲` is not a short form of anything, and
it is exactly the string the band is too narrow for. So four base names per language, not twelve
short names for twelve months:

```kotlin
WidgetLocalization.pairedHijriMonths            // {3, 4, 5, 6}
WidgetLocalization.pairedHijriMonthBase(3, URDU) // "ربیع"
WidgetLocalization.hijriMonthShortName(month, monthName, ordinal, language)
```

`ShortHijriMonthNameTest` pins all twelve months in both languages and both numeral styles, asserts
the eight unpaired months are byte-identical to their long names, and asserts the base is never
derived by trimming the long name.

## Layout

Fixed tile, `SizeMode.Single`, no settings screen, no per-widget state. It follows the family options
mirror (`HijriWidgetConfig.loadFamily`) like the other two tiles, and tapping anywhere opens the app.

### Strict 1×1 means the widget-info is the same as the other tiles

```xml
android:minWidth="40dp"
android:minHeight="40dp"
android:resizeMode="none"
android:targetCellWidth="1"
android:targetCellHeight="1"
```

**`minHeight` is deliberately not raised to make the content fit.** On launchers before Android 12 the
cell count is derived from `minWidth`/`minHeight` (`cells = ceil((size + 30) / 70)`), so a 72dp
minimum is *two* cells tall there. The strip can afford that because it is resizable; a strict 1×1
cannot. The content is sized to the cell the launcher grants instead.

### Sizing: borrowed from the sibling tiles

The strongest thing this layout can do is not pick numbers at all. Every size is **read from
`DateTileTypography`** — the object `HijriDateWidget` and `GregorianDateWidget` already size themselves
with:

| Zone | Size | Source |
|---|---|---|
| Hijri day (centred) | fixed | `DateTileTypography.daySize` less a sixth |
| Weekday (the band) | width-scaled name × `CAPTION_RATIO` | `DateTileTypography.weekdaySizeFor` |
| Hijri month name | same rule, same size as the band | `DateTileTypography.weekdaySizeFor` |
| Gregorian date | this tile's own caption | clamped to the sibling caption floor |

The captions are a **scale** of the sibling rule, not constants, so they still widen with the granted
width; the clamp is `CAPTION_MAX_SHARE` of the day figure, so nothing can overtake it.

### The layout

```
┌────────────────────────┐
│        بدھ             │  band: the WEEKDAY name
├────────────────────────┤
│         ۲۴             │  the Hijri day, centred — the answer
│     ربیع الثانی        │  the Hijri month name, IN FULL
│           ستمبر - ۳۰    │  the Gregorian date, centred — day right, month left
└────────────────────────┘
```

**The band carries the weekday, and the month name moved into the body in full.** The band was originally
a band *for the month name*, which is why FD-10 added `hijriMonthShortName` — `ربیع الثانی` does not fit a
1×1 band. Putting the weekday there instead solves the width problem with a one-word string, so the month
name gets the body's width and no longer needs abbreviating. **`hijriMonthShortName` is consequently no
longer read by Android**; it stays on the shared projection for iOS, whose band is still narrow, so the
field and `ShortHijriMonthNameTest` remain.

**The band is grown by padding *below* its text, never above.** A line box's leading is asymmetric — more
space below the baseline than above it — so symmetric padding lands off-centre by the descent, which
showed on a device as a stripe of empty accent above the text.

### The Gregorian date: centred, with a fixed day-right order

```
│             ستمبر ۳۰    │  centred; day right, month left
```

Two requirements that each look like the other, and which needed separating to get right:

- **Centred** answers *"is this one thing?"* — so the halves sit **adjacent**. A revision that weighted one
  of them pushed them to opposite physical ends, which read as two unrelated labels.
- **Fixed order** answers *"which side is the number on?"* — month left, day right. That is a property of the
  design, not of the script.

A revision between the two aligned the **whole phrase** to the reading-start end, right for Urdu and left for
English. That slid the date to one side of the tile — a different mistake with the same-looking symptom.

**The two halves are joined by a dash**, `ستمبر - ۳۰`: without it the phrase reads as two labels rather
than one date. The separator is muted to `widget_text_secondary` so it stays punctuation beside two
`primaryText` month names, and its string is a **literal** — a separator is punctuation, not text, so unlike
the month name, the weekday and the numerals it is not language-specific and does not belong in the
projection (WG-12). The grid header joins its own two title halves with a literal for the same reason.

The order therefore keys on `computeDeviceLayoutRtl` — the **device** — because a Glance `Row` is a
horizontal `LinearLayout` the platform mirrors on an RTL device, and the emission order has to run against
that mirroring exactly once. `computeLayoutRtl` would be the category error: it folds `options.language` in,
so on an Urdu widget it would emit month-first and land the **day on the left**, the opposite of the design —
and it looks correct on an English phone, which is how it would ship.

This is the third arrangement this row has had, and each is recorded in the KDoc for the same reason.

### Both month names, one colour

The Hijri and Gregorian month names are drawn in **`widget_text_primary` on every widget**. The Gregorian one
was `widget_text_secondary` on the strip, the grid header and this tile while the Hijri one beside it was
primary — two weights of the same sentence, which read as though the Gregorian date were a footnote on the
Hijri one rather than its counterpart.

The distinction each widget still needs comes from the **day figures** (the Hijri figure is the accent
colour, the Gregorian one primary), from size, and on the grid header from weight. The two existing 1×1 tiles
were already consistent, because they share one `DateTileRoot` whose month line has always been primary for
both calendars.

On the strip the fix is **structural**: `DateSide` took a per-side `captionColor`, which is precisely the
seam that let the halves drift. It now takes the palette and derives both colours from `gregorian`, so the
captions cannot disagree.

### The posture, and the two versions that failed

The sibling tiles ask for ~107–129dp of line box in a cell whose declared minimum is 40dp and **accept
clipping below that**; `DateTileTypography`'s KDoc records that a band layout fitting each line to its own
share of the cell was tried and reverted because it cost ~10sp of the captions' size. This tile takes the
same trade.

Two earlier versions failed on-device as **unreadable text**, and both are worth keeping as the record of
what does not work:

1. **Every zone as a fraction of the granted height** — band 27%, sub-row 12%, day 31%, weekday 10%.
   Reads as a considered design; rendered a **6sp weekday** on a real 72dp cell.
2. **Its shrink-to-fit remedy, which was worse and invisible.** The shrink's pivot included the day
   figure's floor, so **raising** the caption sizes moved the pivot up. Requesting 20/20/23sp rendered a
   **12.9sp** header — smaller than the 15sp it replaced. A screenshot looked plausible and no test
   failed; only reading the numbers showed it.

**Never shrink a 1×1 tile's type to fit its cell.**

### The equal-margin requirement — and what this layout can and cannot promise

FD-10 asked that the distance from the tile's top edge to the top of the header band's text, and from
the bottom of the weekday's glyphs to the bottom edge, be equal **to within 1dp, for every supported
language and numeral style, at every granted size**.

## D1: how the tile gets glyph-tight text

**Glance 1.2.0 cannot deliver that, and no arrangement of Glance `Text`s can.** Each `Text` is
inflated as a `TextView` whose style sets only `ellipsize`, so the font's ascent-plus-descent leading
stays inside its box; `TextStyle` has no `lineHeight` parameter; `PaddingModifier` is non-negative, so
the leading cannot be pushed out either. The two routes that could measure real glyph bounds were an
`AndroidRemoteViews` layout (`includeFontPadding="false"`, which removes font padding but keeps the
leading, so margins get *closer* to equal rather than provably equal) and a drawn `Bitmap` through
`Image` (which can measure, but costs a hand-written accessibility label).

**Decided: stay Glance, and state the criterion honestly.** The tile is built like every other widget
in the family, and the acceptance criterion is reduced to what it can actually be.

The margin guarantee is then constructed rather than hoped for:

```
Column (vertical padding = EDGE_INSET_DP, the same value at both ends)
├── Box  header band, flush to the tile's width, widget_accent fill
└── Box  defaultWeight(), centre-aligned
    └── Column
        ├── Row  fillMaxWidth: Gregorian month … Gregorian day
        ├── Text Hijri day, widget_accent, bold
        └── Text weekday
```

The two insets are **one constant**, so they cannot drift apart. The weighted region is
centre-aligned, which is what splits the 0.20 slack evenly between the space above the sub-row and
the space below the weekday — a top-pinned region would render its content first and pool all the
slack under the weekday, which reads as a mistake.

**So the honest form of the criterion is: the insets are equal by construction, and the residual is
the per-line leading, bounded by `DualDateTileTypography.RESIDUAL_MARGIN_DP`.** That is what
`DualDateTilePreviewLayoutTest` asserts, and it is why that test asserts the two halves separately
rather than claiming a 1dp equality it cannot deliver.

### Reading direction

The sub-row is the tile's only left/right order. Its children are ordered by
`computeLayoutRtl(context, options.language)` — the strip's rule — so platform mirroring lands the
Gregorian month at the reading-**start** end and the day at the reading-**end** end in both
directions.

Note the reference shows the month at the *left* in an RTL context, which is the reading-*end* end.
This follows the stated rule rather than the reference's placement: the rule is what the rest of the
family is built on, and the reference is a mock-up that already mixes digit systems.

### Colour and typography

- Header band: `widget_accent` fill, `widget_on_today` text. **No new colour token** for the
  reference's blue — reusing the family accent is what makes the band re-resolve for night mode like
  every other surface (FD-07). A family blue would be a palette decision for all of it.
- Hijri day: `widget_accent`, bold, `HijriWidgetFonts.dayNumber`.
- Gregorian month: `widget_text_secondary`, `HijriWidgetFonts.gregorianTitle`.
- Gregorian day: `widget_text_primary`, bold, `HijriWidgetFonts.dayNumber`.
- Weekday: `widget_text_primary`, `HijriWidgetFonts.weekday`.
- Header: `HijriWidgetFonts.monthTitle`.
- No second rounded rectangle inside the host's widget shape.

Sizes come from `DualDateTileTypography`, as data, in the same arrangement as `DateTileTypography` and
`TodayStripTypography`, so the Android 12–14 `previewLayout` mirror can be held to it by a test.

## Where the new widget is registered

Derived from every place `GregorianDateWidget` appears in the repo.

**`calendar-widget-glance`**

- `HijriDualDateWidget` (`GlanceAppWidget`) and `HijriDualDateWidgetReceiver`, beside
  `HijriDateWidget.kt` / `HijriDateWidgetReceiver.kt`
- `<receiver>` in the **library** manifest — the library is the single declaration site (WG-10)
- `res/xml/hijri_dual_date_widget_info.xml` (`updatePeriodMillis="1800000"` like the other infos)
- `res/layout/hijri_dual_date_widget_preview_layout.xml` — Android 12–14 `previewLayout`
- `res/drawable/hijri_dual_date_widget_preview.xml` — `previewImage` for < 12
- `res/values/strings.xml` — label and description
- `providePreview` + `previewSizeMode` (`SizeMode.Single`) for Android 15+
- `HijriWidgetPreviewPublisher` — added to the `setWidgetPreviews` set
- `HijriWidgetRenderQueue.renderAction` — sweeps **five** classes
- `HijriDualDateWidgetLivePreview`, matching the other four
- `api/public-surface.txt` — three new declarations, with reasons
- `.github/consumer-check/.../ConsumerResolutionCheck.kt` — the widget, receiver, preview composable,
  and `hijriMonthShortName`

**`sample-android-app`**

- `res/xml/hijri_dual_date_widget_info.xml` override with `android:configure`
- `HijriDualDateWidgetConfigureActivity`, a thin subclass of `HijriWidgetSettingsActivity`
- `WidgetKind.DUAL_DATE` driving the settings screen's title, subtitle and live preview
- A `WidgetCatalogActivity` entry with live preview and **Add to home**
- Manifest `<activity>` for the configure activity

**Docs and gates** — `AGENTS.md`'s "four widget classes" paragraphs become five; `CHANGELOG.md`;
`./gradlew apiDump` for the new field. `detektBaseline` was **not** regenerated.

**iOS** is out of scope. `hijriMonthShortName` reaches Swift through the shared projection, so a
WidgetKit equivalent is a follow-up ticket.

## Tests

- `ShortHijriMonthNameTest` (widget-data, commonTest) — all twelve months × both languages × both
  numeral styles; the base is never derived by trimming; the field survives alongside
  `hijriMonthName`.
- `DualDateTilePreviewLayoutTest` (glance) — 17 tests: insets equal at every granted size, each inset
  the edge inset plus half the slack, the slack covering both insets at the declared minimum, nothing
  clipping at any size, the Hijri day largest at every size, monotonic growth, the reference
  proportions, the residual bounded and fitting inside half the slack, and the static preview mirror
  held to the live numbers (sizes, order, centred region, weighted sub-row, equal padding, accent
  band, and a strict-single-cell widget-info).

## Known limitations

- The margins are equal at the level the layout controls; the residue is font leading, bounded and
  quoted. See D1.
- A 40dp grant renders all four zones small. That is below where this tile reads well — the same
  position `DateTileTypography` takes for the existing tiles — and it clips nothing.
- Arabic month and weekday names are not available (D2): `WidgetLanguage` has only `URDU` and
  `ENGLISH`, so this tile renders `ربیع ۱` / `ستمبر` / `بدھ` where the reference shows `ربيع ٢` /
  `سبتمبر` / `الأربعاء`. Adding Arabic is a schema change with `@JsonNames` aliases, localized name
  lists and wire-format tests, and FD-10 scopes it out.

## Out of scope

- Resizing — a resizable dual-date widget already exists, the Today strip.
- Settings of its own — it follows the family mirror.
- Showing either year — the existing tiles dropped the year for the same lack of room.
- Changing the two existing 1×1 tiles.
