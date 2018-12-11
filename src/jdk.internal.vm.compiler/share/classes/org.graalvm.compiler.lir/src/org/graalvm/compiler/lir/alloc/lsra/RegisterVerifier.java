/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;
import java.util.EnumSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 */
final class RegisterVerifier {

    LinearScan allocator;
    ArrayList<AbstractBlockBase<?>> workList; // all blocks that must be processed
    BlockMap<Interval[]> savedStates; // saved information of previous check

    // simplified access to methods of LinearScan
    Interval intervalAt(Value operand) {
        return allocator.intervalFor(operand);
    }

    // currently, only registers are processed
    int stateSize() {
        return allocator.maxRegisterNumber() + 1;
    }

    // accessors
    Interval[] stateForBlock(AbstractBlockBase<?> block) {
        return savedStates.get(block);
    }

    void setStateForBlock(AbstractBlockBase<?> block, Interval[] savedState) {
        savedStates.put(block, savedState);
    }

    void addToWorkList(AbstractBlockBase<?> block) {
        if (!workList.contains(block)) {
            workList.add(block);
        }
    }

    RegisterVerifier(LinearScan allocator) {
        this.allocator = allocator;
        workList = new ArrayList<>(16);
        this.savedStates = new BlockMap<>(allocator.getLIR().getControlFlowGraph());

    }

    @SuppressWarnings("try")
    void verify(AbstractBlockBase<?> start) {
        DebugContext debug = allocator.getDebug();
        try (DebugContext.Scope s = debug.scope("RegisterVerifier")) {
            // setup input registers (method arguments) for first block
            Interval[] inputState = new Interval[stateSize()];
            setStateForBlock(start, inputState);
            addToWorkList(start);

            // main loop for verification
            do {
                AbstractBlockBase<?> block = workList.get(0);
                workList.remove(0);

                processBlock(block);
            } while (!workList.isEmpty());
        }
    }

    @SuppressWarnings("try")
    private void processBlock(AbstractBlockBase<?> block) {
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("processBlock B%d", block.getId())) {
            // must copy state because it is modified
            Interval[] inputState = copy(stateForBlock(block));

            try (Indent indent2 = debug.logAndIndent("Input-State of intervals:")) {
                printState(inputState);
            }

            // process all operations of the block
            processOperations(block, inputState);

            try (Indent indent2 = debug.logAndIndent("Output-State of intervals:")) {
                printState(inputState);
            }

            // iterate all successors
            for (AbstractBlockBase<?> succ : block.getSuccessors()) {
                processSuccessor(succ, inputState);
            }
        }
    }

    protected void printState(Interval[] inputState) {
        DebugContext debug = allocator.getDebug();
        for (int i = 0; i < stateSize(); i++) {
            Register reg = allocator.getRegisters().get(i);
            assert reg.number == i;
            if (inputState[i] != null) {
                debug.log(" %6s %4d  --  %s", reg, inputState[i].operandNumber, inputState[i]);
            } else {
                debug.log(" %6s   __", reg);
            }
        }
    }

    private void processSuccessor(AbstractBlockBase<?> block, Interval[] inputState) {
        DebugContext debug = allocator.getDebug();
        Interval[] savedState = stateForBlock(block);

        if (savedState != null) {
            // this block was already processed before.
            // check if new inputState is consistent with savedState

            boolean savedStateCorrect = true;
            for (int i = 0; i < stateSize(); i++) {
                if (inputState[i] != savedState[i]) {
                    // current inputState and previous savedState assume a different
                    // interval in this register . assume that this register is invalid
                    if (savedState[i] != null) {
                        // invalidate old calculation only if it assumed that
                        // register was valid. when the register was already invalid,
                        // then the old calculation was correct.
                        savedStateCorrect = false;
                        savedState[i] = null;

                        debug.log("processSuccessor B%d: invalidating slot %d", block.getId(), i);
                    }
                }
            }

            if (savedStateCorrect) {
                // already processed block with correct inputState
                debug.log("processSuccessor B%d: previous visit already correct", block.getId());
            } else {
                // must re-visit this block
                debug.log("processSuccessor B%d: must re-visit because input state changed", block.getId());
                addToWorkList(block);
            }

        } else {
            // block was not processed before, so set initial inputState
            debug.log("processSuccessor B%d: initial visit", block.getId());

            setStateForBlock(block, copy(inputState));
            addToWorkList(block);
        }
    }

    static Interval[] copy(Interval[] inputState) {
        return inputState.clone();
    }

    static void statePut(DebugContext debug, Interval[] inputState, Value location, Interval interval) {
        if (location != null && isRegister(location)) {
            Register reg = asRegister(location);
            int regNum = reg.number;
            if (interval != null) {
                debug.log("%s = %s", reg, interval.operand);
            } else if (inputState[regNum] != null) {
                debug.log("%s = null", reg);
            }

            inputState[regNum] = interval;
        }
    }

    static boolean checkState(AbstractBlockBase<?> block, LIRInstruction op, Interval[] inputState, Value operand, Value reg, Interval interval) {
        if (reg != null && isRegister(reg)) {
            if (inputState[asRegister(reg).number] != interval) {
                throw new GraalError(
                                "Error in register allocation: operation (%s) in block %s expected register %s (operand %s) to contain the value of interval %s but data-flow says it contains interval %s",
                                op, block, reg, operand, interval, inputState[asRegister(reg).number]);
            }
        }
        return true;
    }

    void processOperations(AbstractBlockBase<?> block, final Interval[] inputState) {
        ArrayList<LIRInstruction> ops = allocator.getLIR().getLIRforBlock(block);
        DebugContext debug = allocator.getDebug();
        InstructionValueConsumer useConsumer = new InstructionValueConsumer() {

            @Override
            public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                // we skip spill moves inserted by the spill position optimization
                if (LinearScan.isVariableOrRegister(operand) && allocator.isProcessed(operand) && op.id() != LinearScan.DOMINATOR_SPILL_MOVE_ID) {
                    Interval interval = intervalAt(operand);
                    if (op.id() != -1) {
                        interval = interval.getSplitChildAtOpId(op.id(), mode, allocator);
                    }

                    assert checkState(block, op, inputState, interval.operand, interval.location(), interval.splitParent());
                }
            }
        };

        InstructionValueConsumer defConsumer = (op, operand, mode, flags) -> {
            if (LinearScan.isVariableOrRegister(operand) && allocator.isProcessed(operand)) {
                Interval interval = intervalAt(operand);
                if (op.id() != -1) {
                    interval = interval.getSplitChildAtOpId(op.id(), mode, allocator);
                }

                statePut(debug, inputState, interval.location(), interval.splitParent());
            }
        };

        // visit all instructions of the block
        for (int i = 0; i < ops.size(); i++) {
            final LIRInstruction op = ops.get(i);

            if (debug.isLogEnabled()) {
                debug.log("%s", op.toStringWithIdPrefix());
            }

            // check if input operands are correct
            op.visitEachInput(useConsumer);
            // invalidate all caller save registers at calls
            if (op.destroysCallerSavedRegisters()) {
                for (Register r : allocator.getRegisterAllocationConfig().getRegisterConfig().getCallerSaveRegisters()) {
                    statePut(debug, inputState, r.asValue(), null);
                }
            }
            op.visitEachAlive(useConsumer);
            // set temp operands (some operations use temp operands also as output operands, so
            // can't set them null)
            op.visitEachTemp(defConsumer);
            // set output operands
            op.visitEachOutput(defConsumer);
        }
    }
}
