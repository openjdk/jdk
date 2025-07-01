/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.bench.jdk.internal.jrtfs;

import jdk.internal.jimage.ImageReader;
import jdk.internal.jimage.ImageReader.Node;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/// Benchmarks for ImageReader. See individual benchmarks for details on what they
/// measure, and their potential applicability for real world conclusions.
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgs = {"--add-exports", "java.base/jdk.internal.jimage=ALL-UNNAMED"})
public class ImageReaderBenchmark {

    private static final Path SYSTEM_IMAGE_FILE = Path.of(System.getProperty("java.home"), "lib", "modules");
    static {
        if (!Files.exists(SYSTEM_IMAGE_FILE)) {
            throw new IllegalStateException("Cannot locate jimage file for benchmark: " + SYSTEM_IMAGE_FILE);
        }
    }

    /// NOT annotated with `@State` since it needs to potentially be used as a
    /// per-benchmark or a per-iteration state object. The subclasses provide
    /// any lifetime annotations that are needed.
    static class BaseState {
        protected Path copiedImageFile;
        protected ByteOrder byteOrder;
        long count = 0;

        public void setUp() throws IOException {
            copiedImageFile = Files.createTempFile("copied_jimage", "");
            byteOrder = ByteOrder.nativeOrder();
            Files.copy(SYSTEM_IMAGE_FILE, copiedImageFile, REPLACE_EXISTING);
        }

        public void tearDown() throws IOException {
            Files.deleteIfExists(copiedImageFile);
            System.err.println("Result: " + count);
        }
    }

    @State(Scope.Benchmark)
    public static class WarmStartWithImageReader extends BaseState {
        ImageReader reader;

        @Setup(Level.Trial)
        public void setUp() throws IOException {
            super.setUp();
            reader = ImageReader.open(copiedImageFile, byteOrder);
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            super.tearDown();
        }
    }

    @State(Scope.Benchmark)
    public static class ColdStart extends BaseState {
        @Setup(Level.Iteration)
        public void setUp() throws IOException {
            super.setUp();
        }

        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            super.tearDown();
        }
    }

    @State(Scope.Benchmark)
    public static class ColdStartWithImageReader extends BaseState {
        ImageReader reader;

        @Setup(Level.Iteration)
        public void setup() throws IOException {
            super.setUp();
            reader = ImageReader.open(copiedImageFile, byteOrder);
        }

        @TearDown(Level.Iteration)
        public void tearDown() throws IOException {
            reader.close();
            super.tearDown();
        }
    }

    /// Benchmarks counting of all nodes in the system image *after* they have all
    /// been visited at least once. Image nodes should be cached after first use,
    /// so this benchmark should be fast and very stable.
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void warmCache_CountAllNodes(WarmStartWithImageReader state) throws IOException {
        state.count = countAllNodes(state.reader, state.reader.findNode("/"));
    }

    /// Benchmarks counting of all nodes in the system image from a "cold start". This
    /// visits all nodes in depth-first order and counts them.
    ///
    /// This benchmark is not representative of any typical usage pattern, but can be
    /// used for comparisons between versions of `ImageReader`.
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public void coldStart_InitAndCount(ColdStart state) throws IOException {
        try (var reader = ImageReader.open(state.copiedImageFile, state.byteOrder)) {
            state.count = countAllNodes(reader, reader.findNode("/"));
        }
    }

    /// As above, but includes the time to initialize the `ImageReader`.
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public void coldStart_CountOnly(ColdStartWithImageReader state) throws IOException {
        state.count = countAllNodes(state.reader, state.reader.findNode("/"));
    }

    /// Benchmarks the time taken to load the byte array contents of classes
    /// representative of those loaded by javac to for the simplest `HelloWorld`
    /// program.
    ///
    /// This benchmark is somewhat representative of the cost of class loading
    /// during javac startup. It is useful for comparisons between versions of
    /// `ImageReader`, but also to estimate a lower bound for any reduction or
    /// increase in the real-world startup time of javac.
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public void coldStart_LoadJavacInitClasses(Blackhole bh, ColdStart state) throws IOException {
        int errors = 0;
        try (var reader = ImageReader.open(state.copiedImageFile, state.byteOrder)) {
            for (String path : INIT_CLASSES) {
                // Path determination isn't perfect so there can be a few "misses" in here.
                // Report the count of bad paths as the "result", which should be < 20 or so.
                Node node = reader.findNode(path);
                if (node != null) {
                    bh.consume(reader.getResource(node));
                } else {
                    errors += 1;
                }
            }
        }
        state.count = INIT_CLASSES.size();
        // Allow up to 2% missing classes before complaining.
        if ((100 * errors) / INIT_CLASSES.size() >= 2) {
            reportMissingClassesAndFail(state, errors);
        }
    }

    static long countAllNodes(ImageReader reader, Node node) {
        long count = 1;
        if (node.isDirectory()) {
            count += node.getChildren().stream().mapToLong(n -> {
                try {
                    return countAllNodes(reader, reader.findNode(n.getName()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).sum();
        }
        return count;
    }

    // Run if the INIT_CLASSES list below is sufficiently out-of-date.
    // DO NOT run this before the benchmark, as it will cache all the nodes!
    private static void reportMissingClassesAndFail(ColdStart state, int errors) throws IOException {
        List<String> missing = new ArrayList<>(errors);
        try (var reader = ImageReader.open(state.copiedImageFile, state.byteOrder)) {
            for (String path : INIT_CLASSES) {
                if (reader.findNode(path) == null) {
                    missing.add(path);
                }
            }
        }
        throw new IllegalStateException(
                String.format(
                        "Too many missing classes (%d of %d) in the hardcoded benchmark list.\n" +
                                "Please regenerate it according to instructions in the source code.\n" +
                                "Missing classes:\n\t%s",
                        errors, INIT_CLASSES.size(), String.join("\n\t", missing)));
    }

    // Note: This list is inherently a little fragile and may end up being more
    // trouble than it's worth to maintain. If it turns out that it needs to be
    // regenerated often when this benchmark is run, then a new approach should
    // be considered, such as:
    // * Limit the list of classes to non-internal ones.
    // * Calculate the list dynamically based on the running JVM.
    //
    // Created by running "java -verbose:class", throwing away anonymous inner
    // classes and anything without a reliable name, and grouping by the stated
    // source. It's not perfect, but it's representative.
    //
    // <jdk_root>/bin/java -verbose:class HelloWorld 2>&1 \
    //   | fgrep '[class,load]' | cut -d' ' -f2 \
    //   | tr '.' '/' \
    //   | egrep -v '\$[0-9$]' \
    //   | fgrep -v 'HelloWorld' \
    //   | fgrep -v '/META-INF/preview/' \
    //   | while read f ; do echo "${f}.class" ; done \
    //   > initclasses.txt
    //
    // Output:
    //    java/lang/Object.class
    //    java/io/Serializable.class
    //    ...
    //
    // jimage list <jdk_root>/images/jdk/lib/modules \
    //     | awk '/^Module: */ { MOD=$2 }; /^    */ { print "/modules/"MOD"/"$1 }' \
    //     > fullpaths.txt
    //
    // Output:
    //     ...
    //     /modules/java.base/java/lang/Object.class
    //     /modules/java.base/java/lang/OutOfMemoryError.class
    //     ...
    //
    // while read c ; do grep "/$c" fullpaths.txt ; done < initclasses.txt \
    //     | while read c ; do printf '    "%s",\n' "$c" ; done \
    //     > initpaths.txt
    //
    // Output:
    private static final Set<String> INIT_CLASSES = Set.of(
            "/modules/java.base/java/lang/Object.class",
            "/modules/java.base/java/io/Serializable.class",
            "/modules/java.base/java/lang/Comparable.class",
            "/modules/java.base/java/lang/CharSequence.class",
            "/modules/java.base/java/lang/constant/Constable.class",
            "/modules/java.base/java/lang/constant/ConstantDesc.class",
            "/modules/java.base/java/lang/String.class",
            "/modules/java.base/java/lang/reflect/AnnotatedElement.class",
            "/modules/java.base/java/lang/reflect/GenericDeclaration.class",
            "/modules/java.base/java/lang/reflect/Type.class",
            "/modules/java.base/java/lang/invoke/TypeDescriptor.class",
            "/modules/java.base/java/lang/invoke/TypeDescriptor$OfField.class",
            "/modules/java.base/java/lang/Class.class",
            "/modules/java.base/java/lang/Cloneable.class",
            "/modules/java.base/java/lang/ClassLoader.class",
            "/modules/java.base/java/lang/System.class",
            "/modules/java.base/java/lang/Throwable.class",
            "/modules/java.base/java/lang/Error.class",
            "/modules/java.base/java/lang/Exception.class",
            "/modules/java.base/java/lang/RuntimeException.class",
            "/modules/java.base/java/security/ProtectionDomain.class",
            "/modules/java.base/java/security/SecureClassLoader.class",
            "/modules/java.base/java/lang/ReflectiveOperationException.class",
            "/modules/java.base/java/lang/ClassNotFoundException.class",
            "/modules/java.base/java/lang/Record.class",
            "/modules/java.base/java/lang/LinkageError.class",
            "/modules/java.base/java/lang/NoClassDefFoundError.class",
            "/modules/java.base/java/lang/ClassCastException.class",
            "/modules/java.base/java/lang/ArrayStoreException.class",
            "/modules/java.base/java/lang/VirtualMachineError.class",
            "/modules/java.base/java/lang/InternalError.class",
            "/modules/java.base/java/lang/OutOfMemoryError.class",
            "/modules/java.base/java/lang/StackOverflowError.class",
            "/modules/java.base/java/lang/IllegalMonitorStateException.class",
            "/modules/java.base/java/lang/ref/Reference.class",
            "/modules/java.base/java/lang/IllegalCallerException.class",
            "/modules/java.base/java/lang/ref/SoftReference.class",
            "/modules/java.base/java/lang/ref/WeakReference.class",
            "/modules/java.base/java/lang/ref/FinalReference.class",
            "/modules/java.base/java/lang/ref/PhantomReference.class",
            "/modules/java.base/java/lang/ref/Finalizer.class",
            "/modules/java.base/java/lang/Runnable.class",
            "/modules/java.base/java/lang/Thread.class",
            "/modules/java.base/java/lang/Thread$FieldHolder.class",
            "/modules/java.base/java/lang/Thread$Constants.class",
            "/modules/java.base/java/lang/Thread$UncaughtExceptionHandler.class",
            "/modules/java.base/java/lang/ThreadGroup.class",
            "/modules/java.base/java/lang/BaseVirtualThread.class",
            "/modules/java.base/java/lang/VirtualThread.class",
            "/modules/java.base/java/lang/ThreadBuilders$BoundVirtualThread.class",
            "/modules/java.base/java/util/Map.class",
            "/modules/java.base/java/util/Dictionary.class",
            "/modules/java.base/java/util/Hashtable.class",
            "/modules/java.base/java/util/Properties.class",
            "/modules/java.base/java/lang/Module.class",
            "/modules/java.base/java/lang/reflect/AccessibleObject.class",
            "/modules/java.base/java/lang/reflect/Member.class",
            "/modules/java.base/java/lang/reflect/Field.class",
            "/modules/java.base/java/lang/reflect/Parameter.class",
            "/modules/java.base/java/lang/reflect/Executable.class",
            "/modules/java.base/java/lang/reflect/Method.class",
            "/modules/java.base/java/lang/reflect/Constructor.class",
            "/modules/java.base/jdk/internal/vm/ContinuationScope.class",
            "/modules/java.base/jdk/internal/vm/Continuation.class",
            "/modules/java.base/jdk/internal/vm/StackChunk.class",
            "/modules/java.base/jdk/internal/reflect/MethodAccessor.class",
            "/modules/java.base/jdk/internal/reflect/MethodAccessorImpl.class",
            "/modules/java.base/jdk/internal/reflect/ConstantPool.class",
            "/modules/java.base/java/lang/annotation/Annotation.class",
            "/modules/java.base/jdk/internal/reflect/CallerSensitive.class",
            "/modules/java.base/jdk/internal/reflect/ConstructorAccessor.class",
            "/modules/java.base/jdk/internal/reflect/ConstructorAccessorImpl.class",
            "/modules/java.base/jdk/internal/reflect/DirectConstructorHandleAccessor$NativeAccessor.class",
            "/modules/java.base/java/lang/invoke/MethodHandle.class",
            "/modules/java.base/java/lang/invoke/DirectMethodHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandle.class",
            "/modules/java.base/java/lang/invoke/MemberName.class",
            "/modules/java.base/java/lang/invoke/ResolvedMethodName.class",
            "/modules/java.base/java/lang/invoke/MethodHandleNatives.class",
            "/modules/java.base/java/lang/invoke/LambdaForm.class",
            "/modules/java.base/java/lang/invoke/TypeDescriptor$OfMethod.class",
            "/modules/java.base/java/lang/invoke/MethodType.class",
            "/modules/java.base/java/lang/BootstrapMethodError.class",
            "/modules/java.base/java/lang/invoke/CallSite.class",
            "/modules/java.base/jdk/internal/foreign/abi/NativeEntryPoint.class",
            "/modules/java.base/jdk/internal/foreign/abi/ABIDescriptor.class",
            "/modules/java.base/jdk/internal/foreign/abi/VMStorage.class",
            "/modules/java.base/jdk/internal/foreign/abi/UpcallLinker$CallRegs.class",
            "/modules/java.base/java/lang/invoke/ConstantCallSite.class",
            "/modules/java.base/java/lang/invoke/MutableCallSite.class",
            "/modules/java.base/java/lang/invoke/VolatileCallSite.class",
            "/modules/java.base/java/lang/AssertionStatusDirectives.class",
            "/modules/java.base/java/lang/Appendable.class",
            "/modules/java.base/java/lang/AbstractStringBuilder.class",
            "/modules/java.base/java/lang/StringBuffer.class",
            "/modules/java.base/java/lang/StringBuilder.class",
            "/modules/java.base/jdk/internal/misc/UnsafeConstants.class",
            "/modules/java.base/jdk/internal/misc/Unsafe.class",
            "/modules/java.base/jdk/internal/module/Modules.class",
            "/modules/java.base/java/lang/AutoCloseable.class",
            "/modules/java.base/java/io/Closeable.class",
            "/modules/java.base/java/io/InputStream.class",
            "/modules/java.base/java/io/ByteArrayInputStream.class",
            "/modules/java.base/java/net/URL.class",
            "/modules/java.base/java/lang/Enum.class",
            "/modules/java.base/java/util/jar/Manifest.class",
            "/modules/java.base/jdk/internal/loader/BuiltinClassLoader.class",
            "/modules/java.base/jdk/internal/loader/ClassLoaders.class",
            "/modules/java.base/jdk/internal/loader/ClassLoaders$AppClassLoader.class",
            "/modules/java.base/jdk/internal/loader/ClassLoaders$PlatformClassLoader.class",
            "/modules/java.base/java/security/CodeSource.class",
            "/modules/java.base/java/util/concurrent/ConcurrentMap.class",
            "/modules/java.base/java/util/AbstractMap.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap.class",
            "/modules/java.base/java/lang/Iterable.class",
            "/modules/java.base/java/util/Collection.class",
            "/modules/java.base/java/util/SequencedCollection.class",
            "/modules/java.base/java/util/List.class",
            "/modules/java.base/java/util/RandomAccess.class",
            "/modules/java.base/java/util/AbstractCollection.class",
            "/modules/java.base/java/util/AbstractList.class",
            "/modules/java.base/java/util/ArrayList.class",
            "/modules/java.base/java/lang/StackTraceElement.class",
            "/modules/java.base/java/nio/Buffer.class",
            "/modules/java.base/java/lang/StackWalker.class",
            "/modules/java.base/java/lang/StackStreamFactory$AbstractStackWalker.class",
            "/modules/java.base/java/lang/StackWalker$StackFrame.class",
            "/modules/java.base/java/lang/ClassFrameInfo.class",
            "/modules/java.base/java/lang/StackFrameInfo.class",
            "/modules/java.base/java/lang/LiveStackFrame.class",
            "/modules/java.base/java/lang/LiveStackFrameInfo.class",
            "/modules/java.base/java/util/concurrent/locks/AbstractOwnableSynchronizer.class",
            "/modules/java.base/java/lang/Boolean.class",
            "/modules/java.base/java/lang/Character.class",
            "/modules/java.base/java/lang/Number.class",
            "/modules/java.base/java/lang/Float.class",
            "/modules/java.base/java/lang/Double.class",
            "/modules/java.base/java/lang/Byte.class",
            "/modules/java.base/java/lang/Short.class",
            "/modules/java.base/java/lang/Integer.class",
            "/modules/java.base/java/lang/Long.class",
            "/modules/java.base/java/lang/Void.class",
            "/modules/java.base/java/util/Iterator.class",
            "/modules/java.base/java/lang/reflect/RecordComponent.class",
            "/modules/java.base/jdk/internal/vm/vector/VectorSupport.class",
            "/modules/java.base/jdk/internal/vm/vector/VectorSupport$VectorPayload.class",
            "/modules/java.base/jdk/internal/vm/vector/VectorSupport$Vector.class",
            "/modules/java.base/jdk/internal/vm/vector/VectorSupport$VectorMask.class",
            "/modules/java.base/jdk/internal/vm/vector/VectorSupport$VectorShuffle.class",
            "/modules/java.base/jdk/internal/vm/FillerObject.class",
            "/modules/java.base/java/lang/NullPointerException.class",
            "/modules/java.base/java/lang/ArithmeticException.class",
            "/modules/java.base/java/lang/IndexOutOfBoundsException.class",
            "/modules/java.base/java/lang/ArrayIndexOutOfBoundsException.class",
            "/modules/java.base/java/io/ObjectStreamField.class",
            "/modules/java.base/java/util/Comparator.class",
            "/modules/java.base/java/lang/String$CaseInsensitiveComparator.class",
            "/modules/java.base/jdk/internal/misc/VM.class",
            "/modules/java.base/java/lang/Module$ArchivedData.class",
            "/modules/java.base/jdk/internal/misc/CDS.class",
            "/modules/java.base/java/util/Set.class",
            "/modules/java.base/java/util/ImmutableCollections$AbstractImmutableCollection.class",
            "/modules/java.base/java/util/ImmutableCollections$AbstractImmutableSet.class",
            "/modules/java.base/java/util/ImmutableCollections$Set12.class",
            "/modules/java.base/java/util/Objects.class",
            "/modules/java.base/java/util/ImmutableCollections.class",
            "/modules/java.base/java/util/ImmutableCollections$AbstractImmutableList.class",
            "/modules/java.base/java/util/ImmutableCollections$ListN.class",
            "/modules/java.base/java/util/ImmutableCollections$SetN.class",
            "/modules/java.base/java/util/ImmutableCollections$AbstractImmutableMap.class",
            "/modules/java.base/java/util/ImmutableCollections$MapN.class",
            "/modules/java.base/jdk/internal/access/JavaLangReflectAccess.class",
            "/modules/java.base/java/lang/reflect/ReflectAccess.class",
            "/modules/java.base/jdk/internal/access/SharedSecrets.class",
            "/modules/java.base/jdk/internal/reflect/ReflectionFactory.class",
            "/modules/java.base/java/io/ObjectStreamClass.class",
            "/modules/java.base/java/lang/Math.class",
            "/modules/java.base/jdk/internal/reflect/ReflectionFactory$Config.class",
            "/modules/java.base/jdk/internal/access/JavaLangRefAccess.class",
            "/modules/java.base/java/lang/ref/ReferenceQueue.class",
            "/modules/java.base/java/lang/ref/ReferenceQueue$Null.class",
            "/modules/java.base/java/lang/ref/ReferenceQueue$Lock.class",
            "/modules/java.base/jdk/internal/access/JavaLangAccess.class",
            "/modules/java.base/jdk/internal/util/SystemProps.class",
            "/modules/java.base/jdk/internal/util/SystemProps$Raw.class",
            "/modules/java.base/java/nio/charset/Charset.class",
            "/modules/java.base/java/nio/charset/spi/CharsetProvider.class",
            "/modules/java.base/sun/nio/cs/StandardCharsets.class",
            "/modules/java.base/java/lang/StringLatin1.class",
            "/modules/java.base/sun/nio/cs/HistoricallyNamedCharset.class",
            "/modules/java.base/sun/nio/cs/Unicode.class",
            "/modules/java.base/sun/nio/cs/UTF_8.class",
            "/modules/java.base/java/util/HashMap.class",
            "/modules/java.base/java/lang/StrictMath.class",
            "/modules/java.base/jdk/internal/util/ArraysSupport.class",
            "/modules/java.base/java/util/Map$Entry.class",
            "/modules/java.base/java/util/HashMap$Node.class",
            "/modules/java.base/java/util/LinkedHashMap$Entry.class",
            "/modules/java.base/java/util/HashMap$TreeNode.class",
            "/modules/java.base/java/lang/StringConcatHelper.class",
            "/modules/java.base/java/lang/VersionProps.class",
            "/modules/java.base/java/lang/Runtime.class",
            "/modules/java.base/java/util/concurrent/locks/Lock.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantLock.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$Segment.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$CounterCell.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$Node.class",
            "/modules/java.base/java/util/concurrent/locks/LockSupport.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$ReservationNode.class",
            "/modules/java.base/java/util/AbstractSet.class",
            "/modules/java.base/java/util/HashMap$EntrySet.class",
            "/modules/java.base/java/util/HashMap$HashIterator.class",
            "/modules/java.base/java/util/HashMap$EntryIterator.class",
            "/modules/java.base/jdk/internal/util/StaticProperty.class",
            "/modules/java.base/java/io/FileInputStream.class",
            "/modules/java.base/java/lang/System$In.class",
            "/modules/java.base/java/io/FileDescriptor.class",
            "/modules/java.base/jdk/internal/access/JavaIOFileDescriptorAccess.class",
            "/modules/java.base/java/io/Flushable.class",
            "/modules/java.base/java/io/OutputStream.class",
            "/modules/java.base/java/io/FileOutputStream.class",
            "/modules/java.base/java/lang/System$Out.class",
            "/modules/java.base/java/io/FilterInputStream.class",
            "/modules/java.base/java/io/BufferedInputStream.class",
            "/modules/java.base/java/io/FilterOutputStream.class",
            "/modules/java.base/java/io/PrintStream.class",
            "/modules/java.base/java/io/BufferedOutputStream.class",
            "/modules/java.base/java/io/Writer.class",
            "/modules/java.base/java/io/OutputStreamWriter.class",
            "/modules/java.base/sun/nio/cs/StreamEncoder.class",
            "/modules/java.base/java/nio/charset/CharsetEncoder.class",
            "/modules/java.base/sun/nio/cs/UTF_8$Encoder.class",
            "/modules/java.base/java/nio/charset/CodingErrorAction.class",
            "/modules/java.base/java/util/Arrays.class",
            "/modules/java.base/java/nio/ByteBuffer.class",
            "/modules/java.base/jdk/internal/misc/ScopedMemoryAccess.class",
            "/modules/java.base/java/util/function/Function.class",
            "/modules/java.base/jdk/internal/util/Preconditions.class",
            "/modules/java.base/java/util/function/BiFunction.class",
            "/modules/java.base/jdk/internal/access/JavaNioAccess.class",
            "/modules/java.base/java/nio/HeapByteBuffer.class",
            "/modules/java.base/java/nio/ByteOrder.class",
            "/modules/java.base/java/io/BufferedWriter.class",
            "/modules/java.base/java/lang/Terminator.class",
            "/modules/java.base/jdk/internal/misc/Signal$Handler.class",
            "/modules/java.base/jdk/internal/misc/Signal.class",
            "/modules/java.base/java/util/Hashtable$Entry.class",
            "/modules/java.base/jdk/internal/misc/Signal$NativeHandler.class",
            "/modules/java.base/java/lang/Integer$IntegerCache.class",
            "/modules/java.base/jdk/internal/misc/OSEnvironment.class",
            "/modules/java.base/java/lang/Thread$State.class",
            "/modules/java.base/java/lang/ref/Reference$ReferenceHandler.class",
            "/modules/java.base/java/lang/Thread$ThreadIdentifiers.class",
            "/modules/java.base/java/lang/ref/Finalizer$FinalizerThread.class",
            "/modules/java.base/jdk/internal/ref/Cleaner.class",
            "/modules/java.base/java/util/Collections.class",
            "/modules/java.base/java/util/Collections$EmptySet.class",
            "/modules/java.base/java/util/Collections$EmptyList.class",
            "/modules/java.base/java/util/Collections$EmptyMap.class",
            "/modules/java.base/java/lang/IllegalArgumentException.class",
            "/modules/java.base/java/lang/invoke/MethodHandleStatics.class",
            "/modules/java.base/java/lang/reflect/ClassFileFormatVersion.class",
            "/modules/java.base/java/lang/CharacterData.class",
            "/modules/java.base/java/lang/CharacterDataLatin1.class",
            "/modules/java.base/jdk/internal/util/ClassFileDumper.class",
            "/modules/java.base/java/util/HexFormat.class",
            "/modules/java.base/java/lang/Character$CharacterCache.class",
            "/modules/java.base/java/util/concurrent/atomic/AtomicInteger.class",
            "/modules/java.base/jdk/internal/module/ModuleBootstrap.class",
            "/modules/java.base/java/lang/module/ModuleDescriptor.class",
            "/modules/java.base/java/lang/invoke/MethodHandles.class",
            "/modules/java.base/java/lang/invoke/MemberName$Factory.class",
            "/modules/java.base/jdk/internal/reflect/Reflection.class",
            "/modules/java.base/java/lang/invoke/MethodHandles$Lookup.class",
            "/modules/java.base/java/util/ImmutableCollections$MapN$MapNIterator.class",
            "/modules/java.base/java/util/KeyValueHolder.class",
            "/modules/java.base/sun/invoke/util/VerifyAccess.class",
            "/modules/java.base/java/lang/reflect/Modifier.class",
            "/modules/java.base/jdk/internal/access/JavaLangModuleAccess.class",
            "/modules/java.base/java/io/File.class",
            "/modules/java.base/java/io/DefaultFileSystem.class",
            "/modules/java.base/java/io/FileSystem.class",
            "/modules/java.base/java/io/UnixFileSystem.class",
            "/modules/java.base/jdk/internal/util/DecimalDigits.class",
            "/modules/java.base/jdk/internal/module/ModulePatcher.class",
            "/modules/java.base/jdk/internal/module/ModuleBootstrap$IllegalNativeAccess.class",
            "/modules/java.base/java/util/HashSet.class",
            "/modules/java.base/jdk/internal/module/ModuleLoaderMap.class",
            "/modules/java.base/jdk/internal/module/ModuleLoaderMap$Modules.class",
            "/modules/java.base/jdk/internal/module/ModuleBootstrap$Counters.class",
            "/modules/java.base/jdk/internal/module/ArchivedBootLayer.class",
            "/modules/java.base/jdk/internal/module/ArchivedModuleGraph.class",
            "/modules/java.base/jdk/internal/module/SystemModuleFinders.class",
            "/modules/java.base/java/net/URI.class",
            "/modules/java.base/jdk/internal/access/JavaNetUriAccess.class",
            "/modules/java.base/jdk/internal/module/SystemModulesMap.class",
            "/modules/java.base/jdk/internal/module/SystemModules.class",
            "/modules/java.base/jdk/internal/module/ExplodedSystemModules.class",
            "/modules/java.base/java/nio/file/Watchable.class",
            "/modules/java.base/java/nio/file/Path.class",
            "/modules/java.base/java/nio/file/FileSystems.class",
            "/modules/java.base/sun/nio/fs/DefaultFileSystemProvider.class",
            "/modules/java.base/java/nio/file/spi/FileSystemProvider.class",
            "/modules/java.base/sun/nio/fs/AbstractFileSystemProvider.class",
            "/modules/java.base/sun/nio/fs/UnixFileSystemProvider.class",
            "/modules/java.base/sun/nio/fs/LinuxFileSystemProvider.class",
            "/modules/java.base/java/nio/file/OpenOption.class",
            "/modules/java.base/java/nio/file/StandardOpenOption.class",
            "/modules/java.base/java/nio/file/FileSystem.class",
            "/modules/java.base/sun/nio/fs/UnixFileSystem.class",
            "/modules/java.base/sun/nio/fs/LinuxFileSystem.class",
            "/modules/java.base/sun/nio/fs/UnixPath.class",
            "/modules/java.base/sun/nio/fs/Util.class",
            "/modules/java.base/java/lang/StringCoding.class",
            "/modules/java.base/sun/nio/fs/UnixNativeDispatcher.class",
            "/modules/java.base/jdk/internal/loader/BootLoader.class",
            "/modules/java.base/java/lang/Module$EnableNativeAccess.class",
            "/modules/java.base/jdk/internal/loader/NativeLibraries.class",
            "/modules/java.base/jdk/internal/loader/ClassLoaderHelper.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$CollectionView.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$KeySetView.class",
            "/modules/java.base/jdk/internal/loader/NativeLibraries$LibraryPaths.class",
            "/modules/java.base/java/io/File$PathStatus.class",
            "/modules/java.base/jdk/internal/loader/NativeLibraries$CountedLock.class",
            "/modules/java.base/java/util/concurrent/locks/AbstractQueuedSynchronizer.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantLock$Sync.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantLock$NonfairSync.class",
            "/modules/java.base/jdk/internal/loader/NativeLibraries$NativeLibraryContext.class",
            "/modules/java.base/java/util/Queue.class",
            "/modules/java.base/java/util/Deque.class",
            "/modules/java.base/java/util/ArrayDeque.class",
            "/modules/java.base/java/util/ArrayDeque$DeqIterator.class",
            "/modules/java.base/jdk/internal/loader/NativeLibrary.class",
            "/modules/java.base/jdk/internal/loader/NativeLibraries$NativeLibraryImpl.class",
            "/modules/java.base/java/security/cert/Certificate.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$ValuesView.class",
            "/modules/java.base/java/util/Enumeration.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$Traverser.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$BaseIterator.class",
            "/modules/java.base/java/util/concurrent/ConcurrentHashMap$ValueIterator.class",
            "/modules/java.base/java/nio/file/attribute/BasicFileAttributes.class",
            "/modules/java.base/java/nio/file/attribute/PosixFileAttributes.class",
            "/modules/java.base/sun/nio/fs/UnixFileAttributes.class",
            "/modules/java.base/sun/nio/fs/UnixFileStoreAttributes.class",
            "/modules/java.base/sun/nio/fs/UnixMountEntry.class",
            "/modules/java.base/java/nio/file/CopyOption.class",
            "/modules/java.base/java/nio/file/LinkOption.class",
            "/modules/java.base/java/nio/file/Files.class",
            "/modules/java.base/sun/nio/fs/NativeBuffers.class",
            "/modules/java.base/java/lang/ThreadLocal.class",
            "/modules/java.base/jdk/internal/misc/CarrierThreadLocal.class",
            "/modules/java.base/jdk/internal/misc/TerminatingThreadLocal.class",
            "/modules/java.base/java/lang/ThreadLocal$ThreadLocalMap.class",
            "/modules/java.base/java/lang/ThreadLocal$ThreadLocalMap$Entry.class",
            "/modules/java.base/java/util/IdentityHashMap.class",
            "/modules/java.base/java/util/Collections$SetFromMap.class",
            "/modules/java.base/java/util/IdentityHashMap$KeySet.class",
            "/modules/java.base/sun/nio/fs/NativeBuffer.class",
            "/modules/java.base/jdk/internal/ref/CleanerFactory.class",
            "/modules/java.base/java/util/concurrent/ThreadFactory.class",
            "/modules/java.base/java/lang/ref/Cleaner.class",
            "/modules/java.base/jdk/internal/ref/CleanerImpl.class",
            "/modules/java.base/jdk/internal/ref/CleanerImpl$CleanableList.class",
            "/modules/java.base/jdk/internal/ref/CleanerImpl$CleanableList$Node.class",
            "/modules/java.base/java/lang/ref/Cleaner$Cleanable.class",
            "/modules/java.base/jdk/internal/ref/PhantomCleanable.class",
            "/modules/java.base/jdk/internal/ref/CleanerImpl$CleanerCleanable.class",
            "/modules/java.base/jdk/internal/misc/InnocuousThread.class",
            "/modules/java.base/sun/nio/fs/NativeBuffer$Deallocator.class",
            "/modules/java.base/jdk/internal/ref/CleanerImpl$PhantomCleanableRef.class",
            "/modules/java.base/java/lang/module/ModuleFinder.class",
            "/modules/java.base/jdk/internal/module/ModulePath.class",
            "/modules/java.base/java/util/jar/Attributes$Name.class",
            "/modules/java.base/java/lang/reflect/Array.class",
            "/modules/java.base/jdk/internal/perf/PerfCounter.class",
            "/modules/java.base/jdk/internal/perf/Perf.class",
            "/modules/java.base/sun/nio/ch/DirectBuffer.class",
            "/modules/java.base/java/nio/MappedByteBuffer.class",
            "/modules/java.base/java/nio/DirectByteBuffer.class",
            "/modules/java.base/java/nio/Bits.class",
            "/modules/java.base/java/util/concurrent/atomic/AtomicLong.class",
            "/modules/java.base/jdk/internal/misc/VM$BufferPool.class",
            "/modules/java.base/java/nio/LongBuffer.class",
            "/modules/java.base/java/nio/DirectLongBufferU.class",
            "/modules/java.base/java/util/zip/ZipConstants.class",
            "/modules/java.base/java/util/zip/ZipFile.class",
            "/modules/java.base/java/util/jar/JarFile.class",
            "/modules/java.base/java/util/BitSet.class",
            "/modules/java.base/jdk/internal/access/JavaUtilZipFileAccess.class",
            "/modules/java.base/jdk/internal/access/JavaUtilJarAccess.class",
            "/modules/java.base/java/util/jar/JavaUtilJarAccessImpl.class",
            "/modules/java.base/java/lang/Runtime$Version.class",
            "/modules/java.base/java/util/ImmutableCollections$List12.class",
            "/modules/java.base/java/util/Optional.class",
            "/modules/java.base/java/nio/file/attribute/DosFileAttributes.class",
            "/modules/java.base/java/nio/file/attribute/AttributeView.class",
            "/modules/java.base/java/nio/file/attribute/FileAttributeView.class",
            "/modules/java.base/java/nio/file/attribute/BasicFileAttributeView.class",
            "/modules/java.base/java/nio/file/attribute/DosFileAttributeView.class",
            "/modules/java.base/java/nio/file/attribute/UserDefinedFileAttributeView.class",
            "/modules/java.base/sun/nio/fs/UnixFileAttributeViews.class",
            "/modules/java.base/sun/nio/fs/DynamicFileAttributeView.class",
            "/modules/java.base/sun/nio/fs/AbstractBasicFileAttributeView.class",
            "/modules/java.base/sun/nio/fs/UnixFileAttributeViews$Basic.class",
            "/modules/java.base/sun/nio/fs/UnixFileAttributes$UnixAsBasicFileAttributes.class",
            "/modules/java.base/java/nio/file/DirectoryStream$Filter.class",
            "/modules/java.base/java/nio/file/Files$AcceptAllFilter.class",
            "/modules/java.base/java/nio/file/DirectoryStream.class",
            "/modules/java.base/java/nio/file/SecureDirectoryStream.class",
            "/modules/java.base/sun/nio/fs/UnixSecureDirectoryStream.class",
            "/modules/java.base/sun/nio/fs/UnixDirectoryStream.class",
            "/modules/java.base/java/util/concurrent/locks/ReadWriteLock.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantReadWriteLock.class",
            "/modules/java.base/java/util/concurrent/locks/AbstractQueuedLongSynchronizer.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantReadWriteLock$Sync.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantReadWriteLock$FairSync.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantReadWriteLock$Sync$ThreadLocalHoldCounter.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock.class",
            "/modules/java.base/java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock.class",
            "/modules/java.base/sun/nio/fs/UnixDirectoryStream$UnixDirectoryIterator.class",
            "/modules/java.base/java/nio/file/attribute/FileAttribute.class",
            "/modules/java.base/sun/nio/fs/UnixFileModeAttribute.class",
            "/modules/java.base/sun/nio/fs/UnixChannelFactory.class",
            "/modules/java.base/sun/nio/fs/UnixChannelFactory$Flags.class",
            "/modules/java.base/java/util/Collections$EmptyIterator.class",
            "/modules/java.base/java/nio/channels/Channel.class",
            "/modules/java.base/java/nio/channels/ReadableByteChannel.class",
            "/modules/java.base/java/nio/channels/WritableByteChannel.class",
            "/modules/java.base/java/nio/channels/ByteChannel.class",
            "/modules/java.base/java/nio/channels/SeekableByteChannel.class",
            "/modules/java.base/java/nio/channels/GatheringByteChannel.class",
            "/modules/java.base/java/nio/channels/ScatteringByteChannel.class",
            "/modules/java.base/java/nio/channels/InterruptibleChannel.class",
            "/modules/java.base/java/nio/channels/spi/AbstractInterruptibleChannel.class",
            "/modules/java.base/java/nio/channels/FileChannel.class",
            "/modules/java.base/sun/nio/ch/FileChannelImpl.class",
            "/modules/java.base/sun/nio/ch/NativeDispatcher.class",
            "/modules/java.base/sun/nio/ch/FileDispatcher.class",
            "/modules/java.base/sun/nio/ch/UnixFileDispatcherImpl.class",
            "/modules/java.base/sun/nio/ch/FileDispatcherImpl.class",
            "/modules/java.base/sun/nio/ch/IOUtil.class",
            "/modules/java.base/sun/nio/ch/Interruptible.class",
            "/modules/java.base/sun/nio/ch/NativeThreadSet.class",
            "/modules/java.base/sun/nio/ch/FileChannelImpl$Closer.class",
            "/modules/java.base/java/nio/channels/Channels.class",
            "/modules/java.base/sun/nio/ch/Streams.class",
            "/modules/java.base/sun/nio/ch/SelChImpl.class",
            "/modules/java.base/java/nio/channels/NetworkChannel.class",
            "/modules/java.base/java/nio/channels/SelectableChannel.class",
            "/modules/java.base/java/nio/channels/spi/AbstractSelectableChannel.class",
            "/modules/java.base/java/nio/channels/SocketChannel.class",
            "/modules/java.base/sun/nio/ch/SocketChannelImpl.class",
            "/modules/java.base/sun/nio/ch/ChannelInputStream.class",
            "/modules/java.base/java/lang/invoke/LambdaMetafactory.class",
            "/modules/java.base/java/util/function/Supplier.class",
            "/modules/java.base/jdk/internal/util/ReferencedKeySet.class",
            "/modules/java.base/jdk/internal/util/ReferencedKeyMap.class",
            "/modules/java.base/jdk/internal/util/ReferenceKey.class",
            "/modules/java.base/jdk/internal/util/StrongReferenceKey.class",
            "/modules/java.base/java/lang/invoke/MethodTypeForm.class",
            "/modules/java.base/jdk/internal/util/WeakReferenceKey.class",
            "/modules/java.base/sun/invoke/util/Wrapper.class",
            "/modules/java.base/sun/invoke/util/Wrapper$Format.class",
            "/modules/java.base/java/lang/constant/ConstantDescs.class",
            "/modules/java.base/java/lang/constant/ClassDesc.class",
            "/modules/java.base/jdk/internal/constant/ClassOrInterfaceDescImpl.class",
            "/modules/java.base/jdk/internal/constant/ArrayClassDescImpl.class",
            "/modules/java.base/jdk/internal/constant/ConstantUtils.class",
            "/modules/java.base/java/lang/constant/DirectMethodHandleDesc$Kind.class",
            "/modules/java.base/java/lang/constant/MethodTypeDesc.class",
            "/modules/java.base/jdk/internal/constant/MethodTypeDescImpl.class",
            "/modules/java.base/java/lang/constant/MethodHandleDesc.class",
            "/modules/java.base/java/lang/constant/DirectMethodHandleDesc.class",
            "/modules/java.base/jdk/internal/constant/DirectMethodHandleDescImpl.class",
            "/modules/java.base/java/lang/constant/DynamicConstantDesc.class",
            "/modules/java.base/jdk/internal/constant/PrimitiveClassDescImpl.class",
            "/modules/java.base/java/lang/constant/DynamicConstantDesc$AnonymousDynamicConstantDesc.class",
            "/modules/java.base/java/lang/invoke/LambdaForm$NamedFunction.class",
            "/modules/java.base/java/lang/invoke/DirectMethodHandle$Holder.class",
            "/modules/java.base/sun/invoke/util/ValueConversions.class",
            "/modules/java.base/java/lang/invoke/MethodHandleImpl.class",
            "/modules/java.base/java/lang/invoke/Invokers.class",
            "/modules/java.base/java/lang/invoke/LambdaForm$Kind.class",
            "/modules/java.base/java/lang/NoSuchMethodException.class",
            "/modules/java.base/java/lang/invoke/LambdaForm$BasicType.class",
            "/modules/java.base/java/lang/classfile/TypeKind.class",
            "/modules/java.base/java/lang/invoke/LambdaForm$Name.class",
            "/modules/java.base/java/lang/invoke/LambdaForm$Holder.class",
            "/modules/java.base/java/lang/invoke/InvokerBytecodeGenerator.class",
            "/modules/java.base/java/lang/classfile/AnnotationElement.class",
            "/modules/java.base/java/lang/classfile/Annotation.class",
            "/modules/java.base/java/lang/classfile/constantpool/ConstantPool.class",
            "/modules/java.base/java/lang/classfile/constantpool/ConstantPoolBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/TemporaryConstantPool.class",
            "/modules/java.base/java/lang/classfile/constantpool/PoolEntry.class",
            "/modules/java.base/java/lang/classfile/constantpool/AnnotationConstantValueEntry.class",
            "/modules/java.base/java/lang/classfile/constantpool/Utf8Entry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$Utf8EntryImpl.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$Utf8EntryImpl$State.class",
            "/modules/java.base/jdk/internal/classfile/impl/AnnotationImpl.class",
            "/modules/java.base/java/lang/classfile/ClassFileElement.class",
            "/modules/java.base/java/lang/classfile/Attribute.class",
            "/modules/java.base/java/lang/classfile/ClassElement.class",
            "/modules/java.base/java/lang/classfile/MethodElement.class",
            "/modules/java.base/java/lang/classfile/FieldElement.class",
            "/modules/java.base/java/lang/classfile/attribute/RuntimeVisibleAnnotationsAttribute.class",
            "/modules/java.base/jdk/internal/classfile/impl/Util$Writable.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractElement.class",
            "/modules/java.base/jdk/internal/classfile/impl/UnboundAttribute.class",
            "/modules/java.base/jdk/internal/classfile/impl/UnboundAttribute$UnboundRuntimeVisibleAnnotationsAttribute.class",
            "/modules/java.base/java/lang/classfile/Attributes.class",
            "/modules/java.base/java/lang/classfile/AttributeMapper.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractAttributeMapper.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractAttributeMapper$RuntimeVisibleAnnotationsMapper.class",
            "/modules/java.base/java/lang/classfile/AttributeMapper$AttributeStability.class",
            "/modules/java.base/java/lang/invoke/MethodHandleImpl$Intrinsic.class",
            "/modules/java.base/jdk/internal/classfile/impl/SplitConstantPool.class",
            "/modules/java.base/java/lang/classfile/BootstrapMethodEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/BootstrapMethodEntryImpl.class",
            "/modules/java.base/jdk/internal/classfile/impl/EntryMap.class",
            "/modules/java.base/jdk/internal/classfile/impl/Util.class",
            "/modules/java.base/java/lang/classfile/constantpool/LoadableConstantEntry.class",
            "/modules/java.base/java/lang/classfile/constantpool/ClassEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$AbstractRefEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$AbstractNamedEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$ClassEntryImpl.class",
            "/modules/java.base/java/util/function/Consumer.class",
            "/modules/java.base/java/lang/classfile/ClassFile.class",
            "/modules/java.base/jdk/internal/classfile/impl/ClassFileImpl.class",
            "/modules/java.base/java/lang/classfile/ClassFileBuilder.class",
            "/modules/java.base/java/lang/classfile/ClassBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractDirectBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/DirectClassBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/AttributeHolder.class",
            "/modules/java.base/java/lang/classfile/Superclass.class",
            "/modules/java.base/jdk/internal/classfile/impl/SuperclassImpl.class",
            "/modules/java.base/java/lang/classfile/attribute/SourceFileAttribute.class",
            "/modules/java.base/jdk/internal/classfile/impl/UnboundAttribute$UnboundSourceFileAttribute.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractAttributeMapper$SourceFileMapper.class",
            "/modules/java.base/jdk/internal/classfile/impl/BoundAttribute.class",
            "/modules/java.base/java/lang/classfile/MethodBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/MethodInfo.class",
            "/modules/java.base/jdk/internal/classfile/impl/TerminalMethodBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/DirectMethodBuilder.class",
            "/modules/java.base/java/lang/classfile/constantpool/NameAndTypeEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$AbstractRefsEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$NameAndTypeEntryImpl.class",
            "/modules/java.base/java/lang/classfile/constantpool/MemberRefEntry.class",
            "/modules/java.base/java/lang/classfile/constantpool/FieldRefEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$AbstractMemberRefEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$FieldRefEntryImpl.class",
            "/modules/java.base/java/lang/invoke/InvokerBytecodeGenerator$ClassData.class",
            "/modules/java.base/java/lang/classfile/CodeBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/LabelContext.class",
            "/modules/java.base/jdk/internal/classfile/impl/TerminalCodeBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/DirectCodeBuilder.class",
            "/modules/java.base/java/lang/classfile/CodeElement.class",
            "/modules/java.base/java/lang/classfile/PseudoInstruction.class",
            "/modules/java.base/java/lang/classfile/instruction/CharacterRange.class",
            "/modules/java.base/java/lang/classfile/instruction/LocalVariable.class",
            "/modules/java.base/java/lang/classfile/instruction/LocalVariableType.class",
            "/modules/java.base/jdk/internal/classfile/impl/DirectCodeBuilder$DeferredLabel.class",
            "/modules/java.base/java/lang/classfile/BufWriter.class",
            "/modules/java.base/jdk/internal/classfile/impl/BufWriterImpl.class",
            "/modules/java.base/java/lang/classfile/Label.class",
            "/modules/java.base/java/lang/classfile/instruction/LabelTarget.class",
            "/modules/java.base/jdk/internal/classfile/impl/LabelImpl.class",
            "/modules/java.base/sun/invoke/util/VerifyType.class",
            "/modules/java.base/java/lang/classfile/Opcode.class",
            "/modules/java.base/java/lang/classfile/Opcode$Kind.class",
            "/modules/java.base/java/lang/classfile/constantpool/MethodRefEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$MethodRefEntryImpl.class",
            "/modules/java.base/sun/invoke/empty/Empty.class",
            "/modules/java.base/jdk/internal/classfile/impl/BytecodeHelpers.class",
            "/modules/java.base/jdk/internal/classfile/impl/UnboundAttribute$AdHocAttribute.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractAttributeMapper$CodeMapper.class",
            "/modules/java.base/java/lang/classfile/FieldBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/TerminalFieldBuilder.class",
            "/modules/java.base/jdk/internal/classfile/impl/DirectFieldBuilder.class",
            "/modules/java.base/java/lang/classfile/CustomAttribute.class",
            "/modules/java.base/jdk/internal/classfile/impl/AnnotationReader.class",
            "/modules/java.base/java/util/ListIterator.class",
            "/modules/java.base/java/util/ImmutableCollections$ListItr.class",
            "/modules/java.base/jdk/internal/classfile/impl/StackMapGenerator.class",
            "/modules/java.base/jdk/internal/classfile/impl/StackMapGenerator$Frame.class",
            "/modules/java.base/jdk/internal/classfile/impl/StackMapGenerator$Type.class",
            "/modules/java.base/jdk/internal/classfile/impl/RawBytecodeHelper.class",
            "/modules/java.base/jdk/internal/classfile/impl/RawBytecodeHelper$CodeRange.class",
            "/modules/java.base/jdk/internal/classfile/impl/ClassHierarchyImpl.class",
            "/modules/java.base/java/lang/classfile/ClassHierarchyResolver.class",
            "/modules/java.base/jdk/internal/classfile/impl/ClassHierarchyImpl$ClassLoadingClassHierarchyResolver.class",
            "/modules/java.base/jdk/internal/classfile/impl/ClassHierarchyImpl$CachedClassHierarchyResolver.class",
            "/modules/java.base/java/lang/classfile/ClassHierarchyResolver$ClassHierarchyInfo.class",
            "/modules/java.base/jdk/internal/classfile/impl/ClassHierarchyImpl$ClassHierarchyInfoImpl.class",
            "/modules/java.base/java/lang/classfile/ClassReader.class",
            "/modules/java.base/jdk/internal/classfile/impl/ClassReaderImpl.class",
            "/modules/java.base/jdk/internal/util/ModifiedUtf.class",
            "/modules/java.base/java/lang/invoke/MethodHandles$Lookup$ClassDefiner.class",
            "/modules/java.base/java/lang/IncompatibleClassChangeError.class",
            "/modules/java.base/java/lang/NoSuchMethodError.class",
            "/modules/java.base/java/lang/invoke/BootstrapMethodInvoker.class",
            "/modules/java.base/java/lang/invoke/AbstractValidatingLambdaMetafactory.class",
            "/modules/java.base/java/lang/invoke/InnerClassLambdaMetafactory.class",
            "/modules/java.base/java/lang/invoke/MethodHandleInfo.class",
            "/modules/java.base/java/lang/invoke/InfoFromMemberName.class",
            "/modules/java.base/java/util/ImmutableCollections$Access.class",
            "/modules/java.base/jdk/internal/access/JavaUtilCollectionAccess.class",
            "/modules/java.base/java/lang/classfile/Interfaces.class",
            "/modules/java.base/jdk/internal/classfile/impl/InterfacesImpl.class",
            "/modules/java.base/java/lang/invoke/TypeConvertingMethodAdapter.class",
            "/modules/java.base/java/lang/invoke/DirectMethodHandle$Constructor.class",
            "/modules/java.base/jdk/internal/access/JavaLangInvokeAccess.class",
            "/modules/java.base/java/lang/invoke/VarHandle$AccessMode.class",
            "/modules/java.base/java/lang/invoke/VarHandle$AccessType.class",
            "/modules/java.base/java/lang/invoke/Invokers$Holder.class",
            "/modules/java.base/jdk/internal/module/ModuleInfo.class",
            "/modules/java.base/java/io/DataInput.class",
            "/modules/java.base/java/io/DataInputStream.class",
            "/modules/java.base/jdk/internal/module/ModuleInfo$CountingDataInput.class",
            "/modules/java.base/sun/nio/ch/NativeThread.class",
            "/modules/java.base/jdk/internal/misc/Blocker.class",
            "/modules/java.base/sun/nio/ch/Util.class",
            "/modules/java.base/sun/nio/ch/Util$BufferCache.class",
            "/modules/java.base/sun/nio/ch/IOStatus.class",
            "/modules/java.base/jdk/internal/util/ByteArray.class",
            "/modules/java.base/java/lang/invoke/VarHandles.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsShorts$ByteArrayViewVarHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsShorts$ArrayHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleGuards.class",
            "/modules/java.base/java/lang/invoke/VarForm.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsChars$ByteArrayViewVarHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsChars$ArrayHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsInts$ByteArrayViewVarHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsInts$ArrayHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsFloats$ByteArrayViewVarHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsFloats$ArrayHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsLongs$ByteArrayViewVarHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsLongs$ArrayHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsDoubles$ByteArrayViewVarHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandleByteArrayAsDoubles$ArrayHandle.class",
            "/modules/java.base/java/lang/invoke/VarHandle$AccessDescriptor.class",
            "/modules/java.base/jdk/internal/module/ModuleInfo$ConstantPool.class",
            "/modules/java.base/jdk/internal/module/ModuleInfo$ConstantPool$Entry.class",
            "/modules/java.base/jdk/internal/module/ModuleInfo$ConstantPool$IndexEntry.class",
            "/modules/java.base/java/nio/charset/StandardCharsets.class",
            "/modules/java.base/sun/nio/cs/US_ASCII.class",
            "/modules/java.base/sun/nio/cs/ISO_8859_1.class",
            "/modules/java.base/sun/nio/cs/UTF_16BE.class",
            "/modules/java.base/sun/nio/cs/UTF_16LE.class",
            "/modules/java.base/sun/nio/cs/UTF_16.class",
            "/modules/java.base/sun/nio/cs/UTF_32BE.class",
            "/modules/java.base/sun/nio/cs/UTF_32LE.class",
            "/modules/java.base/sun/nio/cs/UTF_32.class",
            "/modules/java.base/jdk/internal/module/ModuleInfo$ConstantPool$ValueEntry.class",
            "/modules/java.base/java/lang/module/ModuleDescriptor$Builder.class",
            "/modules/java.base/java/lang/module/ModuleDescriptor$Modifier.class",
            "/modules/java.base/java/lang/reflect/AccessFlag.class",
            "/modules/java.base/java/lang/reflect/AccessFlag$Location.class",
            "/modules/java.base/java/lang/module/ModuleDescriptor$Requires$Modifier.class",
            "/modules/java.base/java/lang/module/ModuleDescriptor$Requires.class",
            "/modules/java.base/java/util/HashMap$KeySet.class",
            "/modules/java.base/java/util/HashMap$KeyIterator.class",
            "/modules/java.base/jdk/internal/module/Checks.class",
            "/modules/java.base/java/util/ArrayList$Itr.class",
            "/modules/java.base/java/lang/module/ModuleDescriptor$Provides.class",
            "/modules/java.base/java/util/Collections$UnmodifiableCollection.class",
            "/modules/java.base/java/util/Collections$UnmodifiableSet.class",
            "/modules/java.base/java/util/HashMap$Values.class",
            "/modules/java.base/java/util/HashMap$ValueIterator.class",
            "/modules/java.base/java/util/ImmutableCollections$SetN$SetNIterator.class",
            "/modules/java.base/jdk/internal/module/ModuleInfo$Attributes.class",
            "/modules/java.base/jdk/internal/module/ModuleReferences.class",
            "/modules/java.base/java/lang/module/ModuleReader.class",
            "/modules/java.base/sun/nio/fs/UnixUriUtils.class",
            "/modules/java.base/java/net/URI$Parser.class",
            "/modules/java.base/java/lang/module/ModuleReference.class",
            "/modules/java.base/jdk/internal/module/ModuleReferenceImpl.class",
            "/modules/java.base/java/lang/module/ModuleDescriptor$Exports.class",
            "/modules/java.base/java/lang/module/ModuleDescriptor$Opens.class",
            "/modules/java.base/sun/nio/fs/UnixException.class",
            "/modules/java.base/java/io/IOException.class",
            "/modules/java.base/jdk/internal/loader/ArchivedClassLoaders.class",
            "/modules/java.base/jdk/internal/loader/ClassLoaders$BootClassLoader.class",
            "/modules/java.base/java/lang/ClassLoader$ParallelLoaders.class",
            "/modules/java.base/java/util/WeakHashMap.class",
            "/modules/java.base/java/util/WeakHashMap$Entry.class",
            "/modules/java.base/java/util/WeakHashMap$KeySet.class",
            "/modules/java.base/java/security/Principal.class",
            "/modules/java.base/jdk/internal/loader/URLClassPath.class",
            "/modules/java.base/java/net/URLStreamHandlerFactory.class",
            "/modules/java.base/java/net/URL$DefaultFactory.class",
            "/modules/java.base/jdk/internal/access/JavaNetURLAccess.class",
            "/modules/java.base/sun/net/www/ParseUtil.class",
            "/modules/java.base/java/net/URLStreamHandler.class",
            "/modules/java.base/sun/net/www/protocol/file/Handler.class",
            "/modules/java.base/sun/net/util/IPAddressUtil.class",
            "/modules/java.base/sun/net/util/IPAddressUtil$MASKS.class",
            "/modules/java.base/sun/net/www/protocol/jar/Handler.class",
            "/modules/java.base/jdk/internal/module/ServicesCatalog.class",
            "/modules/java.base/jdk/internal/loader/AbstractClassLoaderValue.class",
            "/modules/java.base/jdk/internal/loader/ClassLoaderValue.class",
            "/modules/java.base/jdk/internal/loader/BuiltinClassLoader$LoadedModule.class",
            "/modules/java.base/jdk/internal/module/DefaultRoots.class",
            "/modules/java.base/java/util/Spliterator.class",
            "/modules/java.base/java/util/HashMap$HashMapSpliterator.class",
            "/modules/java.base/java/util/HashMap$ValueSpliterator.class",
            "/modules/java.base/java/util/stream/StreamSupport.class",
            "/modules/java.base/java/util/stream/BaseStream.class",
            "/modules/java.base/java/util/stream/Stream.class",
            "/modules/java.base/java/util/stream/PipelineHelper.class",
            "/modules/java.base/java/util/stream/AbstractPipeline.class",
            "/modules/java.base/java/util/stream/ReferencePipeline.class",
            "/modules/java.base/java/util/stream/ReferencePipeline$Head.class",
            "/modules/java.base/java/util/stream/StreamOpFlag.class",
            "/modules/java.base/java/util/stream/StreamOpFlag$Type.class",
            "/modules/java.base/java/util/stream/StreamOpFlag$MaskBuilder.class",
            "/modules/java.base/java/util/EnumMap.class",
            "/modules/java.base/java/lang/Class$ReflectionData.class",
            "/modules/java.base/java/lang/Class$Atomic.class",
            "/modules/java.base/java/lang/PublicMethods$MethodList.class",
            "/modules/java.base/java/lang/PublicMethods$Key.class",
            "/modules/java.base/sun/reflect/annotation/AnnotationParser.class",
            "/modules/java.base/jdk/internal/reflect/MethodHandleAccessorFactory.class",
            "/modules/java.base/jdk/internal/reflect/MethodHandleAccessorFactory$LazyStaticHolder.class",
            "/modules/java.base/java/lang/invoke/BoundMethodHandle.class",
            "/modules/java.base/java/lang/invoke/ClassSpecializer.class",
            "/modules/java.base/java/lang/invoke/BoundMethodHandle$Specializer.class",
            "/modules/java.base/jdk/internal/vm/annotation/Stable.class",
            "/modules/java.base/java/lang/invoke/ClassSpecializer$SpeciesData.class",
            "/modules/java.base/java/lang/invoke/BoundMethodHandle$SpeciesData.class",
            "/modules/java.base/java/lang/invoke/ClassSpecializer$Factory.class",
            "/modules/java.base/java/lang/invoke/BoundMethodHandle$Specializer$Factory.class",
            "/modules/java.base/java/lang/invoke/SimpleMethodHandle.class",
            "/modules/java.base/java/lang/NoSuchFieldException.class",
            "/modules/java.base/java/lang/invoke/BoundMethodHandle$Species_L.class",
            "/modules/java.base/java/lang/invoke/DirectMethodHandle$Accessor.class",
            "/modules/java.base/java/lang/invoke/DelegatingMethodHandle.class",
            "/modules/java.base/java/lang/invoke/DelegatingMethodHandle$Holder.class",
            "/modules/java.base/java/lang/invoke/LambdaFormEditor.class",
            "/modules/java.base/java/lang/invoke/LambdaFormEditor$TransformKey.class",
            "/modules/java.base/java/lang/invoke/LambdaFormBuffer.class",
            "/modules/java.base/java/lang/invoke/LambdaFormEditor$Transform.class",
            "/modules/java.base/jdk/internal/reflect/DirectMethodHandleAccessor.class",
            "/modules/java.base/java/util/stream/Collectors.class",
            "/modules/java.base/java/util/stream/Collector$Characteristics.class",
            "/modules/java.base/java/util/EnumSet.class",
            "/modules/java.base/java/util/RegularEnumSet.class",
            "/modules/java.base/java/util/stream/Collector.class",
            "/modules/java.base/java/util/stream/Collectors$CollectorImpl.class",
            "/modules/java.base/java/util/function/BiConsumer.class",
            "/modules/java.base/java/lang/invoke/DirectMethodHandle$Interface.class",
            "/modules/java.base/java/lang/classfile/constantpool/InterfaceMethodRefEntry.class",
            "/modules/java.base/jdk/internal/classfile/impl/AbstractPoolEntry$InterfaceMethodRefEntryImpl.class",
            "/modules/java.base/java/util/function/BinaryOperator.class",
            "/modules/java.base/java/util/stream/ReduceOps.class",
            "/modules/java.base/java/util/stream/TerminalOp.class",
            "/modules/java.base/java/util/stream/ReduceOps$ReduceOp.class",
            "/modules/java.base/java/util/stream/StreamShape.class",
            "/modules/java.base/java/util/stream/Sink.class",
            "/modules/java.base/java/util/stream/TerminalSink.class",
            "/modules/java.base/java/util/stream/ReduceOps$AccumulatingSink.class",
            "/modules/java.base/java/util/stream/ReduceOps$Box.class",
            "/modules/java.base/java/util/HashMap$KeySpliterator.class",
            "/modules/java.base/java/util/function/Predicate.class",
            "/modules/java.base/java/util/stream/ReferencePipeline$StatelessOp.class",
            "/modules/java.base/java/util/stream/Sink$ChainedReference.class",
            "/modules/java.base/jdk/internal/module/ModuleResolution.class",
            "/modules/java.base/java/util/stream/FindOps.class",
            "/modules/java.base/java/util/stream/FindOps$FindSink.class",
            "/modules/java.base/java/util/stream/FindOps$FindSink$OfRef.class",
            "/modules/java.base/java/util/stream/FindOps$FindOp.class",
            "/modules/java.base/java/util/Spliterators.class",
            "/modules/java.base/java/util/Spliterators$IteratorSpliterator.class",
            "/modules/java.base/java/lang/module/Configuration.class",
            "/modules/java.base/java/lang/module/Resolver.class",
            "/modules/java.base/java/lang/ModuleLayer.class",
            "/modules/java.base/java/util/SequencedSet.class",
            "/modules/java.base/java/util/LinkedHashSet.class",
            "/modules/java.base/java/util/SequencedMap.class",
            "/modules/java.base/java/util/LinkedHashMap.class",
            "/modules/java.base/java/lang/module/ResolvedModule.class",
            "/modules/java.base/jdk/internal/module/ModuleLoaderMap$Mapper.class",
            "/modules/java.base/jdk/internal/loader/AbstractClassLoaderValue$Memoizer.class",
            "/modules/java.base/jdk/internal/module/ServicesCatalog$ServiceProvider.class",
            "/modules/java.base/java/util/concurrent/CopyOnWriteArrayList.class",
            "/modules/java.base/java/lang/ModuleLayer$Controller.class",
            "/modules/java.base/jdk/internal/module/ModuleBootstrap$SafeModuleFinder.class",
            "/modules/java.base/jdk/internal/vm/ContinuationSupport.class",
            "/modules/java.base/jdk/internal/vm/Continuation$Pinned.class",
            "/modules/java.base/sun/launcher/LauncherHelper.class",
            "/modules/java.base/sun/net/util/URLUtil.class",
            "/modules/java.base/jdk/internal/loader/URLClassPath$Loader.class",
            "/modules/java.base/jdk/internal/loader/URLClassPath$FileLoader.class",
            "/modules/java.base/jdk/internal/loader/Resource.class",
            "/modules/java.base/java/io/FileCleanable.class",
            "/modules/java.base/sun/nio/ByteBuffered.class",
            "/modules/java.base/java/security/SecureClassLoader$CodeSourceKey.class",
            "/modules/java.base/java/security/PermissionCollection.class",
            "/modules/java.base/java/security/Permissions.class",
            "/modules/java.base/java/lang/NamedPackage.class",
            "/modules/java.base/jdk/internal/misc/MethodFinder.class",
            "/modules/java.base/java/lang/Readable.class",
            "/modules/java.base/java/nio/CharBuffer.class",
            "/modules/java.base/java/nio/HeapCharBuffer.class",
            "/modules/java.base/java/nio/charset/CoderResult.class",
            "/modules/java.base/java/util/IdentityHashMap$IdentityHashMapIterator.class",
            "/modules/java.base/java/util/IdentityHashMap$KeyIterator.class",
            "/modules/java.base/java/lang/Shutdown.class",
            "/modules/java.base/java/lang/Shutdown$Lock.class");
}
