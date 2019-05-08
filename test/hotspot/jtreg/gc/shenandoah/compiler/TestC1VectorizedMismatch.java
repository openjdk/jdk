/*
 * Copyright (c) 2016, 2018, Red Hat, Inc. All rights reserved.
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
 *
 */

/* @test TestC1VectorizedMismatch
 * @summary test C1 vectorized mismatch intrinsic
 * @key gc
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 * @run main/othervm -XX:TieredStopAtLevel=1 -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=aggressive TestC1VectorizedMismatch
 */

import java.util.Arrays;

public class TestC1VectorizedMismatch {

    private static final int NUM_RUNS = 10000;
    private static final int ARRAY_SIZE = 10000;
    private static int[] a;
    private static int[] b;

    public static void main(String[] args) {
        a = new int[ARRAY_SIZE];
        b = new int[ARRAY_SIZE];
        for (int i = 0; i < NUM_RUNS; i++) {
            test();
        }
    }

    private static void test() {
        int[] a1 = new int[ARRAY_SIZE];
        int[] b1 = new int[ARRAY_SIZE];
        fillArray(a);
        System.arraycopy(a, 0, b, 0, ARRAY_SIZE);
        if (!Arrays.equals(a, b)) {
            throw new RuntimeException("arrays not equal");
        }
    }

    private static void fillArray(int[] array) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            int val = (int) (Math.random() * Integer.MAX_VALUE);
            array[i] = val;
        }
    }
}
