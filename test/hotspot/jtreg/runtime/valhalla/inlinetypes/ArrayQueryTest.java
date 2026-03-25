/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test ValueClass APIs to get array properties
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @enablePreview
 * @run main/othervm -XX:+UseArrayFlattening -XX:+UseFieldFlattening
 *           -XX:+UseNonAtomicValueFlattening -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening
 *           runtime.valhalla.inlinetypes.ArrayQueryTest
 */

 package runtime.valhalla.inlinetypes;

 import jdk.internal.value.ValueClass;
 import jdk.internal.vm.annotation.LooselyConsistentValue;
 import jdk.test.lib.Asserts;


 public class ArrayQueryTest {

    static value class SmallValue {
        short i = 0;
        short j = 0;
    }

    @LooselyConsistentValue
    static value class WeakValue {
        short i = 0;
        short j = 0;
    }

    static value class NaturallyAtomic {
        int i = 0;
    }

    @LooselyConsistentValue
    static value class BigValue {
        long l0 = 0L;
        long l1 = 0L;
        long l2 = 0L;
    }

    public static void main(String[] args) {
        SmallValue[] array0 = new SmallValue[10];
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(array0));
        Asserts.assertTrue(ValueClass.isFlatArray(array0));
        Asserts.assertTrue(ValueClass.isAtomicArray(array0));

        Object[] array1 = ValueClass.newNullRestrictedAtomicArray(SmallValue.class, 10, new SmallValue());
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(array1));
        Asserts.assertTrue(ValueClass.isFlatArray(array1));
        Asserts.assertTrue(ValueClass.isAtomicArray(array1));

        Object[] array2 = ValueClass.newNullableAtomicArray(SmallValue.class, 10);
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(array2));
        Asserts.assertTrue(ValueClass.isFlatArray(array2));
        Asserts.assertTrue(ValueClass.isAtomicArray(array2));

        Object[] array3 = ValueClass.newNullRestrictedNonAtomicArray(WeakValue.class, 10, new WeakValue());
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(array3));
        Asserts.assertTrue(ValueClass.isFlatArray(array3));
        Asserts.assertFalse(ValueClass.isAtomicArray(array3));

        Object[] array4 = ValueClass.newNullRestrictedAtomicArray(WeakValue.class, 10, new WeakValue());
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(array4));
        Asserts.assertTrue(ValueClass.isFlatArray(array4));
        Asserts.assertTrue(ValueClass.isAtomicArray(array4));

        Object[] array5 = ValueClass.newNullRestrictedAtomicArray(NaturallyAtomic.class, 10, new NaturallyAtomic());
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(array5));
        Asserts.assertTrue(ValueClass.isFlatArray(array5));
        Asserts.assertTrue(ValueClass.isAtomicArray(array5));

        Object[] array6 = ValueClass.newNullRestrictedNonAtomicArray(BigValue.class, 10, new BigValue());
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(array6));
        Asserts.assertTrue(ValueClass.isFlatArray(array6));
        Asserts.assertFalse(ValueClass.isAtomicArray(array6));

        Object[] array7 = ValueClass.newNullRestrictedAtomicArray(BigValue.class, 10, new BigValue());
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(array7));
        Asserts.assertFalse(ValueClass.isFlatArray(array7));
        Asserts.assertTrue(ValueClass.isAtomicArray(array7));

        Object[] array8 = ValueClass.newNullableAtomicArray(BigValue.class, 10);
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(array8));
        Asserts.assertFalse(ValueClass.isFlatArray(array8));
        Asserts.assertTrue(ValueClass.isAtomicArray(array8));
    }

 }
