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

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.debug.LIRGenerationDebugContext.getSourceForOperandFromDebugContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;

import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.alloc.ComputeBlockOrder;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.util.BitMap2D;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterPriority;
import org.graalvm.compiler.lir.alloc.lsra.Interval.SpillState;
import org.graalvm.compiler.lir.alloc.lsra.LinearScan.BlockData;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class LinearScanLifetimeAnalysisPhase extends LinearScanAllocationPhase {

    protected final LinearScan allocator;
    protected final DebugContext debug;

    /**
     * @param linearScan
     */
    protected LinearScanLifetimeAnalysisPhase(LinearScan linearScan) {
        allocator = linearScan;
        debug = allocator.getDebug();
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        numberInstructions();
        debug.dump(DebugContext.VERBOSE_LEVEL, lirGenRes.getLIR(), "Before register allocation");
        computeLocalLiveSets();
        computeGlobalLiveSets();
        buildIntervals(Assertions.detailedAssertionsEnabled(allocator.getOptions()));
    }

    /**
     * Bit set for each variable that is contained in each loop.
     */
    private BitMap2D intervalInLoop;

    boolean isIntervalInLoop(int interval, int loop) {
        return intervalInLoop.at(interval, loop);
    }

    /**
     * Numbers all instructions in all blocks. The numbering follows the
     * {@linkplain ComputeBlockOrder linear scan order}.
     */
    protected void numberInstructions() {

        allocator.initIntervals();

        ValueConsumer setVariableConsumer = (value, mode, flags) -> {
            if (isVariable(value)) {
                allocator.getOrCreateInterval(asVariable(value));
            }
        };

        // Assign IDs to LIR nodes and build a mapping, lirOps, from ID to LIRInstruction node.
        int numInstructions = 0;
        for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
            numInstructions += allocator.getLIR().getLIRforBlock(block).size();
        }

        // initialize with correct length
        allocator.initOpIdMaps(numInstructions);

        int opId = 0;
        int index = 0;
        for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
            allocator.initBlockData(block);

            ArrayList<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(block);

            int numInst = instructions.size();
            for (int j = 0; j < numInst; j++) {
                LIRInstruction op = instructions.get(j);
                op.setId(opId);

                allocator.putOpIdMaps(index, op, block);
                assert allocator.instructionForId(opId) == op : "must match";

                op.visitEachTemp(setVariableConsumer);
                op.visitEachOutput(setVariableConsumer);

                index++;
                opId += 2; // numbering of lirOps by two
            }
        }
        assert index == numInstructions : "must match";
        assert (index << 1) == opId : "must match: " + (index << 1);
    }

    /**
     * Computes local live sets (i.e. {@link BlockData#liveGen} and {@link BlockData#liveKill})
     * separately for each block.
     */
    @SuppressWarnings("try")
    void computeLocalLiveSets() {
        int liveSize = allocator.liveSetSize();

        intervalInLoop = new BitMap2D(allocator.operandSize(), allocator.numLoops());

        try {
            final BitSet liveGenScratch = new BitSet(liveSize);
            final BitSet liveKillScratch = new BitSet(liveSize);
            // iterate all blocks
            for (final AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                try (Indent indent = debug.logAndIndent("compute local live sets for block %s", block)) {

                    liveGenScratch.clear();
                    liveKillScratch.clear();

                    ArrayList<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(block);
                    int numInst = instructions.size();

                    ValueConsumer useConsumer = (operand, mode, flags) -> {
                        if (isVariable(operand)) {
                            int operandNum = allocator.operandNumber(operand);
                            if (!liveKillScratch.get(operandNum)) {
                                liveGenScratch.set(operandNum);
                                if (debug.isLogEnabled()) {
                                    debug.log("liveGen for operand %d(%s)", operandNum, operand);
                                }
                            }
                            if (block.getLoop() != null) {
                                intervalInLoop.setBit(operandNum, block.getLoop().getIndex());
                            }
                        }

                        if (allocator.detailedAsserts) {
                            verifyInput(block, liveKillScratch, operand);
                        }
                    };
                    ValueConsumer stateConsumer = (operand, mode, flags) -> {
                        if (LinearScan.isVariableOrRegister(operand)) {
                            int operandNum = allocator.operandNumber(operand);
                            if (!liveKillScratch.get(operandNum)) {
                                liveGenScratch.set(operandNum);
                                if (debug.isLogEnabled()) {
                                    debug.log("liveGen in state for operand %d(%s)", operandNum, operand);
                                }
                            }
                        }
                    };
                    ValueConsumer defConsumer = (operand, mode, flags) -> {
                        if (isVariable(operand)) {
                            int varNum = allocator.operandNumber(operand);
                            liveKillScratch.set(varNum);
                            if (debug.isLogEnabled()) {
                                debug.log("liveKill for operand %d(%s)", varNum, operand);
                            }
                            if (block.getLoop() != null) {
                                intervalInLoop.setBit(varNum, block.getLoop().getIndex());
                            }
                        }

                        if (allocator.detailedAsserts) {
                            /*
                             * Fixed intervals are never live at block boundaries, so they need not
                             * be processed in live sets. Process them only in debug mode so that
                             * this can be checked
                             */
                            verifyTemp(liveKillScratch, operand);
                        }
                    };

                    // iterate all instructions of the block
                    for (int j = 0; j < numInst; j++) {
                        final LIRInstruction op = instructions.get(j);

                        try (Indent indent2 = debug.logAndIndent("handle op %d: %s", op.id(), op)) {
                            op.visitEachInput(useConsumer);
                            op.visitEachAlive(useConsumer);
                            /*
                             * Add uses of live locals from interpreter's point of view for proper
                             * debug information generation.
                             */
                            op.visitEachState(stateConsumer);
                            op.visitEachTemp(defConsumer);
                            op.visitEachOutput(defConsumer);
                        }
                    } // end of instruction iteration

                    BlockData blockSets = allocator.getBlockData(block);
                    blockSets.liveGen = trimClone(liveGenScratch);
                    blockSets.liveKill = trimClone(liveKillScratch);
                    // sticky size, will get non-sticky in computeGlobalLiveSets
                    blockSets.liveIn = new BitSet(0);
                    blockSets.liveOut = new BitSet(0);

                    if (debug.isLogEnabled()) {
                        debug.log("liveGen  B%d %s", block.getId(), blockSets.liveGen);
                        debug.log("liveKill B%d %s", block.getId(), blockSets.liveKill);
                    }

                }
            } // end of block iteration
        } catch (OutOfMemoryError oom) {
            throw new PermanentBailoutException(oom, "Out-of-memory during live set allocation of size %d", liveSize);
        }
    }

    private void verifyTemp(BitSet liveKill, Value operand) {
        /*
         * Fixed intervals are never live at block boundaries, so they need not be processed in live
         * sets. Process them only in debug mode so that this can be checked
         */
        if (isRegister(operand)) {
            if (allocator.isProcessed(operand)) {
                liveKill.set(allocator.operandNumber(operand));
            }
        }
    }

    private void verifyInput(AbstractBlockBase<?> block, BitSet liveKill, Value operand) {
        /*
         * Fixed intervals are never live at block boundaries, so they need not be processed in live
         * sets. This is checked by these assertions to be sure about it. The entry block may have
         * incoming values in registers, which is ok.
         */
        if (isRegister(operand) && block != allocator.getLIR().getControlFlowGraph().getStartBlock()) {
            if (allocator.isProcessed(operand)) {
                assert liveKill.get(allocator.operandNumber(operand)) : "using fixed register " + asRegister(operand) + " that is not defined in this block " + block;
            }
        }
    }

    /**
     * Performs a backward dataflow analysis to compute global live sets (i.e.
     * {@link BlockData#liveIn} and {@link BlockData#liveOut}) for each block.
     */
    @SuppressWarnings("try")
    protected void computeGlobalLiveSets() {
        try (Indent indent = debug.logAndIndent("compute global live sets")) {
            int numBlocks = allocator.blockCount();
            boolean changeOccurred;
            boolean changeOccurredInBlock;
            int iterationCount = 0;
            BitSet scratch = new BitSet(allocator.liveSetSize()); // scratch set for calculations

            /*
             * Perform a backward dataflow analysis to compute liveOut and liveIn for each block.
             * The loop is executed until a fixpoint is reached (no changes in an iteration).
             */
            do {
                changeOccurred = false;

                try (Indent indent2 = debug.logAndIndent("new iteration %d", iterationCount)) {

                    // iterate all blocks in reverse order
                    for (int i = numBlocks - 1; i >= 0; i--) {
                        AbstractBlockBase<?> block = allocator.blockAt(i);
                        BlockData blockSets = allocator.getBlockData(block);

                        changeOccurredInBlock = false;

                        /*
                         * liveOut(block) is the union of liveIn(sux), for successors sux of block.
                         */
                        int n = block.getSuccessorCount();
                        if (n > 0) {
                            scratch.clear();
                            // block has successors
                            if (n > 0) {
                                for (AbstractBlockBase<?> successor : block.getSuccessors()) {
                                    scratch.or(allocator.getBlockData(successor).liveIn);
                                }
                            }

                            if (!blockSets.liveOut.equals(scratch)) {
                                blockSets.liveOut = trimClone(scratch);

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
                             *
                             * Note: liveIn set can only grow, never shrink. No need to clear it.
                             */
                            BitSet liveIn = blockSets.liveIn;
                            /*
                             * BitSet#or will call BitSet#ensureSize (since the bit set is of length
                             * 0 initially) and set sticky to false
                             */
                            liveIn.or(blockSets.liveOut);
                            liveIn.andNot(blockSets.liveKill);
                            liveIn.or(blockSets.liveGen);

                            liveIn.clone(); // trimToSize()

                            if (debug.isLogEnabled()) {
                                debug.log("block %d: livein = %s,  liveout = %s", block.getId(), liveIn, blockSets.liveOut);
                            }
                        }
                    }
                    iterationCount++;

                    if (changeOccurred && iterationCount > 50) {
                        /*
                         * Very unlikely, should never happen: If it happens we cannot guarantee it
                         * won't happen again.
                         */
                        throw new PermanentBailoutException("too many iterations in computeGlobalLiveSets");
                    }
                }
            } while (changeOccurred);

            if (Assertions.detailedAssertionsEnabled(allocator.getOptions())) {
                verifyLiveness();
            }

            // check that the liveIn set of the first block is empty
            AbstractBlockBase<?> startBlock = allocator.getLIR().getControlFlowGraph().getStartBlock();
            if (allocator.getBlockData(startBlock).liveIn.cardinality() != 0) {
                if (Assertions.detailedAssertionsEnabled(allocator.getOptions())) {
                    reportFailure(numBlocks);
                }
                // bailout if this occurs in product mode.
                throw new GraalError("liveIn set of first block must be empty: " + allocator.getBlockData(startBlock).liveIn);
            }
        }
    }

    /**
     * Creates a trimmed copy a bit set.
     *
     * {@link BitSet#clone()} cannot be used since it will not {@linkplain BitSet#trimToSize trim}
     * the array if the bit set is {@linkplain BitSet#sizeIsSticky sticky}.
     */
    @SuppressWarnings("javadoc")
    private static BitSet trimClone(BitSet set) {
        BitSet trimmedSet = new BitSet(0); // zero-length words array, sticky
        trimmedSet.or(set); // words size ensured to be words-in-use of set,
                            // also makes it non-sticky
        return trimmedSet;
    }

    @SuppressWarnings("try")
    protected void reportFailure(int numBlocks) {
        try (DebugContext.Scope s = debug.forceLog()) {
            try (Indent indent = debug.logAndIndent("report failure")) {

                BitSet startBlockLiveIn = allocator.getBlockData(allocator.getLIR().getControlFlowGraph().getStartBlock()).liveIn;
                try (Indent indent2 = debug.logAndIndent("Error: liveIn set of first block must be empty (when this fails, variables are used before they are defined):")) {
                    for (int operandNum = startBlockLiveIn.nextSetBit(0); operandNum >= 0; operandNum = startBlockLiveIn.nextSetBit(operandNum + 1)) {
                        Interval interval = allocator.intervalFor(operandNum);
                        if (interval != null) {
                            Value operand = interval.operand;
                            debug.log("var %d; operand=%s; node=%s", operandNum, operand, getSourceForOperandFromDebugContext(debug, operand));
                        } else {
                            debug.log("var %d; missing operand", operandNum);
                        }
                    }
                }

                // print some additional information to simplify debugging
                for (int operandNum = startBlockLiveIn.nextSetBit(0); operandNum >= 0; operandNum = startBlockLiveIn.nextSetBit(operandNum + 1)) {
                    Interval interval = allocator.intervalFor(operandNum);
                    Value operand = null;
                    Object valueForOperandFromDebugContext = null;
                    if (interval != null) {
                        operand = interval.operand;
                        valueForOperandFromDebugContext = getSourceForOperandFromDebugContext(debug, operand);
                    }
                    try (Indent indent2 = debug.logAndIndent("---- Detailed information for var %d; operand=%s; node=%s ----", operandNum, operand, valueForOperandFromDebugContext)) {

                        ArrayDeque<AbstractBlockBase<?>> definedIn = new ArrayDeque<>();
                        EconomicSet<AbstractBlockBase<?>> usedIn = EconomicSet.create(Equivalence.IDENTITY);
                        for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                            if (allocator.getBlockData(block).liveGen.get(operandNum)) {
                                usedIn.add(block);
                                try (Indent indent3 = debug.logAndIndent("used in block B%d", block.getId())) {
                                    for (LIRInstruction ins : allocator.getLIR().getLIRforBlock(block)) {
                                        try (Indent indent4 = debug.logAndIndent("%d: %s", ins.id(), ins)) {
                                            ins.forEachState((liveStateOperand, mode, flags) -> {
                                                debug.log("operand=%s", liveStateOperand);
                                                return liveStateOperand;
                                            });
                                        }
                                    }
                                }
                            }
                            if (allocator.getBlockData(block).liveKill.get(operandNum)) {
                                definedIn.add(block);
                                try (Indent indent3 = debug.logAndIndent("defined in block B%d", block.getId())) {
                                    for (LIRInstruction ins : allocator.getLIR().getLIRforBlock(block)) {
                                        debug.log("%d: %s", ins.id(), ins);
                                    }
                                }
                            }
                        }

                        int[] hitCount = new int[numBlocks];

                        while (!definedIn.isEmpty()) {
                            AbstractBlockBase<?> block = definedIn.removeFirst();
                            usedIn.remove(block);
                            for (AbstractBlockBase<?> successor : block.getSuccessors()) {
                                if (successor.isLoopHeader()) {
                                    if (!block.isLoopEnd()) {
                                        definedIn.add(successor);
                                    }
                                } else {
                                    if (++hitCount[successor.getId()] == successor.getPredecessorCount()) {
                                        definedIn.add(successor);
                                    }
                                }
                            }
                        }
                        try (Indent indent3 = debug.logAndIndent("**** offending usages are in: ")) {
                            for (AbstractBlockBase<?> block : usedIn) {
                                debug.log("B%d", block.getId());
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    protected void verifyLiveness() {
        /*
         * Check that fixed intervals are not live at block boundaries (live set must be empty at
         * fixed intervals).
         */
        for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
            for (int j = 0; j <= allocator.maxRegisterNumber(); j++) {
                assert !allocator.getBlockData(block).liveIn.get(j) : "liveIn  set of fixed register must be empty";
                assert !allocator.getBlockData(block).liveOut.get(j) : "liveOut set of fixed register must be empty";
                assert !allocator.getBlockData(block).liveGen.get(j) : "liveGen set of fixed register must be empty";
            }
        }
    }

    protected void addUse(AllocatableValue operand, int from, int to, RegisterPriority registerPriority, ValueKind<?> kind, boolean detailedAsserts) {
        if (!allocator.isProcessed(operand)) {
            return;
        }

        Interval interval = allocator.getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        interval.addRange(from, to);

        // Register use position at even instruction id.
        interval.addUsePos(to & ~1, registerPriority, detailedAsserts);

        if (debug.isLogEnabled()) {
            debug.log("add use: %s, from %d to %d (%s)", interval, from, to, registerPriority.name());
        }
    }

    protected void addTemp(AllocatableValue operand, int tempPos, RegisterPriority registerPriority, ValueKind<?> kind, boolean detailedAsserts) {
        if (!allocator.isProcessed(operand)) {
            return;
        }

        Interval interval = allocator.getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        interval.addRange(tempPos, tempPos + 1);
        interval.addUsePos(tempPos, registerPriority, detailedAsserts);
        interval.addMaterializationValue(null);

        if (debug.isLogEnabled()) {
            debug.log("add temp: %s tempPos %d (%s)", interval, tempPos, RegisterPriority.MustHaveRegister.name());
        }
    }

    protected void addDef(AllocatableValue operand, LIRInstruction op, RegisterPriority registerPriority, ValueKind<?> kind, boolean detailedAsserts) {
        if (!allocator.isProcessed(operand)) {
            return;
        }
        int defPos = op.id();

        Interval interval = allocator.getOrCreateInterval(operand);
        if (!kind.equals(LIRKind.Illegal)) {
            interval.setKind(kind);
        }

        Range r = interval.first();
        if (r.from <= defPos) {
            /*
             * Update the starting point (when a range is first created for a use, its start is the
             * beginning of the current block until a def is encountered).
             */
            r.from = defPos;
            interval.addUsePos(defPos, registerPriority, detailedAsserts);

        } else {
            /*
             * Dead value - make vacuous interval also add register priority for dead intervals
             */
            interval.addRange(defPos, defPos + 1);
            interval.addUsePos(defPos, registerPriority, detailedAsserts);
            if (debug.isLogEnabled()) {
                debug.log("Warning: def of operand %s at %d occurs without use", operand, defPos);
            }
        }

        changeSpillDefinitionPos(op, operand, interval, defPos);
        if (registerPriority == RegisterPriority.None && interval.spillState().ordinal() <= SpillState.StartInMemory.ordinal() && isStackSlot(operand)) {
            // detection of method-parameters and roundfp-results
            interval.setSpillState(SpillState.StartInMemory);
        }
        interval.addMaterializationValue(getMaterializedValue(op, operand, interval));

        if (debug.isLogEnabled()) {
            debug.log("add def: %s defPos %d (%s)", interval, defPos, registerPriority.name());
        }
    }

    /**
     * Optimizes moves related to incoming stack based arguments. The interval for the destination
     * of such moves is assigned the stack slot (which is in the caller's frame) as its spill slot.
     */
    protected void handleMethodArguments(LIRInstruction op) {
        if (ValueMoveOp.isValueMoveOp(op)) {
            ValueMoveOp move = ValueMoveOp.asValueMoveOp(op);
            if (optimizeMethodArgument(move.getInput())) {
                StackSlot slot = asStackSlot(move.getInput());
                if (Assertions.detailedAssertionsEnabled(allocator.getOptions())) {
                    assert op.id() > 0 : "invalid id";
                    assert allocator.blockForId(op.id()).getPredecessorCount() == 0 : "move from stack must be in first block";
                    assert isVariable(move.getResult()) : "result of move must be a variable";

                    if (debug.isLogEnabled()) {
                        debug.log("found move from stack slot %s to %s", slot, move.getResult());
                    }
                }

                Interval interval = allocator.intervalFor(move.getResult());
                interval.setSpillSlot(slot);
                interval.assignLocation(slot);
            }
        }
    }

    protected void addRegisterHint(final LIRInstruction op, final Value targetValue, OperandMode mode, EnumSet<OperandFlag> flags, final boolean hintAtDef) {
        if (flags.contains(OperandFlag.HINT) && LinearScan.isVariableOrRegister(targetValue)) {

            op.forEachRegisterHint(targetValue, mode, (registerHint, valueMode, valueFlags) -> {
                if (LinearScan.isVariableOrRegister(registerHint)) {
                    Interval from = allocator.getOrCreateInterval((AllocatableValue) registerHint);
                    Interval to = allocator.getOrCreateInterval((AllocatableValue) targetValue);

                    /* hints always point from def to use */
                    if (hintAtDef) {
                        to.setLocationHint(from);
                    } else {
                        from.setLocationHint(to);
                    }
                    if (debug.isLogEnabled()) {
                        debug.log("operation at opId %d: added hint from interval %d to %d", op.id(), from.operandNumber, to.operandNumber);
                    }

                    return registerHint;
                }
                return null;
            });
        }
    }

    /**
     * Eliminates moves from register to stack if the stack slot is known to be correct.
     *
     * @param op
     * @param operand
     */
    protected void changeSpillDefinitionPos(LIRInstruction op, AllocatableValue operand, Interval interval, int defPos) {
        assert interval.isSplitParent() : "can only be called for split parents";

        switch (interval.spillState()) {
            case NoDefinitionFound:
                assert interval.spillDefinitionPos() == -1 : "must no be set before";
                interval.setSpillDefinitionPos(defPos);
                interval.setSpillState(SpillState.NoSpillStore);
                break;

            case NoSpillStore:
                assert defPos <= interval.spillDefinitionPos() : "positions are processed in reverse order when intervals are created";
                if (defPos < interval.spillDefinitionPos() - 2) {
                    // second definition found, so no spill optimization possible for this interval
                    interval.setSpillState(SpillState.NoOptimization);
                } else {
                    // two consecutive definitions (because of two-operand LIR form)
                    assert allocator.blockForId(defPos) == allocator.blockForId(interval.spillDefinitionPos()) : "block must be equal";
                }
                break;

            case NoOptimization:
                // nothing to do
                break;

            default:
                throw GraalError.shouldNotReachHere("other states not allowed at this time");
        }
    }

    private static boolean optimizeMethodArgument(Value value) {
        /*
         * Object method arguments that are passed on the stack are currently not optimized because
         * this requires that the runtime visits method arguments during stack walking.
         */
        return isStackSlot(value) && asStackSlot(value).isInCallerFrame() && LIRKind.isValue(value);
    }

    /**
     * Determines the register priority for an instruction's output/result operand.
     */
    protected RegisterPriority registerPriorityOfOutputOperand(LIRInstruction op) {
        if (ValueMoveOp.isValueMoveOp(op)) {
            ValueMoveOp move = ValueMoveOp.asValueMoveOp(op);
            if (optimizeMethodArgument(move.getInput())) {
                return RegisterPriority.None;
            }
        }

        // all other operands require a register
        return RegisterPriority.MustHaveRegister;
    }

    /**
     * Determines the priority which with an instruction's input operand will be allocated a
     * register.
     */
    protected static RegisterPriority registerPriorityOfInputOperand(EnumSet<OperandFlag> flags) {
        if (flags.contains(OperandFlag.STACK)) {
            return RegisterPriority.ShouldHaveRegister;
        }
        // all other operands require a register
        return RegisterPriority.MustHaveRegister;
    }

    @SuppressWarnings("try")
    protected void buildIntervals(boolean detailedAsserts) {

        try (Indent indent = debug.logAndIndent("build intervals")) {
            InstructionValueConsumer outputConsumer = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    addDef((AllocatableValue) operand, op, registerPriorityOfOutputOperand(op), operand.getValueKind(), detailedAsserts);
                    addRegisterHint(op, operand, mode, flags, true);
                }
            };

            InstructionValueConsumer tempConsumer = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    addTemp((AllocatableValue) operand, op.id(), RegisterPriority.MustHaveRegister, operand.getValueKind(), detailedAsserts);
                    addRegisterHint(op, operand, mode, flags, false);
                }
            };

            InstructionValueConsumer aliveConsumer = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    RegisterPriority p = registerPriorityOfInputOperand(flags);
                    int opId = op.id();
                    int blockFrom = allocator.getFirstLirInstructionId((allocator.blockForId(opId)));
                    addUse((AllocatableValue) operand, blockFrom, opId + 1, p, operand.getValueKind(), detailedAsserts);
                    addRegisterHint(op, operand, mode, flags, false);
                }
            };

            InstructionValueConsumer inputConsumer = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    int opId = op.id();
                    int blockFrom = allocator.getFirstLirInstructionId((allocator.blockForId(opId)));
                    RegisterPriority p = registerPriorityOfInputOperand(flags);
                    addUse((AllocatableValue) operand, blockFrom, opId, p, operand.getValueKind(), detailedAsserts);
                    addRegisterHint(op, operand, mode, flags, false);
                }
            };

            InstructionValueConsumer stateProc = (op, operand, mode, flags) -> {
                if (LinearScan.isVariableOrRegister(operand)) {
                    int opId = op.id();
                    int blockFrom = allocator.getFirstLirInstructionId((allocator.blockForId(opId)));
                    addUse((AllocatableValue) operand, blockFrom, opId + 1, RegisterPriority.None, operand.getValueKind(), detailedAsserts);
                }
            };

            // create a list with all caller-save registers (cpu, fpu, xmm)
            RegisterArray callerSaveRegs = allocator.getRegisterAllocationConfig().getRegisterConfig().getCallerSaveRegisters();

            // iterate all blocks in reverse order
            for (int i = allocator.blockCount() - 1; i >= 0; i--) {

                AbstractBlockBase<?> block = allocator.blockAt(i);
                try (Indent indent2 = debug.logAndIndent("handle block %d", block.getId())) {

                    ArrayList<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(block);
                    final int blockFrom = allocator.getFirstLirInstructionId(block);
                    int blockTo = allocator.getLastLirInstructionId(block);

                    assert blockFrom == instructions.get(0).id();
                    assert blockTo == instructions.get(instructions.size() - 1).id();

                    // Update intervals for operands live at the end of this block;
                    BitSet live = allocator.getBlockData(block).liveOut;
                    for (int operandNum = live.nextSetBit(0); operandNum >= 0; operandNum = live.nextSetBit(operandNum + 1)) {
                        assert live.get(operandNum) : "should not stop here otherwise";
                        AllocatableValue operand = allocator.intervalFor(operandNum).operand;
                        if (debug.isLogEnabled()) {
                            debug.log("live in %d: %s", operandNum, operand);
                        }

                        addUse(operand, blockFrom, blockTo + 2, RegisterPriority.None, LIRKind.Illegal, detailedAsserts);

                        /*
                         * Add special use positions for loop-end blocks when the interval is used
                         * anywhere inside this loop. It's possible that the block was part of a
                         * non-natural loop, so it might have an invalid loop index.
                         */
                        if (block.isLoopEnd() && block.getLoop() != null && isIntervalInLoop(operandNum, block.getLoop().getIndex())) {
                            allocator.intervalFor(operandNum).addUsePos(blockTo + 1, RegisterPriority.LiveAtLoopEnd, detailedAsserts);
                        }
                    }

                    /*
                     * Iterate all instructions of the block in reverse order. definitions of
                     * intervals are processed before uses.
                     */
                    for (int j = instructions.size() - 1; j >= 0; j--) {
                        final LIRInstruction op = instructions.get(j);
                        final int opId = op.id();

                        try (Indent indent3 = debug.logAndIndent("handle inst %d: %s", opId, op)) {

                            // add a temp range for each register if operation destroys
                            // caller-save registers
                            if (op.destroysCallerSavedRegisters()) {
                                for (Register r : callerSaveRegs) {
                                    if (allocator.attributes(r).isAllocatable()) {
                                        addTemp(r.asValue(), opId, RegisterPriority.None, LIRKind.Illegal, detailedAsserts);
                                    }
                                }
                                if (debug.isLogEnabled()) {
                                    debug.log("operation destroys all caller-save registers");
                                }
                            }

                            op.visitEachOutput(outputConsumer);
                            op.visitEachTemp(tempConsumer);
                            op.visitEachAlive(aliveConsumer);
                            op.visitEachInput(inputConsumer);

                            /*
                             * Add uses of live locals from interpreter's point of view for proper
                             * debug information generation. Treat these operands as temp values (if
                             * the live range is extended to a call site, the value would be in a
                             * register at the call otherwise).
                             */
                            op.visitEachState(stateProc);

                            // special steps for some instructions (especially moves)
                            handleMethodArguments(op);

                        }

                    } // end of instruction iteration
                }
            } // end of block iteration

            /*
             * Add the range [0, 1] to all fixed intervals. the register allocator need not handle
             * unhandled fixed intervals.
             */
            for (Interval interval : allocator.intervals()) {
                if (interval != null && isRegister(interval.operand)) {
                    interval.addRange(0, 1);
                }
            }
        }
    }

    /**
     * Returns a value for a interval definition, which can be used for re-materialization.
     *
     * @param op An instruction which defines a value
     * @param operand The destination operand of the instruction
     * @param interval The interval for this defined value.
     * @return Returns the value which is moved to the instruction and which can be reused at all
     *         reload-locations in case the interval of this instruction is spilled. Currently this
     *         can only be a {@link JavaConstant}.
     */
    protected Constant getMaterializedValue(LIRInstruction op, Value operand, Interval interval) {
        if (LoadConstantOp.isLoadConstantOp(op)) {
            LoadConstantOp move = LoadConstantOp.asLoadConstantOp(op);

            if (!allocator.neverSpillConstants()) {
                /*
                 * Check if the interval has any uses which would accept an stack location (priority
                 * == ShouldHaveRegister). Rematerialization of such intervals can result in a
                 * degradation, because rematerialization always inserts a constant load, even if
                 * the value is not needed in a register.
                 */
                Interval.UsePosList usePosList = interval.usePosList();
                int numUsePos = usePosList.size();
                for (int useIdx = 0; useIdx < numUsePos; useIdx++) {
                    Interval.RegisterPriority priority = usePosList.registerPriority(useIdx);
                    if (priority == Interval.RegisterPriority.ShouldHaveRegister) {
                        return null;
                    }
                }
            }
            return move.getConstant();
        }
        return null;
    }
}
