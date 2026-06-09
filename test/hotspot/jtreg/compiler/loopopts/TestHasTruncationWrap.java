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

    // TODO: add back in
    // // Test shape first reported in JDK-8385855, led to assert in JDK27:
    // //   assert(cmp->Opcode() == Op_CmpI) failed: signed comparison required
    // public static int   test0_start = 0;
    // public static int   test0_stop  = 100;
    // public static int[] test0_array = new int[100];

    // @Test
    // public static void test0() {
    //     int   start = test0_start;
    //     int   stop  = test0_stop;
    //     int[] array = test0_array;

    //     stop = (stop << 16) >> 16;
    //     int v = array[start]; // dominating CmpU detected by filtered_int_type
    //     for (int i = start; i < stop;) {
    //         i++;
    //         i = (i << 16) >> 16; // iv truncation
    //     }
    // }


    // A second reproducer from JDK-8385855, leads to wrong result since JDK18 (JDK-8276162).
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

    // ---- More general tests, Checking that truncated iv loops become CountedLoops ---------

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
