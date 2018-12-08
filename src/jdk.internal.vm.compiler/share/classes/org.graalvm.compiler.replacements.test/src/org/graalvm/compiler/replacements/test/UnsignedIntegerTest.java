/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;

/**
 * Tests the unsigned operations on {@link Integer} and {@link Long}.
 */
public class UnsignedIntegerTest extends GraalCompilerTest {

    public static int compareInteger(int a, int b) {
        return Integer.compareUnsigned(a, b);
    }

    public static int divideInteger(int a, int b) {
        return Integer.divideUnsigned(a, b);
    }

    public static int remainderInteger(int a, int b) {
        return Integer.remainderUnsigned(a, b);
    }

    public static long compareLong(long a, long b) {
        return Long.compareUnsigned(a, b);
    }

    public static long divideLong(long a, long b) {
        return Long.divideUnsigned(a, b);
    }

    public static long remainderLong(long a, long b) {
        return Long.remainderUnsigned(a, b);
    }

    private void testInteger(int a, int b) {
        test("compareInteger", a, b);
        test("divideInteger", a, b);
        test("remainderInteger", a, b);
    }

    private void testLong(long a, long b) {
        test("compareLong", a, b);
        test("divideLong", a, b);
        test("remainderLong", a, b);
    }

    @Test
    public void testInteger() {
        testInteger(5, 7);
        testInteger(-3, -7);
        testInteger(-3, 7);
        testInteger(42, -5);
    }

    @Test
    public void testLong() {
        testLong(5, 7);
        testLong(-3, -7);
        testLong(-3, 7);
        testLong(42, -5);
    }
}
