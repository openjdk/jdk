/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import java.util.HashMap;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AbsNode;
import org.graalvm.compiler.nodes.calc.ReinterpretNode;
import org.graalvm.compiler.replacements.nodes.BitCountNode;
import org.graalvm.compiler.replacements.nodes.BitScanForwardNode;
import org.graalvm.compiler.replacements.nodes.BitScanReverseNode;
import org.graalvm.compiler.replacements.nodes.ReverseBytesNode;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the VM independent {@link MethodSubstitution}s.
 */
public class StandardMethodSubstitutionsTest extends MethodSubstitutionTest {

    @Test
    public void testMathSubstitutions() {
        assertInGraph(assertNotInGraph(testGraph("mathAbs"), IfNode.class), AbsNode.class);     // Java
        double value = 34567.891D;
        testGraph("mathCos");
        testGraph("mathLog");
        testGraph("mathLog10");
        testGraph("mathSin");
        testGraph("mathSqrt");
        testGraph("mathTan");
        testGraph("mathAll");

        test("mathCos", value);
        test("mathLog", value);
        test("mathLog10", value);
        test("mathSin", value);
        test("mathSqrt", value);
        test("mathTan", value);
        test("mathAll", value);
    }

    @Test
    public void testMathPow() {
        double a = 34567.891D;
        double b = 4.6D;
        test("mathPow", a, b);

        // Test the values directly handled by the substitution

        // If the second argument is positive or negative zero, then the result is 1.0.
        test("mathPow", a, 0.0D);
        test("mathPow", a, -0.0D);
        // If the second argument is 1.0, then the result is the same as the first argument.
        test("mathPow", a, 1.0D);
        // If the second argument is NaN, then the result is NaN.
        test("mathPow", a, Double.NaN);
        // If the first argument is NaN and the second argument is nonzero, then the result is NaN.
        test("mathPow", Double.NaN, b);
        test("mathPow", Double.NaN, 0.0D);
        // x**-1 = 1/x
        test("mathPow", a, -1.0D);
        // x**2 = x*x
        test("mathPow", a, 2.0D);
        // x**0.5 = sqrt(x)
        test("mathPow", a, 0.5D);
    }

    public static double mathPow(double a, double b) {
        return mathPow0(a, b);
    }

    public static double mathPow0(double a, double b) {
        return Math.pow(a, b);
    }

    public static double mathAbs(double value) {
        return Math.abs(value);
    }

    public static double mathSqrt(double value) {
        return Math.sqrt(value);
    }

    public static double mathLog(double value) {
        return Math.log(value);
    }

    public static double mathLog10(double value) {
        return Math.log10(value);
    }

    public static double mathSin(double value) {
        return Math.sin(value);
    }

    public static double mathCos(double value) {
        return Math.cos(value);
    }

    public static double mathTan(double value) {
        return Math.tan(value);
    }

    public static double mathAll(double value) {
        return Math.sqrt(value) + Math.log(value) + Math.log10(value) + Math.sin(value) + Math.cos(value) + Math.tan(value);
    }

    public void testSubstitution(String testMethodName, Class<?> intrinsicClass, Class<?> holder, String methodName, boolean optional, Object... args) {
        ResolvedJavaMethod realJavaMethod = getResolvedJavaMethod(holder, methodName);
        ResolvedJavaMethod testJavaMethod = getResolvedJavaMethod(testMethodName);
        StructuredGraph graph = testGraph(testMethodName);

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getSubstitution(realJavaMethod, -1, false, null);
        if (replacement == null && !optional) {
            assertInGraph(graph, intrinsicClass);
        }

        for (Object l : args) {
            // Force compilation
            InstalledCode code = getCode(testJavaMethod);
            assert optional || code != null;
            // Verify that the original method and the substitution produce the same value
            Object expected = invokeSafe(realJavaMethod, null, l);
            assertDeepEquals(expected, invokeSafe(testJavaMethod, null, l));
            // Verify that the generated code and the original produce the same value
            assertDeepEquals(expected, executeVarargsSafe(code, l));
        }
    }

    @Test
    public void testCharSubstitutions() {
        Object[] args = new Character[]{Character.MIN_VALUE, (char) -1, (char) 0, (char) 1, Character.MAX_VALUE};

        testSubstitution("charReverseBytes", ReverseBytesNode.class, Character.class, "reverseBytes", false, args);
    }

    public static char charReverseBytes(char value) {
        return Character.reverseBytes(value);
    }

    @Test
    public void testCharSubstitutionsNarrowing() {
        Object[] args = new Integer[]{(int) Character.MIN_VALUE, -1, 0, 1, (int) Character.MAX_VALUE};

        for (Object arg : args) {
            test("charReverseBytesNarrowing", arg);
        }
    }

    public static char charReverseBytesNarrowing(int value) {
        return Character.reverseBytes((char) value);
    }

    @Test
    public void testShortSubstitutions() {
        Object[] args = new Short[]{Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE};

        testSubstitution("shortReverseBytes", ReverseBytesNode.class, Short.class, "reverseBytes", false, args);
    }

    public static short shortReverseBytes(short value) {
        return Short.reverseBytes(value);
    }

    @Test
    public void testShortSubstitutionsNarrowing() {
        Object[] args = new Integer[]{(int) Short.MIN_VALUE, -1, 0, 1, (int) Short.MAX_VALUE};

        for (Object arg : args) {
            test("shortReverseBytesNarrowing", arg);
        }
    }

    public static short shortReverseBytesNarrowing(int value) {
        return Short.reverseBytes((short) value);
    }

    @Test
    public void testIntegerSubstitutions() {
        Object[] args = new Object[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};

        testSubstitution("integerReverseBytes", ReverseBytesNode.class, Integer.class, "reverseBytes", false, args);
        testSubstitution("integerNumberOfLeadingZeros", BitScanReverseNode.class, Integer.class, "numberOfLeadingZeros", true, args);
        testSubstitution("integerNumberOfTrailingZeros", BitScanForwardNode.class, Integer.class, "numberOfTrailingZeros", false, args);
        testSubstitution("integerBitCount", BitCountNode.class, Integer.class, "bitCount", true, args);
    }

    public static int integerReverseBytes(int value) {
        return Integer.reverseBytes(value);
    }

    public static int integerNumberOfLeadingZeros(int value) {
        return Integer.numberOfLeadingZeros(value);
    }

    public static int integerNumberOfTrailingZeros(int value) {
        return Integer.numberOfTrailingZeros(value);
    }

    public static int integerBitCount(int value) {
        return Integer.bitCount(value);
    }

    @Test
    public void testLongSubstitutions() {
        Object[] args = new Object[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};

        testSubstitution("longReverseBytes", ReverseBytesNode.class, Long.class, "reverseBytes", false, args);
        testSubstitution("longNumberOfLeadingZeros", BitScanReverseNode.class, Long.class, "numberOfLeadingZeros", true, args);
        testSubstitution("longNumberOfTrailingZeros", BitScanForwardNode.class, Long.class, "numberOfTrailingZeros", false, args);
        testSubstitution("longBitCount", BitCountNode.class, Long.class, "bitCount", true, args);
    }

    public static long longReverseBytes(long value) {
        return Long.reverseBytes(value);
    }

    public static int longNumberOfLeadingZeros(long value) {
        return Long.numberOfLeadingZeros(value);
    }

    public static int longNumberOfTrailingZeros(long value) {
        return Long.numberOfTrailingZeros(value);
    }

    public static int longBitCount(long value) {
        return Long.bitCount(value);
    }

    @Test
    public void testFloatSubstitutions() {
        assertInGraph(testGraph("floatToIntBits"), ReinterpretNode.class); // Java
        testGraph("intBitsToFloat");
    }

    public static int floatToIntBits(float value) {
        return Float.floatToIntBits(value);
    }

    public static float intBitsToFloat(int value) {
        return Float.intBitsToFloat(value);
    }

    @Test
    public void testDoubleSubstitutions() {
        assertInGraph(testGraph("doubleToLongBits"), ReinterpretNode.class); // Java
        testGraph("longBitsToDouble");
    }

    public static long doubleToLongBits(double value) {
        return Double.doubleToLongBits(value);
    }

    public static double longBitsToDouble(long value) {
        return Double.longBitsToDouble(value);
    }

    public static boolean isInstance(Class<?> clazz, Object object) {
        return clazz.isInstance(object);
    }

    public static boolean isInstance2(boolean cond, Object object) {
        Class<?> clazz;
        if (cond) {
            clazz = String.class;
        } else {
            clazz = java.util.HashMap.class;
        }
        return clazz.isInstance(object);
    }

    public static boolean isAssignableFrom(Class<?> clazz, Class<?> other) {
        return clazz.isAssignableFrom(other);
    }

    @Test
    public void testClassSubstitutions() {
        testGraph("isInstance");
        testGraph("isInstance2");
        testGraph("isAssignableFrom");
        for (Class<?> c : new Class<?>[]{getClass(), Cloneable.class, int[].class, String[][].class}) {
            for (Object o : new Object[]{this, new int[5], new String[2][], new Object()}) {
                test("isInstance", c, o);
                test("isAssignableFrom", c, o.getClass());
            }
        }

        test("isInstance2", true, null);
        test("isInstance2", false, null);
        test("isInstance2", true, "string");
        test("isInstance2", false, "string");
        test("isInstance2", true, new HashMap<>());
        test("isInstance2", false, new HashMap<>());
    }
}
