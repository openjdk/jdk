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
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorReassociateBenchmark {
    @Param({"1024", "2048"})
    int size;

    int [] intIn1;
    int [] intOut;

    long [] longIn1;
    long [] longOut;

    short [] shortIn1;
    short [] shortOut;

    byte [] byteIn1;
    byte [] byteOut;

    static final VectorSpecies<Float> fspecies = FloatVector.SPECIES_PREFERRED;
    static final VectorSpecies<Double> dspecies = DoubleVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> ispecies = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long> lspecies = LongVector.SPECIES_PREFERRED;
    static final VectorSpecies<Short> sspecies = ShortVector.SPECIES_PREFERRED;
    static final VectorSpecies<Byte> bspecies = ByteVector.SPECIES_PREFERRED;

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(2048);
        intIn1 = new int[size];
        intOut = new int[size];

        longIn1 = new long[size];
        longOut = new long[size];

        shortIn1 = new short[size];
        shortOut = new short[size];

        byteIn1 = new byte[size];
        byteOut = new byte[size];

        for (int i = 4; i < size; i++) {
            intIn1[i] = r.nextInt();
            longIn1[i] = r.nextLong();
            shortIn1[i] = (short) r.nextInt();
            byteIn1[i] = (byte) r.nextInt();
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

    // int: bcast(a) MUL (bcast(b) MUL (bcast(c) MUL array))
    @Benchmark
    public void reassociateIntMulChainedBroadcasts() {
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

    // int: (bcast(a) MUL bcast(b)) MUL (bcast(c) MUL array)
    @Benchmark
    public void reassociateIntMulBalancedBroadcasts() {
        for (int i = 0; i < ispecies.loopBound(size); i += ispecies.length()) {
            IntVector left =
                IntVector.broadcast(ispecies, i)
                         .lanewise(VectorOperators.MUL,
                                   IntVector.broadcast(ispecies, i + 1));

            IntVector right =
                IntVector.broadcast(ispecies, i + 2)
                         .lanewise(VectorOperators.MUL,
                                   IntVector.fromArray(ispecies, intIn1, i));

            left.lanewise(VectorOperators.MUL, right)
                .intoArray(intOut, i);
        }
    }

    // long: bcast(a) MUL (bcast(b) MUL (bcast(c) MUL array))
    @Benchmark
    public void reassociateLongMulChainedBroadcasts() {
        for (int i = 0; i < lspecies.loopBound(size); i += lspecies.length()) {
            LongVector.broadcast(lspecies, (long) i)
                      .lanewise(VectorOperators.MUL,
                                LongVector.broadcast(lspecies, (long) (i + 1))
                                          .lanewise(VectorOperators.MUL,
                                                    LongVector.broadcast(lspecies, (long) (i + 2))
                                                              .lanewise(VectorOperators.MUL,
                                                                        LongVector.fromArray(lspecies, longIn1, i))))
            .intoArray(longOut, i);
        }
    }

    // long: (bcast(a) MUL bcast(b)) MUL (bcast(c) MUL array)
    @Benchmark
    public void reassociateLongMulBalancedBroadcasts() {
        for (int i = 0; i < lspecies.loopBound(size); i += lspecies.length()) {
            LongVector left =
                LongVector.broadcast(lspecies, (long) i)
                          .lanewise(VectorOperators.MUL,
                                    LongVector.broadcast(lspecies, (long) (i + 1)));

            LongVector right =
                LongVector.broadcast(lspecies, (long) (i + 2))
                          .lanewise(VectorOperators.MUL,
                                    LongVector.fromArray(lspecies, longIn1, i));

            left.lanewise(VectorOperators.MUL, right)
                .intoArray(longOut, i);
        }
    }

    // short: bcast(a) MUL (bcast(b) MUL (bcast(c) MUL array))
    @Benchmark
    public void reassociateShortMulChainedBroadcasts() {
        for (int i = 0; i < sspecies.loopBound(size); i += sspecies.length()) {
            ShortVector.broadcast(sspecies, (short) i)
                       .lanewise(VectorOperators.MUL,
                                 ShortVector.broadcast(sspecies, (short) (i + 1))
                                            .lanewise(VectorOperators.MUL,
                                                      ShortVector.broadcast(sspecies, (short) (i + 2))
                                                                 .lanewise(VectorOperators.MUL,
                                                                           ShortVector.fromArray(sspecies, shortIn1, i))))
            .intoArray(shortOut, i);
        }
    }

    // short: (bcast(a) MUL bcast(b)) MUL (bcast(c) MUL array)
    @Benchmark
    public void reassociateShortMulBalancedBroadcasts() {
        for (int i = 0; i < sspecies.loopBound(size); i += sspecies.length()) {
            ShortVector left =
                ShortVector.broadcast(sspecies, (short) i)
                           .lanewise(VectorOperators.MUL,
                                     ShortVector.broadcast(sspecies, (short) (i + 1)));

            ShortVector right =
                ShortVector.broadcast(sspecies, (short) (i + 2))
                           .lanewise(VectorOperators.MUL,
                                     ShortVector.fromArray(sspecies, shortIn1, i));

            left.lanewise(VectorOperators.MUL, right)
                .intoArray(shortOut, i);
        }
    }

    // byte: bcast(a) MUL (bcast(b) MUL (bcast(c) MUL array))
    @Benchmark
    public void reassociateByteMulChainedBroadcasts() {
        for (int i = 0; i < bspecies.loopBound(size); i += bspecies.length()) {
            ByteVector.broadcast(bspecies, (byte) i)
                      .lanewise(VectorOperators.MUL,
                                ByteVector.broadcast(bspecies, (byte) (i + 1))
                                           .lanewise(VectorOperators.MUL,
                                                     ByteVector.broadcast(bspecies, (byte) (i + 2))
                                                                .lanewise(VectorOperators.MUL,
                                                                          ByteVector.fromArray(bspecies, byteIn1, i))))
            .intoArray(byteOut, i);
        }
    }

    // byte: (bcast(a) MUL bcast(b)) MUL (bcast(c) MUL array)
    @Benchmark
    public void reassociateByteMulBalancedBroadcasts() {
        for (int i = 0; i < bspecies.loopBound(size); i += bspecies.length()) {
            ByteVector left =
                ByteVector.broadcast(bspecies, (byte) i)
                          .lanewise(VectorOperators.MUL,
                                    ByteVector.broadcast(bspecies, (byte) (i + 1)));

            ByteVector right =
                ByteVector.broadcast(bspecies, (byte) (i + 2))
                          .lanewise(VectorOperators.MUL,
                                    ByteVector.fromArray(bspecies, byteIn1, i));

            left.lanewise(VectorOperators.MUL, right)
                .intoArray(byteOut, i);
        }
    }
}
