/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.lang;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class Math_exact extends JTTTest {

    public static int testIntAddExact(int a, int b) {
        return Math.addExact(a, b);
    }

    @Test
    public void runTestIntAddExact() throws Throwable {
        runTest("testIntAddExact", 1, 2);
        runTest("testIntAddExact", 1, Integer.MAX_VALUE);
        runTest("testIntAddExact", -1, Integer.MIN_VALUE);
    }

    public static long testLongAddExact(long a, long b) {
        return Math.addExact(a, b);
    }

    @Test
    public void runTestLongAddExact() throws Throwable {
        runTest("testLongAddExact", 1L, 2L);
        runTest("testLongAddExact", 1L, Long.MAX_VALUE);
        runTest("testLongAddExact", -1L, Long.MIN_VALUE);
    }

    public static int testIntSubExact(int a, int b) {
        return Math.subtractExact(a, b);
    }

    @Test
    public void runTestIntSubExact() throws Throwable {
        runTest("testIntSubExact", 1, 2);
        runTest("testIntSubExact", -2, Integer.MAX_VALUE);
        runTest("testIntSubExact", 2, Integer.MIN_VALUE);
    }

    public static long testLongSubExact(long a, long b) {
        return Math.subtractExact(a, b);
    }

    @Test
    public void runTestLongSubExact() throws Throwable {
        runTest("testLongSubExact", 1L, 2L);
        runTest("testLongSubExact", -2L, Long.MAX_VALUE);
        runTest("testLongSubExact", 2L, Long.MIN_VALUE);
    }

    public static int testIntMulExact(int a, int b) {
        return Math.multiplyExact(a, b);
    }

    @Test
    public void runTestIntMulExact() throws Throwable {
        runTest("testIntMulExact", 1, 2);
        runTest("testIntMulExact", -2, Integer.MAX_VALUE);
        runTest("testIntMulExact", 2, Integer.MIN_VALUE);
    }

    public static long testLongMulExact(long a, long b) {
        return Math.multiplyExact(a, b);
    }

    @Test
    public void runTestLongMulExact() throws Throwable {
        runTest("testLongMulExact", 1L, 2L);
        runTest("testLongMulExact", 2L, Long.MAX_VALUE);
        runTest("testLongMulExact", -2L, Long.MIN_VALUE);
    }
}
