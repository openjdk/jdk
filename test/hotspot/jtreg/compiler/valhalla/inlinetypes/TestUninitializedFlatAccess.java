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

import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8389089
 * @summary Accessing an uninitialized flat field or array should not initialize its value class.
 * @library /test/lib
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 */

public class TestUninitializedFlatAccess {
    static int fieldClassInitCount;
    static int arrayClassInitCount;

    static value class FieldValue {
        final int value = 0;

        static {
            fieldClassInitCount++;
        }
    }

    static value class ArrayValue {
        final int value = 0;

        static {
            arrayClassInitCount++;
        }
    }

    FieldValue value;

    Object testFieldLoad() {
        return value;
    }

    void testFieldStore(FieldValue value) {
        this.value = value;
    }

    static Object testArrayLoad() {
        ArrayValue[] array = new ArrayValue[1];
        return array[0];
    }

    static ArrayValue[] testArrayStore(ArrayValue value) {
        ArrayValue[] array = new ArrayValue[1];
        array[0] = value;
        return array;
    }

    public static void main(String[] args) {
        TestUninitializedFlatAccess t = new TestUninitializedFlatAccess();
        ArrayValue[] tmp = new ArrayValue[0];
        Asserts.assertEQ(fieldClassInitCount, 0, "FieldValue should not be initialized");
        Asserts.assertEQ(arrayClassInitCount, 0, "ArrayValue should not be initialized");

        Object fieldValue = t.testFieldLoad();
        Asserts.assertNull(fieldValue, "Unexpected field value");
        Asserts.assertEQ(fieldClassInitCount, 0, "FieldValue should not be initialized");

        t.testFieldStore(null);
        Asserts.assertNull(fieldValue, "Unexpected field value");
        Asserts.assertEQ(fieldClassInitCount, 0, "FieldValue should not be initialized");

        Object arrayValue = testArrayLoad();
        Asserts.assertNull(arrayValue, "Unexpected array value");
        Asserts.assertEQ(arrayClassInitCount, 0, "ArrayValue should not be initialized");

        ArrayValue[] array = testArrayStore(null);
        Asserts.assertNull(array[0], "Unexpected array value");
        Asserts.assertEQ(arrayClassInitCount, 0, "ArrayValue should not be initialized");
    }
}

