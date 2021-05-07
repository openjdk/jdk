//
// Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
// DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
//
// This code is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 2 only, as
// published by the Free Software Foundation.
//
// This code is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// version 2 for more details (a copy is included in the LICENSE file that
// accompanied this code).
//
// You should have received a copy of the GNU General Public License version
// 2 along with this work; if not, write to the Free Software Foundation,
// Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
//
// Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
// or visit www.oracle.com if you need additional information or have any
// questions.
//
//
package org.openjdk.bench.jdk.incubator.vector;

import java.util.Random;
import jdk.incubator.vector.*;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class MaskQueryOperationsBenchmark {
    @Param({"128","256","512"})
    int bits;

    @Param({"1","2","3"})
    int inputs;

    VectorSpecies bspecies;
    VectorSpecies sspecies;
    VectorSpecies ispecies;
    VectorSpecies lspecies;
    VectorMask    bmask;
    VectorMask    smask;
    VectorMask    imask;
    VectorMask    lmask;
    boolean []    mask_arr;


    static final boolean [] mask_avg_case = {
       false, false, false, true, false, false, false, false,
       false, false, false, true, false, false, false, false,
       false, false, false, true, false, false, false, false,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       false, false, false, true, false, false, false, false,
       false, false, false, true, false, false, false, false,
       false, false, false, true, false, false, false, false
    };

    static final boolean [] mask_best_case  = {
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true,
       true, true, true, true, true, true, true, true
    };

    static final boolean [] mask_worst_case  = {
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false,
       false, false, false, false, false, false, false, false
    };

    @Setup(Level.Trial)
    public void BmSetup() {
        bspecies = VectorSpecies.of(int.class, VectorShape.forBitSize(bits));
        sspecies = VectorSpecies.of(int.class, VectorShape.forBitSize(bits));
        ispecies = VectorSpecies.of(int.class, VectorShape.forBitSize(bits));
        lspecies = VectorSpecies.of(long.class, VectorShape.forBitSize(bits));

        if( 1 == inputs) {
          mask_arr = mask_best_case;
        } else if ( 2 == inputs ) {
          mask_arr = mask_worst_case;
        } else {
          mask_arr = mask_avg_case;
        }

        bmask   = VectorMask.fromArray(bspecies, mask_arr, 0);
        smask   = VectorMask.fromArray(sspecies, mask_arr, 0);
        imask   = VectorMask.fromArray(ispecies, mask_arr, 0);
        lmask   = VectorMask.fromArray(lspecies, mask_arr, 0);
    }

    @Benchmark
    public void testTrueCountByte(Blackhole bh) {
        bh.consume(bmask.trueCount());
    }

    @Benchmark
    public void testTrueCountShort(Blackhole bh) {
        bh.consume(smask.trueCount());
    }
    @Benchmark
    public void testTrueCountInt(Blackhole bh) {
        bh.consume(imask.trueCount());
    }
    @Benchmark
    public void testTrueCountLong(Blackhole bh) {
        bh.consume(lmask.trueCount());
    }

    @Benchmark
    public void testFirstTrueByte(Blackhole bh) {
        bh.consume(bmask.firstTrue());
    }

    @Benchmark
    public void testFirstTrueShort(Blackhole bh) {
        bh.consume(smask.firstTrue());
    }
    @Benchmark
    public void testFirstTrueInt(Blackhole bh) {
        bh.consume(imask.firstTrue());
    }
    @Benchmark
    public void testFirstTrueLong(Blackhole bh) {
        bh.consume(lmask.firstTrue());
    }

    @Benchmark
    public void testLastTrueByte(Blackhole bh) {
        bh.consume(bmask.lastTrue());
    }

    @Benchmark
    public void testLastTrueShort(Blackhole bh) {
        bh.consume(smask.lastTrue());
    }
    @Benchmark
    public void testLastTrueInt(Blackhole bh) {
        bh.consume(imask.lastTrue());
    }
    @Benchmark
    public void testLastTrueLong(Blackhole bh) {
        bh.consume(lmask.lastTrue());
    }
}
