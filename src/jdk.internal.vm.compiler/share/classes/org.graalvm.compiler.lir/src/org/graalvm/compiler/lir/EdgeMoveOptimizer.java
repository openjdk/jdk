/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.StandardOp.MoveOp;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.TargetDescription;

/**
 * This class optimizes moves, particularly those that result from eliminating SSA form.
 * <p>
 * When a block has more than one predecessor, and all predecessors end with the
 * {@linkplain Optimizer#same(LIRInstruction, LIRInstruction) same} sequence of {@linkplain MoveOp
 * move} instructions, then these sequences can be replaced with a single copy of the sequence at
 * the beginning of the block.
 * <p>
 * Similarly, when a block has more than one successor, then same sequences of moves at the
 * beginning of the successors can be placed once at the end of the block. But because the moves
 * must be inserted before all branch instructions, this works only when there is exactly one
 * conditional branch at the end of the block (because the moves must be inserted before all
 * branches, but after all compares).
 * <p>
 * This optimization affects all kind of moves (reg-&gt;reg, reg-&gt;stack and stack-&gt;reg).
 * Because this optimization works best when a block contains only a few moves, it has a huge impact
 * on the number of blocks that are totally empty.
 */
public final class EdgeMoveOptimizer extends PostAllocationOptimizationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        LIR ir = lirGenRes.getLIR();
        Optimizer optimizer = new Optimizer(ir);

        AbstractBlockBase<?>[] blockList = ir.linearScanOrder();
        // ignore the first block in the list (index 0 is not processed)
        for (int i = blockList.length - 1; i >= 1; i--) {
            AbstractBlockBase<?> block = blockList[i];

            if (block.getPredecessorCount() > 1) {
                optimizer.optimizeMovesAtBlockEnd(block);
            }
            if (block.getSuccessorCount() == 2) {
                optimizer.optimizeMovesAtBlockBegin(block);
            }
        }
    }

    private static final class Optimizer {
        private final List<List<LIRInstruction>> edgeInstructionSeqences;
        private LIR ir;

        Optimizer(LIR ir) {
            this.ir = ir;
            edgeInstructionSeqences = new ArrayList<>(4);
        }

        /**
         * Determines if two operations are both {@linkplain MoveOp moves} that have the same source
         * and {@linkplain MoveOp#getResult() destination} operands.
         *
         * @param op1 the first instruction to compare
         * @param op2 the second instruction to compare
         * @return {@code true} if {@code op1} and {@code op2} are the same by the above algorithm
         */
        private static boolean same(LIRInstruction op1, LIRInstruction op2) {
            assert op1 != null;
            assert op2 != null;

            if (ValueMoveOp.isValueMoveOp(op1) && ValueMoveOp.isValueMoveOp(op2)) {
                ValueMoveOp move1 = ValueMoveOp.asValueMoveOp(op1);
                ValueMoveOp move2 = ValueMoveOp.asValueMoveOp(op2);
                if (move1.getInput().equals(move2.getInput()) && move1.getResult().equals(move2.getResult())) {
                    // these moves are exactly equal and can be optimized
                    return true;
                }
            } else if (LoadConstantOp.isLoadConstantOp(op1) && LoadConstantOp.isLoadConstantOp(op2)) {
                LoadConstantOp move1 = LoadConstantOp.asLoadConstantOp(op1);
                LoadConstantOp move2 = LoadConstantOp.asLoadConstantOp(op2);
                if (move1.getConstant().equals(move2.getConstant()) && move1.getResult().equals(move2.getResult())) {
                    // these moves are exactly equal and can be optimized
                    return true;
                }
            }
            return false;
        }

        /**
         * Moves the longest {@linkplain #same common} subsequence at the end all predecessors of
         * {@code block} to the start of {@code block}.
         */
        private void optimizeMovesAtBlockEnd(AbstractBlockBase<?> block) {
            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                if (pred == block) {
                    // currently we can't handle this correctly.
                    return;
                }
            }

            // clear all internal data structures
            edgeInstructionSeqences.clear();

            int numPreds = block.getPredecessorCount();
            assert numPreds > 1 : "do not call otherwise";

            // setup a list with the LIR instructions of all predecessors
            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                assert pred != null;
                assert ir.getLIRforBlock(pred) != null;
                ArrayList<LIRInstruction> predInstructions = ir.getLIRforBlock(pred);

                if (pred.getSuccessorCount() != 1) {
                    // this can happen with switch-statements where multiple edges are between
                    // the same blocks.
                    return;
                }

                assert pred.getSuccessors()[0] == block : "invalid control flow";
                assert predInstructions.get(predInstructions.size() - 1) instanceof StandardOp.JumpOp : "block must end with unconditional jump";

                if (predInstructions.get(predInstructions.size() - 1).hasState()) {
                    // can not optimize instructions that have debug info
                    return;
                }

                // ignore the unconditional branch at the end of the block
                List<LIRInstruction> seq = predInstructions.subList(0, predInstructions.size() - 1);
                edgeInstructionSeqences.add(seq);
            }

            // process lir-instructions while all predecessors end with the same instruction
            while (true) {
                List<LIRInstruction> seq = edgeInstructionSeqences.get(0);
                if (seq.isEmpty()) {
                    return;
                }

                LIRInstruction op = last(seq);
                for (int i = 1; i < numPreds; ++i) {
                    List<LIRInstruction> otherSeq = edgeInstructionSeqences.get(i);
                    if (otherSeq.isEmpty() || !same(op, last(otherSeq))) {
                        return;
                    }
                }

                // insert the instruction at the beginning of the current block
                ir.getLIRforBlock(block).add(1, op);

                // delete the instruction at the end of all predecessors
                for (int i = 0; i < numPreds; i++) {
                    seq = edgeInstructionSeqences.get(i);
                    removeLast(seq);
                }
            }
        }

        /**
         * Moves the longest {@linkplain #same common} subsequence at the start of all successors of
         * {@code block} to the end of {@code block} just prior to the branch instruction ending
         * {@code block}.
         */
        private void optimizeMovesAtBlockBegin(AbstractBlockBase<?> block) {

            edgeInstructionSeqences.clear();
            int numSux = block.getSuccessorCount();

            ArrayList<LIRInstruction> instructions = ir.getLIRforBlock(block);

            assert numSux == 2 : "method should not be called otherwise";

            LIRInstruction lastInstruction = instructions.get(instructions.size() - 1);
            if (lastInstruction.hasState()) {
                // cannot optimize instructions when debug info is needed
                return;
            }

            LIRInstruction branch = lastInstruction;
            if (!(branch instanceof StandardOp.BranchOp) || branch.hasOperands()) {
                // Only blocks that end with a conditional branch are optimized.
                // In addition, a conditional branch with operands (including state) cannot
                // be optimized. Moving a successor instruction before such a branch may
                // interfere with the operands of the branch. For example, a successive move
                // instruction may redefine an input operand of the branch.
                return;
            }

            // Now it is guaranteed that the block ends with a conditional branch.
            // The instructions are inserted at the end of the block before the branch.
            int insertIdx = instructions.size() - 1;

            // setup a list with the lir-instructions of all successors
            for (AbstractBlockBase<?> sux : block.getSuccessors()) {
                ArrayList<LIRInstruction> suxInstructions = ir.getLIRforBlock(sux);

                assert suxInstructions.get(0) instanceof StandardOp.LabelOp : "block must start with label";

                if (sux.getPredecessorCount() != 1) {
                    // this can happen with switch-statements where multiple edges are between
                    // the same blocks.
                    return;
                }
                assert sux.getPredecessors()[0] == block : "invalid control flow";

                // ignore the label at the beginning of the block
                List<LIRInstruction> seq = suxInstructions.subList(1, suxInstructions.size());
                edgeInstructionSeqences.add(seq);
            }

            // process LIR instructions while all successors begin with the same instruction
            while (true) {
                List<LIRInstruction> seq = edgeInstructionSeqences.get(0);
                if (seq.isEmpty()) {
                    return;
                }

                LIRInstruction op = first(seq);
                for (int i = 1; i < numSux; i++) {
                    List<LIRInstruction> otherSeq = edgeInstructionSeqences.get(i);
                    if (otherSeq.isEmpty() || !same(op, first(otherSeq))) {
                        // these instructions are different and cannot be optimized .
                        // no further optimization possible
                        return;
                    }
                }

                // insert instruction at end of current block
                ir.getLIRforBlock(block).add(insertIdx, op);
                insertIdx++;

                // delete the instructions at the beginning of all successors
                for (int i = 0; i < numSux; i++) {
                    seq = edgeInstructionSeqences.get(i);
                    removeFirst(seq);
                }
            }
        }

        /**
         * Gets the first element from a LIR instruction sequence.
         */
        private static LIRInstruction first(List<LIRInstruction> seq) {
            return seq.get(0);
        }

        /**
         * Gets the last element from a LIR instruction sequence.
         */
        private static LIRInstruction last(List<LIRInstruction> seq) {
            return seq.get(seq.size() - 1);
        }

        /**
         * Removes the first element from a LIR instruction sequence.
         */
        private static void removeFirst(List<LIRInstruction> seq) {
            seq.remove(0);
        }

        /**
         * Removes the last element from a LIR instruction sequence.
         */
        private static void removeLast(List<LIRInstruction> seq) {
            seq.remove(seq.size() - 1);
        }

    }
}
