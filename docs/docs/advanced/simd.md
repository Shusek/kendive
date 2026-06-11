---
sidebar_position: 2
sidebar_label: Simd
title: Simd support
---

> **NOTE:** SIMD support is available only for Java 21+ and interpreter mode

If you are using a version of Java that supports [JEP 448 - Vector API](https://openjdk.org/jeps/448) you can leverage [Vector instructions](https://webassembly.github.io/spec/core/syntax/instructions.html#vector-instructions).

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT
//DEPS uk.shusek.krwa:simd:0.3.0-SNAPSHOT

import uk.shusek.krwa.wasm.Parser;
import uk.shusek.krwa.runtime.Instance;

docs.FileOps.copyFromWasmCorpus("count_vowels.rs.wasm", "your.wasm");
```
-->

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT

```
-->

After adding the dependency:

```xml
<dependency>
  <groupId>uk.shusek.krwa</groupId>
  <artifactId>simd</artifactId>
</dependency>
```

You can instantiate a module with SIMD support by explicitly providing a `MachineFactory`:

```java
import uk.shusek.krwa.simd.SimdInterpreterMachine;

var module = Parser.parse(new File("your.wasm"));
var instance = Instance.builder(module).withMachineFactory(SimdInterpreterMachine::new).build();
```

> **_NOTE:_**  SIMD support **REQUIRES** validation. Disabling validation  (`WasmModule.builder().withValidation(false)`) is likely to produce incorrect results.

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT

docs.FileOps.writeResult("docs/advanced", "simd.md.result", "empty");
```
-->

