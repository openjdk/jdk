/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8318306
 * @run main/othervm/timeout=200 -XX:+IgnoreUnrecognizedVMOptions -Xcomp -ea -esa -XX:CompileThreshold=100 -XX:+UnlockExperimentalVMOptions -server -XX:-TieredCompilation -XX:+DeoptimizeALot SortingDeoptimizationTest 1e-2 100 50
 * @summary Exercise Arrays.parallelSort when -XX:+DeoptimizeALot is enabled
 *
 */

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;

public class SortingDeoptimizationTest {

    private static final PrintStream err = System.err;
    private static final PrintStream out = System.out;

    public static void main(String[] args) {
        int MAX = 2147483647; // 2^32 - 1
        float fraction = Float.parseFloat(args[0]);
        int size = (int) (fraction * MAX); // size is a fraction of the MAX size
        int iters = Integer.parseInt(args[1]); // number of iterations
        int max = args.length > 2 ? Integer.parseInt(args[2]) : -1 ; // max value for the array elements
        long seed = 0xC0FFEE;
        Random rand = new Random(seed);

        for (int i = 0; i < iters; i++) {
            boolean isSorted = runSort(size, max, rand);
            out.println("Iteration " + i + ": is sorted? -> "+ isSorted);
            if (!isSorted) fail("Array is not correctly sorted.");
        }
    }

    private static void fail(String message) {
        err.format("\n*** TEST FAILED ***\n\n%s\n\n", message);
        throw new RuntimeException("Test failed");
    }

    private static boolean runSort(int size, int max, Random rand) {
        int[] a = new int[size];
        for (int i = 0; i < a.length; i++) a[i] =  max > 0 ? rand.nextInt(max) : rand.nextInt();
        // call parallel sort
        Arrays.parallelSort(a);
        // check if sorted
        boolean isSorted = true;
        for (int i = 0; i < (a.length -1); i++) isSorted = isSorted && (a[i] <= a[i+1]);
        return isSorted;
    }
}
