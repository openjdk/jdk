/*
 * Copyright (c) 2026, BELLSOFT. All rights reserved.
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

package compiler.predicates;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/*
 * @test id=ir
 * @bug 8379409
 * @summary Loop predication of signed and unsigned checks with any comparison.
 * @library /test/lib /
 * @run driver compiler.predicates.TestGeneralizedLoopPredication
 */

/*
 * @test id=deopt
 * @bug 8379409
 * @summary A failing hoisted check traps in the predicate before the loop.
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:CompileThreshold=100
 *                   -XX:CompileCommand=compileonly,compiler.predicates.TestGeneralizedLoopPredication::*
 *                   -XX:CompileCommand=exclude,compiler.predicates.TestGeneralizedLoopPredication::run*
 *                   compiler.predicates.TestGeneralizedLoopPredication deopt
 */
public class TestGeneralizedLoopPredication {
    static final int N = 1000;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("deopt")) {
            checkDeopts();
        } else {
            TestFramework.run();
        }
    }

    // Predication hoists the check out of the loop:
    // no unstable_if trap in the loop body, but a predicate trap before the loop.
    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP,
        counts = {IRNode.PREDICATE_TRAP, ">= 1"})
    static int signedLt(int n, int off, int lim) {
        int r = 0;
        for (int i = 0; i < n; i++) {
            if (i * 3 + off < lim) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "signedLt")
    static void runSignedLt() {
        Asserts.assertEQ(signedLt(N, 0, Integer.MAX_VALUE), N * (N - 1) / 2);
    }

    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP,
        counts = {IRNode.PREDICATE_TRAP, ">= 1"})
    static int signedLe(int n, int off, int lim) {
        int r = 0;
        for (int i = 0; i < n; i++) {
            if (i * -2 + off <= lim) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "signedLe")
    static void runSignedLe() {
        Asserts.assertEQ(signedLe(N, 0, 0), N * (N - 1) / 2);
    }

    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP,
        counts = {IRNode.PREDICATE_TRAP, ">= 1"})
    static int signedGt(int n, int off, int lim) {
        int r = 0;
        for (int i = n; i > 0; i--) {
            if (i * 5 + off > lim) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "signedGt")
    static void runSignedGt() {
        Asserts.assertEQ(signedGt(N, 0, 0), N * (N + 1) / 2);
    }

    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP,
        counts = {IRNode.PREDICATE_TRAP, ">= 1"})
    static int signedGe(int n, int off, int lim) {
        int r = 0;
        for (int i = 0; i < n; i += 2) {
            if (off - i >= lim) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "signedGe")
    static void runSignedGe() {
        Asserts.assertEQ(signedGe(N, 0, Integer.MIN_VALUE), N * (N - 2) / 4);
    }

    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP,
        counts = {IRNode.PREDICATE_TRAP, ">= 1"})
    static int unsignedLt(int n, int off, int lim) {
        int r = 0;
        for (int i = 0; i < n; i++) {
            if (Integer.compareUnsigned(i * 2 + off, lim) < 0) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "unsignedLt")
    static void runUnsignedLt() {
        Asserts.assertEQ(unsignedLt(N, 0, -1), N * (N - 1) / 2);
    }

    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP,
        counts = {IRNode.PREDICATE_TRAP, ">= 1"})
    static int unsignedLe(int n, int off, int lim) {
        int r = 0;
        for (int i = 0; i < n; i++) {
            if (Integer.compareUnsigned(i * -3 + off, lim) <= 0) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "unsignedLe")
    static void runUnsignedLe() {
        Asserts.assertEQ(unsignedLe(N, 3 * N, -1), N * (N - 1) / 2);
    }

    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP,
        counts = {IRNode.PREDICATE_TRAP, ">= 1"})
    static int unsignedGt(int n, int off, int lim) {
        int r = 0;
        for (int i = n; i > 0; i--) {
            if (Integer.compareUnsigned(i * 7 + off, lim) > 0) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "unsignedGt")
    static void runUnsignedGt() {
        Asserts.assertEQ(unsignedGt(N, 1, 0), N * (N + 1) / 2);
    }

    @Test
    @IR(failOn = IRNode.UNSTABLE_IF_TRAP,
        counts = {IRNode.PREDICATE_TRAP, ">= 1"})
    static int unsignedGe(int n, int off, int lim) {
        int r = 0;
        for (int i = n; i > 0; i -= 3) {
            if (Integer.compareUnsigned(i + off, lim) >= 0) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "unsignedGe")
    static void runUnsignedGe() {
        int expected = 0;
        for (int i = N; i > 0; i -= 3) {
            expected += i;
        }
        Asserts.assertEQ(unsignedGe(N, 10, 10), expected);
    }

    // Not hoisted: != is not an ordered comparison.
    @Test
    @IR(counts = {IRNode.UNSTABLE_IF_TRAP, ">= 1"}, failOn = IRNode.PREDICATE_TRAP)
    static int signedNe(int n, int off, int lim) {
        int r = 0;
        for (int i = 0; i < n; i++) {
            if (i * 3 + off != lim) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "signedNe")
    static void runSignedNe() {
        Asserts.assertEQ(signedNe(N, 0, -1), N * (N - 1) / 2);
    }

    // Not hoisted: long range checks are only predicated for <.
    @Test
    @IR(counts = {IRNode.UNSTABLE_IF_TRAP, ">= 1"}, failOn = IRNode.PREDICATE_TRAP)
    static long longLe(long n, long off, long lim) {
        long r = 0;
        for (long i = 0; i < n; i++) {
            if (i * 3 + off <= lim) {
                r += i;
            }
        }
        return r;
    }

    @Run(test = "longLe")
    static void runLongLe() {
        Asserts.assertEQ(longLe(N, 0, Long.MAX_VALUE), (long) N * (N - 1) / 2);
    }

    static void checkDeopts() {
        checkDeopt(TestGeneralizedLoopPredication::runSignedLt, () -> signedLt(N, 0, 3 * (N - 1)));
        checkDeopt(TestGeneralizedLoopPredication::runSignedLe, () -> signedLe(N, 0, -1));
        checkDeopt(TestGeneralizedLoopPredication::runSignedGt, () -> signedGt(N, 0, 5));
        checkDeopt(TestGeneralizedLoopPredication::runSignedGe, () -> signedGe(N, 0, -(N - 3)));
        checkDeopt(TestGeneralizedLoopPredication::runUnsignedLt, () -> unsignedLt(N, 0, 2 * (N - 1)));
        checkDeopt(TestGeneralizedLoopPredication::runUnsignedLe, () -> unsignedLe(N, 3 * N, 3 * N - 1));
        checkDeopt(TestGeneralizedLoopPredication::runUnsignedGt, () -> unsignedGt(N, 1, 8));
        checkDeopt(TestGeneralizedLoopPredication::runUnsignedGe, () -> unsignedGe(N, 10, 12));
    }

    static final int COMPILE_THRESHOLD = 100; // -XX:CompileThreshold=100 of the deopt run

    // Warm up and compile, then fail the check in one edge iteration: the predicate must trap.
    static void checkDeopt(Runnable warmUp, Runnable failingCall) {
        WhiteBox wb = WhiteBox.getWhiteBox();
        for (int i = 0; i < 2 * COMPILE_THRESHOLD; i++) {
            warmUp.run();
        }
        int predicateDeoptsCount = wb.getDeoptCount("predicate", null);
        failingCall.run();
        int predicateDeoptsCountDiff = wb.getDeoptCount("predicate", null) - predicateDeoptsCount;
        Asserts.assertEQ(predicateDeoptsCountDiff, 1, "predicate deopt is expected");
    }
}
