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
 * @bug 8390625
 * @summary Test VectorRearrange with the same source and shuffle operand
 * @requires vm.compiler2.enabled
 * @modules jdk.incubator.vector
 * @library /test/lib
 * @run main/othervm -Xbatch ${test.main.class}
 */

package compiler.vectorapi;

import jdk.incubator.vector.ShortVector;
import jdk.test.lib.Asserts;

public class TestSelectFromSameOperand {
    private static final short[] INPUT = {7, 6, 5, 4, 3, 2, 1, 0};
    private static final short[] OUTPUT = new short[INPUT.length];

    public static void test() {
        // The already masked 'vector' becomes both source and shuffle of the VectorRearrange node
        ShortVector vector = ShortVector.fromArray(ShortVector.SPECIES_128, INPUT, 0).and((short) 7);
        vector.selectFrom(vector).intoArray(OUTPUT, 0);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100_000; i++) {
            test();
        }
        for (int i = 0; i < OUTPUT.length; i++) {
            Asserts.assertEQ(OUTPUT[i], (short) i);
        }
    }
}

