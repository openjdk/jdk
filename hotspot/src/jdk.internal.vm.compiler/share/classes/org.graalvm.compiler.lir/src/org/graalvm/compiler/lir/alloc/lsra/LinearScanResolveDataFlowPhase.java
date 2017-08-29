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
package org.graalvm.compiler.lir.alloc.lsra;

import java.util.ArrayList;
import java.util.BitSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;

import jdk.vm.ci.code.TargetDescription;

/**
 * Phase 6: resolve data flow
 *
 * Insert moves at edges between blocks if intervals have been split.
 */
public class LinearScanResolveDataFlowPhase extends LinearScanAllocationPhase {

    protected final LinearScan allocator;

    protected LinearScanResolveDataFlowPhase(LinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        resolveDataFlow();
        allocator.printIntervals("After resolve data flow");
    }

    protected void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
        assert moveResolver.checkEmpty();
        assert midBlock == null ||
                        (midBlock.getPredecessorCount() == 1 && midBlock.getSuccessorCount() == 1 && midBlock.getPredecessors()[0].equals(fromBlock) && midBlock.getSuccessors()[0].equals(
                                        toBlock));

        int toBlockFirstInstructionId = allocator.getFirstLirInstructionId(toBlock);
        int fromBlockLastInstructionId = allocator.getLastLirInstructionId(fromBlock) + 1;
        int numOperands = allocator.operandSize();
        BitSet liveAtEdge = allocator.getBlockData(toBlock).liveIn;

        // visit all variables for which the liveAtEdge bit is set
        for (int operandNum = liveAtEdge.nextSetBit(0); operandNum >= 0; operandNum = liveAtEdge.nextSetBit(operandNum + 1)) {
            assert operandNum < numOperands : "live information set for not exisiting interval";
            assert allocator.getBlockData(fromBlock).liveOut.get(operandNum) && allocator.getBlockData(toBlock).liveIn.get(operandNum) : "interval not live at this edge";

            Interval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(operandNum), fromBlockLastInstructionId, LIRInstruction.OperandMode.DEF);
            Interval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(operandNum), toBlockFirstInstructionId, LIRInstruction.OperandMode.DEF);

            if (fromInterval != toInterval && !fromInterval.location().equals(toInterval.location())) {
                // need to insert move instruction
                moveResolver.addMapping(fromInterval, toInterval);
            }
        }
    }

    void resolveFindInsertPos(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, MoveResolver moveResolver) {
        DebugContext debug = allocator.getDebug();
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

            if (allocator.detailedAsserts) {
                assert allocator.getLIR().getLIRforBlock(fromBlock).get(0) instanceof StandardOp.LabelOp : "block does not start with a label";

                /*
                 * Because the number of predecessor edges matches the number of successor edges,
                 * blocks which are reached by switch statements may have be more than one
                 * predecessor but it will be guaranteed that all predecessors will be the same.
                 */
                for (AbstractBlockBase<?> predecessor : toBlock.getPredecessors()) {
                    assert fromBlock == predecessor : "all critical edges must be broken";
                }
            }

            moveResolver.setInsertPosition(allocator.getLIR().getLIRforBlock(toBlock), 1);
        }
    }

    /**
     * Inserts necessary moves (spilling or reloading) at edges between blocks for intervals that
     * have been split.
     */
    @SuppressWarnings("try")
    protected void resolveDataFlow() {
        try (Indent indent = allocator.getDebug().logAndIndent("resolve data flow")) {

            MoveResolver moveResolver = allocator.createMoveResolver();
            BitSet blockCompleted = new BitSet(allocator.blockCount());

            optimizeEmptyBlocks(moveResolver, blockCompleted);

            resolveDataFlow0(moveResolver, blockCompleted);

        }
    }

    protected void optimizeEmptyBlocks(MoveResolver moveResolver, BitSet blockCompleted) {
        for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {

            // check if block has only one predecessor and only one successor
            if (block.getPredecessorCount() == 1 && block.getSuccessorCount() == 1) {
                ArrayList<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(block);
                assert instructions.get(0) instanceof StandardOp.LabelOp : "block must start with label";
                assert instructions.get(instructions.size() - 1) instanceof StandardOp.JumpOp : "block with successor must end with unconditional jump";

                // check if block is empty (only label and branch)
                if (instructions.size() == 2) {
                    AbstractBlockBase<?> pred = block.getPredecessors()[0];
                    AbstractBlockBase<?> sux = block.getSuccessors()[0];

                    // prevent optimization of two consecutive blocks
                    if (!blockCompleted.get(pred.getLinearScanNumber()) && !blockCompleted.get(sux.getLinearScanNumber())) {
                        DebugContext debug = allocator.getDebug();
                        if (debug.isLogEnabled()) {
                            debug.log(" optimizing empty block B%d (pred: B%d, sux: B%d)", block.getId(), pred.getId(), sux.getId());
                        }

                        blockCompleted.set(block.getLinearScanNumber());

                        /*
                         * Directly resolve between pred and sux (without looking at the empty block
                         * between).
                         */
                        resolveCollectMappings(pred, sux, block, moveResolver);
                        if (moveResolver.hasMappings()) {
                            moveResolver.setInsertPosition(instructions, 1);
                            moveResolver.resolveAndAppendMoves();
                        }
                    }
                }
            }
        }
    }

    protected void resolveDataFlow0(MoveResolver moveResolver, BitSet blockCompleted) {
        BitSet alreadyResolved = new BitSet(allocator.blockCount());
        for (AbstractBlockBase<?> fromBlock : allocator.sortedBlocks()) {
            if (!blockCompleted.get(fromBlock.getLinearScanNumber())) {
                alreadyResolved.clear();
                alreadyResolved.or(blockCompleted);

                for (AbstractBlockBase<?> toBlock : fromBlock.getSuccessors()) {

                    /*
                     * Check for duplicate edges between the same blocks (can happen with switch
                     * blocks).
                     */
                    if (!alreadyResolved.get(toBlock.getLinearScanNumber())) {
                        DebugContext debug = allocator.getDebug();
                        if (debug.isLogEnabled()) {
                            debug.log("processing edge between B%d and B%d", fromBlock.getId(), toBlock.getId());
                        }

                        alreadyResolved.set(toBlock.getLinearScanNumber());

                        // collect all intervals that have been split between
                        // fromBlock and toBlock
                        resolveCollectMappings(fromBlock, toBlock, null, moveResolver);
                        if (moveResolver.hasMappings()) {
                            resolveFindInsertPos(fromBlock, toBlock, moveResolver);
                            moveResolver.resolveAndAppendMoves();
                        }
                    }
                }
            }
        }
    }

}
