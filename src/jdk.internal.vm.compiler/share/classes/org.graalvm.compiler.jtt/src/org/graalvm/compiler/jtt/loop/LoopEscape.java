/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop


package org.graalvm.compiler.jtt.loop;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Test around an object that escapes directly from inside a loop (no virtual phi on the loop)
 */
public class LoopEscape extends JTTTest {

    public static L ll = new L(0, 1, 2);

    private static class L {

        public int a;
        public int b;
        public int c;

        public L(int a, int b, int c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }
    }

    public static int test0(int count) {
        L l = new L(5, 5, 5);
        for (int i = 0; i < count; i++) {
            l.a++;
            l.b--;
            l.c = 4;
        }

        return l.a + l.b * 10 + l.c * 100;
    }

    public static int test1(int count) {
        L l = new L(5, 5, 5);
        for (int i = 0; i < count; i++) {
            if (l.a % 2 == 0) {
                l.a++;
                l.b--;
                l.c = 4;
            } else {
                l.a++;
            }
        }

        return l.a + l.b * 10 + l.c * 100;
    }

    @Test
    public void run10() throws Throwable {
        runTest("test1", 0);
    }

    @Test
    public void run11() throws Throwable {
        runTest("test1", 1);
    }

    @Test
    public void run12() throws Throwable {
        runTest("test1", 2);
    }

    @Test
    public void run00() throws Throwable {
        runTest("test0", 0);
    }

    @Test
    public void run01() throws Throwable {
        runTest("test0", 1);
    }

    @Test
    public void run02() throws Throwable {
        runTest("test0", 2);
    }

    @Test
    public void run05() throws Throwable {
        runTest("test0", 5);
    }
}
