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

/*
 * @test
 * @bug 8386163
 * @summary Checks there is no assertion failure with macro logic optimization when both inputs of not patterns are all one vectors
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class TestMaskedNotAllOnes {

    private static final VectorSpecies<Integer> ISP = IntVector.SPECIES_PREFERRED;
    private static final int VALUE = 1234567;

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @Test
    @Warmup(10000)
    static int[] testMaskedDivNegOne() {
        IntVector v = IntVector.broadcast(ISP, VALUE);
        VectorMask<Integer> mask = VectorMask.fromLong(ISP, -1L);
        int[] out = new int[ISP.length()];
        v.div(-1, mask).intoArray(out, 0);
        return out;
    }

    static final int[] GOLD_DIV = testMaskedDivNegOne();

    @Check(test = "testMaskedDivNegOne")
    static void checkMaskedDivNegOne(int[] out) {
        Verify.checkEQ(GOLD_DIV, out);
    }

    @Test
    @Warmup(10000)
    static int[] testMaskedNotAllOnesVector() {
        IntVector allOnes = IntVector.broadcast(ISP, -1);
        VectorMask<Integer> mask = VectorMask.fromLong(ISP, -1L);
        int[] out = new int[ISP.length()];
        allOnes.lanewise(VectorOperators.NOT, mask).intoArray(out, 0);
        return out;
    }

    static final int[] GOLD_NOT = testMaskedNotAllOnesVector();

    @Check(test = "testMaskedNotAllOnesVector")
    static void checkMaskedNotAllOnesVector(int[] out) {
        Verify.checkEQ(GOLD_NOT, out);
    }
}
