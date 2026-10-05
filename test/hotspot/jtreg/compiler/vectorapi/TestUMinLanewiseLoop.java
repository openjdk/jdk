/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

package compiler.vectorapi;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/*
 * @test
 * @bug 8393465
 * @summary C2 should not crash when SuperWord inspects a Vector API UMIN loop
 * @requires vm.compiler2.enabled
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class}
 */

public class TestUMinLanewiseLoop {
    static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    static int intUmin(int[] a) {
        IntVector acc = IntVector.fromArray(SPECIES, a, 0);
        for (int i = SPECIES.length(); i <= a.length - SPECIES.length(); i += SPECIES.length()) {
            acc = acc.lanewise(VectorOperators.UMIN, IntVector.fromArray(SPECIES, a, i));
        }
        return acc.lane(0);
    }

    public static void main(String[] args) {
        int[] a = new int[1024];
        int sink = 0;
        for (int round = 0; round < 100001; round++) {
            sink += intUmin(a);
        }
        System.out.println("completed, sink=" + sink);
    }
}
