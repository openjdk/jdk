/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.optimize;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Tests optimization of float operations.
 */
public class VN_Double02 extends JTTTest {

    private static boolean cond = true;

    public static double test(double arg) {
        if (arg == 0) {
            return add(arg + 10);
        }
        if (arg == 1) {
            return sub(arg + 10);
        }
        if (arg == 2) {
            return mul(arg + 10);
        }
        if (arg == 3) {
            return div(arg + 10);
        }
        return 0;
    }

    public static double add(double x) {
        double c = 1.0d;
        double t = x + c;
        if (cond) {
            double u = x + c;
            return t + u;
        }
        return 1;
    }

    public static double sub(double x) {
        double c = 1.0d;
        double t = x - c;
        if (cond) {
            double u = x - c;
            return t - u;
        }
        return 1;
    }

    public static double mul(double x) {
        double c = 1.0d;
        double t = x * c;
        if (cond) {
            double u = x * c;
            return t * u;
        }
        return 1.0d;
    }

    public static double div(double x) {
        double c = 1.0d;
        double t = x / c;
        if (cond) {
            double u = x / c;
            return t / u;
        }
        return 1.0d;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0d);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1d);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2d);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3d);
    }

}
