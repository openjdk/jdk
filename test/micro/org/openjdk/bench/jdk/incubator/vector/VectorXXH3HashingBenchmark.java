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
package org.openjdk.bench.jdk.incubator.vector;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import jdk.incubator.vector.*;
import java.util.stream.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class VectorXXH3HashingBenchmark {
    @Param({"1024", "2048", "4096", "8192"})
    private int  SIZE;
    private long [] accumulators;
    private byte [] input;
    private byte [] SECRET;

    private static final VectorShuffle<Long> LONG_SHUFFLE_PREFERRED = VectorShuffle.fromOp(LongVector.SPECIES_PREFERRED, i -> i ^ 1);

    @Setup(Level.Trial)
    public void Setup() {
        accumulators = new long[SIZE];
        input = new byte[SIZE * 8];
        SECRET = new byte[SIZE*8];
        IntStream.range(0, SIZE*8).forEach(
            i -> {
                     input[i] = (byte)i;
                     SECRET[i] = (byte)-i;
                 }
        );
    }

    @Benchmark
    public void hashingKernel() {
        for (int block = 0; block < input.length / 1024; block++) {
            for (int stripe = 0; stripe < 16; stripe++) {
                int inputOffset = block * 1024 + stripe * 64;
                int secretOffset = stripe * 8;

                for (int i = 0; i < 8; i += LongVector.SPECIES_PREFERRED.length()) {
                    LongVector accumulatorsVector = LongVector.fromArray(LongVector.SPECIES_PREFERRED, accumulators, i);
                    LongVector inputVector = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, input, inputOffset + i * 8).reinterpretAsLongs();
                    LongVector secretVector = ByteVector.fromArray(ByteVector.SPECIES_PREFERRED, SECRET, secretOffset + i * 8).reinterpretAsLongs();

                    LongVector key = inputVector
                            .lanewise(VectorOperators.XOR, secretVector)
                            .reinterpretAsLongs();

                    LongVector low = key.and(0xFFFF_FFFFL);
                    LongVector high = key.lanewise(VectorOperators.LSHR, 32);

                    accumulatorsVector
                            .add(inputVector.rearrange(LONG_SHUFFLE_PREFERRED))
                            .add(high.mul(low))
                            .intoArray(accumulators, i);
                }
            }
        }
    }
}
