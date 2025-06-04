/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
@Fork(value = 1, jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorMultiplyOptBenchmark {
    @Param({"1024", "2048", "4096"})
    private int  SIZE;
    private int  [] isrc1;
    private int  [] isrc2;
    private long [] lsrc1;
    private long [] lsrc2;
    private long [] res;

    private static final VectorSpecies<Long> LSP = LongVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;

    @Setup(Level.Trial)
    public void Setup() {
        lsrc1 = LongStream.range(Long.MAX_VALUE - SIZE, Long.MAX_VALUE).toArray();
        lsrc2 = LongStream.range(0, SIZE).toArray();
        isrc1 = IntStream.range(Integer.MAX_VALUE - 2 * SIZE, Integer.MAX_VALUE).toArray();
        isrc2 = IntStream.range(0, 2 * SIZE).toArray();
        res = new long[SIZE];
    }

    @Benchmark
    public void test_bm_pattern1() {
        for (int i = 0; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.AND, 0xFFFFFFFFL)
                 .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.AND, 0xFFFFFFFFL))
                 .intoArray(res, i);
        }
    }

    @Benchmark
    public void test_bm_pattern2() {
        for (int i = 0; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.AND, 0xFFFFFFFFL)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.LSHR, 32))
                .intoArray(res, i);
        }
    }

    @Benchmark
    public void test_bm_pattern3() {
        for (int i = 0; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.LSHR, 32)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.LSHR, 32))
                .intoArray(res, i);
        }
    }

    @Benchmark
    public void test_bm_pattern4() {
        for (int i = 0; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.LSHR, 32)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.AND, 0xFFFFFFFFL))
                .intoArray(res, i);
        }
    }

    @Benchmark
    public void test_bm_pattern5() {
        for (int i = 0; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = IntVector.fromArray(ISP, isrc1, i)
                                        .convert(VectorOperators.I2L, 0)
                                        .reinterpretAsLongs();
            LongVector vsrc2 = IntVector.fromArray(ISP, isrc2, i)
                                        .convert(VectorOperators.I2L, 0)
                                        .reinterpretAsLongs();
            vsrc1.lanewise(VectorOperators.MUL, vsrc2).intoArray(res, i);
        }
    }

    @Benchmark
    public void test_bm_pattern6() {
        for (int i = 0; i < LSP.loopBound(res.length); i += LSP.length()) {
            LongVector vsrc1 = LongVector.fromArray(LSP, lsrc1, i);
            LongVector vsrc2 = LongVector.fromArray(LSP, lsrc2, i);
            vsrc1.lanewise(VectorOperators.ASHR, 32)
                .lanewise(VectorOperators.MUL, vsrc2.lanewise(VectorOperators.ASHR, 32))
                .intoArray(res, i);
        }
    }

}
