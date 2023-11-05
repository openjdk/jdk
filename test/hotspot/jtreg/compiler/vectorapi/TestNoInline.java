/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.vector.VectorSpecies;

/*
 * @test
 * @bug 8244675
 * @modules jdk.incubator.vector
 *
 * @run main/othervm -Xbatch -XX:-Inline            compiler.vectorapi.TestNoInline
 * @run main/othervm -Xbatch -XX:-IncrementalInline compiler.vectorapi.TestNoInline
 */
public class TestNoInline {
    static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_PREFERRED;

    static IntVector test(int[] arr) {
        return IntVector.fromArray(I_SPECIES, arr, 0);
    }
    public static void main(String[] args) {
        int[] arr = new int[64];
        for (int i = 0; i < 20_000; i++) {
            test(arr);
        }
    }
}
