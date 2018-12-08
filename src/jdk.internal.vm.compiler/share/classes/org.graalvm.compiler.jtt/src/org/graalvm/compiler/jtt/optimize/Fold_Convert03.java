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
 * Tests constant folding of float conversions
 */
public class Fold_Convert03 extends JTTTest {

    public static float test(float arg) {
        if (arg == 0) {
            return i2f();
        }
        if (arg == 1) {
            return l2f();
        }
        if (arg == 2) {
            return d2f();
        }
        return 0;
    }

    public static float i2f() {
        int x = 1024;
        return x;
    }

    public static float l2f() {
        long x = -33;
        return x;
    }

    public static float d2f() {
        double x = -78.1d;
        return (float) x;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0.0F);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1.0F);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 2.0F);
    }

}
