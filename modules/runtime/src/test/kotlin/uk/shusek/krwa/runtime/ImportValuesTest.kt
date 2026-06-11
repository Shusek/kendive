package uk.shusek.krwa.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.shusek.krwa.wasm.types.FunctionType
import uk.shusek.krwa.wasm.types.Value

class ImportValuesTest {
    @Nested
    inner class Builder {
        @Test
        fun empty() {
            val result = ImportValues.builder().build()
            assertEquals(0, result.functionCount())
            assertEquals(0, result.globalCount())
            assertEquals(0, result.memoryCount())
            assertEquals(0, result.tableCount())
        }

        @Nested
        inner class Function {
            @Test
            fun withFunctions() {
                val result =
                    ImportValues.builder()
                        .withFunctions(
                            listOf(
                                HostFunction("module_1", "", FunctionType.empty(), null),
                                HostFunction("module_2", "", FunctionType.empty(), null),
                            )
                        )
                        .build()
                assertEquals(2, result.functionCount())
            }

            @Test
            fun addFunction() {
                val result =
                    ImportValues.builder()
                        .addFunction(HostFunction("module_1", "", FunctionType.empty(), null))
                        .addFunction(HostFunction("module_2", "", FunctionType.empty(), null))
                        .build()
                assertEquals(2, result.functionCount())
            }
        }

        @Nested
        inner class Global {
            @Test
            fun withGlobals() {
                val result =
                    ImportValues.builder()
                        .withGlobals(
                            listOf(
                                ImportGlobal(
                                    "spectest",
                                    "global_i32",
                                    GlobalInstance(Value.i32(666)),
                                ),
                                ImportGlobal(
                                    "spectest",
                                    "global_i64",
                                    GlobalInstance(Value.i64(666)),
                                ),
                            )
                        )
                        .build()
                assertEquals(2, result.globalCount())
            }

            @Test
            fun addGlobal() {
                val result =
                    ImportValues.builder()
                        .addGlobal(
                            ImportGlobal("spectest", "global_i32", GlobalInstance(Value.i32(666)))
                        )
                        .addGlobal(
                            ImportGlobal("spectest", "global_i64", GlobalInstance(Value.i64(666)))
                        )
                        .build()
                assertEquals(2, result.globalCount())
            }
        }

        @Nested
        inner class Memory {
            @Test
            fun withMemories() {
                val result =
                    ImportValues.builder()
                        .withMemories(
                            listOf(
                                ImportMemory("spectest", "memory", null),
                                ImportMemory("spectest", "memory_2", null),
                            )
                        )
                        .build()
                assertEquals(2, result.memoryCount())
            }

            @Test
            fun addMemory() {
                val result =
                    ImportValues.builder()
                        .addMemory(ImportMemory("spectest", "memory", null))
                        .addMemory(ImportMemory("spectest", "memory_2", null))
                        .build()
                assertEquals(2, result.memoryCount())
            }
        }

        @Nested
        inner class Table {
            @Test
            fun withTables() {
                val result =
                    ImportValues.builder()
                        .withTables(
                            listOf(
                                ImportTable("spectest", "table", emptyMap<Int, Int>()),
                                ImportTable("spectest", "table_2", emptyMap<Int, Int>()),
                            )
                        )
                        .build()
                assertEquals(2, result.tableCount())
            }

            @Test
            fun addMemory() {
                val result =
                    ImportValues.builder()
                        .addTable(ImportTable("spectest", "table", emptyMap<Int, Int>()))
                        .addTable(ImportTable("spectest", "table_2", emptyMap<Int, Int>()))
                        .build()
                assertEquals(2, result.tableCount())
            }
        }
    }
}
