/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8283307
 * @key randomness
 * @summary Auto-vectorization enhancement for unsigned shift right on signed subword types
 * @requires ((os.arch=="amd64" | os.arch=="x86_64") & (vm.opt.UseSSE == "null" | vm.opt.UseSSE > 3)) | os.arch=="aarch64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorizeURShiftSubword
 */

public class TestVectorizeURShiftSubword {

    private static final Random RANDOM = Utils.getRandomInstance();

    final private static int NUM = 3000;

    private short[] shorta = new short[NUM];
    private short[] shortb = new short[NUM];
    private byte[] bytea = new byte[NUM];
    private byte[] byteb = new byte[NUM];

    private static final int[] SPECIALS = {
        0, 0x1, 0x8, 0xF, 0x3F, 0x7C, 0x7F, 0x80, 0x81, 0x8F, 0xF3, 0xF8, 0xFF,
        0x38FF, 0x3FFF, 0x8F8F, 0x8FFF, 0x7FF3, 0x7FFF, 0xFF33, 0xFFF8, 0xFFFF, 0xFFFFFF,
        Integer.MAX_VALUE, Integer.MIN_VALUE
    };

    public byte urshift(byte input, int amount) {
        return (byte) (input >>> amount);
    }

    public short urshift(short input, int amount) {
        return (short) (input >>> amount);
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestVectorizeURShiftSubword.class);
        framework.setDefaultWarmup(1).addFlags("-XX:CompileCommand=exclude,*.urshift").start();
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, ">0", IRNode.RSHIFT_VB, ">0", IRNode.STORE_VECTOR, ">0"})
    public void testByte0() {
        for(int i = 0; i < NUM; i++) {
            byteb[i] = (byte) (bytea[i] >>> 3);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, ">0", IRNode.RSHIFT_VB, ">0", IRNode.STORE_VECTOR, ">0"})
    public void testByte1() {
        for(int i = 0; i < NUM; i++) {
            byteb[i] = (byte) (bytea[i] >>> 24);
        }
    }

    @Test
    @IR(failOn = {IRNode.LOAD_VECTOR_B, IRNode.RSHIFT_VB, IRNode.STORE_VECTOR})
    public void testByte2() {
        for(int i = 0; i < NUM; i++) {
            byteb[i] = (byte) (bytea[i] >>> 25);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, ">0", IRNode.RSHIFT_VS, ">0", IRNode.STORE_VECTOR, ">0"})
    public void testShort0() {
        for(int i = 0; i < NUM; i++) {
            shortb[i] = (short) (shorta[i] >>> 10);
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_S, ">0", IRNode.RSHIFT_VS, ">0", IRNode.STORE_VECTOR, ">0"})
    public void testShort1() {
        for(int i = 0; i < NUM; i++) {
            shortb[i] = (short) (shorta[i] >>> 16);
        }
    }

    @Test
    @IR(failOn = {IRNode.LOAD_VECTOR_S, IRNode.RSHIFT_VS, IRNode.STORE_VECTOR})
    public void testShort2() {
        for(int i = 0; i < NUM; i++) {
            shortb[i] = (short) (shorta[i] >>> 17);
        }
    }

    @Test
    public void checkTest() {
        testByte0();
        for (int i = 0; i < bytea.length; i++) {
            Asserts.assertEquals(byteb[i], urshift(bytea[i], 3));
        }
        testByte1();
        for (int i = 0; i < bytea.length; i++) {
            Asserts.assertEquals(byteb[i], urshift(bytea[i], 24));
        }
        testByte2();
        for (int i = 0; i < bytea.length; i++) {
            Asserts.assertEquals(byteb[i], urshift(bytea[i], 25));
        }
        testShort0();
        for (int i = 0; i < shorta.length; i++) {
            Asserts.assertEquals(shortb[i], urshift(shorta[i], 10));
        }
        testShort1();
        for (int i = 0; i < shorta.length; i++) {
            Asserts.assertEquals(shortb[i], urshift(shorta[i], 16));
        }
        testShort2();
        for (int i = 0; i < shorta.length; i++) {
            Asserts.assertEquals(shortb[i], urshift(shorta[i], 17));
        }

    }

    @Run(test = "checkTest")
    public void checkTest_runner() {
        for (int i = 0; i < SPECIALS.length; i++) {
            for (int j = 0; j < shorta.length; j++) {
                shorta[j] = (short) SPECIALS[i];
                bytea[j] = (byte) SPECIALS[i];
            }
            checkTest();
        }
        for (int j = 0; j < shorta.length; j++) {
            shorta[j] = (short) RANDOM.nextInt();;
            bytea[j] = (byte) RANDOM.nextInt();
        }
        checkTest();
    }
}
