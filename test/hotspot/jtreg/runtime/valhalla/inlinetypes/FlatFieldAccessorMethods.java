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
 *
 */

/*
 * @test FlatFieldAccessorMethods
 * @bug 8380059
 * @summary Test trivial getter and setter methods for flattened fields
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @enablePreview
 * @compile FlatFieldAccessorMethods.java
 * @run main runtime.valhalla.inlinetypes.FlatFieldAccessorMethods
 */

package runtime.valhalla.inlinetypes;

import java.lang.reflect.Field;

import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

public class FlatFieldAccessorMethods {
    static final Unsafe U = Unsafe.getUnsafe();

    static class Holder {
        Boolean value;

        Holder(Boolean value) {
            this.value = value;
        }

        Boolean getValue() {
            return value;
        }

        void setValue(Boolean value) {
            this.value = value;
        }
    }

    public static void main(String[] args) throws Exception {
        assertFlatField();
        testGetterMaterializesFlatField();
        testSetterStoresFlatFieldPayload();
        testGetterResultCanBeStoredThroughSetter();
    }

    static void assertFlatField() throws ReflectiveOperationException {
        Field value = Holder.class.getDeclaredField("value");
        Asserts.assertTrue(U.isFlatField(value), "Holder.value should be flat");
        Asserts.assertTrue(U.hasNullMarker(value), "Holder.value should be nullable flat");
    }

    static void testGetterMaterializesFlatField() {
        Holder holder = new Holder(Boolean.TRUE);

        holder.getValue(); // Resolve the field entry.
        assertBoolean(holder.getValue(), true, "getter result");
    }

    static void testSetterStoresFlatFieldPayload() {
        Holder holder = new Holder(Boolean.FALSE);

        holder.setValue(Boolean.TRUE); // Resolve the field entry.
        holder.setValue(Boolean.FALSE);

        assertBoolean(holder.value, false, "direct field read after setter");
        assertBoolean(holder.getValue(), false, "getter result after setter");
    }

    static void testGetterResultCanBeStoredThroughSetter() {
        Holder source = new Holder(Boolean.TRUE);
        Holder target = new Holder(Boolean.FALSE);

        source.getValue(); // Resolve the getter field entry.
        target.setValue(Boolean.FALSE); // Resolve the setter field entry.
        target.setValue(source.getValue());

        assertBoolean(target.value, true, "direct field read after getter-to-setter");
        assertBoolean(target.getValue(), true, "getter result after getter-to-setter");
    }

    static void assertBoolean(Boolean value, boolean expected, String message) {
        Asserts.assertNotNull(value, message);
        Asserts.assertEquals(value.booleanValue(), expected, message);
    }
}
