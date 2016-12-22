/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.alloc.trace.lsra;

import static org.graalvm.compiler.core.common.GraalOptions.DetailedAsserts;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.List;

import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanPhase.TraceLinearScan;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.ssa.SSAUtil.PhiValueVisitor;
import org.graalvm.compiler.lir.ssi.SSIUtil;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Phase 6: resolve data flow
 *
 * Insert moves at edges between blocks if intervals have been split.
 */
final class TraceLinearScanResolveDataFlowPhase extends TraceLinearScanAllocationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, TraceLinearScanAllocationContext context) {
        TraceBuilderResult traceBuilderResult = context.resultTraces;
        TraceLinearScan allocator = context.allocator;
        new Resolver(allocator, traceBuilderResult).resolveDataFlow(trace, allocator.sortedBlocks());
    }

    private static final class Resolver {
        private final TraceLinearScan allocator;
        private final TraceBuilderResult traceBuilderResult;

        private Resolver(TraceLinearScan allocator, TraceBuilderResult traceBuilderResult) {
            this.allocator = allocator;
            this.traceBuilderResult = traceBuilderResult;
        }

        private void resolveFindInsertPos(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, TraceLocalMoveResolver moveResolver) {
            if (fromBlock.getSuccessorCount() <= 1) {
                if (Debug.isLogEnabled()) {
                    Debug.log("inserting moves at end of fromBlock B%d", fromBlock.getId());
                }

                List<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(fromBlock);
                LIRInstruction instr = instructions.get(instructions.size() - 1);
                if (instr instanceof StandardOp.JumpOp) {
                    // insert moves before branch
                    moveResolver.setInsertPosition(instructions, instructions.size() - 1);
                } else {
                    moveResolver.setInsertPosition(instructions, instructions.size());
                }

            } else {
                if (Debug.isLogEnabled()) {
                    Debug.log("inserting moves at beginning of toBlock B%d", toBlock.getId());
                }

                if (DetailedAsserts.getValue()) {
                    assert allocator.getLIR().getLIRforBlock(fromBlock).get(0) instanceof StandardOp.LabelOp : "block does not start with a label";

                    /*
                     * Because the number of predecessor edges matches the number of successor
                     * edges, blocks which are reached by switch statements may have be more than
                     * one predecessor but it will be guaranteed that all predecessors will be the
                     * same.
                     */
                    for (AbstractBlockBase<?> predecessor : toBlock.getPredecessors()) {
                        assert fromBlock == predecessor : "all critical edges must be broken";
                    }
                }

                moveResolver.setInsertPosition(allocator.getLIR().getLIRforBlock(toBlock), 1);
            }
        }

        /**
         * Inserts necessary moves (spilling or reloading) at edges between blocks for intervals
         * that have been split.
         */
        @SuppressWarnings("try")
        private void resolveDataFlow(Trace currentTrace, AbstractBlockBase<?>[] blocks) {
            if (blocks.length < 2) {
                // no resolution necessary
                return;
            }
            try (Indent indent = Debug.logAndIndent("resolve data flow")) {

                TraceLocalMoveResolver moveResolver = allocator.createMoveResolver();
                AbstractBlockBase<?> toBlock = null;
                for (int i = 0; i < blocks.length - 1; i++) {
                    AbstractBlockBase<?> fromBlock = blocks[i];
                    toBlock = blocks[i + 1];
                    assert containedInTrace(currentTrace, fromBlock) : "Not in Trace: " + fromBlock;
                    assert containedInTrace(currentTrace, toBlock) : "Not in Trace: " + toBlock;
                    resolveCollectMappings(fromBlock, toBlock, moveResolver);
                }
                assert blocks[blocks.length - 1].equals(toBlock);
                if (toBlock.isLoopEnd()) {
                    assert toBlock.getSuccessorCount() == 1;
                    AbstractBlockBase<?> loopHeader = toBlock.getSuccessors()[0];
                    if (containedInTrace(currentTrace, loopHeader)) {
                        resolveCollectMappings(toBlock, loopHeader, moveResolver);
                    }
                }

            }
        }

        @SuppressWarnings("try")
        private void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, TraceLocalMoveResolver moveResolver) {
            try (Indent indent0 = Debug.logAndIndent("Edge %s -> %s", fromBlock, toBlock)) {
                // collect all intervals that have been split between
                // fromBlock and toBlock
                SSIUtil.forEachValuePair(allocator.getLIR(), toBlock, fromBlock, new MappingCollector(moveResolver, toBlock, fromBlock));
                if (moveResolver.hasMappings()) {
                    resolveFindInsertPos(fromBlock, toBlock, moveResolver);
                    moveResolver.resolveAndAppendMoves();
                }
            }
        }

        private boolean containedInTrace(Trace currentTrace, AbstractBlockBase<?> block) {
            return currentTrace.getId() == traceBuilderResult.getTraceForBlock(block).getId();
        }

        private static final DebugCounter numSSIResolutionMoves = Debug.counter("SSI LSRA[numSSIResolutionMoves]");
        private static final DebugCounter numStackToStackMoves = Debug.counter("SSI LSRA[numStackToStackMoves]");

        private class MappingCollector implements PhiValueVisitor {
            final TraceLocalMoveResolver moveResolver;
            final int toId;
            final int fromId;

            MappingCollector(TraceLocalMoveResolver moveResolver, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> fromBlock) {
                this.moveResolver = moveResolver;
                toId = allocator.getFirstLirInstructionId(toBlock);
                fromId = allocator.getLastLirInstructionId(fromBlock);
                assert fromId >= 0;
            }

            @Override
            public void visit(Value phiIn, Value phiOut) {
                assert !isRegister(phiOut) : "Out is a register: " + phiOut;
                assert !isRegister(phiIn) : "In is a register: " + phiIn;
                if (Value.ILLEGAL.equals(phiIn)) {
                    // The value not needed in this branch.
                    return;
                }
                if (isVirtualStackSlot(phiIn) && isVirtualStackSlot(phiOut) && phiIn.equals(phiOut)) {
                    // no need to handle virtual stack slots
                    return;
                }
                TraceInterval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiIn), toId, LIRInstruction.OperandMode.DEF);
                if (isConstantValue(phiOut)) {
                    numSSIResolutionMoves.increment();
                    moveResolver.addMapping(asConstant(phiOut), toInterval);
                } else {
                    TraceInterval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiOut), fromId, LIRInstruction.OperandMode.DEF);
                    if (fromInterval != toInterval) {
                        numSSIResolutionMoves.increment();
                        if (!(isStackSlotValue(toInterval.location()) && isStackSlotValue(fromInterval.location()))) {
                            moveResolver.addMapping(fromInterval, toInterval);
                        } else {
                            numStackToStackMoves.increment();
                            moveResolver.addMapping(fromInterval, toInterval);
                        }
                    }
                }
            }
        }
    }

}
