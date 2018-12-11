/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Tests calls to the array copy method.
 */
public class ArrayCopy02 extends JTTTest {

    public static int[] src = new int[]{0, 1, 2, 3, 4, 5};
    public static int[] dest0 = new int[]{5, 4, 3, 2, 1, 0};
    public static int[] dest = new int[]{5, 4, 3, 2, 1, 0};
    static {
        // Ensure System is resolved
        System.arraycopy(src, 0, src, 0, src.length);
    }

    @Before
    public void setUp() {
        System.currentTimeMillis();
        for (int i = 0; i < dest.length; i++) {
            dest[i] = dest0[i];
        }
    }

    public static int[] test(int srcPos, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
        return dest;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0, 0, 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 0, 0, -1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", -1, 0, 0);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 0, -1, 0);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 0, 0, 2);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 0, 1, 6);
    }

    @Test
    public void run6() throws Throwable {
        runTest("test", 1, 0, 6);
    }

    @Test
    public void run7() throws Throwable {
        runTest("test", 1, 1, -1);
    }

    @Test
    public void run8() throws Throwable {
        runTest("test", 0, 1, 2);
    }

    @Test
    public void run9() throws Throwable {
        runTest("test", 1, 0, 2);
    }

    @Test
    public void run10() throws Throwable {
        runTest("test", 1, 1, 2);
    }

    @Test
    public void run11() throws Throwable {
        runTest("test", 0, 0, 6);
    }

    @Test
    public void run12() throws Throwable {
        runTest("test", 0, 1, 5);
    }

    @Test
    public void run13() throws Throwable {
        runTest("test", 1, 0, 5);
    }

    @Test
    public void run14() throws Throwable {
        runTest("test", 1, 1, 5);
    }

}
