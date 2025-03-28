/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

package org.openjdk.bench.vm.compiler;

import java.lang.reflect.Method;
import java.util.Random;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import org.openjdk.bench.util.InMemoryJavaCompiler;

import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.NMethod;

/*
 * This benchmark is used to check performance when the code cache is sparse.
 *
 * We use C2 compiler to compile the same Java method multiple times
 * to produce as many code as needed.
 * These compiled methods represent the active methods in the code cache.
 * We split active methods into groups.
 * We put a group into a fixed size code region.
 * We make a code region size aligned.
 * CodeCache becomes sparse when code regions are not fully filled.
 *
 * The benchmark parameters are active method count, group count, and code region size.
 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 3, jvmArgs = {
    "-XX:+UnlockDiagnosticVMOptions",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+WhiteBoxAPI",
    "-Xbootclasspath/a:lib-test/wb.jar",
    "-XX:CompileCommand=dontinline,A::sum",
    "-XX:-UseCodeCacheFlushing",
    "-XX:-TieredCompilation",
    "-XX:+SegmentedCodeCache",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:InitialCodeCacheSize=512m",
    "-XX:+UseSerialGC",
    "-XX:+PrintCodeCache"
})
public class SparseCodeCache {

    private static final int C2_LEVEL = 4;
    private static final int DUMMY_BLOB_SIZE = 1024 * 1024;
    private static final int DUMMY_BLOB_COUNT = 128;

    static byte[] num1;
    static byte[] num2;

    @State(Scope.Thread)
    public static class ThreadState {
        byte[] result;

        @Setup
        public void setup() {
            result = new byte[num1.length + 1];
        }
    }

    private static Object WB;

    @Param({"256", "512", "1024"})
    public int activeMethodCount;

    @Param({"1", "32", "64", "128"})
    public int groupCount;

    @Param({"2097152"})
    public int codeRegionSize;

    private TestMethod[] methods = {};

    private static byte[] genNum(Random random, int digitCount) {
        byte[] num = new byte[digitCount];
        int d;
        do {
            d = random.nextInt(10);
        } while (d == 0);

        num[0] = (byte)d;
        for (int i = 1; i < digitCount; ++i) {
            num[i] = (byte)random.nextInt(10);
        }
        return num;
    }

    private static void initWhiteBox() {
        WB = WhiteBox.getWhiteBox();
    }

    private static void initNums() {
        final long seed = 8374592837465123L;
        Random random = new Random(seed);

        final int digitCount = 40;
        num1 = genNum(random, digitCount);
        num2 = genNum(random, digitCount);
    }

    private static WhiteBox getWhiteBox() {
        return (WhiteBox)WB;
    }

    private static final class TestMethod {
        private static final String CLASS_NAME = "A";
        private static final String METHOD_TO_COMPILE = "sum";
        private static final String JAVA_CODE = """
        public class A {

            public static void sum(byte[] n1, byte[] n2, byte[] out) {
                final int digitCount = n1.length;
                int carry = 0;
                for (int i = digitCount - 1; i >= 0; --i) {
                    int sum = n1[i] + n2[i] + carry;
                    out[i] = (byte)(sum % 10);
                    carry = sum / 10;
                }
                if (carry != 0) {
                    for (int i = digitCount; i > 0; --i) {
                        out[i] = out[i - 1];
                    }
                    out[0] = (byte)carry;
                }
            }
        }""";

        private static final byte[] BYTE_CODE;

        static {
            BYTE_CODE = InMemoryJavaCompiler.compile(CLASS_NAME, JAVA_CODE);
        }

        private final Method method;

        private static ClassLoader createClassLoaderFor() {
            return new ClassLoader() {
                @Override
                public Class<?> loadClass(String name) throws ClassNotFoundException {
                    if (!name.equals(CLASS_NAME)) {
                        return super.loadClass(name);
                    }

                    return defineClass(name, BYTE_CODE, 0, BYTE_CODE.length);
                }
            };
        }

        public TestMethod() throws Exception {
            var cl = createClassLoaderFor().loadClass(CLASS_NAME);
            method = cl.getMethod(METHOD_TO_COMPILE, byte[].class, byte[].class, byte[].class);
            getWhiteBox().testSetDontInlineMethod(method, true);
        }

        public void profile(byte[] num1, byte[] num2, byte[] result) throws Exception {
            method.invoke(null, num1, num2, result);
            getWhiteBox().markMethodProfiled(method);
        }

        public void invoke(byte[] num1, byte[] num2, byte[] result) throws Exception {
            method.invoke(null, num1, num2, result);
        }

        public void compileWithC2() throws Exception {
            getWhiteBox().enqueueMethodForCompilation(method, C2_LEVEL);
            while (getWhiteBox().isMethodQueuedForCompilation(method)) {
                Thread.onSpinWait();
            }
            if (getWhiteBox().getMethodCompilationLevel(method) != C2_LEVEL) {
                throw new IllegalStateException("Method " + method + " is not compiled by C2.");
            }
        }

        public NMethod getNMethod() {
            return NMethod.get(method, false);
        }
    }

    private void generateOneGroupCode() throws Exception {
        byte[] result = new byte[num1.length + 1];

        methods = new TestMethod[activeMethodCount];
        for (int i = 0; i < activeMethodCount; ++i) {
            methods[i] = new TestMethod();
            methods[i].profile(num1, num2, result);
            methods[i].compileWithC2();
        }
        allocateDummyBlobs(DUMMY_BLOB_COUNT, DUMMY_BLOB_SIZE, methods[activeMethodCount - 1].getNMethod().code_blob_type.id);
        compileCallMethods();
    }

    private void allocateDummyBlobs(int count, int size, int codeBlobType) {
        getWhiteBox().lockCompilation();
        for (int i = 0; i < count; i++) {
            var dummyBlob = getWhiteBox().allocateCodeBlob(size, codeBlobType);
            if (dummyBlob == 0) {
                throw new IllegalStateException("Failed to allocate dummy blob.");
            }
        }
        getWhiteBox().unlockCompilation();
    }

    private void generateCode() throws Exception {
        initNums();

        if (groupCount == 1) {
            generateOneGroupCode();
            return;
        }

        final int defaultMethodsPerGroup = activeMethodCount / groupCount;
        if (defaultMethodsPerGroup == 0) {
            throw new IllegalArgumentException("activeMethodCount = " + activeMethodCount
                + ",  groupCount = " + groupCount
                + ". 'activeMethodCount' must be greater than or equal to 'groupCount'.");
        }

        if ((codeRegionSize & (codeRegionSize - 1)) != 0) {
            throw new IllegalArgumentException("codeRegionSize = " + codeRegionSize
                + ". 'codeRegionSize' must be a power of 2.");
        }

        byte[] result = new byte[num1.length + 1];
        methods = new TestMethod[activeMethodCount];
        methods[0] = new TestMethod();
        methods[0].profile(num1, num2, result);
        methods[0].compileWithC2();
        final var nmethod = methods[0].getNMethod();
        if (nmethod.size * defaultMethodsPerGroup > codeRegionSize) {
            throw new IllegalArgumentException("codeRegionSize = " + codeRegionSize
                    + ", methodsPerRegion = " + defaultMethodsPerGroup
                    + ", nmethod size = " + nmethod.size
                    + ". One code region does not have enough space to hold " + defaultMethodsPerGroup + " nmethods.");
        }

        final var codeHeapSize = nmethod.code_blob_type.getSize();
        final var neededSpace = groupCount * codeRegionSize;
        if (neededSpace > codeHeapSize) {
            throw new IllegalArgumentException(nmethod.code_blob_type.sizeOptionName + " = " + codeHeapSize
                    + ". Not enough space to hold " + groupCount + " groups "
                    + "of code region size " + codeRegionSize + ".");
        }

        int j = 1;
        for (; j < defaultMethodsPerGroup; ++j) {
            methods[j] = new TestMethod();
            methods[j].profile(num1, num2, result);
            methods[j].compileWithC2();
        }

        int methodsPerGroup = defaultMethodsPerGroup;
        int remainingMethods = activeMethodCount % groupCount;
        for (int i = 1; i < groupCount; ++i) {
            getWhiteBox().lockCompilation();
            var firstNmethodInPrevGroup = methods[j - methodsPerGroup].getNMethod();
            var regionStart = firstNmethodInPrevGroup.address & ~(codeRegionSize - 1);
            var regionEnd = regionStart + codeRegionSize;
            var lastNmethodInPrevGroup = methods[j - 1].getNMethod();

            // We have disabled code cache flushing. This should guarantee our just compiled
            // not yet used code will not be flushed.
            // Besides our test methods, we don't use a lot of Java methods in this benchmark.
            // This should guarantee that most of code in the code cache is our test methods.
            // If C2 occasionally compiles other methods, it should not affect test methods code placement much.
            // We don't expect a lot of deoptimizations in this benchmark. So we don't expect
            // CodeCache to be fragmented.
            // We assume addresses of our compiled methods and dummy code blobs are in increasing order.
            // Methods compiled during the same iteration are in the same code region.
            if ((lastNmethodInPrevGroup.address + lastNmethodInPrevGroup.size) < regionEnd) {
                var dummyBlob = getWhiteBox().allocateCodeBlob(regionEnd - lastNmethodInPrevGroup.address - lastNmethodInPrevGroup.size,
                                                               lastNmethodInPrevGroup.code_blob_type.id);
                if (dummyBlob == 0) {
                    throw new IllegalStateException("Failed to allocate dummy blob.");
                }
            }
            getWhiteBox().unlockCompilation();

            methodsPerGroup = defaultMethodsPerGroup;
            if (remainingMethods > 0) {
                ++methodsPerGroup;
                --remainingMethods;
            }

            for (int k = 0; k < methodsPerGroup; ++k, ++j) {
                methods[j] = new TestMethod();
                methods[j].profile(num1, num2, result);
                methods[j].compileWithC2();
            }
        }

        allocateDummyBlobs(DUMMY_BLOB_COUNT, DUMMY_BLOB_SIZE, methods[j - 1].getNMethod().code_blob_type.id);
        compileCallMethods();
    }

    private void compileCallMethods() throws Exception {
        var threadState = new ThreadState();
        threadState.setup();
        callMethods(threadState);
        Method method = SparseCodeCache.class.getDeclaredMethod("callMethods", ThreadState.class);
        getWhiteBox().markMethodProfiled(method);
        getWhiteBox().enqueueMethodForCompilation(method, C2_LEVEL);
        while (getWhiteBox().isMethodQueuedForCompilation(method)) {
            Thread.onSpinWait();
        }
        if (getWhiteBox().getMethodCompilationLevel(method) != C2_LEVEL) {
            throw new IllegalStateException("Method SparseCodeCache::callMethods is not compiled by C2.");
        }
        getWhiteBox().testSetDontInlineMethod(method, true);
    }

    @Setup(Level.Trial)
    public void setupCodeCache() throws Exception {
        initWhiteBox();
        generateCode();
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private void callMethods(ThreadState s) throws Exception {
        for (var m : methods) {
            m.invoke(num1, num2, s.result);
        }
    }

    @Benchmark
    @Warmup(iterations = 2)
    public void runMethodsWithReflection(ThreadState s) throws Exception {
        callMethods(s);
    }
}
