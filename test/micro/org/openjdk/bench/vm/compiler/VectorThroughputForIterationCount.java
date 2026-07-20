/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

// Note: I commented out the short, char, float and double benchmarks, so it only takes 5h instead of 12h.
// The goal is to track the performance of various loop sizes, and see the effect of pre/post loops.

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public abstract class VectorThroughputForIterationCount {
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
            "300",
            // Above, the "small loops".
            // Below, some "medium" to "large" loops.
            "1000", "3000", "10000"})
    // Number of iterations spent in a loop.
    public static int ITERATION_COUNT;

    // Add enough slack so we can play with offsets / alignment.
    public static int CONTAINER_SIZE = 20_000;

    private byte[] aB;
    private byte[] bB;
    private byte[] rB;

    private short[] aS;
    private short[] bS;
    private short[] rS;

    private char[] aC;
    private char[] bC;
    private char[] rC;

    private int[] aI;
    private int[] bI;
    private int[] rI;

    private long[] aL;
    private long[] bL;
    private long[] rL;

    private float[] aF;
    private float[] bF;
    private float[] rF;

    private double[] aD;
    private double[] bD;
    private double[] rD;

    @Param({"1024"})
    // Number of times we run the loop, possibly with different offsets.
    public static int REPETITIONS;

    @Param({"true", "false"})
    public static boolean RANDOMIZE_OFFSETS;

    @Param({"0"})
    // If RANDOMIZE_OFFSETS is disabled, use this offset:
    public static int FIXED_OFFSET;

    // A different offset for each repetition of the loop. Depending on
    // RANDOMIZE_OFFSETS, the values are random or all FIXED_OFFSET.
    private int[] offsets;

    @Param("42")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        aI = new int[CONTAINER_SIZE];
        bI = new int[CONTAINER_SIZE];
        rI = new int[CONTAINER_SIZE];

        aL = new long[CONTAINER_SIZE];
        bL = new long[CONTAINER_SIZE];
        rL = new long[CONTAINER_SIZE];

        aS = new short[CONTAINER_SIZE];
        bS = new short[CONTAINER_SIZE];
        rS = new short[CONTAINER_SIZE];

        aC = new char[CONTAINER_SIZE];
        bC = new char[CONTAINER_SIZE];
        rC = new char[CONTAINER_SIZE];

        aB = new byte[CONTAINER_SIZE];
        bB = new byte[CONTAINER_SIZE];
        rB = new byte[CONTAINER_SIZE];

        aF = new float[CONTAINER_SIZE];
        bF = new float[CONTAINER_SIZE];
        rF = new float[CONTAINER_SIZE];

        aD = new double[CONTAINER_SIZE];
        bD = new double[CONTAINER_SIZE];
        rD = new double[CONTAINER_SIZE];

        for (int i = 0; i < CONTAINER_SIZE; i++) {
            aB[i] = (byte) r.nextInt();
            bB[i] = (byte) r.nextInt();

            aS[i] = (short) r.nextInt();
            bS[i] = (short) r.nextInt();

            aC[i] = (char) r.nextInt();
            bC[i] = (char) r.nextInt();

            aI[i] = r.nextInt();
            bI[i] = r.nextInt();

            aL[i] = r.nextLong();
            bL[i] = r.nextLong();

            aF[i] = r.nextFloat();
            bF[i] = r.nextFloat();

            aD[i] = r.nextDouble();
            bD[i] = r.nextDouble();
        }

        offsets = new int[REPETITIONS];
        if (RANDOMIZE_OFFSETS) {
            for (int i = 0; i < REPETITIONS; i++) {
                // Make sure it is predictable and uniform.
                offsets[i] = i % 64;
            }
        } else {
            for (int i = 0; i < REPETITIONS; i++) {
                offsets[i] = FIXED_OFFSET;
            }
        }
    }

    @Benchmark
    public void bench001B_aligned_computeBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                // Have multiple MUL operations to make loop compute bound (more compute than load/store)
                rB[i] = (byte)(aB[i] * aB[i] * aB[i] * aB[i]);
            }
        }
    }

    @Benchmark
    public void bench011B_aligned_memoryBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                rB[i] = (byte)(aB[i] + bB[i]);
            }
        }
    }

    @Benchmark
    public void bench021B_unaligned_memoryBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                rB[i] = (byte)(aB[i+1] + bB[i+2]);
            }
        }
    }

//    @Benchmark
//    public void bench002S_aligned_computeBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                // Have multiple MUL operations to make loop compute bound (more compute than load/store)
//                rS[i] = (short)(aS[i] * aS[i] * aS[i] * aS[i]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench012S_aligned_memoryBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                rS[i] = (short)(aS[i] + bS[i]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench022S_unaligned_memoryBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                rS[i] = (short)(aS[i+1] + bS[i+2]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench003C_aligned_computeBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                // Have multiple MUL operations to make loop compute bound (more compute than load/store)
//                rC[i] = (char)(aC[i] * aC[i] * aC[i] * aC[i]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench013C_aligned_memoryBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                rC[i] = (char)(aC[i] + bC[i]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench023C_unaligned_memoryBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                rC[i] = (char)(aC[i+1] + bC[i+2]);
//            }
//        }
//    }

    @Benchmark
    public void bench004I_aligned_computeBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                // Have multiple MUL operations to make loop compute bound (more compute than load/store)
                rI[i] = (int)(aI[i] * aI[i] * aI[i] * aI[i]);
            }
        }
    }

    @Benchmark
    public void bench014I_aligned_memoryBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                rI[i] = (int)(aI[i] + bI[i]);
            }
        }
    }

    @Benchmark
    public void bench024I_unaligned_memoryBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                rI[i] = (int)(aI[i+1] + bI[i+2]);
            }
        }
    }

    @Benchmark
    public void bench005L_aligned_computeBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                // Have multiple MUL operations to make loop compute bound (more compute than load/store)
                rL[i] = (long)(aL[i] * aL[i] * aL[i] * aL[i]);
            }
        }
    }

    @Benchmark
    public void bench015L_aligned_memoryBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                rL[i] = (long)(aL[i] + bL[i]);
            }
        }
    }

    @Benchmark
    public void bench025L_unaligned_memoryBound() {
        for (int r = 0; r < REPETITIONS; r++) {
            int init = offsets[r];
            int limit = init + ITERATION_COUNT;
            for (int i = init; i < limit; i++) {
                rL[i] = (long)(aL[i+1] + bL[i+2]);
            }
        }
    }

//    @Benchmark
//    public void bench006F_aligned_computeBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                // Have multiple MUL operations to make loop compute bound (more compute than load/store)
//                rF[i] = (float)(aF[i] * aF[i] * aF[i] * aF[i]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench016F_aligned_memoryBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                rF[i] = (float)(aF[i] + bF[i]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench026F_unaligned_memoryBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                rF[i] = (float)(aF[i+1] + bF[i+2]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench007D_aligned_computeBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                // Have multiple MUL operations to make loop compute bound (more compute than load/store)
//                rD[i] = (double)(aD[i] * aD[i] * aD[i] * aD[i]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench017D_aligned_memoryBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                rD[i] = (double)(aD[i] + bD[i]);
//            }
//        }
//    }
//
//    @Benchmark
//    public void bench027D_unaligned_memoryBound() {
//        for (int r = 0; r < REPETITIONS; r++) {
//            int init = offsets[r];
//            int limit = init + ITERATION_COUNT;
//            for (int i = init; i < limit; i++) {
//                rD[i] = (double)(aD[i+1] + bD[i+2]);
//            }
//        }
//    }

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord"
    })
    public static class SuperWord extends VectorThroughputForIterationCount {}
}
