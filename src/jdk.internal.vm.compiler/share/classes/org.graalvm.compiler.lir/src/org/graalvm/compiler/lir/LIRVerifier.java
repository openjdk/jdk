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

import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.ssa.SSAUtil;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

public final class LIRVerifier {

    private final LIR lir;
    private final FrameMap frameMap;

    private final boolean beforeRegisterAllocation;

    private final BitSet[] blockLiveOut;
    private final Object[] variableDefinitions;

    private BitSet liveOutFor(AbstractBlockBase<?> block) {
        return blockLiveOut[block.getId()];
    }

    private void setLiveOutFor(AbstractBlockBase<?> block, BitSet liveOut) {
        blockLiveOut[block.getId()] = liveOut;
    }

    private int maxRegisterNum() {
        return frameMap.getTarget().arch.getRegisters().size();
    }

    private boolean isAllocatableRegister(Value value) {
        return isRegister(value) && frameMap.getRegisterConfig().getAttributesMap()[asRegister(value).number].isAllocatable();
    }

    public static boolean verify(final LIRInstruction op) {

        op.visitEachInput(LIRVerifier::allowed);
        op.visitEachAlive(LIRVerifier::allowed);
        op.visitEachState(LIRVerifier::allowed);
        op.visitEachTemp(LIRVerifier::allowed);
        op.visitEachOutput(LIRVerifier::allowed);

        op.verify();
        return true;
    }

    public static boolean verify(boolean beforeRegisterAllocation, LIR lir, FrameMap frameMap) {
        LIRVerifier verifier = new LIRVerifier(beforeRegisterAllocation, lir, frameMap);
        verifier.verify();
        return true;
    }

    private LIRVerifier(boolean beforeRegisterAllocation, LIR lir, FrameMap frameMap) {
        this.beforeRegisterAllocation = beforeRegisterAllocation;
        this.lir = lir;
        this.frameMap = frameMap;
        this.blockLiveOut = new BitSet[lir.linearScanOrder().length];
        this.variableDefinitions = new Object[lir.numVariables()];
    }

    private BitSet curVariablesLive;
    private Value[] curRegistersLive;

    private AbstractBlockBase<?> curBlock;
    private Object curInstruction;
    private BitSet curRegistersDefined;

    private void verify() {
        ValueConsumer useConsumer = new ValueConsumer() {

            @Override
            public void visitValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                use(value, mode, flags);
            }
        };
        ValueConsumer defConsumer = new ValueConsumer() {

            @Override
            public void visitValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                def(value, mode, flags);
            }
        };

        int maxRegisterNum = maxRegisterNum();
        curRegistersDefined = new BitSet();
        for (AbstractBlockBase<?> block : lir.linearScanOrder()) {
            curBlock = block;
            curVariablesLive = new BitSet();
            curRegistersLive = new Value[maxRegisterNum];

            if (block.getDominator() != null) {
                curVariablesLive.or(liveOutFor(block.getDominator()));
            }

            assert lir.getLIRforBlock(block).get(0) instanceof StandardOp.LabelOp : "block must start with label";

            if (block.getSuccessorCount() > 0) {
                LIRInstruction last = lir.getLIRforBlock(block).get(lir.getLIRforBlock(block).size() - 1);
                assert last instanceof StandardOp.JumpOp : "block with successor must end with unconditional jump";
            }
            if (block.getPredecessorCount() > 1) {
                SSAUtil.verifyPhi(lir, block);
            }

            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                curInstruction = op;

                op.visitEachInput(useConsumer);
                if (op.destroysCallerSavedRegisters()) {
                    for (Register register : frameMap.getRegisterConfig().getCallerSaveRegisters()) {
                        curRegistersLive[register.number] = null;
                    }
                }
                curRegistersDefined.clear();
                op.visitEachAlive(useConsumer);
                op.visitEachState(useConsumer);
                op.visitEachTemp(defConsumer);
                op.visitEachOutput(defConsumer);

                curInstruction = null;
            }

            setLiveOutFor(block, curVariablesLive);
        }
    }

    private void use(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        allowed(curInstruction, value, mode, flags);

        if (isVariable(value)) {
            assert beforeRegisterAllocation;

            int variableIdx = asVariable(value).index;
            if (!curVariablesLive.get(variableIdx)) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live variables: %s", curVariablesLive);
                if (variableDefinitions[variableIdx] != null) {
                    TTY.println("definition of %s: %s", value, variableDefinitions[variableIdx]);
                }
                TTY.println("ERROR: Use of variable %s that is not defined in dominator", value);
                throw GraalError.shouldNotReachHere();
            }

        } else if (isAllocatableRegister(value)) {
            int regNum = asRegister(value).number;
            if (mode == OperandMode.ALIVE) {
                curRegistersDefined.set(regNum);
            }

            if (beforeRegisterAllocation && !curRegistersLive[regNum].equals(value)) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live registers: %s", Arrays.toString(curRegistersLive));
                TTY.println("ERROR: Use of fixed register %s that is not defined in this block", value);
                throw GraalError.shouldNotReachHere();
            }
        }
    }

    private void def(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        allowed(curInstruction, value, mode, flags);

        if (isVariable(value)) {
            assert beforeRegisterAllocation;

            int variableIdx = asVariable(value).index;
            if (variableDefinitions[variableIdx] != null) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("live variables: %s", curVariablesLive);
                TTY.println("definition of %s: %s", value, variableDefinitions[variableIdx]);
                TTY.println("ERROR: Variable %s defined multiple times", value);
                throw GraalError.shouldNotReachHere();
            }
            assert curInstruction != null;
            variableDefinitions[variableIdx] = curInstruction;
            assert !curVariablesLive.get(variableIdx);
            if (mode == OperandMode.DEF) {
                curVariablesLive.set(variableIdx);
            }

        } else if (isAllocatableRegister(value)) {
            int regNum = asRegister(value).number;
            if (curRegistersDefined.get(regNum)) {
                TTY.println("block %s  instruction %s", curBlock, curInstruction);
                TTY.println("ERROR: Same register defined twice in the same instruction: %s", value);
                throw GraalError.shouldNotReachHere();
            }
            curRegistersDefined.set(regNum);

            if (beforeRegisterAllocation) {
                if (mode == OperandMode.DEF) {
                    curRegistersLive[regNum] = value;
                } else {
                    curRegistersLive[regNum] = null;
                }
            }
        }
    }

    // @formatter:off
    private static void allowed(Object op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        if ((isVariable(value) && flags.contains(OperandFlag.REG)) ||
            (isRegister(value) && flags.contains(OperandFlag.REG)) ||
            (isStackSlotValue(value) && flags.contains(OperandFlag.STACK)) ||
            (isJavaConstant(value) && flags.contains(OperandFlag.CONST) && mode != OperandMode.DEF) ||
            (isIllegal(value) && flags.contains(OperandFlag.ILLEGAL))) {
            return;
        }
        throw new GraalError("Invalid LIR%n  Instruction: %s%n  Mode: %s%n  Flags: %s%n  Unexpected value: %s %s",
                        op, mode, flags, value.getClass().getSimpleName(), value);
    }
    // @formatter:on
}
