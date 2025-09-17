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
 * TODO:
 * - Array, MemorySegment
 * - copy, fill, maybe more. (Array.fill, System.arraycopy)
 * - over various sizes
 * - control: alignment, 4k-aliasing
 * - OptimizeFill
 * - fill: with constant 0, and with some other arbitrary constant/non-constant.
 * - Impact of CompactObjectHeaders
 * - Object array? Which primitives?
 * - -XX:SuperWordAutomaticAlignment=0
 * - Limit unroll factor
 * - Investigate performance on various platforms.
 * - Compare to Graal?
 *
 * TODO:: how to run the test:
 * make test TEST="micro:vm.compiler.VectorBulkOperationsArray.fill_var.*" CONF=linux-x64 TEST_VM_OPTS="-XX:+OptimizeFill -XX:UseAVX=2" MICRO="OPTIONS=-prof perfasm -p NUM_ACCESS_ELEMENTS=10000"
 * make test TEST="micro:vm.compiler.VectorBulkOperationsArray.copy_byte.*" CONF=linux-x64 TEST_VM_OPTS="-XX:+OptimizeFill -XX:UseAVX=2" MICRO="FORK=3;OPTIONS=-prof perfasm -p NUM_ACCESS_ELEMENTS=10000"
 * make test TEST="micro:vm.compiler.VectorBulkOperationsArray.copy_byte.*" CONF=linux-x64 TEST_VM_OPTS="-XX:+OptimizeFill -XX:UseAVX=3 -XX:LoopMaxUnroll=64" MICRO="FORK=3;OPTIONS=-prof perfasm -p NUM_ACCESS_ELEMENTS=10000"
 *
 * TODO: observations
 * - Arrays.fill is sensitive to OptimizeFill, loop version not. That's a little strange, would have expected it to get replaced by stub.
 *   They do use different vector instructions though on AVX512, but same vector length. Maybe it is also the loops of auto-vec that
 *   are more complicated. And more unrolling, maybe too much? Ok, but then if I go to AVX2 it all looks way different, maybe just an artifact.
 * - Similar effect with copy_byte. Wow. There is a lot to be gained here probably.
 * - It also seems that -XX:LoopMaxUnroll=64 is not really repected. I think I've seen this before. It only seems to work before SuperWord, but not after.
 *   Right, but -XX:LoopMaxUnroll=32 is respected ... before SuperWord but then still super-unrolled. Fail.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class VectorBulkOperationsArray {
    @Param({  "0", "8", "256", "10000" })
    // TODO: replace with below
    //// @Param({  "0",  "1",  "2",  "3",  "4",  "5",  "6",  "7",  "8",  "9",
    ////          "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
    ////          "20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
    ////          "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
    ////          "40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
    ////          "50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
    ////          "60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
    ////          "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
    ////          "80", "81", "82", "83", "84", "85", "86", "87", "88", "89",
    ////          "90", "91", "92", "93", "94", "95", "96", "97", "98", "99",
    ////         "100","101","102","103","104","105","106","107","108","109",
    ////         "110","111","112","113","114","115","116","117","118","119",
    ////         "120","121","122","123","124","125","126","127","128","129",
    ////         "130","131","132","133","134","135","136","137","138","139",
    ////         "140","141","142","143","144","145","146","147","148","149",
    ////         "150","151","152","153","154","155","156","157","158","159",
    ////         "160","161","162","163","164","165","166","167","168","169",
    ////         "170","171","172","173","174","175","176","177","178","179",
    ////         "180","181","182","183","184","185","186","187","188","189",
    ////         "190","191","192","193","194","195","196","197","198","199",
    ////         "200","201","202","203","204","205","206","207","208","209",
    ////         "210","211","212","213","214","215","216","217","218","219",
    ////         "220","221","222","223","224","225","226","227","228","229",
    ////         "230","231","232","233","234","235","236","237","238","239",
    ////         "240","241","242","243","244","245","246","247","248","249",
    ////         "250","251","252","253","254","255","256","257","258","259",
    ////         "260","261","262","263","264","265","266","267","268","269",
    ////         "270","271","272","273","274","275","276","277","278","279",
    ////         "280","281","282","283","284","285","286","287","288","289",
    ////         "290","291","292","293","294","295","296","297","298","299",
    ////         "300",
    ////         // Above, the "small loops".
    ////         // Below, some "medium" to "large" loops.
    ////         "1000", "3000", "10000"})
    // Describes how many array elements are accessed, i.e. how many
    // elements we loop over.
    public static int NUM_ACCESS_ELEMENTS;

    // Every array has two regions:
    // - read region
    // - write region
    public static final int REGION_SIZE = 1024 * 32;
    public static final int REGION_2_BYTE_OFFSET = 1024 * 2; // prevent 4k-aliasing

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

    // Number of repetitions, to randomize the offsets.
    public static final int REPETITIONS = 64 * 64;
    private int[] offsets_load;
    private int[] offsets_store;

    @Param("42")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        aB = new byte[2 * REGION_SIZE];
        aC = new char[2 * REGION_SIZE];
        aS = new short[2 * REGION_SIZE];
        aI = new int[2 * REGION_SIZE];
        aL = new long[2 * REGION_SIZE];
        aF = new float[2 * REGION_SIZE];
        aD = new double[2 * REGION_SIZE];

        for (int i = 0; i < REGION_SIZE; i++) {
            aB[i] = (byte) r.nextInt();
            aS[i] = (short) r.nextInt();
            aC[i] = (char) r.nextInt();
            aI[i] = r.nextInt();
            aL[i] = r.nextLong();
            aF[i] = r.nextFloat();
            aD[i] = r.nextDouble();
        }

        offsets_load = new int[REPETITIONS];
        offsets_store = new int[REPETITIONS];
        for (int i = 0; i < REPETITIONS; i++) {
            // Make sure it is predictable and uniform.
            offsets_load[i] = i % 64;
            offsets_store[i] = (i / 64) % 64;
        }
    }

    @Benchmark
    public void fill_zero_byte_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsets_store[r] + REGION_SIZE + REGION_2_BYTE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aB[i + offset_store] = 0;
            }
        }
    }

    @Benchmark
    public void fill_var_byte_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsets_store[r] + REGION_SIZE + REGION_2_BYTE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aB[i + offset_store] = varB;
            }
        }
    }

    @Benchmark
    public void fill_zero_byte_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsets_store[r] + REGION_SIZE + REGION_2_BYTE_OFFSET;
            Arrays.fill(aB, offset_store, offset_store + NUM_ACCESS_ELEMENTS, (byte)0);
        }
    }

    @Benchmark
    public void fill_var_byte_arrays_fill() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_store = offsets_store[r] + REGION_SIZE + REGION_2_BYTE_OFFSET;
            Arrays.fill(aB, offset_store, offset_store + NUM_ACCESS_ELEMENTS, varB);
        }
    }

    @Benchmark
    public void copy_byte_loop() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsets_load[r];
            int offset_store = offsets_store[r] + REGION_SIZE + REGION_2_BYTE_OFFSET;
            for (int i = 0; i < NUM_ACCESS_ELEMENTS; i++) {
                aB[i + offset_store] = aB[i + offset_load];
            }
        }
    }

    @Benchmark
    public void copy_byte_system_arraycopy() {
        for (int r = 0; r < REPETITIONS; r++) {
            int offset_load = offsets_load[r];
            int offset_store = offsets_store[r] + REGION_SIZE + REGION_2_BYTE_OFFSET;
            System.arraycopy(aB, offset_load, aB, offset_store, NUM_ACCESS_ELEMENTS);
        }
    }
}
