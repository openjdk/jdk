/*
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

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @summary Test that patterns involving duplicated conversion nodes behind phi are properly optimized.
 * @bug 8316918
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestPhiDuplicatedConversion
 */
public class TestPhiDuplicatedConversion {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static float int2Float(boolean c, int a, int b) {
        return c ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static double int2Double(boolean c, int a, int b) {
        return c ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static long int2Long(boolean c, int a, int b) {
        return c ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static int float2Int(boolean c, float a, float b) {
        return c ? (int)a : (int)b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static double float2Double(boolean c, float a, float b) {
        return c ? (double)a : (double)b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static long float2Long(boolean c, float a, float b) {
        return c ? (long)a : (long)b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static int double2Int(boolean c, double a, double b) {
        return c ? (int)a : (int)b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static float double2Float(boolean c, double a, double b) {
        return c ? (float)a : (float)b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static long double2Long(boolean c, double a, double b) {
        return c ? (long)a : (long)b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static float long2Float(boolean c, long a, long b) {
        return c ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static double long2Double(boolean c, long a, long b) {
        return c ? a : b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"})
    public static int long2Int(boolean c, long a, long b) {
        return c ? (int)a : (int)b;
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public static short float2HalfFloat(boolean c, float a, float b) {
        return c ? Float.floatToFloat16(a) : Float.floatToFloat16(b);
    }

    @Test
    @IR(counts = {IRNode.CONV, "1"}, applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    public static float halfFloat2Float(boolean c, short a, short b) {
        return c ? Float.float16ToFloat(a) : Float.float16ToFloat(b);
    }

    @Run(test = {"int2Float", "int2Double", "int2Long",
                 "float2Int", "float2Double", "float2Long",
                 "double2Int", "double2Float", "double2Long",
                 "long2Float", "long2Double", "long2Int",
                 "float2HalfFloat", "halfFloat2Float"})
    public void runTests() {
        assertResults(true, 10, 20, 3.14f, -1.6f, 3.1415, -1.618, 30L, 400L, Float.floatToFloat16(10.5f), Float.floatToFloat16(20.5f));
        assertResults(false, 10, 20, 3.14f, -1.6f, 3.1415, -1.618, 30L, 400L, Float.floatToFloat16(10.5f), Float.floatToFloat16(20.5f));
    }

    @DontCompile
    public void assertResults(boolean c, int intA, int intB, float floatA, float floatB, double doubleA, double doubleB, long longA, long longB, short halfFloatA, short halfFloatB) {
        Asserts.assertEQ(c ? (float)intA : (float)intB, int2Float(c, intA, intB));
        Asserts.assertEQ(c ? (double)intA : (double)intB, int2Double(c, intA, intB));
        Asserts.assertEQ(c ? (long)intA : (long)intB, int2Long(c, intA, intB));
        Asserts.assertEQ(c ? (int)floatA : (int)floatB, float2Int(c, floatA, floatB));
        Asserts.assertEQ(c ? (double)floatA : (double)floatB, float2Double(c, floatA, floatB));
        Asserts.assertEQ(c ? (long)floatA : (long)floatB, float2Long(c, floatA, floatB));
        Asserts.assertEQ(c ? (int)doubleA : (int)doubleB, double2Int(c, doubleA, doubleB));
        Asserts.assertEQ(c ? (float)doubleA : (float)doubleB, double2Float(c, doubleA, doubleB));
        Asserts.assertEQ(c ? (long)doubleA : (long)doubleB, double2Long(c, doubleA, doubleB));
        Asserts.assertEQ(c ? (float)longA : (float)longB, long2Float(c, longA, longB));
        Asserts.assertEQ(c ? (double)longA : (double)longB, long2Double(c, longA, longB));
        Asserts.assertEQ(c ? (int)longA : (int)longB, long2Int(c, longA, longB));
        Asserts.assertEQ(c ? Float.floatToFloat16(floatA) : Float.floatToFloat16(floatB), float2HalfFloat(c, floatA, floatB));
        Asserts.assertEQ(c ? Float.float16ToFloat(halfFloatA) : Float.float16ToFloat(halfFloatB), halfFloat2Float(c, halfFloatA, halfFloatB));
    }
}