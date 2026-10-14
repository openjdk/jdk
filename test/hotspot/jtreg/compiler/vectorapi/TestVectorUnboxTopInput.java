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
 * @bug 8388490
 * @key randomness
 * @summary VectorUnboxNode::Ideal must handle a TOP value during IGVN
 * @requires vm.debug == true & vm.compiler2.enabled
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+StressReflectiveCode
 *                   -XX:-UseLoopPredicate -XX:+StressIGVN -XX:StressSeed=93 -XX:MaxVectorSize=16
 *                   ${test.main.class}
 */

package compiler.vectorapi;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
import jdk.test.lib.Asserts;

public class TestVectorUnboxTopInput {
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_128;
    private static final VectorSpecies<Long> L_SPECIES = LongVector.SPECIES_128;

    private static int LENGTH = I_SPECIES.length();
    private static boolean[] ma;
    private static boolean[] mb;
    private static boolean[] mr;

    static {
        ma = new boolean[LENGTH];
        mb = new boolean[LENGTH];
        mr = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            long lb = i;
            ma[i] = (lb & 1) == 0;
            mb[i] = (lb & 2) == 0;
        }
    }

    public static void testSingleMaskAllI() {
        VectorMask<Integer> avm = VectorMask.fromArray(I_SPECIES, ma, 0);
        VectorMask<Integer> bvm = VectorMask.fromArray(I_SPECIES, mb, 0);
        avm.not().or(bvm.not()).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < I_SPECIES.length(); i++) {
            Foo.assertEquals(!ma[i] | !mb[i], mr[i]);
        }
    }

    public static void testSingleMaskAllL() {
        VectorMask<Long> avm = VectorMask.fromArray(L_SPECIES, ma, 0);
        VectorMask<Long> bvm = VectorMask.fromArray(L_SPECIES, mb, 0);
        avm.not().or(bvm.not()).intoArray(mr, 0);

        // Verify results
        for (int i = 0; i < L_SPECIES.length(); i++) {
            Foo.assertEquals(!ma[i] | !mb[i], mr[i]);
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            testSingleMaskAllL();
        }
        for (int i = 0; i < 10_000; i++) {
            testSingleMaskAllI();
        }
    }
}

class Foo {
    static void assertEquals(Object lhs, Object rhs) {
        Asserts.assertEquals(lhs, rhs, "Unexpected mask result");
    }
}
