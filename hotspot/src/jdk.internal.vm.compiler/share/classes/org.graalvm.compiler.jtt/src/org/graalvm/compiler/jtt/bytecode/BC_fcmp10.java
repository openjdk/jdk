/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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
public class BC_fcmp10 extends JTTTest {

    public static boolean test(int x) {
        float a = 0;
        float b = 0;
        switch (x) {
            case 0:
                a = Float.POSITIVE_INFINITY;
                b = 1;
                break;
            case 1:
                a = 1;
                b = Float.POSITIVE_INFINITY;
                break;
            case 2:
                a = Float.NEGATIVE_INFINITY;
                b = 1;
                break;
            case 3:
                a = 1;
                b = Float.NEGATIVE_INFINITY;
                break;
            case 4:
                a = Float.NEGATIVE_INFINITY;
                b = Float.NEGATIVE_INFINITY;
                break;
            case 5:
                a = Float.NEGATIVE_INFINITY;
                b = Float.POSITIVE_INFINITY;
                break;
            case 6:
                a = Float.NaN;
                b = Float.POSITIVE_INFINITY;
                break;
            case 7:
                a = 1;
                b = Float.NaN;
                break;
            case 8:
                a = 1;
                b = -0.0f / 0.0f;
                break;
        }
        return a <= b;
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

    @Test
    public void run6() throws Throwable {
        runTest("test", 6);
    }

    @Test
    public void run7() throws Throwable {
        runTest("test", 7);
    }

    @Test
    public void run8() throws Throwable {
        runTest("test", 8);
    }

}
