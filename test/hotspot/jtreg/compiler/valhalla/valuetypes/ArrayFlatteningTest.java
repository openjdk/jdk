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

/*
 * @test id=normal
 * @bug 8353851
 * @summary Tests that control flow in LIR is recognized by the register allocator
 * @library /test/lib /
 * @enablePreview
 * @run main ${test.main.class}
 */

/*
 * @test id=stress
 * @bug 8353851
 * @summary Tests that control flow in LIR is recognized by the register allocator
 * @library /test/lib /
 * @requires vm.compiler1.enabled
 * @requires vm.gc.Parallel
 * @comment StressC1Spilling will fail with G1 and Shenandoah due to control flow in barriers
 * @enablePreview
 * @run main/othervm -XX:+UseParallelGC -XX:+IgnoreUnrecognizedVMOptions -XX:+StressC1Spilling
 *                   -XX:CompileCommand=compileonly,${test.main.class}::mySort
 *                   ${test.main.class}
 */

package compiler.valhalla.valuetypes;

import java.util.*;

import jdk.test.lib.Utils;

public class ArrayFlatteningTest {
    // Simplified binarySort pattern
    static <T> void mySort(T[] a, int lo, int hi, Comparator<? super T> c) {
        for (int start = lo + 1; start < hi; start++) {
            T pivot = a[start];
            int left = lo;
            int right = start;
            while (left < right) {
                int mid = (left + right) >>> 1;
                if (c.compare(pivot, a[mid]) < 0)
                    right = mid;
                else
                    left = mid + 1;
            }
            int n = start - left;
            switch (n) {
                case 2:  a[left + 2] = a[left + 1];
                case 1:  a[left + 1] = a[left];
                         break;
                default: System.arraycopy(a, left, a, left + 1, n);
            }
            a[left] = pivot;
        }
    }

    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        Comparator<Integer> cmp = new Comparator<Integer>() {
            public int compare(Integer o1, Integer o2) {
                return o1.compareTo(o2);
            }
        };
        for (int i = 0; i < 10000; i++) {
            Integer[] arr = new Integer[32];
            for (int j = 0; j < arr.length; j++) {
                arr[j] = Integer.valueOf(RANDOM.nextInt());
            }
            mySort(arr, arr.length / 3, arr.length / 2, cmp);
        }
    }
}
