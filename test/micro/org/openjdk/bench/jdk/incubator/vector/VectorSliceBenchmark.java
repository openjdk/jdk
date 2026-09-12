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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class VectorSliceBenchmark {
    static final ByteOrder ORDER = ByteOrder.nativeOrder();

    static final long MASK_BITS = 0x5555555555555555L;

    static final int KERNEL_BYTES = 4096;

    static final VectorSpecies<Byte> B128    = ByteVector.SPECIES_128;
    static final VectorSpecies<Byte> B256    = ByteVector.SPECIES_256;
    static final VectorSpecies<Byte> B512    = ByteVector.SPECIES_512;
    static final VectorSpecies<Short> S128   = ShortVector.SPECIES_128;
    static final VectorSpecies<Short> S256   = ShortVector.SPECIES_256;
    static final VectorSpecies<Short> S512   = ShortVector.SPECIES_512;
    static final VectorSpecies<Integer> I128 = IntVector.SPECIES_128;
    static final VectorSpecies<Integer> I256 = IntVector.SPECIES_256;
    static final VectorSpecies<Integer> I512 = IntVector.SPECIES_512;
    static final VectorSpecies<Long> L128    = LongVector.SPECIES_128;
    static final VectorSpecies<Long> L256    = LongVector.SPECIES_256;
    static final VectorSpecies<Long> L512    = LongVector.SPECIES_512;
    static final VectorSpecies<Float> F128   = FloatVector.SPECIES_128;
    static final VectorSpecies<Float> F256   = FloatVector.SPECIES_256;
    static final VectorSpecies<Float> F512   = FloatVector.SPECIES_512;
    static final VectorSpecies<Double> D128  = DoubleVector.SPECIES_128;
    static final VectorSpecies<Double> D256  = DoubleVector.SPECIES_256;
    static final VectorSpecies<Double> D512  = DoubleVector.SPECIES_512;

    static final VectorMask<Byte> B128_MASK    = VectorMask.fromLong(B128, MASK_BITS);
    static final VectorMask<Byte> B256_MASK    = VectorMask.fromLong(B256, MASK_BITS);
    static final VectorMask<Byte> B512_MASK    = VectorMask.fromLong(B512, MASK_BITS);
    static final VectorMask<Short> S128_MASK   = VectorMask.fromLong(S128, MASK_BITS);
    static final VectorMask<Short> S256_MASK   = VectorMask.fromLong(S256, MASK_BITS);
    static final VectorMask<Short> S512_MASK   = VectorMask.fromLong(S512, MASK_BITS);
    static final VectorMask<Integer> I128_MASK = VectorMask.fromLong(I128, MASK_BITS);
    static final VectorMask<Integer> I256_MASK = VectorMask.fromLong(I256, MASK_BITS);
    static final VectorMask<Integer> I512_MASK = VectorMask.fromLong(I512, MASK_BITS);
    static final VectorMask<Long> L128_MASK    = VectorMask.fromLong(L128, MASK_BITS);
    static final VectorMask<Long> L256_MASK    = VectorMask.fromLong(L256, MASK_BITS);
    static final VectorMask<Long> L512_MASK    = VectorMask.fromLong(L512, MASK_BITS);
    static final VectorMask<Float> F128_MASK   = VectorMask.fromLong(F128, MASK_BITS);
    static final VectorMask<Float> F256_MASK   = VectorMask.fromLong(F256, MASK_BITS);
    static final VectorMask<Float> F512_MASK   = VectorMask.fromLong(F512, MASK_BITS);
    static final VectorMask<Double> D128_MASK  = VectorMask.fromLong(D128, MASK_BITS);
    static final VectorMask<Double> D256_MASK  = VectorMask.fromLong(D256, MASK_BITS);
    static final VectorMask<Double> D512_MASK  = VectorMask.fromLong(D512, MASK_BITS);

    MemorySegment ksrc1;
    MemorySegment ksrc2;
    MemorySegment kfsrc1;
    MemorySegment kfsrc2;
    MemorySegment kdsrc1;
    MemorySegment kdsrc2;

    int vidx0;
    int vidx1;
    int vidx2;
    int vidx3;

    static MemorySegment kernelOperand(Arena arena, Random r, char kind) {
        MemorySegment ms = arena.allocate(KERNEL_BYTES, 64);
        switch (kind) {
            case 'f' -> {
                for (int i = 0; i < KERNEL_BYTES / Float.BYTES; i++) {
                    ms.setAtIndex(ValueLayout.JAVA_FLOAT, i, r.nextFloat());
                }
            }
            case 'd' -> {
                for (int i = 0; i < KERNEL_BYTES / Double.BYTES; i++) {
                    ms.setAtIndex(ValueLayout.JAVA_DOUBLE, i, r.nextDouble());
                }
            }
            default -> {
                for (int i = 0; i < KERNEL_BYTES; i++) {
                    ms.setAtIndex(ValueLayout.JAVA_BYTE, i, (byte)r.nextInt());
                }
            }
        }
        return ms;
    }

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(2048);
        Arena arena = Arena.ofAuto();
        ksrc1  = kernelOperand(arena, r, 'i');
        ksrc2  = kernelOperand(arena, r, 'i');
        kfsrc1 = kernelOperand(arena, r, 'f');
        kfsrc2 = kernelOperand(arena, r, 'f');
        kdsrc1 = kernelOperand(arena, r, 'd');
        kdsrc2 = kernelOperand(arena, r, 'd');
        vidx0 = 1;
        vidx1 = 2;
        vidx2 = 3;
        vidx3 = 4;
    }

    @Benchmark
    public byte byte128ConstOrigin() {
        ByteVector acc = ByteVector.zero(B128);
        for (long i = 0; i < KERNEL_BYTES; i += B128.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B128, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B128, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(1, v2).add(v1.slice(5, v2));
            ByteVector s1 = v1.slice(9, v2).add(v1.slice(13, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public byte byte128ConstOriginMasked() {
        ByteVector acc = ByteVector.zero(B128);
        for (long i = 0; i < KERNEL_BYTES; i += B128.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B128, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B128, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(1, v2, B128_MASK).add(v1.slice(5, v2, B128_MASK));
            ByteVector s1 = v1.slice(9, v2, B128_MASK).add(v1.slice(13, v2, B128_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public byte byte512DwordAlignedOrigin() {
        ByteVector acc = ByteVector.zero(B512);
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(4, v2).add(v1.slice(20, v2));
            ByteVector s1 = v1.slice(36, v2).add(v1.slice(52, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public byte byte512DwordAlignedOriginMasked() {
        ByteVector acc = ByteVector.zero(B512);
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(4, v2, B512_MASK).add(v1.slice(20, v2, B512_MASK));
            ByteVector s1 = v1.slice(36, v2, B512_MASK).add(v1.slice(52, v2, B512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public byte byte512SubDwordOriginMidRange() {
        ByteVector acc = ByteVector.zero(B512);
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(17, v2).add(v1.slice(26, v2));
            ByteVector s1 = v1.slice(35, v2).add(v1.slice(46, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public byte byte512SubDwordOriginMidRangeMasked() {
        ByteVector acc = ByteVector.zero(B512);
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(17, v2, B512_MASK).add(v1.slice(26, v2, B512_MASK));
            ByteVector s1 = v1.slice(35, v2, B512_MASK).add(v1.slice(46, v2, B512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public byte byte512SubDwordOriginEdgeRange() {
        ByteVector acc = ByteVector.zero(B512);
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(3, v2).add(v1.slice(14, v2));
            ByteVector s1 = v1.slice(51, v2).add(v1.slice(62, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public byte byte512SubDwordOriginEdgeRangeMasked() {
        ByteVector acc = ByteVector.zero(B512);
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(3, v2, B512_MASK).add(v1.slice(14, v2, B512_MASK));
            ByteVector s1 = v1.slice(51, v2, B512_MASK).add(v1.slice(62, v2, B512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short128ConstOrigin() {
        ShortVector acc = ShortVector.zero(S128);
        for (long i = 0; i < KERNEL_BYTES; i += S128.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S128, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S128, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(1, v2).add(v1.slice(3, v2));
            ShortVector s1 = v1.slice(5, v2).add(v1.slice(7, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short128ConstOriginMasked() {
        ShortVector acc = ShortVector.zero(S128);
        for (long i = 0; i < KERNEL_BYTES; i += S128.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S128, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S128, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(1, v2, S128_MASK).add(v1.slice(3, v2, S128_MASK));
            ShortVector s1 = v1.slice(5, v2, S128_MASK).add(v1.slice(7, v2, S128_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short512DwordAlignedOrigin() {
        ShortVector acc = ShortVector.zero(S512);
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(2, v2).add(v1.slice(10, v2));
            ShortVector s1 = v1.slice(18, v2).add(v1.slice(26, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short512DwordAlignedOriginMasked() {
        ShortVector acc = ShortVector.zero(S512);
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(2, v2, S512_MASK).add(v1.slice(10, v2, S512_MASK));
            ShortVector s1 = v1.slice(18, v2, S512_MASK).add(v1.slice(26, v2, S512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short512SubDwordOriginMidRange() {
        ShortVector acc = ShortVector.zero(S512);
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(9, v2).add(v1.slice(13, v2));
            ShortVector s1 = v1.slice(17, v2).add(v1.slice(21, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short512SubDwordOriginMidRangeMasked() {
        ShortVector acc = ShortVector.zero(S512);
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(9, v2, S512_MASK).add(v1.slice(13, v2, S512_MASK));
            ShortVector s1 = v1.slice(17, v2, S512_MASK).add(v1.slice(21, v2, S512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short512SubDwordOriginEdgeRange() {
        ShortVector acc = ShortVector.zero(S512);
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(3, v2).add(v1.slice(7, v2));
            ShortVector s1 = v1.slice(25, v2).add(v1.slice(29, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short512SubDwordOriginEdgeRangeMasked() {
        ShortVector acc = ShortVector.zero(S512);
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(3, v2, S512_MASK).add(v1.slice(7, v2, S512_MASK));
            ShortVector s1 = v1.slice(25, v2, S512_MASK).add(v1.slice(29, v2, S512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public int int128ConstOrigin() {
        IntVector acc = IntVector.zero(I128);
        for (long i = 0; i < KERNEL_BYTES; i += I128.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I128, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I128, ksrc2, i, ORDER);
            IntVector s0 = v1.slice(1, v2).add(v1.slice(2, v2));
            IntVector s1 = v1.slice(3, v2).add(v2.slice(1, v1));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public int int128ConstOriginMasked() {
        IntVector acc = IntVector.zero(I128);
        for (long i = 0; i < KERNEL_BYTES; i += I128.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I128, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I128, ksrc2, i, ORDER);
            IntVector s0 = v1.slice(1, v2, I128_MASK).add(v1.slice(2, v2, I128_MASK));
            IntVector s1 = v1.slice(3, v2, I128_MASK).add(v2.slice(1, v1, I128_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public int int512DwordAlignedOrigin() {
        IntVector acc = IntVector.zero(I512);
        for (long i = 0; i < KERNEL_BYTES; i += I512.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I512, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I512, ksrc2, i, ORDER);
            IntVector s0 = v1.slice(1, v2).add(v1.slice(5, v2));
            IntVector s1 = v1.slice(9, v2).add(v1.slice(13, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public int int512DwordAlignedOriginMasked() {
        IntVector acc = IntVector.zero(I512);
        for (long i = 0; i < KERNEL_BYTES; i += I512.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I512, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I512, ksrc2, i, ORDER);
            IntVector s0 = v1.slice(1, v2, I512_MASK).add(v1.slice(5, v2, I512_MASK));
            IntVector s1 = v1.slice(9, v2, I512_MASK).add(v1.slice(13, v2, I512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public long long128ConstOrigin() {
        LongVector acc = LongVector.zero(L128);
        for (long i = 0; i < KERNEL_BYTES; i += L128.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L128, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L128, ksrc2, i, ORDER);
            acc = acc.add(v1.slice(1, v2).add(v2.slice(1, v1)));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public long long128ConstOriginMasked() {
        LongVector acc = LongVector.zero(L128);
        for (long i = 0; i < KERNEL_BYTES; i += L128.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L128, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L128, ksrc2, i, ORDER);
            acc = acc.add(v1.slice(1, v2, L128_MASK).add(v2.slice(1, v1, L128_MASK)));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public long long512DwordAlignedOrigin() {
        LongVector acc = LongVector.zero(L512);
        for (long i = 0; i < KERNEL_BYTES; i += L512.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L512, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L512, ksrc2, i, ORDER);
            LongVector s0 = v1.slice(1, v2).add(v1.slice(3, v2));
            LongVector s1 = v1.slice(5, v2).add(v1.slice(7, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public long long512DwordAlignedOriginMasked() {
        LongVector acc = LongVector.zero(L512);
        for (long i = 0; i < KERNEL_BYTES; i += L512.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L512, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L512, ksrc2, i, ORDER);
            LongVector s0 = v1.slice(1, v2, L512_MASK).add(v1.slice(3, v2, L512_MASK));
            LongVector s1 = v1.slice(5, v2, L512_MASK).add(v1.slice(7, v2, L512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public float float128ConstOrigin() {
        FloatVector acc = FloatVector.zero(F128);
        for (long i = 0; i < KERNEL_BYTES; i += F128.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F128, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F128, kfsrc2, i, ORDER);
            FloatVector s0 = v1.slice(1, v2).add(v1.slice(2, v2));
            FloatVector s1 = v1.slice(3, v2).add(v2.slice(1, v1));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public float float128ConstOriginMasked() {
        FloatVector acc = FloatVector.zero(F128);
        for (long i = 0; i < KERNEL_BYTES; i += F128.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F128, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F128, kfsrc2, i, ORDER);
            FloatVector s0 = v1.slice(1, v2, F128_MASK).add(v1.slice(2, v2, F128_MASK));
            FloatVector s1 = v1.slice(3, v2, F128_MASK).add(v2.slice(1, v1, F128_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public float float512DwordAlignedOrigin() {
        FloatVector acc = FloatVector.zero(F512);
        for (long i = 0; i < KERNEL_BYTES; i += F512.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F512, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F512, kfsrc2, i, ORDER);
            FloatVector s0 = v1.slice(1, v2).add(v1.slice(5, v2));
            FloatVector s1 = v1.slice(9, v2).add(v1.slice(13, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public float float512DwordAlignedOriginMasked() {
        FloatVector acc = FloatVector.zero(F512);
        for (long i = 0; i < KERNEL_BYTES; i += F512.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F512, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F512, kfsrc2, i, ORDER);
            FloatVector s0 = v1.slice(1, v2, F512_MASK).add(v1.slice(5, v2, F512_MASK));
            FloatVector s1 = v1.slice(9, v2, F512_MASK).add(v1.slice(13, v2, F512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public double double128ConstOrigin() {
        DoubleVector acc = DoubleVector.zero(D128);
        for (long i = 0; i < KERNEL_BYTES; i += D128.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D128, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D128, kdsrc2, i, ORDER);
            acc = acc.add(v1.slice(1, v2).add(v2.slice(1, v1)));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public double double128ConstOriginMasked() {
        DoubleVector acc = DoubleVector.zero(D128);
        for (long i = 0; i < KERNEL_BYTES; i += D128.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D128, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D128, kdsrc2, i, ORDER);
            acc = acc.add(v1.slice(1, v2, D128_MASK).add(v2.slice(1, v1, D128_MASK)));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public double double512DwordAlignedOrigin() {
        DoubleVector acc = DoubleVector.zero(D512);
        for (long i = 0; i < KERNEL_BYTES; i += D512.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D512, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D512, kdsrc2, i, ORDER);
            DoubleVector s0 = v1.slice(1, v2).add(v1.slice(3, v2));
            DoubleVector s1 = v1.slice(5, v2).add(v1.slice(7, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public double double512DwordAlignedOriginMasked() {
        DoubleVector acc = DoubleVector.zero(D512);
        for (long i = 0; i < KERNEL_BYTES; i += D512.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D512, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D512, kdsrc2, i, ORDER);
            DoubleVector s0 = v1.slice(1, v2, D512_MASK).add(v1.slice(3, v2, D512_MASK));
            DoubleVector s1 = v1.slice(5, v2, D512_MASK).add(v1.slice(7, v2, D512_MASK));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public byte byte512VariableOrigin() {
        ByteVector acc = ByteVector.zero(B512);
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            ByteVector s0 = v1.slice(vidx0, v2).add(v1.slice(vidx1, v2));
            ByteVector s1 = v1.slice(vidx2, v2).add(v1.slice(vidx3, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public short short512VariableOrigin() {
        ShortVector acc = ShortVector.zero(S512);
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            ShortVector s0 = v1.slice(vidx0, v2).add(v1.slice(vidx1, v2));
            ShortVector s1 = v1.slice(vidx2, v2).add(v1.slice(vidx3, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public int int512VariableOrigin() {
        IntVector acc = IntVector.zero(I512);
        for (long i = 0; i < KERNEL_BYTES; i += I512.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I512, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I512, ksrc2, i, ORDER);
            IntVector s0 = v1.slice(vidx0, v2).add(v1.slice(vidx1, v2));
            IntVector s1 = v1.slice(vidx2, v2).add(v1.slice(vidx3, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public long long512VariableOrigin() {
        LongVector acc = LongVector.zero(L512);
        for (long i = 0; i < KERNEL_BYTES; i += L512.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L512, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L512, ksrc2, i, ORDER);
            LongVector s0 = v1.slice(vidx0, v2).add(v1.slice(vidx1, v2));
            LongVector s1 = v1.slice(vidx2, v2).add(v1.slice(vidx3, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public float float512VariableOrigin() {
        FloatVector acc = FloatVector.zero(F512);
        for (long i = 0; i < KERNEL_BYTES; i += F512.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F512, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F512, kfsrc2, i, ORDER);
            FloatVector s0 = v1.slice(vidx0, v2).add(v1.slice(vidx1, v2));
            FloatVector s1 = v1.slice(vidx2, v2).add(v1.slice(vidx3, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @Benchmark
    public double double512VariableOrigin() {
        DoubleVector acc = DoubleVector.zero(D512);
        for (long i = 0; i < KERNEL_BYTES; i += D512.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D512, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D512, kdsrc2, i, ORDER);
            DoubleVector s0 = v1.slice(vidx0, v2).add(v1.slice(vidx1, v2));
            DoubleVector s1 = v1.slice(vidx2, v2).add(v1.slice(vidx3, v2));
            acc = acc.add(s0.add(s1));
        }
        return acc.reduceLanes(VectorOperators.ADD);
    }

    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Fork(jvmArgs = {"--add-modules=jdk.incubator.vector", "-XX:UseAVX=2"})
    public static class Avx2 {
        MemorySegment ksrc1;
        MemorySegment ksrc2;
        MemorySegment kfsrc1;
        MemorySegment kfsrc2;
        MemorySegment kdsrc1;
        MemorySegment kdsrc2;

        @Setup(Level.Trial)
        public void setup() {
            Random r = new Random(2048);
            Arena arena = Arena.ofAuto();
            ksrc1  = kernelOperand(arena, r, 'i');
            ksrc2  = kernelOperand(arena, r, 'i');
            kfsrc1 = kernelOperand(arena, r, 'f');
            kfsrc2 = kernelOperand(arena, r, 'f');
            kdsrc1 = kernelOperand(arena, r, 'd');
            kdsrc2 = kernelOperand(arena, r, 'd');
        }

        @Benchmark
        public byte byte256LaneAlignedOrigin() {
            ByteVector acc = ByteVector.zero(B256);
            for (long i = 0; i < KERNEL_BYTES; i += B256.vectorByteSize()) {
                ByteVector v1 = ByteVector.fromMemorySegment(B256, ksrc1, i, ORDER);
                ByteVector v2 = ByteVector.fromMemorySegment(B256, ksrc2, i, ORDER);
                acc = acc.add(v1.slice(16, v2).add(v2.slice(16, v1)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public byte byte256LaneAlignedOriginMasked() {
            ByteVector acc = ByteVector.zero(B256);
            for (long i = 0; i < KERNEL_BYTES; i += B256.vectorByteSize()) {
                ByteVector v1 = ByteVector.fromMemorySegment(B256, ksrc1, i, ORDER);
                ByteVector v2 = ByteVector.fromMemorySegment(B256, ksrc2, i, ORDER);
                acc = acc.add(v1.slice(16, v2, B256_MASK).add(v2.slice(16, v1, B256_MASK)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public short short256LaneAlignedOrigin() {
            ShortVector acc = ShortVector.zero(S256);
            for (long i = 0; i < KERNEL_BYTES; i += S256.vectorByteSize()) {
                ShortVector v1 = ShortVector.fromMemorySegment(S256, ksrc1, i, ORDER);
                ShortVector v2 = ShortVector.fromMemorySegment(S256, ksrc2, i, ORDER);
                acc = acc.add(v1.slice(8, v2).add(v2.slice(8, v1)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public short short256LaneAlignedOriginMasked() {
            ShortVector acc = ShortVector.zero(S256);
            for (long i = 0; i < KERNEL_BYTES; i += S256.vectorByteSize()) {
                ShortVector v1 = ShortVector.fromMemorySegment(S256, ksrc1, i, ORDER);
                ShortVector v2 = ShortVector.fromMemorySegment(S256, ksrc2, i, ORDER);
                acc = acc.add(v1.slice(8, v2, S256_MASK).add(v2.slice(8, v1, S256_MASK)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public int int256LaneAlignedOrigin() {
            IntVector acc = IntVector.zero(I256);
            for (long i = 0; i < KERNEL_BYTES; i += I256.vectorByteSize()) {
                IntVector v1 = IntVector.fromMemorySegment(I256, ksrc1, i, ORDER);
                IntVector v2 = IntVector.fromMemorySegment(I256, ksrc2, i, ORDER);
                acc = acc.add(v1.slice(4, v2).add(v2.slice(4, v1)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public int int256LaneAlignedOriginMasked() {
            IntVector acc = IntVector.zero(I256);
            for (long i = 0; i < KERNEL_BYTES; i += I256.vectorByteSize()) {
                IntVector v1 = IntVector.fromMemorySegment(I256, ksrc1, i, ORDER);
                IntVector v2 = IntVector.fromMemorySegment(I256, ksrc2, i, ORDER);
                acc = acc.add(v1.slice(4, v2, I256_MASK).add(v2.slice(4, v1, I256_MASK)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public long long256LaneAlignedOrigin() {
            LongVector acc = LongVector.zero(L256);
            for (long i = 0; i < KERNEL_BYTES; i += L256.vectorByteSize()) {
                LongVector v1 = LongVector.fromMemorySegment(L256, ksrc1, i, ORDER);
                LongVector v2 = LongVector.fromMemorySegment(L256, ksrc2, i, ORDER);
                acc = acc.add(v1.slice(2, v2).add(v2.slice(2, v1)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public long long256LaneAlignedOriginMasked() {
            LongVector acc = LongVector.zero(L256);
            for (long i = 0; i < KERNEL_BYTES; i += L256.vectorByteSize()) {
                LongVector v1 = LongVector.fromMemorySegment(L256, ksrc1, i, ORDER);
                LongVector v2 = LongVector.fromMemorySegment(L256, ksrc2, i, ORDER);
                acc = acc.add(v1.slice(2, v2, L256_MASK).add(v2.slice(2, v1, L256_MASK)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public float float256LaneAlignedOrigin() {
            FloatVector acc = FloatVector.zero(F256);
            for (long i = 0; i < KERNEL_BYTES; i += F256.vectorByteSize()) {
                FloatVector v1 = FloatVector.fromMemorySegment(F256, kfsrc1, i, ORDER);
                FloatVector v2 = FloatVector.fromMemorySegment(F256, kfsrc2, i, ORDER);
                acc = acc.add(v1.slice(4, v2).add(v2.slice(4, v1)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public float float256LaneAlignedOriginMasked() {
            FloatVector acc = FloatVector.zero(F256);
            for (long i = 0; i < KERNEL_BYTES; i += F256.vectorByteSize()) {
                FloatVector v1 = FloatVector.fromMemorySegment(F256, kfsrc1, i, ORDER);
                FloatVector v2 = FloatVector.fromMemorySegment(F256, kfsrc2, i, ORDER);
                acc = acc.add(v1.slice(4, v2, F256_MASK).add(v2.slice(4, v1, F256_MASK)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public double double256LaneAlignedOrigin() {
            DoubleVector acc = DoubleVector.zero(D256);
            for (long i = 0; i < KERNEL_BYTES; i += D256.vectorByteSize()) {
                DoubleVector v1 = DoubleVector.fromMemorySegment(D256, kdsrc1, i, ORDER);
                DoubleVector v2 = DoubleVector.fromMemorySegment(D256, kdsrc2, i, ORDER);
                acc = acc.add(v1.slice(2, v2).add(v2.slice(2, v1)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public double double256LaneAlignedOriginMasked() {
            DoubleVector acc = DoubleVector.zero(D256);
            for (long i = 0; i < KERNEL_BYTES; i += D256.vectorByteSize()) {
                DoubleVector v1 = DoubleVector.fromMemorySegment(D256, kdsrc1, i, ORDER);
                DoubleVector v2 = DoubleVector.fromMemorySegment(D256, kdsrc2, i, ORDER);
                acc = acc.add(v1.slice(2, v2, D256_MASK).add(v2.slice(2, v1, D256_MASK)));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public byte byte256LaneUnalignedOrigin() {
            ByteVector acc = ByteVector.zero(B256);
            for (long i = 0; i < KERNEL_BYTES; i += B256.vectorByteSize()) {
                ByteVector v1 = ByteVector.fromMemorySegment(B256, ksrc1, i, ORDER);
                ByteVector v2 = ByteVector.fromMemorySegment(B256, ksrc2, i, ORDER);
                ByteVector s0 = v1.slice(3, v2).add(v1.slice(9, v2));
                ByteVector s1 = v1.slice(22, v2).add(v1.slice(29, v2));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public byte byte256LaneUnalignedOriginMasked() {
            ByteVector acc = ByteVector.zero(B256);
            for (long i = 0; i < KERNEL_BYTES; i += B256.vectorByteSize()) {
                ByteVector v1 = ByteVector.fromMemorySegment(B256, ksrc1, i, ORDER);
                ByteVector v2 = ByteVector.fromMemorySegment(B256, ksrc2, i, ORDER);
                ByteVector s0 = v1.slice(3, v2, B256_MASK).add(v1.slice(9, v2, B256_MASK));
                ByteVector s1 = v1.slice(22, v2, B256_MASK).add(v1.slice(29, v2, B256_MASK));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public short short256LaneUnalignedOrigin() {
            ShortVector acc = ShortVector.zero(S256);
            for (long i = 0; i < KERNEL_BYTES; i += S256.vectorByteSize()) {
                ShortVector v1 = ShortVector.fromMemorySegment(S256, ksrc1, i, ORDER);
                ShortVector v2 = ShortVector.fromMemorySegment(S256, ksrc2, i, ORDER);
                ShortVector s0 = v1.slice(2, v2).add(v1.slice(5, v2));
                ShortVector s1 = v1.slice(11, v2).add(v1.slice(14, v2));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public short short256LaneUnalignedOriginMasked() {
            ShortVector acc = ShortVector.zero(S256);
            for (long i = 0; i < KERNEL_BYTES; i += S256.vectorByteSize()) {
                ShortVector v1 = ShortVector.fromMemorySegment(S256, ksrc1, i, ORDER);
                ShortVector v2 = ShortVector.fromMemorySegment(S256, ksrc2, i, ORDER);
                ShortVector s0 = v1.slice(2, v2, S256_MASK).add(v1.slice(5, v2, S256_MASK));
                ShortVector s1 = v1.slice(11, v2, S256_MASK).add(v1.slice(14, v2, S256_MASK));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public int int256LaneUnalignedOrigin() {
            IntVector acc = IntVector.zero(I256);
            for (long i = 0; i < KERNEL_BYTES; i += I256.vectorByteSize()) {
                IntVector v1 = IntVector.fromMemorySegment(I256, ksrc1, i, ORDER);
                IntVector v2 = IntVector.fromMemorySegment(I256, ksrc2, i, ORDER);
                IntVector s0 = v1.slice(1, v2).add(v1.slice(2, v2));
                IntVector s1 = v1.slice(6, v2).add(v1.slice(7, v2));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public int int256LaneUnalignedOriginMasked() {
            IntVector acc = IntVector.zero(I256);
            for (long i = 0; i < KERNEL_BYTES; i += I256.vectorByteSize()) {
                IntVector v1 = IntVector.fromMemorySegment(I256, ksrc1, i, ORDER);
                IntVector v2 = IntVector.fromMemorySegment(I256, ksrc2, i, ORDER);
                IntVector s0 = v1.slice(1, v2, I256_MASK).add(v1.slice(2, v2, I256_MASK));
                IntVector s1 = v1.slice(6, v2, I256_MASK).add(v1.slice(7, v2, I256_MASK));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public long long256LaneUnalignedOrigin() {
            LongVector acc = LongVector.zero(L256);
            for (long i = 0; i < KERNEL_BYTES; i += L256.vectorByteSize()) {
                LongVector v1 = LongVector.fromMemorySegment(L256, ksrc1, i, ORDER);
                LongVector v2 = LongVector.fromMemorySegment(L256, ksrc2, i, ORDER);
                LongVector s0 = v1.slice(1, v2).add(v1.slice(3, v2));
                LongVector s1 = v2.slice(1, v1).add(v2.slice(3, v1));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public long long256LaneUnalignedOriginMasked() {
            LongVector acc = LongVector.zero(L256);
            for (long i = 0; i < KERNEL_BYTES; i += L256.vectorByteSize()) {
                LongVector v1 = LongVector.fromMemorySegment(L256, ksrc1, i, ORDER);
                LongVector v2 = LongVector.fromMemorySegment(L256, ksrc2, i, ORDER);
                LongVector s0 = v1.slice(1, v2, L256_MASK).add(v1.slice(3, v2, L256_MASK));
                LongVector s1 = v2.slice(1, v1, L256_MASK).add(v2.slice(3, v1, L256_MASK));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public float float256LaneUnalignedOrigin() {
            FloatVector acc = FloatVector.zero(F256);
            for (long i = 0; i < KERNEL_BYTES; i += F256.vectorByteSize()) {
                FloatVector v1 = FloatVector.fromMemorySegment(F256, kfsrc1, i, ORDER);
                FloatVector v2 = FloatVector.fromMemorySegment(F256, kfsrc2, i, ORDER);
                FloatVector s0 = v1.slice(1, v2).add(v1.slice(2, v2));
                FloatVector s1 = v1.slice(6, v2).add(v1.slice(7, v2));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public float float256LaneUnalignedOriginMasked() {
            FloatVector acc = FloatVector.zero(F256);
            for (long i = 0; i < KERNEL_BYTES; i += F256.vectorByteSize()) {
                FloatVector v1 = FloatVector.fromMemorySegment(F256, kfsrc1, i, ORDER);
                FloatVector v2 = FloatVector.fromMemorySegment(F256, kfsrc2, i, ORDER);
                FloatVector s0 = v1.slice(1, v2, F256_MASK).add(v1.slice(2, v2, F256_MASK));
                FloatVector s1 = v1.slice(6, v2, F256_MASK).add(v1.slice(7, v2, F256_MASK));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public double double256LaneUnalignedOrigin() {
            DoubleVector acc = DoubleVector.zero(D256);
            for (long i = 0; i < KERNEL_BYTES; i += D256.vectorByteSize()) {
                DoubleVector v1 = DoubleVector.fromMemorySegment(D256, kdsrc1, i, ORDER);
                DoubleVector v2 = DoubleVector.fromMemorySegment(D256, kdsrc2, i, ORDER);
                DoubleVector s0 = v1.slice(1, v2).add(v1.slice(3, v2));
                DoubleVector s1 = v2.slice(1, v1).add(v2.slice(3, v1));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }

        @Benchmark
        public double double256LaneUnalignedOriginMasked() {
            DoubleVector acc = DoubleVector.zero(D256);
            for (long i = 0; i < KERNEL_BYTES; i += D256.vectorByteSize()) {
                DoubleVector v1 = DoubleVector.fromMemorySegment(D256, kdsrc1, i, ORDER);
                DoubleVector v2 = DoubleVector.fromMemorySegment(D256, kdsrc2, i, ORDER);
                DoubleVector s0 = v1.slice(1, v2, D256_MASK).add(v1.slice(3, v2, D256_MASK));
                DoubleVector s1 = v2.slice(1, v1, D256_MASK).add(v2.slice(3, v1, D256_MASK));
                acc = acc.add(s0.add(s1));
            }
            return acc.reduceLanes(VectorOperators.ADD);
        }
    }
}
