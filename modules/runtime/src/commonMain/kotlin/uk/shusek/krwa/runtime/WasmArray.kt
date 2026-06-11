package uk.shusek.krwa.runtime

/**
 * Runtime representation of a WasmGC array instance. Elements are stored as raw long values (same
 * encoding as stack values). Packed types (i8, i16) are stored as full long slots for simplicity.
 */
class WasmArray(private val typeIdxValue: Int, private val elementsValue: LongArray) : WasmGcRef {
    override fun typeIdx(): Int = typeIdxValue

    fun get(idx: Int): Long = elementsValue[idx]

    fun set(idx: Int, value: Long) {
        elementsValue[idx] = value
    }

    fun length(): Int = elementsValue.size

    fun elements(): LongArray = elementsValue
}
