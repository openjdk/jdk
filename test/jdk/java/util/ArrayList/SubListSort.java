/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;

/**
 * @test
 * @bug 8325679
 * @summary Optimize ArrayList subList sort
 */

public class SubListSort {
    private static final int LENGTH = 10000;
    private static final int ROUNDS = 5000;

    public static void main(String[] args) throws Exception {
        var r = new Random();
        var s = r.nextLong();
        System.out.println("Random seed is " + s);
        r.setSeed(s);

        var arr = new ArrayList<>(r.ints(LENGTH).boxed().toList());
        System.out.println("Sorting without a comparator");
        testSubListSort(arr, null, r);
        System.out.println("Sorting with a comparator");
        testSubListSort(arr, Comparator.reverseOrder(), r);
        System.out.println("Success!");
    }

    private static <T> void testSubListSort(ArrayList<T> arr, Comparator<T> c, Random r) {
        for (var i = 0; i < ROUNDS; i++) {
            var start = r.nextInt(LENGTH);
            var end = start + r.nextInt(LENGTH - start);
            var arr2 = cloneArrayList(arr);
            arr2.subList(start, end).sort(c);
            var arr3 = new ArrayList<>(arr.subList(start, end));
            arr3.sort(c);
            if (!arr2.subList(start, end).equals(arr3)) {
                throw new AssertionError("Failed sorting sublist no. " + i + " [" + start + ", " + end + ")\n" +
                                         arr2.subList(start, end) + "\n" + arr3);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ArrayList<T> cloneArrayList(ArrayList<T> a) {
        return (ArrayList<T>)a.clone();
    }
}
