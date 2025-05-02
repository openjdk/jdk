/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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
package org.openjdk.bench.jdk.incubator.vector;

import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorExtractBenchmark {
    private int idx = 0;
    private boolean[] res = new boolean[8];

    private static final VectorMask bmask64 = VectorMask.fromLong(ByteVector.SPECIES_64, 1L);
    private static final VectorMask bmask128 = VectorMask.fromLong(ByteVector.SPECIES_128, 1L);
    private static final VectorMask bmask256 = VectorMask.fromLong(ByteVector.SPECIES_256, 1L);
    private static final VectorMask bmask512 = VectorMask.fromLong(ByteVector.SPECIES_512, 1L);

    private static final VectorMask smask64 = VectorMask.fromLong(ShortVector.SPECIES_64, 1L);
    private static final VectorMask smask128 = VectorMask.fromLong(ShortVector.SPECIES_128, 1L);
    private static final VectorMask smask256 = VectorMask.fromLong(ShortVector.SPECIES_256, 1L);
    private static final VectorMask smask512 = VectorMask.fromLong(ShortVector.SPECIES_512, 1L);

    private static final VectorMask imask64 = VectorMask.fromLong(IntVector.SPECIES_64, 1L);
    private static final VectorMask imask128 = VectorMask.fromLong(IntVector.SPECIES_128, 1L);
    private static final VectorMask imask256 = VectorMask.fromLong(IntVector.SPECIES_256, 1L);
    private static final VectorMask imask512 = VectorMask.fromLong(IntVector.SPECIES_512, 1L);

    private static final VectorMask lmask64 = VectorMask.fromLong(LongVector.SPECIES_64, 1L);
    private static final VectorMask lmask128 = VectorMask.fromLong(LongVector.SPECIES_128, 1L);
    private static final VectorMask lmask256 = VectorMask.fromLong(LongVector.SPECIES_256, 1L);
    private static final VectorMask lmask512 = VectorMask.fromLong(LongVector.SPECIES_512, 1L);

    private static final VectorMask fmask64 = VectorMask.fromLong(FloatVector.SPECIES_64, 1L);
    private static final VectorMask fmask128 = VectorMask.fromLong(FloatVector.SPECIES_128, 1L);
    private static final VectorMask fmask256 = VectorMask.fromLong(FloatVector.SPECIES_256, 1L);
    private static final VectorMask fmask512 = VectorMask.fromLong(FloatVector.SPECIES_512, 1L);

    private static final VectorMask dmask64 = VectorMask.fromLong(DoubleVector.SPECIES_64, 1L);
    private static final VectorMask dmask128 = VectorMask.fromLong(DoubleVector.SPECIES_128, 1L);
    private static final VectorMask dmask256 = VectorMask.fromLong(DoubleVector.SPECIES_256, 1L);
    private static final VectorMask dmask512 = VectorMask.fromLong(DoubleVector.SPECIES_512, 1L);

    @Benchmark
    public boolean[] microMaskLaneIsSetByte64_con() {
        res[0] = bmask64.laneIsSet(0);
        res[1] = bmask64.laneIsSet(1);
        res[2] = bmask64.laneIsSet(2);
        res[3] = bmask64.laneIsSet(3);
        res[4] = bmask64.laneIsSet(4);
        res[5] = bmask64.laneIsSet(5);
        res[6] = bmask64.laneIsSet(6);
        res[7] = bmask64.laneIsSet(7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetByte64_var() {
        res[0] = bmask64.laneIsSet(idx);
        res[1] = bmask64.laneIsSet(idx + 1);
        res[2] = bmask64.laneIsSet(idx + 2);
        res[3] = bmask64.laneIsSet(idx + 3);
        res[4] = bmask64.laneIsSet(idx + 4);
        res[5] = bmask64.laneIsSet(idx + 5);
        res[6] = bmask64.laneIsSet(idx + 6);
        res[7] = bmask64.laneIsSet(idx + 7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetByte128_con() {
        res[0] = bmask128.laneIsSet(0);
        res[1] = bmask128.laneIsSet(1);
        res[2] = bmask128.laneIsSet(2);
        res[3] = bmask128.laneIsSet(3);
        res[4] = bmask128.laneIsSet(12);
        res[5] = bmask128.laneIsSet(13);
        res[6] = bmask128.laneIsSet(14);
        res[7] = bmask128.laneIsSet(15);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetByte128_var() {
        res[0] = bmask128.laneIsSet(idx);
        res[1] = bmask128.laneIsSet(idx + 1);
        res[2] = bmask128.laneIsSet(idx + 2);
        res[3] = bmask128.laneIsSet(idx + 3);
        res[4] = bmask128.laneIsSet(idx + 12);
        res[5] = bmask128.laneIsSet(idx + 13);
        res[6] = bmask128.laneIsSet(idx + 14);
        res[7] = bmask128.laneIsSet(idx + 15);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetByte256_con() {
        res[0] = bmask256.laneIsSet(0);
        res[1] = bmask256.laneIsSet(1);
        res[2] = bmask256.laneIsSet(2);
        res[3] = bmask256.laneIsSet(3);
        res[4] = bmask256.laneIsSet(28);
        res[5] = bmask256.laneIsSet(29);
        res[6] = bmask256.laneIsSet(30);
        res[7] = bmask256.laneIsSet(31);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetByte256_var() {
        res[0] = bmask256.laneIsSet(idx);
        res[1] = bmask256.laneIsSet(idx + 1);
        res[2] = bmask256.laneIsSet(idx + 2);
        res[3] = bmask256.laneIsSet(idx + 3);
        res[4] = bmask256.laneIsSet(idx + 28);
        res[5] = bmask256.laneIsSet(idx + 29);
        res[6] = bmask256.laneIsSet(idx + 30);
        res[7] = bmask256.laneIsSet(idx + 31);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetByte512_con() {
        res[0] = bmask512.laneIsSet(0);
        res[1] = bmask512.laneIsSet(1);
        res[2] = bmask512.laneIsSet(2);
        res[3] = bmask512.laneIsSet(3);
        res[4] = bmask512.laneIsSet(60);
        res[5] = bmask512.laneIsSet(61);
        res[6] = bmask512.laneIsSet(62);
        res[7] = bmask512.laneIsSet(63);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetByte512_var() {
        res[0] = bmask512.laneIsSet(idx);
        res[1] = bmask512.laneIsSet(idx + 1);
        res[2] = bmask512.laneIsSet(idx + 2);
        res[3] = bmask512.laneIsSet(idx + 3);
        res[4] = bmask512.laneIsSet(idx + 60);
        res[5] = bmask512.laneIsSet(idx + 61);
        res[6] = bmask512.laneIsSet(idx + 62);
        res[7] = bmask512.laneIsSet(idx + 63);
        return res;
    }


    @Benchmark
    public boolean[] microMaskLaneIsSetShort64_con() {
        res[0] = smask64.laneIsSet(0);
        res[1] = smask64.laneIsSet(1);
        res[2] = smask64.laneIsSet(2);
        res[3] = smask64.laneIsSet(3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetShort64_var() {
        res[0] = smask64.laneIsSet(idx);
        res[1] = smask64.laneIsSet(idx + 1);
        res[2] = smask64.laneIsSet(idx + 2);
        res[3] = smask64.laneIsSet(idx + 3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetShort128_con() {
        res[0] = smask128.laneIsSet(0);
        res[1] = smask128.laneIsSet(1);
        res[2] = smask128.laneIsSet(2);
        res[3] = smask128.laneIsSet(3);
        res[4] = smask128.laneIsSet(4);
        res[5] = smask128.laneIsSet(5);
        res[6] = smask128.laneIsSet(6);
        res[7] = smask128.laneIsSet(7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetShort128_var() {
        res[0] = smask128.laneIsSet(idx);
        res[1] = smask128.laneIsSet(idx + 1);
        res[2] = smask128.laneIsSet(idx + 2);
        res[3] = smask128.laneIsSet(idx + 3);
        res[4] = smask128.laneIsSet(idx + 4);
        res[5] = smask128.laneIsSet(idx + 5);
        res[6] = smask128.laneIsSet(idx + 6);
        res[7] = smask128.laneIsSet(idx + 7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetShort256_con() {
        res[0] = smask256.laneIsSet(0);
        res[1] = smask256.laneIsSet(1);
        res[2] = smask256.laneIsSet(2);
        res[3] = smask256.laneIsSet(3);
        res[4] = smask256.laneIsSet(12);
        res[5] = smask256.laneIsSet(13);
        res[6] = smask256.laneIsSet(14);
        res[7] = smask256.laneIsSet(15);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetShort256_var() {
        res[0] = smask256.laneIsSet(idx);
        res[1] = smask256.laneIsSet(idx + 1);
        res[2] = smask256.laneIsSet(idx + 2);
        res[3] = smask256.laneIsSet(idx + 3);
        res[4] = smask256.laneIsSet(idx + 12);
        res[5] = smask256.laneIsSet(idx + 13);
        res[6] = smask256.laneIsSet(idx + 14);
        res[7] = smask256.laneIsSet(idx + 15);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetShort512_con() {
        res[0] = smask512.laneIsSet(0);
        res[1] = smask512.laneIsSet(1);
        res[2] = smask512.laneIsSet(2);
        res[3] = smask512.laneIsSet(3);
        res[4] = smask512.laneIsSet(28);
        res[5] = smask512.laneIsSet(29);
        res[6] = smask512.laneIsSet(30);
        res[7] = smask512.laneIsSet(31);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetShort512_var() {
        res[0] = smask512.laneIsSet(idx);
        res[1] = smask512.laneIsSet(idx + 1);
        res[2] = smask512.laneIsSet(idx + 2);
        res[3] = smask512.laneIsSet(idx + 3);
        res[4] = smask512.laneIsSet(idx + 28);
        res[5] = smask512.laneIsSet(idx + 29);
        res[6] = smask512.laneIsSet(idx + 30);
        res[7] = smask512.laneIsSet(idx + 31);
        return res;
    }


    @Benchmark
    public boolean[] microMaskLaneIsSetInt64_con() {
        res[0] = imask64.laneIsSet(0);
        res[1] = imask64.laneIsSet(1);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetInt64_var() {
        res[0] = imask64.laneIsSet(idx);
        res[1] = imask64.laneIsSet(idx + 1);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetInt128_con() {
        res[0] = imask128.laneIsSet(0);
        res[1] = imask128.laneIsSet(1);
        res[2] = imask128.laneIsSet(2);
        res[3] = imask128.laneIsSet(3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetInt128_var() {
        res[0] = imask128.laneIsSet(idx);
        res[1] = imask128.laneIsSet(idx + 1);
        res[2] = imask128.laneIsSet(idx + 2);
        res[3] = imask128.laneIsSet(idx + 3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetInt256_con() {
        res[0] = imask256.laneIsSet(0);
        res[1] = imask256.laneIsSet(1);
        res[2] = imask256.laneIsSet(2);
        res[3] = imask256.laneIsSet(3);
        res[4] = imask256.laneIsSet(4);
        res[5] = imask256.laneIsSet(5);
        res[6] = imask256.laneIsSet(6);
        res[7] = imask256.laneIsSet(7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetInt256_var() {
        res[0] = imask256.laneIsSet(idx);
        res[1] = imask256.laneIsSet(idx + 1);
        res[2] = imask256.laneIsSet(idx + 2);
        res[3] = imask256.laneIsSet(idx + 3);
        res[4] = imask256.laneIsSet(idx + 4);
        res[5] = imask256.laneIsSet(idx + 5);
        res[6] = imask256.laneIsSet(idx + 6);
        res[7] = imask256.laneIsSet(idx + 7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetInt512_con() {
        res[0] = imask512.laneIsSet(0);
        res[1] = imask512.laneIsSet(1);
        res[2] = imask512.laneIsSet(2);
        res[3] = imask512.laneIsSet(3);
        res[4] = imask512.laneIsSet(12);
        res[5] = imask512.laneIsSet(13);
        res[6] = imask512.laneIsSet(14);
        res[7] = imask512.laneIsSet(15);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetInt512_var() {
        res[0] = imask512.laneIsSet(idx);
        res[1] = imask512.laneIsSet(idx + 1);
        res[2] = imask512.laneIsSet(idx + 2);
        res[3] = imask512.laneIsSet(idx + 3);
        res[4] = imask512.laneIsSet(idx + 12);
        res[5] = imask512.laneIsSet(idx + 13);
        res[6] = imask512.laneIsSet(idx + 14);
        res[7] = imask512.laneIsSet(idx + 15);
        return res;
    }


    @Benchmark
    public boolean[] microMaskLaneIsSetLong64_con() {
        res[0] = lmask64.laneIsSet(0);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetLong64_var() {
        res[0] = lmask64.laneIsSet(idx);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetLong128_con() {
        res[0] = lmask128.laneIsSet(0);
        res[1] = lmask128.laneIsSet(1);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetLong128_var() {
        res[0] = lmask128.laneIsSet(idx);
        res[1] = lmask128.laneIsSet(idx + 1);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetLong256_con() {
        res[0] = lmask256.laneIsSet(0);
        res[1] = lmask256.laneIsSet(1);
        res[2] = lmask256.laneIsSet(2);
        res[3] = lmask256.laneIsSet(3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetLong256_var() {
        res[0] = lmask256.laneIsSet(idx);
        res[1] = lmask256.laneIsSet(idx + 1);
        res[2] = lmask256.laneIsSet(idx + 2);
        res[3] = lmask256.laneIsSet(idx + 3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetLong512_con() {
        res[0] = lmask512.laneIsSet(0);
        res[1] = lmask512.laneIsSet(1);
        res[2] = lmask512.laneIsSet(2);
        res[3] = lmask512.laneIsSet(3);
        res[4] = lmask512.laneIsSet(4);
        res[5] = lmask512.laneIsSet(5);
        res[6] = lmask512.laneIsSet(6);
        res[7] = lmask512.laneIsSet(7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetLong512_var() {
        res[0] = lmask512.laneIsSet(idx);
        res[1] = lmask512.laneIsSet(idx + 1);
        res[2] = lmask512.laneIsSet(idx + 2);
        res[3] = lmask512.laneIsSet(idx + 3);
        res[4] = lmask512.laneIsSet(idx + 4);
        res[5] = lmask512.laneIsSet(idx + 5);
        res[6] = lmask512.laneIsSet(idx + 6);
        res[7] = lmask512.laneIsSet(idx + 7);
        return res;
    }


    @Benchmark
    public boolean[] microMaskLaneIsSetFloat64_con() {
        res[0] = fmask64.laneIsSet(0);
        res[1] = fmask64.laneIsSet(1);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetFloat64_var() {
        res[0] = fmask64.laneIsSet(idx);
        res[1] = fmask64.laneIsSet(idx + 1);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetFloat128_con() {
        res[0] = fmask128.laneIsSet(0);
        res[1] = fmask128.laneIsSet(1);
        res[2] = fmask128.laneIsSet(2);
        res[3] = fmask128.laneIsSet(3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetFloat128_var() {
        res[0] = fmask128.laneIsSet(idx);
        res[1] = fmask128.laneIsSet(idx + 1);
        res[2] = fmask128.laneIsSet(idx + 2);
        res[3] = fmask128.laneIsSet(idx + 3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetFloat256_con() {
        res[0] = fmask256.laneIsSet(0);
        res[1] = fmask256.laneIsSet(1);
        res[2] = fmask256.laneIsSet(2);
        res[3] = fmask256.laneIsSet(3);
        res[4] = fmask256.laneIsSet(4);
        res[5] = fmask256.laneIsSet(5);
        res[6] = fmask256.laneIsSet(6);
        res[7] = fmask256.laneIsSet(7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetFloat256_var() {
        res[0] = fmask256.laneIsSet(idx);
        res[1] = fmask256.laneIsSet(idx + 1);
        res[2] = fmask256.laneIsSet(idx + 2);
        res[3] = fmask256.laneIsSet(idx + 3);
        res[4] = fmask256.laneIsSet(idx + 4);
        res[5] = fmask256.laneIsSet(idx + 5);
        res[6] = fmask256.laneIsSet(idx + 6);
        res[7] = fmask256.laneIsSet(idx + 7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetFloat512_con() {
        res[0] = fmask512.laneIsSet(0);
        res[1] = fmask512.laneIsSet(1);
        res[2] = fmask512.laneIsSet(2);
        res[3] = fmask512.laneIsSet(3);
        res[4] = fmask512.laneIsSet(12);
        res[5] = fmask512.laneIsSet(13);
        res[6] = fmask512.laneIsSet(14);
        res[7] = fmask512.laneIsSet(15);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetFloat512_var() {
        res[0] = fmask512.laneIsSet(idx);
        res[1] = fmask512.laneIsSet(idx + 1);
        res[2] = fmask512.laneIsSet(idx + 2);
        res[3] = fmask512.laneIsSet(idx + 3);
        res[4] = fmask512.laneIsSet(idx + 12);
        res[5] = fmask512.laneIsSet(idx + 13);
        res[6] = fmask512.laneIsSet(idx + 14);
        res[7] = fmask512.laneIsSet(idx + 15);
        return res;
    }


    @Benchmark
    public boolean[] microMaskLaneIsSetDouble64_con() {
        res[0] = dmask64.laneIsSet(0);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetDouble64_var() {
        res[0] = dmask64.laneIsSet(idx);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetDouble128_con() {
        res[0] = dmask128.laneIsSet(0);
        res[1] = dmask128.laneIsSet(1);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetDouble128_var() {
        res[0] = dmask128.laneIsSet(idx);
        res[1] = dmask128.laneIsSet(idx + 1);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetDouble256_con() {
        res[0] = dmask256.laneIsSet(0);
        res[1] = dmask256.laneIsSet(1);
        res[2] = dmask256.laneIsSet(2);
        res[3] = dmask256.laneIsSet(3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetDouble256_var() {
        res[0] = dmask256.laneIsSet(idx);
        res[1] = dmask256.laneIsSet(idx + 1);
        res[2] = dmask256.laneIsSet(idx + 2);
        res[3] = dmask256.laneIsSet(idx + 3);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetDouble512_con() {
        res[0] = dmask512.laneIsSet(0);
        res[1] = dmask512.laneIsSet(1);
        res[2] = dmask512.laneIsSet(2);
        res[3] = dmask512.laneIsSet(3);
        res[4] = dmask512.laneIsSet(4);
        res[5] = dmask512.laneIsSet(5);
        res[6] = dmask512.laneIsSet(6);
        res[7] = dmask512.laneIsSet(7);
        return res;
    }

    @Benchmark
    public boolean[] microMaskLaneIsSetDouble512_var() {
        res[0] = dmask512.laneIsSet(idx);
        res[1] = dmask512.laneIsSet(idx + 1);
        res[2] = dmask512.laneIsSet(idx + 2);
        res[3] = dmask512.laneIsSet(idx + 3);
        res[4] = dmask512.laneIsSet(idx + 4);
        res[5] = dmask512.laneIsSet(idx + 5);
        res[6] = dmask512.laneIsSet(idx + 6);
        res[7] = dmask512.laneIsSet(idx + 7);
        return res;
    }
}
