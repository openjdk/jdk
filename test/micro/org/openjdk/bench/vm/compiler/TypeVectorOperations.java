/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public abstract class TypeVectorOperations {
    @Param({"512", /* "1024", */ "2048"})
    public int COUNT;

    private boolean[] boolsA;
    private boolean[] boolsB;
    private boolean[] resZ;
    private byte[] bytesA;
    private byte[] bytesB;
    private byte[] resB;
    private short[] shorts;
    private short[] resS;
    private int[] ints;
    private int[] resI;
    private long[] longs;
    private long[] resL;
    private double[] doubles;
    private double[] resD;
    private float[] floats;
    private float[] resF;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        boolsA = new boolean[COUNT];
        boolsB = new boolean[COUNT];
        resZ = new boolean[COUNT];
        bytesA = new byte[COUNT];
        bytesB = new byte[COUNT];
        resB = new byte[COUNT];
        shorts = new short[COUNT];
        resS = new short[COUNT];
        ints = new int[COUNT];
        resI = new int[COUNT];
        longs = new long[COUNT];
        resL = new long[COUNT];
        doubles = new double[COUNT];
        resD = new double[COUNT];
        floats = new float[COUNT];
        resF = new float[COUNT];

        for (int i = 0; i < COUNT; i++) {
            boolsA[i] = r.nextBoolean();
            boolsB[i] = r.nextBoolean();
            shorts[i] = (short) r.nextInt(Short.MAX_VALUE + 1);
            ints[i] = r.nextInt();
            longs[i] = r.nextLong();
            floats[i] = r.nextFloat();
            doubles[i] = r.nextDouble();
        }

        r.nextBytes(bytesA);
        r.nextBytes(bytesB);

    }

    @Benchmark
    public void absB() {
        for (int i = 0; i < COUNT; i++) {
            byte a = bytesA[i];
            resB[i] = (byte) (Math.abs((byte) a));
        }
    }

    @Benchmark
    public void mulB() {
        for (int i = 0; i < COUNT; i++) {
            byte a = bytesA[i];
            byte b = bytesB[i];
            resI[i] = a * b;
        }
    }

    @Benchmark
    public void lShiftB() {
        for (int i = 0; i < COUNT; i++) {
            resB[i] = (byte) (bytesA[i] << 3);
        }
    }

    @Benchmark
    public void rShiftB() {
        for (int i = 0; i < COUNT; i++) {
            resB[i] = (byte) (bytesA[i] >> 3);
        }
    }

    @Benchmark
    public void urShiftB() {
        for (int i = 0; i < COUNT; i++) {
            resB[i] = (byte) (bytesA[i] >>> 3);
        }
    }

    @Benchmark
    public void absS() {
        for (int i = 0; i < COUNT; i++) {
            short a = shorts[i];
            resS[i] = (short) (Math.abs((short) a));
        }
    }

    @Benchmark
    public void absI() {
        for (int i = 0; i < COUNT; i++) {
            int a = ints[i];
            resI[i] = Math.abs(a);
        }
    }

    @Benchmark
    public void mulI() {
        for (int i = 0; i < COUNT; i++) {
            resI[i] = (ints[i] * ints[i]);
        }
    }

    @Benchmark
    public void mulL() {
        for (int i = 0; i < COUNT; i++) {
            resL[i] = (longs[i] * longs[i]);
        }
    }

    @Benchmark
    public void absL() {
        for (int i = 0; i < COUNT; i++) {
            long a = longs[i];
            resL[i] = (long) (Math.abs((long) a));
        }
    }

    @Benchmark
    public void rShiftL() {
        for (int i = 0; i < COUNT; i++) {
            resL[i] = longs[i] >> 3;
        }
    }

    @Benchmark
    public void absF() {
        for (int i = 0; i < COUNT; i++) {
            float a = floats[i];
            resF[i] = (float) (Math.abs(a));
        }
    }

    @Benchmark
    public void negF() {
        for (int i = 0; i < COUNT; i++) {
            resF[i] = -floats[i];
        }
    }

    @Benchmark
    public void absD() {
        for (int i = 0; i < COUNT; i++) {
            double a = doubles[i];
            resD[i] = (double) (Math.abs(a));
        }
    }

    @Benchmark
    public void negD() {
        for (int i = 0; i < COUNT; i++) {
            resD[i] = -doubles[i];
        }
    }

    @Benchmark
    public void convertB2D() {
        for (int i = 0; i < COUNT; i++) {
            resD[i] = (double) bytesA[i];
        }
    }

    @Benchmark
    public void convertB2F() {
        for (int i = 0; i < COUNT; i++) {
            resF[i] = (float) bytesA[i];
        }
    }

    @Benchmark
    public void convertB2L() {
        for (int i = 0; i < COUNT; i++) {
            resL[i] = (long) bytesA[i];
        }
    }

    @Benchmark
    public void convertD2B() {
        for (int i = 0; i < COUNT; i++) {
            resB[i] = (byte) doubles[i];
        }
    }

    @Benchmark
    public void convertD2F() {
        for (int i = 0; i < COUNT; i++) {
            resF[i] = (float) doubles[i];
        }
    }

    @Benchmark
    public void convertD2I() {
        for (int i = 0; i < COUNT; i++) {
            resI[i] = (int) doubles[i];
        }
    }

    @Benchmark
    public void convertD2S() {
        for (int i = 0; i < COUNT; i++) {
            resS[i] = (short) doubles[i];
        }
    }

    @Benchmark
    public void convertD2L() {
        for (int i = 0; i < COUNT; i++) {
            resL[i] = (long) doubles[i];
        }
    }

    @Benchmark
    public void convertF2I() {
        for (int i = 0; i < COUNT; i++) {
            resI[i] = (int) floats[i];
        }
    }

    @Benchmark
    public void convertF2B() {
        for (int i = 0; i < COUNT; i++) {
            resB[i] = (byte) floats[i];
        }
    }

    @Benchmark
    public void convertF2D() {
        for (int i = 0; i < COUNT; i++) {
            resD[i] = (double) floats[i];
        }
    }

    @Benchmark
    public void convertF2L() {
        for (int i = 0; i < COUNT; i++) {
            resL[i] = (long) floats[i];
        }
    }

    @Benchmark
    public void convertF2S() {
        for (int i = 0; i < COUNT; i++) {
            resS[i] = (short) floats[i];
        }
    }

    @Benchmark
    public void convertI2F() {
        for (int i = 0; i < COUNT; i++) {
            resF[i] = (float) ints[i];
        }
    }

    @Benchmark
    public void convertI2D() {
        for (int i = 0; i < COUNT; i++) {
            resD[i] = (double) ints[i];
        }
    }

    @Benchmark
    public void convertI2L() {
        for (int i = 0; i < COUNT; i++) {
            resL[i] = (long) ints[i];
        }
    }

    @Benchmark
    public void convertL2D() {
        for (int i = 0; i < COUNT; i++) {
            resD[i] = (double) longs[i];
        }
    }

    @Benchmark
    public void convertL2B() {
        for (int i = 0; i < COUNT; i++) {
            resB[i] = (byte) longs[i];
        }
    }

    @Benchmark
    public void convertL2F() {
        for (int i = 0; i < COUNT; i++) {
            resF[i] = (float) longs[i];
        }
    }

    @Benchmark
    public void convertL2I() {
        for (int i = 0; i < COUNT; i++) {
            resI[i] = (int) longs[i];
        }
    }

    @Benchmark
    public void convertL2S() {
        for (int i = 0; i < COUNT; i++) {
            resS[i] = (short) longs[i];
        }
    }

    @Benchmark
    public void convertS2D() {
        for (int i = 0; i < COUNT; i++) {
            resD[i] = (double) shorts[i];
        }
    }

    @Benchmark
    public void convertS2F() {
        for (int i = 0; i < COUNT; i++) {
            resF[i] = (float) shorts[i];
        }
    }

    @Benchmark
    public void convertS2L() {
        for (int i = 0; i < COUNT; i++) {
            resL[i] = (long) shorts[i];
        }
    }

    @Benchmark
    public void andZ() {
        for (int i = 0; i < COUNT; i++) {
            resZ[i] = boolsA[i] & boolsB[i];
        }
    }

    @Benchmark
    @Fork(jvmArgs = {"-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov"})
    public void cmoveD() {
        for (int i = 0; i < COUNT; i++) {
            resD[i] = resD[i] < doubles[i] ? resD[i] : doubles[i];
        }
    }

    @Benchmark
    @Fork(jvmArgs = {"-XX:+UseCMoveUnconditionally", "-XX:+UseVectorCmov"})
    public void cmoveF() {
        for (int i = 0; i < COUNT; i++) {
            resF[i] = resF[i] < floats[i] ? resF[i] : floats[i];
        }
    }

    @Fork(value = 2, jvmArgs = {
        "-XX:+UseSuperWord"
    })
    public static class TypeVectorOperationsSuperWord extends TypeVectorOperations {

    }

    @Fork(value = 2, jvmArgs = {
        "-XX:-UseSuperWord"
    })
    public static class TypeVectorOperationsNonSuperWord extends TypeVectorOperations {
    }

}
