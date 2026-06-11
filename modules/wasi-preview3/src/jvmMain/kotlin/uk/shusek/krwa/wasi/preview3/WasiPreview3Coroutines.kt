package uk.shusek.krwa.wasi.preview3

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.yield
import uk.shusek.krwa.component.ComponentModelException
import uk.shusek.krwa.component.WasiPreview3
import uk.shusek.krwa.component.WitFuture
import uk.shusek.krwa.component.WitStream

@Suppress("UNCHECKED_CAST")
public suspend fun <T> WasiPreview3.await(future: WitFuture<T>): T {
    while (!futureCompleted(future)) {
        yield()
    }
    return futureValue(future) as T
}

public fun <T> WasiPreview3.completed(value: T): WitFuture<T> = completedFutureOf(value)

public fun <T> WitFuture<T>.asDeferred(
    wasi: WasiPreview3,
    scope: CoroutineScope,
): Deferred<T> = scope.async { wasi.await(this@asDeferred) }

public suspend fun <T> Deferred<T>.toCompletedWitFuture(wasi: WasiPreview3): WitFuture<T> =
    wasi.completed(await())

@OptIn(ExperimentalUnsignedTypes::class)
public fun WitStream<UByte>.asByteArray(wasi: WasiPreview3): ByteArray =
    wasi.streamBytes(this)

@OptIn(ExperimentalUnsignedTypes::class)
public fun WitStream<UByte>.asByteFlow(wasi: WasiPreview3): Flow<UByte> =
    flow {
        for (byte in wasi.streamBytes(this@asByteFlow)) {
            emit(byte.toUByte())
        }
    }

@OptIn(ExperimentalUnsignedTypes::class)
public fun ByteArray.toWitByteStream(wasi: WasiPreview3): WitStream<UByte> =
    wasi.byteStream(this)

@OptIn(ExperimentalUnsignedTypes::class)
public suspend fun Flow<UByte>.toWitByteStream(wasi: WasiPreview3): WitStream<UByte> {
    val values = toList()
    val bytes = ByteArray(values.size)
    for (index in values.indices) {
        bytes[index] = values[index].toByte()
    }
    return wasi.byteStream(bytes)
}

public fun <T> Iterable<T>.toWitStream(wasi: WasiPreview3): WitStream<T> =
    wasi.streamOf(this)

public fun <T> WitStream<T>.asList(wasi: WasiPreview3): List<T> =
    wasi.streamValues(this)

public fun WitFuture<*>.requireCompleted(wasi: WasiPreview3) {
    if (!wasi.futureCompleted(this)) {
        throw ComponentModelException("WASI Preview 3 future $handle is not completed")
    }
}
