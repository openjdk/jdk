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
 * @run driver ${test.main.class}
 */

/*
 * @test id=Xcomp
 * @bug 8385855
 * @library /test/lib /
 * @run driver ${test.main.class} -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,${test.main.class}::test*
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
    public static int opaqueSum(int i) {
        return i + 1;
    }

    @DontInline
    public static int opaqueSum(int i, int j) {
        return i + j + 1;
    }

    public static int lo = 11;
    public static int hi = 33;

    // testIRShort0: just a regular int loop
    public static int testIRShort0_gold = testIRShort0();

    @Run(test = "testIRShort0")
    private static void runIRShort0() {
        int val = testIRShort0();
        if (val != testIRShort0_gold) { throw new RuntimeException("wrong value: " + testIRShort0_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort0() {
        int init  = lo;
        int limit = hi;
        int sum = 0;
        for (int i = init; i < limit; i++) {
            sum = opaqueSum(sum);
        }
        return sum;
    }

    // testIRShort0b: just a regular int loop, but with NEQ exit check.
    public static int testIRShort0b_gold = testIRShort0b();

    @Run(test = "testIRShort0b")
    private static void runIRShort0b() {
        int val = testIRShort0b();
        if (val != testIRShort0b_gold) { throw new RuntimeException("wrong value: " + testIRShort0b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort0b() {
        int init  = lo;
        int limit = hi;
        int sum = 0;
        for (int i = init; i != limit; i++) {
            sum = opaqueSum(sum);
        }
        return sum;
    }

    // testIRShort1: short loop, but values are trivially in short range.
    public static int testIRShort1_gold = testIRShort1();

    @Run(test = "testIRShort1")
    private static void runIRShort1() {
        int val = testIRShort1();
        if (val != testIRShort1_gold) { throw new RuntimeException("wrong value: " + testIRShort1_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort1() {
        short init  = (short)lo;
        short limit = (short)hi;
        int sum = 0;
        for (short i = init; i < limit; i++) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShort1b: short loop, but values are trivially in short range. Decrement iv.
    public static int testIRShort1b_gold = testIRShort1b();

    @Run(test = "testIRShort1b")
    private static void runIRShort1b() {
        int val = testIRShort1b();
        if (val != testIRShort1b_gold) { throw new RuntimeException("wrong value: " + testIRShort1b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort1b() {
        short init  = (short)hi;
        short limit = (short)lo;
        int sum = 0;
        for (short i = init; i > limit; i--) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShort1c: short loop, but values are trivially in short range. Incr by 2.
    // Not safe: lo=32766+2 would wrap past short_max.
    public static int testIRShort1c_gold = testIRShort1c();

    @Run(test = "testIRShort1c")
    private static void runIRShort1c() {
        int val = testIRShort1c();
        if (val != testIRShort1c_gold) { throw new RuntimeException("wrong value: " + testIRShort1c_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort1c() {
        short init  = (short)lo;
        short limit = (short)hi;
        int sum = 0;
        for (short i = init; i < limit; i+=2) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShort1d: short loop, but values are trivially in short range. Decrement iv by 2.
    // Not safe: lo=-32767-2 would wrap past short_min.
    public static int testIRShort1d_gold = testIRShort1d();

    @Run(test = "testIRShort1d")
    private static void runIRShort1d() {
        int val = testIRShort1d();
        if (val != testIRShort1d_gold) { throw new RuntimeException("wrong value: " + testIRShort1d_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort1d() {
        short init  = (short)hi;
        short limit = (short)lo;
        int sum = 0;
        for (short i = init; i > limit; i-=2) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShort2: short loop, ranges proved in short range via CmpI before loop.
    public static int testIRShort2_gold = testIRShort2();

    @Run(test = "testIRShort2")
    private static void runIRShort2() {
        int val = testIRShort2();
        if (val != testIRShort2_gold) { throw new RuntimeException("wrong value: " + testIRShort2_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort2() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        int sum = 0;
        for (int i = init; i < limit; i = (short)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
            // The backedge value of i is also far
            // enough from short boundaries, because of
            // the loop exit check:
            //   i < limit <= 100
        }
        return sum;
    }

    // testIRShort2b: short loop, ranges proved in short range via CmpI before loop.
    // Compared to testIRShort2, the check in the loop is an NEQ.
    //
    // Since the bug fix of JDK-8386830, we no longer allow this case to detect CountedLoop:
    // The backedge finds no useful constraint, the "i != limit" does not give any restrictions,
    // and so we have to assume it produces the full range.
    // Comparing with testIRShort2, there we have a useful check "i < limit", which does
    // give us a restriction, that helps us prove there is not wrap overflow.
    //
    // In the future, we could try to do something more smart, and combine the info about
    // entry type "init < limit <= 100" with the fact that we have unity-stride, and so
    // we should not be able to skip the NEQ "i != limit", and be able to canonicalize
    // NEQ to LT. But for now, I consider this an edge-case that we will just have to accept
    // will not be optimized to CountedLoop for now. For now, a workaround is using the
    // exit condition "i < limit".
    // This is really a problem about iv evolution (iv starts in range, increments by 1,
    // and cannot skip exit check, so NEQ can be converted to LT), and cannot be solved
    // by the type info of entry/backedge separately, so I don't have a quick fix here.
    // We do this NEQ to LT canonicalization for int loops, but we would also need
    // dedicated logic for it specifically combined with the wrap-detection logic.
    public static int testIRShort2b_gold = testIRShort2b();

    @Run(test = "testIRShort2b")
    private static void runIRShort2b() {
        int val = testIRShort2b();
        if (val != testIRShort2b_gold) { throw new RuntimeException("wrong value: " + testIRShort2b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort2b() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in short range.
        int sum = 0;
        for (int i = init; i != limit; i = (short)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
            // Unfortunately, the backedge does not produce a useful
            // check with "i != limit", and so the type is unconstrained.
        }
        return sum;
    }

    // testIRShort3: short loop, and range in short range via CmpI before loop (for loop limit).
    public static int testIRShort3_gold = testIRShort3();

    @Run(test = "testIRShort3")
    private static void runIRShort3() {
        int val = testIRShort3();
        if (val != testIRShort3_gold) { throw new RuntimeException("wrong value: " + testIRShort3_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort3() {
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
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShort3b: short loop, and range in short range via CmpI before loop (for loop limit).
    // Decr iv.
    // Missed optimization opportunity:
    //   CountedLoopConverter::LoopStructure::is_infinite_loop
    // It wrongly fires, and prevents CountedLoop detection.
    // This check is increment-specific, and fails to acocunt for decrement:
    //   if (limit_t->hi_as_long() > incr_t->hi_as_long()) {
    // I don't think this is intentional, because we have handling for positive and
    // negative stride in CountedLoopConverter::has_truncation_wrap.
    public static int testIRShort3b_gold = testIRShort3b();

    @Run(test = "testIRShort3b")
    private static void runIRShort3b() {
        int val = testIRShort3b();
        if (val != testIRShort3b_gold) { throw new RuntimeException("wrong value: " + testIRShort3b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort3b() {
        int limit = Math.max(lo, 0);   // limit in [0..max_int]
        int init  = Math.min(hi, 100); // init  in [min_int..100]
        int sum = 0;
        for (int i = init; i > limit; i = (short)(i-1)) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShort3x: short loop, fails to be recognized as CountedLoop.
    // Compared to testIRShort3, the check in the loop is an NEQ.
    public static int testIRShort3x_gold = testIRShort3x();

    @Run(test = "testIRShort3x")
    private static void runIRShort3x() {
        int val = testIRShort3x();
        if (val != testIRShort3x_gold) { throw new RuntimeException("wrong value: " + testIRShort3x_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort3x() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        int sum = 0;
        // No useful CmpI before the loop.
        // And the CmpI of the for limit is NEQ, so not useful either.
        for (int i = init; i != limit; i = (short)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShort4: short loop, with a CmpI, but the limit ranges are bad.
    public static int testIRShort4_gold = testIRShort4();

    @Run(test = "testIRShort4")
    private static void runIRShort4() {
        int val = testIRShort4();
        if (val != testIRShort4_gold) { throw new RuntimeException("wrong value: " + testIRShort4_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort4() {
        int init  = Math.max(lo, 0);       // init  in [0..max_int]
        int limit = Math.min(hi, 100_000); // limit in [min_int..100_000]
        int sum = 0;
        // Now, the check is not good enough:
        // -> init < limit <= 100_000
        // -> filtered_int_type return [min_int..99_999]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99_999], which is NOT in short range.
        for (int i = init; i < limit; i = (short)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
            // Also: the backedge range is not good because
            // the exit check is not strong enough for short:
            //   i < limit <= 100_000
        }
        return sum;
    }

    // testIRShort5: short do-while-loop, and range in short range via CmpI before loop (for loop limit).
    public static int testIRShort5_gold = testIRShort5();

    @Run(test = "testIRShort5")
    private static void runIRShort5() {
        int val = testIRShort5();
        if (val != testIRShort5_gold) { throw new RuntimeException("wrong value: " + testIRShort5_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort5() {
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
            sum = opaqueSum(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i < limit); // exit check at the end.
        return sum;
    }

    // testIRShort5b: short do-while-loop, but the backedge check with NEQ is not strong enough to prevent wrapping.
    // Compared to testIRShort5, the check in the loop is an NEQ.
    public static int testIRShort5b_gold = testIRShort5b();

    @Run(test = "testIRShort5b")
    private static void runIRShort5b() {
        int val = testIRShort5b();
        if (val != testIRShort5b_gold) { throw new RuntimeException("wrong value: " + testIRShort5b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort5b() {
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
            sum = opaqueSum(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i != limit); // exit check at the end, but with NEQ.
        return sum;
    }

    // testIRShort5c: short do-while-loop.
    // While the code shape looks very close to testIRShort2b, it does not behave the same.
    // The while loop below is peeled once. The additional "exit check" is eliminated,
    // because redundant after "init >= limit" check.
    // From peeling, the new initial value is a truncated short value, and not init, so
    // the "init >= limit" check is not helpful any more, as far as I can see.
    // Also the backedge value is truncated to short value. But this is not enough to
    // guarantee that there is no short-overflow (wrap): we do not manage to
    // prove that i could never be short_max, and then overflow the short range at
    // the next increment.
    public static int testIRShort5c_gold = testIRShort5c();

    @Run(test = "testIRShort5c")
    private static void runIRShort5c() {
        int val = testIRShort5c();
        if (val != testIRShort5c_gold) { throw new RuntimeException("wrong value: " + testIRShort5c_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort5c() {
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
            sum = opaqueSum(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i != limit); // exit check at the end, but with NEQ.
        return sum;
    }

    // testIRShort5d: short while-loop, again similar to testIRShort2b and testIRShort5c, but with while-loop form.
    //
    // Same issue as with testIRShort2b:
    // After JDK-8386830, we now see that the backedge type is not constrained,
    // and so don't allow CountedLoop detection.
    // However, we could be smarter in the future, and canonicalize NEQ
    // to LT, because this is a unity-stride loop where the "i != limit"
    // can provably not be skipped. For now, we just have to accept that
    // we cannot optimize this, and people would have to use "i < limit",
    // see testIRShort5.
    public static int testIRShort5d_gold = testIRShort5d();

    @Run(test = "testIRShort5d")
    private static void runIRShort5d() {
        int val = testIRShort5d();
        if (val != testIRShort5d_gold) { throw new RuntimeException("wrong value: " + testIRShort5d_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort5d() {
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
            sum = opaqueSum(sum); // work to keep loop alive
            i = (short)(i+1);
            // Unfortunately, the backedge does not produce a useful
            // check with "i != limit", and so the type is unconstrained.
        }
        return sum;
    }

    // testIRShort6: short do-while-loop, missing the CmpI before the loop.
    public static int testIRShort6_gold = testIRShort6();

    @Run(test = "testIRShort6")
    private static void runIRShort6() {
        int val = testIRShort6();
        if (val != testIRShort6_gold) { throw new RuntimeException("wrong value: " + testIRShort6_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort6() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        // No CmpI before the loop!
        // But the loop exit check is strong enough to ignore truncation.
        int sum = 0;
        int i = init;
        do {
            sum = opaqueSum(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i < limit); // exit check at the end.
        return sum;
    }

    // testIRShort6b: short do-while-loop, missing the CmpI before the loop.
    // Compared to testIRShort6, the check in the loop is an NEQ.
    public static int testIRShort6b_gold = testIRShort6b();

    @Run(test = "testIRShort6b")
    private static void runIRShort6b() {
        int val = testIRShort6b();
        if (val != testIRShort6b_gold) { throw new RuntimeException("wrong value: " + testIRShort6b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort6b() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        // No CmpI before the loop!
        // And the loop exit check is NOT strong enough to ignore truncation.
        int sum = 0;
        int i = init;
        do {
            sum = opaqueSum(sum); // work to keep loop alive
            i = (short)(i+1);
        } while (i != limit); // exit check at the end.
        return sum;
    }

    public static int opaqueCounter;

    @DontInline
    public static void opaqueReset() {
        opaqueCounter = 0;
    }

    @DontInline
    public static boolean opaqueCheck() {
        return (opaqueCounter++) >= 100_000;
    }

    // testIRShort7: with additional opaque exit check.
    // Useful to verify that TestTruncationWrapFuzzer.java opaque exit checks
    // do not prohibit CountedLoop detection.
    // We start from testIRShort3 and testIRShort4, but add the additional opaque exit.
    public static int testIRShort7_gold = testIRShort7();

    @Run(test = "testIRShort7")
    private static void runIRShort7() {
        int val = testIRShort7();
        if (val != testIRShort7_gold) { throw new RuntimeException("wrong value: " + testIRShort7_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShort7() {
        opaqueReset();
        int init  = Math.max(lo, 0);
        int limit = Math.min(hi, 100); // good bounds
        int sum = 0;
        for (int i = init; i < limit; i = (short)(i+1)) {
            sum = opaqueSum(sum, i);
            if (opaqueCheck()) { break; }
        }
        return sum;
    }

    // testIRShort7b
    public static int testIRShort7b_gold = testIRShort7b();

    @Run(test = "testIRShort7b")
    private static void runIRShort7b() {
        int val = testIRShort7b();
        if (val != testIRShort7b_gold) { throw new RuntimeException("wrong value: " + testIRShort7b_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShort7b() {
        opaqueReset();
        int init  = Math.max(lo, 0);
        int limit = Math.min(hi, 100_000); // bad bounds
        int sum = 0;
        for (int i = init; i < limit; i = (short)(i+1)) {
            sum = opaqueSum(sum, i);
            if (opaqueCheck()) { break; }
        }
        return sum;
    }

    // testIRByte1: byte loop, but values are trivially in byte range.
    // But: "byte i++" goes through "<< 24 >> 24" truncation with signed extension,
    //      and that's not recognized by TruncatedIncrement::build.
    public static int testIRByte1_gold = testIRByte1();

    @Run(test = "testIRByte1")
    private static void runIRByte1() {
        int val = testIRByte1();
        if (val != testIRByte1_gold) { throw new RuntimeException("wrong value: " + testIRByte1_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRByte1() {
        byte init  = (byte)lo;
        byte limit = (byte)hi;
        int sum = 0;
        for (byte i = init; i < limit; i++) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRByte2: byte loop, ranges proved in byte range via CmpI before loop.
    // But: "byte i++" goes through "<< 24 >> 24" truncation with signed extension,
    //      and that's not recognized by TruncatedIncrement::build.
    public static int testIRByte2_gold = testIRByte2();

    @Run(test = "testIRByte2")
    private static void runIRByte2() {
        int val = testIRByte2();
        if (val != testIRByte2_gold) { throw new RuntimeException("wrong value: " + testIRByte2_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRByte2() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in byte range.
        int sum = 0;
        for (int i = init; i < limit; i = (byte)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
            // The backedge value of i is also far
            // enough from byte boundaries, because of
            // the loop exit check:
            //   i < limit <= 100
        }
        return sum;
    }

    // testIRByte4: byte loop, with a CmpI, but the limit ranges are bad.
    // And: "byte i++" goes through "<< 24 >> 24" truncation with signed extension,
    //      and that's not recognized by TruncatedIncrement::build.
    public static int testIRByte4_gold = testIRByte4();

    @Run(test = "testIRByte4")
    private static void runIRByte4() {
        int val = testIRByte4();
        if (val != testIRByte4_gold) { throw new RuntimeException("wrong value: " + testIRByte4_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRByte4() {
        int init  = Math.max(lo, 0);       // init  in [0..max_int]
        int limit = Math.min(hi, 1_000); // limit in [min_int..1_000]
        int sum = 0;
        // Now, the check is not good enough:
        // -> init < limit <= 1_000
        // -> filtered_int_type return [min_int..999]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..999], which is NOT in byte range.
        for (int i = init; i < limit; i = (byte)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
            // Also: the backedge range is not good because
            // the exit check is not strong enough for byte:
            //   i < limit <= 1_000
        }
        return sum;
    }

    // testIRChar1: char loop, but values are trivially in char range.
    // But: "char i++" lowers through mask "& 0xffff", not recognized by TruncatedIncrement::build.
    public static int testIRChar1_gold = testIRChar1();

    @Run(test = "testIRChar1")
    private static void runIRChar1() {
        int val = testIRChar1();
        if (val != testIRChar1_gold) { throw new RuntimeException("wrong value: " + testIRChar1_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRChar1() {
        char init  = (char)lo;
        char limit = (char)hi;
        int sum = 0;
        for (char i = init; i < limit; i++) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRChar2: char loop, ranges proved in char range via CmpI before loop.
    // But: "char i++" lowers through mask "& 0xffff", not recognized by TruncatedIncrement::build.
    public static int testIRChar2_gold = testIRChar2();

    @Run(test = "testIRChar2")
    private static void runIRChar2() {
        int val = testIRChar2();
        if (val != testIRChar2_gold) { throw new RuntimeException("wrong value: " + testIRChar2_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRChar2() {
        int init  = Math.max(lo, 0);   // init  in [0..max_int]
        int limit = Math.min(hi, 100); // limit in [min_int..100]
        if (init >= limit) { return -1; } // CmpI before loop
        // -> init < limit <= 100
        // -> filtered_int_type return [min_int..99]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99], which is in char range.
        int sum = 0;
        for (int i = init; i < limit; i = (char)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
            // The backedge value of i is also far
            // enough from char boundaries, because of
            // the loop exit check:
            //   i < limit <= 100
        }
        return sum;
    }

    // testIRChar3: char loop, and range in char range via CmpI before loop (for loop limit).
    // But: "char i++" lowers through mask "& 0xffff", not recognized by TruncatedIncrement::build.
    public static int testIRChar3_gold = testIRChar3();

    @Run(test = "testIRChar3")
    private static void runIRChar3() {
        int val = testIRChar3();
        if (val != testIRChar3_gold) { throw new RuntimeException("wrong value: " + testIRChar3_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRChar3() {
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
        //    we get init in [0..99], which is in char range.
        for (int i = init; i < limit; i = (char)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRChar3Mask: char loop, and range in char range via CmpI before loop (for loop limit).
    public static int testIRChar3Mask_gold = testIRChar3Mask();

    @Run(test = "testIRChar3Mask")
    private static void runIRChar3Mask() {
        int val = testIRChar3Mask();
        if (val != testIRChar3Mask_gold) { throw new RuntimeException("wrong value: " + testIRChar3Mask_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRChar3Mask() {
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
        //    we get init in [0..99], which is in char range.
        for (int i = init; i < limit; i = (i+1) & 0x7fff) { // mask instead of cast
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRChar4: char loop, with a CmpI, but the limit ranges are bad.
    // And: "char i++" lowers through mask "& 0xffff", not recognized by TruncatedIncrement::build.
    public static int testIRChar4_gold = testIRChar4();

    @Run(test = "testIRChar4")
    private static void runIRChar4() {
        int val = testIRChar4();
        if (val != testIRChar4_gold) { throw new RuntimeException("wrong value: " + testIRChar4_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRChar4() {
        int init  = Math.max(lo, 0);       // init  in [0..max_int]
        int limit = Math.min(hi, 100_000); // limit in [min_int..100_000]
        int sum = 0;
        // Now, the check is not good enough:
        // -> init < limit <= 100_000
        // -> filtered_int_type return [min_int..99_999]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99_999], which is NOT in char range.
        for (int i = init; i < limit; i = (char)(i+1)) {
            sum = opaqueSum(sum); // work to keep loop alive
            // Also: the backedge range is not good because
            // the exit check is not strong enough for char:
            //   i < limit <= 100_000
        }
        return sum;
    }

    // testIRChar4Mask: char loop, with a CmpI, but the limit ranges are bad.
    public static int testIRChar4Mask_gold = testIRChar4Mask();

    @Run(test = "testIRChar4Mask")
    private static void runIRChar4Mask() {
        int val = testIRChar4Mask();
        if (val != testIRChar4Mask_gold) { throw new RuntimeException("wrong value: " + testIRChar4Mask_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRChar4Mask() {
        int init  = Math.max(lo, 0);       // init  in [0..max_int]
        int limit = Math.min(hi, 100_000); // limit in [min_int..100_000]
        int sum = 0;
        // Now, the check is not good enough:
        // -> init < limit <= 100_000
        // -> filtered_int_type return [min_int..99_999]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99_999], which is NOT in char range.
        for (int i = init; i < limit; i = (i+1) & 0x7fff) { // mask instead of cast
            sum = opaqueSum(sum); // work to keep loop alive
            // Also: the backedge range is not good because
            // the exit check is not strong enough for char:
            //   i < limit <= 100_000
        }
        return sum;
    }

    // testIRShift16: short loop, and range in short range via CmpI before loop (for loop limit).
    public static int testIRShift16_gold = testIRShift16();

    @Run(test = "testIRShift16")
    private static void runIRShift16() {
        int val = testIRShift16();
        if (val != testIRShift16_gold) { throw new RuntimeException("wrong value: " + testIRShift16_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "> 0"})
    static int testIRShift16() {
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
        for (int i = init; i < limit; i = ((i+1) << 16) >> 16) { // explicit shift instead of cast
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShift16BadBounds: short loop, with a CmpI, but the limit ranges are bad.
    public static int testIRShift16BadBounds_gold = testIRShift16BadBounds();

    @Run(test = "testIRShift16BadBounds")
    private static void runIRShift16BadBounds() {
        int val = testIRShift16BadBounds();
        if (val != testIRShift16BadBounds_gold) { throw new RuntimeException("wrong value: " + testIRShift16BadBounds_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShift16BadBounds() {
        int init  = Math.max(lo, 0);       // init  in [0..max_int]
        int limit = Math.min(hi, 100_000); // limit in [min_int..100_000]
        int sum = 0;
        // Now, the check is not good enough:
        // -> init < limit <= 100_000
        // -> filtered_int_type return [min_int..99_999]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..99_999], which is NOT in short range.
        for (int i = init; i < limit; i = ((i+1) << 16) >> 16) { // explicit shift instead of cast
            sum = opaqueSum(sum); // work to keep loop alive
            // Also: the backedge range is not good because
            // the exit check is not strong enough for short:
            //   i < limit <= 100_000
        }
        return sum;
    }

    // testIRShift8: 24-bit loop, and range in 24-bit range via CmpI before loop (for loop limit).
    // Note: this shift value is strange, we probably wanted to implement byte truncation
    //       with shift=24, but instead we have 24-bit signed truncation.
    // Note2: this pattern would have been supported by TruncatedIncrement::build, but it gets
    //        modified by LShiftINode::Ideal:
    //          RShiftI(AddI(LShiftI(Phi, 8), 256), 8)
    //        The same is explicitly excluded for shift 16, to preserve short/byte idioms.
    public static int testIRShift8_gold = testIRShift8();

    @Run(test = "testIRShift8")
    private static void runIRShift8() {
        int val = testIRShift8();
        if (val != testIRShift8_gold) { throw new RuntimeException("wrong value: " + testIRShift8_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShift8() {
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
        //    we get init in [0..99], which is in 24-bit (and byte) range.
        for (int i = init; i < limit; i = ((i+1) << 8) >> 8) { // explicit shift instead of cast
            sum = opaqueSum(sum); // work to keep loop alive
        }
        return sum;
    }

    // testIRShift8BadBounds: 24-bit loop, with a CmpI, but the limit ranges are bad.
    // Note: same issues as for testIRShift8.
    // Note2: the range argument seems a bit strange here, but it turns out that
    //        TruncatedIncrement::build maps shift=8 to BYTE, which just shows that
    //        the implementation confused the shift=24 with shift=8.
    //        Since we map to BYTE, 1_000 would be out of bounds, that's why this
    //        is still a bad bounds example.
    public static int testIRShift8BadBounds_gold = testIRShift8BadBounds();

    @Run(test = "testIRShift8BadBounds")
    private static void runIRShift8BadBounds() {
        int val = testIRShift8BadBounds();
        if (val != testIRShift8BadBounds_gold) { throw new RuntimeException("wrong value: " + testIRShift8BadBounds_gold + " vs " + val); }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "= 0"})
    static int testIRShift8BadBounds() {
        int init  = Math.max(lo, 0);       // init  in [0..max_int]
        int limit = Math.min(hi, 1_000); // limit in [min_int..1_000]
        int sum = 0;
        // Now, the check is not good enough:
        // -> init < limit <= 1_000
        // -> filtered_int_type return [min_int..999]
        // -> and intersected with its previous type [0..max_int]
        //    we get init in [0..999], which is NOT in byte range.
        for (int i = init; i < limit; i = ((i+1) << 8) >> 8) { // explicit shift instead of cast
            sum = opaqueSum(sum); // work to keep loop alive
            // Also: the backedge range is not good because
            // the exit check is not strong enough for byte:
            //   i < limit <= 1_000
        }
        return sum;
    }
}
