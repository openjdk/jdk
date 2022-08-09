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

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8276673 8280089
 * @summary Test abs nodes optimization in C2.
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRAbs
 */

public class TestIRAbs {

    public static char [] cspecial = {
        0, 42, 128, 256, 1024, 4096, 65535
    };

    public static int [] ispecial = {
        0, Integer.MAX_VALUE, Integer.MIN_VALUE, -42, 42, -1, 1
    };

    public static long [] lspecial = {
        0, Long.MAX_VALUE, Long.MIN_VALUE, -42, 42, -1, 1
    };

    public static float [] fspecial = {
        0.0f,
        -0.0f,
        Float.MAX_VALUE,
        Float.MIN_VALUE,
        -Float.MAX_VALUE,
        -Float.MIN_VALUE,
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        Integer.MAX_VALUE,
        Integer.MIN_VALUE,
        Long.MAX_VALUE,
        Long.MIN_VALUE,
        -1.0f,
        1.0f,
        -42.0f,
        42.0f
    };

    public static double [] dspecial = {
        0.0,
        -0.0,
        Double.MAX_VALUE,
        Double.MIN_VALUE,
        -Double.MAX_VALUE,
        -Double.MIN_VALUE,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        Integer.MAX_VALUE,
        Integer.MIN_VALUE,
        Long.MIN_VALUE,
        Long.MAX_VALUE,
        -1,
        1,
        42,
        -42,
        Math.PI,
        Math.E,
        Float.MAX_VALUE,
        Float.MIN_VALUE,
        -Float.MAX_VALUE,
        -Float.MIN_VALUE,
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY
    };

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.ABS_I, IRNode.ABS_L, IRNode.ABS_F, IRNode.ABS_D})
    public void testAbsConstant() {
        // Test abs(constant) optimization for int
        Asserts.assertEquals(Integer.MAX_VALUE, Math.abs(Integer.MAX_VALUE));
        Asserts.assertEquals(Integer.MIN_VALUE, Math.abs(Integer.MIN_VALUE));
        Asserts.assertEquals(Integer.MAX_VALUE, Math.abs(-Integer.MAX_VALUE));

        // Test abs(constant) optimization for long
        Asserts.assertEquals(Long.MAX_VALUE, Math.abs(Long.MAX_VALUE));
        Asserts.assertEquals(Long.MIN_VALUE, Math.abs(Long.MIN_VALUE));
        Asserts.assertEquals(Long.MAX_VALUE, Math.abs(-Long.MAX_VALUE));

        // Test abs(constant) optimization for float
        Asserts.assertTrue(Float.isNaN(Math.abs(Float.NaN)));
        Asserts.assertEquals(Float.POSITIVE_INFINITY, Math.abs(Float.NEGATIVE_INFINITY));
        Asserts.assertEquals(Float.POSITIVE_INFINITY, Math.abs(Float.POSITIVE_INFINITY));
        Asserts.assertEquals(0.0f, Math.abs(0.0f));
        Asserts.assertEquals(0.0f, Math.abs(-0.0f));
        Asserts.assertEquals(Float.MAX_VALUE, Math.abs(Float.MAX_VALUE));
        Asserts.assertEquals(Float.MIN_VALUE, Math.abs(Float.MIN_VALUE));
        Asserts.assertEquals(Float.MAX_VALUE, Math.abs(-Float.MAX_VALUE));
        Asserts.assertEquals(Float.MIN_VALUE, Math.abs(-Float.MIN_VALUE));

        // Test abs(constant) optimization for double
        Asserts.assertTrue(Double.isNaN(Math.abs(Double.NaN)));
        Asserts.assertEquals(Double.POSITIVE_INFINITY, Math.abs(Double.NEGATIVE_INFINITY));
        Asserts.assertEquals(Double.POSITIVE_INFINITY, Math.abs(Double.POSITIVE_INFINITY));
        Asserts.assertEquals(0.0, Math.abs(0.0));
        Asserts.assertEquals(0.0, Math.abs(-0.0));
        Asserts.assertEquals(Double.MAX_VALUE, Math.abs(Double.MAX_VALUE));
        Asserts.assertEquals(Double.MIN_VALUE, Math.abs(Double.MIN_VALUE));
        Asserts.assertEquals(Double.MAX_VALUE, Math.abs(-Double.MAX_VALUE));
        Asserts.assertEquals(Double.MIN_VALUE, Math.abs(-Double.MIN_VALUE));
    }

    @Test
    @IR(counts = {IRNode.ABS_I, "1"})
    public int testInt0(int x) {
        return Math.abs(Math.abs(x)); // transformed to Math.abs(x)
    }

    @Test
    @IR(failOn = {IRNode.SUB_I})
    @IR(counts = {IRNode.ABS_I, "1"})
    public int testInt1(int x) {
        return Math.abs(0 - x); // transformed to Math.abs(x)
    }

    @Run(test = {"testInt0", "testInt1"})
    public void checkTestInt(RunInfo info) {
        for (int i = 0; i < ispecial.length; i++) {
            Asserts.assertEquals(Math.abs(ispecial[i]), testInt0(ispecial[i]));
            Asserts.assertEquals(Math.abs(ispecial[i]), testInt1(ispecial[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ABS_L, "1"})
    public long testLong0(long x) {
        return Math.abs(Math.abs(x)); // transformed to Math.abs(x)
    }

    @Test
    @IR(failOn = {IRNode.SUB_L})
    @IR(counts = {IRNode.ABS_L, "1"})
    public long testLong1(long x) {
        return Math.abs(0 - x); // transformed to Math.abs(x)
    }

    @Run(test = {"testLong0", "testLong1"})
    public void checkTestLong(RunInfo info) {
        for (int i = 0; i < lspecial.length; i++) {
            Asserts.assertEquals(Math.abs(lspecial[i]), testLong0(lspecial[i]));
            Asserts.assertEquals(Math.abs(lspecial[i]), testLong1(lspecial[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ABS_F, "1"})
    public float testFloat0(float x) {
        return Math.abs(Math.abs(x)); // transformed to Math.abs(x)
    }

    @Test
    @IR(failOn = {IRNode.SUB_F})
    @IR(counts = {IRNode.ABS_F, "1"})
    public float testFloat1(float x) {
        return Math.abs(0 - x); // transformed to Math.abs(x)
    }

    @Run(test = {"testFloat0", "testFloat1"})
    public void checkTestFloat(RunInfo info) {
        for (int i = 0; i < fspecial.length; i++) {
            Asserts.assertEquals(Math.abs(fspecial[i]), testFloat0(fspecial[i]));
            Asserts.assertEquals(Math.abs(fspecial[i]), testFloat1(fspecial[i]));
        }
    }

    @Test
    @IR(counts = {IRNode.ABS_D, "1"})
    public double testDouble0(double x) {
        return Math.abs(Math.abs(x)); // transformed to Math.abs(x)
    }

    @Test
    @IR(failOn = {IRNode.SUB_D})
    @IR(counts = {IRNode.ABS_D, "1"})
    public double testDouble1(double x) {
        return Math.abs(0 - x); // transformed to Math.abs(x)
    }

    @Run(test = {"testDouble0", "testDouble1"})
    public void checkTestDouble(RunInfo info) {
        for (int i = 0; i < dspecial.length; i++) {
            Asserts.assertEquals(Math.abs(dspecial[i]), testDouble0(dspecial[i]));
            Asserts.assertEquals(Math.abs(dspecial[i]), testDouble1(dspecial[i]));
        }
    }

    @Test
    @IR(failOn = {IRNode.ABS_I})
    public void testChar() {
        for (int i = 0; i < cspecial.length; i++) {
            Asserts.assertEquals(cspecial[i], (char) Math.abs(cspecial[i]));
        }
    }
 }
