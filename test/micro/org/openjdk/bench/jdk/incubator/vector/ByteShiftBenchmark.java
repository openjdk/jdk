/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class ByteShiftBenchmark {
    static final int BYTES = 4096;

    static final VectorSpecies<Byte> B128 = ByteVector.SPECIES_128;
    static final VectorSpecies<Byte> B256 = ByteVector.SPECIES_256;
    static final VectorSpecies<Byte> B512 = ByteVector.SPECIES_512;

    @Param({"3"})
    int shift;

    byte[] src;
    byte[] dst;

    @Setup
    public void setup() {
        src = new byte[BYTES];
        dst = new byte[BYTES];
        for (int i = 0; i < src.length; i++) {
            src[i] = (byte) i;
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void lshl128() {
        for (int i = 0; i < BYTES; i += B128.length()) {
            ByteVector.fromArray(B128, src, i)
                    .lanewise(VectorOperators.LSHL, shift).intoArray(dst, i);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void ashr128() {
        for (int i = 0; i < BYTES; i += B128.length()) {
            ByteVector.fromArray(B128, src, i)
                    .lanewise(VectorOperators.ASHR, shift).intoArray(dst, i);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void lshr128() {
        for (int i = 0; i < BYTES; i += B128.length()) {
            ByteVector.fromArray(B128, src, i)
                    .lanewise(VectorOperators.LSHR, shift).intoArray(dst, i);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void lshl256() {
        for (int i = 0; i < BYTES; i += B256.length()) {
            ByteVector.fromArray(B256, src, i)
                    .lanewise(VectorOperators.LSHL, shift).intoArray(dst, i);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void ashr256() {
        for (int i = 0; i < BYTES; i += B256.length()) {
            ByteVector.fromArray(B256, src, i)
                    .lanewise(VectorOperators.ASHR, shift).intoArray(dst, i);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void lshr256() {
        for (int i = 0; i < BYTES; i += B256.length()) {
            ByteVector.fromArray(B256, src, i)
                    .lanewise(VectorOperators.LSHR, shift).intoArray(dst, i);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void lshl512() {
        for (int i = 0; i < BYTES; i += B512.length()) {
            ByteVector.fromArray(B512, src, i)
                    .lanewise(VectorOperators.LSHL, shift).intoArray(dst, i);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void ashr512() {
        for (int i = 0; i < BYTES; i += B512.length()) {
            ByteVector.fromArray(B512, src, i)
                    .lanewise(VectorOperators.ASHR, shift).intoArray(dst, i);
        }
    }

    @Benchmark
    @OperationsPerInvocation(BYTES)
    public void lshr512() {
        for (int i = 0; i < BYTES; i += B512.length()) {
            ByteVector.fromArray(B512, src, i)
                    .lanewise(VectorOperators.LSHR, shift).intoArray(dst, i);
        }
    }
}
