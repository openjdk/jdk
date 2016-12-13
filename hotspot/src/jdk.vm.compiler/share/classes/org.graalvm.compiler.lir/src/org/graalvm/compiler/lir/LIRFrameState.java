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

import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;
import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isConstantJavaValue;
import static jdk.vm.ci.code.ValueUtil.isIllegalJavaValue;
import static jdk.vm.ci.code.ValueUtil.isVirtualObject;

import java.util.Arrays;
import java.util.EnumSet;

import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.util.IndexedValueMap;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.StackLockValue;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.Value;

/**
 * This class represents garbage collection and deoptimization information attached to a LIR
 * instruction.
 */
public class LIRFrameState {

    public final BytecodeFrame topFrame;
    private final VirtualObject[] virtualObjects;
    public final LabelRef exceptionEdge;
    protected DebugInfo debugInfo;

    private IndexedValueMap liveBasePointers;

    public LIRFrameState(BytecodeFrame topFrame, VirtualObject[] virtualObjects, LabelRef exceptionEdge) {
        this.topFrame = topFrame;
        this.virtualObjects = virtualObjects;
        this.exceptionEdge = exceptionEdge;
    }

    public boolean hasDebugInfo() {
        return debugInfo != null;
    }

    public DebugInfo debugInfo() {
        assert debugInfo != null : "debug info not allocated yet";
        return debugInfo;
    }

    /**
     * Iterates the frame state and calls the {@link InstructionValueProcedure} for every variable.
     *
     * @param proc The procedure called for variables.
     */
    public void forEachState(LIRInstruction inst, InstructionValueProcedure proc) {
        for (BytecodeFrame cur = topFrame; cur != null; cur = cur.caller()) {
            processValues(inst, cur.values, proc);
        }
        if (virtualObjects != null) {
            for (VirtualObject obj : virtualObjects) {
                processValues(inst, obj.getValues(), proc);
            }
        }
        if (liveBasePointers != null) {
            liveBasePointers.forEach(inst, OperandMode.ALIVE, STATE_FLAGS, proc);
        }
    }

    /**
     * Iterates the frame state and calls the {@link InstructionValueConsumer} for every variable.
     *
     * @param proc The procedure called for variables.
     */
    public void visitEachState(LIRInstruction inst, InstructionValueConsumer proc) {
        for (BytecodeFrame cur = topFrame; cur != null; cur = cur.caller()) {
            visitValues(inst, cur.values, proc);
        }
        if (virtualObjects != null) {
            for (VirtualObject obj : virtualObjects) {
                visitValues(inst, obj.getValues(), proc);
            }
        }
        if (liveBasePointers != null) {
            liveBasePointers.visitEach(inst, OperandMode.ALIVE, STATE_FLAGS, proc);
        }
    }

    /**
     * We filter out constant and illegal values ourself before calling the procedure, so
     * {@link OperandFlag#CONST} and {@link OperandFlag#ILLEGAL} need not be set.
     */
    protected static final EnumSet<OperandFlag> STATE_FLAGS = EnumSet.of(OperandFlag.REG, OperandFlag.STACK);

    protected void processValues(LIRInstruction inst, JavaValue[] values, InstructionValueProcedure proc) {
        for (int i = 0; i < values.length; i++) {
            JavaValue value = values[i];
            if (isIllegalJavaValue(value)) {
                continue;
            }
            if (value instanceof AllocatableValue) {
                AllocatableValue allocatable = (AllocatableValue) value;
                Value result = proc.doValue(inst, allocatable, OperandMode.ALIVE, STATE_FLAGS);
                if (!allocatable.identityEquals(result)) {
                    values[i] = (JavaValue) result;
                }
            } else if (value instanceof StackLockValue) {
                StackLockValue monitor = (StackLockValue) value;
                JavaValue owner = monitor.getOwner();
                if (owner instanceof AllocatableValue) {
                    monitor.setOwner((JavaValue) proc.doValue(inst, (AllocatableValue) owner, OperandMode.ALIVE, STATE_FLAGS));
                }
                Value slot = monitor.getSlot();
                if (isVirtualStackSlot(slot)) {
                    monitor.setSlot(asAllocatableValue(proc.doValue(inst, slot, OperandMode.ALIVE, STATE_FLAGS)));
                }
            } else {
                assert unprocessed(value);
            }
        }
    }

    protected void visitValues(LIRInstruction inst, JavaValue[] values, InstructionValueConsumer proc) {
        for (int i = 0; i < values.length; i++) {
            JavaValue value = values[i];
            if (isIllegalJavaValue(value)) {
                continue;
            } else if (value instanceof AllocatableValue) {
                proc.visitValue(inst, (AllocatableValue) value, OperandMode.ALIVE, STATE_FLAGS);
            } else if (value instanceof StackLockValue) {
                StackLockValue monitor = (StackLockValue) value;
                JavaValue owner = monitor.getOwner();
                if (owner instanceof AllocatableValue) {
                    proc.visitValue(inst, (AllocatableValue) owner, OperandMode.ALIVE, STATE_FLAGS);
                }
                Value slot = monitor.getSlot();
                if (isVirtualStackSlot(slot)) {
                    proc.visitValue(inst, slot, OperandMode.ALIVE, STATE_FLAGS);
                }
            } else {
                assert unprocessed(value);
            }
        }
    }

    private boolean unprocessed(JavaValue value) {
        if (isIllegalJavaValue(value)) {
            // Ignore dead local variables.
            return true;
        } else if (isConstantJavaValue(value)) {
            // Ignore constants, the register allocator does not need to see them.
            return true;
        } else if (isVirtualObject(value)) {
            assert Arrays.asList(virtualObjects).contains(value);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Called by the register allocator to initialize the frame state.
     *
     * @param frameMap The frame map.
     * @param canHaveRegisters True if there can be any register map entries.
     */
    public void initDebugInfo(FrameMap frameMap, boolean canHaveRegisters) {
        debugInfo = new DebugInfo(topFrame, virtualObjects);
    }

    public IndexedValueMap getLiveBasePointers() {
        return liveBasePointers;
    }

    public void setLiveBasePointers(IndexedValueMap liveBasePointers) {
        this.liveBasePointers = liveBasePointers;
    }

    @Override
    public String toString() {
        return debugInfo != null ? debugInfo.toString() : topFrame != null ? topFrame.toString() : "<empty>";
    }
}
