//
// Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class RotateBenchmark {
    @Param({"256","512"})
    int size;

    @Param({"128","256", "512"})
    int bits;

    @Param({"7","15","31"})
    int shift;

    byte[] byteinp;
    byte[] byteres;

    short[] shortinp;
    short[] shortres;

    int[] intinp;
    int[] intres;

    long[] longinp;
    long[] longres;

    VectorSpecies<Byte> bspecies;
    VectorSpecies<Short> sspecies;
    VectorSpecies<Integer> ispecies;
    VectorSpecies<Long> lspecies;

    static final byte[] specialvalsbyte = {0, -0, Byte.MIN_VALUE, Byte.MAX_VALUE};
    static final short[] specialvalsshort = {0, -0, Short.MIN_VALUE, Short.MAX_VALUE};
    static final int[] specialvalsint = {0, -0, Integer.MIN_VALUE, Integer.MAX_VALUE};
    static final long[] specialvalslong = {0L, -0L, Long.MIN_VALUE, Long.MAX_VALUE};

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(1024);
        byteinp = new byte[size];
        byteres = new byte[size];
        shortinp = new short[size];
        shortres = new short[size];
        intinp = new int[size];
        intres = new int[size];
        longinp = new long[size];
        longres = new long[size];

        bspecies = VectorSpecies.of(byte.class, VectorShape.forBitSize(bits));
        sspecies = VectorSpecies.of(short.class, VectorShape.forBitSize(bits));
        ispecies = VectorSpecies.of(int.class, VectorShape.forBitSize(bits));
        lspecies = VectorSpecies.of(long.class, VectorShape.forBitSize(bits));

        for (int i = 4; i < size; i++) {
            byteinp[i] = (byte)i;
            shortinp[i] = (short)i;
            intinp[i] = i;
            longinp[i] = i;
        }
        for (int i = 0; i < specialvalsbyte.length; i++) {
            byteinp[i] = specialvalsbyte[i];
        }
        for (int i = 0; i < specialvalsshort.length; i++) {
            shortinp[i] = specialvalsshort[i];
        }
        for (int i = 0; i < specialvalsint.length; i++) {
            intinp[i] = specialvalsint[i];
        }
        for (int i = 0; i < specialvalslong.length; i++) {
            longinp[i] = specialvalslong[i];
        }
    }

    @Benchmark
    public void testRotateLeftB() {
        for (int j = 0; j < size; j += bspecies.length()) {
            ByteVector.fromArray(bspecies, byteinp, j)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift).intoArray(byteres, j);
        }
    }

    @Benchmark
    public void testRotateRightB() {
        for (int j = 0; j < size; j += bspecies.length()) {
            ByteVector.fromArray(bspecies, byteinp, j)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift).intoArray(byteres, j);
        }
    }

    @Benchmark
    public void testRotateLeftS() {
        for (int j = 0; j < size; j += sspecies.length()) {
            ShortVector.fromArray(sspecies, shortinp, j)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift).intoArray(shortres, j);
        }
    }

    @Benchmark
    public void testRotateRightS() {
        for (int j = 0; j < size; j += sspecies.length()) {
            ShortVector.fromArray(sspecies, shortinp, j)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift).intoArray(shortres, j);
        }
    }

    @Benchmark
    public void testRotateLeftI() {
        for (int j = 0; j < size; j += ispecies.length()) {
            IntVector.fromArray(ispecies, intinp, j)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift)
                .lanewise(VectorOperators.ROL, shift).intoArray(intres, j);
        }
    }

    @Benchmark
    public void testRotateRightI() {
        for (int j = 0; j < size; j += ispecies.length()) {
            IntVector.fromArray(ispecies, intinp, j)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift)
                .lanewise(VectorOperators.ROR, shift).intoArray(intres, j);
        }
    }

    @Benchmark
    public void testRotateLeftL() {
        for (int j = 0; j < size; j += lspecies.length()) {
            LongVector.fromArray(lspecies, longinp, j)
                   .lanewise(VectorOperators.ROL, shift)
                   .lanewise(VectorOperators.ROL, shift)
                   .lanewise(VectorOperators.ROL, shift)
                   .lanewise(VectorOperators.ROL, shift)
                   .lanewise(VectorOperators.ROL, shift)
                   .lanewise(VectorOperators.ROL, shift)
                   .lanewise(VectorOperators.ROL, shift).intoArray(longres, j);
        }
    }

    @Benchmark
    public void testRotateRightL() {
        for (int j = 0; j < size; j += lspecies.length()) {
            LongVector.fromArray(lspecies, longinp, j)
                   .lanewise(VectorOperators.ROR, shift)
                   .lanewise(VectorOperators.ROR, shift)
                   .lanewise(VectorOperators.ROR, shift)
                   .lanewise(VectorOperators.ROR, shift)
                   .lanewise(VectorOperators.ROR, shift)
                   .lanewise(VectorOperators.ROR, shift)
                   .lanewise(VectorOperators.ROR, shift).intoArray(longres, j);
        }
    }
}
