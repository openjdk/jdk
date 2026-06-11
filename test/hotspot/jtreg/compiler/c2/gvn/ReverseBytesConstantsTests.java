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
package compiler.c2.gvn;

import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8353551 8359678
 * @summary Test that ReverseBytes operations constant-fold.
 * @library /test/lib /
 * @compile ReverseBytesConstantsHelper.jasm
 * @run driver compiler.c2.gvn.ReverseBytesConstantsTests
 */
public class ReverseBytesConstantsTests {

    private static final RestrictableGenerator<Integer> GEN_CHAR = Generators.G.safeRestrict(Generators.G.ints(), Character.MIN_VALUE, Character.MAX_VALUE);
    private static final char C_CHAR = (char) GEN_CHAR.next().intValue();
    private static final RestrictableGenerator<Integer> GEN_SHORT = Generators.G.safeRestrict(Generators.G.ints(), Short.MIN_VALUE, Short.MAX_VALUE);
    private static final short C_SHORT = GEN_SHORT.next().shortValue();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    private static final long C_LONG = GEN_LONG.next();
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final int C_INT = GEN_INT.next();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:CompileCommand=inline,compiler.c2.gvn.ReverseBytesConstantsHelper::*");
    }

    @Run(test = {
        "testI1", "testI2", "testI3", "testI4",
        "testL1", "testL2", "testL3", "testL4",
        "testS1", "testS2", "testS3", "testS4",
        "testUS1", "testUS2", "testUS3", "testUS4",
    })
    public void runMethod() {
        assertResultI();
        assertResultL();
        assertResultS();
        assertResultUS();
    }

    @DontCompile
    public void assertResultI() {
        Asserts.assertEQ(Integer.reverseBytes(0x04030201), testI1());
        Asserts.assertEQ(Integer.reverseBytes(0x50607080), testI2());
        Asserts.assertEQ(Integer.reverseBytes(0x80706050), testI3());
        Asserts.assertEQ(Integer.reverseBytes(C_INT), testI4());
    }

    @DontCompile
    public void assertResultL() {
        Asserts.assertEQ(Long.reverseBytes(0x0807060504030201L), testL1());
        Asserts.assertEQ(Long.reverseBytes(0x1020304050607080L), testL2());
        Asserts.assertEQ(Long.reverseBytes(0x8070605040302010L), testL3());
        Asserts.assertEQ(Long.reverseBytes(C_LONG), testL4());
    }

    @DontCompile
    public void assertResultS() {
        Asserts.assertEQ(Short.reverseBytes((short) 0x0201), testS1());
        Asserts.assertEQ(Short.reverseBytes((short) 0x7080), testS2());
        Asserts.assertEQ(Short.reverseBytes((short) 0x8070), testS3());
        Asserts.assertEQ(Short.reverseBytes(C_SHORT), testS4());
    }

    @DontCompile
    public void assertResultUS() {
        Asserts.assertEQ(Character.reverseBytes((char) 0x0201), testUS1());
        Asserts.assertEQ(Character.reverseBytes((char) 0x7080), testUS2());
        Asserts.assertEQ(Character.reverseBytes((char) 0x8070), testUS3());
        Asserts.assertEQ(Character.reverseBytes(C_CHAR), testUS4());
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_I})
    public int testI1() {
        return Integer.reverseBytes(0x04030201);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_I})
    public int testI2() {
        return Integer.reverseBytes(0x50607080);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_I})
    public int testI3() {
        return Integer.reverseBytes(0x80706050);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_I})
    public int testI4() {
        return Integer.reverseBytes(C_INT);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_L})
    public long testL1() {
        return Long.reverseBytes(0x0807060504030201L);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_L})
    public long testL2() {
        return Long.reverseBytes(0x1020304050607080L);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_L})
    public long testL3() {
        return Long.reverseBytes(0x8070605040302010L);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_L})
    public long testL4() {
        return Long.reverseBytes(C_LONG);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_S})
    public short testS1() {
        return Short.reverseBytes((short) 0x0201);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_S})
    public short testS2() {
        return Short.reverseBytes((short) 0x7080);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_S})
    public short testS3() {
        return Short.reverseBytes((short) 0x8070);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_S})
    public short testS4() {
        return Short.reverseBytes(C_SHORT);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_S, IRNode.CALL})
    public short testS5() {
        return ReverseBytesConstantsHelper.reverseBytesShort(C_INT);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_S, IRNode.CALL})
    public short testS6() {
        return ReverseBytesConstantsHelper.reverseBytesShort(C_CHAR);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_US})
    public char testUS1() {
        return Character.reverseBytes((char) 0x0201);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_US})
    public char testUS2() {
        return Character.reverseBytes((char) 0x7080);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_US})
    public char testUS3() {
        return Character.reverseBytes((char) 0x8070);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_US})
    public char testUS4() {
        return Character.reverseBytes(C_CHAR);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_US, IRNode.CALL})
    public char testUS5() {
        return ReverseBytesConstantsHelper.reverseBytesChar(C_INT);
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_US, IRNode.CALL})
    public char testUS6() {
        return ReverseBytesConstantsHelper.reverseBytesChar(C_SHORT);
    }

}
