/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.predicates;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8347449 8388858
 * @summary Test that profiled loop predicates are turned off if loop predicates are turned off
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

public class TestDisabledParsePredicates {
    static final int SIZE = 100;
    static final int MIN = 3;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UseLoopPredicate",
                                   "-XX:+UseProfiledLoopPredicate");
        TestFramework.runWithFlags("-XX:-UseLoopPredicate");
        TestFramework.runWithFlags("-XX:-UseProfiledLoopPredicate");
        TestFramework.runWithFlags("-XX:-UseParsePredicates");
        TestFramework.runWithFlags("-XX:-UseLoopLimitCheckPredicate");
        TestFramework.runWithFlags("-XX:-UseAutoVectorizationPredicate");
        TestFramework.runWithFlags("-XX:-ShortRunningLongLoop");
    }

    @Run(test = "test")
    private static void check() {
        int res = test(true);
        Asserts.assertEQ(res, ((SIZE - 1) * SIZE - MIN * (MIN + 1)) / 2);
    }

    @DontInline
    private static void blackhole(int i) {
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
    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "1",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "1",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "1",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "1",
                   IRNode.SHORT_RUNNING_LONG_LOOP_PARSE_PREDICATE, "1"},
        applyIfAnd = { "UseLoopPredicate", "true",
                       "UseProfiledLoopPredicate", "true",
                       "UseLoopLimitCheckPredicate", "true",
                       "UseAutoVectorizationPredicate", "true",
                       "ShortRunningLongLoop", "true"})
    @IR(failOn = { IRNode.LOOP_PARSE_PREDICATE,
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE },
        counts = { IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "1",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "1",
                   IRNode.SHORT_RUNNING_LONG_LOOP_PARSE_PREDICATE, "1"},
        applyIfAnd = { "UseLoopPredicate", "false",
                       "UseParsePredicates", "true",
                       "UseLoopLimitCheckPredicate", "true",
                       "UseAutoVectorizationPredicate", "true",
                       "ShortRunningLongLoop", "true"})
    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "1",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "1",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "1",
                   IRNode.SHORT_RUNNING_LONG_LOOP_PARSE_PREDICATE, "1"},
        failOn = { IRNode.PROFILED_LOOP_PARSE_PREDICATE },
        applyIfAnd = { "UseLoopPredicate", "true",
                       "UseLoopLimitCheckPredicate", "true",
                       "UseAutoVectorizationPredicate", "true",
                       "ShortRunningLongLoop", "true",
                       "UseProfiledLoopPredicate", "false" })
    @IR(failOn = { IRNode.LOOP_PARSE_PREDICATE,
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE,
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE,
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE,
                   IRNode.SHORT_RUNNING_LONG_LOOP_PARSE_PREDICATE},
        applyIf = { "UseParsePredicates", "false"})

    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "1",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "1",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "1",
                   IRNode.SHORT_RUNNING_LONG_LOOP_PARSE_PREDICATE, "1"},
        failOn = { IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE },
        applyIfAnd = { "UseLoopLimitCheckPredicate", "false",
                       "UseParsePredicates", "true" })
    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "1",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "1",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "1",
                   IRNode.SHORT_RUNNING_LONG_LOOP_PARSE_PREDICATE, "1"},
        failOn = { IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE },
        applyIfAnd = { "UseAutoVectorizationPredicate", "false",
                       "UseParsePredicates", "true" })
    @IR(counts = { IRNode.LOOP_PARSE_PREDICATE, "1",
                   IRNode.PROFILED_LOOP_PARSE_PREDICATE, "1",
                   IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "1",
                   IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "1"},
        failOn = { IRNode.SHORT_RUNNING_LONG_LOOP_PARSE_PREDICATE },
        applyIfAnd = { "ShortRunningLongLoop", "false",
                       "UseParsePredicates", "true" })
    public static int test(boolean cond) {
        int[] arr = getArr();
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            if (cond) {
                if (arr[i] > MIN) {
                    sum += arr[i];
                }
            }
            blackhole(arr[i]);
        }

        return sum;
    }
}
