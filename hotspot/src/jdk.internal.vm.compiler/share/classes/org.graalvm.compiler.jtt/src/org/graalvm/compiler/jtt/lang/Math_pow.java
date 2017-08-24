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
package org.graalvm.compiler.jtt.lang;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Math_pow extends JTTTest {

    public static double test(double x, double y) {
        return Math.pow(x, y);
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 2d, 0d);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 2d, 0.5d);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", -2d, 0.5d);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 2d, 1d);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 2d, -1d);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 2d, 2d);
    }

    @Test
    public void run6() throws Throwable {
        runTest("test", 2d, 3.1d);
    }

    @Test
    public void run7() throws Throwable {
        runTest("test", 2d, Double.NaN);
    }

    @Test
    public void run8() throws Throwable {
        runTest("test", Double.NaN, 0d);
    }

    @Test
    public void run9() throws Throwable {
        runTest("test", Double.NaN, 23d);
    }

    @Test
    public void run10() throws Throwable {
        runTest("test", 0.999998, 1500000.0);
    }
}
