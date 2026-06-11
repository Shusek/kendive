package uk.shusek.krwa.wasm.types

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uk.shusek.krwa.corpus.CorpusResources
import uk.shusek.krwa.wasm.Parser

class ValTypeTest {
    @Test
    fun roundtrip() {
        val cases =
            arrayOf(
                ValType.F64,
                ValType.F32,
                ValType.I64,
                ValType.I32,
                ValType.V128,
                ValType.FuncRef,
                ValType.ExternRef,
                ValType.builder()
                    .withOpcode(ValType.ID.RefNull)
                    .withTypeIdx(ValType.TypeIdxCode.FUNC.code())
                    .build(),
                ValType.builder()
                    .withOpcode(ValType.ID.Ref)
                    .withTypeIdx(ValType.TypeIdxCode.EXTERN.code())
                    .build(),
                ValType.builder().withOpcode(ValType.ID.Ref).withTypeIdx(16).build(),
            )

        for (valueType in cases) {
            val id = valueType.id()
            val roundTrip = ValType.builder().fromId(id).build()
            assert(valueType == roundTrip) { "Failed to roundtrip: $valueType" }
        }
    }

    @Test
    fun noneRefMatchesAnyHierarchyReferenceTypes() {
        assertTrue(ValType.matches(ValType.NoneRef, ValType.AnyRef))
        assertTrue(ValType.matches(ValType.NoneRef, ValType.EqRef))
        assertTrue(ValType.matches(ValType.NoneRef, ValType.I31Ref))
        assertTrue(ValType.matches(ValType.NoneRef, ValType.StructRef))
        assertTrue(ValType.matches(ValType.NoneRef, ValType.ArrayRef))
        assertFalse(ValType.matches(ValType.NoneRef, ValType.FuncRef))
        assertFalse(ValType.matches(ValType.NoneRef, ValType.ExternRef))
    }

    @Test
    fun checkExternRef() {
        val module =
            Parser.parse(CorpusResources.getResource("compiled/externref-example.wat.wasm"))

        assertEquals(3, module.typeSection().types().size)

        val type0 = module.typeSection().types()[0].returns()[0]
        assertEquals(ValType.TypeIdxCode.EXTERN.code(), type0.typeIdx())
    }
}
