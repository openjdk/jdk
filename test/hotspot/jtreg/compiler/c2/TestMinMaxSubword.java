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

package compiler.c2;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8294816
 * @summary Test Math.min/max vectorization miscompilation for integer subwords
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.TestMinMaxSubword
 */

public class TestMinMaxSubword {
    private static final int LENGTH = 256;
    private static final Random RANDOM = Utils.getRandomInstance();
    private static int val = 65536;
    private static short[] sa;
    private static short[] sb;
    private static byte[] ba;
    private static byte[] bb;

    static {
        sa = new short[LENGTH];
        sb = new short[LENGTH];
        ba = new byte[LENGTH];
        bb = new byte[LENGTH];
        for(int i = 0; i < LENGTH; i++) {
            sa[i] = (short) (RANDOM.nextInt(999) + 1);
            ba[i] = (byte) (RANDOM.nextInt(99) + 1);
        }
    }

    // Ensure vector max/min instructions are not generated for integer subword types
    // as Java APIs for Math.min/max do not support integer subword types and superword
    // should not generate vectorized Min/Max nodes for them.
    @Test
    @IR(failOn = {IRNode.MIN_VI, IRNode.MIN_VF, IRNode.MIN_VD})
    public static void testMinShort() {
        for (int i = 0; i < LENGTH; i++) {
           sb[i] = (short) Math.min(sa[i], val);
        }
    }

    @Run(test = "testMinShort")
    public static void testMinShort_runner() {
        testMinShort();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(sb[i], sa[i]);
        }
    }

    @Test
    @IR(failOn = {IRNode.MAX_VI, IRNode.MAX_VF, IRNode.MAX_VD})
    public static void testMaxShort() {
        for (int i = 0; i < LENGTH; i++) {
            sb[i] = (short) Math.max(sa[i], val);
        }
    }
    @Run(test = "testMaxShort")
    public static void testMaxShort_runner() {
        testMaxShort();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(sb[i], (short) 0);
        }
    }

    @Test
    @IR(failOn = {IRNode.MIN_VI, IRNode.MIN_VF, IRNode.MIN_VD})
    public static void testMinByte() {
        for (int i = 0; i < LENGTH; i++) {
           bb[i] = (byte) Math.min(ba[i], val);
        }
    }

    @Run(test = "testMinByte")
    public static void testMinByte_runner() {
        testMinByte();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(bb[i], ba[i]);
        }
    }

    @Test
    @IR(failOn = {IRNode.MAX_VI, IRNode.MAX_VF, IRNode.MAX_VD})
    public static void testMaxByte() {
        for (int i = 0; i < LENGTH; i++) {
            bb[i] = (byte) Math.max(ba[i], val);
        }
    }
    @Run(test = "testMaxByte")
    public static void testMaxByte_runner() {
        testMaxByte();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(bb[i], (byte) 0);
        }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }
}
