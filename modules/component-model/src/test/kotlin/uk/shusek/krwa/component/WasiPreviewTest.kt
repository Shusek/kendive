package uk.shusek.krwa.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WasiPreviewTest {
    @Test
    fun preview2IsStableComponentModel() {
        assertTrue(WasiPreview.PREVIEW2.isStable())
        assertTrue(WasiPreview.PREVIEW2.isComponentModel())
    }

    @Test
    fun preview3IsReleaseCandidateComponentModel() {
        assertEquals("0.3.0-rc-2026-03-15", WasiPreview.PREVIEW3.version())
        assertFalse(WasiPreview.PREVIEW3.isStable())
        assertTrue(WasiPreview.PREVIEW3.isReleaseCandidate())
        assertTrue(WasiPreview.PREVIEW3.isComponentModel())
        assertThrows(ComponentModelException::class.java) { WasiPreview.PREVIEW3.requireStable() }
    }
}
