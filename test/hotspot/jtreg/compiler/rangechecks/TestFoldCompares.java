/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8346420
 * @summary Test logic in IfNode::fold_compares, which folds 2 signed comparisons
 *          into a single comparison.
 * @library /test/lib /
 * @run main ${test.main.class} vanilla
 */

/*
 * @test id=Xcomp
 * @bug 8346420
 * @library /test/lib /
 * @run main ${test.main.class} Xcomp
 */

package compiler.rangechecks;

import compiler.lib.ir_framework.*;

/**
 * This test here is here to cover some basic cases of IfNode::fold_compares. It also contains the
 * reproducers for JDK-8346420. We don't do any result verification, other than that we should never
 * hit an Exception. For a test with result verification, see TestFoldComparesFuzzer.java
 */
public class TestFoldCompares {
    public static boolean FLAG_FALSE = false;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        switch (args[0]) {
            case "vanilla" -> { /* no extra flags */ }
            case "Xcomp"   -> { framework.addFlags("-Xcomp", "-XX:-TieredCompilation", "-XX:CompileCommand=compileonly,compiler.rangechecks.TestFoldCompares::test*"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

// TODO: //    // ------------------------- Failing cases for JDK-8346420 ------------------------------
// TODO: //
// TODO: //    @Test
// TODO: //    @Arguments(values = {Argument.NUMBER_42})
// TODO: //    // Reported overflow case with wrong result in JDK-8346420
// TODO: //    public static void test_Case3a_LTLE_overflow(int i) {
// TODO: //        int minimum, maximum;
// TODO: //        if (FLAG_FALSE) {
// TODO: //            minimum = 0;
// TODO: //            maximum = 1;
// TODO: //        } else {
// TODO: //            // Always goes to else-path
// TODO: //            minimum = Integer.MIN_VALUE;
// TODO: //            maximum = Integer.MAX_VALUE;
// TODO: //        }
// TODO: //        // i  < INT_MIN    || i  > MAX_INT
// TODO: //        // 42 < INT_MIN    || 42 > MAX_INT
// TODO: //        //    false           false
// TODO: //        // => false
// TODO: //        //
// TODO: //        // C2 transforms this into:
// TODO: //        // i  - minimum >=u (maximum - minimum) + 1
// TODO: //        // 42 - INT_MIN >=u (INT_MAX - INT_MIN) + 1
// TODO: //        // 42 + MIN_INT >=u -1                  + 1
// TODO: //        //                  ------ overflow -------
// TODO: //        // 42 + MIN_INT >=u 0
// TODO: //        // => true
// TODO: //        if (i < minimum || i > maximum) {
// TODO: //            throw new RuntimeException("i can never be outside [min_int, max_int]");
// TODO: //        }
// TODO: //    }
// TODO: //
// TODO: //    @Test
// TODO: //    @Arguments(values = {Argument.NUMBER_42})
// TODO: //    // Same as  test_Case3a_LTLE_overflow, just with swapped conditions (JDK-8346420).
// TODO: //    public static void test_Case3b_LTLE_overflow(int i) {
// TODO: //        int minimum, maximum;
// TODO: //        if (FLAG_FALSE) {
// TODO: //            minimum = 0;
// TODO: //            maximum = 1;
// TODO: //        } else {
// TODO: //            // Always goes to else-path
// TODO: //            minimum = Integer.MIN_VALUE;
// TODO: //            maximum = Integer.MAX_VALUE;
// TODO: //        }
// TODO: //        if (i > maximum || i < minimum) {
// TODO: //            throw new RuntimeException("i can never be outside [min_int, max_int]");
// TODO: //        }
// TODO: //    }
// TODO: //
// TODO: //    @Test
// TODO: //    @Arguments(values = {Argument.NUMBER_42})
// TODO: //    //  22  ConI  === 0  [[ 25 37 ]]  #int:0
// TODO: //    //  35  ConI  === 0  [[ 37 ]]  #int:minint
// TODO: //    //  33  ConI  === 0  [[ 38 81 ]]  #int:1
// TODO: //    //  37  Phi  === 34 35 22  [[ 42 80 81 84 ]]  #int:minint..0, 0u..maxint+1
// TODO: //    //  81  AddI  === _ 37 33  [[ 82 ]]
// TODO: //    //  82  Node  === 81  [[ ]]                      <----- hook
// TODO: //    //
// TODO: //    // We hit this assert, also found during work for JDK-8346420:
// TODO: //    // "fatal error: no reachable node should have no use"
// TODO: //    //
// TODO: //    // Because we compute:
// TODO: //    //   lo = lo + 1
// TODO: //    //   hook = Node(lo)
// TODO: //    //   adjusted_val = i - lo
// TODO: //    //   -> gvn transformed to: (i - lo) + -1
// TODO: //    //   -> the "lo = lo + 1" AddI now is only used by the hook,
// TODO: //    //      but once the hook is destroyed, it has no use any more,
// TODO: //    //      and we hit the assert.
// TODO: //    public static void test_Case4a_LELE_assert(int i) {
// TODO: //        int minimum, maximum;
// TODO: //        if (FLAG_FALSE) {
// TODO: //            minimum = 0;
// TODO: //            maximum = 1;
// TODO: //        } else {
// TODO: //            minimum = Integer.MIN_VALUE;
// TODO: //            maximum = Integer.MAX_VALUE;
// TODO: //        }
// TODO: //        if (i <= minimum || i > maximum) {
// TODO: //            throw new RuntimeException("should never be reached");
// TODO: //        }
// TODO: //    }

    // ------------------- IR tests to check that optimization was performed ------------------------

    // The following tests with constant bounds are expected to fold to a single CmpU.

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    @Arguments(values = {Argument.NUMBER_42})
    public static void test_lohi_ltle(int i) {
        if (i < -100_000 || i > 100_000) {
            throw new RuntimeException();
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    @Arguments(values = {Argument.NUMBER_42})
    public static void test_lohi_lele(int i) {
        if (i <= -100_000 || i > 100_000) {
            throw new RuntimeException();
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    @Arguments(values = {Argument.NUMBER_42})
    public static void test_lohi_ltlt(int i) {
        if (i < -100_000 || i >= 100_000) {
            throw new RuntimeException();
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    @Arguments(values = {Argument.NUMBER_42})
    public static void test_lohi_lelt(int i) {
        if (i <= -100_000 || i >= 100_000) {
            throw new RuntimeException();
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    @Arguments(values = {Argument.NUMBER_42})
    public static void test_hilo_ltle(int i) {
        if (i >= 100_000 || i <= -100_000) {
            throw new RuntimeException();
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    @Arguments(values = {Argument.NUMBER_42})
    public static void test_hilo_lele(int i) {
        if (i > 100_000 || i <= -100_000) {
            throw new RuntimeException();
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    @Arguments(values = {Argument.NUMBER_42})
    public static void test_hilo_lelt(int i) {
        if (i > 100_000 || i < -100_000) {
            throw new RuntimeException();
        }
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"})
    @Arguments(values = {Argument.NUMBER_42})
    public static void test_hilo_ltlt(int i) {
        if (i >= 100_000 || i < -100_000) {
            throw new RuntimeException();
        }
    }

    // The following tests can completely remove the test and branches, we can prove that
    // the path cannot be taken.

    @Setup
    public static Object[] range256(SetupInfo info) {
        return new Object[]{info.invocationCounter() & 255};
    }

    @Setup
    public static Object[] rangeM128P127(SetupInfo info) {
        return new Object[]{(info.invocationCounter() & 255) - 128};
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 0"})
    @Arguments(setup = "rangeM128P127")
    // Case from JDK-8135069. We used to do the CmpI->CmpU trick, but we can also constant fold
    // this directly!
    public static void test_empty_0(int i) {
        if (i < 0 || i > -1) {
            return; // always success
        }
        throw new RuntimeException("should not be reached");
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 0"})
    @Arguments(setup = "range256")
    public static void test_empty_1(int i) {
        if (i < 100 || i > 50) {
            return; // always success
        }
        throw new RuntimeException("should not be reached");
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 0"})
    @Arguments(setup = "range256")
    public static void test_empty_2(int i) {
        if (i <= 100 || i >= 101) {
            return; // always success
        }
        throw new RuntimeException("should not be reached");
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 1", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    // Note: the two CmpI->Bool pairs are already canonicallized and commoned to a single pair.
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 0"})
    @Arguments(setup = "range256")
    public static void test_empty_3(int i) {
        if (i <= 100 || i > 100) {
            return; // always success
        }
        throw new RuntimeException("should not be reached");
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 1", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    // Note: the two CmpI->Bool pairs are already canonicallized and commoned to a single pair.
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 0"})
    @Arguments(setup = "range256")
    public static void test_empty_4(int i) {
        if (i < 101 || i >= 101) {
            return; // always success
        }
        throw new RuntimeException("should not be reached");
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 0"})
    @Arguments(setup = "range256")
    public static void test_empty_5(int i) {
        if (i < 101 || i > 100) {
            return; // always success
        }
        throw new RuntimeException("should not be reached");
    }

    // Now test that we can use a.length, which means we do a null-check
    // and then a comparison with a LoadRange that has type int[>=0]

    public static int[] ARR = new int[256];

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING,
        applyIf = {"TieredCompilation", "true"}) // proxy for "not Xcomp"
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"},
        applyIf = {"TieredCompilation", "true"}) // proxy for "not Xcomp"
    @Arguments(setup = "range256")
    // Note: cannot get optimized with Xcomp
    static int test_array_length_and_null_check_1(int i) {
        if (i < 0 || i >= ARR.length) {
            return -1; // never happens
        }
        return i;
    }

    @Test
    @IR(counts = {IRNode.CMP_I, "= 2", IRNode.CMP_U, "= 0"}, phase = CompilePhase.AFTER_PARSING,
        applyIf = {"TieredCompilation", "true"}) // proxy for "not Xcomp"
    @IR(counts = {IRNode.CMP_I, "= 0", IRNode.CMP_U, "= 1"},
        applyIf = {"TieredCompilation", "true"}) // proxy for "not Xcomp"
    @Arguments(setup = "range256")
    // Note: cannot get optimized with Xcomp
    static int test_array_length_and_null_check_2(int i) {
        if (i < 0 || i >= ARR.length) {
            throw new RuntimeException("never go out of bounds");
        }
        return i;
    }
}
