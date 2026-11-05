# Glance resolves every ActionCallback subclass reflectively at tap time: `AsyncRequestWorker`
# reads the class name Glance baked into the widget's PendingIntent and instantiates it with
# `Class.forName(name).getDeclaredConstructor().newInstance()`.
#
# Glance's own consumer rule (`-keep public class * extends ActionCallback`) keeps only the class,
# so on a minified build the six navigation callbacks below are present while their no-arg
# constructors are not, and every tap lands on `NoSuchMethodException` with nothing visible to the
# user. The constructors are kept here, in the library, so a consumer does not discover this on a
# device.
#
# The wildcard is deliberate: it covers the whole `HijriWidget*Callback` family by segment, so a
# future callback needs no rule to stay tappable.
-keep class com.muazdev.hijricalendar.widget.glance.HijriWidget*Callback {
    public <init>();
}
