package uk.shusek.krwa.wasm.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import uk.shusek.krwa.wasm.InvalidException

class MemoryLimitsTest {
    @Test
    fun shouldCreateDefaultMemoryLimits() {
        val defaults = MemoryLimits.defaultLimits()
        assertNotNull(defaults)
        assertEquals(0, defaults.initialPages())
        assertEquals(MemoryLimits.MAX_PAGES, defaults.maximumPages())
    }

    @Test
    fun shouldThrowOnInvalidMemoryLimits() {
        assertThrows(InvalidException::class.java) { MemoryLimits(-1, -1) }
        assertThrows(InvalidException::class.java) { MemoryLimits(0, -1) }
        assertThrows(InvalidException::class.java) { MemoryLimits(2, 1) }
        assertThrows(InvalidException::class.java) { MemoryLimits(2, Int.MAX_VALUE) }
    }
}
