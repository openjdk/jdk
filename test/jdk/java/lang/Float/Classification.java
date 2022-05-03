/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;

/**
 * @test
 * @summary Verify isNan, isInfinite, isFinite
 * @bug 8285973
 * @library /test/lib
 * @run main/othervm Classification
 */
public class Classification {
    public static void main(String[] args) {
        testIsNaN();
        testIsInfinite();
        testIsFinite();
    }

    static void testIsNaN() {
        Asserts.assertTrue(Float.isNaN(Float.NaN));
        Asserts.assertFalse(Float.isNaN(0));
        Asserts.assertFalse(Float.isNaN(1));
        Asserts.assertFalse(Float.isNaN(Float.MIN_VALUE));
        Asserts.assertFalse(Float.isNaN(Float.MAX_VALUE));
        Asserts.assertFalse(Float.isNaN(Float.POSITIVE_INFINITY));
        Asserts.assertFalse(Float.isNaN(Float.NEGATIVE_INFINITY));
    }

    static void testIsInfinite() {
        Asserts.assertFalse(Float.isInfinite(Float.NaN));
        Asserts.assertFalse(Float.isInfinite(0));
        Asserts.assertFalse(Float.isInfinite(1));
        Asserts.assertFalse(Float.isInfinite(Float.MIN_VALUE));
        Asserts.assertFalse(Float.isInfinite(Float.MAX_VALUE));
        Asserts.assertTrue(Float.isInfinite(Float.POSITIVE_INFINITY));
        Asserts.assertTrue(Float.isInfinite(Float.NEGATIVE_INFINITY));
    }

    static void testIsFinite() {
        Asserts.assertFalse(Float.isFinite(Float.NaN));
        Asserts.assertTrue(Float.isFinite(0));
        Asserts.assertTrue(Float.isFinite(1));
        Asserts.assertTrue(Float.isFinite(Float.MIN_VALUE));
        Asserts.assertTrue(Float.isFinite(Float.MAX_VALUE));
        Asserts.assertFalse(Float.isFinite(Float.POSITIVE_INFINITY));
        Asserts.assertFalse(Float.isFinite(Float.NEGATIVE_INFINITY));
    }
}
