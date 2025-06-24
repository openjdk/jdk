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
package org.openjdk.bench.vm.compiler.x86;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
// Fix heap size since the StoreN benchmarks are allocating a lot and dependent on GC selection and compressed oop mode.
@Fork(value = 3, jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
@State(Scope.Thread)
public class RedundantLeaPeephole {
    @State(Scope.Thread)
    public class StoreNHelper {
        Object o1;
        Object o2;

        public StoreNHelper(Object o1, Object o2) {
            this.o1 = o1;
            this.o2 = o2;
        }
    }

    @State(Scope.Thread)
    public class StringEqualsHelper {
        private String str;

        public StringEqualsHelper(String str) {
            this.str = str;
        }

        @CompilerControl(CompilerControl.Mode.INLINE)
        public boolean doEquals(String other) {
            return this.str.equals(other);
        }
    }

    private static final int SIZE = 42;
    private static final int SMALL_IDX = 3;
    private static final int BIG_IDX = 33;

    private static final Object O1 = new Object();
    private static final Object O2 = new Object();

    private Object[] arr1 = new Object[SIZE];
    private Object[] arr2 = new Object[SIZE];
    private StoreNHelper[] arrH1 = new StoreNHelper[SIZE];
    private StoreNHelper[] arrH2 = new StoreNHelper[SIZE];

    private StringEqualsHelper strEqHelper = new StringEqualsHelper("foo");

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UseSerialGC"})
    public void benchStoreNRemoveSpillSerial() {
        this.arrH1[SMALL_IDX] = new StoreNHelper(O1, O2);
        this.arrH2[BIG_IDX] = new StoreNHelper(O2, O1);
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UseParallelGC"})
    public void benchStoreNRemoveSpillParallel() {
        this.arrH1[SMALL_IDX] = new StoreNHelper(O1, O2);
        this.arrH2[BIG_IDX] = new StoreNHelper(O2, O1);
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UseSerialGC"})
    public void benchStoreNNoAllocSerial() {
        this.arr1[SMALL_IDX] = O1;
        this.arr1[BIG_IDX] = O2;
        this.arr2[SMALL_IDX] = O1;
        this.arr2[BIG_IDX] = O2;
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UseParallelGC"})
    public void benchStoreNNoAllocParallel() {
        this.arr1[SMALL_IDX] = O1;
        this.arr1[BIG_IDX] = O2;
        this.arr2[SMALL_IDX] = O1;
        this.arr2[BIG_IDX] = O2;
    }

    @Benchmark
    public boolean benchStringEquals() {
        return this.strEqHelper.doEquals("bar");
    }
}
