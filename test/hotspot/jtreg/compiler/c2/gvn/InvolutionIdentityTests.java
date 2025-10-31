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

import compiler.lib.generators.Generator;
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
 * @bug 8350988 8364407
 * @summary Test that Identity simplifications of Involution nodes are being performed as expected.
 * @library /test/lib /
 * @compile ReverseBytesConstantsHelper.jasm
 * @run driver compiler.c2.gvn.InvolutionIdentityTests
 */
public class InvolutionIdentityTests {

    public static final RestrictableGenerator<Integer> GEN_CHAR = Generators.G.safeRestrict(Generators.G.ints(), Character.MIN_VALUE, Character.MAX_VALUE);
    public static final RestrictableGenerator<Integer> GEN_SHORT = Generators.G.safeRestrict(Generators.G.ints(), Short.MIN_VALUE, Short.MAX_VALUE);
    public static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    public static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    public static final Generator<Float> GEN_FLOAT = Generators.G.floats();
    public static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:CompileCommand=inline,compiler.c2.gvn.ReverseBytesConstantsHelper::*");
    }

    @Run(test = {
        "testI1", "testI2",
        "testL1", "testL2",
        "testS1", "testS2",
        "testUS1", "testUS2",
        "testF1",
        "testD1"
    })
    public void runMethod() {
        int ai = GEN_INT.next();

        int mini = Integer.MIN_VALUE;
        int maxi = Integer.MAX_VALUE;

        assertResultI(0);
        assertResultI(ai);
        assertResultI(mini);
        assertResultI(maxi);

        long al = GEN_LONG.next();

        long minl = Long.MIN_VALUE;
        long maxl = Long.MAX_VALUE;

        assertResultL(0);
        assertResultL(al);
        assertResultL(minl);
        assertResultL(maxl);

        short as = GEN_SHORT.next().shortValue();

        short mins = Short.MIN_VALUE;
        short maxs = Short.MAX_VALUE;

        assertResultS((short) 0);
        assertResultS(as);
        assertResultS(mins);
        assertResultS(maxs);

        char ac = (char) GEN_CHAR.next().intValue();

        char minc = Character.MIN_VALUE;
        char maxc = Character.MAX_VALUE;

        assertResultUS((char) 0);
        assertResultUS(ac);
        assertResultUS(minc);
        assertResultUS(maxc);

        // short and char variants but passing an int
        assertResultTruncating(0);
        assertResultTruncating(ai);
        assertResultTruncating(mini);
        assertResultTruncating(maxi);

        float af = GEN_FLOAT.next();
        float inf = Float.POSITIVE_INFINITY;
        float nanf = Float.NaN;

        assertResultF(0f);
        assertResultF(-0f);
        assertResultF(af);
        assertResultF(inf);
        assertResultF(nanf);

        double ad = GEN_DOUBLE.next();
        double ind = Double.POSITIVE_INFINITY;
        double nand = Double.NaN;

        assertResultD(0d);
        assertResultD(-0d);
        assertResultD(ad);
        assertResultD(ind);
        assertResultD(nand);

    }

    @DontCompile
    public void assertResultI(int a) {
        Asserts.assertEQ(Integer.reverseBytes(Integer.reverseBytes(a)), testI1(a));
        Asserts.assertEQ(Integer.reverse(Integer.reverse(a))          , testI2(a));
    }

    @DontCompile
    public void assertResultL(long a) {
        Asserts.assertEQ(Long.reverseBytes(Long.reverseBytes(a)), testL1(a));
        Asserts.assertEQ(Long.reverse(Long.reverse(a))          , testL2(a));
    }

    @DontCompile
    public void assertResultS(short a) {
        Asserts.assertEQ(Short.reverseBytes(Short.reverseBytes(a)), testS1(a));
    }

    @DontCompile
    public void assertResultUS(char a) {
        Asserts.assertEQ(Character.reverseBytes(Character.reverseBytes(a)), testUS1(a));
    }

    @DontCompile
    public void assertResultF(float a) {
        Asserts.assertEQ(Float.floatToRawIntBits(-(-a)), Float.floatToRawIntBits(testF1(a)));
    }

    @DontCompile
    public void assertResultD(double a) {
        Asserts.assertEQ(Double.doubleToRawLongBits(-(-a)), Double.doubleToRawLongBits(testD1(a)));
    }

    @DontCompile
    public void assertResultTruncating(int a) {
        Asserts.assertEQ((int) Short.reverseBytes(    ReverseBytesConstantsHelper.reverseBytesShort(a)), testS2( a));
        Asserts.assertEQ((int) Character.reverseBytes(ReverseBytesConstantsHelper.reverseBytesChar( a)), testUS2(a));
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_I})
    public int testI1(int x) {
        return Integer.reverseBytes(Integer.reverseBytes(x));
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_I})
    public int testI2(int x) {
        return Integer.reverse(Integer.reverse(x));
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_L})
    public long testL1(long x) {
        return Long.reverseBytes(Long.reverseBytes(x));
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_L})
    public long testL2(long x) {
        return Long.reverse(Long.reverse(x));
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_S})
    public short testS1(short x) {
        // explicit cast to restrict the input to TypeInt::SHORT
        return Short.reverseBytes(Short.reverseBytes((short) (int) x));
    }

    @Test
    @IR(failOn = {IRNode.REVERSE_BYTES_US})
    public char testUS1(char x) {
        // explicit cast to restrict the input to TypeInt::CHAR
        return Character.reverseBytes(Character.reverseBytes((char) (int) x));
    }

    @Test
    @IR(counts = {IRNode.REVERSE_BYTES_S, "2"})
    public int testS2(int x) {
        return Short.reverseBytes(ReverseBytesConstantsHelper.reverseBytesShort(x));
    }

    @Test
    @IR(counts = {IRNode.REVERSE_BYTES_US, "2"})
    public int testUS2(int x) {
        return Character.reverseBytes(ReverseBytesConstantsHelper.reverseBytesChar(x));
    }

    @Test
    @IR(failOn = {IRNode.NEG_F})
    public float testF1(float x) {
        return -(-x);
    }

    @Test
    @IR(failOn = {IRNode.NEG_D})
    public double testD1(double x) {
        return -(-x);
    }
}
