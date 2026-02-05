/*
 *  Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.Random;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:UseAVX=2"})
public class ReassociateVectorBenchmark {
    @Param({"1024", "2048", "4096"})
    int size;

    int [] intIn1;
    int [] intOut;

    long [] longIn1;
    long [] longOut;

    static final VectorSpecies<Float> fspecies = FloatVector.SPECIES_PREFERRED;
    static final VectorSpecies<Double> dspecies = DoubleVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> ispecies = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long> lspecies = LongVector.SPECIES_PREFERRED;

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(2048);
        intIn1 = new int[size];
        intOut = new int[size];

        longIn1 = new long[size];
        longOut = new long[size];

        for (int i = 4; i < size; i++) {
            intIn1[i] = r.nextInt();
            longIn1[i] = r.nextLong();
        }
    }

    @Benchmark
    public float pushBroadcastsAcrossVectorKernel1() {
        FloatVector res = FloatVector.broadcast(fspecies, 0.0f);
        for (int i = 0; i < size; i++) {
            FloatVector vec1 = FloatVector.broadcast(fspecies, (float)i);
            FloatVector vec2 = FloatVector.broadcast(fspecies, (float)i + 1);
            FloatVector vec3 = FloatVector.broadcast(fspecies, (float)i + 2);
            res = res.lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.FMA, vec2, vec3));
        }
        return res.lane(0);
    }

   @Benchmark
   public double pushBroadcastsAcrossVectorKernel2() {
       DoubleVector res = DoubleVector.broadcast(dspecies, 0.0f);
       for (int i = 0; i < size; i++) {
           DoubleVector vec1 = DoubleVector.broadcast(dspecies, (double)i);
           res = res.lanewise(VectorOperators.ADD, vec1.lanewise(VectorOperators.SQRT));
       }
       return res.lane(0);
   }

   @Benchmark
   public void reassociateVectorsKernel1() {
       for (int i = 0; i < ispecies.loopBound(size); i += ispecies.length()) {
           IntVector.broadcast(ispecies, i)
                    .lanewise(VectorOperators.MUL,
                              IntVector.broadcast(ispecies, i + 1)
                                       .lanewise(VectorOperators.MUL,
                                                 IntVector.broadcast(ispecies, i + 2)
                                                          .lanewise(VectorOperators.MUL,
                                                                    IntVector.fromArray(ispecies, intIn1, i))))
           .intoArray(intOut, i);
       }
   }

   @Benchmark
   public void reassociateVectorsKernel2() {
       for (int i = 0; i < lspecies.loopBound(size); i += lspecies.length()) {
           LongVector.broadcast(lspecies, i)
                     .lanewise(VectorOperators.ADD,
                               LongVector.broadcast(lspecies, i + 1)
                                         .lanewise(VectorOperators.ADD,
                                                   LongVector.broadcast(lspecies, i + 2)
                                                             .lanewise(VectorOperators.ADD,
                                                                       LongVector.fromArray(lspecies, longIn1, i))))
           .intoArray(longOut, i);
       }
   }
}
