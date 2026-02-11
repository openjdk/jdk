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

package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @summary Test that inter-block redundant flag operations are removed by the
 *          peephole optimizer.
 * @library /test/lib /
 * @requires vm.debug == true & vm.compiler2.enabled
 * @requires os.arch == "x86_64" | os.arch == "amd64"
 * @run driver compiler.c2.irTests.TestInterBlockRedundantFlagOps
 */
public class TestInterBlockRedundantFlagOps {
    static volatile int sinkI;

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addScenarios(
            new Scenario(0, "-XX:-OptoPeephole"),
            new Scenario(1, "-XX:+OptoPeephole")
        ).start();
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {"compI_rReg", "2"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "false"})
    @IR(counts = {"compI_rReg", "1"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "true"})
    public static int testIntRegReg(int x, int y) {
        if (x < y) { sinkI = -1; return -1; }
        if (x > y) { sinkI = +1; return +1; }
        return 0;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {"compI_rReg_imm", "2"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "false"})
    @IR(counts = {"compI_rReg_imm", "1"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "true"})
    public static int testIntRegImm(int x) {
        if (x < 42) { sinkI = -1; return -1; }
        if (x > 42) { sinkI = +1; return +1; }
        return 0;
    }

    @DontCompile
    public void assertResults(int x, int y) {
        Asserts.assertEQ(Integer.compare(x, y), testIntRegReg(x, y));
        Asserts.assertEQ(Integer.compare(x, 42), testIntRegImm(x));
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {"compU_rReg", "2"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "false"})
    @IR(counts = {"compU_rReg", "1"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "true"})
    public static int testUnsignedIntRegReg(int x, int y) {
        int cmp = Integer.compareUnsigned(x, y);
        if (cmp < 0) { sinkI = -1; return -1; }
        if (cmp > 0) { sinkI = +1; return +1; }
        return 0;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {"compU_rReg_imm", "2"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "false"})
    @IR(counts = {"compU_rReg_imm", "1"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "true"})
    public static int testUnsignedIntRegImm(int x) {
        int cmp = Integer.compareUnsigned(x, 42);
        if (cmp < 0) { sinkI = -1; return -1; }
        if (cmp > 0) { sinkI = +1; return +1; }
        return 0;
    }

    @DontCompile
    public void assertUnsignedResults(int x, int y) {
        Asserts.assertEQ(Integer.compareUnsigned(x, y), testUnsignedIntRegReg(x, y));
        Asserts.assertEQ(Integer.compareUnsigned(x, 42), testUnsignedIntRegImm(x));
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {"compL_rReg", "2"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "false"})
    @IR(counts = {"compL_rReg", "1"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "true"})
    public static long testLongRegReg(long x, long y) {
        if (x < y) { sinkI = -1; return -1L; }
        if (x > y) { sinkI = +1; return +1L; }
        return 0L;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {"compL_rReg_imm", "2"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "false"})
    @IR(counts = {"compL_rReg_imm", "1"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "true"})
    public static long testLongRegImm(long x) {
        if (x < 42L) { sinkI = -1; return -1L; }
        if (x > 42L) { sinkI = +1; return +1L; }
        return 0L;
    }

    @DontCompile
    public void assertResults(long x, long y) {
        Asserts.assertEQ(Long.compare(x, y), testLongRegReg(x, y));
        Asserts.assertEQ(Long.compare(x, 42L), testLongRegImm(x));
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(counts = {"compUL_rReg", "2"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "false"})
    @IR(counts = {"compUL_rReg", "1"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "true"})
    public static long testUnsignedLongRegReg(long x, long y) {
        int cmp = Long.compareUnsigned(x, y);
        if (cmp < 0) { sinkI = -1; return -1L; }
        if (cmp > 0) { sinkI = +1; return +1L; }
        return 0L;
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = {"compUL_rReg_imm", "2"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "false"})
    @IR(counts = {"compUL_rReg_imm", "1"}, phase = CompilePhase.FINAL_CODE, applyIf = {"OptoPeephole", "true"})
    public static long testUnsignedLongRegImm(long x) {
        int cmp = Long.compareUnsigned(x, 42L);
        if (cmp < 0) { sinkI = -1; return -1L; }
        if (cmp > 0) { sinkI = +1; return +1L; }
        return 0L;
    }

    @DontCompile
    public void assertUnsignedResults(long x, long y) {
        Asserts.assertEQ((long) Long.compareUnsigned(x, y), testUnsignedLongRegReg(x, y));
        Asserts.assertEQ((long) Long.compareUnsigned(x, 42L), testUnsignedLongRegImm(x));
    }
}
