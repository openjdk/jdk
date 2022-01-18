/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.foreign;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeSymbol;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SymbolLookup;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.lookup;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgsAppend = { "--add-modules=jdk.incubator.foreign", "--enable-native-access=ALL-UNNAMED" })
public class QSort extends CLayouts {

    static final CLinker abi = CLinker.systemCLinker();
    static final MethodHandle clib_qsort;
    static final NativeSymbol native_compar;
    static final NativeSymbol panama_upcall_compar;
    static final long jni_upcall_compar;

    static final int[] INPUT = { 5, 3, 2, 7, 8, 12, 1, 7 };
    static final MemorySegment INPUT_SEGMENT;

    static NativeSymbol qsort_addr = abi.lookup("qsort").get();

    static {
        INPUT_SEGMENT = MemorySegment.allocateNative(MemoryLayout.sequenceLayout(INPUT.length, JAVA_INT), ResourceScope.globalScope());
        INPUT_SEGMENT.copyFrom(MemorySegment.ofArray(INPUT));

        System.loadLibrary("QSortJNI");
        jni_upcall_compar = JNICB.makeCB("org/openjdk/bench/jdk/incubator/foreign/QSort", "jni_upcall_compar", "(II)I");

        try {
            clib_qsort = abi.downcallHandle(
                    qsort_addr,
                    FunctionDescriptor.ofVoid(C_POINTER, C_LONG_LONG, C_LONG_LONG, C_POINTER)
            );
            System.loadLibrary("QSort");
            native_compar = SymbolLookup.loaderLookup().lookup("compar").orElseThrow();
            panama_upcall_compar = abi.upcallStub(
                    lookup().findStatic(QSort.class,
                            "panama_upcall_compar",
                            MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class)),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER),
                    ResourceScope.globalScope()
            );
        } catch (ReflectiveOperationException e) {
            throw new BootstrapMethodError(e);
        }
    }

    static native void jni_qsort_optimized(int[] array, long cb);
    static native void jni_qsort_naive(int[] array);

    @FunctionalInterface
    interface JNIComparator {
        int cmp(int e0, int e1);
    }

    static final JNIComparator COMP = QSort::jni_upcall_compar;

    @Benchmark
    public void native_qsort() throws Throwable {
         clib_qsort.invokeExact((Addressable)INPUT_SEGMENT, (long) INPUT.length, JAVA_INT.byteSize(), (Addressable)native_compar);
    }

    @Benchmark
    public void jni_upcall_qsort_optimized() {
        jni_qsort_optimized(INPUT, jni_upcall_compar);
    }

    @Benchmark
    public void jni_upcall_qsort_naive() {
        jni_qsort_naive(INPUT);
    }

    @Benchmark
    public void panama_upcall_qsort() throws Throwable {
        clib_qsort.invokeExact((Addressable)INPUT_SEGMENT, (long) INPUT.length, JAVA_INT.byteSize(), (Addressable)panama_upcall_compar);
    }

    private static int getIntAbsolute(MemoryAddress addr) {
        return addr.get(JAVA_INT, 0);
    }

    static int panama_upcall_compar(MemoryAddress e0, MemoryAddress e1) {
        return Integer.compare(getIntAbsolute(e0), getIntAbsolute(e1));
    }

    static int jni_upcall_compar(int j0, int j1) {
        return Integer.compare(j0, j1);
    }
}
