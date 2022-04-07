/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class MaskedLogicOpts {
    @Param({"256","512","1024"})
    private int ARRAYLEN;

    boolean [] mask_arr = {
        false, false, false, true, false, false, false, false,
        false, false, false, true, false, false, false, false,
        false, false, false, true, false, false, false, false,
        true, true, true, true, true, true, true, true,
        true, true, true, true, true, true, true, true,
        false, false, false, true, false, false, false, false,
        false, false, false, true, false, false, false, false,
        false, false, false, true, false, false, false, false
    };

    int INVOC_COUNTER = 4096;

    int [] i1 = new int[ARRAYLEN];
    int [] i2 = new int[ARRAYLEN];
    int [] i3 = new int[ARRAYLEN];
    int [] i4 = new int[ARRAYLEN];
    int [] i5 = new int[ARRAYLEN];

    long [] l1 = new long[ARRAYLEN];
    long [] l2 = new long[ARRAYLEN];
    long [] l3 = new long[ARRAYLEN];
    long [] l4 = new long[ARRAYLEN];
    long [] l5 = new long[ARRAYLEN];

    Vector<Integer> iv1;
    Vector<Integer> iv2;
    Vector<Integer> iv3;
    Vector<Integer> iv4;
    Vector<Integer> iv5;

    Vector<Long> lv1;
    Vector<Long> lv2;
    Vector<Long> lv3;
    Vector<Long> lv4;
    Vector<Long> lv5;

    VectorMask<Integer> imask;
    VectorMask<Long> lmask;

    VectorSpecies<Integer> ispecies;
    VectorSpecies<Long> lspecies;

    int int512_arr_idx;
    int int256_arr_idx;
    int int128_arr_idx;
    int long256_arr_idx;
    int long512_arr_idx;

    private Random r = new Random();

    @Setup(Level.Trial)
    public void init() {
        int512_arr_idx = 0;
        int256_arr_idx = 0;
        int128_arr_idx = 0;
        long256_arr_idx = 0;
        long512_arr_idx = 0;
        i1 = new int[ARRAYLEN];
        i2 = new int[ARRAYLEN];
        i3 = new int[ARRAYLEN];
        i4 = new int[ARRAYLEN];
        i5 = new int[ARRAYLEN];

        l1 = new long[ARRAYLEN];
        l2 = new long[ARRAYLEN];
        l3 = new long[ARRAYLEN];
        l4 = new long[ARRAYLEN];
        l5 = new long[ARRAYLEN];

        for (int i=0; i<ARRAYLEN; i++) {
            i1[i] = r.nextInt();
            i2[i] = r.nextInt();
            i3[i] = r.nextInt();
            i4[i] = r.nextInt();
            i5[i] = r.nextInt();

            l1[i] = r.nextLong();
            l2[i] = r.nextLong();
            l3[i] = r.nextLong();
            l4[i] = r.nextLong();
            l5[i] = r.nextLong();
        }

    }

    @Setup(Level.Invocation)
    public void init_per_invoc() {
        int512_arr_idx = (int512_arr_idx + 16) & (ARRAYLEN-1);
        int256_arr_idx = (int256_arr_idx + 8) & (ARRAYLEN-1);
        int128_arr_idx = (int128_arr_idx + 4) & (ARRAYLEN-1);
        long512_arr_idx = (long512_arr_idx + 8) & (ARRAYLEN-1);
        long256_arr_idx = (long256_arr_idx + 4) & (ARRAYLEN-1);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    public void maskedLogicKernel(VectorSpecies<Integer> SPECIES) {
        imask = VectorMask.fromArray(SPECIES, mask_arr, 0);
        iv2 = IntVector.fromArray(SPECIES, i2, int512_arr_idx);
        iv3 = IntVector.fromArray(SPECIES, i3, int512_arr_idx);
        iv4 = IntVector.fromArray(SPECIES, i4, int512_arr_idx);
        iv5 = IntVector.fromArray(SPECIES, i5, int512_arr_idx);
        for(int i = 0; i < INVOC_COUNTER; i++) {
            for(int j = 0 ; j < ARRAYLEN; j+= SPECIES.length()) {
                IntVector.fromArray(SPECIES, i1, j)
                    .lanewise(VectorOperators.AND, iv2, imask)
                    .lanewise(VectorOperators.OR,  iv2, imask)
                    .lanewise(VectorOperators.AND, iv3, imask)
                    .lanewise(VectorOperators.OR,  iv3, imask)
                    .lanewise(VectorOperators.AND, iv4, imask)
                    .lanewise(VectorOperators.OR,  iv4, imask)
                    .lanewise(VectorOperators.AND, iv5, imask)
                    .lanewise(VectorOperators.XOR, iv5, imask)
                    .intoArray(i1, j);
            }
        }
    }

    @Benchmark
    public void maskedLogicOperationsInt512() {
       maskedLogicKernel(IntVector.SPECIES_512);
    }

    @Benchmark
    public void maskedLogicOperationsInt256() {
       maskedLogicKernel(IntVector.SPECIES_256);
    }

    @Benchmark
    public void maskedLogicOperationsInt128() {
       maskedLogicKernel(IntVector.SPECIES_128);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    public void partiallyMaskedLogicOperationsIntKernel(VectorSpecies<Integer> SPECIES) {
       imask = VectorMask.fromArray(SPECIES, mask_arr, 0);
       iv2 = IntVector.fromArray(SPECIES, i2, int512_arr_idx);
       iv3 = IntVector.fromArray(SPECIES, i3, int512_arr_idx);
       iv4 = IntVector.fromArray(SPECIES, i4, int512_arr_idx);
       iv5 = IntVector.fromArray(SPECIES, i5, int512_arr_idx);
       for(int i = 0; i < INVOC_COUNTER; i++) {
           for(int j = 0 ; j < ARRAYLEN; j+= SPECIES.length()) {
               IntVector.fromArray(SPECIES, i1, j)
                   .lanewise(VectorOperators.AND, iv2, imask)
                   .lanewise(VectorOperators.OR,  iv2, imask)
                   .lanewise(VectorOperators.AND, iv3)
                   .lanewise(VectorOperators.OR,  iv3)
                   .lanewise(VectorOperators.OR,  iv4, imask)
                   .lanewise(VectorOperators.AND, iv4, imask)
                   .lanewise(VectorOperators.XOR, iv5, imask)
                   .intoArray(i1, j);
           }
       }
    }

    @Benchmark
    public void partiallyMaskedLogicOperationsInt512() {
        partiallyMaskedLogicOperationsIntKernel(IntVector.SPECIES_512);
    }

    @Benchmark
    public void partiallyMaskedLogicOperationsInt256() {
        partiallyMaskedLogicOperationsIntKernel(IntVector.SPECIES_256);
    }

    @Benchmark
    public void partiallyMaskedLogicOperationsInt128() {
        partiallyMaskedLogicOperationsIntKernel(IntVector.SPECIES_128);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    public void bitwiseBlendOperationIntKernel(VectorSpecies<Integer> SPECIES) {
       imask = VectorMask.fromArray(SPECIES, mask_arr, 0);
       iv2 = IntVector.fromArray(SPECIES, i2, int512_arr_idx);
       iv3 = IntVector.fromArray(SPECIES, i3, int512_arr_idx);
       iv4 = IntVector.fromArray(SPECIES, i4, int512_arr_idx);
       iv5 = IntVector.fromArray(SPECIES, i5, int512_arr_idx);
       for(int i = 0; i < INVOC_COUNTER; i++) {
           for(int j = 0 ; j < ARRAYLEN; j+= SPECIES.length()) {
               IntVector.fromArray(SPECIES, i1, j)
                   .lanewise(VectorOperators.BITWISE_BLEND, iv2, iv3, imask)
                   .lanewise(VectorOperators.BITWISE_BLEND, iv3, iv4, imask)
                   .lanewise(VectorOperators.BITWISE_BLEND, iv4, iv5, imask)
                   .intoArray(i1, j);
           }
       }
    }

    @Benchmark
    public void bitwiseBlendOperationInt512() {
       bitwiseBlendOperationIntKernel(IntVector.SPECIES_512);
    }

    @Benchmark
    public void bitwiseBlendOperationInt256() {
       bitwiseBlendOperationIntKernel(IntVector.SPECIES_256);
    }

    @Benchmark
    public void bitwiseBlendOperationInt128() {
       bitwiseBlendOperationIntKernel(IntVector.SPECIES_128);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    public void maskedLogicOperationsLongKernel(VectorSpecies<Long> SPECIES) {
       lmask = VectorMask.fromArray(SPECIES, mask_arr, 0);
       lv2 = LongVector.fromArray(SPECIES, l2, long256_arr_idx);
       lv3 = LongVector.fromArray(SPECIES, l3, long256_arr_idx);
       lv4 = LongVector.fromArray(SPECIES, l4, long256_arr_idx);
       lv5 = LongVector.fromArray(SPECIES, l5, long256_arr_idx);
       for(int i = 0; i < INVOC_COUNTER; i++) {
           for(int j = 0 ; j < ARRAYLEN; j+= SPECIES.length()) {
               LongVector.fromArray(SPECIES, l1, j)
                   .lanewise(VectorOperators.AND, lv2, lmask)
                   .lanewise(VectorOperators.OR,  lv3, lmask)
                   .lanewise(VectorOperators.AND, lv3, lmask)
                   .lanewise(VectorOperators.OR,  lv4, lmask)
                   .lanewise(VectorOperators.AND, lv4, lmask)
                   .lanewise(VectorOperators.XOR, lv5, lmask)
                   .intoArray(l1, j);
           }
       }
    }

    @Benchmark
    public void maskedLogicOperationsLong512() {
       maskedLogicOperationsLongKernel(LongVector.SPECIES_512);
    }
    @Benchmark
    public void maskedLogicOperationsLong256() {
       maskedLogicOperationsLongKernel(LongVector.SPECIES_256);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    public void partiallyMaskedLogicOperationsLongKernel(VectorSpecies<Long> SPECIES) {
       lmask = VectorMask.fromArray(SPECIES, mask_arr, 0);
       lv2 = LongVector.fromArray(SPECIES, l2, long512_arr_idx);
       lv3 = LongVector.fromArray(SPECIES, l3, long512_arr_idx);
       lv4 = LongVector.fromArray(SPECIES, l4, long512_arr_idx);
       lv5 = LongVector.fromArray(SPECIES, l5, long512_arr_idx);
       for(int i = 0; i < INVOC_COUNTER; i++) {
           for(int j = 0 ; j < ARRAYLEN; j+= SPECIES.length()) {
               LongVector.fromArray(SPECIES, l1, j)
                   .lanewise(VectorOperators.AND, lv2, lmask)
                   .lanewise(VectorOperators.OR,  lv2, lmask)
                   .lanewise(VectorOperators.AND, lv3)
                   .lanewise(VectorOperators.OR,  lv3)
                   .lanewise(VectorOperators.AND, lv4)
                   .lanewise(VectorOperators.OR,  lv4, lmask)
                   .lanewise(VectorOperators.XOR, lv5, lmask)
                   .intoArray(l1, j);
           }
       }
    }

    @Benchmark
    public void partiallyMaskedLogicOperationsLong512() {
       partiallyMaskedLogicOperationsLongKernel(LongVector.SPECIES_512);
    }

    @Benchmark
    public void partiallyMaskedLogicOperationsLong256() {
       partiallyMaskedLogicOperationsLongKernel(LongVector.SPECIES_256);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    public void bitwiseBlendOperationLongKernel(VectorSpecies<Long> SPECIES) {
       lmask = VectorMask.fromArray(SPECIES, mask_arr, 0);
       lv2 = LongVector.fromArray(SPECIES, l2, long512_arr_idx);
       lv3 = LongVector.fromArray(SPECIES, l3, long512_arr_idx);
       lv4 = LongVector.fromArray(SPECIES, l4, long512_arr_idx);
       lv5 = LongVector.fromArray(SPECIES, l5, long512_arr_idx);
       for(int i = 0; i < INVOC_COUNTER; i++) {
           for(int j = 0 ; j < ARRAYLEN; j+= SPECIES.length()) {
               LongVector.fromArray(SPECIES, l1, j)
                   .lanewise(VectorOperators.BITWISE_BLEND, lv2, lv3, lmask)
                   .lanewise(VectorOperators.BITWISE_BLEND, lv3, lv4, lmask)
                   .lanewise(VectorOperators.BITWISE_BLEND, lv4, lv5, lmask)
                   .intoArray(l1, j);
           }
       }
    }

    @Benchmark
    public void bitwiseBlendOperationLong512() {
       bitwiseBlendOperationLongKernel(LongVector.SPECIES_512);
    }

    @Benchmark
    public void bitwiseBlendOperationLong256() {
       bitwiseBlendOperationLongKernel(LongVector.SPECIES_256);
    }
}
