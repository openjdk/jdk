//
// Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MINUTES)
@State(Scope.Thread)
public class RotateBenchmark {
    @Param({"64","128","256"})
    int size;

    @Param({"128","256", "512"})
    int bits;

    @Param({"11","21","31"})
    int shift;

    long[] longinp;
    long[] longres;
    int[] intinp;
    int[] intres;
    VectorSpecies ispecies;
    VectorSpecies lspecies;

    static final long[] specialvalslong = {0L, -0L, Long.MIN_VALUE, Long.MAX_VALUE};
    static final int[] specialvalsint = {0, -0, Integer.MIN_VALUE, Integer.MAX_VALUE};

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(1024);
        longinp = new long[size];
        longres = new long[size];
        intinp = new int[size];
        intres = new int[size];

        ispecies = VectorSpecies.of(int.class, VectorShape.forBitSize(bits));
        lspecies = VectorSpecies.of(long.class, VectorShape.forBitSize(bits));

        for (int i = 4; i < size; i++) {
            intinp[i] = i;
            longinp[i] = i;
        }
        for (int i = 0 ; i < specialvalslong.length; i++) {
            longinp[i] = specialvalslong[i];
        }
        for (int i = 0 ; i < specialvalsint.length; i++) {
            intinp[i] = specialvalsint[i];
        }
    }

    @Benchmark
    public void testRotateLeftI(Blackhole bh) {
        IntVector intvec = null;
        for (int j = 0 ; j < size; j+= ispecies.length()) {
            intvec = IntVector.fromArray(ispecies, intinp, shift);
            intvec = intvec.lanewise(VectorOperators.ROL, shift);
            intvec = intvec.lanewise(VectorOperators.ROL, shift);
            intvec = intvec.lanewise(VectorOperators.ROL, shift);
            intvec = intvec.lanewise(VectorOperators.ROL, shift);
            intvec.lanewise(VectorOperators.ROL, j).intoArray(intres, j);
        }
        bh.consume(intvec);
    }

    @Benchmark
    public void testRotateRightI(Blackhole bh) {
        IntVector intvec = null;
        for (int j = 0 ; j < size; j+= ispecies.length()) {
            intvec = IntVector.fromArray(ispecies, intinp, shift);
            intvec = intvec.lanewise(VectorOperators.ROR, shift);
            intvec = intvec.lanewise(VectorOperators.ROR, shift);
            intvec = intvec.lanewise(VectorOperators.ROR, shift);
            intvec = intvec.lanewise(VectorOperators.ROR, shift);
            intvec.lanewise(VectorOperators.ROR, j).intoArray(intres, j);
        }
        bh.consume(intvec);
    }

    @Benchmark
    public void testRotateLeftL(Blackhole bh) {
        LongVector longvec = null;
        for (int j = 0 ; j < size; j+= lspecies.length()) {
            longvec = LongVector.fromArray(lspecies, longinp, shift);
            longvec = longvec.lanewise(VectorOperators.ROL, shift);
            longvec = longvec.lanewise(VectorOperators.ROL, shift);
            longvec = longvec.lanewise(VectorOperators.ROL, shift);
            longvec = longvec.lanewise(VectorOperators.ROL, shift);
            longvec.lanewise(VectorOperators.ROL, j).intoArray(longres, j);
        }
        bh.consume(longvec);
    }

    @Benchmark
    public void testRotateRightL(Blackhole bh) {
        LongVector longvec = null;
        for (int j = 0 ; j < size; j+= lspecies.length()) {
            longvec = LongVector.fromArray(lspecies, longinp, shift);
            longvec = longvec.lanewise(VectorOperators.ROR, shift);
            longvec = longvec.lanewise(VectorOperators.ROR, shift);
            longvec = longvec.lanewise(VectorOperators.ROR, shift);
            longvec = longvec.lanewise(VectorOperators.ROR, shift);
            longvec.lanewise(VectorOperators.ROR, j).intoArray(longres, j);
        }
        bh.consume(longvec);
    }
}
