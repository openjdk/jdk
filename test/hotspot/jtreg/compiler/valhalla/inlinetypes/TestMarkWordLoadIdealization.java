/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/**
 * @test
 * @summary Test that loads of object markword bits that are know at JIT-compile
 *          time are constant-folded by C2 idealizations.
 * @library /test/lib /
 * @requires vm.compiler2.enabled & vm.flagless
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 * @run driver ${test.main.class}
 */

public class TestMarkWordLoadIdealization {

    // Wrap these variables into helper class because WhiteBox API needs to be
    // initialized by TestFramework first.
    static class WB {
        static final long MARK_WORD_OFFSET             = WhiteBox.getWhiteBox().getMarkWordOffset();
        static final long INLINE_TYPE_PATTERN          = WhiteBox.getWhiteBox().getInlineTypePattern();
        static final long NULL_FREE_ARRAY_BIT_IN_PLACE = WhiteBox.getWhiteBox().getNullFreeArrayBitInPlace();
        static final long FLAT_ARRAY_BIT_IN_PLACE      = WhiteBox.getWhiteBox().getFlatArrayBitInPlace();
    }

    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static final Object IDENTITY_OBJECT = new Object();
    static final Integer VALUE_OBJECT = Integer.valueOf(42);

    static final String[]  IDENTITY_OBJECT_ARRAY = new String[1];
    static final Integer[] VALUE_OBJECT_ARRAY    = new Integer[1];
    static final Integer[] VALUE_OBJECT_ARRAY_NULL_RESTRICTED =
        (Integer[]) ValueClass.newNullRestrictedNonAtomicArray(Integer.class, 2, Integer.valueOf(0));

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                                   "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                                   "--enable-preview");
    }

    @Test
    @IR(failOn = IRNode.LOAD_L)
    public static boolean testInlineTypePatternBitLoadNegativeIdealization() {
        return (UNSAFE.getLong(IDENTITY_OBJECT, WB.MARK_WORD_OFFSET) & WB.INLINE_TYPE_PATTERN) != 0L;
    }

    @Test
    @IR(failOn = IRNode.LOAD_L)
    public static boolean testInlineTypePatternBitLoadPositiveIdealization() {
        return (UNSAFE.getLong(VALUE_OBJECT, WB.MARK_WORD_OFFSET) & WB.INLINE_TYPE_PATTERN) != 0L;
    }

    @Test
    @IR(failOn = IRNode.LOAD_L)
    public static boolean testNullFreeArrayBitLoadNegativeIdealization() {
        return (UNSAFE.getLong(VALUE_OBJECT_ARRAY, WB.MARK_WORD_OFFSET) & WB.NULL_FREE_ARRAY_BIT_IN_PLACE) != 0L;
    }

    @Test
    @IR(failOn = IRNode.LOAD_L)
    public static boolean testNullFreeArrayBitLoadPositiveIdealization() {
        return (UNSAFE.getLong(VALUE_OBJECT_ARRAY_NULL_RESTRICTED, WB.MARK_WORD_OFFSET) & WB.NULL_FREE_ARRAY_BIT_IN_PLACE) != 0L;
    }

    @Test
    @IR(failOn = IRNode.LOAD_L)
    public static boolean testFlatArrayBitLoadNegativeIdealization() {
        return (UNSAFE.getLong(IDENTITY_OBJECT_ARRAY, WB.MARK_WORD_OFFSET) & WB.FLAT_ARRAY_BIT_IN_PLACE) != 0L;
    }

    @Test
    @IR(failOn = IRNode.LOAD_L)
    public static boolean testFlatArrayBitLoadPositiveIdealization() {
        return (UNSAFE.getLong(VALUE_OBJECT_ARRAY, WB.MARK_WORD_OFFSET) & WB.FLAT_ARRAY_BIT_IN_PLACE) != 0L;
    }

    @Run(test = {"testInlineTypePatternBitLoadNegativeIdealization",
                 "testInlineTypePatternBitLoadPositiveIdealization",
                 "testNullFreeArrayBitLoadNegativeIdealization",
                 "testNullFreeArrayBitLoadPositiveIdealization",
                 "testFlatArrayBitLoadNegativeIdealization",
                 "testFlatArrayBitLoadPositiveIdealization"})
    void run() {
        Asserts.assertFalse(testInlineTypePatternBitLoadNegativeIdealization());
        Asserts.assertTrue(testInlineTypePatternBitLoadPositiveIdealization());
        Asserts.assertFalse(testNullFreeArrayBitLoadNegativeIdealization());
        Asserts.assertTrue(testNullFreeArrayBitLoadPositiveIdealization());
        Asserts.assertFalse(testFlatArrayBitLoadNegativeIdealization());
        Asserts.assertTrue(testFlatArrayBitLoadPositiveIdealization());
    }
}
