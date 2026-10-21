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

/*
 * @test
 * @bug 8379409
 * @summary Loop predication of signed and unsigned checks with any comparison.
 * @library /test/lib /
 * @run driver compiler.predicates.TestGeneralizedLoopPredication
 */
public class TestGeneralizedLoopPredication {
    static final int N = 1000;

    public static void main(String[] args) {
        TestFramework.run();
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
            } else {
                r--;
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
            } else {
                r--;
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
            } else {
                r--;
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
            } else {
                r--;
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
            } else {
                r--;
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
            } else {
                r--;
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
            } else {
                r--;
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
            } else {
                r--;
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
}
