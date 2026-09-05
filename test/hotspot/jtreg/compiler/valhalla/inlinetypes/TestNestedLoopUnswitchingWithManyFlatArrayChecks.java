/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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


/**
 * @test
 * @bug 8384364
 * @summary [lworld] C2: assert(cloned_flat_array_check->req() == 3) failed: unexpected number of inputs for FlatArrayCheck
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:-BackgroundCompilation -XX:+UnlockDiagnosticVMOptions -XX:-UseArrayLoadStoreProfile ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import jdk.internal.value.ValueClass;

public class TestNestedLoopUnswitchingWithManyFlatArrayChecks {
    public static void main(String[] args) {
        MyValue1[] objectArray = (MyValue1[])ValueClass.newReferenceArray(MyValue1.class, 1);
        MyValue1[] valueArray = new MyValue1[1];
        for (int i = 0; i < 20_000; i++) {
            inlined1(objectArray, objectArray, 10);
            test1(objectArray, objectArray);
            test1(valueArray, valueArray);
            inlined2(objectArray, objectArray, 10);
            test2(objectArray, objectArray);
            test2(valueArray, valueArray);
        }
    }

    static Object field1;
    static Object field2;

    static void test1(MyValue1[] array1, MyValue1[] array2) {
        int stop;
        for (stop = 0; stop < 2; stop++) {
        }
        field1 = null;
        field2 = null;

        for (int j = 0; j < 10; j++) {
            inlined1(array1, array2, stop);
        }
    }

    static void inlined1(Object[] array1, Object[] array2, int stop) {
        for (int i = 0; i < stop; i++) {
            field1 = array1[0];
            field2 = array2[0];
        }
    }

    static void test2(MyValue1[] array1, MyValue1[] array2) {
        int stop;
        for (stop = 0; stop < 2; stop++) {
        }
        field1 = null;
        field2 = null;

        for (int j = 0; j < 10; j++) {
            inlined2(array1, array2, stop);
            inlined2(array1, array2, stop);
        }
    }

    static void inlined2(Object[] array1, Object[] array2, int stop) {
        for (int i = 0; i < stop; i++) {
            field1 = array1[0];
            field2 = array2[0];
        }
    }

    static value class MyValue1 {
        byte byteField;
        MyValue1(byte byteField) {
            this.byteField = byteField;
        }
     }
}
