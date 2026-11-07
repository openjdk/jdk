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

package compiler.c2.igvn;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8396021
 * @key randomness
 * @summary AddNode::IdealIL and OrI/OrLNode::Ideal turn shift/add and shift/or
 *          patterns into rotates. When an input of the shifts, or of a
 *          (0 - s) shift count, changes, the Add/Or must be put back on the
 *          IGVN worklist. Use -XX:VerifyIterativeGVN=1110 to catch missing
 *          optimizations.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestMissingRotateIdealization {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions",
                                   "-XX:VerifyIterativeGVN=1110");
    }

    @Run(test = {"testAddI", "testAddL", "testOrI", "testOrL", "testOrIRotateLeft", "testOrLRotateLeft"})
    public void runTests() {
        int xi = RANDOM.nextInt();
        long xl = RANDOM.nextLong();
        int s = RANDOM.nextInt();

        Asserts.assertEQ(Integer.rotateRight(xi, 16), testAddI(xi));
        Asserts.assertEQ(Long.rotateRight(xl, 16), testAddL(xl));
        Asserts.assertEQ(Integer.rotateRight(xi, s), testOrI(xi, s));
        Asserts.assertEQ(Long.rotateRight(xl, s), testOrL(xl, s));
        Asserts.assertEQ(Integer.rotateLeft(xi, s), testOrIRotateLeft(xi, s));
        Asserts.assertEQ(Long.rotateLeft(xl, s), testOrLRotateLeft(xl, s));
    }

    // delayedOne is only known to be 1 after loop opts, so the shifted value
    // becomes x late: n -> shift -> add
    @Test
    @IR(failOn = {IRNode.ROTATE_LEFT, IRNode.ROTATE_RIGHT}, phase = CompilePhase.BEFORE_LOOP_OPTS)
    @IR(failOn = IRNode.ADD_I, counts = {IRNode.ROTATE_RIGHT, "1"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true"})
    @IR(failOn = IRNode.ADD_I, counts = {IRNode.ROTATE_RIGHT, "1"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"zbb", "true"})
    static int testAddI(int x) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x >>> 16) + ((x * delayedOne) << 16);
    }

    @Test
    @IR(failOn = {IRNode.ROTATE_LEFT, IRNode.ROTATE_RIGHT}, phase = CompilePhase.BEFORE_LOOP_OPTS)
    @IR(failOn = IRNode.ADD_L, counts = {IRNode.ROTATE_RIGHT, "1"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true"})
    @IR(failOn = IRNode.ADD_L, counts = {IRNode.ROTATE_RIGHT, "1"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"zbb", "true"})
    static long testAddL(long x) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x >>> 16) + ((x * delayedOne) << 48);
    }

    // the count of one shift becomes (0 - s) late: n -> SubI -> shift -> or
    @Test
    @IR(failOn = {IRNode.ROTATE_LEFT, IRNode.ROTATE_RIGHT}, phase = CompilePhase.BEFORE_LOOP_OPTS)
    @IR(failOn = IRNode.OR_I, counts = {IRNode.ROTATE_RIGHT, "1"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true"})
    @IR(failOn = IRNode.OR_I, counts = {IRNode.ROTATE_RIGHT, "1"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"zbb", "true"})
    static int testOrI(int x, int s) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x >>> s) | (x << -(s * delayedOne));
    }

    @Test
    @IR(failOn = {IRNode.ROTATE_LEFT, IRNode.ROTATE_RIGHT}, phase = CompilePhase.BEFORE_LOOP_OPTS)
    @IR(failOn = IRNode.OR_L, counts = {IRNode.ROTATE_RIGHT, "1"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true"})
    @IR(failOn = IRNode.OR_L, counts = {IRNode.ROTATE_RIGHT, "1"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"zbb", "true"})
    static long testOrL(long x, int s) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x >>> s) | (x << -(s * delayedOne));
    }

    @Test
    @IR(failOn = {IRNode.ROTATE_LEFT, IRNode.ROTATE_RIGHT}, phase = CompilePhase.BEFORE_LOOP_OPTS)
    @IR(failOn = IRNode.OR_I, counts = {IRNode.ROTATE_LEFT, "1"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true"})
    @IR(failOn = IRNode.OR_I, counts = {IRNode.ROTATE_LEFT, "1"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"zbb", "true"})
    static int testOrIRotateLeft(int x, int s) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x << s) | (x >>> -(s * delayedOne));
    }

    @Test
    @IR(failOn = {IRNode.ROTATE_LEFT, IRNode.ROTATE_RIGHT}, phase = CompilePhase.BEFORE_LOOP_OPTS)
    @IR(failOn = IRNode.OR_L, counts = {IRNode.ROTATE_LEFT, "1"},
        applyIfPlatformOr = {"x64", "true", "aarch64", "true"})
    @IR(failOn = IRNode.OR_L, counts = {IRNode.ROTATE_LEFT, "1"},
        applyIfPlatform = {"riscv64", "true"}, applyIfCPUFeature = {"zbb", "true"})
    static long testOrLRotateLeft(long x, int s) {
        int delayedOne = 100;
        for (int i = 0; i < 99; i++) { delayedOne--; }
        return (x << s) | (x >>> -(s * delayedOne));
    }
}
