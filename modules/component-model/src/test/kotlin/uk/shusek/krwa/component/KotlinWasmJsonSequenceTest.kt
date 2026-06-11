package uk.shusek.krwa.component

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumMap
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uk.shusek.krwa.runtime.HostFunction
import uk.shusek.krwa.runtime.ImportValues
import uk.shusek.krwa.runtime.Instance
import uk.shusek.krwa.runtime.WasmFunctionHandle
import uk.shusek.krwa.wasi.WasiOptions
import uk.shusek.krwa.wasi.WasiPreview1
import uk.shusek.krwa.wasm.Parser
import uk.shusek.krwa.wasm.types.FunctionType
import uk.shusek.krwa.wasm.types.OpCode
import uk.shusek.krwa.wasm.types.ValType

class KotlinWasmJsonSequenceTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun benchmarksJsonSequenceDecodeFromHostStreamInKotlinWasm() {
        assumeTrue(
            java.lang.Boolean.getBoolean(BenchmarkEnabledProperty),
            "Enable with -D$BenchmarkEnabledProperty=true",
        )

        val targetBytes =
            Integer.getInteger(BenchmarkPayloadBytesProperty, DefaultBenchmarkPayloadBytes)
        val payload = generatedCatalogJsonPayload(targetBytes)
        val wasm = compileJsonSequenceGuest()
        val host = JsonStreamHost(payload.bytes)
        val stdout = ByteArrayOutputStream()
        val wasi = WasiPreview1.builder()
            .withOptions(WasiOptions.builder().withStdout(stdout).build())
            .build()
        val imports = ImportValues.builder()
            .addFunction(*wasi.toHostFunctions())
            .addFunction(*host.functions())
            .build()
        val opcodeProfiler = OpcodeProfiler()
        val instanceBuilder = Instance.builder(parseWasm(wasm))
            .withImportValues(imports)
        if (java.lang.Boolean.getBoolean(ProfileOpcodesProperty)) {
            instanceBuilder.withUnsafeExecutionListener(opcodeProfiler::onExecution)
        }
        val instance = instanceBuilder.build()

        opcodeProfiler.start("drain")
        val drainStarted = System.nanoTime()
        val drainedBytes = instance.export("run_drain_source").apply()[0]
        val drainElapsedNanos = System.nanoTime() - drainStarted
        val drainGuestElapsedNanos = host.reportedElapsedNanos
        val drainReads = host.readCalls
        val drainProfile = opcodeProfiler.stop()

        assertEquals(payload.bytes.size.toLong(), drainedBytes)
        assertEquals(payload.bytes.size.toLong(), host.reportedPrimaryValue)
        assertTrue(drainReads > 1, "Expected streaming drain reads, got $drainReads")

        opcodeProfiler.start("decode")
        val decodeStarted = System.nanoTime()
        val publicCount = instance.export("run_decode_filter").apply()[0]
        val decodeElapsedNanos = System.nanoTime() - decodeStarted
        val decodeProfile = opcodeProfiler.stop()

        assertEquals(payload.publicCount.toLong(), publicCount)
        assertEquals(payload.itemCount.toLong(), host.reportedPrimaryValue)
        assertEquals(payload.publicCount.toLong(), host.reportedPublicCount)
        assertTrue(host.readCalls > 1, "Expected streaming reads, got ${host.readCalls}")
        println(
            "KRWA Kotlin/Wasm JSON sequence: bytes=${payload.bytes.size}, " +
                "items=${payload.itemCount}, public=${payload.publicCount}, " +
                "drainReads=$drainReads, drainGuestMs=${drainGuestElapsedNanos / 1_000_000}, " +
                "drainHostMs=${drainElapsedNanos / 1_000_000}, " +
                "decodeReads=${host.readCalls}, " +
                "decodeGuestMs=${host.reportedElapsedNanos / 1_000_000}, " +
                "decodeHostMs=${decodeElapsedNanos / 1_000_000}" +
                drainProfile.formatted() +
                decodeProfile.formatted()
        )
    }

    private fun compileJsonSequenceGuest(): Path {
        val projectDir = tempDir.resolve("json-sequence-guest")
        copyTestFixtureProject("json-sequence-guest", projectDir)

        val outputFile = projectDir.resolve("gradle-output.log")
        val process = ProcessBuilder(
            repoGradlew().toString(),
            "--no-daemon",
            "--stacktrace",
            "-q",
            "compileProductionExecutableKotlinWasmWasi",
        )
            .directory(projectDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(outputFile.toFile())
            .start()
        val finished = process.waitFor(180, TimeUnit.SECONDS)
        val output = if (Files.exists(outputFile)) Files.readString(outputFile) else ""
        if (!finished) process.destroyForcibly()
        assertTrue(finished, output)
        assertEquals(0, process.exitValue(), output)
        return findCompiledWasm(projectDir)
    }

    private fun repoGradlew(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current.parent != null && !Files.exists(current.resolve("gradlew"))) {
            current = current.parent
        }
        val gradlew = if (System.getProperty("os.name").startsWith("Windows")) {
            "gradlew.bat"
        } else {
            "gradlew"
        }
        return current.resolve(gradlew)
    }

    private fun findCompiledWasm(projectDir: Path): Path =
        Files.walk(projectDir.resolve("build")).use { paths ->
            paths
                .filter { path -> path.fileName.toString().endsWith(".wasm") }
                .filter { path -> path.toString().contains("productionExecutable") }
                .findFirst()
                .orElseThrow { AssertionError("Compiled Wasm output was not found.") }
        }

    private fun parseWasm(wasm: Path) =
        runCatching { Parser.parse(wasm) }.getOrElse {
            Parser.builder()
                .withValidation(false)
                .build()
                .parse { Files.newInputStream(wasm) }
        }

}

private class JsonStreamHost(private val bytes: ByteArray) {
    private var offset = 0
    var readCalls = 0
        private set
    var reportedPrimaryValue = -1L
        private set
    var reportedPublicCount = -1L
        private set
    var reportedElapsedNanos = -1L
        private set

    fun functions(): Array<HostFunction> =
        arrayOf(
            HostFunction(
                "bench",
                "read",
                FunctionType.of(listOf(ValType.I32, ValType.I32), listOf(ValType.I32)),
                WasmFunctionHandle { instance, args ->
                    readCalls += 1
                    if (offset >= bytes.size) {
                        longArrayOf(-1L)
                    } else {
                        val ptr = args[0].toInt()
                        val len = args[1].toInt()
                        val count = minOf(len, bytes.size - offset)
                        instance.memory().write(ptr, bytes, offset, count)
                        offset += count
                        longArrayOf(count.toLong())
                    }
                },
            ),
            HostFunction(
                "bench",
                "reset",
                FunctionType.empty(),
                WasmFunctionHandle { _, _ ->
                    offset = 0
                    readCalls = 0
                    reportedPrimaryValue = -1L
                    reportedPublicCount = -1L
                    reportedElapsedNanos = -1L
                    null
                },
            ),
            HostFunction(
                "bench",
                "now-nanos",
                FunctionType.of(emptyList(), listOf(ValType.I64)),
                WasmFunctionHandle { _, _ -> longArrayOf(System.nanoTime()) },
            ),
            HostFunction(
                "bench",
                "report",
                FunctionType.of(listOf(ValType.I32, ValType.I64), emptyList()),
                WasmFunctionHandle { _, args ->
                    when (args[0].toInt()) {
                        1 -> reportedPrimaryValue = args[1]
                        2 -> reportedPublicCount = args[1]
                        3 -> reportedElapsedNanos = args[1]
                    }
                    null
                },
            ),
        )
}

private data class JsonPayload(
    val bytes: ByteArray,
    val itemCount: Int,
    val publicCount: Int,
)

private fun generatedCatalogJsonPayload(targetBytes: Int): JsonPayload {
    val builder = StringBuilder(targetBytes + 1024)
    var itemCount = 0
    var publicCount = 0
    builder.append('[')
    while (builder.length < targetBytes) {
        if (itemCount > 0) builder.append(',')
        val public = itemCount % 5 != 0
        if (public) publicCount += 1
        builder.append(catalogItemJson(itemCount, if (public) "public" else "premium"))
        itemCount += 1
    }
    builder.append(']')
    return JsonPayload(
        bytes = builder.toString().toByteArray(StandardCharsets.UTF_8),
        itemCount = itemCount,
        publicCount = publicCount,
    )
}

private fun catalogItemJson(index: Int, visibility: String): String =
    """
    {"itemId":"entry-$index","name":"Synthetic Catalog Entry $index","summary":"Generated summary for catalog item $index","visibility":"$visibility","license":"standard","lengthSeconds":${1200 + index},"sourceLabel":"Synthetic Source","categories":["category-a","category-b","category-c"],"contributors":[{"contributorId":"contributor-${index % 97}","displayName":"Contributor ${index % 97}"},{"contributorId":"contributor-${index % 53}","displayName":"Alias ${index % 53}"}]}
    """.trimIndent()

private const val BenchmarkEnabledProperty = "krwa.jsonSequenceBenchmark"
private const val BenchmarkPayloadBytesProperty = "krwa.jsonSequenceBytes"
private const val ProfileOpcodesProperty = "krwa.jsonSequenceProfileOpcodes"
private const val DefaultBenchmarkPayloadBytes = 2 * 1024 * 1024
private class OpcodeProfiler {
    private var phase: String? = null
    private var counts = EnumMap<OpCode, Long>(OpCode::class.java)

    fun start(phase: String) {
        this.phase = phase
        counts = EnumMap(OpCode::class.java)
    }

    fun stop(): OpcodeProfile {
        val currentPhase = phase ?: return OpcodeProfile("", emptyList(), 0)
        phase = null
        val result = counts.entries
            .map { OpcodeCount(it.key, it.value) }
            .sortedByDescending { it.count }
        return OpcodeProfile(currentPhase, result, result.sumOf { it.count })
    }

    fun onExecution(instruction: uk.shusek.krwa.wasm.types.Instruction, stack: uk.shusek.krwa.runtime.MStack) {
        if (phase == null) return
        val opcode = instruction.opcode()
        counts[opcode] = (counts[opcode] ?: 0) + 1
    }
}

private data class OpcodeProfile(
    val phase: String,
    val counts: List<OpcodeCount>,
    val total: Long,
) {
    fun formatted(): String {
        if (phase.isEmpty() || total == 0L) return ""
        val top = counts.take(12).joinToString(", ") { "${it.opcode}=${it.count}" }
        return ", ${phase}Instructions=$total, ${phase}TopOpcodes=[$top]"
    }
}

private data class OpcodeCount(
    val opcode: OpCode,
    val count: Long,
)
