/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.alloc.trace;

import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.alloc.trace.TraceUtil.asShadowedRegisterValue;
import static org.graalvm.compiler.lir.alloc.trace.TraceUtil.isShadowedRegisterValue;
import static jdk.vm.ci.code.ValueUtil.asRegisterValue;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.List;

import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.ssa.SSAUtil.PhiValueVisitor;
import org.graalvm.compiler.lir.ssi.SSIUtil;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public final class TraceGlobalMoveResolutionPhase extends LIRPhase<TraceAllocationPhase.TraceAllocationContext> {

    /**
     * Abstract move resolver interface for testing.
     */
    public abstract static class MoveResolver {
        public abstract void addMapping(Value src, AllocatableValue dst, Value fromStack);
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, TraceAllocationContext context) {
        MoveFactory spillMoveFactory = context.spillMoveFactory;
        resolveGlobalDataFlow(context.resultTraces, lirGenRes, spillMoveFactory, target.arch);
    }

    @SuppressWarnings("try")
    private static void resolveGlobalDataFlow(TraceBuilderResult resultTraces, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory, Architecture arch) {
        LIR lir = lirGenRes.getLIR();
        /* Resolve trace global data-flow mismatch. */
        TraceGlobalMoveResolver moveResolver = new TraceGlobalMoveResolver(lirGenRes, spillMoveFactory, arch);
        PhiValueVisitor visitor = (Value phiIn, Value phiOut) -> {
            if (!isIllegal(phiIn)) {
                addMapping(moveResolver, phiOut, phiIn);
            }
        };

        try (Indent indent = Debug.logAndIndent("Trace global move resolution")) {
            for (Trace trace : resultTraces.getTraces()) {
                for (AbstractBlockBase<?> fromBlock : trace.getBlocks()) {
                    for (AbstractBlockBase<?> toBlock : fromBlock.getSuccessors()) {
                        if (resultTraces.getTraceForBlock(fromBlock) != resultTraces.getTraceForBlock(toBlock)) {
                            try (Indent indent0 = Debug.logAndIndent("Handle trace edge from %s (Trace%d) to %s (Trace%d)", fromBlock, resultTraces.getTraceForBlock(fromBlock).getId(), toBlock,
                                            resultTraces.getTraceForBlock(toBlock).getId())) {

                                final List<LIRInstruction> instructions;
                                final int insertIdx;
                                if (fromBlock.getSuccessorCount() == 1) {
                                    instructions = lir.getLIRforBlock(fromBlock);
                                    insertIdx = instructions.size() - 1;
                                } else {
                                    assert toBlock.getPredecessorCount() == 1;
                                    instructions = lir.getLIRforBlock(toBlock);
                                    insertIdx = 1;
                                }

                                moveResolver.setInsertPosition(instructions, insertIdx);
                                SSIUtil.forEachValuePair(lir, toBlock, fromBlock, visitor);
                                moveResolver.resolveAndAppendMoves();
                            }
                        }
                    }
                }
            }
        }
    }

    public static void addMapping(MoveResolver moveResolver, Value from, Value to) {
        assert !isIllegal(to);
        if (isShadowedRegisterValue(to)) {
            ShadowedRegisterValue toSh = asShadowedRegisterValue(to);
            addMappingToRegister(moveResolver, from, toSh.getRegister());
            addMappingToStackSlot(moveResolver, from, toSh.getStackSlot());
        } else {
            if (isRegister(to)) {
                addMappingToRegister(moveResolver, from, asRegisterValue(to));
            } else {
                assert isStackSlotValue(to) : "Expected stack slot: " + to;
                addMappingToStackSlot(moveResolver, from, (AllocatableValue) to);
            }
        }
    }

    private static void addMappingToRegister(MoveResolver moveResolver, Value from, RegisterValue register) {
        if (isShadowedRegisterValue(from)) {
            RegisterValue fromReg = asShadowedRegisterValue(from).getRegister();
            AllocatableValue fromStack = asShadowedRegisterValue(from).getStackSlot();
            checkAndAddMapping(moveResolver, fromReg, register, fromStack);
        } else {
            checkAndAddMapping(moveResolver, from, register, null);
        }
    }

    private static void addMappingToStackSlot(MoveResolver moveResolver, Value from, AllocatableValue stack) {
        if (isShadowedRegisterValue(from)) {
            ShadowedRegisterValue shadowedFrom = asShadowedRegisterValue(from);
            RegisterValue fromReg = shadowedFrom.getRegister();
            AllocatableValue fromStack = shadowedFrom.getStackSlot();
            if (!fromStack.equals(stack)) {
                checkAndAddMapping(moveResolver, fromReg, stack, fromStack);
            }
        } else {
            checkAndAddMapping(moveResolver, from, stack, null);
        }

    }

    private static void checkAndAddMapping(MoveResolver moveResolver, Value from, AllocatableValue to, AllocatableValue fromStack) {
        if (!from.equals(to) && (fromStack == null || !fromStack.equals(to))) {
            moveResolver.addMapping(from, to, fromStack);
        }
    }
}
