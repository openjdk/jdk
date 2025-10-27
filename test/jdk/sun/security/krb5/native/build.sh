#!/bin/bash
# Build script for NativeCacheTest - compiles Java classes and native library

set -e

# Use jtreg environment variables when available, fallback to manual calculation
if [ -n "$TESTJAVA" ]; then
    # Running under jtreg
    BUILT_JDK="$TESTJAVA"
    JDK_ROOT="$(dirname $(dirname $TESTROOT))"
    LIB_DIR="$JDK_ROOT/test/lib"
    TEST_DIR="$TESTSRC"
else
    # Running manually
    TEST_DIR=$(pwd)
    JDK_ROOT="$(cd ../../../../../../ && pwd)"
    LIB_DIR="$JDK_ROOT/test/lib"
    BUILT_JDK="$JDK_ROOT/build/linux-x86_64-server-release/jdk"
fi

export JAVA_HOME="$BUILT_JDK"
export PATH="$BUILT_JDK/bin:$PATH"

# Module exports required for Kerberos internal APIs
if [ -n "$TESTCLASSPATH" ]; then
    # Use jtreg's prepared classpath
    JAVA_CP="$TESTCLASSPATH"
else
    # Manual execution classpath
    JAVA_CP="$LIB_DIR:../auto:."
fi
MODULE_EXPORTS="--add-exports java.security.jgss/sun.security.krb5=ALL-UNNAMED \
--add-exports java.security.jgss/sun.security.krb5.internal=ALL-UNNAMED \
--add-exports java.security.jgss/sun.security.krb5.internal.ccache=ALL-UNNAMED \
--add-exports java.security.jgss/sun.security.krb5.internal.crypto=ALL-UNNAMED \
--add-exports java.security.jgss/sun.security.krb5.internal.ktab=ALL-UNNAMED \
--add-exports java.security.jgss/sun.security.jgss.krb5=ALL-UNNAMED \
--add-exports java.base/sun.security.util=ALL-UNNAMED \
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED"

cd "$TEST_DIR"

# For jtreg, classes are already compiled by the harness
# For manual execution, compile what's needed
if [ -z "$TESTJAVA" ]; then
    # Manual execution - compile everything

    # Compile test library classes
    cd "$LIB_DIR"
    javac -cp . --add-exports java.base/jdk.internal.misc=ALL-UNNAMED jdk/test/lib/Platform.java 2>/dev/null || true

    # Compile OneKDC and test infrastructure  
    cd "$TEST_DIR/../auto"
    javac -cp "$LIB_DIR:." $MODULE_EXPORTS -XDignore.symbol.file \
        KDC.java OneKDC.java Context.java 2>/dev/null || true

    cd "$TEST_DIR"

    # Compile test classes
    javac -cp "$JAVA_CP" $MODULE_EXPORTS -XDignore.symbol.file \
        NativeCredentialCacheHelper.java NativeCacheTest.java
fi

# Generate JNI header (always needed for native compilation)
cd "$TEST_DIR"
if [ -n "$TESTCLASSPATH" ]; then
    javac -cp "$TESTCLASSPATH" -h . NativeCredentialCacheHelper.java 2>/dev/null || true
else
    javac -cp . -h . NativeCredentialCacheHelper.java 2>/dev/null || true
fi

# Check for krb5 development libraries
if ! pkg-config --exists krb5; then
    echo "Warning: krb5-devel not found - may need: sudo yum install krb5-devel"
fi

# Compile native library (work from test source directory)
cd "$TEST_DIR"
gcc -shared -fPIC \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    $(pkg-config --cflags krb5 2>/dev/null || echo "-I/usr/include") \
    -L/usr/lib64 -lkrb5 \
    -Wl,-rpath,/usr/lib64 \
    -o libnativecredentialcachehelper.so \
    NativeCredentialCacheHelper.c

echo "Build completed successfully"
