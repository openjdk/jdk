/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8351844
 * @summary Verify assertion checks in long min/max vector with same inputs
 * @modules jdk.incubator.vector
 * @library /test/lib /
 *
 * @run driver compiler.vectorapi.TestVectorMinMaxSameInputs
 */

package compiler.vectorapi;

import jdk.incubator.vector.*;
import compiler.lib.ir_framework.*;

public class TestVectorMinMaxSameInputs {
    public static int idx = 0;
    public static final VectorSpecies<Long> SPECIES = LongVector.SPECIES_PREFERRED;

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-ea", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:UseAVX=2");
    }

    @Test
    @IR(counts={IRNode.MIN_VL, "1"})
    public static void minTest() {
        LongVector lv1 = LongVector.broadcast(SPECIES, -17179869184L);
        LongVector lv2 = LongVector.broadcast(SPECIES, -17179869184L);
        LongVector lv3 = LongVector.broadcast(SPECIES, 16385L);
        assert lv1.lanewise(VectorOperators.MIN, lv2).add(lv3)
                  .lane(idx++ & (SPECIES.length() - 1)) == (-17179869184L + 16385L);
    }

    @Test
    @IR(counts={IRNode.MAX_VL, "1"})
    public static void maxTest() {
        LongVector lv1 = LongVector.broadcast(SPECIES, -17179869184L);
        LongVector lv2 = LongVector.broadcast(SPECIES, -17179869184L);
        LongVector lv3 = LongVector.broadcast(SPECIES, 16385L);
        assert lv1.lanewise(VectorOperators.MAX, lv2).add(lv3)
                  .lane(idx++ & (SPECIES.length() - 1)) == (-17179869184L + 16385L);
    }
}
