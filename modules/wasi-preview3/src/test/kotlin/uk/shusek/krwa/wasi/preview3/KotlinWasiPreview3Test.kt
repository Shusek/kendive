package uk.shusek.krwa.wasi.preview3

import java.nio.file.Files
import java.util.Comparator
import kotlin.time.Clock as KotlinClock
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant as KotlinInstant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import uk.shusek.krwa.component.KotlinWitBindings
import uk.shusek.krwa.component.WasiPreview3
import uk.shusek.krwa.component.WitPackage

class KotlinWasiPreview3Test {
    @Test
    fun awaitsCompletedWitFuture() = runBlocking {
        val runtime = KotlinWasiPreview3.builder().build()
        val future = runtime.completed("ready")

        assertEquals("ready", runtime.await(future))
        assertEquals("ready", runtime.wasi.await(future))
    }

    @Test
    fun exposesWitFutureAsDeferred() = runBlocking {
        val runtime = KotlinWasiPreview3.builder().build()
        val future = runtime.completed(42)

        assertEquals(42, future.asDeferred(runtime.wasi, this).await())
    }

    @Test
    fun convertsDeferredToCompletedWitFuture() = runBlocking {
        val runtime = KotlinWasiPreview3.builder().build()
        val deferred = CompletableDeferred("from-deferred")

        val future = deferred.toCompletedWitFuture(runtime.wasi)

        assertEquals("from-deferred", runtime.await(future))
    }

    @Test
    fun exposesByteStreamsAsArraysAndFlows() = runBlocking {
        val runtime = KotlinWasiPreview3.builder().build()
        val bytes = byteArrayOf(1, 2, 3)
        val stream = bytes.toWitByteStream(runtime.wasi)

        assertArrayEquals(bytes, stream.asByteArray(runtime.wasi))
        assertEquals(
            listOf(1u.toUByte(), 2u.toUByte(), 3u.toUByte()),
            stream.asByteFlow(runtime.wasi).toList(),
        )

        val fromFlow = flowOf(4u.toUByte(), 5u.toUByte()).toWitByteStream(runtime.wasi)
        assertArrayEquals(byteArrayOf(4, 5), fromFlow.asByteArray(runtime.wasi))
    }

    @Test
    fun exposesTypedListStreams() {
        val runtime = KotlinWasiPreview3.builder().build()
        val stream = listOf("alpha", "beta").toWitStream(runtime.wasi)

        assertEquals(listOf("alpha", "beta"), stream.asList(runtime.wasi))
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun acceptsKotlinFirstClockRandomAndPathConfiguration() {
        val root = Files.createTempDirectory("krwa-wasi-preview3-kotlin-builder")
        try {
            val runtime =
                KotlinWasiPreview3.builder()
                    .withWallClock(
                        object : KotlinClock {
                            override fun now(): KotlinInstant =
                                KotlinInstant.fromEpochSeconds(1_700_000_000L)
                        },
                        resolution = 123.nanoseconds,
                    )
                    .withWallClockResolution(124.nanoseconds)
                    .withMonotonicClock { 1_000_000L.nanoseconds }
                    .withMonotonicResolution(456.nanoseconds)
                    .withSecureRandom(kotlin.random.Random(7L))
                    .withInsecureRandom(kotlin.random.Random(8L))
                    .withInsecureSeed(11uL, 12uL)
                    .withPreopenedDirectory("/", root.toString())
                    .build()

            assertEquals(WasiPreview3.DEFAULT_VERSION, runtime.version)
            assertTrue(runtime.fileSystem().writable)
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun exposesFirstPartyFileSystemFacade() = runBlocking {
        val root = Files.createTempDirectory("krwa-wasi-preview3-fs")
        try {
            val runtime =
                KotlinWasiPreview3.builder().withPreopenedDirectory("/", root.toString()).build()
            val fs = runtime.fileSystem("/")

            fs.writeText("dir/hello.txt", "hello")
            fs.appendText("/dir/hello.txt", " world")

            assertEquals("hello world", fs.readText("dir/hello.txt"))
            assertTrue(fs.exists("dir/hello.txt"))
            assertTrue(fs.metadata("dir/hello.txt").isRegularFile)
            assertEquals(listOf("hello.txt"), fs.list("dir").map { it.name })

            val chunks = fs.readByteChunks("dir/hello.txt", chunkSize = 5).toList()
            assertEquals(listOf("hello", " worl", "d"), chunks.map { it.decodeToString() })

            fs.writeByteChunks("dir/chunks.txt", flowOf("a".toByteArray(), "b".toByteArray()))
            assertEquals("ab", fs.readText("dir/chunks.txt"))

            val stream = fs.readWitByteStream("dir/hello.txt", runtime.wasi)
            fs.writeWitByteStream("dir/copy.txt", stream, runtime.wasi)
            assertEquals("hello world", fs.readText("dir/copy.txt"))
        } finally {
            Files.walk(root).use { walk ->
                walk.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun fileSystemFacadeEnforcesPreopenBoundaryAndReadonlyMode() {
        val root = Files.createTempDirectory("krwa-wasi-preview3-fs-guard")
        try {
            val runtime =
                KotlinWasiPreview3.builder()
                    .withReadOnlyPreopenedDirectory("/", root.toString())
                    .build()
            val fs = runtime.fileSystem()

            assertFalse(fs.exists("missing.txt"))
            assertThrows(IllegalArgumentException::class.java) { fs.readBytes("../outside.txt") }
            assertThrows(IllegalArgumentException::class.java) {
                fs.writeText("blocked.txt", "blocked")
            }
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun generatedBindingsCanTargetFirstPartyRuntimePackage() {
        val witPackage =
            WitPackage.parse(
                """
                package sample:first-party;

                interface api {
                  run: func(input: future<string>, body: stream<u8>) -> future<u32>;
                }

                world plugin {
                  export api;
                }
                """
                    .trimIndent()
            )

        val generated =
            KotlinWitBindings.builder(witPackage)
                .withPackageName("sample.generated")
                .withRuntimePackageName("uk.shusek.krwa.wasi.preview3")
                .build()
                .generate()

        assertTrue(generated.contains("import uk.shusek.krwa.wasi.preview3.WitFuture"))
        assertTrue(generated.contains("import uk.shusek.krwa.wasi.preview3.WitStream"))
        assertTrue(
            generated.contains(
                "fun run(input: WitFuture<String>, body: WitStream<UByte>): WitFuture<UInt>"
            )
        )
    }
}
