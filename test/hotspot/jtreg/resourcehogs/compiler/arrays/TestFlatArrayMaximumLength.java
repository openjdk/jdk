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

package compiler.arrays;

/**
 * @test
 * @bug 8391178
 * @summary Test correctness of C2's type for the length of large flat arrays
 * @enablePreview
 * @requires vm.compiler2.enabled & os.maxMemory > 4G
 * @run main/othervm -Xmx4g -Xcomp -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+UseArrayFlattening -XX:+UseNullableAtomicValueFlattening
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 */
public class TestFlatArrayMaximumLength {

    static value class EmptyValue { }
    static final int[] smallArray = new int[2];
    static EmptyValue[] largeArray;

    static int test1() {
        // C2 must preserve the normal return from this legal allocation
        EmptyValue[] array = new EmptyValue[Integer.MAX_VALUE];
        largeArray = array;
        return array.length;
    }

    static void test2(int length) {
        // The type of array.length is set to [0..Integer.MAX_VALUE-2]
        // while it should be [0..Integer.MAX_VALUE] for a flat array.
        EmptyValue[] array = new EmptyValue[length];
        // The range of index is therefore [0..1] but should be [0..2].
        int index = (array.length + 2) >>> 30;
        // The range of index is still [0..1] but should be [0..4] now.
        index = index * index;
        // The range check will be removed here because [0..1] is always
        // in range but that's incorrect because [0..4] is not in range.
        // With length == Integer.MAX_VALUE, index is 4 and we fail to
        // throw an exception and write beyond the end of the array.
        smallArray[index] = 42;
    }

    public static void main(String[] args) {
        // Make sure that class is loaded
        EmptyValue tmp = new EmptyValue();

        if (test1() != Integer.MAX_VALUE) {
            throw new RuntimeException("Incorrect array length");
        }
        largeArray = null;
        System.gc();

        for (int i = 0; i < 30_000; i++) {
            test2(1);
        }

        try {
            test2(Integer.MAX_VALUE);
            throw new RuntimeException("No IndexOutOfBoundsException thrown!");
        } catch (IndexOutOfBoundsException expected) {
            // Expected
        }
    }
}

