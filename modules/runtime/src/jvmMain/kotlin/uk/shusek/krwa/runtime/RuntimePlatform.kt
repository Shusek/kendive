package uk.shusek.krwa.runtime

import uk.shusek.krwa.wasm.WasmEngineException
import uk.shusek.krwa.wasm.types.MemoryLimits

internal actual object RuntimePlatform {
    actual fun defaultMemoryFactory(): (MemoryLimits) -> Memory = { limits -> ByteBufferMemory(limits) }

    actual fun defaultMachineFactory(): (Instance) -> Machine =
        RuntimeDefaults.defaultMachineFactory { Thread.currentThread().isInterrupted }

    actual fun <T> runCatchingStackOverflow(block: () -> T): T =
        try {
            block()
        } catch (e: StackOverflowError) {
            throw WasmEngineException("call stack exhausted", e)
        }
}
