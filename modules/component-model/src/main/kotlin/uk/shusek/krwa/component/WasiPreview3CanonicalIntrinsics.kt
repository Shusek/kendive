package uk.shusek.krwa.component

public interface WasiPreview3CanonicalIntrinsics {
    public fun completedFutureHandle(value: Any?): Long =
        throw ComponentModelException("canonical future intrinsics cannot create completed futures")

    public fun futureNew(): Long

    public fun futureRead(
        context: WasiPreview3CanonicalContext,
        futureHandle: Long,
        ptr: Int,
        payloadType: WitPackage.TypeRef,
    ): Long

    public fun futureWrite(
        context: WasiPreview3CanonicalContext,
        futureHandle: Long,
        ptr: Int,
        payloadType: WitPackage.TypeRef,
    ): Long

    public fun futureCancelRead(futureHandle: Long): Long

    public fun futureCancelWrite(futureHandle: Long): Long

    public fun futureDropReadable(futureHandle: Long)

    public fun futureDropWritable(futureHandle: Long)

    public fun streamNew(payloadType: WitPackage.TypeRef): Long

    public fun streamRead(
        context: WasiPreview3CanonicalContext,
        streamHandle: Long,
        ptr: Int,
        len: Int,
        payloadType: WitPackage.TypeRef,
    ): Long

    public fun streamWrite(
        context: WasiPreview3CanonicalContext,
        streamHandle: Long,
        ptr: Int,
        len: Int,
        payloadType: WitPackage.TypeRef,
    ): Long

    public fun streamCancelRead(streamHandle: Long): Long

    public fun streamCancelWrite(streamHandle: Long): Long

    public fun streamDropReadable(streamHandle: Long)

    public fun streamDropWritable(streamHandle: Long)
}

public interface WasiPreview3CanonicalContext {
    public fun writeMemory(ptr: Int, bytes: ByteArray)

    public fun readMemory(ptr: Int, len: Int): ByteArray

    public fun storeListElements(ptr: Int, payloadType: WitPackage.TypeRef, values: List<Any?>)

    public fun loadListElements(ptr: Int, len: Int, payloadType: WitPackage.TypeRef): List<Any?>

    public fun storeFutureValue(ptr: Int, payloadType: WitPackage.TypeRef, value: Any?)

    public fun loadFutureValue(ptr: Int, payloadType: WitPackage.TypeRef): Any?
}
