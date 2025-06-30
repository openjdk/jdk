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
 *
 */

/*
 * @test id=Xbatch
 * @bug 8288981 8350579 8350577
 * @summary Test all possible cases in which Assertion Predicates are required such that the graph is not left in a
 *          broken state to trigger assertions. Additional tests ensure the correctness of the implementation.
 *          All tests additionally -XX:+AbortVMOnCompilationFailure which would catch bad graphs as a result of missing
            or wrong Assertion Predicates where we simply bail out of C2 compilation.
 * @run main/othervm -Xbatch
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates Xbatch
 */

/*
 * @test id=NoTieredCompilation
 * @bug 8288981 8350579 8350577
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates NoTieredCompilation
 */

/*
 * @test id=Xcomp
 * @bug 8288981 8350579 8350577
 * @run main/othervm -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=inline,compiler.predicates.assertion.TestAssertionPredicates::inline
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates Xcomp
 */

/*
 * @test id=XcompNoTiered
 * @bug 8288981 8350577
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=inline,compiler.predicates.assertion.TestAssertionPredicates::inline
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates XcompNoTiered
 */

/*
 * @test id=LoopMaxUnroll0
 * @bug 8288981 8350579 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:LoopMaxUnroll=0
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates LoopMaxUnroll0
 */

/*
 * @test id=LoopMaxUnroll2
 * @bug 8288981 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:LoopMaxUnroll=2
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates LoopMaxUnroll2
 */

/*
 * @test id=LoopUnrollLimit40
 * @bug 8288981 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:LoopUnrollLimit=40
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates LoopUnrollLimit40
 */

/*
 * @test id=LoopUnrollLimit150
 * @bug 8288981 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:LoopUnrollLimit=150
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates LoopUnrollLimit150
 */

/*
 * @test id=UseProfiledLoopPredicateFalse
 * @bug 8288981 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:-UseProfiledLoopPredicate
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates NoProfiledLoopPredicate
 */

/*
 * @test id=DataUpdate
 * @key randomness
 * @bug 8288981 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates DataUpdate
 */

/*
 * @test id=CloneDown
 * @bug 8288981 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:-BlockLayoutByFrequency -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates CloneDown
 */

/*
 * @test id=StressXcomp
 * @key randomness
 * @bug 8288981 8350579 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:+StressGCM -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates Stress
 */

/*
 * @test id=StressXbatch
 * @key randomness
 * @bug 8288981 8350579 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:+StressGCM -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates Stress
 */

/*
 * @test id=StressXcompMaxUnroll0
 * @key randomness
 * @bug 8288981 8356084
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:+AbortVMOnCompilationFailure
 *                   -XX:LoopMaxUnroll=0 -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   compiler.predicates.assertion.TestAssertionPredicates StressXcompMaxUnroll0
 */

/*
 * @test id=NoLoopPredicationXcomp
 * @bug 8288981 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:-UseLoopPredicate
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates NoLoopPredication
 */

/*
 * @test id=NoLoopPredicationXbatch
 * @bug 8288981 8350579 8350577
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xbatch -XX:-UseLoopPredicate
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.assertion.TestAssertionPredicates::*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-KillPathsReachableByDeadTypeNode
 *                   compiler.predicates.assertion.TestAssertionPredicates NoLoopPredication
 */

/*
 * @test id=NoFlags
 * @bug 8288981 8350579 8350577
 * @run main compiler.predicates.assertion.TestAssertionPredicates NoFlags
 */

package compiler.predicates.assertion;

public class TestAssertionPredicates {
    static int[] iArrShort = new int[10];
    static int[] iArr = new int[200];
    static int[] iArr2 = new int[200];
    static int[] iArr3 = new int[300];
    static int[] iArrNull = null;
    static int[][] iArr2D = new int[10][10];
    static short[] sArr = new short[10];
    static float[] fArr = new float[10];
    static float[][] fArr2D = new float[10][10];

    static boolean flag, flag2, flagTrue = true;
    static boolean flagFalse, flagFalse2;
    static int iFld = 34;
    static int iFld2, iFld3;
    static int two = 2;
    static long lFld, lFldOne = 1;
    static float fFld;
    static short sFld;
    static short five = 5;
    static byte byFld;
    volatile byte byFldVol;

    static class Foo {
        int iFld;
    }

    static Foo foo = new Foo();
    static int fooArrSize = 10000001;

    public static void main(String[] args) {
        switch (args[0]) {
            case "Xbatch" -> {
                for (int i = 0; i < 10000; i++) {
                    flag = !flag;
                    testTemplateAssertionPredicateNotRemovedHalt();
                    testTemplateAssertionPredicateNotRemovedMalformedGraph();
                    test8305428();
                    test8305428No2();
                }
            }
            case "NoTieredCompilation" -> {
                for (int i = 0; i < 1000; i++) {
                    test8320920();
                    test8332501();
                }
            }
            case "NoProfiledLoopPredicate" -> testWithPartialPeelingFirst();
            case "LoopMaxUnroll0" -> {
                testPeeling();
                testPeelingTwice();
                testPeelingThreeTimes();
                testUnswitchingThenPeeling();
                testPeelingThenUnswitchingThenPeeling();
                testPeelingThenUnswitchingThenPeelingThenPreMainPost();
                testDyingRuntimePredicate();
                testDyingNegatedRuntimePredicate();
            }
            case "LoopMaxUnroll2" -> {
                testPeelMainLoopAfterUnrollingThenPreMainPost();
                testPeelMainLoopAfterUnrolling2();
            }
            case "LoopUnrollLimit40" -> testPeelMainLoopAfterUnrollingThenPreMainPostThenUnrolling();
            case "LoopUnrollLimit150" -> {
                testUnrolling8();
                testUnrolling16();
                testPeelingUnrolling16();
            }
            case "Xcomp" -> {
                testPreMainPost();
                testUnrolling2();
                testUnrolling4();
                testPeelingThenPreMainPost();
                testUnswitchingThenPeelingThenPreMainPost();
                testDyingInitializedAssertionPredicate();
                test8288981();
                test8288941();
                testRemovingParsePredicatesThenMissingTemplates();
                iFld = -1;
                test8292507();
                TestAssertionPredicates t = new TestAssertionPredicates();
                t.test8296077();
                test8308504No2();
                test8307131();
                test8308392No1();
                iFld = -50000;
                test8308392No2();
                test8308392No3();
                test8308392No4();
                test8308392No5();
                test8308392No6();
                test8308392No7();
                iFld = 0;
                test8308392No8();
                runTest8308392No9();
                test8308392No10();
                testSplitIfCloneDownWithOpaqueAssertionPredicate();
            }
            case "XcompNoTiered" -> {
                TestAssertionPredicates t = new TestAssertionPredicates();
                for (int i = 0; i < 10; i++) {
                    t.test8308504();
                    test8308504No2();
                }
            }
            case "DataUpdate", "StressXcompMaxUnroll0" -> {
                for (int i = 0; i < 10; i++) {
                    // The following tests create large arrays. Limit the number of invocations to reduce the time spent.
                    flag = !flag;
                    testDataUpdateUnswitchingPeelingUnroll();
                    testDataUpdateUnswitchUnroll();
                    testDataUpdateUnroll();
                    testDataUpdatePeelingUnroll();
                    testPeelingThreeTimesDataUpdate();
                }
            }
            case "CloneDown" -> {
                for (int i = 0; i < 100; i++) {
                    // The following tests create large arrays. Limit the number of invocations to reduce the time spent.
                    testTrySplitUpNonOpaqueExpressionNode();
                    testTrySplitUpOpaqueLoopInit();
                }
            }
            case "NoLoopPredication", "NoFlags", "Stress" -> {
                runAllFastTests();
            }
            default -> throw new RuntimeException("invalid arg");
        }
    }

    // Runs almost all tests except for the heavy ones like the testData*() tests.
    static void runAllFastTests() {
        for (int i = 0; i < 10000; i++) {
            testPeeling();
            testPeelingTwice();
            testPeelingThreeTimes();
            testUnswitchingThenPeeling();
            testPeelingThenUnswitchingThenPeeling();
            testPeelingThenUnswitchingThenPeelingThenPreMainPost();
            testDyingRuntimePredicate();
            testDyingNegatedRuntimePredicate();
            testPeelMainLoopAfterUnrollingThenPreMainPost();
            testPeelMainLoopAfterUnrolling2();
            testUnrolling8();
            testUnrolling16();
            testPeelingUnrolling16();
            testPreMainPost();
            testUnrolling2();
            testUnrolling4();
            testPeelingThenPreMainPost();
            testUnswitchingThenPeelingThenPreMainPost();
            testDyingInitializedAssertionPredicate();
            test8288981();
            test8288941();
            testRemovingParsePredicatesThenMissingTemplates();
            iFld = -1;
            test8292507();
            test8307131();
            test8308392No1();
            iFld = -50000;
            test8308392No2();
            test8308392No3();
            test8308392No4();
            test8308392No5();
            test8308392No6();
            test8308392No7();
            iFld = 0;
            test8308392No8();
            runTest8308392No9();
            test8308392No10();
            testSplitIfCloneDownWithOpaqueAssertionPredicate();
            testTemplateAssertionPredicateNotRemovedHalt();
            testTemplateAssertionPredicateNotRemovedMalformedGraph();
            testDyingRuntimePredicate();
            testDyingNegatedRuntimePredicate();
            testDyingInitializedAssertionPredicate();
            test8305428();
            test8305428No2();
            test8320920();
            test8332501();
            test8308504();
            test8308504No2();
            testBackToBackLoopLimitCheckPredicate();
            testTrySplitUpNonOpaqueExpressionNode();
            testTrySplitUpOpaqueLoopInit();
        }
    }

        // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static void testPreMainPost() {
        int x = 0;
        for (int i = 1; i > five; i -= 2) {
            x = iArr[i];
            if (x == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static void testUnrolling2() {
        for (int i = 3; i > five; i -= 2) {
            int x = 0;
            x = iArr[i];
            if (x == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static void testUnrolling4() {
        int x = 0;
        for (int i = 7; i > five; i -= 2) {
            x = iArr[i];
            if (x == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:LoopUnrollLimit=150 -XX:CompileCommand=compileonly,Test*::*
    static void testUnrolling8() {
        int x = 0;
        for (int i = 15; i > five; i -= 2) {
            x = iArr[i];
            if (x == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:LoopUnrollLimit=150 -XX:CompileCommand=compileonly,Test*::*
    static void testUnrolling16() {
        int x = 0;
        for (int i = 31; i > five; i -= 2) {
            x = iArr[i];
            if (x == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:LoopUnrollLimit=150 -XX:CompileCommand=compileonly,Test*::*
    // Loop is first peeled and then unrolled.
    static void testPeelingUnrolling16() {
        int three = 0;
        int limit = 2;
        long l1 = 34L;
        long l2 = 35L;
        long l3 = 36L;
        long l4 = 37L;

        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            three = 33;
        }

        for (int i = 33; i > five; i -= 2) {
            int x = iArr[i];
            if (x == i) {
                iFld += 34;
            }

            if (i > three) {
                // DivLs add 30 to the loop body count and we hit LoopUnrollLimit.
                // After CCP, these statements are folded away and we can unroll this loop.
                l1 /= lFld;
                l2 /= lFld;
                l3 /= lFld;
                l4 /= lFld;
            }

            if (flag) {
                return;
            }
        }
    }

    // -Xcomp -XX:LoopMaxUnroll=0 -XX:CompileCommand=compileonly,Test*::*
    static void testPeeling() {
        for (int i = 1; i > five; i -= 2) {
            int arrLoad = iArr[i];

            if (flag) {
                return;
            }

            if (arrLoad == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:LoopMaxUnroll=0 -XX:CompileCommand=compileonly,Test*::*
    private static void testPeelingTwice() {
        for (int i = 3; i > five; i -= 2) {
            int arrLoad = iArr[i];

            if (flag) {
                iFld2 = 3;
                return;
            }

            if (i < 2 && flag2) {
                return;
            }

            if (arrLoad == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:LoopMaxUnroll=0 -XX:CompileCommand=compileonly,Test*::*
    private static void testPeelingThreeTimes() {
        for (int i = 5; i > five; i -= 2) {
            int arrLoad = iArr[i];

            if (iFld2 == 4) {
                iFld2 = 20;
                return;
            }

            if (i < 4 && iFld2 == 3) {
                iFld2 = 42;
                return;
            }

            if (i < 2 && iFld2 == 2) {
                iFld2 = 52;
                return;
            }

            if (arrLoad == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:LoopMaxUnroll=0 -XX:CompileCommand=compileonly,Test*::*
    static void testUnswitchingThenPeeling() {
        for (int i = 1; i > five; i -= 2) {
            iFld = iArr[i];

            if (flag2) {
                iFld2 = 24;
            }

            if (flag) {
                return;
            }

            if (iFld == i) {
                iFld = 34;
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:LoopMaxUnroll=0
    static void testPeelingThenUnswitchingThenPeeling() {
        int zero = 34;
        int limit = 2;

        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        for (int i = 3; i > five; i -= 2) {
            iFld = iArr[i];
            if (iFld == i) {
                fFld = 324;
            }

            if (flag) { // 1) Triggers loop peeling
                return;
            }

            int k = iFld2 + i * zero; // loop variant before CCP

            if (k == 34) { // 2) After CCP loop invariant -> triggers loop unswitching
                iFld = 3;

            } else {
                iFld = iArr2[i]; // 3) After loop unswitching, triggers loop peeling again.
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:LoopMaxUnroll=0
    static void testPeelingThenUnswitchingThenPeelingThenPreMainPost() {
        int zero = 34;
        int limit = 2;

        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        for (int i = 5; i > five; i -= 2) {
            iFld = iArr[i];
            if (iFld == i) {
                fFld = 324;
            }

            if (flag) { // 1) Triggers loop peeling
                return;
            }

            int k = iFld2 + i * zero; // loop variant before CCP

            if (k == 34) { // 2) After CCP loop invariant -> triggers loop unswitching
                iFld = 3;

            } else {
                iFld = iArr2[i]; // 3) After loop unswitching, triggers loop peeling again, then pre/main/post
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static void testPeelingThenPreMainPost() {
        int three = 0;
        int limit = 2;
        long l1 = 34L;
        long l2 = 35L;

        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            three = 3;
        }

        for (int i = 3; i > five; i -= 2) {
            iFld = iArr[i];
            if (iFld == i) {
                iFld = 34;
            }

            if (i > three) {
                // DivLs add 30 to the loop body count and we hit LoopUnrollLimit.
                // After CCP, these statements are folded away and we can unroll this loop.
                l1 /= lFld;
                l2 /= lFld;
            }

            if (flag) {
                return;
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static void testUnswitchingThenPeelingThenPreMainPost() {
        int three = 0;
        int limit = 2;
        long l1 = 34L;
        long l2 = 35L;

        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            three = 3;
        }

        for (int i = 3; i > five; i -= 2) {
            iFld = iArr[i];
            if (iFld == i) {
                iFld = 34;
            }

            if (i > three) {
                // DivLs add 30 to the loop body count and we hit LoopUnrollLimit.
                // After CCP, these statements are folded away and we can unroll this loop.
                l1 /= lFld;
                l2 /= lFld;
            }

            if (flag2) {
                iFld2 = 34;
            }

            if (flag) {
                return;
            }
        }
    }

      // -XX:-UseProfiledLoopPredicate -Xcomp -XX:CompileCommand=compileonly,Test*::test*
    static void testWithPartialPeelingFirst() {
        int i = 3;

        if (i > five) {
            while (true) {

                // Found as loop head in ciTypeFlow, but both path inside loop -> head not cloned.
                // As a result, this head has the safepoint as backedge instead of the loop exit test
                // and we cannot create a counted loop (yet). We first need to partial peel.
                if (flag) {
                }

                // Loop exit test.
                if (i <= five) {
                    break;
                }
                // <-- Partial Peeling CUT -->
                // Safepoint
                iFld = iArr[i];
                if (iFld == i) {
                    fFld = 324;
                }
                i--;
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:LoopUnrollLimit=40
    static void testPeelMainLoopAfterUnrollingThenPreMainPostThenUnrolling() {
        int zero = 34;
        int limit = 2;

        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        // 1) Pre/Main/Post
        // 2) Unroll
        // 3) Peel main loop
        // 4) Pre/Main/Post peeled main loop
        // 5) Unroll new main loop
        for (int i = 13; i > five; i -= 2) {
            iFld = iArr[i];
            if (iFld == i) {
                fFld = 324;
            }
            if (i < 13) { // Always true and folded in main loop because of executing pre-loop at least once -> i = [min_short..11]

                int k = iFld2 + i * zero; // Loop variant before CCP
                if (k  == 40) { // After CCP: Loop Invariant -> Triggers Loop Peeling of main loop
                    return;
                }
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:LoopMaxUnroll=2
    static void testPeelMainLoopAfterUnrollingThenPreMainPost() {
        int zero = 34;
        int limit = 2;

        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        for (int i = 9; i > five; i -= 2) {
            iFld = iArr[i];
            if (iFld == i) {
                fFld = 324;
            }
            if (i < 9) { // Always true and folded in main loop because of executing pre-loop at least once -> i = [min_short..7]
                int k = iFld2 + i * zero; // Loop variant before CCP
                if (k  == 40) { // 2) After CCP: Loop Invariant -> Triggers Loop Peeling of main loop
                    return;
                } else {
                    iFld3 = iArr2[i]; // After Peeling Main Loop: Check can be eliminated with Range Check Elimination -> 3) apply Pre/Main/Post and then 4) Range Check Elimination
                }
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:LoopMaxUnroll=2
    static void testPeelMainLoopAfterUnrolling2() {
        int zero = 34;
        int limit = 2;

        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        for (int i = 7; i > five; i -= 2) {
            iFld = iArr[i];
            if (iFld == i) {
                fFld = 324;
            }
            if (i < 7) { // Always true and folded in main loop because of executing pre-loop at least once -> i = [min_short..5]

                int k = iFld2 + i * zero; // Loop variant before CCP
                if (k  == 40) { // 2) After CCP: Loop Invariant -> Triggers Loop Peeling of main loop
                    return;
                }

            }
        }
    }

    // Corresponds to JDK-8305428.
    // -Xbatch -XX:CompileCommand=compileonly,Test*::*
    public static void testTemplateAssertionPredicateNotRemovedHalt() {
        int one = 34;

        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            one = 1;
        }

        int i = 80;
        // 1) Found to be a counted loop with a limit between 1 and 34 (C2 only knows after CCP that 'one'
        //    is the constant 1).
        // 2b) Two Hoisted Range Check Predicates.
        //     Two Template Assertion Predicates but triggering this bug, we only need to focus on one:
        //         Init Value: OpaqueLoopInit(0) <u iArrShort.length
        // 4) After CCP, we know that this loop only runs for one iteration. The backedge is never taken
        //    and the CountedLoopNode can be folded away during IGVN.
        //    The Parse Predicates together with the Template Assertion Predicate from 2b) now end up
        //    above the partially peeled inner loop.
        for (int j = 0; j < one; j++) {
            // 3) Apply Partial Peeling which gives us the following loop:
            //      for (i = 80; i >= 5; i--) { fFld += 34; }
            //    Note that this loop does not have any Parse Predicates above its loop entry.
            // 5) This loop is converted to a counted loop.
            // 6) We pre-main-post this loop and update the Template Assertion Predicate for the main-loop
            //    The problem now is, that we use the init value of this loop which is completely
            //    unrelated to the init value of the outer loop for which this Tempalte Asseriton Predicate
            //    was originally created! We get the following wrong Template Assertion Predicate for the
            //    init value:
            //        OpaqueLoopInit(79) <u iArrShort.length
            //    From that we create a wrong Initialized Assertion Predicate:
            //        79 <u iArrShort.length
            //    During runtime, we know the length:
            //        79 <u iArrShort.length = 10
            //    And the Initialized Assertion Predidate fails. We execute the corresponding Halt node
            //    and the VM crashes.
            while (true) {

                // Found as loop head in ciTypeFlow, but both paths inside loop -> head not cloned.
                // As a result, this head has the safepoint as backedge instead of the loop exit test
                // and we cannot create a counted loop (yet). We first need to partial peel.
                if (flag) {
                }

                // Loop exit test.
                if (i < 5) {
                    break;
                }
                // <-- Partial Peeling CUT -->
                // Safepoint
                fFld += 34; // Make sure loop not empty.
                i--;
            }
            // 2a) Loop Predication hoists this check out of the loop with two Hoisted Range Check
            //     Predicates and two Template Assertion Predicates at 2b).
            iArrShort[j] = 3;
        }
    }

    // Corresponds to JDK-8305428 but with different manifestation (malformed graph).
    // -Xbatch -XX:CompileCommand=compileonly,Test*::*
    public static void testTemplateAssertionPredicateNotRemovedMalformedGraph() {
        int zero = 34;

        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 1;
        }

        int i = 80;
        for (int j = 0; j < zero; j++) {
            while (true) {

                // Found as loop head in ciTypeFlow, but both paths inside loop -> head not cloned.
                // As a result, this head has the safepoint as backedge instead of the loop exit test
                // and we cannot create a counted loop (yet). We first need to partial peel.
                if (flag) {
                }

                // Loop exit test.
                if (i < -5) {
                    break;
                }
                // <-- Partial Peeling CUT -->
                // Safepoint
                fFld = iArr2[i+5];
                i--;
            }
            iArr[j] = 3;
        }
    }

    /*
     * Some tests catching some issues while adding the new implementation.
     */

    // Initialized Assertion Predicate with ConI as bool node is not recognized, and we miss to remove a Template
    // Assertion Predicate from which we later create a wrong Initialized Assertion Predicate (for wrong loop).
    static void testDyingInitializedAssertionPredicate() {
        boolean b = false;
        int i4, i6, i7 = 14, i8, iArr[][] = new int[10][10];
        for (int i = 0; i < iArr.length; i++) {
            inline(iArr[i]);
        }
        for (i4 = 7; i4 < 10; i4++) {
            iArr2[1] += 5;
        }
        for (i6 = 100; i6 > 4; --i6) {
            i8 = 1;
            while (++i8 < 6) {
                sArr[i8] = 3;
                i7 += i8 + i8;
                iArr2[i8] -= 34;
            }
        }
    }

    public static void inline(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = 4;
        }
    }

    static void testDyingRuntimePredicate() {
        int zero = 34;
        int[] iArrLoc = new int[100];

        int limit = 2;
        int loopInit = -10;
        int four = -10;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
            loopInit = 99;
            four = 4;
        }

        // Template + Hoisted Range Check Predicate for iArr[i]. Hoisted Invariant Check Predicate IC for iArr3[index].
        // After CCP: ConI for IC and uncommon proj already killed -> IGVN will fold this away. But Predicate logic
        // need to still recognize this predicate to find the Template above to kill it. If we don't do it, then it
        // will end up at loop below and peeling will clone the template and create a completely wrong Initialized
        // Assertion Predicate, killing some parts of the graph and leaving us with a broken graph.
        for (int i = loopInit; i < 100; i++) {
            iArr[i] = 34;
            iArrLoc[four] = 34;
        }

        int i = -10;
        while (true) {

            // Found as loop head in ciTypeFlow, but both paths inside loop -> head not cloned.
            // As a result, this head has the safepoint as backedge instead of the loop exit test
            // and we cannot create a counted loop (yet). We first need to partial peel.
            if (zero * i == 34) {
                iFld2 = 23;
            } else {
                iFld = 2;
            }

            // Loop exit test.
            if (i >= -2) {
                break;
            }
            // <-- Partial Peeling CUT -->
            // Safepoint
            if (zero * i + five == 0) {
                return;
            }
            iFld2 = 34;
            i++;
        }
    }

    static void testDyingNegatedRuntimePredicate() {
        int zero = 34;
        int[] iArrLoc = new int[100];

        int limit = 2;
        int loopInit = -10;
        int four = -10;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
            loopInit = 99;
            four = 4;
        }

        // Template + Hoisted Range Check Predicate for iArr[i]. Hoisted Invariant Check Predicate IC for iArr3[index].
        // After CCP: ConI for IC and uncommon proj already killed -> IGVN will fold this away. But Predicate logic
        // need to still recognize this predicate to find the Template above to kill it. If we don't do it, then it
        // will end up at loop below and peeling will clone the template and create a completely wrong Initialized
        // Assertion Predicate, killing some parts of the graph and leaving us with a broken graph.
        for (int i = loopInit; i < 100; i++) {
            iArr[i] = 34;
            if (-3 > loopInit) {
                // Negated Hoisted Invariant Check Predicate.
                iArrLoc[101] = 34; // Always out of bounds and will be a range_check trap in the graph.
            }
        }

        int i = -10;
        while (true) {

            // Found as loop head in ciTypeFlow, but both paths inside loop -> head not cloned.
            // As a result, this head has the safepoint as backedge instead of the loop exit test
            // and we cannot create a counted loop (yet). We first need to partial peel.
            if (zero * i == 34) {
                iFld2 = 23;
            } else {
                iFld = 2;
            }

            // Loop exit test.
            if (i >= -2) {
                break;
            }
            // <-- Partial Peeling CUT -->
            // Safepoint
            if (zero * i + five == 0) {
                return;
            }
            iFld2 = 34;
            i++;
        }
    }

    /*
     * Tests to verify correct data dependencies update when splitting loops for which we created Hoisted Predicates.
     * If they are not updated correctly, we could wrongly execute an out-of-bounds load resulting in a segfault.
     */

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:+StressGCM -XX:CompileCommand=dontinline,*::dontInline
    static void testDataUpdateUnswitchingPeelingUnroll() {
        long l1 = 34L;
        long l2 = 566L;

        int hundred = 0;
        for (int i = 0; i < 8; i++) {
            if ((i % 2) == 0) {
                hundred = 100;
            }
        }

        Foo[] fooArr;
        int limit;
        if (flag) {
            limit = 3;
            fooArr = new Foo[20000001];
        } else {
            limit = 5;
            fooArr = new Foo[40000001];
        }
        for (int i = 0; i < limit; i++) {
            fooArr[10000000 * i] = foo;
        }
        // 10) This loop is not optimized in any way because we have a call inside the loop. This loop is only required
        //    to trigger a crash.
        // 11) During GCM with StressGCM, we could schedule the LoadN from the main loop before checking if we should
        //     enter the main loop. When 'flag' is true, we only have an array of size 20000001. We then perform
        //     the LoadN[3*10000000] and crash when the memory is unmapped.
        for (float f = 0; f < 1.6f; f += 0.5f) {
            // 2) Loop is unswitched
            // 4) Both loop are peeled (we focus on one of those since both are almost identical except for the
            //    unswitched condition):
            //      Peeled iteration [i = 0]
            //      Loop [i = 1..4, stride = 1]
            // 5) Loop unroll policy now returns true.
            //    Peeled iteration [i = 0]
            //    - Loop is pre-main-posted
            //        Loop-pre[i = 1..4, stride = 1]
            //        Loop-main[i = 2..4, stride = 1]
            //        Loop-post[i = 2..4, stride = 1]
            //    - Loop is unrolled once
            //        Loop-pre[i = 1..4, stride = 1]
            //        Loop-main[i = 2..4, stride = 2]
            //        Loop-post[i = 2..4, stride = 1]
            // 6) During IGVN, we find that the backedge is never taken for main loop (we would over-iteratre) and it
            //    collapses to a single iteration.
            // 7) After loop opts, the pre-loop is removed.
            for (int i = 0; i < limit; i++) {
                // 1) Hoisted with a Hoisted Range Check Predicate
                // 8) The 'i = 1' value is propagated to the single main loop iteration and we have the following
                //    fixed-index accesses:
                //      LoadN[2*10000000];
                //      LoadN[3*10000000];
                // 9) Without explicitly pinning the LoadN from the main loop at the main loop entry (i.e. below the
                //    zero trip guard), they are still pinned below the Hoisted Range Check Predicate before the loop.
                fooArr[i * 10000000].iFld += 34;
                if (flagFalse) {
                    return; //Enables peeling
                }

                // 3) hundred only known to be 100 after second loop opts -> becomes dead.
                //    The expense statements are folded away and we can unroll this loop because we are below the
                //    LoopUnrollLimit again.
                if (i > hundred) {
                    // DivLs add 30 to the loop body count and we hit LoopUnrollLimit -> loop not unrolled
                    l1 /= lFld;
                    l2 /= lFld;
                }

                if (flagFalse2) { // Loop invariant -> enables Loop Unswitching
                    iFld += 34;
                }


            }
            dontInline(); // Ensure that float loop is not peeled
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:+StressGCM -XX:CompileCommand=dontinline,*::dontInline
    static void testDataUpdateUnswitchUnroll() {
        long l1 = 34L;
        long l2 = 566L;

        int hundred = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                hundred = 100;
            }
        }

        Foo[] fooArr;
        int limit;
        if (flag) {
            limit = 2;
            fooArr = new Foo[10000001];
        } else {
            limit = 3;
            fooArr = new Foo[20000001];
        }
        for (int i = 0; i < limit; i++) {
            fooArr[10000000 * i] = foo;
        }
        // 9) This loop is not optimized in any way because we have a call inside the loop. This loop is only required
        //    to trigger a crash.
        // 10) During GCM with StressGCM, we could schedule the LoadN from the main loop before checking if we should
        //     enter the main loop. When 'flag' is true, we only have an array of size 10000001. We then perform
        //     the LoadN[2*10000000] and crash when the memory is unmapped.
        for (float f = 0; f < 1.6f; f += 0.5f) {
            // 2) Loop is unswitched
            // 4) Loop unroll policy now returns true.
            //    - Loop is pre-main-posted
            //        Loop-pre[i = 0..2, stride = 1]
            //        Loop-main[i = 1..2, stride = 1]
            //        Loop-post[i = 1..2, stride = 1]
            //    - Loop is unrolled once
            //        Loop-pre[i = 0..2, stride = 1]
            //        Loop-main[i = 1..2, stride = 2]
            //        Loop-post[i = 1..2, stride = 1]
            // 5) During IGVN, we find that the backedge is never taken for main loop and it collapses to a single
            //    iteration.
            // 6) After loop opts, the pre-loop is removed.
            for (int i = 0; i < limit; i++) {
                // 1) Hoisted with a Hoisted Range Check Predicate
                // 7) The 'i = 1' value is propagated to the single main loop iteration and we have the following
                //    fixed-index accesses:
                //      LoadN[1*10000000];
                //      LoadN[2*10000000];
                // 8) Without explicitly pinning the LoadN from the main loop at the main loop entry (i.e. below the
                //    zero trip guard), they are still pinned below the Hoisted Range Check Predicate before the loop.
                fooArr[i * 10000000].iFld += 34;

                // 3) hundred only known to be 100 after second loop opts -> becomes dead.
                //    The expense statements are folded away and we can unroll this loop because we are below the
                //    LoopUnrollLimit again.
                if (i > hundred) {
                    // DivLs add 30 to the loop body count and we hit LoopUnrollLimit -> loop not unrolled
                    l1 /= lFld;
                    l2 /= lFld;
                }

                if (flag) { // invariant -> enabled Loop Unswitching
                    iFld = 34;
                }
            }
            dontInline(); // Ensure that float loop is not peeled
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:+StressGCM -XX:CompileCommand=dontinline,*::dontInline
    static void testDataUpdateUnroll() {
        long l1 = 34L;
        long l2 = 566L;

        int hundred = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                hundred = 100;
            }
        }

        Foo[] fooArr;
        int limit;
        if (flag) {
            limit = 2;
            fooArr = new Foo[10000001];
        } else {
            limit = 3;
            fooArr = new Foo[20000001];
        }
        for (int i = 0; i < limit; i++) {
            fooArr[10000000 * i] = foo;
        }
        // 8) This loop is not optimized in any way because we have a call inside the loop. This loop is only required
        //    to trigger a crash.
        // 9) During GCM with StressGCM, we could schedule the LoadN from the main loop before checking if we should
        //    enter the main loop. When 'flag' is true, we only have an array of size 10000001. We then perform
        //    the LoadN[2*10000000] and crash when the memory is unmapped.
        for (float f = 0; f < 1.6f; f += 0.5f) {
            // 3) Loop unroll policy now returns true.
            //    - Loop is pre-main-posted
            //        Loop-pre[i = 0..2, stride = 1]
            //        Loop-main[i = 1..2, stride = 1]
            //        Loop-post[i = 1..2, stride = 1]
            //    - Loop is unrolled once
            //        Loop-pre[i = 0..2, stride = 1]
            //        Loop-main[i = 1..2, stride = 2]
            //        Loop-post[i = 1..2, stride = 1]
            // 4) During IGVN, we find that the backedge is never taken for main loop and it collapses to a single
            //    iteration.
            // 5) After loop opts, the pre-loop is removed.
            for (int i = 0; i < limit; i++) {
                // 1) Hoisted with a Hoisted Range Check Predicate
                // 6) The 'i = 1' value is propagated to the single main loop iteration and we have the following
                //    fixed-index accesses:
                //      LoadN[1*10000000];
                //      LoadN[2*10000000];
                // 7) Without explicitly pinning the LoadN from the main loop at the main loop entry (i.e. below the
                //    zero trip guard), they are still pinned below the Hoisted Range Check Predicate before the loop.
                fooArr[i * 10000000].iFld += 34;

                // 2) hundred only known to be 100 after second loop opts -> becomes dead.
                //    The expense statements are folded away and we can unroll this loop because we are below the
                //    LoopUnrollLimit again.
                if (i > hundred) {
                    // DivLs add 30 to the loop body count and we hit LoopUnrollLimit -> loop not unrolled
                    l1 /= lFld;
                    l2 /= lFld;
                }
            }
            dontInline(); // Ensure that float loop is not peeled
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::test* -XX:+StressGCM -XX:CompileCommand=dontinline,*::dontInline
    static void testDataUpdatePeelingUnroll() {
        long l1 = 34L;
        long l2 = 566L;

        int hundred = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                hundred = 100;
            }
        }

        Foo[] fooArr;
        int limit;
        if (flag) {
            limit = 3;
            fooArr = new Foo[20000001];
        } else {
            limit = 5;
            fooArr = new Foo[40000001];
        }
        for (int i = 0; i < limit; i++) {
            fooArr[10000000 * i] = foo;
        }
        // 9) This loop is not optimized in any way because we have a call inside the loop. This loop is only required
        //    to trigger a crash.
        // 10) During GCM with StressGCM, we could schedule the LoadN from the main loop before checking if we should
        //     enter the main loop. When 'flag' is true, we only have an array of size 20000001. We then perform
        //     the LoadN[3*10000000] and crash when the memory is unmapped.
        for (float f = 0; f < 1.6f; f += 0.5f) {
            // 2) Loop is peeled:
            //      Peeled iteration [i = 0]
            //      Loop [i = 1..4, stride = 1]
            // 4) Loop unroll policy now returns true.
            //    Peeled iteration [i = 0]
            //    - Loop is pre-main-posted
            //        Loop-pre[i = 1..4, stride = 1]
            //        Loop-main[i = 2..4, stride = 1]
            //        Loop-post[i = 2..4, stride = 1]
            //    - Loop is unrolled once
            //        Loop-pre[i = 1..4, stride = 1]
            //        Loop-main[i = 2..4, stride = 2]
            //        Loop-post[i = 2..4, stride = 1]
            // 5) During IGVN, we find that the backedge is never taken for main loop (we would over-iteratre) and it
            //    collapses to a single iteration.
            // 6) After loop opts, the pre-loop is removed.
            for (int i = 0; i < limit; i++) {
                // 1) Hoisted with a Hoisted Range Check Predicate
                // 7) The 'i = 1' value is propagated to the single main loop iteration and we have the following
                //    fixed-index accesses:
                //      LoadN[2*10000000];
                //      LoadN[3*10000000];
                // 8) Without explicitly pinning the LoadN from the main loop at the main loop entry (i.e. below the
                //    zero trip guard), they are still pinned below the Hoisted Range Check Predicate before the loop.
                fooArr[i * 10000000].iFld += 34;
                if (flagFalse) {
                    return; // Enables peeling
                }

                // 3) hundred only known to be 100 after second loop opts -> becomes dead.
                //    The expense statements are folded away and we can unroll this loop because we are below the
                //    LoopUnrollLimit again.
                if (i > hundred) {
                    // DivLs add 30 to the loop body count and we hit LoopUnrollLimit -> loop not unrolled
                    l1 /= lFld;
                    l2 /= lFld;
                }
            }
            dontInline(); // Ensure that float loop is not peeled
        }
    }


    // -Xcomp -XX:LoopMaxUnroll=0 -XX:+StressGCM -XX:CompileCommand=compileonly,Test*::*
    private static void testPeelingThreeTimesDataUpdate() {
        Foo[] fooArr = new Foo[fooArrSize];
        for (int i = 0; i < two; i++) {
            fooArr[10000000 * i] = foo;
        }
        int x = 0;

        // 2) The Hoisted Range Check Predicate is accompanied by two Template Assertion Predicates. The LoadN node,
        //    previously pinned at the hoisted range check, is now pinned at the Template Assertion Predicate. Note
        //    that the LoadN is still inside the loop body.
        // 3) The loop is now peeled 3 times which also peels 3 loads from 'fooArr' out of the loop:
        //       // Peeled section from 1st Loop Peeling
        //       ...
        //       LoadN[0]
        //       ...
        //       <loop entry guard>
        //       // Peeled section from 2nd Loop Peeling
        //       ...
        //       LoadN[10000000]
        //       Initialized Assertion Predicate (***)
        //       <loop entry guard>
        //       // Peeled section from 3rd Loop Peeling
        //       ...
        //       LoadN[20000000]
        //       ...
        //       Initialized Assertion Predicate
        //       <loop entry guard>
        //       Template Assertion Predicate
        //       Initialized Assertion Predicate
        // Loop:
        //   LoadN[i*10000000]
        //
        // To avoid that the peeled LoadN nodes float above the corresponding loop entry guards, we need to pin them
        // below. That is done by updating the dependency of the peeled LoadN to the new Template Assertion Predicate.
        // This is currently broken: We wrongly set the dependency to the Initialized Assertion Predicate instead of the
        // Template Assertion Predicate. We can then no longer find the dependency and miss to update it in the next
        // Loop Peeling application. As a result, all the LoadN pile up at the originally added Initialized Assertion
        // Predicate of the first Loop Peeling application at (***).
        //
        // With GCM, we could schedule LoadN[20000000] at (***), before the loop entry corresponding loop entry guard
        // for this load. We then crash during runtime because we are accessing an out-of-range index. The fix is to
        // properly update the data dependencies to the Template Assertion Predicates and not the Initialized Assertion
        // Predicates.
        for (int i = 0; i < two; i++) {
            // 1) Hoisted with a Hoisted Range Check Predicate
            x += fooArr[i * 10000000].iFld;

            if (iFld2 == 4) {
                return;
            }

            if (i > 0 && iFld2 == 3) {
                iFld2 = 42;
                return;
            }

            if (i > 1 && iFld2 == 2) {
                return;
            }
        }
    }

    /*
     * Tests collected in JBS and duplicated issues
     */

    // -Xbatch -XX:CompileCommand=compileonly,Test*::*
    static void test8305428() {
        int j = 1;
        do {
            for (int k = 270; k > 1; --k) {
                iFld++;
            }

            switch (j) {
                case 1:
                    switch (92) {
                        case 92:
                            flag = flag;
                    }
                case 2:
                    iArr[j] = 3;
            }
        } while (++j < 100);
    }

    // -Xbatch -XX:CompileCommand=compileonly,Test*::*
    static void test8305428No2() {
        int i = 1;
        do {
            for (int j = 103; j > 1; --j) {
                iArr[i] = iArr[j / 34];
            }
            for (int j = 103; j > 4; j -= 3) {
                switch (i % 9) {
                    case 0:
                    case 2:
                    case 3:
                        iArr[i - 1] = 34;
                    case 8:
                }
            }
        } while (++i < 99);
    }

    static void test8320920() {
        int i = 1;
        do {
            for (int j = 83; j > i; j--) {
                iFld = 3;
            }
            for (int j = 5; j < 83; j++) {
                for (int k = i; k < 2; k++)
                    ;
            }
            iArr3[i - 1] = 34;
        } while (++i < 300);
    }

    static void test8332501() {
        int i = 1;
        do {
            for (int j = 108; j > 1; j -= 2) {
                fFld += j;
            }
            for (int j = 3; 108 > j; j++) {
                for (int k = 2; k > i; --k) {
                }
            }
            iArr[i] = 34;
        } while (++i < 150);
    }

        // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static void test8288981() {
        int x = 1;
        // Sufficiently many iterations to trigger OSR
        for (int j = 0; j < 50_000; j++) {
            for (int i = 1; i > x; --i) {
                float v = fArr[0] + fFld;
                fArr2D[i + 1][x] = v;
                iFld += v;
            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static void test8292507() {
        int i = iFld, j, iArr[] = new int[40];
        while (i > 0) {
            for (j = i; 1 > j; ++j) {
                try {
                    iArr[j] = 0;
                    iArr[1] = iFld = 0;
                } catch (ArithmeticException a_e) {
                }
                switch (i) {
                    case 4:
                    case 43:
                        iFld2 = j;
                }

            }
        }
    }

    // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static void test8307131() {
        int i21 = 6, i, i23 = 3, y, iArr2[] = new int[40];
        for (i = 50000; 3 < i; i--) {
            for (y = 2; y > i; y--) {
                try {
                    i21 = i23 / 416 / iArr2[y];
                } catch (ArithmeticException a_e) {
                }
                i23 -= 3;
            }
        }
        i21 -= 2;
    }

    void test8296077() {
        int i4 = 4, iArr1[] = new int[10];
        float f2;
        for (f2 = 7; f2 > 4; --f2) {}

        float f4 = five;
        while (f4 < 50000) {
            for (int i7 = (int) f4; 1 > i7; i7++) {
                iArr1[i7] = 5;
                if (i4 != 0) {
                    return;
                }
                byFldVol = 2;
                try {
                    iArr1[(int) f4] = i4 = iArr1[(int) f4 + 1] % iArr1[(int) f2];
                } catch (ArithmeticException a_e) {
                }
            }
            f4++;
        }
    }

    static void test8308392No1() {
        int i10, i16;
        try {
            for (i10 = 61; i10 < 50000 ; i10++) {
                for (i16 = 2; i16 > i10; i16--) {
                    sFld *= iArr2D[i16][i16] = byFld *= sFld;
                }
            }
        } catch (NegativeArraySizeException exc3) {
        }
    }

    static void test8308392No2() {
        try {
            int j, k, i19 = 8;

            for (int i = 0; i < 10; i++) {
                for (j = 2; j < 3; ) {
                    if (flagTrue) {
                        iFld++;
                        iFld2 = 34 / iFld; // Will eventually divide by zero and break infinite loop.
                    }
                    for (k = 2; k > j; --k) {
                        i19 *= fArr2D[k][j] += i;
                    }
                }
            }

        } catch (ArithmeticException e) {
            // Expected
        }
    }
    static void test8308392No3() {
        int i18, i19, i21, i22 = 1, iArr2[][] = new int[40][];
        double dArr[] = new double[40];

        i18 = 1;
        for (i19 = 5; i19 < 50000; i19++) {
            for (i21 = i18; i21 < 4; ++i21) {
                switch (i19) {
                    case 4:
                        iArr2[i21 - 1][i18] = 3;
                        try {
                            iFld = 2 % iFld;
                            iFld = i22;
                        } catch (ArithmeticException a_e) {
                        }
                        break;
                    case 45:
                        i22 += dArr[i22];
                }
            }
        }
    }

    static void test8308392No4() {
        int i20, i22 = 6, i25;
        for (i20 = 50000; i20 > 3; i20--) {
            for (i25 = 1; i25 > i22; i25--) {
                iArr2D[i25 + 1][i22] += fFld -= iFld;
            }
        }
    }

    static void test8308392No5() {
        float f1;
        int i20, i23, i25 = 5, i26 = 4, i27;
        long lArr[][] = new long[10][10];
        for (f1 = 40; f1 > 3; --f1) {
            for (i20 = 2; 11 > i20; i20++) {
                for (i23 = 1; i23 < 11; ) {
                    i23++;
                    for (i27 = (int) f1; i27 < 1; ++i27) {
                        iFld = 3;
                        lArr[i27][i25] = 5;
                        if (flag) {
                            i26 += i25;
                        }
                    }
                }
            }
        }
    }

    static void test8308392No6() {
        int i, i1, i2, i23 = 7, i24;
        double dArr1[][] = new double[10][];
        boolean bArr[] = new boolean[10];
        for (i = 9; i < 88; i++) {
            i2 = 1;
            do {
                i1 = Short.reverseBytes((short) 0);
                for (i24 = 1; i2 < i24; --i24) {
                    i1 %= dArr1[i24 + 1][i];
                    switch (i23) {
                        case 0:
                            bArr[i] = false;
                    }
                }
                i2++;
            } while (i2 < 50000);
        }
    }

    static void test8308392No7() {
        int i16 = 2, i17 = 1, i18, i20, i21, i23;
        double d2 = 86.53938;
        long lArr[][] = new long[10][];
        for (i18 = 1; i18 < 10; i18++) {
            i20 = 1;
            while (i20 < 5000) {
                for (i21 = i23 = 1; i23 > i20; --i23) {
                    d2 *= i16 >>= lArr[i23 + 1][i20] >>= i17;
                }
                i20++;
            }
        }
    }
    static void test8308392No8() {
        int i21, i22, i25 = 1, i26 = 032, i28;
        i21 = iFld;
        while (--i21 > 0) {
            for (i22 = 2; i22 < 71; i22++) {
                for (i28 = 2; i28 > i21; --i28) {
                    i25 %= i26;
                    iArr2D[i28][1] ^= 5;
                }
            }
            i21--;
        }
    }

    static void runTest8308392No9() {
        try {
            test8308392No9();
        } catch (ArithmeticException | ArrayIndexOutOfBoundsException e) {
            // Expected.
        }
    }

    static void test8308392No9() {
        for (int i20 = 60; ; i20--) {
            for (int i22 = 2; i22 > i20; --i22) {
                fFld += 5;
                iFld = iFld / 9 / iArr[i22];
            }
        }
    }

    static void test8308392No10() {
        int i14, i16 = -27148, i18, i21;
        for (i14 = 21; i16 < 9; ++i16) {
            for (i18 = 2; i14 < i18; i18--) {
                iArr2D[i18][i18] -= lFld = i18;
            }
            for (i21 = 1; i21 < 2; i21++) {}
        }
    }

    // -Xcomp -XX:-TieredCompilation
    static void test8308504() {
        int iArr[][] = new int[400][400];
        for (int i16 = 294; i16 > 6; --i16) {
            for (int i21 = 1; i21 < 87; ++i21) {
                for (int i23 = 2; i23 > i21; i23--) {
                    switch (118) {
                        case 118:
                            iFld *= 3.4f;
                            iArr[i23][i16] *= 5;
                    }
                }
            }
        }
        System.nanoTime(); // Unloaded, triggers deopt
    }

    // -Xcomp -XX:-TieredCompilation
    static void test8308504No2() {
        int iArr[][] = new int[400][400];
        for (int i = 294; i > 6; i--) {
            for (int j = 1; j < 87; j++) {
                for (int k = 2; k > j; k--) {
                    iFld *= 3.4f;
                    iArr[k][i] *= 5;
                }
            }
        }
        Math.pow(2, 3); // Unloaded, triggers deopt
    }

    static void testSplitIfCloneDownWithOpaqueAssertionPredicate() {
        int p = 0, j;
        if (flag) {
            iArr[3] = 3;
            dontInline();
        }
        int i = 1;
        while (++i < 4) {
            if (flag) {
                p = 8;
            }
            iArr[i - 1] = 4;
            for (j = 1; j < 3; ++j) {
                iArr[j] = 34;
            }
        }
        long n = p;
    }

    static void testTrySplitUpNonOpaqueExpressionNode() {
        int zero = 34;
        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            flag = !flag;
            flag2 = !flag2;
            iFld = flag ? 50 : -50;
            zero = 0;
        }

        for (int t = 0; t < 100; t++) {
            // 4) Graph looks like this now:
            // Split If is applied because the graph has the following shape:
            //
            //        Region  # **REGION**
            //          |
            //   NULL  Phi[new int[100], iArr2]  # 'iArr'
            //     \  /
            //     CmpU   \
            //      |     |
            //     Bool   | # Hoisted NullCheck from 2b)
            //      |     |
            //     If     /
            //
            //
            // We find that we can split the If through the Region because we have a Phi input for the condition CmpU
            //
            // 5) We apply Split-If to split the If through the region. This requires that we also handle all the Phi nodes
            //    belonging to the region to split through. We need to empty the block ('--- BLOCK start/end ---') by
            //    checking all users of the Phi* node. This is done removing all user of the
            //    phi nodes belonging to the Region to split the If through. phi nodes.
            //    This requires that we split up all users of the phi recursively through the phi. The new users of the Phis
            //    are then revisited again since Split-If is applied iteratively for all nodes. We stop the "splitting up"
            //    when a user of a Phi node has its get_ctrl() at a different node than the region to split through.
            //
            //    We have the following graph before Split If:
            //
            //        Region  # **REGION**
            //                 |
            //                Phi
            //                 |
            //               LoadUB  # 'flag2'loaded either from the merged memory state of the region
            //                 |
            //                CmpI
            //                 |
            //                Bool
            //                 |
            //               CmoveI  # From 1)
            //                 |
            //              ConvI2L
            //                 |
            //                AddL   # First node part of Template Assertion Predicate expression of 3b).
            //                 |
            //               CmpUL
            //                 |
            //  OpaqueTemplateAssertionPredicate
            //
            // 6) We start applying Split If and iteratively split users of Phi nodes up. We find that all nodes including
            //    the AddL, can be split through the new phis because they have their get_ctrl() set to the REGION.
            //    Why isnt the control of the AddL not set to the latest control (which we usually do when having the
            //    same loop depth) at 3b) where the Template Assertion Predicate Expression node belongs to?
            //    The reason is that during build_loop_late_post_work(), we skip all predicates to not interfere with
            //    Loop Predication, including the NullCheck from 2b) . Thus, we move late control up to REGION because the
            //    if/else is already removed with a CMove in 1).
            //
            // 7) When splitting the AddL node, have the following graph:
            //
             //     Region    # **REGION**
            //        |
            //        |   OpaqueLoopInit
            //        |        |
            //       Phi    ConvI2L
            //         \    /
            //          AddL
            //
            //    We find that it's part of a Template Assertion Predicate Expression. We do not want to split such a node
            //    to not introduce a Phi node within the expression which would mess with pattern matching to find the
            //    OpaqueLoop* nodes from the Template Assertion Predicate If. As a solution, we "clone down" the Template
            //    Assertion Predicate Expression by creating a clone of the entire Template Assertion Predicate Expression.
            //    We then feed the Phi for the new Region, after splitting the If, into the AddL as part of the
            //    Template Assertion Predicate Expression.
            //
            //    Note: When trying to split AddL, we first try to split its inputs, i.e. ConvI2L. But ConvI2L has its
            //          get_ctrl() outside of the outer "for (t = ...)" loop because the OpaqueLoopInit node has a constant
            //          as input and thus build_loop_late_post_work() will make sure that late control does not have a
            //          deeper nesting than early. In testTrySplitUpOpaqueLoopInit(), we remove the outer loop and
            //          therefore try to split up the OpaqueLoopInit node first which will initiate the "clone down".

            int[] iArr;
            if (flag) {
                iArr = new int[100];
            } else {
                dontInline();
                iArr = iArr2;
            }

            // **REGION**
            // - Phi[new int[100], iArr2] = Phi[DecodeN[int:100], DecodeN[n>=0]] # 'iArr'
            // - Phi[flag, flag]  # 'flag' -> once loaded from the if-branch and once from the else-branch related memory

            // --- BLOCK start ---

            // 1) Replaced with CMove:
            //     a = CMove(flag, 3, 4)
            //
            //    Note: Even though we run with -Xcomp where we don't know the frequency, we still cmove because of
            //          -XX:-BlockLayoutByFrequency
            int a;
            if (flag2) {
                a = 4;
            } else {
                a = 3;
            }
            // --- BLOCK end ---

            // 2b) Hoisted Check Predicate: NullCheck(iArr)
            // 3b) Hoisted Check Predicate: RangeCheck(iArr) + TAPs(iArr)
            for (int i = 0; i < 100; i++) {
                // 2a) Null check hoisted with a Hoisted Check Predicate -> put at 2b)
                // 3a) Range check hoisted with a Hoisted Check Predicate + Template Assertion Predicates -> put at 3b)
                iArr[i + a] = 34;
                // 8) After CCP, we find that this condition equals to "0 < iFld". Since this is loop-invariant and
                //    a loop-exit, we apply Loop Peeling which verifies that we do not have a Template Assertion
                //    Predicate with a Phi node.
                if (i * zero < iFld) {
                    return;
                }
            }
        }
    }

    // Same as test above but this time the "clone down" is initiated when trying to split up an OpaqueLoopInitNode.
    static void testTrySplitUpOpaqueLoopInit() {
            int zero = 34;
        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        // 4) Graph looks like this now:
        // Split If is applied because the graph has the following shape:
        //
        //        Region  # **REGION**
        //          |
        //   NULL  Phi[new int[100], iArr2]  # 'iArr'
        //     \  /
        //     CmpU   \
        //      |     |
        //     Bool   | # Hoisted NullCheck from 2b)
        //      |     |
        //     If     /
        //
        //
        // We find that we can split the If through the Region because we have a Phi input for the condition CmpU
        //
        // 5) We apply Split-If to split the If through the region. This requires that we also handle all the Phi nodes
        //    belonging to the region to split through. We need to empty the block ('--- BLOCK start/end ---') by
        //    checking all users of the Phi* node. This is done removing all user of the
        //    phi nodes belonging to the Region to split the If through. phi nodes.
        //    This requires that we split up all users of the phi recursively through the phi. The new users of the Phis
        //    are then revisited again since Split-If is applied iteratively for all nodes. We stop the "splitting up"
        //    when a user of a Phi node has its get_ctrl() at a different node than the region to split through.
        //
        //    We have the following graph before Split If:
        //
        //        Region  # **REGION**
        //                 |
        //                Phi
        //                 |
        //               LoadUB  # 'flag2'loaded either from the merged memory state of the region
        //                 |
        //                CmpI
        //                 |
        //                Bool
        //                 |
        //               CmoveI  # From 1)
        //                 |
        //              ConvI2L
        //                 |
        //                AddL   # First node part of Template Assertion Predicate expression of 3b).
        //                 |
        //               CmpUL
        //                 |
        //  OpaqueTemplateAssertionPredicate
        //
        // 6) We start applying Split If and iteratively split users of Phi nodes up. We find that all nodes including
        //    the AddL, can be split through the new phis because they have their get_ctrl() set to the REGION.
        //    Why isnt the control of the AddL not set to the latest control (which we usually do when having the
        //    same loop depth) at 3b) where the Template Assertion Predicate Expression node belongs to?
        //    The reason is that during build_loop_late_post_work(), we skip all predicates to not interfere with
        //    Loop Predication, including the NullCheck from 2b) . Thus, we move late control up to REGION because the
        //    if/else is already removed with a CMove in 1).
        //
        // 7) When splitting the AddL node, have the following graph:
        //
         //     Region    # **REGION**
        //        |
        //        |   OpaqueLoopInit
        //        |        |
        //       Phi    ConvI2L
        //         \    /
        //          AddL
        //
        //    We find that it's part of a Template Assertion Predicate Expression. We do not want to split such a node
        //    to not introduce a Phi node within the expression which would mess with pattern matching to find the
        //    OpaqueLoop* nodes from the Template Assertion Predicate If. As a solution, we "clone down" the Template
        //    Assertion Predicate Expression by creating a clone of the entire Template Assertion Predicate Expression.
        //    We then feed the Phi for the new Region, after splitting the If, into the AddL as part of the
        //    Template Assertion Predicate Expression.
        //
        //    Note: The difference to testTrySplitUpNonOpaqueExpressionNode() above is that when trying to split AddL,
        //          we first split up its inputs recursively if they also have get_ctrl() at the region to split through.
        //          This is the case for ConvI2L and OpaqueLoopInit. Therefore, the split of OpaqueLoopInit will
        //          initiate the "clone down".

        int[] iArr;
        if (flag) {
            iArr = new int[100];
        } else {
            dontInline();
            iArr = iArr2;
        }

        // **REGION**
        // - Phi[new int[100], iArr2] = Phi[DecodeN[int:100], DecodeN[n>=0]] # 'iArr'
        // - Phi[flag, flag]  # 'flag' -> once loaded from the if-branch and once from the else-branch related memory

        // --- BLOCK start ---

        // 1) Replaced with CMove:
        //     a = CMove(flag, 3, 4)
        //
        //    Note: Even though we run with -Xcomp where we don't know the frequency, we still cmove because of
        //          -XX:-BlockLayoutByFrequency
        int a;
        if (flag2) {
            a = 4;
        } else {
            a = 3;
        }
        // --- BLOCK end ---

        // 2b) Hoisted Check Predicate: NullCheck(iArr)
        // 3b) Hoisted Check Predicate: RangeCheck(iArr) + TAPs(iArr)
        for (int i = 0; i < 100; i++) {
            // 2a) Null check hoisted with a Hoisted Check Predicate -> put at 2b)
            // 3a) Range check hoisted with a Hoisted Check Predicate + Template Assertion Predicates -> put at 3b)
            iArr[i + a] = 34;
            // 8) After CCP, we find that this condition equals to "0 < iFld". Since this is loop-invariant and
            //    a loop-exit, we apply Loop Peeling which verifies that we do not have a Template Assertion
            //    Predicate with a Phi node.
            if (i * zero < iFld) {
                return;
            }
        }
    }

    static void testBackToBackLoopLimitCheckPredicate() {
        int i = 34;
        if (flag) {}
        while (i < 50) {
            i++;
        }
        for (int j = 0; j < 4; j++) {
            iArr[j] += 34;
        }
    }

   // -Xcomp -XX:CompileCommand=compileonly,Test*::*
    static int test8288941() {
        int e = 8, g = 5, j;
        int h = 1;
        while (++h < 100000) {
            for (j = 1; j > h; j--) {
                try {
                    iFld = 0;
                    g = iArr[1] / e;
                } catch (ArithmeticException ae) {
                }
                iArr[j + 1] = 4;
                if (e == 9) {
                    iFld2 = 3;
                }
            }
        }
        return g;
    }

    // JDK-8288941, -Xcomp -XX:CompileCommand=compileonly,Test::test
    static void testRemovingParsePredicatesThenMissingTemplates() {
        int one = 34;

        int limit = 2;
        for (; limit < 4; limit *= 2);
        for (int i = 2; i < limit; i++) {
            one = 1; // Only known to be 1 after CCP.
        }

        long l1 = 34L;
        long l2 = 566L;

        // 10) After IGVN, the StoreF has lost most of its out edges due to the removal of the MergeMems into the UCTs
        //     of the Parse Predicates.
        // 11) policy_maximally_unroll() now returns true and we can maximally unroll this loop.
        int j;
        for (j = 0; j < 4; j++) {
            // 1) StoreF has a huge number of MergeMem uses for the UCTs of all the loops further down. As a result,
            //    policy_maximally_unroll() will return false because est_loop_unroll_sz() -> est_loop_flow_merge_sz()
            //    counts all out edges to nodes outside the loop to estimate the cost of merging nodes when cloning the
            //    loop. This count is larger then the unroll limit and we do not apply MaxUnroll.
            // Note: This is a store to a float array to not interfere with the int array store in the next loop below.
            fArr[j] = 34;

            // 2) DivLs add 30 to the loop body count and we hit LoopUnrollLimit which avoids normal Loop unrolling and
            //    thus pre/main/post loop creation. Therefore, we currently cannot split this loop in any way.
            l1 /= lFldOne;
            l2 /= lFldOne;
        }


        long l3 = 34L;
        long l4 = 566L;
        // 13) Since the DivL nodes are now removed, the LoopUnrollLimit is not reached and we can finally pre/main/post
        //     and unroll this loop - with Template Assertion Predicates above but no more Parse Predicates!
        // 14) We currently do not clone/update/establish Template Assertion Predicates once Parse Predicates are removed.
        //     Therefore, we create pre/main/post loops without Template Assertion Predicates and without Initialized
        //     Assertion Predicates.
        int x = 0;
        for (int i = 1; i > five; i -= 2) {

            // 3)  Loop Predication will hoist the range check out of the loop together with a Template Assertion Predicate.
            // 15) After pre/main/post: the CastII for the array index get the new iv type [-1..-32767] and is replaced by
            //     top. Data dies but there is no Intialized Assertion Predicate above the main loop and we are left with
            //     a broken graph. We assert when running with -XX:+AbortVMOnCompilationFailure due to a malformed graph.
            int arrLoad = iArr[i];
            if (arrLoad == i) {
                iFld = 34;
            }

            // 4)  This check is non-loop invariant, so Loop Unswitching cannot be applied, either.
            // 12) After maximally unrolling the loop above, we know that j == 4 and thus "j+(i*-1) = 4+[-1..32767] < 3" is
            //     always false. The branch is cleaned up in the next IGVN round which also removes the expensive DivL nodes.
            if (j+(i*-1) < 3) {

                // 5) As above: We hit the LoopUnrollLimit which avoids normal Loop unrolling and thus pre/main/post loop
                //    creation. Therefore, we currently cannot split this loop in any way.
                l3 /= lFldOne;
                l4 /= lFldOne;
            }
        }

        // Many non-counted loops that are not optimized but all have Parse Predicates with UCTs + a safepoint in the loop body.
        // The StoreF from the loop above will be merged with a MergeMem into all these UCTs.
        //
        // 6) After CCP, we find that 'one' is 1. Therefore, all loops will only iterate for a single iteration and the loop
        //    backedges die. These are cleaned up in the next round of IGVN.
        // 7) In the next loop opts phase after CCP, we find that all these Parse Predicates above the loops are now useless.
        //    We mark them as such but will only clean them up in the next round of IGVN.
        // 8) Since we do not apply any major loop optimization like loop splitting, we remove the remaining Parse Predicates
        //    (see "PredicatesOff" when running with -XX:+TraceLoopOpts). We set major progress again to see if we can continue
        //    to apply more loop optimizations.
        // 9) IGVN runs and cleans up all the Parse Predicates and its UCTs.
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
        for (int i = 0; i < one; i++) {
           if (i < 2) {
               i++;
           }
        }
    }

    // Not inlined.
    static void dontInline() {
    }
}
