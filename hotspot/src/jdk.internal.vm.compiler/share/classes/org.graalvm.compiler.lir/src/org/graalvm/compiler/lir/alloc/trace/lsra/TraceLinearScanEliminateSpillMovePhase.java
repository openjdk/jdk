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
package org.graalvm.compiler.lir.alloc.trace.lsra;

import static org.graalvm.compiler.core.common.GraalOptions.DetailedAsserts;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.List;

import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.LoadConstantOp;
import org.graalvm.compiler.lir.StandardOp.MoveOp;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceInterval.SpillState;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanPhase.IntervalPredicate;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanPhase.TraceLinearScan;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;

final class TraceLinearScanEliminateSpillMovePhase extends TraceLinearScanAllocationPhase {

    private static final IntervalPredicate spilledIntervals = new TraceLinearScanPhase.IntervalPredicate() {

        @Override
        public boolean apply(TraceInterval i) {
            return i.isSplitParent() && SpillState.IN_MEMORY.contains(i.spillState());
        }
    };

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, TraceLinearScanAllocationContext context) {
        TraceBuilderResult traceBuilderResult = context.resultTraces;
        TraceLinearScan allocator = context.allocator;
        boolean shouldEliminateSpillMoves = shouldEliminateSpillMoves(traceBuilderResult, allocator);
        eliminateSpillMoves(allocator, shouldEliminateSpillMoves, traceBuilderResult);
    }

    private static boolean shouldEliminateSpillMoves(TraceBuilderResult traceBuilderResult, TraceLinearScan allocator) {
        return !traceBuilderResult.incomingSideEdges(traceBuilderResult.getTraceForBlock(allocator.blockAt(0)));
    }

    // called once before assignment of register numbers
    @SuppressWarnings("try")
    private static void eliminateSpillMoves(TraceLinearScan allocator, boolean shouldEliminateSpillMoves, TraceBuilderResult traceBuilderResult) {
        try (Indent indent = Debug.logAndIndent("Eliminating unnecessary spill moves: Trace%d", traceBuilderResult.getTraceForBlock(allocator.blockAt(0)).getId())) {
            allocator.sortIntervalsBySpillPos();

            /*
             * collect all intervals that must be stored after their definition. The list is sorted
             * by Interval.spillDefinitionPos.
             */
            TraceInterval interval = allocator.createUnhandledListBySpillPos(spilledIntervals);
            if (DetailedAsserts.getValue()) {
                checkIntervals(interval);
            }
            if (Debug.isLogEnabled()) {
                try (Indent indent2 = Debug.logAndIndent("Sorted intervals")) {
                    for (TraceInterval i = interval; i != null; i = i.next) {
                        Debug.log("%5d: %s", i.spillDefinitionPos(), i);
                    }
                }
            }

            LIRInsertionBuffer insertionBuffer = new LIRInsertionBuffer();
            for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                try (Indent indent1 = Debug.logAndIndent("Handle %s", block)) {
                    List<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(block);
                    int numInst = instructions.size();

                    int lastOpId = -1;
                    // iterate all instructions of the block.
                    for (int j = 0; j < numInst; j++) {
                        LIRInstruction op = instructions.get(j);
                        int opId = op.id();
                        try (Indent indent2 = Debug.logAndIndent("%5d %s", opId, op)) {

                            if (opId == -1) {
                                MoveOp move = (MoveOp) op;
                                /*
                                 * Remove move from register to stack if the stack slot is
                                 * guaranteed to be correct. Only moves that have been inserted by
                                 * LinearScan can be removed.
                                 */
                                if (shouldEliminateSpillMoves && canEliminateSpillMove(allocator, block, move, lastOpId)) {
                                    /*
                                     * Move target is a stack slot that is always correct, so
                                     * eliminate instruction.
                                     */
                                    if (Debug.isLogEnabled()) {
                                        if (move instanceof ValueMoveOp) {
                                            ValueMoveOp vmove = (ValueMoveOp) move;
                                            Debug.log("eliminating move from interval %d (%s) to %d (%s) in block %s", allocator.operandNumber(vmove.getInput()), vmove.getInput(),
                                                            allocator.operandNumber(vmove.getResult()), vmove.getResult(), block);
                                        } else {
                                            LoadConstantOp load = (LoadConstantOp) move;
                                            Debug.log("eliminating constant load from %s to %d (%s) in block %s", load.getConstant(), allocator.operandNumber(load.getResult()), load.getResult(),
                                                            block);
                                        }
                                    }

                                    // null-instructions are deleted by assignRegNum
                                    instructions.set(j, null);
                                }

                            } else {
                                lastOpId = opId;
                                /*
                                 * Insert move from register to stack just after the beginning of
                                 * the interval.
                                 */
                                // assert interval == TraceInterval.EndMarker ||
                                // interval.spillDefinitionPos() >= opId : "invalid order";
                                assert interval == TraceInterval.EndMarker || (interval.isSplitParent() && SpillState.IN_MEMORY.contains(interval.spillState())) : "invalid interval";

                                while (interval != TraceInterval.EndMarker && interval.spillDefinitionPos() == opId) {
                                    Debug.log("handle %s", interval);
                                    if (!interval.canMaterialize() && interval.spillState() != SpillState.StartInMemory) {

                                        AllocatableValue fromLocation = interval.getSplitChildAtOpId(opId, OperandMode.DEF).location();
                                        AllocatableValue toLocation = allocator.canonicalSpillOpr(interval);
                                        if (!fromLocation.equals(toLocation)) {

                                            if (!insertionBuffer.initialized()) {
                                                /*
                                                 * prepare insertion buffer (appended when all
                                                 * instructions in the block are processed)
                                                 */
                                                insertionBuffer.init(instructions);
                                            }

                                            assert isRegister(fromLocation) : "from operand must be a register but is: " + fromLocation + " toLocation=" + toLocation + " spillState=" +
                                                            interval.spillState();
                                            assert isStackSlotValue(toLocation) : "to operand must be a stack slot";

                                            LIRInstruction move = allocator.getSpillMoveFactory().createMove(toLocation, fromLocation);
                                            insertionBuffer.append(j + 1, move);

                                            if (Debug.isLogEnabled()) {
                                                Debug.log("inserting move after definition of interval %d to stack slot %s at opId %d", interval.operandNumber, interval.spillSlot(), opId);
                                            }
                                        }
                                    }
                                    interval = interval.next;
                                }
                            }
                        }
                    }   // end of instruction iteration

                    if (insertionBuffer.initialized()) {
                        insertionBuffer.finish();
                    }
                }
            }   // end of block iteration

            assert interval == TraceInterval.EndMarker : "missed an interval";
        }
    }

    /**
     * @param allocator
     * @param block The block {@code move} is located in.
     * @param move Spill move.
     * @param lastOpId The id of last "normal" instruction before the spill move. (Spill moves have
     *            no valid opId but -1.)
     */
    private static boolean canEliminateSpillMove(TraceLinearScan allocator, AbstractBlockBase<?> block, MoveOp move, int lastOpId) {
        assert ((LIRInstruction) move).id() == -1 : "Not a spill move: " + move;
        assert isVariable(move.getResult()) : "LinearScan inserts only moves to variables: " + move;
        assert lastOpId >= 0 : "Invalid lastOpId: " + lastOpId;

        TraceInterval curInterval = allocator.intervalFor(move.getResult());

        if (!isRegister(curInterval.location()) && curInterval.inMemoryAt(lastOpId) && !isPhiResolutionMove(allocator, move)) {
            /* Phi resolution moves cannot be removed because they define the value. */
            // TODO (je) check if the comment is still valid!
            assert isStackSlotValue(curInterval.location()) : "Not a stack slot: " + curInterval.location();
            return true;
        }
        return false;
    }

    /**
     * Checks if a (spill or split) move is a Phi resolution move.
     *
     * A spill or split move connects a split parent or a split child with another split child.
     * Therefore the destination of the move is always a split child. Phi resolution moves look like
     * spill moves (i.e. {@link LIRInstruction#id() id} is {@code 0}, but they define a new
     * variable. As a result the destination interval is a split parent.
     */
    private static boolean isPhiResolutionMove(TraceLinearScan allocator, MoveOp move) {
        assert ((LIRInstruction) move).id() == -1 : "Not a spill move: " + move;
        TraceInterval curInterval = allocator.intervalFor(move.getResult());
        return curInterval.isSplitParent();
    }

    private static void checkIntervals(TraceInterval interval) {
        TraceInterval prev = null;
        TraceInterval temp = interval;
        while (temp != TraceInterval.EndMarker) {
            assert temp.spillDefinitionPos() >= 0 : "invalid spill definition pos";
            if (prev != null) {
                // assert temp.from() >= prev.from() : "intervals not sorted";
                assert temp.spillDefinitionPos() >= prev.spillDefinitionPos() : "when intervals are sorted by from :  then they must also be sorted by spillDefinitionPos";
            }

            assert temp.spillSlot() != null || temp.canMaterialize() : "interval has no spill slot assigned";
            assert temp.spillDefinitionPos() >= temp.from() : "invalid order";
            // assert temp.spillDefinitionPos() <= temp.from() + 2 :
            // "only intervals defined once at their start-pos can be optimized";

            if (Debug.isLogEnabled()) {
                Debug.log("interval %d (from %d to %d) must be stored at %d", temp.operandNumber, temp.from(), temp.to(), temp.spillDefinitionPos());
            }

            prev = temp;
            temp = temp.next;
        }
    }

}
