/*
 *  Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package org.openjdk.bench.jdk.incubator.vector;

import java.util.Random;
import java.util.Arrays;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorCommutativeOperSharingBenchmark {
    @Param({"1024","2048"})
    int size;

    byte[] bytesrc1;
    byte[] bytesrc2;
    byte[] byteres;

    short[] shortsrc1;
    short[] shortsrc2;
    short[] shortres;

    int[] intsrc1;
    int[] intsrc2;
    int[] intres;

    long[] longsrc1;
    long[] longsrc2;
    long[] longres;

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(1024);
        bytesrc1 = new byte[size];
        bytesrc2 = new byte[size];
        byteres = new byte[size];

        shortsrc1 = new short[size];
        shortsrc2 = new short[size];
        shortres = new short[size];

        intsrc1 = new int[size];
        intsrc2 = new int[size];
        intres = new int[size];

        longsrc1 = new long[size];
        longsrc2 = new long[size];
        longres = new long[size];

        Arrays.fill(bytesrc1, (byte)1);
        Arrays.fill(bytesrc2, (byte)2);

        Arrays.fill(shortsrc1, (short)1);
        Arrays.fill(shortsrc2, (short)2);

        Arrays.fill(intsrc1, 1);
        Arrays.fill(intsrc2, 2);

        Arrays.fill(longsrc1, 1);
        Arrays.fill(longsrc2, 2);
    }

    @Benchmark
    public void commutativeByteOperationShairing() {
        for (int i = 0; i < size; i += ByteVector.SPECIES_PREFERRED.length()) {
            ByteVector vec1 = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytesrc1, i);
            ByteVector vec2 = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, bytesrc2, i);
            vec1.lanewise(VectorOperators.ADD, vec2)
                 .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
                 .lanewise(VectorOperators.MUL, vec1.lanewise(VectorOperators.MUL, vec2))
                 .lanewise(VectorOperators.MUL, vec2.lanewise(VectorOperators.MUL, vec1))
                 .lanewise(VectorOperators.AND, vec1.lanewise(VectorOperators.AND, vec2))
                 .lanewise(VectorOperators.AND, vec2.lanewise(VectorOperators.AND, vec1))
                 .lanewise(VectorOperators.OR, vec1.lanewise(VectorOperators.OR, vec2))
                 .lanewise(VectorOperators.OR, vec2.lanewise(VectorOperators.OR, vec1))
                 .intoArray(byteres, i);
        }
    }

    @Benchmark
    public void commutativeShortOperationShairing() {
        for (int i = 0; i < size; i += ShortVector.SPECIES_PREFERRED.length()) {
            ShortVector vec1 = ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, shortsrc1, i);
            ShortVector vec2 = ShortVector.fromArray(ShortVector.SPECIES_PREFERRED, shortsrc2, i);
            vec1.lanewise(VectorOperators.ADD, vec2)
                 .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
                 .lanewise(VectorOperators.MUL, vec1.lanewise(VectorOperators.MUL, vec2))
                 .lanewise(VectorOperators.MUL, vec2.lanewise(VectorOperators.MUL, vec1))
                 .lanewise(VectorOperators.AND, vec1.lanewise(VectorOperators.AND, vec2))
                 .lanewise(VectorOperators.AND, vec2.lanewise(VectorOperators.AND, vec1))
                 .lanewise(VectorOperators.OR, vec1.lanewise(VectorOperators.OR, vec2))
                 .lanewise(VectorOperators.OR, vec2.lanewise(VectorOperators.OR, vec1))
                 .intoArray(shortres, i);
        }
    }

    @Benchmark
    public void commutativeIntOperationShairing() {
        for (int i = 0; i < size; i += IntVector.SPECIES_PREFERRED.length()) {
            IntVector vec1 = IntVector.fromArray(IntVector.SPECIES_PREFERRED, intsrc1, i);
            IntVector vec2 = IntVector.fromArray(IntVector.SPECIES_PREFERRED, intsrc2, i);
            vec1.lanewise(VectorOperators.ADD, vec2)
                 .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
                 .lanewise(VectorOperators.MUL, vec1.lanewise(VectorOperators.MUL, vec2))
                 .lanewise(VectorOperators.MUL, vec2.lanewise(VectorOperators.MUL, vec1))
                 .lanewise(VectorOperators.AND, vec1.lanewise(VectorOperators.AND, vec2))
                 .lanewise(VectorOperators.AND, vec2.lanewise(VectorOperators.AND, vec1))
                 .lanewise(VectorOperators.OR, vec1.lanewise(VectorOperators.OR, vec2))
                 .lanewise(VectorOperators.OR, vec2.lanewise(VectorOperators.OR, vec1))
                 .intoArray(intres, i);
        }
    }

    @Benchmark
    public void commutativeLongOperationShairing() {
        for (int i = 0; i < size; i += LongVector.SPECIES_PREFERRED.length()) {
            LongVector vec1 = LongVector.fromArray(LongVector.SPECIES_PREFERRED, longsrc1, i);
            LongVector vec2 = LongVector.fromArray(LongVector.SPECIES_PREFERRED, longsrc2, i);
            vec1.lanewise(VectorOperators.ADD, vec2)
                 .lanewise(VectorOperators.ADD, vec2.lanewise(VectorOperators.ADD, vec1))
                 .lanewise(VectorOperators.MUL, vec1.lanewise(VectorOperators.MUL, vec2))
                 .lanewise(VectorOperators.MUL, vec2.lanewise(VectorOperators.MUL, vec1))
                 .lanewise(VectorOperators.AND, vec1.lanewise(VectorOperators.AND, vec2))
                 .lanewise(VectorOperators.AND, vec2.lanewise(VectorOperators.AND, vec1))
                 .lanewise(VectorOperators.OR, vec1.lanewise(VectorOperators.OR, vec2))
                 .lanewise(VectorOperators.OR, vec2.lanewise(VectorOperators.OR, vec1))
                 .intoArray(longres, i);
        }
    }
}
