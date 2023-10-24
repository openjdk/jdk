/*
 *  Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.vector.*;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class GatherOperationsBenchmark {
    @Param({"64", "256", "1024", "4096"})
    int SIZE;
    byte  [] barr;
    byte  [] bres;
    short [] sarr;
    short [] sres;
    int   [] index;

    static final VectorSpecies<Short> S64 = ShortVector.SPECIES_64;
    static final VectorSpecies<Short> S128 = ShortVector.SPECIES_128;
    static final VectorSpecies<Short> S256 = ShortVector.SPECIES_256;
    static final VectorSpecies<Short> S512 = ShortVector.SPECIES_512;
    static final VectorSpecies<Byte> B64 = ByteVector.SPECIES_64;
    static final VectorSpecies<Byte> B128 = ByteVector.SPECIES_128;
    static final VectorSpecies<Byte> B256 = ByteVector.SPECIES_256;
    static final VectorSpecies<Byte> B512 = ByteVector.SPECIES_512;

    @Setup(Level.Trial)
    public void BmSetup() {
        Random r = new Random(1245);
        index = new int[SIZE];
        barr = new byte[SIZE];
        bres = new byte[SIZE];
        sarr = new short[SIZE];
        sres = new short[SIZE];
        for (int i = 0; i < SIZE; i++) {
           barr[i] = (byte)i;
           sarr[i] = (short)i;
           index[i] = r.nextInt(SIZE-1);
        }
    }



    @Benchmark
    public void microByteGather64() {
        for (int i = 0; i < SIZE; i += B64.length()) {
            ByteVector.fromArray(B64, barr, 0, index, i)
                            .intoArray(bres, i);
        }
    }


    @Benchmark
    public void microByteGather64_NZ_OFF() {
        for (int i = 0; i < SIZE; i += B64.length()) {
            ByteVector.fromArray(B64, barr, 1, index, i)
                            .intoArray(bres, i);
        }
    }

    @Benchmark
    public void microByteGather64_MASK() {
        VectorMask<Byte> VMASK = VectorMask.fromLong(B64, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += B64.length()) {
            ByteVector.fromArray(B64, barr, 0, index, i, VMASK)
                            .intoArray(bres, i);
        }
    }

    @Benchmark
    public void microByteGather64_MASK_NZ_OFF() {
        VectorMask<Byte> VMASK = VectorMask.fromLong(B64, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += B64.length()) {
            ByteVector.fromArray(B64, barr, 1, index, i, VMASK)
                            .intoArray(bres, i);
        }
    }


    @Benchmark
    public void microByteGather128() {
        for (int i = 0; i < SIZE; i += B128.length()) {
            ByteVector.fromArray(B128, barr, 0, index, i)
                            .intoArray(bres, i);
        }
    }


    @Benchmark
    public void microByteGather128_NZ_OFF() {
        for (int i = 0; i < SIZE; i += B128.length()) {
            ByteVector.fromArray(B128, barr, 1, index, i)
                            .intoArray(bres, i);
        }
    }

    @Benchmark
    public void microByteGather128_MASK() {
        VectorMask<Byte> VMASK = VectorMask.fromLong(B128, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += B128.length()) {
            ByteVector.fromArray(B128, barr, 0, index, i, VMASK)
                            .intoArray(bres, i);
        }
    }

    @Benchmark
    public void microByteGather128_MASK_NZ_OFF() {
        VectorMask<Byte> VMASK = VectorMask.fromLong(B128, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += B128.length()) {
            ByteVector.fromArray(B128, barr, 1, index, i, VMASK)
                            .intoArray(bres, i);
        }
    }


    @Benchmark
    public void microByteGather256() {
        for (int i = 0; i < SIZE; i += B256.length()) {
            ByteVector.fromArray(B256, barr, 0, index, i)
                            .intoArray(bres, i);
        }
    }


    @Benchmark
    public void microByteGather256_NZ_OFF() {
        for (int i = 0; i < SIZE; i += B256.length()) {
            ByteVector.fromArray(B256, barr, 1, index, i)
                            .intoArray(bres, i);
        }
    }

    @Benchmark
    public void microByteGather256_MASK() {
        VectorMask<Byte> VMASK = VectorMask.fromLong(B256, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += B256.length()) {
            ByteVector.fromArray(B256, barr, 0, index, i, VMASK)
                            .intoArray(bres, i);
        }
    }

    @Benchmark
    public void microByteGather256_MASK_NZ_OFF() {
        VectorMask<Byte> VMASK = VectorMask.fromLong(B256, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += B256.length()) {
            ByteVector.fromArray(B256, barr, 1, index, i, VMASK)
                            .intoArray(bres, i);
        }
    }


    @Benchmark
    public void microByteGather512() {
        for (int i = 0; i < SIZE; i += B512.length()) {
            ByteVector.fromArray(B512, barr, 0, index, i)
                            .intoArray(bres, i);
        }
    }


    @Benchmark
    public void microByteGather512_NZ_OFF() {
        for (int i = 0; i < SIZE; i += B512.length()) {
            ByteVector.fromArray(B512, barr, 1, index, i)
                            .intoArray(bres, i);
        }
    }

    @Benchmark
    public void microByteGather512_MASK() {
        VectorMask<Byte> VMASK = VectorMask.fromLong(B512, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += B512.length()) {
            ByteVector.fromArray(B512, barr, 0, index, i, VMASK)
                            .intoArray(bres, i);
        }
    }

    @Benchmark
    public void microByteGather512_MASK_NZ_OFF() {
        VectorMask<Byte> VMASK = VectorMask.fromLong(B512, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += B512.length()) {
            ByteVector.fromArray(B512, barr, 1, index, i, VMASK)
                            .intoArray(bres, i);
        }
    }


    @Benchmark
    public void microShortGather64() {
        for (int i = 0; i < SIZE; i += S64.length()) {
            ShortVector.fromArray(S64, sarr, 0, index, i)
                            .intoArray(sres, i);
        }
    }


    @Benchmark
    public void microShortGather64_NZ_OFF() {
        for (int i = 0; i < SIZE; i += S64.length()) {
            ShortVector.fromArray(S64, sarr, 1, index, i)
                            .intoArray(sres, i);
        }
    }

    @Benchmark
    public void microShortGather64_MASK() {
        VectorMask<Short> VMASK = VectorMask.fromLong(S64, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += S64.length()) {
            ShortVector.fromArray(S64, sarr, 0, index, i, VMASK)
                            .intoArray(sres, i);
        }
    }

    @Benchmark
    public void microShortGather64_MASK_NZ_OFF() {
        VectorMask<Short> VMASK = VectorMask.fromLong(S64, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += S64.length()) {
            ShortVector.fromArray(S64, sarr, 1, index, i, VMASK)
                            .intoArray(sres, i);
        }
    }


    @Benchmark
    public void microShortGather128() {
        for (int i = 0; i < SIZE; i += S128.length()) {
            ShortVector.fromArray(S128, sarr, 0, index, i)
                            .intoArray(sres, i);
        }
    }


    @Benchmark
    public void microShortGather128_NZ_OFF() {
        for (int i = 0; i < SIZE; i += S128.length()) {
            ShortVector.fromArray(S128, sarr, 1, index, i)
                            .intoArray(sres, i);
        }
    }

    @Benchmark
    public void microShortGather128_MASK() {
        VectorMask<Short> VMASK = VectorMask.fromLong(S128, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += S128.length()) {
            ShortVector.fromArray(S128, sarr, 0, index, i, VMASK)
                            .intoArray(sres, i);
        }
    }

    @Benchmark
    public void microShortGather128_MASK_NZ_OFF() {
        VectorMask<Short> VMASK = VectorMask.fromLong(S128, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += S128.length()) {
            ShortVector.fromArray(S128, sarr, 1, index, i, VMASK)
                            .intoArray(sres, i);
        }
    }


    @Benchmark
    public void microShortGather256() {
        for (int i = 0; i < SIZE; i += S256.length()) {
            ShortVector.fromArray(S256, sarr, 0, index, i)
                            .intoArray(sres, i);
        }
    }


    @Benchmark
    public void microShortGather256_NZ_OFF() {
        for (int i = 0; i < SIZE; i += S256.length()) {
            ShortVector.fromArray(S256, sarr, 1, index, i)
                            .intoArray(sres, i);
        }
    }

    @Benchmark
    public void microShortGather256_MASK() {
        VectorMask<Short> VMASK = VectorMask.fromLong(S256, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += S256.length()) {
            ShortVector.fromArray(S256, sarr, 0, index, i, VMASK)
                            .intoArray(sres, i);
        }
    }

    @Benchmark
    public void microShortGather256_MASK_NZ_OFF() {
        VectorMask<Short> VMASK = VectorMask.fromLong(S256, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += S256.length()) {
            ShortVector.fromArray(S256, sarr, 1, index, i, VMASK)
                            .intoArray(sres, i);
        }
    }


    @Benchmark
    public void microShortGather512() {
        for (int i = 0; i < SIZE; i += S512.length()) {
            ShortVector.fromArray(S512, sarr, 0, index, i)
                            .intoArray(sres, i);
        }
    }


    @Benchmark
    public void microShortGather512_NZ_OFF() {
        for (int i = 0; i < SIZE; i += S512.length()) {
            ShortVector.fromArray(S512, sarr, 1, index, i)
                            .intoArray(sres, i);
        }
    }

    @Benchmark
    public void microShortGather512_MASK() {
        VectorMask<Short> VMASK = VectorMask.fromLong(S512, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += S512.length()) {
            ShortVector.fromArray(S512, sarr, 0, index, i, VMASK)
                            .intoArray(sres, i);
        }
    }

    @Benchmark
    public void microShortGather512_MASK_NZ_OFF() {
        VectorMask<Short> VMASK = VectorMask.fromLong(S512, 0x5555555555555555L);
        for (int i = 0; i < SIZE; i += S512.length()) {
            ShortVector.fromArray(S512, sarr, 1, index, i, VMASK)
                            .intoArray(sres, i);
        }
    }
}
