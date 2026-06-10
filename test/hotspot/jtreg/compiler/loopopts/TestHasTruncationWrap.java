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
 * Tests for CountedLoopConverter::has_truncation_wrap, which deals with wrapped iv, for byte/char/short iv cases.
 * We have some regression tests for JDK-8385855, as well as some IR tests that ensure that we detect counted
 * loops in many cases, where we have to check that truncation does not lead to wrapping, which would mean
 * the iv would not be linear, but possibly overflow the byte/char/short ranges.
 *
 * Note: the optimization around CountedLoopConverter::has_truncation_wrap is a bit fragile, and depends on
 * the exact loop shape, and if peeling happens or not, etc. The goal of this test is not to prove that we
 * recognize all truncated cases where one could in theory prove there is no wrap/overflow, but simply to
 * list some examples of today's state, so we don't get further regressions in the future.
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
    public static int test1_gold0 = 32767; // test1(-2);
    public static int test1_gold1 = 3;     // test1(2);

    @Run(test = "test1")
    private static void run1() {
        int val0 = test1(-2);
        int val1 = test1( 2);
        if (val0 != test1_gold0) { throw new RuntimeException("wrong value test(-2): " + test1_gold0 + " vs " + val0); }
        if (val1 != test1_gold1) { throw new RuntimeException("wrong value test( 2): " + test1_gold1 + " vs " + val1); }
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
    public static int test2_gold0 = 32767; // test2(-2);
    public static int test2_gold1 = 3;     // test2(2);

    @Run(test = "test2")
    private static void run2() {
        int val0 = test2(-2);
        int val1 = test2( 2);
        if (val0 != test2_gold0) { throw new RuntimeException("wrong value test(-2): " + test2_gold0 + " vs " + val0); }
        if (val1 != test2_gold1) { throw new RuntimeException("wrong value test( 2): " + test2_gold1 + " vs " + val1); }
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

    // testIR0b: just a regular int loop, but with NEQ exit check.
    public static int testIR0b_gold = testIR0b();

    @Run(test = "testIR0b")
    private static void runIR0b() {
        int val = testIR0b();
        if (val != testIR0b_gold) { throw new RuntimeException("wrong value: " + testIR0b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR0b() {
        int init  = lo;
        int limit = hi;
        int sum = 0;
        for (int i = init; i != limit; i++) {
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

    // testIR1b: short loop, but values are trivially in short range. Decrement iv.
    public static int testIR1b_gold = testIR1b();

    @Run(test = "testIR1b")
    private static void runIR1b() {
        int val = testIR1b();
        if (val != testIR1b_gold) { throw new RuntimeException("wrong value: " + testIR1b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR1b() {
        short init  = (short)hi;
        short limit = (short)lo;
        int sum = 0;
        for (short i = init; i > limit; i--) {
            sum = dontinline(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIR1c: short loop, but values are trivially in short range. Incr by 2.
    public static int testIR1c_gold = testIR1c();

    @Run(test = "testIR1c")
    private static void runIR1c() {
        int val = testIR1c();
        if (val != testIR1c_gold) { throw new RuntimeException("wrong value: " + testIR1c_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR1c() {
        short init  = (short)lo;
        short limit = (short)hi;
        int sum = 0;
        for (short i = init; i < limit; i+=2) {
            sum = dontinline(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIR1d: short loop, but values are trivially in short range. Decrement iv by 2.
    public static int testIR1d_gold = testIR1d();

    @Run(test = "testIR1d")
    private static void runIR1d() {
        int val = testIR1d();
        if (val != testIR1d_gold) { throw new RuntimeException("wrong value: " + testIR1d_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR1d() {
        short init  = (short)hi;
        short limit = (short)lo;
        int sum = 0;
        for (short i = init; i > limit; i-=2) {
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

    // testIR2b: short loop, ranges proved in short range via CmpI before loop.
    // Compared to testIR2, the check in the loop is an NEQ.
    public static int testIR2b_gold = testIR2b();

    @Run(test = "testIR2b")
    private static void runIR2b() {
        int val = testIR2b();
        if (val != testIR2b_gold) { throw new RuntimeException("wrong value: " + testIR2b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR2b() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        int sum = 0;
        for (int i = init; i != limit; i = (short)(i+1)) {
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

    // testIR3b: short loop, fails to be recognized as CountedLoop.
    // Compared to testIR3, the check in the loop is an NEQ.
    public static int testIR3b_gold = testIR3b();

    @Run(test = "testIR3b")
    private static void runIR3b() {
        int val = testIR3b();
        if (val != testIR3b_gold) { throw new RuntimeException("wrong value: " + testIR3b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIR3b() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        int sum = 0;
	// No useful CmpI before the loop.
	// And the CmpI of the for limit is NEQ, so not useful either.
        for (int i = init; i != limit; i = (short)(i+1)) {
            sum = dontinline(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIR4: short loop, with a CmpI, but the limit ranges are bad.
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

    // testIR5: short do-while-loop, and range in short range via CmpI before loop (for loop limit).
    public static int testIR5_gold = testIR5();

    @Run(test = "testIR5")
    private static void runIR5() {
        int val = testIR5();
        if (val != testIR5_gold) { throw new RuntimeException("wrong value: " + testIR5_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR5() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        int sum = 0;
        int i = init;
        do {
            sum = dontinline(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i < limit); // exit check at the end.
        return sum;
    }

    // testIR5b: short do-while-loop, but the backedge check with NEQ is not strong enough to prevent wrapping.
    // Compared to testIR5, the check in the loop is an NEQ.
    public static int testIR5b_gold = testIR5b();

    @Run(test = "testIR5b")
    private static void runIR5b() {
        int val = testIR5b();
        if (val != testIR5b_gold) { throw new RuntimeException("wrong value: " + testIR5b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIR5b() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        int sum = 0;
        int i = init;
        do {
            sum = dontinline(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i != limit); // exit check at the end, but with NEQ.
        return sum;
    }

    // testIR5c: short do-while-loop.
    // While the code shape looks very close to testIR2b, it does not behave the same.
    // The while loop below is peeled once. The additional "exit check" is eliminated,
    // because redundant after "init >= limit" check.
    // From peeling, the new initial value is a truncated short value, and not init, so
    // the "init >= limit" check is not helpful any more, as far as I can see.
    // Also the backedge value is truncated to short value. But this is not enough to
    // guarantee that there is no short-overflow (wrap): we do not manage to
    // prove that i could never be short_max, and then overflow the short range at
    // the next increment.
    public static int testIR5c_gold = testIR5c();

    @Run(test = "testIR5c")
    private static void runIR5c() {
        int val = testIR5c();
        if (val != testIR5c_gold) { throw new RuntimeException("wrong value: " + testIR5c_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIR5c() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        int sum = 0;
        int i = init;
        if (i == limit) { return sum; } // additional "exit check" before loop.
        do {
            sum = dontinline(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i != limit); // exit check at the end, but with NEQ.
        return sum;
    }

    // testIR5d: short while-loop, again similar to testIR2b and testIR5c, but with while-loop form.
    // No peeling, and so the entry value is init, and so the "init >= limit" check is useful,
    // and used by has_truncation_wrap. With it, C2 manages to prove no short-overflow.
    public static int testIR5d_gold = testIR5d();

    @Run(test = "testIR5d")
    private static void runIR5d() {
        int val = testIR5d();
        if (val != testIR5d_gold) { throw new RuntimeException("wrong value: " + testIR5d_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR5d() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        int sum = 0;
        int i = init;
        while (i != limit) {
            sum = dontinline(sum); // work to keep loop alive
            i = (short)(i+1);
        }
        return sum;
    }

    // testIR6: short do-while-loop, missing the CmpI before the loop.
    public static int testIR6_gold = testIR6();

    @Run(test = "testIR6")
    private static void runIR6() {
        int val = testIR6();
        if (val != testIR6_gold) { throw new RuntimeException("wrong value: " + testIR6_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIR6() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        // No CmpI before the loop!
        // But the loop exit check is strong enough to ignore truncation.
        int sum = 0;
        int i = init;
        do {
            sum = dontinline(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i < limit); // exit check at the end.
        return sum;
    }

    // testIR6b: short do-while-loop, missing the CmpI before the loop.
    // Compared to testIR6, the check in the loop is an NEQ.
    public static int testIR6b_gold = testIR6b();

    @Run(test = "testIR6b")
    private static void runIR6b() {
        int val = testIR6b();
        if (val != testIR6b_gold) { throw new RuntimeException("wrong value: " + testIR6b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIR6b() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        // No CmpI before the loop!
        // And the loop exit check is NOT strong enough to ignore truncation.
        int sum = 0;
        int i = init;
        do {
            sum = dontinline(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i != limit); // exit check at the end.
        return sum;
    }

    // TODO: ensure coverage
    // - char, byte and short truncation
    // - check for IRNode.COUNTED_LOOP
    // - dontinline call to prevent empty loop
    // - increment and decrement cases, non-unit stride
    // - Cases with and without compare before loop: positive and negative tests
    //
    // Template Fuzzing ideas:
    // - Mostly about correctness, not IR rules
    // - truncation:
    //   - short cast
    //   - short shift: ((i + s) << 16) >> 16
    //   - char/byte mask/shift
    // - stride: pos/neg, small integers (rarely also large?)
    // - loop shape: for, top-tested while, bottom tested do-while. Each with < or !=.
    //   - endless loop control: additional loop exit with hidden condition? - verify with IR test here.
    // - pre-loop cmp: none, explicit "init < limit", using min/max or not, using CmpU vs CmpI.
    // - bounds: small, in around short/char/byte, totally random.
    // - reference vs test methods for correctness comparison.
}
