/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

// Microbenchmark for aarch64 standalone release stores. Forks each method
// twice so the +/-UseStlrForStandaloneRelease delta shows up in the report.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
public class StlrForStandaloneRelease {

    private static final int N = 1024;

    public int    fInt;
    public long   fLong;
    public Object fRef;

    private static final VarHandle VH_INT;
    private static final VarHandle VH_LONG;
    private static final VarHandle VH_REF;
    private static final VarHandle VH_INT_ARR =
            MethodHandles.arrayElementVarHandle(int[].class);
    private static final VarHandle VH_LONG_ARR =
            MethodHandles.arrayElementVarHandle(long[].class);
    private static final VarHandle VH_REF_ARR =
            MethodHandles.arrayElementVarHandle(Object[].class);

    // Single-slot targets: same address every iteration, store-buffer
    // coalescing may inflate the delta.
    private final int[]    intArr = new int[1];
    private final Object[] refArr = new Object[1];

    // N-slot targets: distinct address every iteration, gives the
    // per-store cost of removing the dmb ish.
    private final int[]    intArrN  = new int[N];
    private final long[]   longArrN = new long[N];
    private final Object[] refArrN  = new Object[N];

    private final Object[] values = new Object[N];

    @SuppressWarnings({"deprecation", "removal"})
    private static final sun.misc.Unsafe SUN_U;
    private static final long OFF_INT;
    private static final long OFF_LONG;
    private static final long OFF_REF;

    static {
        try {
            MethodHandles.Lookup L = MethodHandles.lookup();
            VH_INT  = L.findVarHandle(StlrForStandaloneRelease.class, "fInt",  int.class);
            VH_LONG = L.findVarHandle(StlrForStandaloneRelease.class, "fLong", long.class);
            VH_REF  = L.findVarHandle(StlrForStandaloneRelease.class, "fRef",  Object.class);
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            SUN_U = (sun.misc.Unsafe) f.get(null);
            OFF_INT  = SUN_U.objectFieldOffset(StlrForStandaloneRelease.class.getDeclaredField("fInt"));
            OFF_LONG = SUN_U.objectFieldOffset(StlrForStandaloneRelease.class.getDeclaredField("fLong"));
            OFF_REF  = SUN_U.objectFieldOffset(StlrForStandaloneRelease.class.getDeclaredField("fRef"));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public StlrForStandaloneRelease() {
        for (int i = 0; i < N; i++) values[i] = Integer.valueOf(i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    public void plusStlr_intField() {
        for (int i = 0; i < N; i++) VH_INT.setRelease(this, i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    public void plusStlr_longField() {
        for (int i = 0; i < N; i++) VH_LONG.setRelease(this, (long) i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    public void plusStlr_refField() {
        Object[] v = values;
        for (int i = 0; i < N; i++) VH_REF.setRelease(this, v[i]);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    public void plusStlr_intArray() {
        int[] a = intArr;
        for (int i = 0; i < N; i++) VH_INT_ARR.setRelease(a, 0, i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    public void plusStlr_refArray() {
        Object[] a = refArr;
        Object[] v = values;
        for (int i = 0; i < N; i++) VH_REF_ARR.setRelease(a, 0, v[i]);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    @SuppressWarnings({"deprecation", "removal"})
    public void plusStlr_putOrderedInt() {
        for (int i = 0; i < N; i++) SUN_U.putOrderedInt(this, OFF_INT, i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    @SuppressWarnings({"deprecation", "removal"})
    public void plusStlr_putOrderedLong() {
        for (int i = 0; i < N; i++) SUN_U.putOrderedLong(this, OFF_LONG, (long) i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    @SuppressWarnings({"deprecation", "removal"})
    public void plusStlr_putOrderedObject() {
        Object[] v = values;
        for (int i = 0; i < N; i++) SUN_U.putOrderedObject(this, OFF_REF, v[i]);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    public void plusStlr_intArrayVarying() {
        int[] a = intArrN;
        for (int i = 0; i < N; i++) VH_INT_ARR.setRelease(a, i, i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    public void plusStlr_longArrayVarying() {
        long[] a = longArrN;
        for (int i = 0; i < N; i++) VH_LONG_ARR.setRelease(a, i, (long) i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:+UseStlrForStandaloneRelease"})
    public void plusStlr_refArrayVarying() {
        Object[] a = refArrN;
        Object[] v = values;
        for (int i = 0; i < N; i++) VH_REF_ARR.setRelease(a, i, v[i]);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    public void minusStlr_intField() {
        for (int i = 0; i < N; i++) VH_INT.setRelease(this, i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    public void minusStlr_longField() {
        for (int i = 0; i < N; i++) VH_LONG.setRelease(this, (long) i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    public void minusStlr_refField() {
        Object[] v = values;
        for (int i = 0; i < N; i++) VH_REF.setRelease(this, v[i]);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    public void minusStlr_intArray() {
        int[] a = intArr;
        for (int i = 0; i < N; i++) VH_INT_ARR.setRelease(a, 0, i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    public void minusStlr_refArray() {
        Object[] a = refArr;
        Object[] v = values;
        for (int i = 0; i < N; i++) VH_REF_ARR.setRelease(a, 0, v[i]);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    @SuppressWarnings({"deprecation", "removal"})
    public void minusStlr_putOrderedInt() {
        for (int i = 0; i < N; i++) SUN_U.putOrderedInt(this, OFF_INT, i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    @SuppressWarnings({"deprecation", "removal"})
    public void minusStlr_putOrderedLong() {
        for (int i = 0; i < N; i++) SUN_U.putOrderedLong(this, OFF_LONG, (long) i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    @SuppressWarnings({"deprecation", "removal"})
    public void minusStlr_putOrderedObject() {
        Object[] v = values;
        for (int i = 0; i < N; i++) SUN_U.putOrderedObject(this, OFF_REF, v[i]);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    public void minusStlr_intArrayVarying() {
        int[] a = intArrN;
        for (int i = 0; i < N; i++) VH_INT_ARR.setRelease(a, i, i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    public void minusStlr_longArrayVarying() {
        long[] a = longArrN;
        for (int i = 0; i < N; i++) VH_LONG_ARR.setRelease(a, i, (long) i);
    }

    @Benchmark
    @OperationsPerInvocation(N)
    @Fork(value = 1, jvmArgs = {"-XX:-UseStlrForStandaloneRelease"})
    public void minusStlr_refArrayVarying() {
        Object[] a = refArrN;
        Object[] v = values;
        for (int i = 0; i < N; i++) VH_REF_ARR.setRelease(a, i, v[i]);
    }
}
