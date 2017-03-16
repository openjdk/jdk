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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.meta.Value;

public final class FastSSIBuilder extends SSIBuilderBase {

    /**
     * Bit map specifying which operands are live upon entry to this block. These are values used in
     * this block or any of its successors where such value are not defined in this block. The bit
     * index of an operand is its {@linkplain #operandNumber operand number}.
     */
    private final BitSet[] liveIns;

    /**
     * Bit map specifying which operands are live upon exit from this block. These are values used
     * in a successor block that are either defined in this block or were live upon entry to this
     * block. The bit index of an operand is its {@linkplain #operandNumber operand number}.
     */
    private final BitSet[] liveOuts;

    private final AbstractBlockBase<?>[] blocks;

    protected FastSSIBuilder(LIR lir) {
        super(lir);
        int numBlocks = lir.getControlFlowGraph().getBlocks().length;
        this.liveIns = new BitSet[numBlocks];
        this.liveOuts = new BitSet[numBlocks];
        this.blocks = lir.getControlFlowGraph().getBlocks();
    }

    @Override
    BitSet getLiveIn(final AbstractBlockBase<?> block) {
        return liveIns[block.getId()];
    }

    @Override
    BitSet getLiveOut(final AbstractBlockBase<?> block) {
        return liveOuts[block.getId()];
    }

    private void setLiveIn(final AbstractBlockBase<?> block, final BitSet liveIn) {
        liveIns[block.getId()] = liveIn;
    }

    private void setLiveOut(final AbstractBlockBase<?> block, final BitSet liveOut) {
        liveOuts[block.getId()] = liveOut;
    }

    @Override
    protected void buildIntern() {
        Debug.log(1, "SSIConstruction block order: %s", Arrays.asList(blocks));
        computeLiveness();
    }

    /**
     * Gets the size of the {@link #liveIns} and {@link #liveOuts} sets for a basic block.
     */
    private int liveSetSize() {
        return lir.numVariables();
    }

    private static int operandNumber(Value operand) {
        if (isVariable(operand)) {
            return asVariable(operand).index;
        }
        throw GraalError.shouldNotReachHere("Can only handle Variables: " + operand);
    }

    /**
     * Computes live sets for each block.
     */
    @SuppressWarnings("try")
    private void computeLiveness() {
        // iterate all blocks
        for (int i = blocks.length - 1; i >= 0; i--) {
            final AbstractBlockBase<?> block = blocks[i];
            try (Indent indent = Debug.logAndIndent(LOG_LEVEL, "compute local live sets for block %s", block)) {

                final BitSet liveIn = mergeLiveSets(block);
                setLiveOut(block, (BitSet) liveIn.clone());

                InstructionValueConsumer useConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processUse(liveIn, operand);
                    }
                };
                InstructionValueConsumer defConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processDef(liveIn, operand, operands);
                    }
                };
                if (Debug.isLogEnabled()) {
                    Debug.log(LOG_LEVEL, "liveOut B%d %s", block.getId(), getLiveOut(block));
                }

                // iterate all instructions of the block
                ArrayList<LIRInstruction> instructions = getLIR().getLIRforBlock(block);
                for (int j = instructions.size() - 1; j >= 0; j--) {
                    final LIRInstruction op = instructions.get(j);

                    try (Indent indent2 = Debug.logAndIndent(LOG_LEVEL, "handle op %d: %s", op.id(), op)) {
                        op.visitEachOutput(defConsumer);
                        op.visitEachTemp(defConsumer);
                        op.visitEachState(useConsumer);
                        op.visitEachAlive(useConsumer);
                        op.visitEachInput(useConsumer);
                    }
                } // end of instruction iteration

                setLiveIn(block, liveIn);
                if (block.isLoopHeader()) {
                    handleLoopHeader(block.getLoop(), liveIn);
                }

                if (Debug.isLogEnabled()) {
                    Debug.log(LOG_LEVEL, "liveIn  B%d %s", block.getId(), getLiveIn(block));
                }

            }
        } // end of block iteration
    }

    /**
     * All variables live at the beginning of a loop are live throughout the loop.
     */
    private void handleLoopHeader(Loop<?> loop, BitSet live) {
        for (AbstractBlockBase<?> block : loop.getBlocks()) {
            getLiveIn(block).or(live);
            getLiveOut(block).or(live);
        }
    }

    private BitSet mergeLiveSets(final AbstractBlockBase<?> block) {
        assert block != null;
        final BitSet liveOut = new BitSet(liveSetSize());
        for (AbstractBlockBase<?> successor : block.getSuccessors()) {
            BitSet succLiveIn = getLiveIn(successor);
            if (succLiveIn != null) {
                liveOut.or(succLiveIn);
            } else {
                assert successor.isLoopHeader() : "Successor of " + block + " not yet processed and not loop header: " + successor;
            }
        }
        return liveOut;
    }

    private static void processUse(final BitSet liveGen, Value operand) {
        if (isVariable(operand)) {
            int operandNum = operandNumber(operand);
            liveGen.set(operandNum);
            if (Debug.isLogEnabled()) {
                Debug.log(LOG_LEVEL, "liveGen for operand %d(%s)", operandNum, operand);
            }
        }
    }

    private static void processDef(final BitSet liveGen, Value operand, Value[] operands) {
        if (isVariable(operand)) {
            int operandNum = operandNumber(operand);
            if (operands[operandNum] == null) {
                operands[operandNum] = operand;
            }
            liveGen.clear(operandNum);
            if (Debug.isLogEnabled()) {
                Debug.log(LOG_LEVEL, "liveKill for operand %d(%s)", operandNum, operand);
            }
        }
    }
}
