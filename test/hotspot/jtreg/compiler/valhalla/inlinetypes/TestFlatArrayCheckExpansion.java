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

import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8391280
 * @summary Test macro expansion of merged flat-array checks with array and klass inputs
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.value
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 */
public class TestFlatArrayCheckExpansion {

    static int test1(Object[] array) {
        int res = 0;
        for (int i = 0; i < 4; i++) {
            // FlatArrayCheckNode with a Klass* input
            if (ValueClass.isFlatArray(array)) {
                res += 1;
            } else {
                res += 2;
            }
            // FlatArrayCheckNode with an oop input
            res += (Integer)array[i];
        }
        return res;
    }

    // Same as test1 but different order of checks
    static int test2(Object[] array) {
        int res = 0;
        for (int i = 0; i < 4; i++) {
            // FlatArrayCheckNode with an oop input
            res += (Integer)array[i];
            // FlatArrayCheckNode with a Klass* input
            if (ValueClass.isFlatArray(array)) {
                res += 1;
            } else {
                res += 2;
            }
        }
        return res;
    }

    // Same as test1 but with two different arrays
    static int test3(Object[] array1, Object[] array2) {
        int res = 0;
        for (int i = 0; i < 4; i++) {
            // FlatArrayCheckNode with a Klass* input
            if (ValueClass.isFlatArray(array1)) {
                res += 1;
            } else {
                res += 2;
            }
            // FlatArrayCheckNode with an oop input
            res += (Integer)array2[i];
        }
        return res;
    }

    // Same as test2 but with two different arrays
    static int test4(Object[] array1, Object[] array2) {
        int res = 0;
        for (int i = 0; i < 4; i++) {
            // FlatArrayCheckNode with an oop input
            res += (Integer)array2[i];
            // FlatArrayCheckNode with a Klass* input
            if (ValueClass.isFlatArray(array1)) {
                res += 1;
            } else {
                res += 2;
            }
        }
        return res;
    }

    public static void main(String[] args) {
        Object[] refArray = {1, 2, 3, 4};
        Integer[] flatArray = {1, 2, 3, 4};
        boolean isFlat = ValueClass.isFlatArray(flatArray);

        for (int i = 0; i < 50_000; i++) {
            Asserts.assertEQ(test1(refArray), 18);
            Asserts.assertEQ(test1(flatArray), isFlat ? 14 : 18);

            Asserts.assertEQ(test2(refArray), 18);
            Asserts.assertEQ(test2(flatArray), isFlat ? 14 : 18);

            Asserts.assertEQ(test3(refArray, refArray), 18);
            Asserts.assertEQ(test3(refArray, flatArray), 18);
            Asserts.assertEQ(test3(flatArray, refArray), isFlat ? 14 : 18);
            Asserts.assertEQ(test3(flatArray, flatArray), isFlat ? 14 : 18);

            Asserts.assertEQ(test4(refArray, refArray), 18);
            Asserts.assertEQ(test4(refArray, flatArray), 18);
            Asserts.assertEQ(test4(flatArray, refArray), isFlat ? 14 : 18);
            Asserts.assertEQ(test4(flatArray, flatArray), isFlat ? 14 : 18);
        }
    }
}

