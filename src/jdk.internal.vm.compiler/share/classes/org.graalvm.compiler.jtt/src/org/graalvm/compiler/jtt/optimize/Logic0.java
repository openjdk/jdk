/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
public class Logic0 extends JTTTest {

    public static int test(int a, int b) {
        if (((a != 0 ? 1 : 0) & (a != b ? 1 : 0)) != 0) {
            return 42;
        }
        return 11;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0, 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 0, 33);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 33, 66);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 33, 67);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 33, 33);
    }

    @Test
    public void run5() throws Throwable {
        runTest("test", 0, 32);
    }

    @Test
    public void run6() throws Throwable {
        runTest("test", 32, 66);
    }

    @Test
    public void run7() throws Throwable {
        runTest("test", 32, 67);
    }

    @Test
    public void run8() throws Throwable {
        runTest("test", 32, 32);
    }
}
