package uk.shusek.krwa.wasi.preview3

import uk.shusek.krwa.component.WasiPreview3
import uk.shusek.krwa.component.WitStream

@OptIn(ExperimentalUnsignedTypes::class)
public fun WasiFileSystem.readWitByteStream(
    path: String,
    wasi: WasiPreview3,
): WitStream<UByte> = readBytes(path).toWitByteStream(wasi)

@OptIn(ExperimentalUnsignedTypes::class)
public fun WasiFileSystem.writeWitByteStream(
    path: String,
    stream: WitStream<UByte>,
    wasi: WasiPreview3,
    createParentDirectories: Boolean = true,
) {
    writeBytes(path, stream.asByteArray(wasi), createParentDirectories)
}
