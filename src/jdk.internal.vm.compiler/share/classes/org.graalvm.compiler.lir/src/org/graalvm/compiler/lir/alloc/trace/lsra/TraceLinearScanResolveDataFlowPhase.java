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

import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;

import java.util.ArrayList;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.alloc.trace.GlobalLivenessInfo;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanPhase.TraceLinearScan;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.ssa.SSAUtil;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Phase 6: resolve data flow
 *
 * Insert moves at edges between blocks if intervals have been split.
 */
final class TraceLinearScanResolveDataFlowPhase extends TraceLinearScanAllocationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, MoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig,
                    TraceBuilderResult traceBuilderResult, TraceLinearScan allocator) {
        new Resolver(allocator, traceBuilderResult).resolveDataFlow(trace, allocator.sortedBlocks());
    }

    private static final class Resolver {
        private final TraceLinearScan allocator;
        private final TraceBuilderResult traceBuilderResult;
        private final DebugContext debug;

        private Resolver(TraceLinearScan allocator, TraceBuilderResult traceBuilderResult) {
            this.allocator = allocator;
            this.traceBuilderResult = traceBuilderResult;
            this.debug = allocator.getDebug();
        }

        private void resolveFindInsertPos(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, TraceLocalMoveResolver moveResolver) {
            if (fromBlock.getSuccessorCount() <= 1) {
                if (debug.isLogEnabled()) {
                    debug.log("inserting moves at end of fromBlock B%d", fromBlock.getId());
                }

                ArrayList<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(fromBlock);
                LIRInstruction instr = instructions.get(instructions.size() - 1);
                if (instr instanceof StandardOp.JumpOp) {
                    // insert moves before branch
                    moveResolver.setInsertPosition(instructions, instructions.size() - 1);
                } else {
                    moveResolver.setInsertPosition(instructions, instructions.size());
                }

            } else {
                if (debug.isLogEnabled()) {
                    debug.log("inserting moves at beginning of toBlock B%d", toBlock.getId());
                }

                if (Assertions.detailedAssertionsEnabled(allocator.getOptions())) {
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
            try (Indent indent = debug.logAndIndent("resolve data flow")) {

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
            try (Indent indent0 = debug.logAndIndent("Edge %s -> %s", fromBlock, toBlock)) {
                // collect all intervals that have been split between
                // fromBlock and toBlock
                int toId = allocator.getFirstLirInstructionId(toBlock);
                int fromId = allocator.getLastLirInstructionId(fromBlock);
                assert fromId >= 0;
                LIR lir = allocator.getLIR();
                if (SSAUtil.isMerge(toBlock)) {
                    JumpOp blockEnd = SSAUtil.phiOut(lir, fromBlock);
                    LabelOp label = SSAUtil.phiIn(lir, toBlock);
                    for (int i = 0; i < label.getPhiSize(); i++) {
                        addMapping(blockEnd.getOutgoingValue(i), label.getIncomingValue(i), fromId, toId, moveResolver);
                    }
                }
                GlobalLivenessInfo livenessInfo = allocator.getGlobalLivenessInfo();
                int[] locTo = livenessInfo.getBlockIn(toBlock);
                for (int i = 0; i < locTo.length; i++) {
                    TraceInterval interval = allocator.intervalFor(locTo[i]);
                    addMapping(interval, interval, fromId, toId, moveResolver);
                }

                if (moveResolver.hasMappings()) {
                    resolveFindInsertPos(fromBlock, toBlock, moveResolver);
                    moveResolver.resolveAndAppendMoves();
                }
            }
        }

        private boolean containedInTrace(Trace currentTrace, AbstractBlockBase<?> block) {
            return currentTrace.getId() == traceBuilderResult.getTraceForBlock(block).getId();
        }

        private static final CounterKey numResolutionMoves = DebugContext.counter("TraceRA[numTraceLSRAResolutionMoves]");
        private static final CounterKey numStackToStackMoves = DebugContext.counter("TraceRA[numTraceLSRAStackToStackMoves]");

        private void addMapping(Value phiFrom, Value phiTo, int fromId, int toId, TraceLocalMoveResolver moveResolver) {
            assert !isRegister(phiFrom) : "Out is a register: " + phiFrom;
            assert !isRegister(phiTo) : "In is a register: " + phiTo;
            assert !Value.ILLEGAL.equals(phiTo) : "The value not needed in this branch? " + phiFrom;
            if (isVirtualStackSlot(phiTo) && isVirtualStackSlot(phiFrom) && phiTo.equals(phiFrom)) {
                // no need to handle virtual stack slots
                return;
            }
            TraceInterval toParent = allocator.intervalFor(asVariable(phiTo));
            if (isConstantValue(phiFrom)) {
                numResolutionMoves.increment(debug);
                TraceInterval toInterval = allocator.splitChildAtOpId(toParent, toId, LIRInstruction.OperandMode.DEF);
                moveResolver.addMapping(asConstant(phiFrom), toInterval);
            } else {
                addMapping(allocator.intervalFor(asVariable(phiFrom)), toParent, fromId, toId, moveResolver);
            }
        }

        private void addMapping(TraceInterval fromParent, TraceInterval toParent, int fromId, int toId, TraceLocalMoveResolver moveResolver) {
            TraceInterval fromInterval = allocator.splitChildAtOpId(fromParent, fromId, LIRInstruction.OperandMode.USE);
            TraceInterval toInterval = toParent.getSplitChildAtOpIdOrNull(toId, LIRInstruction.OperandMode.DEF);
            if (toInterval == null) {
                // not alive
                return;
            }
            if (fromInterval != toInterval) {
                numResolutionMoves.increment(debug);
                if (isStackSlotValue(toInterval.location()) && isStackSlotValue(fromInterval.location())) {
                    numStackToStackMoves.increment(debug);
                }
                moveResolver.addMapping(fromInterval, toInterval);
            }
        }
    }

}
