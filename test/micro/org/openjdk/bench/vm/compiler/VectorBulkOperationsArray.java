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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.Arrays;

/**
 * This benchmark is here to measure vectorized peformance for some simple bulk operations:
 * - fill
 * - copy
 *
 * We may add more in the future, for example:
 * - comparison
 * - find index
 * - filter
 * - ...
 *
 * One important feature of this benchmark, is that we control for alignment and 4k-aliasing,
 * something almost no benchmarks have considered up to now. But it is important to get more
 * precise, clean and reliable results.
 *
 * Note, you may want to play with "-XX:-OptimizeFill" for the fill benchmarks, so that we do
 * not use the fill-intrinsic, but auto-vectorize. Though I'm currently not seeing a difference,
 * maybe the loop is not recognized properly? Maybe the alignment "randomization" prevents it.
 *
 * Please also look at the companion benchmark:
 *   VectorBulkOperationsMemorySegment.java
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class VectorBulkOperationsArray {
    @Param({  "0",  "1",  "2",  "3",  "4",  "5",  "6",  "7",  "8",  "9",
             "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
             "20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
             "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
             "40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
             "50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
             "60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
             "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
             "80", "81", "82", "83", "84", "85", "86", "87", "88", "89",
             "90", "91", "92", "93", "94", "95", "96", "97", "98", "99",
            "100","101","102","103","104","105","106","107","108","109",
            "110","111","112","113","114","115","116","117","118","119",
            "120","121","122","123","124","125","126","127","128","129",
            "130","131","132","133","134","135","136","137","138","139",
            "140","141","142","143","144","145","146","147","148","149",
            "150","151","152","153","154","155","156","157","158","159",
            "160","161","162","163","164","165","166","167","168","169",
            "170","171","172","173","174","175","176","177","178","179",
            "180","181","182","183","184","185","186","187","188","189",
            "190","191","192","193","194","195","196","197","198","199",
            "200","201","202","203","204","205","206","207","208","209",
            "210","211","212","213","214","215","216","217","218","219",
            "220","221","222","223","224","225","226","227","228","229",
            "230","231","232","233","234","235","236","237","238","239",
            "240","241","242","243","244","245","246","247","248","249",
            "250","251","252","253","254","255","256","257","258","259",
            "260","261","262","263","264","265","266","267","268","269",
            "270","271","272","273","274","275","276","277","278","279",
            "280","281","282","283","284","285","286","287","288","289",
            "290","291","292","293","294","295","296","297","298","299",
            "300"})
    public static int NUM_ACCESS_ELEMENTS;
    // This is just a default to investigate small iteration loops.

    // Every array has two regions:
    // - read region
    // - write region
    // We should make sure that the region is a multiple of 4k, so that the
    // 4k-aliasing prevention trick can work. If we used two arrays, then
    // we would not know what the relative offset is between them, so we could
    // not do anything about the 4k-aliasing effects.
    // Background on 4k-aliasing: many modern CPUs have a store-to-load-forwarding
    // mechanism that speeds up loads if the memory locations that were recently
    // stored to. For this, the CPU needs to check if there is a store in the store
    // buffer with the same address and size. There are various implementations
    // with different performance characteristics on various CPUs. For x86, there
    // is a special mechanism that quickly checks the lowest 12bits of the address,
    // to see if there is a match. If there is a match on the 12bits, we then
    // have to eventually check the rest of the bits. There seems to be something
    // speculative going on, and so if we eventually find that there is a match
    // things are really fast. But if we eventually find that the rest of the
    // bits do not match, we have to abort and redo the load from memory/cache,
    // and that can be slower than if we had gone to memory/cache directly. Hence,
    // if we regularly have matches on the 12bits, but mismatches on the other
    // bits, we can be slower than expected. 12bits of address gives us a cyclic
    // patterns every 4k bytes. So if you load/store with a distance of 4k or
    // a multiple, you can see slower performance than expected, you get a dip/
    // spike in the curve that is a strange artifact. We would like to avoid this
    // in our benchmark. That is why we make sure to have k * 4k + 2k offset
    // between load and store.
    //
    // It would be ince to set REGION_SIZE statically, but we want to keep it rather small if possible,
    // to avoid running out of cache. But it might be quite large if NUM_ACCESS_ELEMENTS is large.
    public static int REGION_SIZE = -1024;
    public static final int REGION_2_BYTE_OFFSET   = 1024 * 2; // prevent 4k-aliasing
    public static final int REGION_2_SHORT_OFFSET  = REGION_2_BYTE_OFFSET / 2;
    public static final int REGION_2_CHAR_OFFSET   = REGION_2_BYTE_OFFSET / 2;
    public static final int REGION_2_INT_OFFSET    = REGION_2_BYTE_OFFSET / 4;
    public static final int REGION_2_LONG_OFFSET   = REGION_2_BYTE_OFFSET / 8;
    public static final int REGION_2_FLOAT_OFFSET  = REGION_2_BYTE_OFFSET / 4;
    public static final int REGION_2_DOUBLE_OFFSET = REGION_2_BYTE_OFFSET / 8;
    // For Objects, it could be 4 or 8 bytes. Dividing by 8 gives us something
    // reasonable for both cases.
    public static final int REGION_2_OBJECT_OFFSET = REGION_2_BYTE_OFFSET / 8;

    // The arrays with the two regions each
    private byte[] aB;
    private short[] aS;
    private char[] aC;
    private int[] aI;
    private long[] aL;
    private float[] aF;
    private double[] aD;

    // Used when we need variable values in fill.
    private byte varB = 42;
    private short varS = 42;
    private char varC = 42;
    private int varI = 42;
    private long varL = 42;
    private float varF = 42;
    private double varD = 42;

    // Classes for Object arrays.
    static class A {
        int x;
        A(int x) {
            this.x = x;
        }
    }
    static class B extends A {
        int y;
        B(int x, int y) {
            super(x);
            this.y = y;
        }
    }
    private A[] aOA;
    private B[] aOB;
    private A varOA = new A(-1);
    private B varOB = new B(-1, -1);

    // Number of repetitions, to randomize the offsets.
    public static final int REPETITIONS = 64 * 64;

    @CompilerControl(CompilerControl.Mode.INLINE)
    public static int offsetLoad(int i) { return i & 63; } // bits 0-7, value from 0-63

    @CompilerControl(CompilerControl.Mode.INLINE)
    public static int offsetStore(int i) { return (i >> 8) & 63; } // bits 8-15, value from 0-63

    @Param("42")
    private int seed;
    private Random r = new Random(seed);

    public static int roundUp4k(int i) {
        return (i + 4*1024-1) & (-4*1024);
    }

    @Setup
    public void init() {
        // Make sure we can fit the longs, and then some whiggle room for alignment.
        REGION_SIZE = roundUp4k(NUM_ACCESS_ELEMENTS * 8 + 1024 + REGION_2_BYTE_OFFSET);
        aB = new byte[2 * REGION_SIZE];
        aC = new char[2 * REGION_SIZE];
        aS = new short[2 * REGION_SIZE];
        aI = new int[2 * REGION_SIZE];
        aL = new long[2 * REGION_SIZE];
        aF = new float[2 * REGION_SIZE];
        aD = new double[2 * REGION_SIZE];
        aOA = new A[2 * REGION_SIZE];
        aOB = new B[2 * REGION_SIZE];

        for (int i = 0; i < 2 * REGION_SIZE; i++) {
            aB[i] = (byte) r.nextInt();
            aS[i] = (short) r.nextInt();
            aC[i] = (char) r.nextInt();
            aI[i] = r.nextInt();
            aL[i] = r.nextLong();
            aF[i] = r.nextFloat();
            aD[i] = r.nextDouble();
            aOA[i] = switch (i % 4) {
                case 0, 1 -> new A(i);
                case 2    -> new B(i, i);
                default   -> null;
            };
            aOB[i] = (i % 3 != 0) ? new B(i, i) : null;
        }
    }

    // -------------------------------- BYTE ------------------------------

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_byte_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_BYTE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aB[i + offset_store] = 0;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_byte_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_BYTE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aB[i + offset_store] = varB;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_byte_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_BYTE_OFFSET;
            Arrays.fill(aB, offset_store, offset_store + NUM_ACCESS_ELEMENTS, (byte)0);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_byte_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_BYTE_OFFSET;
            Arrays.fill(aB, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varB);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_byte_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_BYTE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aB[i + offset_store] = aB[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_byte_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_BYTE_OFFSET;
            System.arraycopy(aB, offset_load, aB, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }

    // -------------------------------- CHAR ------------------------------

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_char_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_CHAR_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aC[i + offset_store] = 0;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_char_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_CHAR_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aC[i + offset_store] = varC;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_char_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_CHAR_OFFSET;
            Arrays.fill(aC, offset_store, offset_store + NUM_ACCESS_ELEMENTS, (char)0);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_char_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_CHAR_OFFSET;
            Arrays.fill(aC, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varC);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_char_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_CHAR_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aC[i + offset_store] = aC[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_char_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_CHAR_OFFSET;
            System.arraycopy(aC, offset_load, aC, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }

    // -------------------------------- SHORT ------------------------------

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_short_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_SHORT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aS[i + offset_store] = 0;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_short_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_SHORT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aS[i + offset_store] = varS;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_short_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_SHORT_OFFSET;
            Arrays.fill(aS, offset_store, offset_store + NUM_ACCESS_ELEMENTS, (short)0);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_short_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_SHORT_OFFSET;
            Arrays.fill(aS, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varS);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_short_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_SHORT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aS[i + offset_store] = aS[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_short_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_SHORT_OFFSET;
            System.arraycopy(aS, offset_load, aS, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }

    // -------------------------------- INT ------------------------------

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_int_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_INT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aI[i + offset_store] = 0;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_int_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_INT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aI[i + offset_store] = varI;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_int_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_INT_OFFSET;
            Arrays.fill(aI, offset_store, offset_store + NUM_ACCESS_ELEMENTS, (int)0);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_int_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_INT_OFFSET;
            Arrays.fill(aI, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varI);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_int_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_INT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aI[i + offset_store] = aI[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_int_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_INT_OFFSET;
            System.arraycopy(aI, offset_load, aI, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }

    // -------------------------------- LONG ------------------------------

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_long_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_LONG_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aL[i + offset_store] = 0;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_long_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_LONG_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aL[i + offset_store] = varL;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_long_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_LONG_OFFSET;
            Arrays.fill(aL, offset_store, offset_store + NUM_ACCESS_ELEMENTS, (long)0);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_long_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_LONG_OFFSET;
            Arrays.fill(aL, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varL);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_long_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_LONG_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aL[i + offset_store] = aL[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_long_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_LONG_OFFSET;
            System.arraycopy(aL, offset_load, aL, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }

    // -------------------------------- FLOAT ------------------------------

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_float_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_FLOAT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aF[i + offset_store] = 0;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_float_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_FLOAT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aF[i + offset_store] = varF;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_float_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_FLOAT_OFFSET;
            Arrays.fill(aF, offset_store, offset_store + NUM_ACCESS_ELEMENTS, (float)0);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_float_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_FLOAT_OFFSET;
            Arrays.fill(aF, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varF);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_float_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_FLOAT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aF[i + offset_store] = aF[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_float_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_FLOAT_OFFSET;
            System.arraycopy(aF, offset_load, aF, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }

    // -------------------------------- DOUBLE ------------------------------

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_double_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_DOUBLE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aD[i + offset_store] = 0;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_double_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_DOUBLE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aD[i + offset_store] = varD;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_zero_double_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_DOUBLE_OFFSET;
            Arrays.fill(aD, offset_store, offset_store + NUM_ACCESS_ELEMENTS, (double)0);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_var_double_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_DOUBLE_OFFSET;
            Arrays.fill(aD, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varD);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_double_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_DOUBLE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aD[i + offset_store] = aD[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_double_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_DOUBLE_OFFSET;
            System.arraycopy(aD, offset_load, aD, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }

    // -------------------------------- OBJECT ------------------------------

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_null_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aOA[i + offset_store] = null;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_A2A_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aOA[i + offset_store] = varOA;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_B2A_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aOA[i + offset_store] = varOB;
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_null_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            Arrays.fill(aOA, offset_store, offset_store + NUM_ACCESS_ELEMENTS, null);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_A2A_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            Arrays.fill(aOA, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varOA);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void fill_B2A_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            Arrays.fill(aOA, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varOB);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_A2A_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aOA[i + offset_store] = aOA[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_B2A_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aOA[i + offset_store] = aOB[i + offset_load];
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_A2A_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            System.arraycopy(aOA, offset_load, aOA, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }

    @Benchmark
    @OperationsPerInvocation(REPETITIONS)
    public void copy_B2A_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsetLoad(r);
            int offset_store = offsetStore(r) + REGION_SIZE + REGION_2_OBJECT_OFFSET;
            System.arraycopy(aOB, offset_load, aOA, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }
}
