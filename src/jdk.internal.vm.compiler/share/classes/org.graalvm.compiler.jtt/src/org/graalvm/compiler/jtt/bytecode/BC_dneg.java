/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.bytecode;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class BC_dneg extends JTTTest {

    public static double test(double a, double b, int which) {
        double result1 = -a;
        double result2 = -b;
        double result = 0.0;
        if (which == 0) {
            result = result1;
        } else {
            result = result2;
        }
        return result;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0.0d, 1.0d, 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", -1.01d, -2.01d, 0);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 7263.8734d, 8263.8734d, 0);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 0.0d, 1.0d, 1);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", -1.01d, -2.01d, 1);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 7263.8734d, 8263.8734d, 1);
    }

    public static double test2(double a, double b) {
        return -(a - b);
    }

    @Test
    public void run6() throws Throwable {
        runTest("test2", -1.0d, -1.0d);
    }
}
