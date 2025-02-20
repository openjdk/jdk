//
// Copyright (c) 2023, Arm Limited. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.Random;
import jdk.incubator.vector.*;
import org.openjdk.jmh.annotations.*;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgs = {"--add-modules=jdk.incubator.vector"})
public class StoreMaskTrueCount {
    private static final VectorSpecies<Short> S_SPECIES = ShortVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_PREFERRED;
    private static final int LENGTH = 128;
    private static final Random RD = new Random();
    private static boolean[] ba;

    static {
        ba = new boolean[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            ba[i] = RD.nextBoolean();
        }
    }

    @Benchmark
    public int testShort() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += S_SPECIES.length()) {
            VectorMask<Short> m = VectorMask.fromArray(S_SPECIES, ba, i);
            res += m.not().trueCount();
        }

        return res;
    }

    @Benchmark
    public int testInt() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            VectorMask<Integer> m = VectorMask.fromArray(I_SPECIES, ba, i);
            res += m.not().trueCount();
        }

        return res;
    }

    @Benchmark
    public int testLong() {
        int res = 0;
        for (int i = 0; i < LENGTH; i += L_SPECIES.length()) {
            VectorMask<Long> m = VectorMask.fromArray(L_SPECIES, ba, i);
            res += m.not().trueCount();
        }

        return res;
    }
}
