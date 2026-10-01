package com.muazdev.hijricalendar.widgetdata

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * A lenient `Int` serializer for [WidgetOptions.adjustmentDays]: a number that does not fit the
 * field is **clamped**, not rejected.
 *
 * **Why this exists.** `coerceInputValues` (WD-06) contains an unknown *enum* value and a `null` for
 * a non-nullable field, but it does **not** contain a number that overflows the target type: a
 * `99999999999999` where an `Int` is expected fails the whole decode, so every *other* field is lost
 * too. That is the exact failure WD-06 exists to prevent — one unreadable value resetting the
 * widget's language, numerals, week start, pin and source — just arriving through the number parser
 * instead of the enum parser.
 *
 * Clamping is the right answer for this field specifically. A moon-sighting adjustment is a handful
 * of days; a stored value of a billion is a corrupt blob, and the useful reading of it is "some
 * absurd number", of which the closest safe value is the limit. Rejecting the blob instead would
 * take a user who had set, say, `+2` down to all-defaults because a *different* field was garbage.
 *
 * Scoped to this one field on purpose. A general "lenient number" serializer would hide real
 * corruption everywhere, and a real corruption elsewhere is worth a `null` from the decoder.
 */
public object AdjustmentDaysSerializer : KSerializer<Int> {

    /** The most extreme adjustment worth honouring. Beyond this the stored value is not a sighting. */
    private const val LIMIT: Int = 100

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AdjustmentDays", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value.coerceIn(-LIMIT, LIMIT))
    }

    override fun deserialize(decoder: Decoder): Int {
        // `content` is the raw token: "-5", "99999999999999", or "\"nope\"". Parsing it as a Long
        // first is what lets an out-of-Int-range number be clamped instead of throwing, and
        // `toLongOrNull` is what makes a non-numeric token take the default rather than throw.
        val raw = (decoder as? JsonDecoder)
            ?.decodeJsonElement()
            .let { it as? JsonPrimitive }
            ?.content
        return raw?.toLongOrNull()?.coerceIn(-LIMIT.toLong(), LIMIT.toLong())?.toInt() ?: 0
    }
}
