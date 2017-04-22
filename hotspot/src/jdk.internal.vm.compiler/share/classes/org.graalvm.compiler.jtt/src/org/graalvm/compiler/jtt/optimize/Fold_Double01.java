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
 * Tests constant folding of float operations.
 */
public class Fold_Double01 extends JTTTest {

    public static double test(double arg) {
        if (arg == 0) {
            return add();
        }
        if (arg == 1) {
            return sub();
        }
        if (arg == 2) {
            return mul();
        }
        if (arg == 3) {
            return div();
        }
        if (arg == 4) {
            return mod();
        }
        return 0;
    }

    public static double add() {
        double x = 3;
        return x + 7;
    }

    public static double sub() {
        double x = 15;
        return x - 4;
    }

    public static double mul() {
        double x = 6;
        return x * 2;
    }

    public static double div() {
        double x = 26;
        return x / 2;
    }

    public static double mod() {
        double x = 29;
        return x % 15;
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

    @Test
    public void run4() throws Throwable {
        runTest("test", 4d);
    }

}
