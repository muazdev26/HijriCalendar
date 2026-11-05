# R8 rules for the sample app's minified release build.
#
# This file is deliberately empty: the library relies on *consumer* rules that ship inside the
# libraries, and adding a rule here would hide the day one of those stops being enough. The
# constructor keep this sample originally would have needed lives in
# `calendar-widget-glance/consumer-rules.pro` (`-keep class ...HijriWidget*Callback { <init>(); }`),
# because Glance keeps the callback *class* reflectively (`-keep public class * extends
# ActionCallback`) but not the constructor — every widget tap would otherwise land on
# `NoSuchMethodException` in a release build. The sample consumes the library, so it inherits the
# rule with no line here.
#
#   - WorkManager keeps `ListenableWorker` names and constructor (`HijriWidgetRefreshWorker`).
#   - `kotlinx-serialization-core` ships the rules that keep `@Serializable` generated serializers
#     (`WidgetOptions`, the widget wire format).
#   - Manifest-declared components (the six widget receivers, the midnight/boot receivers, the
#     activities) are kept by the manifest, not by a rule.
#   - `:sample-android-app:verifyReleaseR8KeepsWidgetCallbacks` (finalizedBy `assembleRelease`, and a
#     step in CI) reads R8's mapping back and fails when a widget-runtime class — or the no-arg
#     constructor of one of the five callbacks — was renamed or stripped.
