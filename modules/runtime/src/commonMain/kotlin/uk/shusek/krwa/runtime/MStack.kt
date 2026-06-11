package uk.shusek.krwa.runtime

open class MStack {
    private var count = 0
    private var elements = LongArray(MIN_CAPACITY)

    private fun increaseCapacity() {
        val newCapacity = elements.size shl 1
        val array = LongArray(newCapacity)
        elements.copyInto(array)
        elements = array
    }

    // internal use only!
    fun array(): LongArray = elements

    fun push(value: Long) {
        elements[count] = value
        count++

        if (count == elements.size) {
            increaseCapacity()
        }
    }

    fun pop(): Long {
        count--
        return elements[count]
    }

    fun peek(): Long = elements[count - 1]

    fun size(): Int = count

    fun discardToSize(size: Int) {
        if (count > size) {
            count = size
        }
    }

    companion object {
        const val MIN_CAPACITY: Int = 8
    }
}
