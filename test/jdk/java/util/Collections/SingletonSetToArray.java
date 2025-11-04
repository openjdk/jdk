/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug     8371164
 * @summary Test Collections.SingletonSet toArray() optimizations
 * @author  John Engebretson
 */

import java.util.Collections;
import java.util.Set;

public class SingletonSetToArray {
    public static void main(String[] args) {
        testToArray();
        testToArrayWithExactSize();
        testToArrayWithLargerArray();
        testToArrayWithSmallerArray();
        testToArrayWithNullElement();
    }

    private static void testToArray() {
        Set<String> set = Collections.singleton("test");
        Object[] array = set.toArray();

        if (array.length != 1 || !array[0].equals("test")) {
            throw new RuntimeException("toArray() failed, expected [test], got: " + java.util.Arrays.toString(array));
        }
    }

    private static void testToArrayWithExactSize() {
        Set<String> set = Collections.singleton("test");
        String[] stringArray = new String[1];
        String[] result = set.toArray(stringArray);

        if (result != stringArray || result.length != 1 || !result[0].equals("test")) {
            throw new RuntimeException("toArray(T[]) with exact size failed, expected same array with [test], got: " + java.util.Arrays.toString(result));
        }
    }

    private static void testToArrayWithLargerArray() {
        Set<String> set = Collections.singleton("test");
        String[] largerArray = new String[5];
        String[] result = set.toArray(largerArray);

        if (result != largerArray || !result[0].equals("test") || result[1] != null) {
            throw new RuntimeException("toArray(T[]) with larger array failed, expected [test, null, ...], got: " + java.util.Arrays.toString(result));
        }

        // Verify remaining elements are unchanged (should remain null)
        for (int i = 2; i < largerArray.length; i++) {
            if (largerArray[i] != null) {
                throw new RuntimeException("Array element " + i + " should remain null, got: " + largerArray[i]);
            }
        }
    }

    private static void testToArrayWithSmallerArray() {
        Set<String> set = Collections.singleton("test");
        String[] smallerArray = new String[0];
        String[] result = set.toArray(smallerArray);

        if (result == smallerArray || result.length != 1 || !result[0].equals("test")) {
            throw new RuntimeException("toArray(T[]) with smaller array failed, expected new array [test], got: " + java.util.Arrays.toString(result));
        }

        // Verify correct array type was created
        if (!result.getClass().getComponentType().equals(String.class)) {
            throw new RuntimeException("Wrong array type created, expected String[], got: " + result.getClass().getComponentType());
        }
    }

    private static void testToArrayWithNullElement() {
        Set<String> set = Collections.singleton(null);
        Object[] array = set.toArray();

        if (array.length != 1 || array[0] != null) {
            throw new RuntimeException("toArray() with null element failed, expected [null], got: " + java.util.Arrays.toString(array));
        }

        String[] stringArray = new String[1];
        String[] result = set.toArray(stringArray);

        if (result != stringArray || result.length != 1 || result[0] != null) {
            throw new RuntimeException("toArray(T[]) with null element failed, expected [null], got: " + java.util.Arrays.toString(result));
        }
    }
}
