//
// Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.
//
//
package org.openjdk.bench.jdk.incubator.vector;

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class MaskFromLongBenchmark {
    private static final int ITERATION = 20000;

    @Benchmark
    public long microMaskFromLong_Byte64() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(ByteVector.SPECIES_64, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Byte128() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(ByteVector.SPECIES_128, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Byte256() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(ByteVector.SPECIES_256, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Byte512() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(ByteVector.SPECIES_512, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Short64() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(ShortVector.SPECIES_64, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Short128() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(ShortVector.SPECIES_128, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Short256() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(ShortVector.SPECIES_256, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Short512() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(ShortVector.SPECIES_512, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Integer64() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(IntVector.SPECIES_64, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Integer128() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(IntVector.SPECIES_128, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Integer256() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(IntVector.SPECIES_256, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Integer512() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(IntVector.SPECIES_512, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Long64() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(LongVector.SPECIES_64, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Long128() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(LongVector.SPECIES_128, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Long256() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(LongVector.SPECIES_256, res);
            res += mask.trueCount();
        }

        return res;
    }

    @Benchmark
    public long microMaskFromLong_Long512() {
        long res = 0;
        for (int i = 0; i < ITERATION; i++) {
            VectorMask mask = VectorMask.fromLong(LongVector.SPECIES_512, res);
            res += mask.trueCount();
        }

        return res;
    }

}
