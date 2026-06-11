package uk.shusek.krwa.wasm

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.stream.Collectors.toList
import kotlinx.io.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import uk.shusek.krwa.corpus.CorpusResources
import uk.shusek.krwa.wasm.types.ActiveDataSegment
import uk.shusek.krwa.wasm.types.CustomSection
import uk.shusek.krwa.wasm.types.ExternalType
import uk.shusek.krwa.wasm.types.OpCode
import uk.shusek.krwa.wasm.types.SectionId
import uk.shusek.krwa.wasm.types.ValType

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ParserTest {
    @Test
    fun shouldParseFile() {
        CorpusResources.getResource("compiled/start.wat.wasm").use { inputStream ->
            val module = Parser.parse(inputStream)

            // check types section
            val typeSection = module.typeSection()
            val types = typeSection.types()
            assertEquals(2, types.size)
            assertEquals("(I32) -> nil", types[0].toString())
            assertEquals("() -> nil", types[1].toString())

            // check import section
            val importSection = module.importSection()
            assertEquals(1, importSection.importCount())
            assertEquals(ExternalType.FUNCTION, importSection.getImport(0).importType())
            assertEquals("env", importSection.getImport(0).module())
            assertEquals("gotit", importSection.getImport(0).name())

            // check data section
            val dataSection = module.dataSection()
            assertEquals(1, dataSection.dataSegmentCount())
            val segment = dataSection.getDataSegment(0) as ActiveDataSegment
            assertEquals(0, segment.index())
            assertEquals(OpCode.I32_CONST, segment.offsetInstructions()[0].opcode())
            assertArrayEquals(byteArrayOf(0x00, 0x01, 0x02, 0x03), segment.data())

            // check start section
            val startSection = module.startSection()
            assertEquals(1, startSection!!.startIndex())

            // check function section
            val funcSection = module.functionSection()
            assertEquals(1, funcSection.functionCount())
            assertEquals(1, funcSection.getFunctionType(0))

            val memorySection = module.memorySection()
            assertEquals(1, memorySection!!.memoryCount())
            assertEquals(1, memorySection.getMemory(0).limits().initialPages())
            assertEquals(65536, memorySection.getMemory(0).limits().maximumPages())

            val codeSection = module.codeSection()
            assertEquals(1, codeSection.functionBodyCount())
            val function = codeSection.getFunctionBody(0)
            assertEquals(0, function.localTypes().size)
            val instructions = function.instructions()
            assertEquals(3, instructions.size)

            assertTrue(instructions[0].toString().contains("0x00000032: I32_CONST [42]"))
            assertEquals(OpCode.I32_CONST, instructions[0].opcode())
            assertEquals(42L, instructions[0].operand(0))
            assertEquals(OpCode.CALL, instructions[1].opcode())
            assertEquals(0L, instructions[1].operand(0))
            assertEquals(OpCode.END, instructions[2].opcode())
        }
    }

    @Test
    fun shouldParseIterfact() {
        CorpusResources.getResource("compiled/iterfact.wat.wasm").use { inputStream ->
            val module = Parser.parse(inputStream)

            // check types section
            val typeSection = module.typeSection()
            val types = typeSection.types()
            assertEquals(1, types.size)
            assertEquals("(I32) -> (I32)", types[0].toString())

            // check function section
            val funcSection = module.functionSection()
            assertEquals(1, funcSection.functionCount())
            assertEquals(0L, funcSection.getFunctionType(0).toLong())

            val codeSection = module.codeSection()
            assertEquals(1, codeSection.functionBodyCount())
            val function = codeSection.getFunctionBody(0)
            val locals = function.localTypes()
            assertEquals(1, locals.size)
            assertEquals(ValType.I32, locals[0])
            val instructions = function.instructions()
            assertEquals(22, instructions.size)
        }
    }

    @Test
    fun shouldParseKotlinxIoSource() {
        val wasm = CorpusResources.getResource("compiled/start.wat.wasm").readBytes()
        val source = Buffer()
        source.write(wasm)
        val module = Parser.parse(source)

        assertEquals(2, module.typeSection().types().size)
        assertEquals(1, module.codeSection().functionBodyCount())
        assertEquals(1, module.memorySection()!!.memoryCount())
    }

    @Test
    fun shouldParseAllFiles() {
        for (file in wasmCorpusFiles()) {
            try {
                FileInputStream(file).use { inputStream -> Parser.parse(inputStream) }
            } catch (e: IOException) {
                throw RuntimeException(String.format("Failed to parse file %s", file), e)
            } catch (e: RuntimeException) {
                throw RuntimeException(String.format("Failed to parse file %s", file), e)
            }
        }
    }

    @Test
    fun shouldSupportCustomListener() {
        val parser = Parser.builder().includeSectionId(SectionId.CUSTOM).build()

        CorpusResources.getResource("compiled/count_vowels.rs.wasm").use { inputStream ->
            parser.parse(inputStream) { section ->
                if (section.sectionId() == SectionId.CUSTOM) {
                    val customSection = section as CustomSection
                    val name = customSection.name()
                    assertFalse(name.isEmpty())
                } else {
                    fail("Should not have received section with id: " + section.sectionId())
                }
            }
        }
    }

    @Test
    fun shouldParseFloats() {
        CorpusResources.getResource("compiled/float.wat.wasm").use { inputStream ->
            val module = Parser.parse(inputStream)
            val codeSection = module.codeSection()
            val functionBody = codeSection.getFunctionBody(0)
            val f32 =
                java.lang.Float.intBitsToFloat(functionBody.instructions()[0].operand(0).toInt())
            assertEquals(0.12345678f, f32, 0.0f)
            val f64 = java.lang.Double.longBitsToDouble(functionBody.instructions()[1].operand(0))
            assertEquals(0.123456789012345, f64, 0.0)
        }
    }

    @Test
    fun shouldProperlyParseSignedValue() {
        CorpusResources.getResource("compiled/i32.wat.wasm").use { inputStream ->
            val module = Parser.parse(inputStream)
            val codeSection = module.codeSection()
            val functionBody = codeSection.getFunctionBody(0)
            assertEquals(-2147483648L, functionBody.instructions()[0].operand(0))
            assertEquals(0L, functionBody.instructions()[2].operand(0))
            assertEquals(2147483647L, functionBody.instructions()[4].operand(0))
            assertEquals(Long.MIN_VALUE, functionBody.instructions()[6].operand(0))
            assertEquals(0L, functionBody.instructions()[8].operand(0))
            assertEquals(Long.MAX_VALUE, functionBody.instructions()[10].operand(0))
            assertEquals(-2147483647L, functionBody.instructions()[12].operand(0))
            assertEquals(2147483646L, functionBody.instructions()[14].operand(0))
            assertEquals(-9223372036854775807L, functionBody.instructions()[16].operand(0))
            assertEquals(9223372036854775806L, functionBody.instructions()[18].operand(0))
            assertEquals(-1L, functionBody.instructions()[20].operand(0))
            assertEquals(1L, functionBody.instructions()[22].operand(0))
            assertEquals(-1L, functionBody.instructions()[24].operand(0))
            assertEquals(1L, functionBody.instructions()[26].operand(0))
        }
    }

    @Test
    fun shouldParseLocalDefinitions() {
        CorpusResources.getResource("compiled/define-locals.wat.wasm").use { inputStream ->
            val module = Parser.parse(inputStream)
            val codeSection = module.codeSection()
            val functionBody = codeSection.getFunctionBody(0)
            assertEquals(functionBody.localTypes()[0], ValType.I32)
            assertEquals(functionBody.localTypes()[1], ValType.I64)
        }
    }

    @Test
    fun shouldParseNamesSection() {
        CorpusResources.getResource("compiled/count_vowels.rs.wasm").use { inputStream ->
            val module = Parser.parse(inputStream)
            val nameSection = module.nameSection()!!
            assertEquals(module.codeSection().functionBodyCount(), nameSection.functionNameCount())
            assertEquals("__stack_pointer", nameSection.nameOfGlobal(0))
            assertEquals(".rodata", nameSection.nameOfData(0))
        }
    }

    @Test
    fun shouldParseSIMD() {
        CorpusResources.getResource("wasm/simd_load.0.wasm").use { inputStream ->
            Parser.parse(inputStream)
        }
    }

    @Test
    fun shouldParseOnlyImportedTags() {
        CorpusResources.getResource("compiled/issue_906.wat.wasm").use { inputStream ->
            Parser.parse(inputStream)
        }
    }

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun wasmCorpusFiles(): List<File> {
            val compiledDir = File("../../testing/wasm-corpus/src/main/resources/compiled/")
            Files.list(compiledDir.toPath()).use { stream ->
                val files =
                    stream
                        .map(Path::toFile)
                        .filter { file -> file.name.lowercase(Locale.ROOT).endsWith(".wasm") }
                        .collect(toList())
                if (files.isEmpty()) {
                    throw IOException("Could not find files")
                }
                return files
            }
        }
    }
}
