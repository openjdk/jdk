/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

/**
 * Check for incorrect elimination of 0.0 and -0.0 from computations. They can affect the sign of
 * the result of an add or substract.
 */
public class FloatOptimizationTest extends GraalCompilerTest {

    @Test
    public void test1() {
        test("test1Snippet", -0.0);
    }

    @SuppressWarnings("all")
    public static double test1Snippet(double x) {
        return x + 0.0;
    }

    @Test
    public void test2() {
        test("test2Snippet", -0.0f);
    }

    @SuppressWarnings("all")
    public static double test2Snippet(float x) {
        return x + 0.0f;
    }

    @Test
    public void test3() {
        test("test3Snippet", -0.0);
    }

    @SuppressWarnings("all")
    public static double test3Snippet(double x) {
        return x - -0.0;
    }

    @Test
    public void test4() {
        test("test4Snippet", -0.0f);
    }

    @SuppressWarnings("all")
    public static double test4Snippet(float x) {
        return x - -0.0f;
    }

    @Override
    protected void assertDeepEquals(String message, Object expected, Object actual, double delta) {
        if (expected instanceof Double && actual instanceof Double) {
            double e = (double) expected;
            double a = (double) actual;
            if (Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(e)) {
                Assert.fail((message == null ? "" : message) + "raw double bits not equal " + Double.doubleToRawLongBits(a) + " != " + Double.doubleToRawLongBits(e));
            }
        } else {
            super.assertDeepEquals(message, expected, actual, delta);
        }
    }
}
