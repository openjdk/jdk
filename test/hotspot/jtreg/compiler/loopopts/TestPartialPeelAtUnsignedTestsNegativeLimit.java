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

/*
 * @test id=Xbatch
 * @bug 8332920
 * @summary Tests partial peeling at unsigned tests with limit being negative in exit tests "i >u limit".
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileOnly=*TestPartialPeel*::original*,*TestPartialPeel*::test*
 *                   compiler.loopopts.TestPartialPeelAtUnsignedTestsNegativeLimit
 */

/*
 * @test id=Xcomp-run-inline
 * @bug 8332920
 * @summary Tests partial peeling at unsigned tests with limit being negative in exit tests "i >u limit".
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileOnly=*TestPartialPeel*::original*,*TestPartialPeel*::run*,*TestPartialPeel*::test*
 *                   -XX:CompileCommand=inline,*TestPartialPeelAtUnsignedTestsNegativeLimit::test*
 *                   -XX:CompileCommand=dontinline,*TestPartialPeelAtUnsignedTestsNegativeLimit::check
 *                   compiler.loopopts.TestPartialPeelAtUnsignedTestsNegativeLimit
 */

/*
 * @test id=Xcomp-compile-test
 * @bug 8332920
 * @summary Tests partial peeling at unsigned tests with limit being negative in exit tests "i >u limit".
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileOnly=*TestPartialPeel*::original*,*TestPartialPeel*::test*
 *                   compiler.loopopts.TestPartialPeelAtUnsignedTestsNegativeLimit
 */

/*
 * @test id=vanilla
 * @bug 8332920
 * @requires vm.flavor == "server" & (vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4)
 * @summary Tests partial peeling at unsigned tests with limit being negative in exit tests "i >u limit".
 *          Only run this test with C2 since it is time-consuming and only tests a C2 issue.
 * @run main compiler.loopopts.TestPartialPeelAtUnsignedTestsNegativeLimit
 */

package compiler.loopopts;

import java.util.Random;

import static java.lang.Integer.*;

public class TestPartialPeelAtUnsignedTestsNegativeLimit {
    static int iFld = 10000;
    static int iterations = 0;
    static int iFld2;
    static boolean flag;
    final static Random RANDOM = new Random();

    public static void main(String[] args) {
        compareUnsigned(3, 3); // Load Integer class for -Xcomp
        for (int i = 0; i < 2; i++) {
            if (!originalTest()) {
                throw new RuntimeException("originalTest() failed");
            }
        }

        for (int i = 0; i < 2000; i++) {
            // For profiling
            iFld = -1;
            originalTestVariation1();

            // Actual run
            iFld = MAX_VALUE - 100_000;
            if (!originalTestVariation1()) {
                throw new RuntimeException("originalTestVariation1() failed");
            }
        }

        for (int i = 0; i < 2000; ++i) {
            // For profiling
            iFld = MAX_VALUE;
            originalTestVariation2();

            // Actual run
            iFld = MIN_VALUE + 100000;
            if (!originalTestVariation2()) {
                throw new RuntimeException("originalTestVariation2() failed");
            }
        }

        runWhileLTIncr();
        runWhileLTDecr();
    }

    // Originally reported simplified regression test with 2 variations (see below).
    public static boolean originalTest() {
        for (int i = MAX_VALUE - 50_000; compareUnsigned(i, -1) < 0; i++) {
            if (compareUnsigned(MIN_VALUE, i) < 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean originalTestVariation1() {
        int a = 0;
        for (int i = iFld; compareUnsigned(i, -1) < 0; ++i) { // i <u -1

            if (i >= Integer.MIN_VALUE + 1 && i <= 100) { // Transformed to unsigned test.
                return true;
            }
            a *= 23;
        }
        return false;
    }

    public static boolean originalTestVariation2() {
        int a = 0;
        for (int i = iFld; compareUnsigned(i, -1000) < 0; i--) { // i <u -1
            if (compareUnsigned(MAX_VALUE - 20, i) > 0) {
                return true;
            }
            a = i;
        }
        System.out.println(a);
        return false;
    }


    public static void testWhileLTIncr(int init, int limit) {
        int i = init;
        while (true) {
            // <Peeled Section>

            // Found as loop head in ciTypeFlow, but both paths inside loop -> head not cloned.
            // As a result, this head has the safepoint as backedge instead of the loop exit test
            // and we cannot create a counted loop (yet). We first need to partial peel.
            if (flag) {
            }

            iFld2++;

            // Loop exit test i >=u limit (i.e. "while (i <u limit)") to partial peel with.
            // insert_cmpi_loop_exit() changes this exit condition into a signed and an unsigned test:
            //   i >= limit && i >=u limit
            // where the signed condition can be used as proper loop exit condition for a counted loop
            // (we cannot use an unsigned counted loop exit condition).
            //
            // After Partial Peeling, we have:
            //   if (i >= limit) goto Exit
            // Loop:
            //   if (i >=u limit) goto Exit
            //   ...
            //   i++;
            //   if (i >= limit) goto Exit
            //   goto Loop
            // Exit:
            //   ...
            //
            // If init = MAX_VALUE and limit = MIN_VALUE:
            //   i >= limit
            //   MAX_VALUE >= MIN_VALUE
            // which is true where
            //   i >=u limit
            //   MAX_VALUE >=u MIN_VALUE
            //   MAX_VALUE >=u (uint)(MAX_INT + 1)
            // is false and we wrongly never enter the loop even though we should have.
            // This results in a wrong execution.
            if (compareUnsigned(i, limit) >= 0) {
                return;
            }
            // <-- Partial Peeling CUT -->
            // Safepoint
            // <Unpeeled Section>
            iterations++;
            i++;
        }
    }

    // Same as testWhileLTIncr() but with decrement instead.
    public static void testWhileLTDecr(int init, int limit) {
        int i = init;
        while (true) {
            if (flag) {
            }

            // Loop exit test.
            if (compareUnsigned(i, limit) >= 0) { // While (i <u limit)
                return;
            }

            iterations++;
            i--;
        }
    }

    public static void runWhileLTIncr() {
        // Currently works:
        testWhileLTIncr(MAX_VALUE, -1);
        check(MIN_VALUE); // MAX_VALUE + 1 iterations
        testWhileLTIncr(-1, 1);
        check(0);
        testWhileLTIncr(0, 0);
        check(0);
        checkIncrWithRandom(0, 0); // Sanity check this method.
        flag = !flag; // Change profiling
        testWhileLTIncr(MAX_VALUE - 2000, MAX_VALUE);
        check(2000);
        testWhileLTIncr(MAX_VALUE - 1990, MAX_VALUE);
        check(1990);
        testWhileLTIncr(MAX_VALUE - 1, MAX_VALUE);
        check(1);
        testWhileLTIncr(MIN_VALUE, MIN_VALUE + 2000);
        check(2000);
        testWhileLTIncr(MIN_VALUE, MIN_VALUE + 1990);
        check(1990);
        testWhileLTIncr(MIN_VALUE, MIN_VALUE + 1);
        check(1);

        flag = !flag;
        // Overflow currently does not work with negative limit and is fixed with patch:
        testWhileLTIncr(MAX_VALUE, MIN_VALUE);
        check(1);
        testWhileLTIncr(MAX_VALUE - 2000, MIN_VALUE);
        check(2001);
        testWhileLTIncr(MAX_VALUE, MIN_VALUE + 2000);
        check(2001);
        testWhileLTIncr(MAX_VALUE - 2000, MIN_VALUE + 2000);
        check(4001);

        // Random values
        int init = RANDOM.nextInt(0, MAX_VALUE);
        int limit = RANDOM.nextInt(MIN_VALUE, 0);
        testWhileLTIncr(init, limit);
        checkIncrWithRandom(init, limit);
    }

    public static void runWhileLTDecr() {
        // Currently works:
        testWhileLTDecr(1, -1);
        check(2);
        testWhileLTDecr(-1, 1);
        check(0);
        testWhileLTDecr(0, 0);
        check(0);
        checkDecrWithRandom(0, 0); // Sanity check this method.
        flag = !flag;
        testWhileLTDecr(MAX_VALUE, MIN_VALUE);
        check(MIN_VALUE); // MAX_VALUE + 1 iterations
        testWhileLTDecr(MAX_VALUE, -1);
        check(MIN_VALUE); // MAX_VALUE + 1 iterations
        testWhileLTDecr(MAX_VALUE, MIN_VALUE);
        check(MIN_VALUE); // MAX_VALUE + 1 iterations
        testWhileLTDecr(MIN_VALUE, 0);
        check(0);
        testWhileLTDecr(MIN_VALUE, 1);
        check(0);
        flag = !flag;

        // Underflow currently does not work with negative limit and is fixed with patch:
        testWhileLTDecr(MIN_VALUE, -1);
        check(MIN_VALUE + 1); // MAX_VALUE + 2 iterations
        testWhileLTDecr(MIN_VALUE, -2000);
        check(MIN_VALUE + 1); // MAX_VALUE + 2 iterations
        testWhileLTDecr(MIN_VALUE, MIN_VALUE + 1);
        check(MIN_VALUE + 1); // MAX_VALUE + 2 iterations
        testWhileLTDecr(MIN_VALUE + 2000, -1);
        check(MIN_VALUE + 2001); // MAX_VALUE + 2002 iterations
        testWhileLTDecr(MIN_VALUE + 2000, -2000);
        check(MIN_VALUE + 2001); // MAX_VALUE + 2002 iterations
        testWhileLTDecr(MIN_VALUE + 2000, MIN_VALUE + 2001);
        check(MIN_VALUE + 2001); // MAX_VALUE + 2002 iterations

        // Random values
        int r1 = RANDOM.nextInt(MIN_VALUE, 0);
        int r2 = RANDOM.nextInt(MIN_VALUE, 0);
        int init = Math.min(r1, r2);
        int limit = Math.max(r1, r2);
        testWhileLTDecr(init, limit);
        checkDecrWithRandom(init, limit);
    }

    static void check(int expectedIterations) {
        if (expectedIterations != iterations) {
            throw new RuntimeException("Expected " + expectedIterations + " iterations but only got " + iterations);
        }
        iterations = 0; // Reset
    }

    static void checkIncrWithRandom(long init, long limit) {
        long expectedIterations = ((long)(MAX_VALUE) - init) + (limit - (long)MIN_VALUE) + 1;
        if ((int)expectedIterations != iterations) {
            String error = "Expected %d iterations but only got %d, init: %d, limit: %d"
                            .formatted(expectedIterations, iterations, init, limit);
            throw new RuntimeException(error);
        }
        iterations = 0; // Reset
    }

    static void checkDecrWithRandom(long init, long limit) {
        long expectedIterations = init + MIN_VALUE + MAX_VALUE + 2;
        if (init == limit) {
            expectedIterations = 0;
        }
        if ((int)expectedIterations != iterations) {
            String error = "Expected %d iterations but only got %d, init: %d, limit: %d"
                    .formatted(expectedIterations, iterations, init, limit);
            throw new RuntimeException(error);
        }
        iterations = 0; // Reset
    }
}
