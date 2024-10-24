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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public abstract class VectorStoreToLoadForwarding {
    @Param({"10000"})
    public int SIZE;

    public int START = 1000;

    private byte[] aB;
    private short[] aS;
    private int[] aI;
    private long[] aL;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    @Setup
    public void init() {
        aB = new byte[SIZE];
        aS = new short[SIZE];
        aI = new int[SIZE];
        aL = new long[SIZE];

        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)r.nextInt();
            aS[i] = (short)r.nextInt();
            aI[i] = r.nextInt();
            aL[i] = r.nextLong();
        }
    }

    @Benchmark
    public void byte_000() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 0] + 1);
        }
    }

    @Benchmark
    public void byte_001() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 1] + 1);
        }
    }

    @Benchmark
    public void byte_002() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 2] + 1);
        }
    }

    @Benchmark
    public void byte_003() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 3] + 1);
        }
    }

    @Benchmark
    public void byte_004() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 4] + 1);
        }
    }

    @Benchmark
    public void byte_005() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 5] + 1);
        }
    }

    @Benchmark
    public void byte_006() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 6] + 1);
        }
    }

    @Benchmark
    public void byte_007() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 7] + 1);
        }
    }

    @Benchmark
    public void byte_008() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 8] + 1);
        }
    }

    @Benchmark
    public void byte_009() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 9] + 1);
        }
    }

    @Benchmark
    public void byte_010() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 10] + 1);
        }
    }

    @Benchmark
    public void byte_011() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 11] + 1);
        }
    }

    @Benchmark
    public void byte_012() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 12] + 1);
        }
    }

    @Benchmark
    public void byte_013() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 13] + 1);
        }
    }

    @Benchmark
    public void byte_014() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 14] + 1);
        }
    }

    @Benchmark
    public void byte_015() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 15] + 1);
        }
    }

    @Benchmark
    public void byte_016() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 16] + 1);
        }
    }

    @Benchmark
    public void byte_017() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 17] + 1);
        }
    }

    @Benchmark
    public void byte_018() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 18] + 1);
        }
    }

    @Benchmark
    public void byte_019() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 19] + 1);
        }
    }

    @Benchmark
    public void byte_020() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 20] + 1);
        }
    }

    @Benchmark
    public void byte_021() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 21] + 1);
        }
    }

    @Benchmark
    public void byte_022() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 22] + 1);
        }
    }

    @Benchmark
    public void byte_023() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 23] + 1);
        }
    }

    @Benchmark
    public void byte_024() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 24] + 1);
        }
    }

    @Benchmark
    public void byte_025() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 25] + 1);
        }
    }

    @Benchmark
    public void byte_026() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 26] + 1);
        }
    }

    @Benchmark
    public void byte_027() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 27] + 1);
        }
    }

    @Benchmark
    public void byte_028() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 28] + 1);
        }
    }

    @Benchmark
    public void byte_029() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 29] + 1);
        }
    }

    @Benchmark
    public void byte_030() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 30] + 1);
        }
    }

    @Benchmark
    public void byte_031() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 31] + 1);
        }
    }

    @Benchmark
    public void byte_032() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 32] + 1);
        }
    }

    @Benchmark
    public void byte_033() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 33] + 1);
        }
    }

    @Benchmark
    public void byte_034() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 34] + 1);
        }
    }

    @Benchmark
    public void byte_035() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 35] + 1);
        }
    }

    @Benchmark
    public void byte_036() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 36] + 1);
        }
    }

    @Benchmark
    public void byte_037() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 37] + 1);
        }
    }

    @Benchmark
    public void byte_038() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 38] + 1);
        }
    }

    @Benchmark
    public void byte_039() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 39] + 1);
        }
    }

    @Benchmark
    public void byte_040() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 40] + 1);
        }
    }

    @Benchmark
    public void byte_041() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 41] + 1);
        }
    }

    @Benchmark
    public void byte_042() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 42] + 1);
        }
    }

    @Benchmark
    public void byte_043() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 43] + 1);
        }
    }

    @Benchmark
    public void byte_044() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 44] + 1);
        }
    }

    @Benchmark
    public void byte_045() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 45] + 1);
        }
    }

    @Benchmark
    public void byte_046() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 46] + 1);
        }
    }

    @Benchmark
    public void byte_047() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 47] + 1);
        }
    }

    @Benchmark
    public void byte_048() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 48] + 1);
        }
    }

    @Benchmark
    public void byte_049() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 49] + 1);
        }
    }

    @Benchmark
    public void byte_050() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 50] + 1);
        }
    }

    @Benchmark
    public void byte_051() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 51] + 1);
        }
    }

    @Benchmark
    public void byte_052() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 52] + 1);
        }
    }

    @Benchmark
    public void byte_053() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 53] + 1);
        }
    }

    @Benchmark
    public void byte_054() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 54] + 1);
        }
    }

    @Benchmark
    public void byte_055() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 55] + 1);
        }
    }

    @Benchmark
    public void byte_056() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 56] + 1);
        }
    }

    @Benchmark
    public void byte_057() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 57] + 1);
        }
    }

    @Benchmark
    public void byte_058() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 58] + 1);
        }
    }

    @Benchmark
    public void byte_059() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 59] + 1);
        }
    }

    @Benchmark
    public void byte_060() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 60] + 1);
        }
    }

    @Benchmark
    public void byte_061() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 61] + 1);
        }
    }

    @Benchmark
    public void byte_062() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 62] + 1);
        }
    }

    @Benchmark
    public void byte_063() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 63] + 1);
        }
    }

    @Benchmark
    public void byte_064() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 64] + 1);
        }
    }

    @Benchmark
    public void byte_065() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 65] + 1);
        }
    }

    @Benchmark
    public void byte_066() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 66] + 1);
        }
    }

    @Benchmark
    public void byte_067() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 67] + 1);
        }
    }

    @Benchmark
    public void byte_068() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 68] + 1);
        }
    }

    @Benchmark
    public void byte_069() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 69] + 1);
        }
    }

    @Benchmark
    public void byte_070() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 70] + 1);
        }
    }

    @Benchmark
    public void byte_071() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 71] + 1);
        }
    }

    @Benchmark
    public void byte_072() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 72] + 1);
        }
    }

    @Benchmark
    public void byte_073() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 73] + 1);
        }
    }

    @Benchmark
    public void byte_074() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 74] + 1);
        }
    }

    @Benchmark
    public void byte_075() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 75] + 1);
        }
    }

    @Benchmark
    public void byte_076() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 76] + 1);
        }
    }

    @Benchmark
    public void byte_077() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 77] + 1);
        }
    }

    @Benchmark
    public void byte_078() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 78] + 1);
        }
    }

    @Benchmark
    public void byte_079() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 79] + 1);
        }
    }

    @Benchmark
    public void byte_080() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 80] + 1);
        }
    }

    @Benchmark
    public void byte_081() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 81] + 1);
        }
    }

    @Benchmark
    public void byte_082() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 82] + 1);
        }
    }

    @Benchmark
    public void byte_083() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 83] + 1);
        }
    }

    @Benchmark
    public void byte_084() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 84] + 1);
        }
    }

    @Benchmark
    public void byte_085() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 85] + 1);
        }
    }

    @Benchmark
    public void byte_086() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 86] + 1);
        }
    }

    @Benchmark
    public void byte_087() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 87] + 1);
        }
    }

    @Benchmark
    public void byte_088() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 88] + 1);
        }
    }

    @Benchmark
    public void byte_089() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 89] + 1);
        }
    }

    @Benchmark
    public void byte_090() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 90] + 1);
        }
    }

    @Benchmark
    public void byte_091() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 91] + 1);
        }
    }

    @Benchmark
    public void byte_092() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 92] + 1);
        }
    }

    @Benchmark
    public void byte_093() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 93] + 1);
        }
    }

    @Benchmark
    public void byte_094() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 94] + 1);
        }
    }

    @Benchmark
    public void byte_095() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 95] + 1);
        }
    }

    @Benchmark
    public void byte_096() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 96] + 1);
        }
    }

    @Benchmark
    public void byte_097() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 97] + 1);
        }
    }

    @Benchmark
    public void byte_098() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 98] + 1);
        }
    }

    @Benchmark
    public void byte_099() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 99] + 1);
        }
    }

    @Benchmark
    public void byte_100() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 100] + 1);
        }
    }

    @Benchmark
    public void byte_101() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 101] + 1);
        }
    }

    @Benchmark
    public void byte_102() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 102] + 1);
        }
    }

    @Benchmark
    public void byte_103() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 103] + 1);
        }
    }

    @Benchmark
    public void byte_104() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 104] + 1);
        }
    }

    @Benchmark
    public void byte_105() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 105] + 1);
        }
    }

    @Benchmark
    public void byte_106() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 106] + 1);
        }
    }

    @Benchmark
    public void byte_107() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 107] + 1);
        }
    }

    @Benchmark
    public void byte_108() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 108] + 1);
        }
    }

    @Benchmark
    public void byte_109() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 109] + 1);
        }
    }

    @Benchmark
    public void byte_110() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 110] + 1);
        }
    }

    @Benchmark
    public void byte_111() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 111] + 1);
        }
    }

    @Benchmark
    public void byte_112() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 112] + 1);
        }
    }

    @Benchmark
    public void byte_113() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 113] + 1);
        }
    }

    @Benchmark
    public void byte_114() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 114] + 1);
        }
    }

    @Benchmark
    public void byte_115() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 115] + 1);
        }
    }

    @Benchmark
    public void byte_116() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 116] + 1);
        }
    }

    @Benchmark
    public void byte_117() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 117] + 1);
        }
    }

    @Benchmark
    public void byte_118() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 118] + 1);
        }
    }

    @Benchmark
    public void byte_119() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 119] + 1);
        }
    }

    @Benchmark
    public void byte_120() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 120] + 1);
        }
    }

    @Benchmark
    public void byte_121() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 121] + 1);
        }
    }

    @Benchmark
    public void byte_122() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 122] + 1);
        }
    }

    @Benchmark
    public void byte_123() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 123] + 1);
        }
    }

    @Benchmark
    public void byte_124() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 124] + 1);
        }
    }

    @Benchmark
    public void byte_125() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 125] + 1);
        }
    }

    @Benchmark
    public void byte_126() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 126] + 1);
        }
    }

    @Benchmark
    public void byte_127() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 127] + 1);
        }
    }

    @Benchmark
    public void byte_128() {
        for (int i = START; i < SIZE; i++) {
            aB[i] = (byte)(aB[i - 128] + 1);
        }
    }

    @Benchmark
    public void short_000() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 0] + 1);
        }
    }

    @Benchmark
    public void short_001() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 1] + 1);
        }
    }

    @Benchmark
    public void short_002() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 2] + 1);
        }
    }

    @Benchmark
    public void short_003() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 3] + 1);
        }
    }

    @Benchmark
    public void short_004() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 4] + 1);
        }
    }

    @Benchmark
    public void short_005() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 5] + 1);
        }
    }

    @Benchmark
    public void short_006() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 6] + 1);
        }
    }

    @Benchmark
    public void short_007() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 7] + 1);
        }
    }

    @Benchmark
    public void short_008() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 8] + 1);
        }
    }

    @Benchmark
    public void short_009() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 9] + 1);
        }
    }

    @Benchmark
    public void short_010() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 10] + 1);
        }
    }

    @Benchmark
    public void short_011() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 11] + 1);
        }
    }

    @Benchmark
    public void short_012() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 12] + 1);
        }
    }

    @Benchmark
    public void short_013() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 13] + 1);
        }
    }

    @Benchmark
    public void short_014() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 14] + 1);
        }
    }

    @Benchmark
    public void short_015() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 15] + 1);
        }
    }

    @Benchmark
    public void short_016() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 16] + 1);
        }
    }

    @Benchmark
    public void short_017() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 17] + 1);
        }
    }

    @Benchmark
    public void short_018() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 18] + 1);
        }
    }

    @Benchmark
    public void short_019() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 19] + 1);
        }
    }

    @Benchmark
    public void short_020() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 20] + 1);
        }
    }

    @Benchmark
    public void short_021() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 21] + 1);
        }
    }

    @Benchmark
    public void short_022() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 22] + 1);
        }
    }

    @Benchmark
    public void short_023() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 23] + 1);
        }
    }

    @Benchmark
    public void short_024() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 24] + 1);
        }
    }

    @Benchmark
    public void short_025() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 25] + 1);
        }
    }

    @Benchmark
    public void short_026() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 26] + 1);
        }
    }

    @Benchmark
    public void short_027() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 27] + 1);
        }
    }

    @Benchmark
    public void short_028() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 28] + 1);
        }
    }

    @Benchmark
    public void short_029() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 29] + 1);
        }
    }

    @Benchmark
    public void short_030() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 30] + 1);
        }
    }

    @Benchmark
    public void short_031() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 31] + 1);
        }
    }

    @Benchmark
    public void short_032() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 32] + 1);
        }
    }

    @Benchmark
    public void short_033() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 33] + 1);
        }
    }

    @Benchmark
    public void short_034() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 34] + 1);
        }
    }

    @Benchmark
    public void short_035() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 35] + 1);
        }
    }

    @Benchmark
    public void short_036() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 36] + 1);
        }
    }

    @Benchmark
    public void short_037() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 37] + 1);
        }
    }

    @Benchmark
    public void short_038() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 38] + 1);
        }
    }

    @Benchmark
    public void short_039() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 39] + 1);
        }
    }

    @Benchmark
    public void short_040() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 40] + 1);
        }
    }

    @Benchmark
    public void short_041() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 41] + 1);
        }
    }

    @Benchmark
    public void short_042() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 42] + 1);
        }
    }

    @Benchmark
    public void short_043() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 43] + 1);
        }
    }

    @Benchmark
    public void short_044() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 44] + 1);
        }
    }

    @Benchmark
    public void short_045() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 45] + 1);
        }
    }

    @Benchmark
    public void short_046() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 46] + 1);
        }
    }

    @Benchmark
    public void short_047() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 47] + 1);
        }
    }

    @Benchmark
    public void short_048() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 48] + 1);
        }
    }

    @Benchmark
    public void short_049() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 49] + 1);
        }
    }

    @Benchmark
    public void short_050() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 50] + 1);
        }
    }

    @Benchmark
    public void short_051() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 51] + 1);
        }
    }

    @Benchmark
    public void short_052() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 52] + 1);
        }
    }

    @Benchmark
    public void short_053() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 53] + 1);
        }
    }

    @Benchmark
    public void short_054() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 54] + 1);
        }
    }

    @Benchmark
    public void short_055() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 55] + 1);
        }
    }

    @Benchmark
    public void short_056() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 56] + 1);
        }
    }

    @Benchmark
    public void short_057() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 57] + 1);
        }
    }

    @Benchmark
    public void short_058() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 58] + 1);
        }
    }

    @Benchmark
    public void short_059() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 59] + 1);
        }
    }

    @Benchmark
    public void short_060() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 60] + 1);
        }
    }

    @Benchmark
    public void short_061() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 61] + 1);
        }
    }

    @Benchmark
    public void short_062() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 62] + 1);
        }
    }

    @Benchmark
    public void short_063() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 63] + 1);
        }
    }

    @Benchmark
    public void short_064() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 64] + 1);
        }
    }

    @Benchmark
    public void short_065() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 65] + 1);
        }
    }

    @Benchmark
    public void short_066() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 66] + 1);
        }
    }

    @Benchmark
    public void short_067() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 67] + 1);
        }
    }

    @Benchmark
    public void short_068() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 68] + 1);
        }
    }

    @Benchmark
    public void short_069() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 69] + 1);
        }
    }

    @Benchmark
    public void short_070() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 70] + 1);
        }
    }

    @Benchmark
    public void short_071() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 71] + 1);
        }
    }

    @Benchmark
    public void short_072() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 72] + 1);
        }
    }

    @Benchmark
    public void short_073() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 73] + 1);
        }
    }

    @Benchmark
    public void short_074() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 74] + 1);
        }
    }

    @Benchmark
    public void short_075() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 75] + 1);
        }
    }

    @Benchmark
    public void short_076() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 76] + 1);
        }
    }

    @Benchmark
    public void short_077() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 77] + 1);
        }
    }

    @Benchmark
    public void short_078() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 78] + 1);
        }
    }

    @Benchmark
    public void short_079() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 79] + 1);
        }
    }

    @Benchmark
    public void short_080() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 80] + 1);
        }
    }

    @Benchmark
    public void short_081() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 81] + 1);
        }
    }

    @Benchmark
    public void short_082() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 82] + 1);
        }
    }

    @Benchmark
    public void short_083() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 83] + 1);
        }
    }

    @Benchmark
    public void short_084() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 84] + 1);
        }
    }

    @Benchmark
    public void short_085() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 85] + 1);
        }
    }

    @Benchmark
    public void short_086() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 86] + 1);
        }
    }

    @Benchmark
    public void short_087() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 87] + 1);
        }
    }

    @Benchmark
    public void short_088() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 88] + 1);
        }
    }

    @Benchmark
    public void short_089() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 89] + 1);
        }
    }

    @Benchmark
    public void short_090() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 90] + 1);
        }
    }

    @Benchmark
    public void short_091() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 91] + 1);
        }
    }

    @Benchmark
    public void short_092() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 92] + 1);
        }
    }

    @Benchmark
    public void short_093() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 93] + 1);
        }
    }

    @Benchmark
    public void short_094() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 94] + 1);
        }
    }

    @Benchmark
    public void short_095() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 95] + 1);
        }
    }

    @Benchmark
    public void short_096() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 96] + 1);
        }
    }

    @Benchmark
    public void short_097() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 97] + 1);
        }
    }

    @Benchmark
    public void short_098() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 98] + 1);
        }
    }

    @Benchmark
    public void short_099() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 99] + 1);
        }
    }

    @Benchmark
    public void short_100() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 100] + 1);
        }
    }

    @Benchmark
    public void short_101() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 101] + 1);
        }
    }

    @Benchmark
    public void short_102() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 102] + 1);
        }
    }

    @Benchmark
    public void short_103() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 103] + 1);
        }
    }

    @Benchmark
    public void short_104() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 104] + 1);
        }
    }

    @Benchmark
    public void short_105() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 105] + 1);
        }
    }

    @Benchmark
    public void short_106() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 106] + 1);
        }
    }

    @Benchmark
    public void short_107() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 107] + 1);
        }
    }

    @Benchmark
    public void short_108() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 108] + 1);
        }
    }

    @Benchmark
    public void short_109() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 109] + 1);
        }
    }

    @Benchmark
    public void short_110() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 110] + 1);
        }
    }

    @Benchmark
    public void short_111() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 111] + 1);
        }
    }

    @Benchmark
    public void short_112() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 112] + 1);
        }
    }

    @Benchmark
    public void short_113() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 113] + 1);
        }
    }

    @Benchmark
    public void short_114() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 114] + 1);
        }
    }

    @Benchmark
    public void short_115() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 115] + 1);
        }
    }

    @Benchmark
    public void short_116() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 116] + 1);
        }
    }

    @Benchmark
    public void short_117() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 117] + 1);
        }
    }

    @Benchmark
    public void short_118() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 118] + 1);
        }
    }

    @Benchmark
    public void short_119() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 119] + 1);
        }
    }

    @Benchmark
    public void short_120() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 120] + 1);
        }
    }

    @Benchmark
    public void short_121() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 121] + 1);
        }
    }

    @Benchmark
    public void short_122() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 122] + 1);
        }
    }

    @Benchmark
    public void short_123() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 123] + 1);
        }
    }

    @Benchmark
    public void short_124() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 124] + 1);
        }
    }

    @Benchmark
    public void short_125() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 125] + 1);
        }
    }

    @Benchmark
    public void short_126() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 126] + 1);
        }
    }

    @Benchmark
    public void short_127() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 127] + 1);
        }
    }

    @Benchmark
    public void short_128() {
        for (int i = START; i < SIZE; i++) {
            aS[i] = (short)(aS[i - 128] + 1);
        }
    }

    @Benchmark
    public void int_000() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 0] + 1;
        }
    }

    @Benchmark
    public void int_001() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 1] + 1;
        }
    }

    @Benchmark
    public void int_002() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 2] + 1;
        }
    }

    @Benchmark
    public void int_003() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 3] + 1;
        }
    }

    @Benchmark
    public void int_004() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 4] + 1;
        }
    }

    @Benchmark
    public void int_005() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 5] + 1;
        }
    }

    @Benchmark
    public void int_006() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 6] + 1;
        }
    }

    @Benchmark
    public void int_007() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 7] + 1;
        }
    }

    @Benchmark
    public void int_008() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 8] + 1;
        }
    }

    @Benchmark
    public void int_009() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 9] + 1;
        }
    }

    @Benchmark
    public void int_010() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 10] + 1;
        }
    }

    @Benchmark
    public void int_011() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 11] + 1;
        }
    }

    @Benchmark
    public void int_012() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 12] + 1;
        }
    }

    @Benchmark
    public void int_013() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 13] + 1;
        }
    }

    @Benchmark
    public void int_014() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 14] + 1;
        }
    }

    @Benchmark
    public void int_015() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 15] + 1;
        }
    }

    @Benchmark
    public void int_016() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 16] + 1;
        }
    }

    @Benchmark
    public void int_017() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 17] + 1;
        }
    }

    @Benchmark
    public void int_018() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 18] + 1;
        }
    }

    @Benchmark
    public void int_019() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 19] + 1;
        }
    }

    @Benchmark
    public void int_020() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 20] + 1;
        }
    }

    @Benchmark
    public void int_021() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 21] + 1;
        }
    }

    @Benchmark
    public void int_022() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 22] + 1;
        }
    }

    @Benchmark
    public void int_023() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 23] + 1;
        }
    }

    @Benchmark
    public void int_024() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 24] + 1;
        }
    }

    @Benchmark
    public void int_025() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 25] + 1;
        }
    }

    @Benchmark
    public void int_026() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 26] + 1;
        }
    }

    @Benchmark
    public void int_027() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 27] + 1;
        }
    }

    @Benchmark
    public void int_028() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 28] + 1;
        }
    }

    @Benchmark
    public void int_029() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 29] + 1;
        }
    }

    @Benchmark
    public void int_030() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 30] + 1;
        }
    }

    @Benchmark
    public void int_031() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 31] + 1;
        }
    }

    @Benchmark
    public void int_032() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 32] + 1;
        }
    }

    @Benchmark
    public void int_033() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 33] + 1;
        }
    }

    @Benchmark
    public void int_034() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 34] + 1;
        }
    }

    @Benchmark
    public void int_035() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 35] + 1;
        }
    }

    @Benchmark
    public void int_036() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 36] + 1;
        }
    }

    @Benchmark
    public void int_037() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 37] + 1;
        }
    }

    @Benchmark
    public void int_038() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 38] + 1;
        }
    }

    @Benchmark
    public void int_039() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 39] + 1;
        }
    }

    @Benchmark
    public void int_040() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 40] + 1;
        }
    }

    @Benchmark
    public void int_041() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 41] + 1;
        }
    }

    @Benchmark
    public void int_042() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 42] + 1;
        }
    }

    @Benchmark
    public void int_043() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 43] + 1;
        }
    }

    @Benchmark
    public void int_044() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 44] + 1;
        }
    }

    @Benchmark
    public void int_045() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 45] + 1;
        }
    }

    @Benchmark
    public void int_046() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 46] + 1;
        }
    }

    @Benchmark
    public void int_047() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 47] + 1;
        }
    }

    @Benchmark
    public void int_048() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 48] + 1;
        }
    }

    @Benchmark
    public void int_049() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 49] + 1;
        }
    }

    @Benchmark
    public void int_050() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 50] + 1;
        }
    }

    @Benchmark
    public void int_051() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 51] + 1;
        }
    }

    @Benchmark
    public void int_052() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 52] + 1;
        }
    }

    @Benchmark
    public void int_053() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 53] + 1;
        }
    }

    @Benchmark
    public void int_054() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 54] + 1;
        }
    }

    @Benchmark
    public void int_055() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 55] + 1;
        }
    }

    @Benchmark
    public void int_056() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 56] + 1;
        }
    }

    @Benchmark
    public void int_057() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 57] + 1;
        }
    }

    @Benchmark
    public void int_058() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 58] + 1;
        }
    }

    @Benchmark
    public void int_059() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 59] + 1;
        }
    }

    @Benchmark
    public void int_060() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 60] + 1;
        }
    }

    @Benchmark
    public void int_061() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 61] + 1;
        }
    }

    @Benchmark
    public void int_062() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 62] + 1;
        }
    }

    @Benchmark
    public void int_063() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 63] + 1;
        }
    }

    @Benchmark
    public void int_064() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 64] + 1;
        }
    }

    @Benchmark
    public void int_065() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 65] + 1;
        }
    }

    @Benchmark
    public void int_066() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 66] + 1;
        }
    }

    @Benchmark
    public void int_067() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 67] + 1;
        }
    }

    @Benchmark
    public void int_068() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 68] + 1;
        }
    }

    @Benchmark
    public void int_069() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 69] + 1;
        }
    }

    @Benchmark
    public void int_070() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 70] + 1;
        }
    }

    @Benchmark
    public void int_071() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 71] + 1;
        }
    }

    @Benchmark
    public void int_072() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 72] + 1;
        }
    }

    @Benchmark
    public void int_073() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 73] + 1;
        }
    }

    @Benchmark
    public void int_074() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 74] + 1;
        }
    }

    @Benchmark
    public void int_075() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 75] + 1;
        }
    }

    @Benchmark
    public void int_076() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 76] + 1;
        }
    }

    @Benchmark
    public void int_077() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 77] + 1;
        }
    }

    @Benchmark
    public void int_078() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 78] + 1;
        }
    }

    @Benchmark
    public void int_079() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 79] + 1;
        }
    }

    @Benchmark
    public void int_080() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 80] + 1;
        }
    }

    @Benchmark
    public void int_081() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 81] + 1;
        }
    }

    @Benchmark
    public void int_082() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 82] + 1;
        }
    }

    @Benchmark
    public void int_083() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 83] + 1;
        }
    }

    @Benchmark
    public void int_084() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 84] + 1;
        }
    }

    @Benchmark
    public void int_085() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 85] + 1;
        }
    }

    @Benchmark
    public void int_086() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 86] + 1;
        }
    }

    @Benchmark
    public void int_087() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 87] + 1;
        }
    }

    @Benchmark
    public void int_088() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 88] + 1;
        }
    }

    @Benchmark
    public void int_089() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 89] + 1;
        }
    }

    @Benchmark
    public void int_090() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 90] + 1;
        }
    }

    @Benchmark
    public void int_091() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 91] + 1;
        }
    }

    @Benchmark
    public void int_092() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 92] + 1;
        }
    }

    @Benchmark
    public void int_093() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 93] + 1;
        }
    }

    @Benchmark
    public void int_094() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 94] + 1;
        }
    }

    @Benchmark
    public void int_095() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 95] + 1;
        }
    }

    @Benchmark
    public void int_096() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 96] + 1;
        }
    }

    @Benchmark
    public void int_097() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 97] + 1;
        }
    }

    @Benchmark
    public void int_098() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 98] + 1;
        }
    }

    @Benchmark
    public void int_099() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 99] + 1;
        }
    }

    @Benchmark
    public void int_100() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 100] + 1;
        }
    }

    @Benchmark
    public void int_101() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 101] + 1;
        }
    }

    @Benchmark
    public void int_102() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 102] + 1;
        }
    }

    @Benchmark
    public void int_103() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 103] + 1;
        }
    }

    @Benchmark
    public void int_104() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 104] + 1;
        }
    }

    @Benchmark
    public void int_105() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 105] + 1;
        }
    }

    @Benchmark
    public void int_106() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 106] + 1;
        }
    }

    @Benchmark
    public void int_107() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 107] + 1;
        }
    }

    @Benchmark
    public void int_108() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 108] + 1;
        }
    }

    @Benchmark
    public void int_109() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 109] + 1;
        }
    }

    @Benchmark
    public void int_110() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 110] + 1;
        }
    }

    @Benchmark
    public void int_111() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 111] + 1;
        }
    }

    @Benchmark
    public void int_112() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 112] + 1;
        }
    }

    @Benchmark
    public void int_113() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 113] + 1;
        }
    }

    @Benchmark
    public void int_114() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 114] + 1;
        }
    }

    @Benchmark
    public void int_115() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 115] + 1;
        }
    }

    @Benchmark
    public void int_116() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 116] + 1;
        }
    }

    @Benchmark
    public void int_117() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 117] + 1;
        }
    }

    @Benchmark
    public void int_118() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 118] + 1;
        }
    }

    @Benchmark
    public void int_119() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 119] + 1;
        }
    }

    @Benchmark
    public void int_120() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 120] + 1;
        }
    }

    @Benchmark
    public void int_121() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 121] + 1;
        }
    }

    @Benchmark
    public void int_122() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 122] + 1;
        }
    }

    @Benchmark
    public void int_123() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 123] + 1;
        }
    }

    @Benchmark
    public void int_124() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 124] + 1;
        }
    }

    @Benchmark
    public void int_125() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 125] + 1;
        }
    }

    @Benchmark
    public void int_126() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 126] + 1;
        }
    }

    @Benchmark
    public void int_127() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 127] + 1;
        }
    }

    @Benchmark
    public void int_128() {
        for (int i = START; i < SIZE; i++) {
            aI[i] = aI[i - 128] + 1;
        }
    }

    @Benchmark
    public void long_000() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 0] + 1);
        }
    }

    @Benchmark
    public void long_001() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 1] + 1);
        }
    }

    @Benchmark
    public void long_002() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 2] + 1);
        }
    }

    @Benchmark
    public void long_003() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 3] + 1);
        }
    }

    @Benchmark
    public void long_004() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 4] + 1);
        }
    }

    @Benchmark
    public void long_005() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 5] + 1);
        }
    }

    @Benchmark
    public void long_006() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 6] + 1);
        }
    }

    @Benchmark
    public void long_007() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 7] + 1);
        }
    }

    @Benchmark
    public void long_008() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 8] + 1);
        }
    }

    @Benchmark
    public void long_009() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 9] + 1);
        }
    }

    @Benchmark
    public void long_010() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 10] + 1);
        }
    }

    @Benchmark
    public void long_011() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 11] + 1);
        }
    }

    @Benchmark
    public void long_012() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 12] + 1);
        }
    }

    @Benchmark
    public void long_013() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 13] + 1);
        }
    }

    @Benchmark
    public void long_014() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 14] + 1);
        }
    }

    @Benchmark
    public void long_015() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 15] + 1);
        }
    }

    @Benchmark
    public void long_016() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 16] + 1);
        }
    }

    @Benchmark
    public void long_017() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 17] + 1);
        }
    }

    @Benchmark
    public void long_018() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 18] + 1);
        }
    }

    @Benchmark
    public void long_019() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 19] + 1);
        }
    }

    @Benchmark
    public void long_020() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 20] + 1);
        }
    }

    @Benchmark
    public void long_021() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 21] + 1);
        }
    }

    @Benchmark
    public void long_022() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 22] + 1);
        }
    }

    @Benchmark
    public void long_023() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 23] + 1);
        }
    }

    @Benchmark
    public void long_024() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 24] + 1);
        }
    }

    @Benchmark
    public void long_025() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 25] + 1);
        }
    }

    @Benchmark
    public void long_026() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 26] + 1);
        }
    }

    @Benchmark
    public void long_027() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 27] + 1);
        }
    }

    @Benchmark
    public void long_028() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 28] + 1);
        }
    }

    @Benchmark
    public void long_029() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 29] + 1);
        }
    }

    @Benchmark
    public void long_030() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 30] + 1);
        }
    }

    @Benchmark
    public void long_031() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 31] + 1);
        }
    }

    @Benchmark
    public void long_032() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 32] + 1);
        }
    }

    @Benchmark
    public void long_033() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 33] + 1);
        }
    }

    @Benchmark
    public void long_034() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 34] + 1);
        }
    }

    @Benchmark
    public void long_035() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 35] + 1);
        }
    }

    @Benchmark
    public void long_036() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 36] + 1);
        }
    }

    @Benchmark
    public void long_037() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 37] + 1);
        }
    }

    @Benchmark
    public void long_038() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 38] + 1);
        }
    }

    @Benchmark
    public void long_039() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 39] + 1);
        }
    }

    @Benchmark
    public void long_040() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 40] + 1);
        }
    }

    @Benchmark
    public void long_041() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 41] + 1);
        }
    }

    @Benchmark
    public void long_042() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 42] + 1);
        }
    }

    @Benchmark
    public void long_043() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 43] + 1);
        }
    }

    @Benchmark
    public void long_044() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 44] + 1);
        }
    }

    @Benchmark
    public void long_045() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 45] + 1);
        }
    }

    @Benchmark
    public void long_046() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 46] + 1);
        }
    }

    @Benchmark
    public void long_047() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 47] + 1);
        }
    }

    @Benchmark
    public void long_048() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 48] + 1);
        }
    }

    @Benchmark
    public void long_049() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 49] + 1);
        }
    }

    @Benchmark
    public void long_050() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 50] + 1);
        }
    }

    @Benchmark
    public void long_051() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 51] + 1);
        }
    }

    @Benchmark
    public void long_052() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 52] + 1);
        }
    }

    @Benchmark
    public void long_053() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 53] + 1);
        }
    }

    @Benchmark
    public void long_054() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 54] + 1);
        }
    }

    @Benchmark
    public void long_055() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 55] + 1);
        }
    }

    @Benchmark
    public void long_056() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 56] + 1);
        }
    }

    @Benchmark
    public void long_057() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 57] + 1);
        }
    }

    @Benchmark
    public void long_058() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 58] + 1);
        }
    }

    @Benchmark
    public void long_059() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 59] + 1);
        }
    }

    @Benchmark
    public void long_060() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 60] + 1);
        }
    }

    @Benchmark
    public void long_061() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 61] + 1);
        }
    }

    @Benchmark
    public void long_062() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 62] + 1);
        }
    }

    @Benchmark
    public void long_063() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 63] + 1);
        }
    }

    @Benchmark
    public void long_064() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 64] + 1);
        }
    }

    @Benchmark
    public void long_065() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 65] + 1);
        }
    }

    @Benchmark
    public void long_066() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 66] + 1);
        }
    }

    @Benchmark
    public void long_067() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 67] + 1);
        }
    }

    @Benchmark
    public void long_068() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 68] + 1);
        }
    }

    @Benchmark
    public void long_069() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 69] + 1);
        }
    }

    @Benchmark
    public void long_070() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 70] + 1);
        }
    }

    @Benchmark
    public void long_071() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 71] + 1);
        }
    }

    @Benchmark
    public void long_072() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 72] + 1);
        }
    }

    @Benchmark
    public void long_073() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 73] + 1);
        }
    }

    @Benchmark
    public void long_074() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 74] + 1);
        }
    }

    @Benchmark
    public void long_075() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 75] + 1);
        }
    }

    @Benchmark
    public void long_076() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 76] + 1);
        }
    }

    @Benchmark
    public void long_077() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 77] + 1);
        }
    }

    @Benchmark
    public void long_078() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 78] + 1);
        }
    }

    @Benchmark
    public void long_079() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 79] + 1);
        }
    }

    @Benchmark
    public void long_080() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 80] + 1);
        }
    }

    @Benchmark
    public void long_081() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 81] + 1);
        }
    }

    @Benchmark
    public void long_082() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 82] + 1);
        }
    }

    @Benchmark
    public void long_083() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 83] + 1);
        }
    }

    @Benchmark
    public void long_084() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 84] + 1);
        }
    }

    @Benchmark
    public void long_085() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 85] + 1);
        }
    }

    @Benchmark
    public void long_086() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 86] + 1);
        }
    }

    @Benchmark
    public void long_087() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 87] + 1);
        }
    }

    @Benchmark
    public void long_088() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 88] + 1);
        }
    }

    @Benchmark
    public void long_089() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 89] + 1);
        }
    }

    @Benchmark
    public void long_090() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 90] + 1);
        }
    }

    @Benchmark
    public void long_091() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 91] + 1);
        }
    }

    @Benchmark
    public void long_092() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 92] + 1);
        }
    }

    @Benchmark
    public void long_093() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 93] + 1);
        }
    }

    @Benchmark
    public void long_094() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 94] + 1);
        }
    }

    @Benchmark
    public void long_095() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 95] + 1);
        }
    }

    @Benchmark
    public void long_096() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 96] + 1);
        }
    }

    @Benchmark
    public void long_097() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 97] + 1);
        }
    }

    @Benchmark
    public void long_098() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 98] + 1);
        }
    }

    @Benchmark
    public void long_099() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 99] + 1);
        }
    }

    @Benchmark
    public void long_100() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 100] + 1);
        }
    }

    @Benchmark
    public void long_101() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 101] + 1);
        }
    }

    @Benchmark
    public void long_102() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 102] + 1);
        }
    }

    @Benchmark
    public void long_103() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 103] + 1);
        }
    }

    @Benchmark
    public void long_104() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 104] + 1);
        }
    }

    @Benchmark
    public void long_105() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 105] + 1);
        }
    }

    @Benchmark
    public void long_106() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 106] + 1);
        }
    }

    @Benchmark
    public void long_107() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 107] + 1);
        }
    }

    @Benchmark
    public void long_108() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 108] + 1);
        }
    }

    @Benchmark
    public void long_109() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 109] + 1);
        }
    }

    @Benchmark
    public void long_110() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 110] + 1);
        }
    }

    @Benchmark
    public void long_111() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 111] + 1);
        }
    }

    @Benchmark
    public void long_112() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 112] + 1);
        }
    }

    @Benchmark
    public void long_113() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 113] + 1);
        }
    }

    @Benchmark
    public void long_114() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 114] + 1);
        }
    }

    @Benchmark
    public void long_115() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 115] + 1);
        }
    }

    @Benchmark
    public void long_116() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 116] + 1);
        }
    }

    @Benchmark
    public void long_117() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 117] + 1);
        }
    }

    @Benchmark
    public void long_118() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 118] + 1);
        }
    }

    @Benchmark
    public void long_119() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 119] + 1);
        }
    }

    @Benchmark
    public void long_120() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 120] + 1);
        }
    }

    @Benchmark
    public void long_121() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 121] + 1);
        }
    }

    @Benchmark
    public void long_122() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 122] + 1);
        }
    }

    @Benchmark
    public void long_123() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 123] + 1);
        }
    }

    @Benchmark
    public void long_124() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 124] + 1);
        }
    }

    @Benchmark
    public void long_125() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 125] + 1);
        }
    }

    @Benchmark
    public void long_126() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 126] + 1);
        }
    }

    @Benchmark
    public void long_127() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 127] + 1);
        }
    }

    @Benchmark
    public void long_128() {
        for (int i = START; i < SIZE; i++) {
            aL[i] = (long)(aL[i - 128] + 1);
        }
    }

    @Fork(value = 1, jvmArgsPrepend = {
        "-XX:+UseSuperWord"
    })
    public static class VectorStoreToLoadForwardingSuperWord extends VectorStoreToLoadForwarding {}

    @Fork(value = 1, jvmArgsPrepend = {
        "-XX:-UseSuperWord"
    })
    public static class VectorStoreToLoadForwardingNoSuperWord extends VectorStoreToLoadForwarding {}
}
