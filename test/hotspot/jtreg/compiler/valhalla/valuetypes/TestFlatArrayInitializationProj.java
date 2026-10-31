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

/*
 * @test
 * @summary Test that cloning flat exact klass arrays have a NarrowMemProj
 *          which is required for some memory optimizations.
 * @bug 8390508
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver ${test.main.class}
 */

package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Asserts;

public class TestFlatArrayInitializationProj {

    @LooselyConsistentValue
    static value class MyValue {
        byte x;
        byte y;

        MyValue(byte value) {
            x = value;
            y = value;
        }
    }

    static final int SIZE = 5;
    static final MyValue VALUE = new MyValue((byte)42);
    static final MyValue[] ATOMIC_NULL_FREE = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, SIZE, VALUE);
    static final MyValue[] NON_ATOMIC_NULL_FREE = (MyValue[])ValueClass.newNullRestrictedNonAtomicArray(MyValue.class, SIZE, VALUE);
    static final MyValue[] ATOMIC_NULLABLE = (MyValue[])ValueClass.newNullableAtomicArray(MyValue.class, SIZE);

    public static void main(String[] args) {
        TestFramework.runWithFlags(
                "--enable-preview",
                "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED"
        );
    }

    @Run(test = {"testAtomicNullFree",
                 "testNonAtomicNullFree",
                 "testAtomicNullable",
                 "testMaybeAtomicNullFree",
                 "testAtomicMaybeNullFree"})
    public static void run() {
        verify(testAtomicNullFree(), ATOMIC_NULL_FREE);
        verify(testNonAtomicNullFree(), NON_ATOMIC_NULL_FREE);
        verify(testAtomicNullable(), ATOMIC_NULLABLE);
        verify(testMaybeAtomicNullFree(true), ATOMIC_NULL_FREE);
        verify(testMaybeAtomicNullFree(false), NON_ATOMIC_NULL_FREE);
        verify(testAtomicMaybeNullFree(true), ATOMIC_NULL_FREE);
        verify(testAtomicMaybeNullFree(false), ATOMIC_NULLABLE);
    }

    private static void verify(MyValue[] src, MyValue[] dst) {
        // Basic checks for array equality.
        Asserts.assertTrue(src != dst);
        Asserts.assertEQ(src.length, dst.length);
        for (int i = 0; i < src.length; i++) {
            Asserts.assertTrue(src[i] == dst[i]);
        }
        // Additional checks for flat array properties.
        Asserts.assertEQ(ValueClass.isFlatArray(src), ValueClass.isFlatArray(dst));
        Asserts.assertEQ(ValueClass.isNullRestrictedArray(src), ValueClass.isNullRestrictedArray(dst));
        Asserts.assertEQ(ValueClass.isAtomicArray(src), ValueClass.isAtomicArray(dst));
    }

    @Test
    @IR(phase = CompilePhase.SPLIT_INLINES_ARRAY,
        counts = {IRNode.NARROW_MEM_PROJ_OF_FIELD, "x", "1",
                  IRNode.NARROW_MEM_PROJ_OF_FIELD, "y", "1",
                  IRNode.NARROW_MEM_PROJ_OF_ADR_TYPE, "aryptr:flat:", "2"})
    public static MyValue[] testAtomicNullFree() {
        return ATOMIC_NULL_FREE.clone();
    }

    @Test
    @IR(phase = CompilePhase.SPLIT_INLINES_ARRAY,
        counts = {IRNode.NARROW_MEM_PROJ_OF_FIELD, "x", "1",
                  IRNode.NARROW_MEM_PROJ_OF_FIELD, "y", "1",
                  IRNode.NARROW_MEM_PROJ_OF_ADR_TYPE, "aryptr:flat:", "2"})
    public static MyValue[] testNonAtomicNullFree() {
        return NON_ATOMIC_NULL_FREE.clone();
    }

    @Test
    @IR(phase = CompilePhase.SPLIT_INLINES_ARRAY,
        counts = {IRNode.NARROW_MEM_PROJ_OF_FIELD, "x", "1",
                  IRNode.NARROW_MEM_PROJ_OF_FIELD, "y", "1",
                  IRNode.NARROW_MEM_PROJ_OF_ADR_TYPE, "aryptr:flat:", "3"}) // 2 + 1 null marker
    public static MyValue[] testAtomicNullable() {
        return ATOMIC_NULLABLE.clone();
    }

    @Test
    // Since we have more than one layout variant, the type is inexact and
    // we don't add a NarrowMemProj.
    @IR(phase = CompilePhase.SPLIT_INLINES_ARRAY,
        counts = {IRNode.NARROW_MEM_PROJ_OF_FIELD, "x", "0",
                  IRNode.NARROW_MEM_PROJ_OF_FIELD, "y", "0",
                  IRNode.NARROW_MEM_PROJ_OF_ADR_TYPE, "aryptr:flat:", "0"})
    public static MyValue[] testMaybeAtomicNullFree(boolean atomic) {
        return (atomic ? ATOMIC_NULL_FREE : NON_ATOMIC_NULL_FREE).clone();
    }

    @Test
    // Since we have more than one layout variant, the type is inexact and
    // we don't add a NarrowMemProj.
    @IR(phase = CompilePhase.SPLIT_INLINES_ARRAY,
        counts = {IRNode.NARROW_MEM_PROJ_OF_FIELD, "x", "0",
                  IRNode.NARROW_MEM_PROJ_OF_FIELD, "y", "0",
                  IRNode.NARROW_MEM_PROJ_OF_ADR_TYPE, "aryptr:flat:", "0"})
    public static MyValue[] testAtomicMaybeNullFree(boolean nullFree) {
        return (nullFree ? ATOMIC_NULL_FREE : ATOMIC_NULLABLE).clone();
    }
}
