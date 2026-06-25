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
 * @bug 8387073
 * @summary Arrays.copyOf must pad overized elements with 0 with partial inlining as well.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

import java.util.Arrays;

public class TestSubwordPartialInlining {
    static boolean[] boolArr = new boolean[] {true, true};
    static byte[] byteArr = new byte[] {(byte)42};

    static boolean[] testBool() {
        return Arrays.copyOf(boolArr, 4);
    }

    static byte[] testByte() {
        // Should be zero-padded, but we seem to get non-zero values.
        return Arrays.copyOf(byteArr, 4);
    }

    public static void main(String[] args) {
        boolean[] boolGold = testBool();
        byte[] byteGold = testByte();
        for (int i = 0; i < 10_000; i++) {
            testBool();
            testByte();
        }
        boolean[] boolComp = testBool();
        byte[] byteComp = testByte();

        if (!Arrays.equals(boolGold, boolComp)) {
            throw new RuntimeException("wrong result: interpreter result = " + Arrays.toString(boolGold) +
                                       " compiler result = " + Arrays.toString(boolComp));
        }
        if (!Arrays.equals(byteGold, byteComp)) {
            throw new RuntimeException("wrong result: interpreter result = " + Arrays.toString(byteGold) +
                                       " compiler result = " + Arrays.toString(byteComp));
        }
    }
}
