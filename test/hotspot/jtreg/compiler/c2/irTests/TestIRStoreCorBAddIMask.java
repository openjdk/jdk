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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8282470
 * @summary C2: Optimize patterns like s = (short) ((x << Imm) >> Imm + y) when Imm < 16 and apply the optimization to byte and char.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRStoreCorBAddIMask
 */
public class TestIRStoreCorBAddIMask {

    private static final Random RANDOM = Utils.getRandomInstance();

    private static final int[] SPECIAL_IN = {
        0, 0x1, 0x8, 0xF, 0x3F, 0x7C, 0x7F, 0x80, 0x81, 0x8F, 0xF3, 0xF8, 0xFF,
        0x38FF, 0x3FFF, 0x8F8F, 0x8FFF, 0x7FF3, 0x7FFF, 0xFF33, 0xFFF8, 0xFFFF, 0xFFFFFF,
        Integer.MAX_VALUE, Integer.MIN_VALUE
    };

    private byte BYTE_OUT = 0;
    private char CHAR_OUT = 0;
    private short SHORT_OUT = 0;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.LSHIFT_I, IRNode.RSHIFT_I})
    @IR(counts = {IRNode.ADD_I, "1", IRNode.STORE_B, "1"})
    public void testByte0(int x, int y) {
        BYTE_OUT = (byte) (((x << 24) >> 24) + y); // transformed to (byte) (x + y)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.LSHIFT_I, IRNode.RSHIFT_I})
    @IR(counts = {IRNode.MUL_I, "1", IRNode.STORE_B, "1"})
    public void testByte1(int x, int y) {
        BYTE_OUT = (byte) (((x << 8) >> 8) * y); // transformed to (byte) (x * y)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.LSHIFT_I, IRNode.RSHIFT_I})
    @IR(counts = {IRNode.SUB_I, "1", IRNode.STORE_B, "1"})
    public void testByte2(int x, int y) {
        BYTE_OUT = (byte) (((x << 16) >> 16) - y); // transformed to (byte) (x - y)
    }

    @Test
    @Arguments({Argument.DEFAULT})
    @IR(failOn = {IRNode.LSHIFT_I, IRNode.RSHIFT_I})
    @IR(counts = {IRNode.SUB_I, "1", IRNode.STORE_B, "1"})
    public void testByte3(int x) {
        BYTE_OUT = (byte) (-((x << 16) >> 16)); // transformed to (byte) (-x)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.LSHIFT_I, "1", IRNode.RSHIFT_I, "1", IRNode.OR_I, "1", IRNode.STORE_B, "1"})
    public void testByte4(int x, int y) {
        BYTE_OUT = (byte) (((x << 25) >> 25) | y); // no transformation
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.XOR_I, "1", IRNode.STORE_B, "1"})
    public void testByte5(int x, int y) {
        BYTE_OUT = (byte) ((x & 0xFF) ^ y); // transformed to (byte) (x ^ y)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.AND_I, "2", IRNode.STORE_B, "1"})
    public void testByte6(int x, int y) {
        BYTE_OUT = (byte) ((x & 0xF) & y); // no transformation
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.ADD_I, "1", IRNode.STORE_C, "1"})
    public void testChar0(int x, int y) {
        CHAR_OUT = (char) ((x & 0xFFFF) + y); // transformed to (char) (x + y)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.MUL_I, "1", IRNode.STORE_C, "1"})
    public void testChar1(int x, int y) {
        CHAR_OUT = (char) ((x & 0xFFFF) * y); // transformed to (char) (x * y)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.AND_I})
    @IR(counts = {IRNode.SUB_I, "1", IRNode.STORE_C, "1"})
    public void testChar2(int x, int y) {
        CHAR_OUT = (char) ((x & 0xFFFF) - y); // transformed to (char) (x - y)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.AND_I, "2", IRNode.STORE_C, "1"})
    public void testChar3(int x, int y) {
        CHAR_OUT = (char) ((x & 0xFF) & y); // no transformation
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.LSHIFT_I, IRNode.RSHIFT_I})
    @IR(counts = {IRNode.OR_I, "1", IRNode.STORE_C, "1"})
    public void testShort0(int x, int y) {
        SHORT_OUT = (short) (((x << 16) >> 16) | y); // transformed to (short) (x | y)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.LSHIFT_I, IRNode.RSHIFT_I})
    @IR(counts = {IRNode.AND_I, "1", IRNode.STORE_C, "1"})
    public void testShort1(int x, int y) {
        SHORT_OUT = (short) (((x << 10) >> 10) & y); // transformed to (short) (x & y)
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(counts = {IRNode.LSHIFT_I, "1", IRNode.RSHIFT_I, "1", IRNode.XOR_I, "1", IRNode.STORE_C, "1"})
    public void testShort2(int x, int y) {
        SHORT_OUT = (short) (((x << 18) >> 18) ^ y); // no transformation
    }

    @Test
    @Arguments({Argument.DEFAULT, Argument.DEFAULT})
    @IR(failOn = {IRNode.RSHIFT_I})
    @IR(counts = {IRNode.LSHIFT_I, "1", IRNode.STORE_C, "1"})
    public void testShort3(int x, int y) {
        SHORT_OUT = (short) (((x << 16) >> 16) << y); // transformed to (short) (x << y)
    }

    @Test
    public void checkTest(int x, int y) {
        testByte0(x, y);
        Asserts.assertEquals((byte)(((x << 24) >> 24) + y), BYTE_OUT);
        testByte1(x, y);
        Asserts.assertEquals((byte)(((x << 8) >> 8) * y), BYTE_OUT);
        testByte2(x, y);
        Asserts.assertEquals((byte)(((x << 16) >> 16) - y), BYTE_OUT);
        testByte3(x);
        Asserts.assertEquals((byte)(-((x << 16) >> 16)), BYTE_OUT);
        testByte4(x, y);
        Asserts.assertEquals((byte)(((x << 25) >> 25) | y), BYTE_OUT);
        testByte5(x, y);
        Asserts.assertEquals((byte)((x & 0xFF) ^ y), BYTE_OUT);
        testByte6(x, y);
        Asserts.assertEquals((byte)((x & 0xF) & y), BYTE_OUT);
        testChar0(x, y);
        Asserts.assertEquals((char)((x & 0xFFFF) + y), CHAR_OUT);
        testChar1(x, y);
        Asserts.assertEquals((char)((x & 0xFFFF) * y), CHAR_OUT);
        testChar2(x, y);
        Asserts.assertEquals((char)((x & 0xFFFF) - y), CHAR_OUT);
        testChar3(x, y);
        Asserts.assertEquals((char)((x & 0xFF) & y), CHAR_OUT);
        testShort0(x, y);
        Asserts.assertEquals((short)(((x << 16) >> 16) | y), SHORT_OUT);
        testShort1(x, y);
        Asserts.assertEquals((short)(((x << 10) >> 10) & y), SHORT_OUT);
        testShort2(x, y);
        Asserts.assertEquals((short)(((x << 18) >> 18) ^ y), SHORT_OUT);
        testShort3(x, y);
        Asserts.assertEquals((short)(((x << 16) >> 16) << y), SHORT_OUT);
    }

    @Run(test = "checkTest")
    public void checkTest_runner() {
        int x = RANDOM.nextInt();
        int y = RANDOM.nextInt();
        checkTest(x, y);
        for (int i = 0; i < SPECIAL_IN.length; ++i) {
            for (int j = 0; j < SPECIAL_IN.length; ++j) {
                checkTest(SPECIAL_IN[i], SPECIAL_IN[j]);
            }
        }
    }

}
