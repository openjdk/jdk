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
            0,
            1,
            Double.MIN_VALUE,
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NaN,
    };

    static final float[] FLOATS = new float[] {
            0,
            1,
            Float.MIN_VALUE,
            Float.MAX_VALUE,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
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
    @IR(counts = {IRNode.CMOVEI, "1"})
    public int cMoveEqualTwoDoubles(double x, double y) {
        return x == y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVEI, "1"})
    public int cMoveEqualTwoFloats(float x, float y) {
        return x == y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVEI, "1"})
    public int cMoveNotEqualTwoDoubles(double x, double y) {
        return x != y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVEI, "1"})
    public int cMoveNotEqualTwoFloats(float x, float y) {
        return x != y ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVEI, "1"})
    public int cMoveEqualOneDouble(double x) {
        return x == x ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVEI, "1"})
    public int cMoveEqualOneFloat(float x) {
        return x == x ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVEI, "1"})
    public int cMoveNotEqualOneDouble(double x) {
        return x != x ? 1 : 0;
    }

    @Test
    @IR(counts = {IRNode.CMOVEI, "1"})
    public int cMoveNotEqualOneFloat(float x) {
        return x != x ? 1 : 0;
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
            "cMoveEqualOneDouble", "cMoveEqualOneFloat", "cMoveNotEqualOneDouble", "cMoveNotEqualOneFloat",
            "branchEqualOneDouble", "branchEqualOneFloat", "branchNotEqualOneDouble", "branchNotEqualOneFloat"})
    public void runTests() {
        for (int i = 0; i < DOUBLES.length; i++) {
            for (int j = 0; j < DOUBLES.length; j++) {
                Asserts.assertEquals(cMoveEqualTwoDoubles(DOUBLES[i], DOUBLES[i]),
                        (i != DOUBLES.length - 1) ? 1 : 0);
                Asserts.assertEquals(cMoveNotEqualTwoDoubles(DOUBLES[i], DOUBLES[i]),
                        (i == DOUBLES.length - 1) ? 1 : 0);
                Asserts.assertEquals(cMoveEqualTwoDoubles(DOUBLES[i], DOUBLES[j]),
                        (i == j && i != DOUBLES.length - 1) ? 1 : 0);
                Asserts.assertEquals(cMoveNotEqualTwoDoubles(DOUBLES[i], DOUBLES[j]),
                        (i != j || i == DOUBLES.length - 1) ? 1 : 0);
            }
        }
        for (int i = 0; i < FLOATS.length; i++) {
            for (int j = 0; j < FLOATS.length; j++) {
                Asserts.assertEquals(cMoveEqualTwoFloats(FLOATS[i], FLOATS[i]),
                        (i != FLOATS.length - 1) ? 1 : 0);
                Asserts.assertEquals(cMoveNotEqualTwoFloats(FLOATS[i], FLOATS[i]),
                        (i == FLOATS.length - 1) ? 1 : 0);
                Asserts.assertEquals(cMoveEqualTwoFloats(FLOATS[i], FLOATS[j]),
                        (i == j && i != FLOATS.length - 1) ? 1 : 0);
                Asserts.assertEquals(cMoveNotEqualTwoFloats(FLOATS[i], FLOATS[j]),
                        (i != j || i == FLOATS.length - 1) ? 1 : 0);
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
