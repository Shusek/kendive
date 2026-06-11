@file:OptIn(kotlin.wasm.ExperimentalWasmInterop::class)

package uk.shusek.krwa.sample.guest

import kotlin.wasm.WasmExport
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlinx.coroutines.delay

private const val CLOCK_REALTIME = 0
private const val CLOCK_MONOTONIC = 1
private const val ERRNO_SUCCESS = 0
private const val PREOPEN_FD_START = 3
private const val PREOPEN_FD_END = 16
private const val OFLAGS_CREAT = 1
private const val OFLAGS_TRUNC = 8
private const val RIGHTS_FD_READ = 2L
private const val RIGHTS_FD_WRITE = 64L

suspend fun main(args: Array<String>) {
    println("Hello from Kotlin/WASI 2.4")
    println("args.main=${args.joinToString(",")}")
    println("args.wasi=${wasiArguments().joinToString(",")}")
    println("clock.realtime=${wasiClockTime(CLOCK_REALTIME) > 0L}")
    println("clock.monotonic=${wasiClockTime(CLOCK_MONOTONIC) > 0L}")
    println("coroutine.result=${runCoroutineProbe()}")
    println("env.KRWA_SAMPLE=${wasiEnvironmentValue("KRWA_SAMPLE")}")
    println("random.checksum=${wasiRandomChecksum()}")
    println("fs.roundtrip=${wasiFileRoundTrip()}")
    wasiWriteStderr("stderr.probe=ok\n")
}

@WasmExport("sample:kotlin-wasi/api#run")
fun runPreview1ComponentProbe(): Int {
    val env = wasiEnvironmentValue("KRWA_SAMPLE")
    val randomChecksum = wasiRandomChecksum()
    val file = wasiFileRoundTrip()
    wasiWriteStdout("component.stdout=ok\n")
    wasiWriteStderr("component.stderr=ok\n")
    return if (env == "component" && randomChecksum > 0 && file == "preview1-file-ok") 42 else 1
}

private suspend fun runCoroutineProbe(): Int {
    delay(1)
    return 42
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiArguments(): List<String> =
    withScopedMemoryAllocator { allocator ->
        val sizes = allocator.allocate(8).address.toInt()
        checkErrno(wasiArgsSizesGet(sizes, sizes + 4), "args_sizes_get")
        val count = loadI32(sizes)
        val bufferSize = loadI32(sizes + 4)
        val args = allocator.allocate(count * 4).address.toInt()
        val buffer = allocator.allocate(bufferSize).address.toInt()
        checkErrno(wasiArgsGet(args, buffer), "args_get")

        val result = ArrayList<String>()
        for (index in 0 until count) {
            val argPtr = loadI32(args + index * 4)
            result += loadUtf8Z(argPtr, buffer + bufferSize - argPtr)
        }
        result
    }

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiEnvironmentValue(name: String): String =
    withScopedMemoryAllocator { allocator ->
        val sizes = allocator.allocate(8).address.toInt()
        checkErrno(wasiEnvironSizesGet(sizes, sizes + 4), "environ_sizes_get")
        val count = loadI32(sizes)
        val bufferSize = loadI32(sizes + 4)
        val environ = allocator.allocate(count * 4).address.toInt()
        val buffer = allocator.allocate(bufferSize).address.toInt()
        checkErrno(wasiEnvironGet(environ, buffer), "environ_get")

        val prefix = "$name="
        var result = ""
        for (index in 0 until count) {
            val entryPtr = loadI32(environ + index * 4)
            val entry = loadUtf8Z(entryPtr, buffer + bufferSize - entryPtr)
            if (entry.startsWith(prefix)) {
                result = entry.substring(prefix.length)
                break
            }
        }
        result
    }

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiRandomChecksum(): Int =
    withScopedMemoryAllocator { allocator ->
        val data = allocator.allocate(8).address.toInt()
        checkErrno(wasiRandomGet(data, 8), "random_get")
        var checksum = 0
        for (index in 0 until 8) {
            checksum += loadByte(data + index).toInt() and 0xff
        }
        checksum
    }

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiFileRoundTrip(): String {
    val fd = wasiPreopenFd("/")
    val path = "krwa-wasi-probe.txt"
    val payload = "preview1-file-ok"
    wasiWriteFile(fd, path, payload)
    return wasiReadFile(fd, path, payload.length)
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiPreopenFd(path: String): Int =
    withScopedMemoryAllocator { allocator ->
        val prestat = allocator.allocate(8).address.toInt()
        var result = -1
        for (fd in PREOPEN_FD_START..PREOPEN_FD_END) {
            if (wasiFdPrestatGet(fd, prestat) == ERRNO_SUCCESS) {
                val nameLength = loadI32(prestat + 4)
                val namePtr = allocator.allocate(nameLength).address.toInt()
                checkErrno(wasiFdPrestatDirName(fd, namePtr, nameLength), "fd_prestat_dir_name")
                if (loadUtf8(namePtr, nameLength) == path) {
                    result = fd
                    break
                }
            }
        }
        check(result >= 0) { "preopen $path not found" }
        result
    }

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiWriteFile(dirFd: Int, path: String, text: String) {
    withScopedMemoryAllocator { allocator ->
        val pathBytes = path.encodeToByteArray()
        val data = text.encodeToByteArray()
        val pathPtr = allocator.allocate(pathBytes.size).address.toInt()
        val dataPtr = allocator.allocate(data.size).address.toInt()
        val fdPtr = allocator.allocate(4).address.toInt()
        val iov = allocator.allocate(8).address.toInt()
        val writtenPtr = allocator.allocate(4).address.toInt()

        storeBytes(pathPtr, pathBytes)
        storeBytes(dataPtr, data)
        checkErrno(
            wasiPathOpen(
                dirFd,
                0,
                pathPtr,
                pathBytes.size,
                OFLAGS_CREAT or OFLAGS_TRUNC,
                RIGHTS_FD_WRITE,
                RIGHTS_FD_WRITE,
                0,
                fdPtr,
            ),
            "path_open write",
        )
        val fd = loadI32(fdPtr)
        storeI32(iov, dataPtr)
        storeI32(iov + 4, data.size)
        checkErrno(wasiFdWrite(fd, iov, 1, writtenPtr), "fd_write file")
        check(loadI32(writtenPtr) == data.size) { "short file write" }
        checkErrno(wasiFdClose(fd), "fd_close write")
    }
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiReadFile(dirFd: Int, path: String, maxLength: Int): String =
    withScopedMemoryAllocator { allocator ->
        val pathBytes = path.encodeToByteArray()
        val pathPtr = allocator.allocate(pathBytes.size).address.toInt()
        val fdPtr = allocator.allocate(4).address.toInt()
        val dataPtr = allocator.allocate(maxLength).address.toInt()
        val iov = allocator.allocate(8).address.toInt()
        val readPtr = allocator.allocate(4).address.toInt()

        storeBytes(pathPtr, pathBytes)
        checkErrno(
            wasiPathOpen(
                dirFd,
                0,
                pathPtr,
                pathBytes.size,
                0,
                RIGHTS_FD_READ,
                RIGHTS_FD_READ,
                0,
                fdPtr,
            ),
            "path_open read",
        )
        val fd = loadI32(fdPtr)
        storeI32(iov, dataPtr)
        storeI32(iov + 4, maxLength)
        checkErrno(wasiFdRead(fd, iov, 1, readPtr), "fd_read file")
        val read = loadI32(readPtr)
        checkErrno(wasiFdClose(fd), "fd_close read")
        loadUtf8(dataPtr, read)
    }

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiWriteStderr(text: String) {
    wasiWriteFd(2, text)
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiWriteStdout(text: String) {
    wasiWriteFd(1, text)
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiWriteFd(fd: Int, text: String) {
    withScopedMemoryAllocator { allocator ->
        val bytes = text.encodeToByteArray()
        val dataPtr = allocator.allocate(bytes.size).address.toInt()
        val iov = allocator.allocate(8).address.toInt()
        val writtenPtr = allocator.allocate(4).address.toInt()
        storeBytes(dataPtr, bytes)
        storeI32(iov, dataPtr)
        storeI32(iov + 4, bytes.size)
        checkErrno(wasiFdWrite(fd, iov, 1, writtenPtr), "fd_write $fd")
    }
}

@WasmImport("wasi_snapshot_preview1", "clock_time_get")
private external fun wasiClockTimeGet(clockId: Int, precision: Long, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "args_sizes_get")
private external fun wasiArgsSizesGet(argc: Int, argvBufSize: Int): Int

@WasmImport("wasi_snapshot_preview1", "args_get")
private external fun wasiArgsGet(argv: Int, argvBuf: Int): Int

@WasmImport("wasi_snapshot_preview1", "environ_sizes_get")
private external fun wasiEnvironSizesGet(environCount: Int, environBufSize: Int): Int

@WasmImport("wasi_snapshot_preview1", "environ_get")
private external fun wasiEnvironGet(environ: Int, environBuf: Int): Int

@WasmImport("wasi_snapshot_preview1", "random_get")
private external fun wasiRandomGet(buf: Int, bufLen: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_prestat_get")
private external fun wasiFdPrestatGet(fd: Int, buf: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_prestat_dir_name")
private external fun wasiFdPrestatDirName(fd: Int, path: Int, pathLen: Int): Int

@WasmImport("wasi_snapshot_preview1", "path_open")
private external fun wasiPathOpen(
    dirFd: Int,
    lookupFlags: Int,
    path: Int,
    pathLen: Int,
    openFlags: Int,
    rightsBase: Long,
    rightsInheriting: Long,
    fdFlags: Int,
    retFd: Int,
): Int

@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun wasiFdWrite(fd: Int, iovs: Int, iovsLen: Int, nwritten: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasiFdRead(fd: Int, iovs: Int, iovsLen: Int, nread: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_close")
private external fun wasiFdClose(fd: Int): Int

@OptIn(UnsafeWasmMemoryApi::class)
private fun wasiClockTime(clockId: Int): Long =
    withScopedMemoryAllocator { allocator ->
        val result = allocator.allocate(8)
        val errno = wasiClockTimeGet(clockId, 1L, result.address.toInt())
        checkErrno(errno, "clock_time_get")
        Pointer(result.address.toInt().toUInt()).loadLong()
    }

private fun checkErrno(errno: Int, call: String) {
    check(errno == ERRNO_SUCCESS) { "$call failed with errno=$errno" }
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun loadByte(ptr: Int): Byte = Pointer(ptr.toUInt()).loadByte()

@OptIn(UnsafeWasmMemoryApi::class)
private fun loadI32(ptr: Int): Int = Pointer(ptr.toUInt()).loadInt()

@OptIn(UnsafeWasmMemoryApi::class)
private fun storeI32(ptr: Int, value: Int) {
    Pointer(ptr.toUInt()).storeInt(value)
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun storeBytes(ptr: Int, bytes: ByteArray) {
    for (index in bytes.indices) {
        Pointer((ptr + index).toUInt()).storeByte(bytes[index])
    }
}

private fun loadUtf8(ptr: Int, length: Int): String {
    val bytes = ByteArray(length)
    for (index in 0 until length) {
        bytes[index] = loadByte(ptr + index)
    }
    return bytes.decodeToString()
}

private fun loadUtf8Z(ptr: Int, maxLength: Int): String {
    var length = 0
    while (length < maxLength && loadByte(ptr + length) != 0.toByte()) {
        length++
    }
    return loadUtf8(ptr, length)
}

@WasmExport
fun endiveSampleMarker(): Int = 240
