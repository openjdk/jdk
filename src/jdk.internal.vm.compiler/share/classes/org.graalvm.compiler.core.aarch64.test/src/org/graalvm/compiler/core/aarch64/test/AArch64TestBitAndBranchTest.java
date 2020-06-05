/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.aarch64.test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow;
import org.graalvm.compiler.lir.aarch64.AArch64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.jtt.LIRTest;
import org.graalvm.compiler.lir.jtt.LIRTestSpecification;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assume.assumeTrue;

public class AArch64TestBitAndBranchTest extends LIRTest {
    private static final Predicate<LIRInstruction> checkForBitTestAndBranchOp = op -> (op instanceof AArch64ControlFlow.BitTestAndBranchOp);
    private LIR lir;

    @Before
    public void checkAArch64() {
        assumeTrue("skipping AArch64 specific test", getTarget().arch instanceof AArch64);
    }

    public static long testBit42Snippet(long a, long b, long c) {
        if ((a & (1L << 42)) == 0) {
            return b + c;
        } else {
            return c;
        }
    }

    @Test
    public void testBit42() {
        test("testBit42Snippet", 1L << 42, Long.MAX_VALUE, Long.MIN_VALUE);
        test("testBit42Snippet", ~(1L << 42), Long.MAX_VALUE, Long.MIN_VALUE);
        checkLIR("testBit42Snippet", checkForBitTestAndBranchOp, 1);
    }

    private static final LargeOpSpec largeOpSingleNop = new LargeOpSpec((1 << 14 - 2) - 10, 2);

    /**
     * Tests the graceful case, where the estimation for
     * {@link CompilationResultBuilder#labelWithinRange(LIRInstruction, org.graalvm.compiler.asm.Label, int)}
     * holds.
     */
    public static int testBitTestAndBranchSingleSnippet(int a) {
        int res;
        if (a % 2 == 0) {
            res = fillOps(largeOpSingleNop, 1);
        } else {
            res = fillOps(largeOpSingleNop, 2);
        }
        return GraalDirectives.opaque(res);
    }

    @Test
    public void testBitTestAndBranchSingle() {
        runTest("testBitTestAndBranchSingleSnippet", 1);
        checkLIR("testBitTestAndBranchSingleSnippet", checkForBitTestAndBranchOp, 1);
    }

    private static final LargeOpSpec largeOpFourNop = new LargeOpSpec((1 << 14 - 2) - 10, 8);

    /**
     * Tests the case, where the estimation for
     * {@link CompilationResultBuilder#labelWithinRange(LIRInstruction, org.graalvm.compiler.asm.Label, int)}
     * does not hold and the code generation must be redone with large branches.
     */
    public static int testBitTestAndBranchFourSnippet(int a) {
        int res;
        if (a % 2 == 0) {
            res = fillOps(largeOpFourNop, 1);
        } else {
            res = fillOps(largeOpFourNop, 2);
        }
        return GraalDirectives.opaque(res);
    }

    @Test
    public void testBitTestAndBranchFour() {
        runTest("testBitTestAndBranchFourSnippet", 1);
        checkLIR("testBitTestAndBranchFourSnippet", checkForBitTestAndBranchOp, 1);
    }

    private static final float trueTarget = Float.MAX_VALUE;
    private static final float falseTarget = Float.MIN_VALUE;

    public static float testLessThanZeroSnippet(long a, long b) {
        if (b + a - b < 0) {
            return trueTarget - a;
        } else {
            return falseTarget + a;
        }
    }

    @Test
    public void testLessThanZero() {
        test("testLessThanZeroSnippet", 1L, 777L);
        test("testLessThanZeroSnippet", 0L, 777L);
        test("testLessThanZeroSnippet", -1L, 777L);
        checkLIR("testLessThanZeroSnippet", checkForBitTestAndBranchOp, 1);
    }

    public static float testLessThanEqualZeroSnippet(long a) {
        if (a <= 0) {
            return trueTarget - a;
        } else {
            return falseTarget + a;
        }
    }

    @Test
    public void testLessThanEqualZero() {
        test("testLessThanEqualZeroSnippet", 1L);
        test("testLessThanEqualZeroSnippet", 0L);
        test("testLessThanEqualZeroSnippet", -1L);
        checkLIR("testLessThanEqualZeroSnippet", checkForBitTestAndBranchOp, 0);
    }

    public static float testGreaterThanZeroSnippet(int a) {
        if (a > 0) {
            return trueTarget - a;
        } else {
            return falseTarget + a;
        }
    }

    @Test
    public void testGreaterThanZero() {
        test("testGreaterThanZeroSnippet", 1);
        test("testGreaterThanZeroSnippet", 0);
        test("testGreaterThanZeroSnippet", -1);
        checkLIR("testGreaterThanZeroSnippet", checkForBitTestAndBranchOp, 0);
    }

    public static float testGreaterThanEqualZeroSnippet(int a) {
        if (a >= 0) {
            return trueTarget - a;
        } else {
            return falseTarget + a;
        }
    }

    @Test
    public void testGreaterThanEqualZero() {
        test("testGreaterThanEqualZeroSnippet", 1);
        test("testGreaterThanEqualZeroSnippet", 0);
        test("testGreaterThanEqualZeroSnippet", -1);
        checkLIR("testGreaterThanEqualZeroSnippet", checkForBitTestAndBranchOp, 1);
    }

    private static class LargeOpSpec extends LIRTestSpecification {
        private final int n;
        private final int nopCount;

        LargeOpSpec(int n, int nopCount) {
            super();
            this.n = n;
            this.nopCount = nopCount;
        }

        @Override
        public void generate(LIRGeneratorTool gen, Value a) {
            for (int i = 0; i < n; i++) {
                gen.append(new NoOp(nopCount));
            }
            setResult(a);
        }
    }

    public static class NoOp extends AArch64LIRInstruction {
        private static final LIRInstructionClass<NoOp> TYPE = LIRInstructionClass.create(NoOp.class);
        private final int nopCount;

        public NoOp(int nopCount) {
            super(TYPE);
            this.nopCount = nopCount;
        }

        @Override
        protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            for (int i = 0; i < nopCount; i++) {
                masm.nop();
            }
        }
    }

    @LIRIntrinsic
    public static int fillOps(@SuppressWarnings("unused") LargeOpSpec s, int a) {
        return a;
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        suites.getPreAllocationOptimizationStage().appendPhase(new CheckPhase());
        return suites;
    }

    public class CheckPhase extends LIRPhase<PreAllocationOptimizationContext> {
        @Override
        protected void run(
                        TargetDescription target, LIRGenerationResult lirGenRes,
                        PreAllocationOptimizationContext context) {
            lir = lirGenRes.getLIR();
        }
    }

    protected void checkLIR(String methodName, Predicate<LIRInstruction> predicate, int expected) {
        compile(getResolvedJavaMethod(methodName), null);
        int actualOpNum = 0;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (predicate.test(ins)) {
                actualOpNum++;
            }
        }
        Assert.assertEquals(expected, actualOpNum);
    }
}
