package uk.shusek.krwa.runtime

import uk.shusek.krwa.wasm.types.MemoryLimits

internal expect object RuntimePlatform {
    fun defaultMemoryFactory(): (MemoryLimits) -> Memory

    fun defaultMachineFactory(): (Instance) -> Machine

    fun <T> runCatchingStackOverflow(block: () -> T): T
}
