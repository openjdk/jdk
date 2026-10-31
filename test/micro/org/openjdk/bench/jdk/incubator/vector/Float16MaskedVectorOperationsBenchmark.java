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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector", "-Xbatch", "-XX:-TieredCompilation"})
public class Float16MaskedVectorOperationsBenchmark {
    @Param({"1024", "2057"})
    int vectorDim;

    static final VectorSpecies<Float16> HSPECIES = Float16Vector.SPECIES_PREFERRED;
    static final ByteOrder BO = ByteOrder.nativeOrder();
    static final long ELEMENT_SIZE = 2; // bytes per Float16 (short carrier)
    static final long ALIGNMENT = 64;   // cache-line aligned base

    Arena arena;
    MemorySegment segment1;
    MemorySegment segment2;
    MemorySegment segment3;
    MemorySegment segmentRes;

    // Loop-body predicate: a mixture of set/unset lanes so the merge-masking
    // semantics are meaningfully exercised for the whole segment (not just the tail).
    VectorMask<Float16> mask;

    @Setup(Level.Trial)
    public void BmSetup() {
        long bytes = (long) vectorDim * ELEMENT_SIZE;
        arena = Arena.ofShared();
        segment1   = arena.allocate(bytes, ALIGNMENT);
        segment2   = arena.allocate(bytes, ALIGNMENT);
        segment3   = arena.allocate(bytes, ALIGNMENT);
        segmentRes = arena.allocate(bytes, ALIGNMENT);

        // Positive, well-scaled inputs keep DIV/SQRT away from NaN/Inf/denormals.
        for (int i = 0; i < vectorDim; i++) {
            segment1.setAtIndex(ValueLayout.JAVA_SHORT_UNALIGNED, i, Float.floatToFloat16(1.0f + (i % 17)));
            segment2.setAtIndex(ValueLayout.JAVA_SHORT_UNALIGNED, i, Float.floatToFloat16(1.0f + (i % 13)));
            segment3.setAtIndex(ValueLayout.JAVA_SHORT_UNALIGNED, i, Float.floatToFloat16(0.5f + (i %  7)));
        }

        boolean[] pred = new boolean[HSPECIES.length()];
        for (int i = 0; i < pred.length; i++) {
            pred[i] = (i % 3) != 0;
        }
        mask = VectorMask.fromArray(HSPECIES, pred, 0);
    }

    @TearDown(Level.Trial)
    public void BmTearDown() {
        arena.close();
    }

    // ======================================================================
    // Realistic load-op-store kernels (explicit loads/stores).
    // ======================================================================

    @Benchmark
    public void addMaskedBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO)
                         .lanewise(VectorOperators.ADD,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO), mask))
                         .intoMemorySegment(segmentRes, off, BO);
        }
        if (i < vectorDim) {
            VectorMask<Float16> tail = HSPECIES.indexInRange(i, vectorDim);
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO, tail)
                         .lanewise(VectorOperators.ADD,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO, tail), tail))
                         .intoMemorySegment(segmentRes, off, BO, tail);
        }
    }

    @Benchmark
    public void subMaskedBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO)
                         .lanewise(VectorOperators.SUB,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO), mask))
                         .intoMemorySegment(segmentRes, off, BO);
        }
        if (i < vectorDim) {
            VectorMask<Float16> tail = HSPECIES.indexInRange(i, vectorDim);
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO, tail)
                         .lanewise(VectorOperators.SUB,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO, tail), tail))
                         .intoMemorySegment(segmentRes, off, BO, tail);
        }
    }

    @Benchmark
    public void mulMaskedBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO)
                         .lanewise(VectorOperators.MUL,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO), mask))
                         .intoMemorySegment(segmentRes, off, BO);
        }
        if (i < vectorDim) {
            VectorMask<Float16> tail = HSPECIES.indexInRange(i, vectorDim);
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO, tail)
                         .lanewise(VectorOperators.MUL,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO, tail), tail))
                         .intoMemorySegment(segmentRes, off, BO, tail);
        }
    }

    @Benchmark
    public void divMaskedBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO)
                         .lanewise(VectorOperators.DIV,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO), mask))
                         .intoMemorySegment(segmentRes, off, BO);
        }
        if (i < vectorDim) {
            VectorMask<Float16> tail = HSPECIES.indexInRange(i, vectorDim);
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO, tail)
                         .lanewise(VectorOperators.DIV,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO, tail), tail))
                         .intoMemorySegment(segmentRes, off, BO, tail);
        }
    }

    @Benchmark
    public void minMaskedBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO)
                         .lanewise(VectorOperators.MIN,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO), mask))
                         .intoMemorySegment(segmentRes, off, BO);
        }
        if (i < vectorDim) {
            VectorMask<Float16> tail = HSPECIES.indexInRange(i, vectorDim);
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO, tail)
                         .lanewise(VectorOperators.MIN,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO, tail), tail))
                         .intoMemorySegment(segmentRes, off, BO, tail);
        }
    }

    @Benchmark
    public void maxMaskedBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO)
                         .lanewise(VectorOperators.MAX,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO), mask))
                         .intoMemorySegment(segmentRes, off, BO);
        }
        if (i < vectorDim) {
            VectorMask<Float16> tail = HSPECIES.indexInRange(i, vectorDim);
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO, tail)
                         .lanewise(VectorOperators.MAX,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO, tail), tail))
                         .intoMemorySegment(segmentRes, off, BO, tail);
        }
    }

    @Benchmark
    public void sqrtMaskedBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO)
                         .lanewise(VectorOperators.SQRT, mask))
                         .intoMemorySegment(segmentRes, off, BO);
        }
        if (i < vectorDim) {
            VectorMask<Float16> tail = HSPECIES.indexInRange(i, vectorDim);
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO, tail)
                         .lanewise(VectorOperators.SQRT, tail))
                         .intoMemorySegment(segmentRes, off, BO, tail);
        }
    }

    @Benchmark
    public void fmaMaskedBenchmark() {
        int i = 0;
        for (; i < HSPECIES.loopBound(vectorDim); i += HSPECIES.length()) {
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO)
                         .lanewise(VectorOperators.FMA,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO),
                                   Float16Vector.fromMemorySegment(HSPECIES, segment3, off, BO), mask))
                         .intoMemorySegment(segmentRes, off, BO);
        }
        if (i < vectorDim) {
            VectorMask<Float16> tail = HSPECIES.indexInRange(i, vectorDim);
            long off = (long) i * ELEMENT_SIZE;
            ((Float16Vector) Float16Vector.fromMemorySegment(HSPECIES, segment1, off, BO, tail)
                         .lanewise(VectorOperators.FMA,
                                   Float16Vector.fromMemorySegment(HSPECIES, segment2, off, BO, tail),
                                   Float16Vector.fromMemorySegment(HSPECIES, segment3, off, BO, tail), tail))
                         .intoMemorySegment(segmentRes, off, BO, tail);
        }
    }

    // ======================================================================
    // Register-resident latency variants (serial dependency chain per op).
    // ======================================================================

    // Float16 bit-encodings of the operand constants.
    static final short HF_ZERO = Float.floatToFloat16(0.0f); // additive identity
    static final short HF_ONE  = Float.floatToFloat16(1.0f); // multiplicative identity
    static final short HF_TWO  = Float.floatToFloat16(2.0f); // ADD addend

    // Number of chained masked ops per invocation (so per-op cost, not JMH call
    // overhead, dominates).
    @Param({"1024"})
    int regIters;

    // Pre-allocated sink; a store here keeps the accumulator from escaping while
    // still preventing dead-code elimination.
    MemorySegment resSeg;

    @Setup(Level.Trial)
    public void RegSetup() {
        resSeg = MemorySegment.ofArray(new short[HSPECIES.length()]);
    }

    @Benchmark
    public void addMaskedRegLat() {
        Vector<Float16> a  = Float16Vector.broadcast(HSPECIES, HF_ONE);
        Vector<Float16> op = Float16Vector.broadcast(HSPECIES, HF_TWO);
        for (int i = 0; i < regIters; i++) {
            a = a.lanewise(VectorOperators.ADD, op, mask);
        }
        a.intoMemorySegment(resSeg, 0, BO);
    }

    @Benchmark
    public void subMaskedRegLat() {
        Vector<Float16> a  = Float16Vector.broadcast(HSPECIES, HF_ONE);
        Vector<Float16> op = Float16Vector.broadcast(HSPECIES, HF_ZERO);
        for (int i = 0; i < regIters; i++) {
            a = a.lanewise(VectorOperators.SUB, op, mask);
        }
        a.intoMemorySegment(resSeg, 0, BO);
    }

    @Benchmark
    public void mulMaskedRegLat() {
        Vector<Float16> a  = Float16Vector.broadcast(HSPECIES, HF_ONE);
        Vector<Float16> op = Float16Vector.broadcast(HSPECIES, HF_ONE);
        for (int i = 0; i < regIters; i++) {
            a = a.lanewise(VectorOperators.MUL, op, mask);
        }
        a.intoMemorySegment(resSeg, 0, BO);
    }

    @Benchmark
    public void divMaskedRegLat() {
        Vector<Float16> a  = Float16Vector.broadcast(HSPECIES, HF_ONE);
        Vector<Float16> op = Float16Vector.broadcast(HSPECIES, HF_ONE);
        for (int i = 0; i < regIters; i++) {
            a = a.lanewise(VectorOperators.DIV, op, mask);
        }
        a.intoMemorySegment(resSeg, 0, BO);
    }

    @Benchmark
    public void minMaskedRegLat() {
        Vector<Float16> a  = Float16Vector.broadcast(HSPECIES, HF_ONE);
        Vector<Float16> op = Float16Vector.broadcast(HSPECIES, HF_ONE);
        for (int i = 0; i < regIters; i++) {
            a = a.lanewise(VectorOperators.MIN, op, mask);
        }
        a.intoMemorySegment(resSeg, 0, BO);
    }

    @Benchmark
    public void maxMaskedRegLat() {
        Vector<Float16> a  = Float16Vector.broadcast(HSPECIES, HF_ONE);
        Vector<Float16> op = Float16Vector.broadcast(HSPECIES, HF_ONE);
        for (int i = 0; i < regIters; i++) {
            a = a.lanewise(VectorOperators.MAX, op, mask);
        }
        a.intoMemorySegment(resSeg, 0, BO);
    }

    @Benchmark
    public void sqrtMaskedRegLat() {
        Vector<Float16> a = Float16Vector.broadcast(HSPECIES, HF_ONE);
        for (int i = 0; i < regIters; i++) {
            a = a.lanewise(VectorOperators.SQRT, mask);
        }
        a.intoMemorySegment(resSeg, 0, BO);
    }

    // acc = acc * 1.0 + 0.0 == acc (accumulator stays 1.0, merge into acc).
    @Benchmark
    public void fmaMaskedRegLat() {
        Vector<Float16> a   = Float16Vector.broadcast(HSPECIES, HF_ONE);
        Vector<Float16> mul = Float16Vector.broadcast(HSPECIES, HF_ONE);
        Vector<Float16> add = Float16Vector.broadcast(HSPECIES, HF_ZERO);
        for (int i = 0; i < regIters; i++) {
            a = a.lanewise(VectorOperators.FMA, mul, add, mask);
        }
        a.intoMemorySegment(resSeg, 0, BO);
    }
}
