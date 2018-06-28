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


package org.graalvm.compiler.jtt.jdk;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

public class DivideUnsigned extends JTTTest {

    public static int divUInt(int a, int b) {
        return Integer.divideUnsigned(a, b);
    }

    public static int remUInt(int a, int b) {
        return Integer.remainderUnsigned(a, b);
    }

    public static long divULong(long a, long b) {
        return Long.divideUnsigned(a, b);
    }

    public static long remULong(long a, long b) {
        return Long.remainderUnsigned(a, b);
    }

    public void testInt(int a, int b) {
        runTest("divUInt", a, b);
        runTest("remUInt", a, b);
    }

    public void testLong(long a, long b) {
        runTest("divULong", a, b);
        runTest("remULong", a, b);
    }

    @Test
    public void testIntPP() {
        testInt(5, 2);
    }

    @Test
    public void testIntNP() {
        testInt(-5, 2);
    }

    @Test
    public void testIntPN() {
        testInt(5, -2);
    }

    @Test
    public void testIntNN() {
        testInt(-5, -2);
    }

    @Test
    public void testLongPP() {
        testLong(5, 2);
    }

    @Test
    public void testLongNP() {
        testLong(-5, 2);
    }

    @Test
    public void testLongPN() {
        testLong(5, -2);
    }

    @Test
    public void testLongNN() {
        testLong(-5, -2);
    }
}
