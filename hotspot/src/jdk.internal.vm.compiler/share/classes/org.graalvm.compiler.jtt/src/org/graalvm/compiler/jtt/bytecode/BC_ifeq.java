/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
public class BC_ifeq extends JTTTest {

    public static int test(int a) {
        int n = 0;
        if (a == 0) {
            n += 1;
        } else {
            n -= 1;
        }
        if (a != 0) {
            n -= 1;
        } else {
            n += 1;
        }
        return n;
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
    public void run3() {
        runTest("testb", 0xff);
    }

    /**
     * Tests if the if does work properly on byte stamp.
     */
    public static int testb(int b) {
        byte x = (byte) b;
        int y = x & 0xff;
        if (y == 0xff) {
            // Just do anything else to force jump instead of conditional move
            y = (int) (System.currentTimeMillis() >> 32);
        }
        return y;
    }

    @Test
    public void run4() {
        runTest("tests", 0xffff);
    }

    /**
     * Tests if the if does work properly on short stamp.
     */
    public static int tests(int b) {
        short x = (short) b;
        int y = x & 0xffff;
        if (y == 0xffff) {
            // Just do anything else to force jump instead of conditional move
            y = (int) (System.currentTimeMillis() >> 32);
        }
        return y;
    }

    @Test
    public void run5() {
        runTest("testc", 0xffff);
    }

    /**
     * Tests if the if does work properly on char stamp (boils down to short, just to cover all the
     * java types).
     */
    public static int testc(int b) {
        char x = (char) b;
        int y = x & 0xffff;
        if (y == 0xffff) {
            // Just do anything else to force jump instead of conditional move
            y = (int) (System.currentTimeMillis() >> 32);
        }
        return y;
    }

    // the same with conditional move
    @Test
    public void run6() {
        runTest("testCondb", 0xff);
    }

    /**
     * Tests if the if does work properly on byte stamp.
     */
    public static boolean testCondb(int b) {
        byte x = (byte) b;
        int y = x & 0xff;
        return y == 0xff;
    }

    @Test
    public void run7() {
        runTest("testConds", 0xffff);
    }

    /**
     * Tests if the if does work properly on short stamp.
     */
    public static boolean testConds(int b) {
        short x = (short) b;
        int y = x & 0xffff;
        return y == 0xffff;
    }

    @Test
    public void run8() {
        runTest("testCondc", 0xffff);
    }

    /**
     * Tests if the if does work properly on char type.
     */
    public static boolean testCondc(int b) {
        char x = (char) b;
        int y = x & 0xffff;
        return y == 0xffff;
    }
}
