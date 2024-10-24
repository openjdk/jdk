/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public abstract class VectorAlignment {
    @Param({/*"512",  "1024", */  "2048"})
    public int COUNT;

    private int[] aI;
    private int[] bI;
    private int[] rI;

    private long[] aL;
    private long[] bL;
    private long[] rL;

    private short[] aS;
    private short[] bS;
    private short[] rS;

    private char[] aC;
    private char[] bC;
    private char[] rC;

    private byte[] aB;
    private byte[] bB;
    private byte[] rB;

    private float[] aF;
    private float[] bF;
    private float[] rF;

    private double[] aD;
    private double[] bD;
    private double[] rD;


    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        aI = new int[COUNT];
        bI = new int[COUNT];
        rI = new int[COUNT];

        aL = new long[COUNT];
        bL = new long[COUNT];
        rL = new long[COUNT];

        aS = new short[COUNT];
        bS = new short[COUNT];
        rS = new short[COUNT];

        aC = new char[COUNT];
        bC = new char[COUNT];
        rC = new char[COUNT];

        aB = new byte[COUNT];
        bB = new byte[COUNT];
        rB = new byte[COUNT];

        aF = new float[COUNT];
        bF = new float[COUNT];
        rF = new float[COUNT];

        aD = new double[COUNT];
        bD = new double[COUNT];
        rD = new double[COUNT];


        for (int i = 0; i < COUNT; i++) {
            aI[i] = r.nextInt();
            bI[i] = r.nextInt();

            aL[i] = r.nextLong();
            bL[i] = r.nextLong();

            aS[i] = (short) r.nextInt();
            bS[i] = (short) r.nextInt();

            aC[i] = (char) r.nextInt();
            bC[i] = (char) r.nextInt();

            aB[i] = (byte) r.nextInt();
            bB[i] = (byte) r.nextInt();

            aF[i] = r.nextFloat();
            bF[i] = r.nextFloat();

            aD[i] = r.nextDouble();
            bD[i] = r.nextDouble();
        }
    }

    @Benchmark
    // Control: should always vectorize with SuperWord
    public void bench000I_control() {
        for (int i = 0; i < COUNT; i++) {
            // Have multiple MUL operations to make loop compute bound (more compute than load/store)
            rI[i] = aI[i] * aI[i] * aI[i] * aI[i];
        }
    }

    @Benchmark
    public void bench000L_control() {
        for (int i = 0; i < COUNT; i++) {
            rL[i] = aL[i] * aL[i] * aL[i] * aL[i];
        }
    }

    @Benchmark
    public void bench000S_control() {
        for (int i = 0; i < COUNT; i++) {
            rS[i] = (short)(aS[i] * aS[i] * aS[i] * aS[i]);
        }
    }

    @Benchmark
    public void bench000C_control() {
        for (int i = 0; i < COUNT; i++) {
            rC[i] = (char)(aC[i] * aC[i] * aC[i] * aC[i]);
        }
    }

    @Benchmark
    public void bench000B_control() {
        for (int i = 0; i < COUNT; i++) {
            rB[i] = (byte)(aB[i] * aB[i] * aB[i] * aB[i]);
        }
    }

    @Benchmark
    public void bench000F_control() {
        for (int i = 0; i < COUNT; i++) {
            rF[i] = aF[i] * aF[i] * aF[i] * aF[i];
        }
    }

    @Benchmark
    public void bench000D_control() {
        for (int i = 0; i < COUNT; i++) {
            rD[i] = aD[i] * aD[i] * aD[i] * aD[i];
        }
    }

    @Benchmark
    // Control: should always vectorize with SuperWord
    public void bench001_control() {
        for (int i = 0; i < COUNT; i++) {
            // Have multiple MUL operations to make loop compute bound (more compute than load/store)
            rI[i] = aI[i] * aI[i] * aI[i] * aI[i] + bI[i];
        }
    }

    @Benchmark
    // Vectorizes without AlignVector
    public void bench100I_misaligned_load() {
        for (int i = 0; i < COUNT-1; i++) {
            rI[i] = aI[i+1] * aI[i+1] * aI[i+1] * aI[i+1];
        }
    }

    @Benchmark
    public void bench100L_misaligned_load() {
        for (int i = 0; i < COUNT-1; i++) {
            rL[i] = aL[i+1] * aL[i+1] * aL[i+1] * aL[i+1];
        }
    }

    @Benchmark
    public void bench100S_misaligned_load() {
        for (int i = 0; i < COUNT-1; i++) {
            rS[i] = (short)(aS[i+1] * aS[i+1] * aS[i+1] * aS[i+1]);
        }
    }

    @Benchmark
    public void bench100C_misaligned_load() {
        for (int i = 0; i < COUNT-1; i++) {
            rC[i] = (char)(aC[i+1] * aC[i+1] * aC[i+1] * aC[i+1]);
        }
    }


    @Benchmark
    public void bench100B_misaligned_load() {
        for (int i = 0; i < COUNT-1; i++) {
            rB[i] = (byte)(aB[i+1] * aB[i+1] * aB[i+1] * aB[i+1]);
        }
    }

    @Benchmark
    public void bench100F_misaligned_load() {
        for (int i = 0; i < COUNT-1; i++) {
            rF[i] = aF[i+1] * aF[i+1] * aF[i+1] * aF[i+1];
        }
    }

    @Benchmark
    public void bench100D_misaligned_load() {
        for (int i = 0; i < COUNT-1; i++) {
            rD[i] = aD[i+1] * aD[i+1] * aD[i+1] * aD[i+1];
        }
    }

    @Benchmark
    // Only without "Vectorize" (confused by hand-unrolling)
    public void bench200_hand_unrolled_aligned() {
        for (int i = 0; i < COUNT-10; i+=2) {
            rI[i+0] = aI[i+0] * aI[i+0] * aI[i+0] * aI[i+0];
            rI[i+1] = aI[i+1] * aI[i+1] * aI[i+1] * aI[i+1];
        }
    }

    @Benchmark
    // Only with "Vectorize", without we get issues with modulo computation of alignment for bI
    public void bench300_multiple_misaligned_loads() {
        for (int i = 0; i < COUNT-10; i++) {
            rI[i] = aI[i] * aI[i] * aI[i] * aI[i] + bI[i+1];
        }
    }

    @Benchmark
    // Only with "Vectorize", without we may confuse aI[5] with aI[4+1] and pack loads in wrong pack
    public void bench301_multiple_misaligned_loads() {
        for (int i = 0; i < COUNT-10; i++) {
            rI[i] = aI[i] * aI[i] * aI[i] * aI[i] + aI[i+1];
        }
    }

    @Benchmark
    // Only with "Vectorize", without we get mix of aI[i] and a[i-2]
    public void bench302_multiple_misaligned_loads_and_stores() {
        for (int i = 2; i < COUNT; i++) {
            rI[i - 2] = aI[i-2] * aI[i-2] * aI[i-2] * aI[i-2]; // can do this for all iterations
            rI[i] = aI[i] + 3;                                 // before doing this second line
        }
    }

    @Benchmark
    // Currently does not vectorize:
    //   hand-unrolled confuses Vectorize -> adjacent loads not from same original node (not even same line)
    //   multiple unaligned loads confuses non-Vectorize: aI[5+1] confused with aI[4+2] (plus modulo alignment issue)
    public void bench400_hand_unrolled_misaligned() {
        for (int i = 0; i < COUNT-10; i+=2) {
            rI[i+0] = aI[i+1] * aI[i+1] * aI[i+1] * aI[i+1] + aI[i];
            rI[i+1] = aI[i+2] * aI[i+2] * aI[i+2] * aI[i+2] + aI[i+1];
        }
    }

    @Benchmark
    // Currently does not vectorize:
    //   hand-unrolled confuses Vectorize -> adjacent loads not from same original node (not even same line)
    //   non-Vectorize: plus modulo alignment issue
    public void bench401_hand_unrolled_misaligned() {
        for (int i = 0; i < COUNT-10; i+=2) {
            rI[i+0] = aI[i+1] * aI[i+1] * aI[i+1] * aI[i+1] + bI[i];
            rI[i+1] = aI[i+2] * aI[i+2] * aI[i+2] * aI[i+2] + bI[i+1];
        }
    }

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord", "-XX:CompileCommand=Option,*::*,Vectorize"
    })
    public static class VectorAlignmentSuperWordWithVectorize extends VectorAlignment {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord", "-XX:+AlignVector"
    })
    public static class VectorAlignmentSuperWordAlignVector extends VectorAlignment {}

    @Fork(value = 1, jvmArgs = {
        "-XX:+UseSuperWord"
    })
    public static class VectorAlignmentSuperWord extends VectorAlignment {}

    @Fork(value = 1, jvmArgs = {
        "-XX:-UseSuperWord"
    })
    public static class VectorAlignmentNoSuperWord extends VectorAlignment {}
}
