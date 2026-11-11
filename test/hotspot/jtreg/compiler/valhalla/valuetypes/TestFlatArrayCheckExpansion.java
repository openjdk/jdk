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

package compiler.valhalla.valuetypes;

import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8391280 8393019
 * @summary Test macro expansion of merged flat-array checks with array and klass inputs
 * @key stress randomness
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.value
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:ConditionalMoveLimit=4
 *                   -XX:+StressIGVN -XX:StressSeed=4
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:ConditionalMoveLimit=4
 *                   -XX:+StressIGVN
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

    // Forcing two outputs to the FlatArrayCheck:
    // Naively, notFlat would be
    //     CMove(Bool(FlatArrayCheck, !=), false=0, true=1)
    // But CMove doesn't like having 0 on the false-case (with true-case being constant),
    // so it is tranformed into
    //     CMove(Bool(FlatArrayCheck, ==), false=1, true=0)
    // On the other hand, (notFlat ? 1 : 2)
    //     CMove(Bool(FlatArrayCheck, !=), false=2, true=1)
    // So we end up with a FlatArrayCheck with two Bool output:
    // one with condition "==", and one "!=". This test makes sure we support FlatArrayCheck
    // with multiple outputs.
    static int test5(Object[] array, int i) {
        boolean notFlat = !ValueClass.isFlatArray(array);
        Object value = array[i];
        return (notFlat ? 1 : 2) + (value == null ? 4 : 8);
    }

    // Pretty much the same as above, but in the situation we want to move the flatness check
    // above the loop.
    static int test6(Object[] array) {
        int res = 0;
        for (int i = 0; i < 4; i++) {
            boolean notFlat = !ValueClass.isFlatArray(array);
            Object value = array[i];
            res += (notFlat ? 1 : 2) + (value == null ? 4 : 8);
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

            Asserts.assertEQ(test5(refArray, 0), 9);
            Asserts.assertEQ(test5(flatArray, 0), isFlat ? 10 : 9);

            Asserts.assertEQ(test6(refArray), 36);
            Asserts.assertEQ(test6(flatArray), isFlat ? 40 : 36);
        }
    }
}
