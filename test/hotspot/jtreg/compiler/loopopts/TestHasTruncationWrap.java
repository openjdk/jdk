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
 * @test id=vanilla
 * @bug 8385855
 * @summary Test CountedLoopConverter::has_truncation_wrap logic that checks if
 *          a truncated iv (e.g. byte or char iv) is still a valid counted loop.
 * @library /test/lib /
 * @run main ${test.main.class}
 */

/*
 * @test id=Xcomp
 * @bug 8385855
 * @library /test/lib /
 * @run main ${test.main.class} -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}::test*
 */

package compiler.loopopts;

import compiler.lib.ir_framework.*;

/**
 * TODO: descr
 */
public class TestHasTruncationWrap {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags(args);
        framework.start();
    }

    // ------------------------- Failing cases for JDK-8385855 ------------------------------

    // Test shape first reported in JDK-8385855, led to assert in JDK27:
    //   assert(cmp->Opcode() == Op_CmpI) failed: signed comparison required
    public static int   test0_start = 0;
    public static int   test0_stop  = 100;
    public static int[] test0_array = new int[100];

    @Test
    public static void test0() {
        int   start = test0_start;
        int   stop  = test0_stop;
        int[] array = test0_array;

        stop = (stop << 16) >> 16;
        int v = array[start]; // dominating CmpU detected by filtered_int_type
        for (int i = start; i < stop;) {
            i++;
            i = (i << 16) >> 16; // iv truncation
        }
    }

    // A second reproducer from JDK-8385855, leads to wrong result since JDK18 (JDK-8276162).
    // We make use of the CmpU via Integer.compareUnsigned, introduced by JDK-8276162.
    public static int test1_gold0 = test1(-2);
    public static int test1_gold1 = test1(2);

    @Run(test = "test1")
    private static void run1() {
        int val0 = test1(-2);
        int val1 = test1( 2);
        if (val0 != test1_gold0) { throw new RuntimeException("wrong value test( 2): " + test1_gold0 + " vs " + val0); }
        if (val1 != test1_gold1) { throw new RuntimeException("wrong value test(-2): " + test1_gold1 + " vs " + val1); }
    }

    @Test
    private static int test1(int start) {
        // CmpU Condition: start <u 2
        if (Integer.compareUnsigned(start, 2) < 0) {
            return 0;
        }
        // Now, correct:        start >=u 2
        // But filtered_int_type mistakes it as a CmpI.
        // Bad CmpU assumption: start >=  2

        int i = start;
        while (i < 3) {
            // While condition: i <= 2

            // char-truncation of iv: has_truncation_wrap
            // We try to see if the char-truncation can be removed.
            //
            // Computing loop entry type:
            //   While condition: i <= 2
            //   Bad assumption from CmpU: start >= 2
            //   -> entry type i = 2
            //
            // Together with the backedge type, we get the complete phi type:
            //   i in [1..2]
            //
            // The truncation below would be a no-op for input ranges [0 .. 32767].
            // Since [1..2] is a subrange: remove truncation!
            //
            // But: the correct CmpU assumption would only be:
            //   start >=u 2
            // And that allows almost all values (except 0 and 1), in particular
            // it allows the whole negative int range.
            // And the while condition also allows all negative ints.
            // And for negative ints, the truncation is NOT a no-op.
            i = (i + 1) & 0x7fff;

            // Continuing after the backedge would mean:
            //   i >= 1
            // Together with while condition:
            //   i <= 2
            // We get a backedge type:
            //   i in [1..2]
            if (i < 1) {
                break;
            }
        }
        return i;
    }

    // A third reproducer from JDK-8385855, leads to wrong result since 6u.
    // We make use of the CmpU in the RangeCheck of an array access.
    // To flip the condition, we just use a try/catch.
    public static final int[] test2_A = new int[2];
    public static int test2_gold0 = test2(-2);
    public static int test2_gold1 = test2(2);

    @Run(test = "test2")
    private static void run2() {
        int val0 = test2(-2);
        int val1 = test2( 2);
        if (val0 != test2_gold0) { throw new RuntimeException("wrong value test( 2): " + test2_gold0 + " vs " + val0); }
        if (val1 != test2_gold1) { throw new RuntimeException("wrong value test(-2): " + test2_gold1 + " vs " + val1); }
    }

    @Test
    static int test2(int start) {
        try {
            // CmpU Condition: start <u A.length = 2
            return test2_A[start];
        } catch (ArrayIndexOutOfBoundsException ex) {
            // From CmpU Condition: start >=u A.length = 2
            int i = start;
            while (i < 3) {
                // Truncating induction-variable update.
                i = (i + 1) & 0x7fff;
                if (i < 1) {
                    break;
                }
            }
            return i;
        }
    }

    // ---- More general tests, Checking that truncated iv loops become CountedLoops ---------

    @DontInline
    public static int dontinline(int i) {
        return i + 1;
    }

    public static int lo = 11;
    public static int hi = 33;

    // testIR0: just a regular int loop
    public static int testIR0_gold = testIR0();

    @Run(test = "testIR0")
    private static void runIR0() {
        int val = testIR0();
        if (val != testIR0_gold) { throw new RuntimeException("wrong value: " + testIR0_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR0() {
        int init  = lo;
        int limit = hi;
        int sum = 0;
        for (int i = init; i < limit; i++) {
            sum = dontinline(sum);
        }
        return sum;
    }

    // testIR1: short loop, but values are trivially in short range.
    public static int testIR1_gold = testIR1();

    @Run(test = "testIR1")
    private static void runIR1() {
        int val = testIR1();
        if (val != testIR1_gold) { throw new RuntimeException("wrong value: " + testIR1_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR1() {
        short init  = (short)lo;
        short limit = (short)hi;
        int sum = 0;
        for (short i = init; i < limit; i++) {
            sum = dontinline(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIR2: short loop, ranges proved in short range via CmpI before loop.
    public static int testIR2_gold = testIR2();

    @Run(test = "testIR2")
    private static void runIR2() {
        int val = testIR2();
        if (val != testIR2_gold) { throw new RuntimeException("wrong value: " + testIR2_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR2() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        int sum = 0;
        for (int i = init; i < limit; i = (short)(i+1)) {
            sum = dontinline(sum); // work to keep loop alive
            // The backedge value of i is also far
            // enough from short boundaries, because of
            // the loop exit check:
            //   i < limit <= 100
        }
        return sum;
    }

    // testIR3: short loop, and range in short range via CmpI before loop (for loop limit).
    public static int testIR3_gold = testIR3();

    @Run(test = "testIR3")
    private static void runIR3() {
        int val = testIR3();
        if (val != testIR3_gold) { throw new RuntimeException("wrong value: " + testIR3_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR3() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        int sum = 0;
        // While there is no explicit CmpI before the loop, we
        // actually have "i < limit" in the for loop check, which
        // is also checked before entering the loop.
        // So also here, we have:
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        for (int i = init; i < limit; i = (short)(i+1)) {
            sum = dontinline(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIR3: short loop, with a CmpI, but the limit ranges are bad.
    public static int testIR4_gold = testIR4();

    @Run(test = "testIR4")
    private static void runIR4() {
        int val = testIR4();
        if (val != testIR4_gold) { throw new RuntimeException("wrong value: " + testIR4_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIR4() {
        int init  = Math.max(lo, 0);       // init  in [0..max_int]
        int limit = Math.min(hi, 100_000); // limit in [min_int..100_000]
        int sum = 0;
        // Now, the check is not good enough:
        // -> init < limit <= 100_000
        // -> filtered_int_type return [min_int..99_999]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99_999], which is NOT in short range.
        for (int i = init; i < limit; i = (short)(i+1)) {
            sum = dontinline(sum); // work to keep loop alive
            // Also: the backedge range is not good because
            // the exit check is not strong enough for short:
            //   i < limit <= 100_000
        }
        return sum;
    }

    // TODO: ensure coverage
    // - char, byte and short truncation
    // - check for IRNode.COUNTED_LOOP
    // - dontinline call to prevent empty loop
    // - increment and decrement cases
    // - Cases with and without compare before loop: positive and negative tests

    // TODO: replace with real test!
    //@Test
    //@IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    //@IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    //@Arguments(values = {Argument.NUMBER_42})
    //public static void test_lohi_ltle(int i) {
    //    if (i < -100_000 || i > 100_000) {
    //        throw new RuntimeException();
    //    }
    //}
}
