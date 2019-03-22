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



package org.graalvm.compiler.core.test;

import org.junit.Test;

public class IntegerStampShiftTest extends GraalCompilerTest {

    public static int unsignedShiftPositiveInt(boolean f) {
        int h = f ? 0x7FFFFFF0 : 0x7FFFFF00;
        return h >>> 8;
    }

    @Test
    public void testUnsignedShiftPositiveInt() {
        test("unsignedShiftPositiveInt", false);
    }

    public static int unsignedShiftNegativeInt(boolean f) {
        int h = f ? 0xFFFFFFF0 : 0xFFFFFF00;
        return h >>> 8;
    }

    @Test
    public void testUnsignedShiftNegativeInt() {
        test("unsignedShiftNegativeInt", false);
    }

    public static long unsignedShiftPositiveLong(boolean f) {
        long h = f ? 0x7FFFFFFFFFFFFFF0L : 0x7FFFFFFFFFFFFF00L;
        return h >>> 8;
    }

    @Test
    public void testUnsignedShiftPositiveLong() {
        test("unsignedShiftPositiveLong", false);
    }

    public static long unsignedShiftNegativeLong(boolean f) {
        long h = f ? 0xFFFFFFFFFFFFFFF0L : 0xFFFFFFFFFFFFFF00L;
        return h >>> 8;
    }

    @Test
    public void testUnsignedShiftNegativeLong() {
        test("unsignedShiftNegativeLong", false);
    }
}
