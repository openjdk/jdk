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
    MemorySegment kdst;
    MemorySegment kfsrc1;
    MemorySegment kfsrc2;
    MemorySegment kfdst;
    MemorySegment kdsrc1;
    MemorySegment kdsrc2;
    MemorySegment kddst;

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
        kdst   = kernelOperand(arena, r, 'i');
        kfsrc1 = kernelOperand(arena, r, 'f');
        kfsrc2 = kernelOperand(arena, r, 'f');
        kfdst  = kernelOperand(arena, r, 'f');
        kdsrc1 = kernelOperand(arena, r, 'd');
        kdsrc2 = kernelOperand(arena, r, 'd');
        kddst  = kernelOperand(arena, r, 'd');
        vidx0 = 1;
        vidx1 = 2;
        vidx2 = 3;
        vidx3 = 4;
    }

    @Benchmark
    public void byte128ConstOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += B128.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B128, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B128, ksrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(5, v2)
              .slice(9, v2)
              .slice(13, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte128ConstOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += B128.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B128, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B128, ksrc2, i, ORDER);
            v1.slice(1, v2, B128_MASK)
              .slice(5, v2, B128_MASK)
              .slice(9, v2, B128_MASK)
              .slice(13, v2, B128_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte256LaneAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += B256.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B256, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B256, ksrc2, i, ORDER);
            v1.slice(16, v2)
              .slice(16, v1)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte256LaneAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += B256.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B256, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B256, ksrc2, i, ORDER);
            v1.slice(16, v2, B256_MASK)
              .slice(16, v1, B256_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte256LaneUnalignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += B256.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B256, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B256, ksrc2, i, ORDER);
            v1.slice(3, v2)
              .slice(9, v2)
              .slice(22, v2)
              .slice(29, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte256LaneUnalignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += B256.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B256, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B256, ksrc2, i, ORDER);
            v1.slice(3, v2, B256_MASK)
              .slice(9, v2, B256_MASK)
              .slice(22, v2, B256_MASK)
              .slice(29, v2, B256_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte512DwordAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            v1.slice(4, v2)
              .slice(20, v2)
              .slice(36, v2)
              .slice(52, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte512DwordAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            v1.slice(4, v2, B512_MASK)
              .slice(20, v2, B512_MASK)
              .slice(36, v2, B512_MASK)
              .slice(52, v2, B512_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte512SubDwordOriginMidRange() {
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            v1.slice(17, v2)
              .slice(26, v2)
              .slice(35, v2)
              .slice(46, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte512SubDwordOriginMidRangeMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            v1.slice(17, v2, B512_MASK)
              .slice(26, v2, B512_MASK)
              .slice(35, v2, B512_MASK)
              .slice(46, v2, B512_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte512SubDwordOriginEdgeRange() {
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            v1.slice(3, v2)
              .slice(14, v2)
              .slice(51, v2)
              .slice(62, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void byte512SubDwordOriginEdgeRangeMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            v1.slice(3, v2, B512_MASK)
              .slice(14, v2, B512_MASK)
              .slice(51, v2, B512_MASK)
              .slice(62, v2, B512_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short128ConstOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += S128.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S128, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S128, ksrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(3, v2)
              .slice(5, v2)
              .slice(7, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short128ConstOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += S128.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S128, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S128, ksrc2, i, ORDER);
            v1.slice(1, v2, S128_MASK)
              .slice(3, v2, S128_MASK)
              .slice(5, v2, S128_MASK)
              .slice(7, v2, S128_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short256LaneAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += S256.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S256, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S256, ksrc2, i, ORDER);
            v1.slice(8, v2)
              .slice(8, v1)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short256LaneAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += S256.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S256, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S256, ksrc2, i, ORDER);
            v1.slice(8, v2, S256_MASK)
              .slice(8, v1, S256_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short256LaneUnalignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += S256.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S256, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S256, ksrc2, i, ORDER);
            v1.slice(2, v2)
              .slice(5, v2)
              .slice(11, v2)
              .slice(14, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short256LaneUnalignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += S256.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S256, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S256, ksrc2, i, ORDER);
            v1.slice(2, v2, S256_MASK)
              .slice(5, v2, S256_MASK)
              .slice(11, v2, S256_MASK)
              .slice(14, v2, S256_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short512DwordAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            v1.slice(2, v2)
              .slice(10, v2)
              .slice(18, v2)
              .slice(26, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short512DwordAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            v1.slice(2, v2, S512_MASK)
              .slice(10, v2, S512_MASK)
              .slice(18, v2, S512_MASK)
              .slice(26, v2, S512_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short512SubDwordOriginMidRange() {
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            v1.slice(9, v2)
              .slice(13, v2)
              .slice(17, v2)
              .slice(21, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short512SubDwordOriginMidRangeMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            v1.slice(9, v2, S512_MASK)
              .slice(13, v2, S512_MASK)
              .slice(17, v2, S512_MASK)
              .slice(21, v2, S512_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short512SubDwordOriginEdgeRange() {
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            v1.slice(3, v2)
              .slice(7, v2)
              .slice(25, v2)
              .slice(29, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short512SubDwordOriginEdgeRangeMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            v1.slice(3, v2, S512_MASK)
              .slice(7, v2, S512_MASK)
              .slice(25, v2, S512_MASK)
              .slice(29, v2, S512_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int128ConstOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += I128.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I128, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I128, ksrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(2, v2)
              .slice(3, v2)
              .slice(1, v1)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int128ConstOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += I128.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I128, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I128, ksrc2, i, ORDER);
            v1.slice(1, v2, I128_MASK)
              .slice(2, v2, I128_MASK)
              .slice(3, v2, I128_MASK)
              .slice(1, v1, I128_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int256LaneAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += I256.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I256, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I256, ksrc2, i, ORDER);
            v1.slice(4, v2)
              .slice(4, v1)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int256LaneAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += I256.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I256, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I256, ksrc2, i, ORDER);
            v1.slice(4, v2, I256_MASK)
              .slice(4, v1, I256_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int256LaneUnalignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += I256.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I256, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I256, ksrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(2, v2)
              .slice(6, v2)
              .slice(7, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int256LaneUnalignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += I256.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I256, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I256, ksrc2, i, ORDER);
            v1.slice(1, v2, I256_MASK)
              .slice(2, v2, I256_MASK)
              .slice(6, v2, I256_MASK)
              .slice(7, v2, I256_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int512DwordAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += I512.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I512, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I512, ksrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(5, v2)
              .slice(9, v2)
              .slice(13, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int512DwordAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += I512.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I512, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I512, ksrc2, i, ORDER);
            v1.slice(1, v2, I512_MASK)
              .slice(5, v2, I512_MASK)
              .slice(9, v2, I512_MASK)
              .slice(13, v2, I512_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long128ConstOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += L128.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L128, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L128, ksrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(1, v1)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long128ConstOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += L128.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L128, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L128, ksrc2, i, ORDER);
            v1.slice(1, v2, L128_MASK)
              .slice(1, v1, L128_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long256LaneAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += L256.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L256, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L256, ksrc2, i, ORDER);
            v1.slice(2, v2)
              .slice(2, v1)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long256LaneAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += L256.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L256, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L256, ksrc2, i, ORDER);
            v1.slice(2, v2, L256_MASK)
              .slice(2, v1, L256_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long256LaneUnalignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += L256.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L256, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L256, ksrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(3, v2)
              .slice(1, v1)
              .slice(3, v1)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long256LaneUnalignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += L256.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L256, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L256, ksrc2, i, ORDER);
            v1.slice(1, v2, L256_MASK)
              .slice(3, v2, L256_MASK)
              .slice(1, v1, L256_MASK)
              .slice(3, v1, L256_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long512DwordAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += L512.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L512, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L512, ksrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(3, v2)
              .slice(5, v2)
              .slice(7, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long512DwordAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += L512.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L512, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L512, ksrc2, i, ORDER);
            v1.slice(1, v2, L512_MASK)
              .slice(3, v2, L512_MASK)
              .slice(5, v2, L512_MASK)
              .slice(7, v2, L512_MASK)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void float128ConstOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += F128.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F128, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F128, kfsrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(2, v2)
              .slice(3, v2)
              .slice(1, v1)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void float128ConstOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += F128.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F128, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F128, kfsrc2, i, ORDER);
            v1.slice(1, v2, F128_MASK)
              .slice(2, v2, F128_MASK)
              .slice(3, v2, F128_MASK)
              .slice(1, v1, F128_MASK)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void float256LaneAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += F256.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F256, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F256, kfsrc2, i, ORDER);
            v1.slice(4, v2)
              .slice(4, v1)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void float256LaneAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += F256.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F256, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F256, kfsrc2, i, ORDER);
            v1.slice(4, v2, F256_MASK)
              .slice(4, v1, F256_MASK)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void float256LaneUnalignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += F256.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F256, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F256, kfsrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(2, v2)
              .slice(6, v2)
              .slice(7, v2)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void float256LaneUnalignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += F256.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F256, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F256, kfsrc2, i, ORDER);
            v1.slice(1, v2, F256_MASK)
              .slice(2, v2, F256_MASK)
              .slice(6, v2, F256_MASK)
              .slice(7, v2, F256_MASK)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void float512DwordAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += F512.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F512, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F512, kfsrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(5, v2)
              .slice(9, v2)
              .slice(13, v2)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void float512DwordAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += F512.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F512, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F512, kfsrc2, i, ORDER);
            v1.slice(1, v2, F512_MASK)
              .slice(5, v2, F512_MASK)
              .slice(9, v2, F512_MASK)
              .slice(13, v2, F512_MASK)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void double128ConstOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += D128.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D128, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D128, kdsrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(1, v1)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }

    @Benchmark
    public void double128ConstOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += D128.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D128, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D128, kdsrc2, i, ORDER);
            v1.slice(1, v2, D128_MASK)
              .slice(1, v1, D128_MASK)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }

    @Benchmark
    public void double256LaneAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += D256.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D256, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D256, kdsrc2, i, ORDER);
            v1.slice(2, v2)
              .slice(2, v1)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }

    @Benchmark
    public void double256LaneAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += D256.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D256, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D256, kdsrc2, i, ORDER);
            v1.slice(2, v2, D256_MASK)
              .slice(2, v1, D256_MASK)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }

    @Benchmark
    public void double256LaneUnalignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += D256.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D256, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D256, kdsrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(3, v2)
              .slice(1, v1)
              .slice(3, v1)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }

    @Benchmark
    public void double256LaneUnalignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += D256.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D256, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D256, kdsrc2, i, ORDER);
            v1.slice(1, v2, D256_MASK)
              .slice(3, v2, D256_MASK)
              .slice(1, v1, D256_MASK)
              .slice(3, v1, D256_MASK)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }

    @Benchmark
    public void double512DwordAlignedOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += D512.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D512, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D512, kdsrc2, i, ORDER);
            v1.slice(1, v2)
              .slice(3, v2)
              .slice(5, v2)
              .slice(7, v2)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }

    @Benchmark
    public void double512DwordAlignedOriginMasked() {
        for (long i = 0; i < KERNEL_BYTES; i += D512.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D512, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D512, kdsrc2, i, ORDER);
            v1.slice(1, v2, D512_MASK)
              .slice(3, v2, D512_MASK)
              .slice(5, v2, D512_MASK)
              .slice(7, v2, D512_MASK)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }

    @Benchmark
    public void byte512VariableOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += B512.vectorByteSize()) {
            ByteVector v1 = ByteVector.fromMemorySegment(B512, ksrc1, i, ORDER);
            ByteVector v2 = ByteVector.fromMemorySegment(B512, ksrc2, i, ORDER);
            v1.slice(vidx0, v2)
              .slice(vidx1, v2)
              .slice(vidx2, v2)
              .slice(vidx3, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void short512VariableOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += S512.vectorByteSize()) {
            ShortVector v1 = ShortVector.fromMemorySegment(S512, ksrc1, i, ORDER);
            ShortVector v2 = ShortVector.fromMemorySegment(S512, ksrc2, i, ORDER);
            v1.slice(vidx0, v2)
              .slice(vidx1, v2)
              .slice(vidx2, v2)
              .slice(vidx3, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void int512VariableOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += I512.vectorByteSize()) {
            IntVector v1 = IntVector.fromMemorySegment(I512, ksrc1, i, ORDER);
            IntVector v2 = IntVector.fromMemorySegment(I512, ksrc2, i, ORDER);
            v1.slice(vidx0, v2)
              .slice(vidx1, v2)
              .slice(vidx2, v2)
              .slice(vidx3, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void long512VariableOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += L512.vectorByteSize()) {
            LongVector v1 = LongVector.fromMemorySegment(L512, ksrc1, i, ORDER);
            LongVector v2 = LongVector.fromMemorySegment(L512, ksrc2, i, ORDER);
            v1.slice(vidx0, v2)
              .slice(vidx1, v2)
              .slice(vidx2, v2)
              .slice(vidx3, v2)
              .intoMemorySegment(kdst, i, ORDER);
        }
    }

    @Benchmark
    public void float512VariableOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += F512.vectorByteSize()) {
            FloatVector v1 = FloatVector.fromMemorySegment(F512, kfsrc1, i, ORDER);
            FloatVector v2 = FloatVector.fromMemorySegment(F512, kfsrc2, i, ORDER);
            v1.slice(vidx0, v2)
              .slice(vidx1, v2)
              .slice(vidx2, v2)
              .slice(vidx3, v2)
              .intoMemorySegment(kfdst, i, ORDER);
        }
    }

    @Benchmark
    public void double512VariableOrigin() {
        for (long i = 0; i < KERNEL_BYTES; i += D512.vectorByteSize()) {
            DoubleVector v1 = DoubleVector.fromMemorySegment(D512, kdsrc1, i, ORDER);
            DoubleVector v2 = DoubleVector.fromMemorySegment(D512, kdsrc2, i, ORDER);
            v1.slice(vidx0, v2)
              .slice(vidx1, v2)
              .slice(vidx2, v2)
              .slice(vidx3, v2)
              .intoMemorySegment(kddst, i, ORDER);
        }
    }
}
