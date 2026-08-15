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

/**
 * @test
 * @summary Test that scalarize_vbox_node treats correctly scalarized value class locals
 * @bug 8385886 8386720
 * @enablePreview
 * @modules jdk.incubator.vector
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+IgnoreUnrecognizedVMOptions
 *                   -XX:+EnableVectorSupport
 *                   -XX:+EnableVectorReboxing
 *                   -XX:+EnableVectorAggressiveReboxing
 *                   -XX:+DeoptimizeALot -XX:DeoptimizeALotInterval=1 -XX:DeoptimizeOnlyAt=61
 *                   -Xbatch
 *                   -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:CompileCommand=dontinline,${test.main.class}::dontcompile
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import jdk.incubator.vector.*;

public class TestScalarizeVboxWithScalarizedValuesAndHalf {
    static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_128;
    static final int[] array = new int[64];

    static value class MyValue {
        int a;

        MyValue(int a) {
            this.a = a;
        }
    }

    static int dontcompile(double d, MyValue myVal, IntVector vector, long l) {
        return myVal.a;
    }

    static int test(boolean flag, int v, double d, long l) {
        MyValue myVal = new MyValue(v);

        IntVector v1 = IntVector.fromArray(SPECIES, array, 0);
        IntVector v2 = IntVector.fromArray(SPECIES, array, SPECIES.length());
        IntVector vector = flag ? v1 : v2;

        // bci 61 is the invokestatic
        // and everything C2 needs to do with it (like allocating arrays for vectors with new_array_blob).
        // myVal is not null, but after deopt, in dontcompile myVal.a throws a NPE.
        return dontcompile(d, myVal, vector, l);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 200_000; i++) {
            test((i & 1) == 0, i, (double)i, (long)(2 * i));
        }
    }
}
