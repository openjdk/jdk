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
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import org.openjdk.bench.util.InMemoryJavaCompiler;

import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.NMethod;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 3, jvmArgs = {
    "-XX:+UnlockDiagnosticVMOptions",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+WhiteBoxAPI",
    "-Xbootclasspath/a:lib-test/wb.jar",
    "-XX:CompileCommand=inline,java.lang.String::*",
    "-XX:CompileCommand=dontinline,A::sum",
    "-XX:-UseCodeCacheFlushing",
    "-XX:-TieredCompilation",
    "-XX:+SegmentedCodeCache",
    "-XX:ReservedCodeCacheSize=320m",
    "-XX:InitialCodeCacheSize=320m",
    "-XX:+UseParallelGC",
    "-XX:+PrintCodeCache"
})
public class SparseCodeCache {

    static String num1;
    static String num2;
    static byte[] result;

    private static final int C2_LEVEL = 4;
    private static final String CLASS_NAME = "A";
    private static final String METHOD_TO_COMPILE = "sum";
    private static final String JAVA_CODE = """
        public class A {

            public static void sum(String n1, String n2, byte[] out) {
                if (n1.length() != n2.length()) {
                   throw new IllegalArgumentException("n1.length() != n2.length()");
                }
                final int digitCount = n1.length();
                int carry = 0;
                for (int i = digitCount - 1; i >= 0; --i) {
                    int d1 = n1.charAt(i) - '0';
                    int d2 = n2.charAt(i) - '0';
                    int sum = d1 + d2 + carry;
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

    @State(Scope.Thread)
    public static class ThreadState {
        byte[] result;

        @Setup
        public void setup() {
            result = new byte[num1.length() + 1];
        }
    }

    private static Object WB;

    @Param({"128", "256", "512", "768", "1024"})
    public int activeMethodCount;

    @Param({"1", "32", "64", "128"})
    public int codeRegionCount;

    @Param({"2097152"})
    public int codeRegionSize;

    private Method[] methods = {};

    private static String genNum(Random random, int digitCount) {
        int d;
        do {
            d = random.nextInt(10);
        } while (d == 0);

        StringBuilder numBuilder = new StringBuilder(digitCount);
        numBuilder.append(d);
        for (int i = 1; i < digitCount; ++i) {
            numBuilder.append(random.nextInt(10));
        }
        return numBuilder.toString();
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

    private static ClassLoader createClassLoaderFor(final byte[] byteCode) {
        return new ClassLoader() {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (!name.equals(CLASS_NAME)) {
                    return super.loadClass(name);
                }

                return defineClass(name, byteCode, 0, byteCode.length);
            }
        };
    }

    private static Method compileMethod(byte[] byteCode) throws Exception {
        var cl = createClassLoaderFor(byteCode).loadClass(CLASS_NAME);
        var m = cl.getMethod(METHOD_TO_COMPILE, String.class, String.class, byte[].class);
        m.invoke(null, num1, num2, result);
        getWhiteBox().markMethodProfiled(m);
        getWhiteBox().enqueueMethodForCompilation(m, C2_LEVEL);
        while (getWhiteBox().isMethodQueuedForCompilation(m)) {
            Thread.onSpinWait();
        }
        if (getWhiteBox().getMethodCompilationLevel(m) != C2_LEVEL) {
            throw new IllegalStateException("Method " + m + " is not compiled by C2.");
        }
        return m;
    }

    private void generateCode() throws Exception {
        initNums();

        result = new byte[num1.length() + 1];

        final int methodsPerRegion = activeMethodCount / codeRegionCount;
        if (methodsPerRegion == 0) {
            throw new IllegalArgumentException("activeMethodCount = " + activeMethodCount
                + ",  codeRegionCount = " + codeRegionCount
                + ". 'activeMethodCount' must be greater than or equal to 'codeRegionCount'.");
        }

        if ((codeRegionSize & (codeRegionSize - 1)) != 0) {
            throw new IllegalArgumentException("codeRegionSize = " + codeRegionSize
                + ". 'codeRegionSize' must be a power of 2.");
        }

        int remainingMethods = activeMethodCount % codeRegionCount;
        final byte[] byteCode = InMemoryJavaCompiler.compile(CLASS_NAME, JAVA_CODE);

        if (codeRegionCount > 1) {
            final var m = compileMethod(byteCode);
            final var nmethod = NMethod.get(m, false);

            if (nmethod.size * methodsPerRegion > codeRegionSize) {
                throw new IllegalArgumentException("activeMethodCount = " + activeMethodCount
                        + ", codeRegionSize = " + codeRegionSize
                        + ", methodsPerRegion = " + methodsPerRegion
                        + ", nmethod size = " + nmethod.size
                        + ". One code region does not have enough space to hold " + methodsPerRegion + " nmethods.");
            }

            getWhiteBox().lockCompilation();
            long codeRegionUsed = nmethod.address + nmethod.size - (nmethod.address & ~(codeRegionSize - 1));
            if (codeRegionUsed < codeRegionSize) {
                getWhiteBox().allocateCodeBlob(codeRegionSize - codeRegionUsed, nmethod.code_blob_type.id);
            }
            getWhiteBox().unlockCompilation();
        }

        methods = new Method[activeMethodCount];
        for (int i = 0, j = 0; i < codeRegionCount; ++i) {
            for (int k = 0; k < methodsPerRegion; ++k, ++j) {
                methods[j] = compileMethod(byteCode);
            }
            var firstNmethodInRegion = NMethod.get(methods[j - methodsPerRegion], false);
            if (remainingMethods > 0) {
                methods[j++] = compileMethod(byteCode);
                --remainingMethods;
            }
            getWhiteBox().lockCompilation();
            var regionStart = firstNmethodInRegion.address & ~(codeRegionSize - 1);
            var regionEnd = regionStart + codeRegionSize;
            var lastNmethodInRegion = NMethod.get(methods[j - 1], false);
            if ((lastNmethodInRegion.address + lastNmethodInRegion.size) < regionEnd) {
                getWhiteBox().allocateCodeBlob(regionEnd - lastNmethodInRegion.address - lastNmethodInRegion.size, lastNmethodInRegion.code_blob_type.id);
            }
            getWhiteBox().unlockCompilation();
        }
    }

    @Setup(Level.Trial)
    public void setupCodeCache() throws Exception {
        initWhiteBox();
        generateCode();
    }

    @Benchmark
    public void runMethods(ThreadState s) throws Exception {
        for (var m : methods) {
            m.invoke(null, num1, num2, s.result);
        }
    }
}
