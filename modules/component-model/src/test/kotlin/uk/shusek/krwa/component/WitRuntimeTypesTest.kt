package uk.shusek.krwa.component

import java.lang.reflect.Modifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WitRuntimeTypesTest {
    @Test
    fun witValueKeepsStaticHelpersAndVariantShape() {
        val record = WitValue.record("body", byteArrayOf(1, 2, 3), "content-type", "text/plain")
        val flags = WitValue.flags("read", "write")
        val none = WitValue.none()
        val some = WitValue.some("payload")

        assertEquals(listOf("body", "content-type"), record.keys.toList())
        assertEquals(byteArrayOf(1, 2, 3).toList(), (record["body"] as ByteArray).toList())
        assertEquals(mapOf("read" to true, "write" to true), flags)
        assertEquals("none", none.label())
        assertFalse(none.hasValue())
        assertEquals("none", none.toString())
        assertEquals("some", some.javaClass.getMethod("label").invoke(some))
        assertEquals("payload", some.value())
        assertTrue(some.hasValue())
        assertEquals("some(payload)", some.toString())
        assertTrue(
            WitValue.Variant::class
                .java
                .declaredConstructors
                .filterNot { it.isSynthetic }
                .all { Modifier.isPrivate(it.modifiers) }
        )
        assertThrows(IllegalArgumentException::class.java) { WitValue.record("orphan") }
    }

    @Test
    fun resourceTableKeepsHandleLifecycle() {
        val table = WitResourceTable<String>()
        val resource = table.insert("value")

        assertEquals(1L, resource.handle())
        assertTrue(table.contains(resource))
        assertEquals(1, table.size())
        assertEquals("value", table.get(resource))
        assertEquals("value", table.remove(resource))
        assertFalse(table.contains(resource))
        assertEquals(0, table.size())
        assertThrows(ComponentModelException::class.java) { table.get(resource) }
        assertThrows(ComponentModelException::class.java) { table.remove(resource) }
    }

    @Test
    fun resourceTableRejectsNullValues() {
        val table = WitResourceTable<String?>()

        assertThrows(NullPointerException::class.java) { table.insert(null) }
    }
}
