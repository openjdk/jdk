/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * Tests constant folding of integer comparisons.
 */
public class Fold_Double02 extends JTTTest {

    public static boolean test(int arg) {
        if (arg == 0) {
            return equ();
        }
        if (arg == 1) {
            return neq();
        }
        if (arg == 2) {
            return geq();
        }
        if (arg == 3) {
            return ge();
        }
        if (arg == 4) {
            return ltq();
        }
        if (arg == 5) {
            return lt();
        }
        return false;
    }

    static boolean equ() {
        double x = 34;
        return x == 34;
    }

    static boolean neq() {
        double x = 34;
        return x != 33;
    }

    static boolean geq() {
        double x = 34;
        return x >= 33;
    }

    static boolean ge() {
        double x = 34;
        return x > 35;
    }

    static boolean ltq() {
        double x = 34;
        return x <= 32;
    }

    static boolean lt() {
        double x = 34;
        return x < 31;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 3);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 4);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 5);
    }

}
