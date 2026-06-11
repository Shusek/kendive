@file:OptIn(
    kotlin.wasm.ExperimentalWasmInterop::class,
    kotlin.wasm.unsafe.UnsafeWasmMemoryApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)

import kotlin.wasm.WasmExport
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.buffered
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeSourceToSequence

@Serializable
data class CatalogItem(
    val itemId: String,
    val name: String,
    val summary: String = "",
    val visibility: String,
    val license: String = "",
    val lengthSeconds: Long? = null,
    val sourceLabel: String? = null,
    val categories: List<String?> = emptyList(),
    val contributors: List<Contributor> = emptyList(),
)

@Serializable
data class Contributor(
    val contributorId: String,
    val displayName: String,
)

@WasmImport("bench", "read")
external fun benchRead(ptr: Int, len: Int): Int

@WasmImport("bench", "reset")
external fun benchReset()

@WasmImport("bench", "now-nanos")
external fun benchNowNanos(): Long

@WasmImport("bench", "report")
external fun benchReport(stage: Int, value: Long)

fun main() {}

@WasmExport("run_drain_source")
fun runDrainSource(): Int {
    benchReset()
    val started = benchNowNanos()
    val source = HostJsonSource()
    val scratch = Buffer()
    var totalBytes = 0L
    while (true) {
        val read = source.readAtMostTo(scratch, 8192)
        if (read == -1L) {
            break
        }
        totalBytes += read
        scratch.clear()
    }
    benchReport(1, totalBytes)
    benchReport(3, benchNowNanos() - started)
    return totalBytes.toInt()
}

@WasmExport("run_decode_filter")
fun runDecodeFilter(): Int {
    benchReset()
    val started = benchNowNanos()
    var totalCount = 0
    var publicCount = 0
    val items = Json.decodeSourceToSequence(
        HostJsonSource().buffered(),
        CatalogItem.serializer(),
        DecodeSequenceMode.ARRAY_WRAPPED,
    )
    for (item in items) {
        totalCount += 1
        if (item.visibility.equals("public", ignoreCase = true)) {
            publicCount += 1
        }
    }
    benchReport(1, totalCount.toLong())
    benchReport(2, publicCount.toLong())
    benchReport(3, benchNowNanos() - started)
    return publicCount
}

class HostJsonSource : RawSource {
    private var closed = false

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (closed) return -1L
        if (byteCount <= 0L) return 0L
        val requested = byteCount.coerceAtMost(GuestReadChunkSize.toLong()).toInt()
        val read = withScopedMemoryAllocator { allocator ->
            val address = allocator.allocate(requested).address.toInt()
            val count = benchRead(address, requested)
            if (count > 0) {
                sink.write(ByteArray(count) { index -> readByte(address + index) })
            }
            count
        }
        if (read <= 0) {
            closed = true
            return -1L
        }
        return read.toLong()
    }

    override fun close() {
        closed = true
    }
}

fun readByte(address: Int): Byte =
    Pointer(address.toUInt()).loadByte()

private const val GuestReadChunkSize = 256 * 1024
