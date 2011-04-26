/*
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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


/* @test
 * @summary Test MergeSort
 *
 * @library ../../../src/share/sample/forkjoin/mergesort
 * @build MergeSortTest MergeDemo MergeSort
 * @run main MergeSortTest
 */

import java.util.Arrays;
import java.util.Random;

public class MergeSortTest {
    private Random random;
    private MergeSort target;

    public MergeSortTest(Random random, MergeSort target) {
        this.random = random;
        this.target = target;
    }

    public static void main(String[] args) {
        MergeSortTest test = new MergeSortTest(new Random(), new MergeSort(Runtime.getRuntime().availableProcessors() * 4));
        test.run();
    }

    private int[] generateArray(int elements) {
        int[] array = new int[elements];
        for (int i = 0; i < array.length; ++i) {
            array[i] = random.nextInt(10);
        }
        return array;
    }

    private void run() {
        testSort();
        testSortSingle();
        testSortEmpty();
        testLong();
    }

    public void testLong() {
        for (int i = 0; i < 1000; ++i) {
            int elements = 1 + i * 100;

            int[] array = generateArray(elements);
            int[] copy = Arrays.copyOf(array, array.length);
            Arrays.sort(copy);
            target.sort(array);
            assertEqual(copy, array);
        }
   }

    private void testSortEmpty() {
        int[] array = { };
        target.sort(array);
        assertEqual(new int[] { }, array);
    }

    private void testSortSingle() {
        int[] array = { 1 };
        target.sort(array);
        assertEqual(new int[] { 1 }, array);
    }

    private void testSort() {
        int[] array = { 7, 3, 9, 0, -6, 12, 54, 3, -6, 88, 1412};
        target.sort(array);
        assertEqual(new int[] { -6, -6, 0, 3, 3, 7, 9, 12, 54, 88, 1412 }, array);
    }

    private void assertEqual(int[] expected, int[] array) {
        if (!Arrays.equals(expected, array)) {
            throw new RuntimeException("Invalid sorted array!");
        }
    }


}
