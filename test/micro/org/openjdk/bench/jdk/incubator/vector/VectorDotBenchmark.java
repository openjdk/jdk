/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */
package org.openjdk.bench.jdk.incubator.vector;

import java.util.concurrent.TimeUnit;
import java.util.Random;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

import static jdk.incubator.vector.VectorOperators.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorDotBenchmark {
    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final int BYTE_LENGTH = 1024;
    private static final Random RD = new Random(42);
    private static byte[] a;
    private static byte[] b;

    static {
        a = new byte[BYTE_LENGTH];
        b = new byte[BYTE_LENGTH];

        for (int i = 0; i < BYTE_LENGTH; i++) {
            a[i] = (byte) RD.nextInt();
            b[i] = (byte) RD.nextInt();
        }
    }

    @Setup
    public void checkFunctionsAgree() {
        int scalarSigned = dotScalar();
        int vectorSigned = dotVector();
        int mulAddSigned = dotMulAdd();
        if (scalarSigned != vectorSigned || scalarSigned != mulAddSigned) {
            throw new AssertionError("Inconsistent signed results. scalar=" + scalarSigned +
                                                                 " vector=" + vectorSigned +
                                                                 " mulAdd=" + mulAddSigned);
        }

        int scalarUnsigned = dotUnsignedScalar();
        int vectorUnsigned = dotUnsignedVector();
        int mulAddUnsigned = dotUnsignedMulAdd();

        if (scalarUnsigned != vectorUnsigned || scalarUnsigned != mulAddUnsigned) {
            throw new AssertionError("Inconsistent unsigned results. scalar=" + scalarUnsigned +
                                                                   " vector=" + vectorUnsigned +
                                                                   " mulAdd=" + mulAddUnsigned);
        }
    }

    @Benchmark
    public int dotVector() {
        IntVector vacc = IntVector.zero(I_SPECIES);

        for (int i = 0; i < B_SPECIES.loopBound(BYTE_LENGTH); i += B_SPECIES.length()) {
            ByteVector va = ByteVector.fromArray(B_SPECIES, a, i);
            ByteVector vb = ByteVector.fromArray(B_SPECIES, b, i);

            vacc = va.dot(vb, vacc);
        }

        return vacc.reduceLanes(ADD);
    }

    @Benchmark
    public int dotMulAdd() {
        IntVector vacc = IntVector.zero(I_SPECIES);

        for (int i = 0; i < B_SPECIES.loopBound(BYTE_LENGTH); i += B_SPECIES.length()) {
            ByteVector va = ByteVector.fromArray(B_SPECIES, a, i);
            ByteVector vb = ByteVector.fromArray(B_SPECIES, b, i);

            IntVector ia0 = (IntVector) va.convertShape(B2I, I_SPECIES, 0);
            IntVector ib0 = (IntVector) vb.convertShape(B2I, I_SPECIES, 0);
            IntVector ia1 = (IntVector) va.convertShape(B2I, I_SPECIES, 1);
            IntVector ib1 = (IntVector) vb.convertShape(B2I, I_SPECIES, 1);
            IntVector ia2 = (IntVector) va.convertShape(B2I, I_SPECIES, 2);
            IntVector ib2 = (IntVector) vb.convertShape(B2I, I_SPECIES, 2);
            IntVector ia3 = (IntVector) va.convertShape(B2I, I_SPECIES, 3);
            IntVector ib3 = (IntVector) vb.convertShape(B2I, I_SPECIES, 3);

            vacc = vacc.add(ia0.mul(ib0)).add(ia1.mul(ib1))
                       .add(ia2.mul(ib2)).add(ia3.mul(ib3));
        }

        return vacc.reduceLanes(ADD);
    }

    @Benchmark
    public int dotScalar() {
        int sum = 0;

        for (int i = 0; i < BYTE_LENGTH; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }

    @Benchmark
    public int dotUnsignedVector() {
        IntVector vacc = IntVector.zero(I_SPECIES);

        for (int i = 0; i < B_SPECIES.loopBound(BYTE_LENGTH); i += B_SPECIES.length()) {
            ByteVector va = ByteVector.fromArray(B_SPECIES, a, i);
            ByteVector vb = ByteVector.fromArray(B_SPECIES, b, i);

            vacc = va.dotUnsigned(vb, vacc);
        }

        return vacc.reduceLanes(ADD);
    }

    @Benchmark
    public int dotUnsignedMulAdd() {
        IntVector vacc = IntVector.zero(I_SPECIES);

        for (int i = 0; i < B_SPECIES.loopBound(BYTE_LENGTH); i += B_SPECIES.length()) {
            ByteVector va = ByteVector.fromArray(B_SPECIES, a, i);
            ByteVector vb = ByteVector.fromArray(B_SPECIES, b, i);

            IntVector ia0 = (IntVector) va.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 0);
            IntVector ib0 = (IntVector) vb.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 0);
            IntVector ia1 = (IntVector) va.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 1);
            IntVector ib1 = (IntVector) vb.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 1);
            IntVector ia2 = (IntVector) va.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 2);
            IntVector ib2 = (IntVector) vb.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 2);
            IntVector ia3 = (IntVector) va.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 3);
            IntVector ib3 = (IntVector) vb.convertShape(ZERO_EXTEND_B2I, I_SPECIES, 3);

            vacc = vacc.add(ia0.mul(ib0)).add(ia1.mul(ib1))
                       .add(ia2.mul(ib2)).add(ia3.mul(ib3));
        }

        return vacc.reduceLanes(ADD);
    }

    @Benchmark
    public int dotUnsignedScalar() {
        int sum = 0;

        for (int i = 0; i < BYTE_LENGTH; i++) {
            sum += Byte.toUnsignedInt(a[i]) * Byte.toUnsignedInt(b[i]);
        }

        return sum;
    }
}
