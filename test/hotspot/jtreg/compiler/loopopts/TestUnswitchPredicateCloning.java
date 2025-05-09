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

package compiler.loopopts;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8346552
 * @summary Test that all parse predicates are cloned after loop unswitching.
 * @key randomness
 * @library /test/lib /
 * @run driver compiler.loopopts.TestUnswitchPredicateCloning
 */

public class TestUnswitchPredicateCloning {
    static final int SIZE = 100;

    private static final Random random = Utils.getRandomInstance();

    public static void main(String[] strArr) {
        TestFramework.run();
    }

    @Run(test = {"testUnswitchingBeforePredication", "testPredicationBeforeUnswitching", "testUnswitchingUncounted"})
    @Warmup(0)
    private static void runNoWarmup() {
        final int idx = random.nextInt(SIZE);
        final boolean cond = random.nextBoolean();
        int res = testUnswitchingBeforePredication(idx);
        Asserts.assertEQ(SIZE * idx, res);
        res = testPredicationBeforeUnswitching(idx, cond);
        Asserts.assertEQ((SIZE * (SIZE - 1)) / 2 + (cond ? SIZE * idx : 0), res);
        res = testUnswitchingUncounted(cond);
        Asserts.assertEQ((SIZE * (SIZE - 1)) / 2 + (cond ? SIZE : 0), res);
    }

    @DontInline
    private static int[] getArr() {
        int[] arr = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            arr[i] = i;
        }
        return arr;
    }

    @Test
    // Check that Loop Unswitching doubled the number of Parse Predicates: We have
    // them at the true- and false-path-loop. Note that the Loop Limit Check Parse
    // Predicate is not cloned when we already have a counted loop.
    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "3",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "3",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "3",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "3" },
        phase = CompilePhase.BEFORE_LOOP_UNSWITCHING)
    // Since we know that Loop Predication happens after Loop Unswitching, we can test the
    // have already been removed in the beautify loop phase.
    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "4",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "4",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "3",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "4" },
        phase = CompilePhase.BEFORE_LOOP_PREDICATION_RC)
    // Check that Opaque Template Assertion Predicates are added in Loop Predication
    // even if Loop Predication only happens after Loop Unswitching.
    @IR(failOn = { IRNode.OPAQUE_TEMPLATE_ASSERTION_PREDICATE },
        phase = CompilePhase.AFTER_LOOP_UNSWITCHING)
    @IR(counts = { IRNode.OPAQUE_TEMPLATE_ASSERTION_PREDICATE, "2" },
        phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    static int testUnswitchingBeforePredication(int j) {
        int zero = 34;
        int limit = 2;

        // Ensure zero == 0 is only known after CCP
        for (; limit < 4; limit *= 2) {
        }
        for (int i = 2; i < limit; i++) {
            zero = 0;
        }

        int[] arr = getArr();
        int res = 0;
        for (int i = 0; i < arr.length; i++) {
            // Trigger unswitching only after CCP
            if (zero == 0) {
                // Trigger range check after loop unswitching
                res += arr[j];
            } else {
                res += arr[i];
            }
        }

        return res;
    }

    @Test
    // Check that Loop Unswitching doubled the number of Parse and Template
    // Assertion Predicates. Again, the Loop Limit Check Parse Predicate
    // remains at the Loop Selector since this is a counted loop.
    @IR(failOn = { IRNode.OPAQUE_TEMPLATE_ASSERTION_PREDICATE },
        phase = CompilePhase.BEFORE_LOOP_PREDICATION_RC)
    @IR(counts = { IRNode.OPAQUE_TEMPLATE_ASSERTION_PREDICATE, "2",
                   IRNode.LOOP_PARSE_PREDICATE, "1",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "1",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "1",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "1" },
        phase = CompilePhase.BEFORE_LOOP_UNSWITCHING)
    // After Loop Unswitching and after removing the killed predicates.
    @IR(counts = { IRNode.OPAQUE_TEMPLATE_ASSERTION_PREDICATE, "4",
                   IRNode.LOOP_PARSE_PREDICATE, "2",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "2",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "1",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "2" },
        phase = CompilePhase.PHASEIDEALLOOP2)
    static int testPredicationBeforeUnswitching(int j, boolean cond) {
        int[] arr = getArr();
        int res = 0;
        for (int i = 0; i < arr.length; i++) {
            if (cond) {
                res += arr[j];
            }
            res += arr[i];
        }
        return res;
    }

    @Test
    // Check that Loop Unswitching doubled the number of all Parse Predicates.
    // Since this is not counted loop, the Loop Limit Check Parse Predicate
    // has to be cloned to both unswitched loops.
    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "1",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "1",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "1",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "1" },
        phase = CompilePhase.BEFORE_LOOP_UNSWITCHING)
    // After Loop Unswitching and after removing the killed predicates all
    // Parse Predicates are doubled.
    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "2",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "2",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "2",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "2" },
        failOn = { IRNode.COUNTED_LOOP },
        phase = CompilePhase.PHASEIDEALLOOP1)
    @IR(failOn = { IRNode.COUNTED_LOOP })
    static int testUnswitchingUncounted(boolean cond) {
        int[] arr = getArr();
        int res = 0;
        int i = 0;
        while (i < arr.length) {
            if (cond) {
                res += 1;
            }
            res += arr[i];

            i = arr[i] + 1; // effectively i += 1, but don't tell the compiler!
        }

        return res;
    }
}