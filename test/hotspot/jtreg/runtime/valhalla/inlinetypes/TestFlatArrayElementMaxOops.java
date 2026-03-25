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

package runtime.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test that the FlatArrayElementMaxOops flag works as expected.
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:+UseArrayFlattening -XX:FlatArrayElementMaxOops=0
 *                   -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                   runtime.valhalla.inlinetypes.TestFlatArrayElementMaxOops 0
 */

/*
 * @test
 * @summary Test that the FlatArrayElementMaxOops flag works as expected.
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:+UseArrayFlattening -XX:FlatArrayElementMaxOops=1
 *                   -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                   runtime.valhalla.inlinetypes.TestFlatArrayElementMaxOops 1
 */

/*
 * @test
 * @summary Test that the FlatArrayElementMaxOops flag works as expected.
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @library /test/lib
 * @enablePreview
 * @run main/othervm -XX:+UseArrayFlattening -XX:FlatArrayElementMaxOops=2
 *                   -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening -XX:+UseNonAtomicValueFlattening
 *                   runtime.valhalla.inlinetypes.TestFlatArrayElementMaxOops 2
 */

public class TestFlatArrayElementMaxOops {

    @LooselyConsistentValue
    static value class ValueWithOneOoop {
        Object obj1 = null;
    }

    @LooselyConsistentValue
    static value class ValueWithTwoOoops {
        Object obj1 = null;
        Object obj2 = null;
    }

    public static void main(String[] args) {
        int FlatArrayElementMaxOops = Integer.valueOf(args[0]);
        Object[] array = ValueClass.newNullRestrictedNonAtomicArray(ValueWithOneOoop.class, 1, new ValueWithOneOoop());
        Asserts.assertEquals(ValueClass.isFlatArray(array), FlatArrayElementMaxOops >= 1);
        array = ValueClass.newNullRestrictedAtomicArray(ValueWithOneOoop.class, 1, new ValueWithOneOoop());
        Asserts.assertEquals(ValueClass.isFlatArray(array), FlatArrayElementMaxOops >= 1);
        array = ValueClass.newNullableAtomicArray(ValueWithOneOoop.class, 1);
        Asserts.assertEquals(ValueClass.isFlatArray(array), FlatArrayElementMaxOops >= 1);

        array = ValueClass.newNullRestrictedNonAtomicArray(ValueWithTwoOoops.class, 1, new ValueWithTwoOoops());
        Asserts.assertEquals(ValueClass.isFlatArray(array), FlatArrayElementMaxOops >= 2);
        array = ValueClass.newNullRestrictedAtomicArray(ValueWithTwoOoops.class, 1, new ValueWithTwoOoops());
        Asserts.assertEquals(ValueClass.isFlatArray(array), FlatArrayElementMaxOops >= 2);
        array = ValueClass.newNullableAtomicArray(ValueWithTwoOoops.class, 1);
        Asserts.assertFalse(ValueClass.isFlatArray(array));
    }
}
