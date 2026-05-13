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

package compiler.loopopts;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8371685
 * @requires vm.flagless & vm.debug
 * @summary Verifies that the LoopPeeling flag correctly disables loop peeling
 *          by checking whether the "After Loop Peeling" compile phase is
 *          emitted.  When loop peeling is disabled, no peeling should occur and
 *          the phase must be absent from the compilation output.
 * @library /test/lib /
 * @run driver compiler.loopopts.TestLoopPeelingDisabled
 */
public class TestLoopPeelingDisabled {
    static int[] array = new int[100];

    public static void main(String[] args) {
        // First, run the test with loop peeling enabled, which is the default.
        // The IR framework should catch if the number of counted loops does not
        // match the annotations.
        TestFramework.run();

        // Then, run the same test with loop peeling disabled, which should
        // elide the {BEFORE,AFTER}_LOOP_PEELING compilation phases, causing the
        // test to throw an IRViolationException. We then check whether the
        // exception message matches our expectation (that the loop peeling
        // phase was not found). If IR verification is disabled, this test will
        // not throw an IRViolationException.
        try {
            TestFramework.runWithFlags("-XX:+UnlockDiagnosticVMOptions",
                                       "-XX:LoopPeeling=0");
            String verifyIR = System.getProperty("VerifyIR", "true");
            String msg = "Expected IRViolationException when performing IR matching";
            Asserts.assertFalse(Boolean.parseBoolean(verifyIR), msg);
        } catch (IRViolationException e) {
            String info = e.getExceptionInfo();
            if (!info.contains("NO compilation output found for this phase")) {
                Asserts.fail("Unexpected IR violation: " + info);
            }
            System.out.println("Loop peeling correctly disabled");
        }

        // Finally, run the same test with loop peeling disabled only when
        // splitting iterations.  Since the function being tested does not hit
        // this case, we expect that the loop will be peeled, which is ensured
        // by the IR annotations.
        TestFramework.runWithFlags("-XX:+UnlockDiagnosticVMOptions",
                                   "-XX:LoopPeeling=2");
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "1"}, phase = CompilePhase.BEFORE_LOOP_PEELING)
    @IR(counts = {IRNode.COUNTED_LOOP, "2"}, phase = CompilePhase.AFTER_LOOP_PEELING)
    public static int test() {
        int sum = 0;

        // Use an odd trip count so that `do_maximally_unroll()` tries to peel
        // the odd iteration.
        for (int i = 0; i < 5; i++) {
            sum += array[i];
        }

        return sum;
    }
}
