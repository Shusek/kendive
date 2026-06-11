package uk.shusek.krwa.component

import java.util.Objects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class WitTupleTest {
    @Test
    fun tuple4KeepsJavaStyleAccessorsAndValueBehavior() {
        val tuple = WitTuple4(1, "two", true, 4L)

        assertEquals(1, tuple.first)
        assertEquals(1, tuple.first())
        assertEquals(1, tuple.component1())
        assertEquals("two", tuple.second())
        assertEquals(true, tuple.third())
        assertEquals(4L, tuple.fourth())
        assertEquals(1, tuple.javaClass.getMethod("getFirst").invoke(tuple))
        assertEquals(1, tuple.javaClass.getMethod("first").invoke(tuple))
        assertEquals("(1, two, true, 4)", tuple.toString())
        assertEquals(Objects.hash(1, "two", true, 4L), tuple.hashCode())
        assertEquals(tuple, WitTuple4(1, "two", true, 4L))
        assertNotEquals(tuple, WitTuple4(1, "two", false, 4L))
    }

    @Test
    fun tuple8KeepsAllPositionalAccessors() {
        val tuple = WitTuple8(1, 2, 3, 4, 5, 6, 7, 8)
        val (first, second, third, fourth, fifth, sixth, seventh, eighth) = tuple

        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8),
            listOf(first, second, third, fourth, fifth, sixth, seventh, eighth),
        )
        assertEquals(1, tuple.first())
        assertEquals(2, tuple.second())
        assertEquals(3, tuple.third())
        assertEquals(4, tuple.fourth())
        assertEquals(5, tuple.fifth())
        assertEquals(6, tuple.sixth())
        assertEquals(7, tuple.seventh())
        assertEquals(8, tuple.eighth())
        assertEquals("(1, 2, 3, 4, 5, 6, 7, 8)", tuple.toString())
        assertEquals(Objects.hash(1, 2, 3, 4, 5, 6, 7, 8), tuple.hashCode())
    }
}
