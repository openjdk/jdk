/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8285973
 * @summary Test that code generation for fp comparison works as intended
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestFPComparison
 */
public class TestFPComparison {
    static final double[] DOUBLES = new double[] {
            Double.NEGATIVE_INFINITY,
            -Double.MAX_VALUE,
            -1.0,
            -Double.MIN_VALUE,
            -0.0,
            0.0,
            Double.MIN_VALUE,
            1.0,
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY,
            Double.NaN,
    };

    static final float[] FLOATS = new float[] {
            Float.NEGATIVE_INFINITY,
            -Float.MAX_VALUE,
            -1.0F,
            -Float.MIN_VALUE,
            -0.0F,
            0.0F,
            Float.MIN_VALUE,
            1.0F,
            Float.MAX_VALUE,
            Float.POSITIVE_INFINITY,
            Float.NaN,
    };

    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    static int call() {
        return 1;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveEqualTwoDoubles(double x, double y) {
        return x == y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveEqualTwoFloats(float x, float y) {
        return x == y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveNotEqualTwoDoubles(double x, double y) {
        return x != y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveNotEqualTwoFloats(float x, float y) {
        return x != y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveLessThanTwoDoubles(double x, double y) {
        return x < y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveLessThanTwoFloats(float x, float y) {
        return x < y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveMoreThanTwoDoubles(double x, double y) {
        return x > y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveMoreThanTwoFloats(float x, float y) {
        return x > y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveLessEqualTwoDoubles(double x, double y) {
        return x <= y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveLessEqualTwoFloats(float x, float y) {
        return x <= y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveMoreEqualTwoDoubles(double x, double y) {
        return x >= y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveMoreEqualTwoFloats(float x, float y) {
        return x >= y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveEqualOneDouble(double x) {
        return x == x ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveEqualOneFloat(float x) {
        return x == x ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveNotEqualOneDouble(double x) {
        return x != x ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVE_I, "1"})
    public int cMoveNotEqualOneFloat(float x) {
        return x != x ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchEqualTwoDoubles(double x, double y) {
        return x == y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchEqualTwoFloats(float x, float y) {
        return x == y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchNotEqualTwoDoubles(double x, double y) {
        return x != y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchNotEqualTwoFloats(float x, float y) {
        return x != y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchLessThanTwoDoubles(double x, double y) {
        return x < y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchLessThanTwoFloats(float x, float y) {
        return x < y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchMoreThanTwoDoubles(double x, double y) {
        return x > y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchMoreThanTwoFloats(float x, float y) {
        return x > y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchLessEqualTwoDoubles(double x, double y) {
        return x <= y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchLessEqualTwoFloats(float x, float y) {
        return x <= y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchMoreEqualTwoDoubles(double x, double y) {
        return x >= y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchMoreEqualTwoFloats(float x, float y) {
        return x >= y ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchEqualOneDouble(double x) {
        return x == x ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchEqualOneFloat(float x) {
        return x == x ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchNotEqualOneDouble(double x) {
        return x != x ? call() : 0;
    }

    @Test
    @IR(counts = {IRNode.IF, "1"})
    public int branchNotEqualOneFloat(float x) {
        return x != x ? call() : 0;
    }

    @Run(test = {"cMoveEqualTwoDoubles", "cMoveEqualTwoFloats", "cMoveNotEqualTwoDoubles", "cMoveNotEqualTwoFloats",
            "cMoveLessThanTwoDoubles", "cMoveLessThanTwoFloats", "cMoveMoreThanTwoDoubles", "cMoveMoreThanTwoFloats",
            "cMoveLessEqualTwoDoubles", "cMoveLessEqualTwoFloats", "cMoveMoreEqualTwoDoubles", "cMoveMoreEqualTwoFloats",
            "cMoveEqualOneDouble", "cMoveEqualOneFloat", "cMoveNotEqualOneDouble", "cMoveNotEqualOneFloat",
            "branchEqualTwoDoubles", "branchEqualTwoFloats", "branchNotEqualTwoDoubles", "branchNotEqualTwoFloats",
            "branchLessThanTwoDoubles", "branchLessThanTwoFloats", "branchMoreThanTwoDoubles", "branchMoreThanTwoFloats",
            "branchLessEqualTwoDoubles", "branchLessEqualTwoFloats", "branchMoreEqualTwoDoubles", "branchMoreEqualTwoFloats",
            "branchEqualOneDouble", "branchEqualOneFloat", "branchNotEqualOneDouble", "branchNotEqualOneFloat"})
    public void runTests() {
        for (int i = 0; i < DOUBLES.length; i++) {
            for (int j = 0; j < DOUBLES.length; j++) {
                int len = DOUBLES.length;
                double x = DOUBLES[i];
                double y = DOUBLES[j];
                Asserts.assertEquals(cMoveEqualTwoDoubles(x, x),
                        (x == x) ? 1 : 0);
                Asserts.assertEquals(cMoveNotEqualTwoDoubles(x, x),
                        (x != x) ? 1 : 0);
                Asserts.assertEquals(cMoveEqualTwoDoubles(x, y),
                        (x == y) ? 1 : 0);
                Asserts.assertEquals(cMoveNotEqualTwoDoubles(x, y),
                        (x != y) ? 1 : 0);
                Asserts.assertEquals(cMoveLessThanTwoDoubles(x, y),
                        (x < y) ? 1 : 0);
                Asserts.assertEquals(cMoveLessEqualTwoDoubles(x, y),
                        (x <= y) ? 1 : 0);
                Asserts.assertEquals(cMoveMoreThanTwoDoubles(x, y),
                        (x > y) ? 1 : 0);
                Asserts.assertEquals(cMoveMoreEqualTwoDoubles(x, y),
                        (x >= y) ? 1 : 0);
                Asserts.assertEquals(branchEqualTwoDoubles(x, y),
                        (x == y) ? 1 : 0);
                Asserts.assertEquals(branchNotEqualTwoDoubles(x, y),
                        (x != y) ? 1 : 0);
                Asserts.assertEquals(branchLessThanTwoDoubles(x, y),
                        (x < y) ? 1 : 0);
                Asserts.assertEquals(branchLessEqualTwoDoubles(x, y),
                        (x <= y) ? 1 : 0);
                Asserts.assertEquals(branchMoreThanTwoDoubles(x, y),
                        (x > y) ? 1 : 0);
                Asserts.assertEquals(branchMoreEqualTwoDoubles(x, y),
                        (x >= y) ? 1 : 0);
            }
        }
        for (int i = 0; i < FLOATS.length; i++) {
            for (int j = 0; j < FLOATS.length; j++) {
                int len = FLOATS.length;
                float x = FLOATS[i];
                float y = FLOATS[j];
                Asserts.assertEquals(cMoveEqualTwoFloats(x, x),
                        (x == x) ? 1 : 0);
                Asserts.assertEquals(cMoveNotEqualTwoFloats(x, x),
                        (x != x) ? 1 : 0);
                Asserts.assertEquals(cMoveEqualTwoFloats(x, y),
                        (x == y) ? 1 : 0);
                Asserts.assertEquals(cMoveNotEqualTwoFloats(x, y),
                        (x != y) ? 1 : 0);
                Asserts.assertEquals(cMoveLessThanTwoFloats(x, y),
                        (x < y) ? 1 : 0);
                Asserts.assertEquals(cMoveLessEqualTwoFloats(x, y),
                        (x <= y) ? 1 : 0);
                Asserts.assertEquals(cMoveMoreThanTwoFloats(x, y),
                        (x > y) ? 1 : 0);
                Asserts.assertEquals(cMoveMoreEqualTwoFloats(x, y),
                        (x >= y) ? 1 : 0);
                Asserts.assertEquals(branchEqualTwoFloats(x, y),
                        (x == y) ? 1 : 0);
                Asserts.assertEquals(branchNotEqualTwoFloats(x, y),
                        (x != y) ? 1 : 0);
                Asserts.assertEquals(branchLessThanTwoFloats(x, y),
                        (x < y) ? 1 : 0);
                Asserts.assertEquals(branchLessEqualTwoFloats(x, y),
                        (x <= y) ? 1 : 0);
                Asserts.assertEquals(branchMoreThanTwoFloats(x, y),
                        (x > y) ? 1 : 0);
                Asserts.assertEquals(branchMoreEqualTwoFloats(x, y),
                        (x >= y) ? 1 : 0);
            }
        }
        for (int i = 0; i < DOUBLES.length; i++) {
            Asserts.assertEquals(cMoveEqualOneDouble(DOUBLES[DOUBLES.length - 1]), 0);
            Asserts.assertEquals(cMoveNotEqualOneDouble(DOUBLES[DOUBLES.length - 1]), 1);
            Asserts.assertEquals(cMoveEqualOneDouble(DOUBLES[i]), (i != DOUBLES.length - 1) ? 1 : 0);
            Asserts.assertEquals(cMoveNotEqualOneDouble(DOUBLES[i]), (i == DOUBLES.length - 1) ? 1 : 0);
            Asserts.assertEquals(branchEqualOneDouble(DOUBLES[i]), (i != DOUBLES.length - 1) ? 1 : 0);
            Asserts.assertEquals(branchNotEqualOneDouble(DOUBLES[i]), (i == DOUBLES.length - 1) ? 1 : 0);
        }
        for (int i = 0; i < FLOATS.length; i++) {
            Asserts.assertEquals(cMoveEqualOneFloat(FLOATS[FLOATS.length - 1]), 0);
            Asserts.assertEquals(cMoveNotEqualOneFloat(FLOATS[FLOATS.length - 1]), 1);
            Asserts.assertEquals(cMoveEqualOneFloat(FLOATS[i]), (i != FLOATS.length - 1) ? 1 : 0);
            Asserts.assertEquals(cMoveNotEqualOneFloat(FLOATS[i]), (i == FLOATS.length - 1) ? 1 : 0);
            Asserts.assertEquals(branchEqualOneFloat(FLOATS[i]), (i != FLOATS.length - 1) ? 1 : 0);
            Asserts.assertEquals(branchNotEqualOneFloat(FLOATS[i]), (i == FLOATS.length - 1) ? 1 : 0);
        }
    }
}
