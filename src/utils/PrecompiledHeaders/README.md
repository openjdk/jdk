# Hotspot precompiled headers

This directory contains a simple tool to refresh the current set of precompiled headers
in `src/hotspot`. The headers are selected according to how frequently they are included
in Hotspot source code.

## Usage

The script requires two parameters:
- The minimum inclusion count a header must reach in order to be precompiled;
- The path to a build, which will be used to extract dependency information for each Hotspot
    source file.
Optionally, the root path to the JDK project can be specified as the second parameter.

```bash
$ javac src/utils/PrecompiledHeaders/PrecompiledHeaders.java
$ java -cp src/utils/PrecompiledHeaders PrecompiledHeaders min_inclusion_count build_root [jdk_root=.]
```

The script will write to `src/hotspot/share/precompiled/precompiled.hpp` the new set of
headers selected to be precompiled.

## Related tickets

- [JDK-8213339](https://bugs.openjdk.org/browse/JDK-8213339)
- [JDK-8365053](https://bugs.openjdk.org/browse/JDK-8365053)
