/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @bug 8291599
 * @summary Assertion in PhaseIdealLoop::skeleton_predicate_has_opaque after JDK-8289127
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -XX:-TieredCompilation -XX:-BackgroundCompilation -XX:-UseOnStackReplacement -XX:LoopMaxUnroll=0 TestPhiInSkeletonPredicateExpression
 */

public class TestPhiInSkeletonPredicateExpression {
    private static int[] array1;
    private static int[] array2;
    private static int off;
    private static volatile int barrier;

    public static void main(String[] args) {
        int[] array = new int[2000];
        array1 = array;
        array2 = array;
        for (int i = 0; i < 20_000; i++) {
            test1(1000, false);
            test1(1000, true);
            test2(1000, false);
            test2(1000, true);
        }
    }

    private static int test1(int stop, boolean flag) {
        int v = 0;

        for (int j = 1; j < 10; j *= 2) {
            int[] array;
            if (flag) {
                if (array1 == null) {

                }
                array = array1;
                barrier = 0x42;
            } else {
                if (array2 == null) {

                }
                array = array2;
                barrier = 0x42;
            }

            int i = 0;
            do {
                synchronized (new Object()) {
                }
                v += array[i + off];
                i++;
            } while (i < stop);
        }
        return v;
    }

    private static int test2(int stop, boolean flag) {
        int v = 0;

        int[] array;
        if (flag) {
            if (array1 == null) {

            }
            array = array1;
            barrier = 0x42;
        } else {
            if (array2 == null) {

            }
            array = array2;
            barrier = 0x42;
        }

        int i = 0;
        do {
            synchronized (new Object()) {
            }
            v += array[i + off];
            i++;
        } while (i < stop);
        return v;
    }
}
