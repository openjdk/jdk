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

package runtime.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;

/*
 * @test
 * @bug 8382937
 * @summary System.arraycopy must throw NPE when copying null into a null-restricted array.
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 * @enablePreview
 * @run main/othervm -XX:+UnlockExperimentalVMOptions
 *                   -XX:-UseNullFreeAtomicValueFlattening -XX:-UseNullFreeNonAtomicValueFlattening
 *                   runtime.valhalla.inlinetypes.ArrayCopyStoreNull
 */
public class ArrayCopyStoreNull {
    value record MyRecord(int f) {}

    public static void main(String... args) {
        MyRecord[] nullableArray =
                (MyRecord[]) ValueClass.newNullableAtomicArray(MyRecord.class, 2);
        MyRecord[] nullRestrictedArray =
                (MyRecord[]) ValueClass.newNullRestrictedAtomicArray(MyRecord.class, 2, new MyRecord(1));

        // Non-null value can be copied into a null-restricted array.
        nullableArray[0] = new MyRecord(42);
        System.arraycopy(nullableArray, 0, nullRestrictedArray, 0, 1);

        // Null value cannot be copied into a null-restricted array.
        nullableArray[1] = null;
        try {
            System.arraycopy(nullableArray, 1, nullRestrictedArray, 1, 1);
        } catch (NullPointerException expected) {
            return;
        }

        throw new RuntimeException("System.arraycopy did not throw the required NullPointerException");
    }
}
