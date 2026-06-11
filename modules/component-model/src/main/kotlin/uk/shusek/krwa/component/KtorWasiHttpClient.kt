package uk.shusek.krwa.component

import io.ktor.client.HttpClient as KtorHttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.RawSource

public class KtorWasiHttpClient(private val delegate: KtorHttpClient) : WasiHttpClient {
    override fun send(request: WasiHttpRequest): WasiHttpResponse = runBlocking {
        val response =
            delegate.request(request.uri) {
                method = KtorHttpMethod(request.method)
                val timeout = request.timeout
                if (timeout != null) {
                    timeout {
                        val millis = maxOf(1L, timeout.inWholeMilliseconds)
                        requestTimeoutMillis = millis
                        connectTimeoutMillis = millis
                        socketTimeoutMillis = millis
                    }
                }
                headers {
                    for (entry in request.headers) {
                        append(entry.name, entry.value)
                    }
                }
                setBody(request.body)
            }
        WasiHttpResponse(
            response.status.value,
            response.headers.entries().associate { entry -> entry.key to entry.value.toList() },
            KtorHttpBodySource(response.bodyAsChannel(), request.timeout),
        )
    }
}

private class KtorHttpBodySource(
    private val channel: ByteReadChannel,
    @Suppress("unused") private val timeout: Duration?,
) : RawSource {
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (byteCount <= 0L) {
            return 0L
        }
        if (channel.availableForRead == 0) {
            channel.closedCause?.let { throw it }
            return if (channel.isClosedForRead) -1L else 0L
        }
        val length = byteCount
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .coerceAtMost(channel.availableForRead.toLong())
            .toInt()
        val count = channel.readAvailable(1) { source ->
            source.readAtMostTo(sink, length.toLong()).toInt()
        }
        if (count < 0) {
            channel.closedCause?.let { throw it }
            return if (channel.isClosedForRead) -1L else 0L
        }
        if (count == 0) {
            return if (channel.isClosedForRead) -1L else 0L
        }
        return count.toLong()
    }

    override fun close() {
        channel.cancel()
    }
}
