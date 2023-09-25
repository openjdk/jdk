/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

package compiler.vectorization;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8297689
 * @summary Test miscompilation of reverseBytes call from subword types
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.vectorization.TestSubwordReverseBytes
 */

public class TestSubwordReverseBytes {
    private static final int SIZE = 32000;

    private static   int[] idx = new int[SIZE];
    private static short[] rbs = new short[SIZE];
    private static  char[] rbc = new char[SIZE];

    static {
        for (int i = 0; i < SIZE; i++) {
            idx[i] = i;
        }
        for (short s = 0; s < SIZE; s++) {
            rbs[s] = Short.reverseBytes(s);
        }
        for (char c = 0; c < SIZE; c++) {
            rbc[c] = Character.reverseBytes(c);
        }
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_VS})
    public static int[] testShortReverseBytes() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
           res[i] = Short.reverseBytes((short) idx[i]);
        }
        return res;
    }

    @Run(test = "testShortReverseBytes")
    public static void testShort() {
        int[] res = testShortReverseBytes();
        for (int i = 0; i < SIZE; i++) {
            if (res[i] != rbs[i]) {
                throw new RuntimeException("Wrong result: expected = " +
                        (int) rbs[i] + ", actual = " + res[i]);
            }
        }
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_VS})
    public static int[] testCharacterReverseBytes() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
           res[i] = Character.reverseBytes((char) idx[i]);
        }
        return res;
    }

    @Run(test = "testCharacterReverseBytes")
    public static void testChar() {
        int[] res = testCharacterReverseBytes();
        for (int i = 0; i < SIZE; i++) {
            if (res[i] != rbc[i]) {
                throw new RuntimeException("Wrong result: expected = " +
                        (int) rbc[i] + ", actual = " + res[i]);
            }
        }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }
}

