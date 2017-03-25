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
 */
public class Fold_Double03 extends JTTTest {

    private static final double MINUS_ZERO = 1 / Double.NEGATIVE_INFINITY;

    public static double test(int t, double a) {
        double v;
        if (t == 0) {
            v = a * 0.0;
        } else {
            v = a * MINUS_ZERO;
        }
        return 1 / v;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0, 5.0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1, 5.0);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 0, -5.0);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 1, -5.0);
    }

}
