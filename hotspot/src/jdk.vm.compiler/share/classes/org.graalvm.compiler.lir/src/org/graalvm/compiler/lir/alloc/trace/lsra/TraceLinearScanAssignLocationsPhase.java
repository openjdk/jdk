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

import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;
import static org.graalvm.compiler.lir.alloc.trace.TraceRegisterAllocationPhase.Options.TraceRAshareSpillInformation;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.StandardOp.MoveOp;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.alloc.trace.ShadowedRegisterValue;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanPhase.TraceLinearScan;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;

import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Specialization of {@link org.graalvm.compiler.lir.alloc.lsra.LinearScanAssignLocationsPhase} that
 * inserts {@link ShadowedRegisterValue}s to describe {@link RegisterValue}s that are also available
 * on the {@link StackSlot stack}.
 */
final class TraceLinearScanAssignLocationsPhase extends TraceLinearScanAllocationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, TraceLinearScanAllocationContext context) {
        TraceLinearScan allocator = context.allocator;
        MoveFactory spillMoveFactory = context.spillMoveFactory;
        new Assigner(allocator, spillMoveFactory).assignLocations();
    }

    private static final class Assigner {
        private final TraceLinearScan allocator;
        private final MoveFactory spillMoveFactory;

        private Assigner(TraceLinearScan allocator, MoveFactory spillMoveFactory) {
            this.allocator = allocator;
            this.spillMoveFactory = spillMoveFactory;
        }

        /**
         * Assigns the allocated location for an LIR instruction operand back into the instruction.
         *
         * @param op current {@link LIRInstruction}
         * @param operand an LIR instruction operand
         * @param mode the usage mode for {@code operand} by the instruction
         * @return the location assigned for the operand
         */
        private Value colorLirOperand(LIRInstruction op, Variable operand, OperandMode mode) {
            int opId = op.id();
            TraceInterval interval = allocator.intervalFor(operand);
            assert interval != null : "interval must exist";

            if (opId != -1) {
                /*
                 * Operands are not changed when an interval is split during allocation, so search
                 * the right interval here.
                 */
                interval = allocator.splitChildAtOpId(interval, opId, mode);
            }

            if (isIllegal(interval.location()) && interval.canMaterialize()) {
                if (op instanceof LabelOp) {
                    /*
                     * Spilled materialized value in a LabelOp (i.e. incoming): no need for move
                     * resolution so we can ignore it.
                     */
                    return Value.ILLEGAL;
                }
                assert mode != OperandMode.DEF;
                return new ConstantValue(interval.kind(), interval.getMaterializedValue());
            }
            return interval.location();
        }

        /**
         * @param op
         * @param operand
         * @param valueMode
         * @param flags
         * @see InstructionValueProcedure#doValue(LIRInstruction, Value, OperandMode, EnumSet)
         */
        private Value debugInfoProcedure(LIRInstruction op, Value operand, OperandMode valueMode, EnumSet<OperandFlag> flags) {
            if (isVirtualStackSlot(operand)) {
                return operand;
            }
            int tempOpId = op.id();
            OperandMode mode = OperandMode.USE;
            AbstractBlockBase<?> block = allocator.blockForId(tempOpId);
            if (block.getSuccessorCount() == 1 && tempOpId == allocator.getLastLirInstructionId(block)) {
                /*
                 * Generating debug information for the last instruction of a block. If this
                 * instruction is a branch, spill moves are inserted before this branch and so the
                 * wrong operand would be returned (spill moves at block boundaries are not
                 * considered in the live ranges of intervals).
                 *
                 * Solution: use the first opId of the branch target block instead.
                 */
                final LIRInstruction instr = allocator.getLIR().getLIRforBlock(block).get(allocator.getLIR().getLIRforBlock(block).size() - 1);
                if (instr instanceof StandardOp.JumpOp) {
                    throw GraalError.unimplemented("DebugInfo on jumps are not supported!");
                }
            }

            /*
             * Get current location of operand. The operand must be live because debug information
             * is considered when building the intervals if the interval is not live,
             * colorLirOperand will cause an assert on failure.
             */
            Value result = colorLirOperand(op, (Variable) operand, mode);
            assert !allocator.hasCall(tempOpId) || isStackSlotValue(result) || isConstantValue(result) || !allocator.isCallerSave(result) : "cannot have caller-save register operands at calls";
            return result;
        }

        private void computeDebugInfo(final LIRInstruction op, LIRFrameState info) {
            info.forEachState(op, this::debugInfoProcedure);
        }

        private void assignLocations(List<LIRInstruction> instructions) {
            int numInst = instructions.size();
            boolean hasDead = false;

            for (int j = 0; j < numInst; j++) {
                final LIRInstruction op = instructions.get(j);
                if (op == null) {
                    /*
                     * this can happen when spill-moves are removed in eliminateSpillMoves
                     */
                    hasDead = true;
                } else if (assignLocations(op, instructions, j)) {
                    hasDead = true;
                }
            }

            if (hasDead) {
                // Remove null values from the list.
                instructions.removeAll(Collections.singleton(null));
            }
        }

        private final InstructionValueProcedure assignProc = new InstructionValueProcedure() {
            @Override
            public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVariable(value)) {
                    return colorLirOperand(instruction, (Variable) value, mode);
                }
                return value;
            }
        };

        /**
         * Assigns the operand of an {@link LIRInstruction}.
         *
         * @param op The {@link LIRInstruction} that should be colored.
         * @param j The index of {@code op} in the {@code instructions} list.
         * @param instructions The instructions of the current block.
         * @return {@code true} if the instruction was deleted.
         */
        private boolean assignLocations(LIRInstruction op, List<LIRInstruction> instructions, int j) {
            assert op != null && instructions.get(j) == op;
            if (TraceRAshareSpillInformation.getValue()) {
                if (op instanceof BlockEndOp) {
                    ((BlockEndOp) op).forEachOutgoingValue(colorOutgoingIncomingValues);
                } else if (op instanceof LabelOp) {
                    ((LabelOp) op).forEachIncomingValue(colorOutgoingIncomingValues);
                }
            }

            // remove useless moves
            if (op instanceof MoveOp) {
                AllocatableValue result = ((MoveOp) op).getResult();
                if (isVariable(result) && allocator.isMaterialized(result, op.id(), OperandMode.DEF)) {
                    /*
                     * This happens if a materializable interval is originally not spilled but then
                     * kicked out in LinearScanWalker.splitForSpilling(). When kicking out such an
                     * interval this move operation was already generated.
                     */
                    instructions.set(j, null);
                    return true;
                }
            }

            op.forEachInput(assignProc);
            op.forEachAlive(assignProc);
            op.forEachTemp(assignProc);
            op.forEachOutput(assignProc);

            // compute reference map and debug information
            op.forEachState((inst, state) -> computeDebugInfo(inst, state));

            // remove useless moves
            if (op instanceof ValueMoveOp) {
                ValueMoveOp move = (ValueMoveOp) op;
                if (move.getInput().equals(move.getResult())) {
                    instructions.set(j, null);
                    return true;
                }
                if (isStackSlotValue(move.getInput()) && isStackSlotValue(move.getResult())) {
                    // rewrite stack to stack moves
                    instructions.set(j, spillMoveFactory.createStackMove(move.getResult(), move.getInput()));
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("try")
        private void assignLocations() {
            try (Indent indent = Debug.logAndIndent("assign locations")) {
                for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                    try (Indent indent2 = Debug.logAndIndent("assign locations in block B%d", block.getId())) {
                        assignLocations(allocator.getLIR().getLIRforBlock(block));
                    }
                }
            }
        }

        private final InstructionValueProcedure colorOutgoingIncomingValues = new InstructionValueProcedure() {

            @Override
            public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVariable(value)) {
                    TraceInterval interval = allocator.intervalFor(value);
                    assert interval != null : "interval must exist";
                    interval = allocator.splitChildAtOpId(interval, instruction.id(), mode);

                    if (interval.inMemoryAt(instruction.id()) && isRegister(interval.location())) {
                        return new ShadowedRegisterValue((RegisterValue) interval.location(), interval.spillSlot());
                    }
                }
                return value;
            }
        };
    }
}
