# CodeCacheFragmenter

This is a simple Java agent for fragmenting the HotSpot code cache. Its main purpose is to create and randomly free dummy code blobs to reach a specified fill percentage. It requires a JDK source tree and a boot JDK to build. Simply running `make` will produce the agent jar.

It produces `codecachefragmenter.jar`, which can be used as a Java agent like this:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -javaagent:codecachefragmenter.jar -Xbootclasspath/a:codecachefragmenter.jar YourMainClass
```

You can configure the agent using agent arguments:

```bash
java \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+WhiteBoxAPI \
     -javaagent:codecachefragmenter.jar=MinBlobSize=500,MaxBlobSize=10000,AvgBlobSize=2000,DivBlobSize=500,RequiredStableGcRounds=3,FillPercentage=50.0,RandomSeed=12345 \
     -Xbootclasspath/a:codecachefragmenter.jar \
     YourMainClass
```

Key parameters:

- **MinBlobSize**: minimum size of dummy code blobs in bytes (default: 500)
- **MaxBlobSize**: maximum size of dummy code blobs in bytes (default: 10000)
- **AvgBlobSize**: average size of blobs in bytes (default: 2000)
- **DivBlobSize**: standard deviation for blob sizes (default: 500)
- **RequiredStableGcRounds**: number of stable GC rounds before filling (default: 3)
- **FillPercentage**: target code cache fill percentage (0–100, default: 50.0)
- **RandomSeed**: seed for random generation (default: current time millis)

To build the agent:

```bash
make all
```

To clean build artifacts:

```bash
make clean
```

You can override paths using environment variables:

```bash
# JDK source tree
make ALT_JDK_SOURCE_PATH=/path/to/jdk/source all

# Boot JDK
make ALT_BOOTDIR=/path/to/jdk all
```

This tool is intended for testing and experimentation with HotSpot code cache fragmentation.
