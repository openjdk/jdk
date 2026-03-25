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
 * @summary Test that rematerialized arrays keep their properties.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestArrayRematerializationWithProperties
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=compileonly,*TestArrayRematerializationWithProperties::test
 *                   compiler.valhalla.inlinetypes.TestArrayRematerializationWithProperties
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

import jdk.test.lib.Asserts;

@LooselyConsistentValue
value class MyValueArrayRematWithProp  {
    byte x = 42;
    byte y = 43;

    static final MyValueArrayRematWithProp DEFAULT = new MyValueArrayRematWithProp();
}

public class TestArrayRematerializationWithProperties {

    static final boolean FLAT0 = ValueClass.isFlatArray(new MyValueArrayRematWithProp[1]);
    static final boolean FLAT1 = ValueClass.isFlatArray(ValueClass.newNullRestrictedAtomicArray(MyValueArrayRematWithProp.class, 1, MyValueArrayRematWithProp.DEFAULT));
    static final boolean FLAT2 = ValueClass.isFlatArray(ValueClass.newNullableAtomicArray(MyValueArrayRematWithProp.class, 1));
    static final boolean FLAT3 = ValueClass.isFlatArray(ValueClass.newNullRestrictedNonAtomicArray(MyValueArrayRematWithProp.class, 1, MyValueArrayRematWithProp.DEFAULT));

    static final boolean ATOMIC0 = ValueClass.isAtomicArray(new MyValueArrayRematWithProp[1]);
    static final boolean ATOMIC1 = ValueClass.isAtomicArray(ValueClass.newNullRestrictedAtomicArray(MyValueArrayRematWithProp.class, 1, MyValueArrayRematWithProp.DEFAULT));
    static final boolean ATOMIC2 = ValueClass.isAtomicArray(ValueClass.newNullableAtomicArray(MyValueArrayRematWithProp.class, 1));
    static final boolean ATOMIC3 = ValueClass.isAtomicArray(ValueClass.newNullRestrictedNonAtomicArray(MyValueArrayRematWithProp.class, 1, MyValueArrayRematWithProp.DEFAULT));

    static void test(boolean b) {
        // C2 will scalar replace these arrays
        MyValueArrayRematWithProp[] array0 = { MyValueArrayRematWithProp.DEFAULT };
        MyValueArrayRematWithProp[] array1 = (MyValueArrayRematWithProp[])ValueClass.newNullRestrictedAtomicArray(MyValueArrayRematWithProp.class, 1, MyValueArrayRematWithProp.DEFAULT);
        MyValueArrayRematWithProp[] array2 = (MyValueArrayRematWithProp[])ValueClass.newNullableAtomicArray(MyValueArrayRematWithProp.class, 1);
        array2[0] = MyValueArrayRematWithProp.DEFAULT;
        MyValueArrayRematWithProp[] array3 = (MyValueArrayRematWithProp[])ValueClass.newNullRestrictedNonAtomicArray(MyValueArrayRematWithProp.class, 1, MyValueArrayRematWithProp.DEFAULT);

        if (b) {
            // Uncommon trap, check content and properties of rematerialized arrays
            Asserts.assertEquals(array0[0], MyValueArrayRematWithProp.DEFAULT);
            Asserts.assertEquals(array1[0], MyValueArrayRematWithProp.DEFAULT);
            Asserts.assertEquals(array2[0], MyValueArrayRematWithProp.DEFAULT);
            Asserts.assertEquals(array3[0], MyValueArrayRematWithProp.DEFAULT);

            Asserts.assertEquals(ValueClass.isAtomicArray(array0), ATOMIC0);
            Asserts.assertEquals(ValueClass.isAtomicArray(array1), ATOMIC1);
            Asserts.assertEquals(ValueClass.isAtomicArray(array2), ATOMIC2);
            Asserts.assertEquals(ValueClass.isAtomicArray(array3), ATOMIC3);

            Asserts.assertFalse(ValueClass.isNullRestrictedArray(array0));
            Asserts.assertTrue(ValueClass.isNullRestrictedArray(array1));
            Asserts.assertFalse(ValueClass.isNullRestrictedArray(array2));
            Asserts.assertTrue(ValueClass.isNullRestrictedArray(array3));

            Asserts.assertEquals(ValueClass.isFlatArray(array0), FLAT0);
            Asserts.assertEquals(ValueClass.isFlatArray(array1), FLAT1);
            Asserts.assertEquals(ValueClass.isFlatArray(array2), FLAT2);
            Asserts.assertEquals(ValueClass.isFlatArray(array3), FLAT3);
        }
    }

    public static void main(String[] args) {
        // Warmup
        for (int i = 0; i < 100_000; ++i) {
            test(false);
        }
        // Trigger deopt
        test(true);
    }
}
