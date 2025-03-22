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
 * @bug 8288981 8350579
 * @summary Test all possible cases in which Assertion Predicates are required such that the graph is not left in a
 *          broken state to trigger assertions. Additional tests ensure the correctness of the implementation.
 *          All tests additionally -XX:+AbortVMOnCompilationFailure which would catch bad graphs as a result of missing
            or wrong Assertion Predicates where we simply bail out of C2 compilation.
 * @run main/othervm -Xbatch
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.TestAssertionPredicates::*
 *                   compiler.predicates.TestAssertionPredicates Xbatch
 */

/*
 * @test id=NoTieredCompilation
 * @bug 8288981 8350579

 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.TestAssertionPredicates::*
 *                   compiler.predicates.TestAssertionPredicates NoTieredCompilation
 */

/*
 * @test id=Xcomp
 * @bug 8288981 8350579
 * @run main/othervm -Xcomp
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.TestAssertionPredicates::*
 *                   -XX:CompileCommand=inline,compiler.predicates.TestAssertionPredicates::inline
 *                   compiler.predicates.TestAssertionPredicates Xcomp
 */

/*
 * @test id=LoopMaxUnroll0
 * @bug 8288981 8350579
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:LoopMaxUnroll=0
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.TestAssertionPredicates::*
 *                   compiler.predicates.TestAssertionPredicates LoopMaxUnroll0
 */

/*
 * @test id=StressXcomp
 * @bug 8288981 8350579
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:+StressGCM -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.TestAssertionPredicates::*
 *                   compiler.predicates.TestAssertionPredicates Stress
 */

/*
 * @test id=StressXbatch
 * @bug 8288981 8350579
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:+StressIGVN -XX:+StressGCM -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.TestAssertionPredicates::*
 *                   compiler.predicates.TestAssertionPredicates Stress
 */

/*
 * @test id=NoLoopPredication
 * @bug 8288981 8350579
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xbatch -XX:-UseLoopPredicate
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestAssertionPredicates::*
 *                   -XX:CompileCommand=dontinline,compiler.predicates.TestAssertionPredicates::*
 *                   compiler.predicates.TestAssertionPredicates NoLoopPredication
 */

/*
 * @test id=NoFlags
 * @bug 8288981 8350579
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure
 *                    compiler.predicates.TestAssertionPredicates NoFlags
 */

package compiler.predicates;

public class TestAssertionPredicates {
    static int[] iArrShort = new int[10];
    static int[] iArr = new int[200];
    static int[] iArr2 = new int[200];
    static int[] iArr3 = new int[300];
    static short[] sArr = new short[10];

    static boolean flag;
    static int iFld = 34;
    static int iFld2, iFld3;
    static float fFld;
    static short five = 5;


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
            case "LoopMaxUnroll0" -> {
                testDyingRuntimePredicate();
                testDyingNegatedRuntimePredicate();
            }
            case "Xcomp" -> {
                testDyingInitializedAssertionPredicate();
            }
            case "NoLoopPredication", "NoFlags", "Stress" -> {
                for (int i = 0; i < 10000; i++) {
                    runAllTests();
                }
            }
            default -> throw new RuntimeException("invalid arg");
        }
    }

    static void runAllTests() {
        testTemplateAssertionPredicateNotRemovedHalt();
        testTemplateAssertionPredicateNotRemovedMalformedGraph();
        testDyingRuntimePredicate();
        testDyingNegatedRuntimePredicate();
        testDyingInitializedAssertionPredicate();
        test8305428();
        test8305428No2();
        test8320920();
        test8332501();
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

    /**
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
}
