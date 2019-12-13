/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

public class NumberOfTrailingZeroings003 extends GraalCompilerTest {

    public static boolean numberOfTrailingZeros0() {
        return Integer.numberOfTrailingZeros(0) == 32;
    }

    @Test
    public void testNumberOfTrailingZeros0() {
        test("numberOfTrailingZeros0");
    }

    public static boolean numberOfTrailingZeros1() {
        return Integer.numberOfTrailingZeros(1) == 0;
    }

    @Test
    public void testNumberOfTrailingZeros1() {
        test("numberOfTrailingZeros1");
    }

    public static boolean numberOfTrailingZerosM1() {
        return Integer.numberOfTrailingZeros(-1) == 0;
    }

    @Test
    public void testNumberOfTrailingZerosM1() {
        test("numberOfTrailingZerosM1");
    }

    public static boolean numberOfTrailingZerosMin() {
        return Integer.numberOfTrailingZeros(Integer.MIN_VALUE) == 31;
    }

    @Test
    public void testNumberOfTrailingZerosMin() {
        test("numberOfTrailingZerosMin");
    }

    public static boolean numberOfTrailingZerosMax() {
        return Integer.numberOfTrailingZeros(Integer.MAX_VALUE) == 0;
    }

    @Test
    public void testNumberOfTrailingZerosMax() {
        test("numberOfTrailingZerosMax");
    }
}
