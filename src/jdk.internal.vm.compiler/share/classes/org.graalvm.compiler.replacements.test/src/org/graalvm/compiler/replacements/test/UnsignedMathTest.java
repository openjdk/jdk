/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.core.test.GraalCompilerTest;

/**
 * Tests the substitutions for the {@link UnsignedMath} class.
 */
public class UnsignedMathTest extends GraalCompilerTest {

    public static boolean aboveThanInt(int a, int b) {
        return UnsignedMath.aboveThan(a, b);
    }

    public static boolean aboveOrEqualInt(int a, int b) {
        return UnsignedMath.aboveOrEqual(a, b);
    }

    public static boolean belowThanInt(int a, int b) {
        return UnsignedMath.belowThan(a, b);
    }

    public static boolean belowOrEqualInt(int a, int b) {
        return UnsignedMath.belowOrEqual(a, b);
    }

    public static int divideInt(int a, int b) {
        return Integer.divideUnsigned(a, b);
    }

    public static int remainderInt(int a, int b) {
        return Integer.remainderUnsigned(a, b);
    }

    public static boolean aboveThanLong(long a, long b) {
        return UnsignedMath.aboveThan(a, b);
    }

    public static boolean aboveOrEqualLong(long a, long b) {
        return UnsignedMath.aboveOrEqual(a, b);
    }

    public static boolean belowThanLong(long a, long b) {
        return UnsignedMath.belowThan(a, b);
    }

    public static boolean belowOrEqualLong(long a, long b) {
        return UnsignedMath.belowOrEqual(a, b);
    }

    public static long divideLong(long a, long b) {
        return Long.divideUnsigned(a, b);
    }

    public static long remainderLong(long a, long b) {
        return Long.remainderUnsigned(a, b);
    }

    private void testInt(int a, int b) {
        test("aboveThanInt", a, b);
        test("aboveOrEqualInt", a, b);
        test("belowThanInt", a, b);
        test("belowOrEqualInt", a, b);
        test("divideInt", a, b);
        test("remainderInt", a, b);
    }

    private void testLong(long a, long b) {
        test("aboveThanLong", a, b);
        test("aboveOrEqualLong", a, b);
        test("belowThanLong", a, b);
        test("belowOrEqualLong", a, b);
        test("divideLong", a, b);
        test("remainderLong", a, b);
    }

    @Test
    public void testInt() {
        testInt(5, 7);
        testInt(-3, -7);
        testInt(-3, 7);
        testInt(42, -5);
    }

    @Test
    public void testLong() {
        testLong(5, 7);
        testLong(-3, -7);
        testLong(-3, 7);
        testLong(42, -5);
    }
}
