# Classfile Processing API for JDK

Provide an API for parsing, generating, and transforming Java class files. This will initially serve as an internal replacement for ASM in the JDK, to be later opened as a public API.

See [JEP](https://bugs.openjdk.java.net/browse/JDK-8280389)
or [online API documentation](https://htmlpreview.github.io/?https://raw.githubusercontent.com/openjdk/jdk-sandbox/classfile-api-javadoc-branch/doc/classfile-api/javadoc/jdk/classfile/package-summary.html)
for more information about Classfile Processing API.

See <https://openjdk.org/> for more information about the OpenJDK
Community and the JDK and see <https://bugs.openjdk.org> for JDK issue
tracking.

### Sources

Classfile Processing API source are a part of java.base JDK module sources:

- [src/java.base/share/classes/jdk/classfile/](src/java.base/share/classes/jdk/classfile/)

### Building

For build instructions please see the
[online documentation](https://openjdk.org/groups/build/doc/building.html),
or either of these files:

- [doc/building.html](doc/building.html) (html version)
- [doc/building.md](doc/building.md) (markdown version)

### Testing

Classfile Processing API tests are a part of JDK tests:

- [test/jdk/jdk/classfile/](test/jdk/jdk/classfile/)

Test can be selectivelly executed as:

    make test TEST=jdk/classfile

See [online JCov report](https://htmlpreview.github.io/?https://raw.githubusercontent.com/openjdk/jdk-sandbox/classfile-api-javadoc-branch/jcov-report/jdk/classfile/package-summary.html) for more information about Classfile API tests coverage
and [doc/testing.md](doc/testing.md) for more information about JDK testing.

### Benchmarking

Classfile Processing API benchmarks are a part of JDK Microbenchmark Suite:

- [test/micro/org/openjdk/bench/jdk/classfile/](test/micro/org/openjdk/bench/jdk/classfile/)

Benchmarks can be selectively executed as:

    make test TEST=micro:org.openjdk.bench.jdk.classfile.+

See [JEP 230: Microbenchmark Suite](https://bugs.openjdk.java.net/browse/JDK-8050952) for more information about JDK benchmarks.

### Use Cases

See our [development branch](https://github.com/openjdk/jdk-sandbox/tree/classfile-api-dev-branch#use-cases) for actual JDK use cases.
