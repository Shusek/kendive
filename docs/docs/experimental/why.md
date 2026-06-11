---
sidebar_position: 1
sidebar_label: Why
title: Why
---
# Why?

Kotlin Runtime Web Assembly has a stable core goal, but several surfaces are
still evolving while the Kotlin Multiplatform, WASI, and Component Model APIs
settle.

Experimental modules are published so early users can test new capabilities
without waiting for a full release cycle. Feedback from those integrations is
used to adjust package names, artifact names, APIs, and runtime behavior before
the modules are promoted.

If a feature is useful in real projects, the preferred path is to keep it and
provide a migration path. Experimental APIs, however, are not covered by the
same SemVer guarantees as stable modules.

This means an experimental module can still rename artifact IDs, classes, and
methods, or rework its usage model, when that leads to a better stable API.

<!--
```java
//DEPS uk.shusek.krwa:docs-lib:0.3.0-SNAPSHOT

docs.FileOps.writeResult("docs/experimental", "why.md.result", "empty");
```
-->
