---
sidebar_position: 3
sidebar_label: WABT
title: WABT
---
# Tools

## WebAssembly Binary Toolkit

Since we use them in the build of the project, we publish a few [wabt](https://github.com/WebAssembly/wabt) tools compiled at build time with Kotlin Runtime Web Assembly.
Adding support for more tools is easy and we welcome contributions in this direction.
The relevant module can be added to the build with:

```xml
<dependency>
  <groupId>uk.shusek.krwa</groupId>
  <artifactId>wabt</artifactId>
  <version>latest-release</version>
</dependency>
```

## Wasm Tools

As we need it to catch up with the latest development of the Wasm spec we publish a few [wasm-tools](https://github.com/bytecodealliance/wasm-tools) compiled at build time with Kotlin Runtime Web Assembly.
The relevant module can be added to the build with:

```xml
<dependency>
  <groupId>uk.shusek.krwa</groupId>
  <artifactId>wasm-tools</artifactId>
  <version>latest-release</version>
</dependency>
```

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT
//DEPS uk.shusek.krwa:wabt:0.3.0-SNAPSHOT
//DEPS uk.shusek.krwa:wasm-tools:0.3.0-SNAPSHOT

import uk.shusek.krwa.wasm.Parser;
import uk.shusek.krwa.runtime.Instance;

System.setOut(new PrintStream(
  new BufferedOutputStream(
    new FileOutputStream("docs/advanced/tools.md.result"))));
```
-->

## wat2wasm

In Kotlin Runtime Web Assembly, we don't have a Wasm text format parser just yet.
To overcome this limitation you can use `wat2wasm`, for example:

```java
import uk.shusek.krwa.wabt.Wat2Wasm;
// or
import uk.shusek.krwa.tools.wasm.Wat2Wasm;

var wasm = Wat2Wasm.parse(
    "(module (func (export \"add\") (param $x i32) (param $y i32) (result i32)"
    + " (i32.add (local.get $x) (local.get $y))))");

var moduleInstance = Instance.builder(Parser.parse(wasm)).build();

var addFunction = moduleInstance.export("add");
var result = addFunction.apply(1, 41)[0];

System.out.println(result);
```
