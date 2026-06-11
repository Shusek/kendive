package docs

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FileOps {
    @JvmStatic
    @Throws(Exception::class)
    fun copyFromWasmCorpus(sourceName: String, destName: String) {
        val dest = File(".").toPath().resolve(destName)
        if (dest.toFile().exists()) {
            dest.toFile().delete()
        }
        Files.copy(
            File("..")
                .toPath()
                .resolve("testing")
                .resolve("wasm-corpus")
                .resolve("src")
                .resolve("main")
                .resolve("resources")
                .resolve("compiled")
                .resolve(sourceName),
            dest,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    @JvmStatic
    @Throws(Exception::class)
    fun writeResult(folder: String, name: String, content: String) {
        val dir = File(".").toPath().resolve(folder)
        dir.toFile().mkdirs()
        FileWriter(dir.resolve(name).toFile(), StandardCharsets.UTF_8).use { fileWriter ->
            PrintWriter(fileWriter).use { printWriter -> printWriter.print(content) }
        }
    }
}
