package uk.shusek.krwa.component

import io.ktor.network.sockets.InetSocketAddress
import kotlinx.io.RawSink
import kotlinx.io.RawSource

internal interface WasiSocketRuntime {
    fun connectTcp(
        remoteAddress: InetSocketAddress,
        keepAlive: Boolean,
        receiveBufferSize: Int,
        sendBufferSize: Int,
    ): WasiTcpConnection

    fun listenTcp(localAddress: InetSocketAddress, backlogSize: Int): WasiTcpListener

    fun bindUdp(
        localAddress: InetSocketAddress,
        receiveBufferSize: Int,
        sendBufferSize: Int,
    ): WasiUdpEndpoint
}

internal expect fun defaultWasiSocketRuntime(): WasiSocketRuntime

internal interface WasiTcpListener {
    val localAddress: InetSocketAddress

    fun accept(timeoutMillis: Long): WasiTcpConnection?

    fun isOpen(): Boolean

    fun close()
}

internal interface WasiTcpConnection {
    val localAddress: InetSocketAddress
    val remoteAddress: InetSocketAddress

    fun isOpen(): Boolean

    fun send(data: ByteArray)

    fun read(max: Int, timeoutMillis: Long): WasiTcpReadChunk

    fun readUntilIdle(firstByteTimeoutMillis: Long, idleTimeoutMillis: Long): ByteArray

    fun inputSource(): RawSource

    fun inputAvailable(): Int

    fun outputSink(): RawSink

    fun shutdownInput()

    fun shutdownOutput()

    fun close()
}

internal data class WasiTcpReadChunk(val bytes: ByteArray, val closed: Boolean)

internal interface WasiUdpEndpoint {
    val localAddress: InetSocketAddress

    fun isOpen(): Boolean

    fun send(data: ByteArray, remoteAddress: InetSocketAddress)

    fun receive(timeoutMillis: Long): WasiDatagram?

    fun close()
}

internal data class WasiDatagram(val data: ByteArray, val remoteAddress: InetSocketAddress)
