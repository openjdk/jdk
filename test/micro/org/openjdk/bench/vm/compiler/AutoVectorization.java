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

import java.lang.foreign.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class AutoVectorization {
    @Param({"10000"})
    public static int SIZE;

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

    private MemorySegment aN;
    private MemorySegment bN;
    private MemorySegment rN;

    private static int zeroI = 0;

    private int[] haystackI;

    @Param("42")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        aI = new int[SIZE];
        bI = new int[SIZE];
        rI = new int[SIZE];

        haystackI = new int[SIZE];

        aL = new long[SIZE];
        bL = new long[SIZE];
        rL = new long[SIZE];

        aS = new short[SIZE];
        bS = new short[SIZE];
        rS = new short[SIZE];

        aC = new char[SIZE];
        bC = new char[SIZE];
        rC = new char[SIZE];

        aB = new byte[SIZE];
        bB = new byte[SIZE];
        rB = new byte[SIZE];

        aF = new float[SIZE];
        bF = new float[SIZE];
        rF = new float[SIZE];

        aD = new double[SIZE];
        bD = new double[SIZE];
        rD = new double[SIZE];

        aN = Arena.ofAuto().allocate(SIZE * 8);
        bN = Arena.ofAuto().allocate(SIZE * 8);
        rN = Arena.ofAuto().allocate(SIZE * 8);

        for (int i = 0; i < SIZE; i++) {
            aB[i] = (byte) r.nextInt();
            bB[i] = (byte) r.nextInt();

            aS[i] = (short) r.nextInt();
            bS[i] = (short) r.nextInt();

            aC[i] = (char) r.nextInt();
            bC[i] = (char) r.nextInt();

            aI[i] = r.nextInt();
            bI[i] = r.nextInt();
            haystackI[i] = (i == SIZE/2) ? 42 : 0;

            aL[i] = r.nextLong();
            bL[i] = r.nextLong();

            aF[i] = r.nextFloat();
            bF[i] = r.nextFloat();

            aD[i] = r.nextDouble();
            bD[i] = r.nextDouble();
        }
    }

    // ------------------------------------- ELEMENT-WISE

    // // TODO rm or expand
    // @Benchmark
    // public void elementwiseByteAdd() {
    //     for (int i = 0; i < aB.length; i++) {
    //         rB[i] = (byte)(aB[i] + bB[i]);
    //     }
    // }

    @Benchmark
    public void elementwiseIntAdd() {
        for (int i = 0; i < aI.length; i++) {
            rI[i] = aI[i] + bI[i];
        }
    }

    // ------------------------------------- REDUCTION

    @Benchmark
    public int reductionIntAdd() {
        int sum = 0;
        for (int i = 0; i < aI.length; i++) {
            sum += aI[i];
        }
        return sum;
    }

    @Benchmark
    public int reductionIntAddDotProduct() {
        int sum = 0;
        for (int i = 0; i < aI.length; i++) {
            sum += aI[i] * bI[i];
        }
        return sum;
    }

    @Benchmark
    public int reductionIntAddStrided() {
        int sum = 0;
        for (int i = 0; i < aI.length / 2; i++) {
            sum += aI[i * 2];
        }
        return sum;
    }

    @Benchmark
    public int reductionIntAddStridedDotProduct() {
        int sum = 0;
        for (int i = 0; i < aI.length / 2; i++) {
            sum += aI[i * 2] * bI[i * 2];
        }
        return sum;
    }

    // ------------------------------------- INDEX

    @Benchmark
    public void indexPopulateInt() {
        for (int i = 0; i < aI.length; i++) {
            rI[i] = i;
        }
    }

    @Benchmark
    public void indexSquareInt() {
        for (int i = 0; i < aI.length; i++) {
            rI[i] = i * i;
        }
    }

    // ------------------------------------- OFFSET

    @Benchmark
    public void offsetM002IntAdd() {
        for (int i = 2; i < aI.length; i++) {
            rI[i] += rI[i - 2];
        }
    }

    @Benchmark
    public void offsetM003IntAdd() {
        for (int i = 3; i < aI.length; i++) {
            rI[i] += rI[i - 3];
        }
    }

    @Benchmark
    public void offsetM128IntAdd() {
        for (int i = 128; i < aI.length; i++) {
            rI[i] += rI[i - 128];
        }
    }

    @Benchmark
    public void offsetP002IntAdd() {
        for (int i = 0; i < aI.length - 2; i++) {
            rI[i] += rI[i + 2];
        }
    }

    @Benchmark
    public void offsetP003IntAdd() {
        for (int i = 0; i < aI.length - 3; i++) {
            rI[i] += rI[i + 3];
        }
    }

    @Benchmark
    public void offsetP128IntAdd() {
        for (int i = 0; i < aI.length - 128; i++) {
            rI[i] += rI[i + 128];
        }
    }

    @Benchmark
    public void offsetM002IntAdd2A() {
        for (int i = 2; i < aI.length; i++) {
            rI[i] += aI[i - 2];
        }
    }

    @Benchmark
    public void offsetM003IntAdd2A() {
        for (int i = 3; i < aI.length; i++) {
            rI[i] += aI[i - 3];
        }
    }

    @Benchmark
    public void offsetM128IntAdd2A() {
        for (int i = 128; i < aI.length; i++) {
            rI[i] += aI[i - 128];
        }
    }

    @Benchmark
    public void offsetP002IntAdd2A() {
        for (int i = 0; i < aI.length - 2; i++) {
            rI[i] += aI[i + 2];
        }
    }

    @Benchmark
    public void offsetP003IntAdd2A() {
        for (int i = 0; i < aI.length - 3; i++) {
            rI[i] += aI[i + 3];
        }
    }

    @Benchmark
    public void offsetP128IntAdd2A() {
        for (int i = 0; i < aI.length - 128; i++) {
            rI[i] += aI[i + 128];
        }
    }

    // ------------------------------------- Aliasing Analysis Runtime Check

    @Benchmark
    public void aliasingCopyInt() {
        for (int i = 0; i < aI.length; i++) {
            rI[i] = aI[i];
        }
    }

    @Benchmark
    public void aliasingCopyInvarInt() {
        for (int i = 0; i < aI.length; i++) {
            rI[i] = aI[i + zeroI];
        }
    }

    @Benchmark
    public void aliasingBoxKernelInt() {
        for (int i = 1; i < aI.length - 1; i++) {
            rI[i] = aI[i - 1] + aI[i] + aI[i + 1];
        }
    }

    @Benchmark
    public void aliasingCopyStridedInt() {
        for (int i = 0; i < aI.length / 2; i++) {
            rI[i] = aI[i * 2];
        }
    }

    @Benchmark
    public void aliasingCopyReverseInt() {
        for (int i = 0; i < aI.length; i++) {
            rI[i] = aI[aI.length - i - 1];
        }
    }

    @Benchmark
    public void aliasingCopyReverseIntFloat() {
        for (int i = 0; i < aI.length; i++) {
            rI[i] = (int)aF[aI.length - i - 1];
        }
    }

    // ------------------------------------- MEMORY SEGMENT

    @Benchmark
    public void memorySegmentNativeElementwiseByteIncr() {
        for (long i = 0; i < aN.byteSize(); i++) {
            byte v = rN.get(ValueLayout.JAVA_BYTE, i);
            rN.set(ValueLayout.JAVA_BYTE, i, (byte)(v + 1));
        }
    }

    @Benchmark
    public void memorySegmentNativeElementwiseByteAdd() {
        for (long i = 0; i < aN.byteSize(); i++) {
            byte v1 = aN.get(ValueLayout.JAVA_BYTE, i);
            byte v2 = bN.get(ValueLayout.JAVA_BYTE, i);
            rN.set(ValueLayout.JAVA_BYTE, i, (byte)(v1 + v2));
        }
    }

    // ------------------------------------- HAND UNROLLED

    @Benchmark
    public void handunrolledIntAdd() {
        for (int i = 0; i < aI.length; i+=2) {
            rI[i + 0] = aI[i + 0] + bI[i + 0];
            rI[i + 1] = aI[i + 1] + bI[i + 1];
        }
    }

    // ------------------------------------- CFG / IF-Conversion / Masking

    @Benchmark
    public void ifMaskedCopyInt() {
        for (int i = 0; i < aI.length; i++) {
            if (aI[i] > 0) { rI[i] = bI[i]; }
        }
    }

    @Benchmark
    public void ifDiamondInt() {
        for (int i = 0; i < aI.length; i++) {
            // similar to ifMaskedLoadInt, but all loads and stores are unconditional.
            int aa = aI[i];
            int bb = bI[i];
            rI[i] = (aa > 0) ? (aa + 1) : (bb * 2);
        }
    }

    @Benchmark
    public void ifMaskedLoadInt() {
        for (int i = 0; i < aI.length; i++) {
            // aI and rI are unconditional, but load from bI is conditional.
            int aa = aI[i];
            rI[i] = (aa > 0) ? (aa + 1) : (bI[i] * 2);
        }
    }

    @Benchmark
    public void ifDiamondIntV2() {
        for (int i = 0; i < aI.length; i++) {
            int aa = aI[i];
            rI[i] = (aa > 0) ? (aa + 1) : (aa * 2);
        }
    }

    @Benchmark
    public int ifSearchInt() {
        for (int i = 0; i < aI.length; i++) {
            if (haystackI[i] == 42) { return i; }
        }
        return -1;
    }
}
