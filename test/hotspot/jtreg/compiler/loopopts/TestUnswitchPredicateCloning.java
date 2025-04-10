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
 import jdk.test.lib.Asserts;

 /*
  * @test
  * @bug 8346552
  * @summary Test that all parse predicates are cloned after loop unswitching.
  * @library /test/lib /
  * @run driver compiler.loopopts.TestUnswitchPredicateCloning
  */

public class TestUnswitchPredicateCloning {
    static final int SIZE = 100;
    static final int IDX = 42;

    public static void main(String[] strArr) {
        TestFramework.run();
    }

    @Run(test = "test")
    @Warmup(0)
    private static void check() {
        int res = test(IDX);
        Asserts.assertEQ(res, SIZE * IDX);
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
    // Check that loop unswitching the number of parse predicates inside the unswitched
    // loop have doubled.
    @IR(counts = {IRNode.LOOP_PARSE_PREDICATE, "3",
                 IRNode.PROFILED_LOOP_PARSE_PREDICATE, "3",
                 IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "3",
                 IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "3"},
        phase = CompilePhase.BEFORE_LOOP_UNSWITCHING)
    @IR(counts = {IRNode.LOOP_PARSE_PREDICATE, "5",
                 IRNode.PROFILED_LOOP_PARSE_PREDICATE, "5",
                 IRNode.LOOP_LIMIT_CHECK_PARSE_PREDICATE, "5",
                 IRNode.AUTO_VECTORIZATION_CHECK_PARSE_PREDICATE, "5"},
        phase = CompilePhase.AFTER_LOOP_UNSWITCHING)
    // Check that opaque template assertion predicated are added in loop predication
    // even if loop predication only happens after loop unswitching.
    @IR(failOn = { IRNode.OPAQUE_TEMPLATE_ASSERTION_PREDICATE },
        phase = CompilePhase.AFTER_LOOP_UNSWITCHING)
    @IR(counts = { IRNode.OPAQUE_TEMPLATE_ASSERTION_PREDICATE, "2" },
        phase = CompilePhase.AFTER_LOOP_PREDICATION_RC)
    static int test(int j) {
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
}