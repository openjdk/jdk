# Welcome to the JDK!

For build instructions please see the
[online documentation](https://openjdk.org/groups/build/doc/building.html),
or either of these files:

- [doc/building.html](doc/building.html) (html version)
- [doc/building.md](doc/building.md) (markdown version)

See <https://openjdk.org/> for more information about the OpenJDK
Community and the JDK and see <https://bugs.openjdk.org> for JDK issue
tracking.

---
Foreign Function & Memory API

This repository contains changes which aim at improving the interoperability between the Java programming language and native libraries, which is one of the main goals of [Project Panama](https://openjdk.java.net/projects/panama/). This is done by introducing a new Java API, the Foreign Function & Memory API, which can be used to:

* interact with different kinds of memory resources, including so-called off-heap or native memory, as shown [here](doc/panama_memaccess.md);
* find native functions in a .dll/.so/.dylib and invoke them using method handles, as shown [here](doc/panama_ffi.md).

This API has been delivered, as incubating/preview APIs, in official JDK releases, see [JEP 412](https://openjdk.java.net/jeps/412), [JEP 419](https://openjdk.java.net/jeps/419) and [JEP 424](https://openjdk.java.net/jeps/424) for more details.

The Foreign Function & Memory API is best used in combination with a tool called `jextract`, which can be used to generate Java bindings to access functions and/or structs in a native library described by a given header file. The tool is available in a standalone [repository](https://github.com/openjdk/jextract) which contains several [examples](https://github.com/openjdk/jextract/tree/master/samples) which should help you getting started.

Early acccess (EA) binary snapshots of this repository can be found at: http://jdk.java.net/panama/
