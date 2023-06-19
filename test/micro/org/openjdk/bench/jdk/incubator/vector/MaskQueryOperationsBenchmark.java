//
// Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Random;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class MaskQueryOperationsBenchmark {
    @Param({"1","2","3"})
    int inputs;

    static final Random RD = new Random();
    static final VectorSpecies<Byte> bspecies = ByteVector.SPECIES_PREFERRED;
    static final VectorSpecies<Short> sspecies = ShortVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> ispecies = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long> lspecies = LongVector.SPECIES_PREFERRED;
    boolean [] mask_arr;
    static final int LENGTH = 512;

    static final boolean [] mask_avg_case;
    static final boolean [] mask_best_case;
    static final boolean [] mask_worst_case;
    static {
        mask_avg_case = new boolean[LENGTH];
        mask_best_case = new boolean[LENGTH];
        mask_worst_case = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            mask_best_case[i] = true;
            mask_worst_case[i] = false;
            mask_avg_case[i] = RD.nextBoolean();
        }
    }

    @Setup(Level.Trial)
    public void bmSetup() {
        if (1 == inputs) {
          mask_arr = mask_best_case;
        } else if (2 == inputs) {
          mask_arr = mask_worst_case;
        } else {
          mask_arr = mask_avg_case;
        }
    }

    @Benchmark
    public int testTrueCountByte() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += bspecies.length()) {
            VectorMask<Byte> m = VectorMask.fromArray(bspecies, mask_arr, i);
            res += m.trueCount();
        }

        return res;
    }

    @Benchmark
    public int testTrueCountShort() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += sspecies.length()) {
            VectorMask<Short> m = VectorMask.fromArray(sspecies, mask_arr, i);
            res += m.trueCount();
        }

        return res;
    }

    @Benchmark
    public int testTrueCountInt() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += ispecies.length()) {
            VectorMask<Integer> m = VectorMask.fromArray(ispecies, mask_arr, i);
            res += m.trueCount();
        }

        return res;
    }

    @Benchmark
    public int testTrueCountLong() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += lspecies.length()) {
            VectorMask<Long> m = VectorMask.fromArray(lspecies, mask_arr, i);
            res += m.trueCount();
        }

        return res;
    }

    @Benchmark
    public int testFirstTrueByte() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += bspecies.length()) {
            VectorMask<Byte> m = VectorMask.fromArray(bspecies, mask_arr, i);
            res += m.firstTrue();
        }

        return res;
    }

    @Benchmark
    public int testFirstTrueShort() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += sspecies.length()) {
            VectorMask<Short> m = VectorMask.fromArray(sspecies, mask_arr, i);
            res += m.firstTrue();
        }

        return res;
    }

    @Benchmark
    public int testFirstTrueInt() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += ispecies.length()) {
            VectorMask<Integer> m = VectorMask.fromArray(ispecies, mask_arr, i);
            res += m.firstTrue();
        }

        return res;
    }

    @Benchmark
    public int testFirstTrueLong() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += lspecies.length()) {
            VectorMask<Long> m = VectorMask.fromArray(lspecies, mask_arr, i);
            res += m.firstTrue();
        }

        return res;
    }

    @Benchmark
    public int testLastTrueByte() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += bspecies.length()) {
            VectorMask<Byte> m = VectorMask.fromArray(bspecies, mask_arr, i);
            res += m.lastTrue();
        }

        return res;
    }

    @Benchmark
    public int testLastTrueShort() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += sspecies.length()) {
            VectorMask<Short> m = VectorMask.fromArray(sspecies, mask_arr, i);
            res += m.lastTrue();
        }

        return res;
    }

    @Benchmark
    public int testLastTrueInt() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += ispecies.length()) {
            VectorMask<Integer> m = VectorMask.fromArray(ispecies, mask_arr, i);
            res += m.lastTrue();
        }

        return res;
    }

    @Benchmark
    public int testLastTrueLong() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += lspecies.length()) {
            VectorMask<Long> m = VectorMask.fromArray(lspecies, mask_arr, i);
            res += m.lastTrue();
        }

        return res;
    }

    @Benchmark
    public long testToLongByte() {
        long res = 0;
        for (int i = 0; i < LENGTH; i += bspecies.length()) {
            VectorMask<Byte> m = VectorMask.fromArray(bspecies, mask_arr, i);
            res += m.toLong();
        }

        return res;
    }

    @Benchmark
    public long testToLongShort() {
        long res = 0;
        for (int i = 0; i < LENGTH; i += sspecies.length()) {
            VectorMask<Short> m = VectorMask.fromArray(sspecies, mask_arr, i);
            res += m.toLong();
        }

        return res;
    }

    @Benchmark
    public long testToLongInt() {
        long res = 0;
        for (int i = 0; i < LENGTH; i += ispecies.length()) {
            VectorMask<Integer> m = VectorMask.fromArray(ispecies, mask_arr, i);
            res += m.toLong();
        }

        return res;
    }

    @Benchmark
    public long testToLongLong() {
        long res = 0;
        for (int i = 0; i < LENGTH; i += lspecies.length()) {
            VectorMask<Long> m = VectorMask.fromArray(lspecies, mask_arr, i);
            res += m.toLong();
        }

        return res;
    }
}
