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
package org.graalvm.compiler.jtt.except;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Catch_Loop01 extends JTTTest {

    public static int test(int arg) {
        int accum = 0;
        for (int i = 0; i < arg; i++) {
            try {
                accum += div(20, i);
            } catch (ArithmeticException e) {
                accum -= 100;
            }
        }
        return accum;
    }

    static int div(int a, int b) {
        return a / (b % 3);
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 4);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 5);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", 6);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", 7);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test", 30);
    }

}
