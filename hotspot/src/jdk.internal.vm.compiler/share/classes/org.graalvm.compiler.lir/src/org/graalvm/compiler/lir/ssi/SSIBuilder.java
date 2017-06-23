/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.ssi;

import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;

import org.graalvm.compiler.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.meta.Value;

public final class SSIBuilder extends SSIBuilderBase {

    private static class BlockData {

        /**
         * Bit map specifying which operands are live upon entry to this block. These are values
         * used in this block or any of its successors where such value are not defined in this
         * block. The bit index of an operand is its {@linkplain #operandNumber operand number}.
         */
        public BitSet liveIn;

        /**
         * Bit map specifying which operands are live upon exit from this block. These are values
         * used in a successor block that are either defined in this block or were live upon entry
         * to this block. The bit index of an operand is its {@linkplain #operandNumber operand
         * number}.
         */
        public BitSet liveOut;

        /**
         * Bit map specifying which operands are used (before being defined) in this block. That is,
         * these are the values that are live upon entry to the block. The bit index of an operand
         * is its {@linkplain #operandNumber operand number}.
         */
        public BitSet liveGen;

        /**
         * Bit map specifying which operands are defined/overwritten in this block. The bit index of
         * an operand is its {@linkplain #operandNumber operand number}.
         */
        public BitSet liveKill;
    }

    private final BlockMap<SSIBuilder.BlockData> blockData;

    protected SSIBuilder(LIR lir) {
        super(lir);
        this.blockData = new BlockMap<>(lir.getControlFlowGraph());
    }

    @Override
    protected void buildIntern() {
        init();
        computeLocalLiveSets();
        computeGlobalLiveSets();
    }

    /**
     * Gets the size of the {@link BlockData#liveIn} and {@link BlockData#liveOut} sets for a basic
     * block.
     */
    private int liveSetSize() {
        return getLIR().numVariables();
    }

    AbstractBlockBase<?>[] getBlocks() {
        return getLIR().getControlFlowGraph().getBlocks();
    }

    static int operandNumber(Value operand) {
        if (isVariable(operand)) {
            return asVariable(operand).index;
        }
        throw GraalError.shouldNotReachHere("Can only handle Variables: " + operand);
    }

    private SSIBuilder.BlockData getBlockData(AbstractBlockBase<?> block) {
        return blockData.get(block);
    }

    private void initBlockData(AbstractBlockBase<?> block) {
        blockData.put(block, new SSIBuilder.BlockData());
    }

    private void init() {
        for (AbstractBlockBase<?> block : getBlocks()) {
            initBlockData(block);
        }
    }

    /**
     * Computes local live sets (i.e. {@link BlockData#liveGen} and {@link BlockData#liveKill})
     * separately for each block.
     */
    @SuppressWarnings("try")
    private void computeLocalLiveSets() {
        int liveSize = liveSetSize();

        // iterate all blocks
        AbstractBlockBase<?>[] blocks = getBlocks();
        for (int i = blocks.length - 1; i >= 0; i--) {
            final AbstractBlockBase<?> block = blocks[i];
            try (Indent indent = Debug.logAndIndent(LOG_LEVEL, "compute local live sets for block %s", block)) {

                final BitSet liveGen = new BitSet(liveSize);
                final BitSet liveKill = new BitSet(liveSize);

                InstructionValueConsumer useConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processLocalUse(liveGen, operand);
                    }
                };
                InstructionValueConsumer aliveConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processLocalUse(liveGen, operand);
                    }
                };
                InstructionValueConsumer stateConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        if (isVariable(operand)) {
                            int operandNum = operandNumber(operand);
                            if (!liveKill.get(operandNum)) {
                                liveGen.set(operandNum);
                                if (Debug.isLogEnabled()) {
                                    Debug.log(LOG_LEVEL, "liveGen in state for operand %d(%s)", operandNum, operand);
                                }
                            }
                        }
                    }
                };
                InstructionValueConsumer defConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processLocalDef(liveGen, liveKill, operand, operands);
                    }
                };
                InstructionValueConsumer tempConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processLocalDef(liveGen, liveKill, operand, operands);
                    }
                };

                // iterate all instructions of the block
                List<LIRInstruction> instructions = getLIR().getLIRforBlock(block);
                ListIterator<LIRInstruction> instIt = instructions.listIterator(instructions.size());
                while (instIt.hasPrevious()) {
                    final LIRInstruction op = instIt.previous();

                    try (Indent indent2 = Debug.logAndIndent(LOG_LEVEL, "handle op %d: %s", op.id(), op)) {
                        op.visitEachOutput(defConsumer);
                        op.visitEachTemp(tempConsumer);
                        op.visitEachState(stateConsumer);
                        op.visitEachAlive(aliveConsumer);
                        op.visitEachInput(useConsumer);
                    }
                } // end of instruction iteration

                SSIBuilder.BlockData blockSets = getBlockData(block);
                blockSets.liveGen = liveGen;
                blockSets.liveKill = liveKill;
                blockSets.liveIn = new BitSet(liveSize);
                blockSets.liveOut = new BitSet(liveSize);

                if (Debug.isLogEnabled()) {
                    Debug.log(LOG_LEVEL, "liveGen  B%d %s", block.getId(), blockSets.liveGen);
                    Debug.log(LOG_LEVEL, "liveKill B%d %s", block.getId(), blockSets.liveKill);
                }

            }
        } // end of block iteration
    }

    private static void processLocalUse(final BitSet liveGen, Value operand) {
        if (isVariable(operand)) {
            int operandNum = operandNumber(operand);
            liveGen.set(operandNum);
            if (Debug.isLogEnabled()) {
                Debug.log(LOG_LEVEL, "liveGen for operand %d(%s)", operandNum, operand);
            }
        }
    }

    private static void processLocalDef(final BitSet liveGen, final BitSet liveKill, Value operand, Value[] operands) {
        if (isVariable(operand)) {
            int operandNum = operandNumber(operand);
            if (operands[operandNum] == null) {
                operands[operandNum] = operand;
            }
            liveKill.set(operandNum);
            liveGen.clear(operandNum);
            if (Debug.isLogEnabled()) {
                Debug.log(LOG_LEVEL, "liveKill for operand %d(%s)", operandNum, operand);
            }
        }
    }

    /**
     * Performs a backward dataflow analysis to compute global live sets (i.e.
     * {@link BlockData#liveIn} and {@link BlockData#liveOut}) for each block.
     */
    @SuppressWarnings("try")
    private void computeGlobalLiveSets() {
        try (Indent indent = Debug.logAndIndent(LOG_LEVEL, "compute global live sets")) {
            boolean changeOccurred;
            boolean changeOccurredInBlock;
            int iterationCount = 0;
            BitSet liveOut = new BitSet(liveSetSize()); // scratch set for
                                                        // calculations

            /*
             * Perform a backward dataflow analysis to compute liveOut and liveIn for each block.
             * The loop is executed until a fixpoint is reached (no changes in an iteration).
             */
            do {
                changeOccurred = false;

                try (Indent indent2 = Debug.logAndIndent(LOG_LEVEL, "new iteration %d", iterationCount)) {

                    // iterate all blocks in reverse order
                    AbstractBlockBase<?>[] blocks = getBlocks();
                    for (int i = blocks.length - 1; i >= 0; i--) {
                        final AbstractBlockBase<?> block = blocks[i];
                        SSIBuilder.BlockData blockSets = getBlockData(block);

                        changeOccurredInBlock = false;

                        /*
                         * liveOut(block) is the union of liveIn(sux), for successors sux of block.
                         */
                        int n = block.getSuccessorCount();
                        if (n > 0) {
                            // block has successors
                            liveOut.clear();
                            for (AbstractBlockBase<?> successor : block.getSuccessors()) {
                                liveOut.or(getBlockData(successor).liveIn);
                            }

                            if (!blockSets.liveOut.equals(liveOut)) {
                                /*
                                 * A change occurred. Swap the old and new live out sets to avoid
                                 * copying.
                                 */
                                BitSet temp = blockSets.liveOut;
                                blockSets.liveOut = liveOut;
                                liveOut = temp;

                                changeOccurred = true;
                                changeOccurredInBlock = true;
                            }
                        }

                        if (iterationCount == 0 || changeOccurredInBlock) {
                            /*
                             * liveIn(block) is the union of liveGen(block) with (liveOut(block) &
                             * !liveKill(block)).
                             *
                             * Note: liveIn has to be computed only in first iteration or if liveOut
                             * has changed!
                             */
                            BitSet liveIn = blockSets.liveIn;
                            liveIn.clear();
                            liveIn.or(blockSets.liveOut);
                            liveIn.andNot(blockSets.liveKill);
                            liveIn.or(blockSets.liveGen);

                            if (Debug.isLogEnabled()) {
                                Debug.log(LOG_LEVEL, "block %d: livein = %s,  liveout = %s", block.getId(), liveIn, blockSets.liveOut);
                            }
                        }
                    }
                    iterationCount++;

                    if (changeOccurred && iterationCount > 50) {
                        /*
                         * Very unlikely should never happen: If it happens we cannot guarantee it
                         * won't happen again.
                         */
                        throw new PermanentBailoutException("too many iterations in computeGlobalLiveSets");
                    }
                }
            } while (changeOccurred);
        }
    }

    @Override
    BitSet getLiveIn(final AbstractBlockBase<?> block) {
        return getBlockData(block).liveIn;
    }

    @Override
    BitSet getLiveOut(final AbstractBlockBase<?> block) {
        return getBlockData(block).liveOut;
    }
}
