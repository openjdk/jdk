/*
 * Copyright (c) 2020, BELLSOFT. All rights reserved.
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

/*
 * @test
 * @bug 8248445 8276673
 * @summary Abs nodes detection and optimization in C2
 * @library /test/lib
 * @requires vm.debug == true
 *
 * @run main/othervm -XX:-TieredCompilation -Xbatch -XX:CompileOnly=java.lang.Math::abs compiler.c2.TestAbs
 * @run main/othervm -XX:-TieredCompilation compiler.c2.TestAbs
 */

package compiler.c2;
import jdk.test.lib.Asserts;

public class TestAbs {
    private static int SIZE = 500;

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

    public static void testAbsConstant() {
        // Test abs(constant) optimization for int
        Asserts.assertEquals(Integer.MAX_VALUE, Math.abs(Integer.MAX_VALUE));
        Asserts.assertEquals(Integer.MIN_VALUE, Math.abs(Integer.MIN_VALUE));
        Asserts.assertEquals(Integer.MAX_VALUE, Math.abs(-Integer.MAX_VALUE));

        // Test abs(constant) optimization for long
        Asserts.assertEquals(Long.MAX_VALUE, Math.abs(Long.MAX_VALUE));
        Asserts.assertEquals(Long.MIN_VALUE, Math.abs(Long.MIN_VALUE));
        Asserts.assertEquals(Long.MAX_VALUE, Math.abs(-Long.MAX_VALUE));

        // Test abs(constant) optimization for float
        Asserts.assertEquals(Float.NaN, Math.abs(Float.NaN));
        Asserts.assertEquals(Float.POSITIVE_INFINITY, Math.abs(Float.NEGATIVE_INFINITY));
        Asserts.assertEquals(Float.POSITIVE_INFINITY, Math.abs(Float.POSITIVE_INFINITY));
        Asserts.assertEquals(0.0f, Math.abs(0.0f));
        Asserts.assertEquals(0.0f, Math.abs(-0.0f));
        Asserts.assertEquals(Float.MAX_VALUE, Math.abs(Float.MAX_VALUE));
        Asserts.assertEquals(Float.MIN_VALUE, Math.abs(Float.MIN_VALUE));
        Asserts.assertEquals(Float.MAX_VALUE, Math.abs(-Float.MAX_VALUE));
        Asserts.assertEquals(Float.MIN_VALUE, Math.abs(-Float.MIN_VALUE));

        // Test abs(constant) optimization for double
        Asserts.assertEquals(Double.NaN, Math.abs(Double.NaN));
        Asserts.assertEquals(Double.POSITIVE_INFINITY, Math.abs(Double.NEGATIVE_INFINITY));
        Asserts.assertEquals(Double.POSITIVE_INFINITY, Math.abs(Double.POSITIVE_INFINITY));
        Asserts.assertEquals(0.0, Math.abs(0.0));
        Asserts.assertEquals(0.0, Math.abs(-0.0));
        Asserts.assertEquals(Double.MAX_VALUE, Math.abs(Double.MAX_VALUE));
        Asserts.assertEquals(Double.MIN_VALUE, Math.abs(Double.MIN_VALUE));
        Asserts.assertEquals(Double.MAX_VALUE, Math.abs(-Double.MAX_VALUE));
        Asserts.assertEquals(Double.MIN_VALUE, Math.abs(-Double.MIN_VALUE));
    }

    private static void testAbsTransformInt(int[] a) {
        for (int i = 0; i < a.length; i++) {
            Asserts.assertEquals(Math.abs(Math.abs(a[i])), Math.abs(a[i]));
            Asserts.assertEquals(Math.abs(0 - a[i]), Math.abs(a[i]));
        }
    }

    private static void testAbsTransformLong(long[] a) {
        for (int i = 0; i < a.length; i++) {
            Asserts.assertEquals(Math.abs(Math.abs(a[i])), Math.abs(a[i]));
            Asserts.assertEquals(Math.abs(0 - a[i]), Math.abs(a[i]));
        }
    }

    private static void testAbsTransformFloat(float[] a) {
        for (int i = 0; i < a.length; i++) {
            Asserts.assertEquals(Math.abs(Math.abs(a[i])), Math.abs(a[i]));
            Asserts.assertEquals(Math.abs(0 - a[i]), Math.abs(a[i]));
        }
    }

    private static void testAbsTransformDouble(double[] a) {
        for (int i = 0; i < a.length; i++) {
            Asserts.assertEquals(Math.abs(Math.abs(a[i])), Math.abs(a[i]));
            Asserts.assertEquals(Math.abs(0 - a[i]), Math.abs(a[i]));
        }
    }

    private static void testAbsOptChar(char[] a) {
        for (int i = 0; i < a.length; i++) {
            Asserts.assertEquals(a[i], (char) Math.abs(a[i]));
        }
    }

    public static void test() {
        // java.lang.Math.abs() collapses into AbsI/AbsL nodes on platforms that support the correspondent nodes
        // Using unsupported nodes triggers a console warning on a release build and gives an error on a debug build
        Math.abs(1);
        Math.abs(-1);
        Math.abs(1L);
        Math.abs(-1L);
    }

    public static void main(String args[]) {
        for (int i = 0; i < 20_000; i++) {
            test();

            testAbsConstant();

            // Verify abs(abs(x)) = abs(x) for all types
            // Verify abs(0-x) = abs(x) for all types
            testAbsTransformInt(ispecial);
            testAbsTransformLong(lspecial);
            testAbsTransformFloat(fspecial);
            testAbsTransformDouble(dspecial);

            // Verify abs(non-negative_value) = non-negative_value
            testAbsOptChar(cspecial);
        }

    }
}

