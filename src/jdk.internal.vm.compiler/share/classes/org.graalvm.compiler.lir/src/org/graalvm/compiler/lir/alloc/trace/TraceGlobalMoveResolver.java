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


package org.graalvm.compiler.lir.alloc.trace;

import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.asVirtualStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;
import static org.graalvm.compiler.lir.alloc.trace.TraceUtil.asShadowedRegisterValue;
import static org.graalvm.compiler.lir.alloc.trace.TraceUtil.isShadowedRegisterValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.framemap.FrameMapBuilderTool;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 */
public final class TraceGlobalMoveResolver extends TraceGlobalMoveResolutionPhase.MoveResolver {

    private static final CounterKey cycleBreakingSlotsAllocated = DebugContext.counter("TraceRA[cycleBreakingSlotsAllocated(global)]");
    private static final CounterKey cycleBreakingSlotsReused = DebugContext.counter("TraceRA[cycleBreakingSlotsReused(global)]");

    private int insertIdx;
    private LIRInsertionBuffer insertionBuffer; // buffer where moves are inserted

    private final ArrayList<Value> mappingFrom;
    private final ArrayList<Value> mappingFromStack;
    private final ArrayList<AllocatableValue> mappingTo;
    private final int[] registerBlocked;
    private static final int STACK_SLOT_IN_CALLER_FRAME_IDX = -1;
    private int[] stackBlocked;
    private final int firstVirtualStackIndex;
    private final MoveFactory spillMoveFactory;
    private final FrameMapBuilder frameMapBuilder;
    private final OptionValues options;
    private final RegisterAllocationConfig registerAllocationConfig;
    private final LIRGenerationResult res;
    private final DebugContext debug;

    private void setValueBlocked(Value location, int direction) {
        assert direction == 1 || direction == -1 : "out of bounds";
        if (isStackSlotValue(location)) {
            int stackIdx = getStackArrayIndex(location);
            if (stackIdx == STACK_SLOT_IN_CALLER_FRAME_IDX) {
                // incoming stack arguments can be ignored
                return;
            }
            if (stackIdx >= stackBlocked.length) {
                stackBlocked = Arrays.copyOf(stackBlocked, stackIdx + 1);
            }
            stackBlocked[stackIdx] += direction;
        } else {
            assert direction == 1 || direction == -1 : "out of bounds";
            if (isRegister(location)) {
                registerBlocked[asRegister(location).number] += direction;
            } else {
                throw GraalError.shouldNotReachHere("unhandled value " + location);
            }
        }
    }

    private int valueBlocked(Value location) {
        if (isStackSlotValue(location)) {
            int stackIdx = getStackArrayIndex(location);
            if (stackIdx == STACK_SLOT_IN_CALLER_FRAME_IDX) {
                // incoming stack arguments are always blocked (aka they can not be written)
                return 1;
            }
            if (stackIdx >= stackBlocked.length) {
                return 0;
            }
            return stackBlocked[stackIdx];
        }
        if (isRegister(location)) {
            return registerBlocked[asRegister(location).number];
        }
        throw GraalError.shouldNotReachHere("unhandled value " + location);
    }

    private static boolean areMultipleReadsAllowed() {
        return true;
    }

    private boolean hasMappings() {
        return mappingFrom.size() > 0;
    }

    private MoveFactory getSpillMoveFactory() {
        return spillMoveFactory;
    }

    private RegisterArray getRegisters() {
        return frameMapBuilder.getRegisterConfig().getAllocatableRegisters();
    }

    public TraceGlobalMoveResolver(LIRGenerationResult res, MoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig, Architecture arch) {

        this.mappingFrom = new ArrayList<>(8);
        this.mappingFromStack = new ArrayList<>(8);
        this.mappingTo = new ArrayList<>(8);
        this.insertIdx = -1;
        this.insertionBuffer = new LIRInsertionBuffer();

        this.frameMapBuilder = res.getFrameMapBuilder();
        this.spillMoveFactory = spillMoveFactory;
        this.registerBlocked = new int[arch.getRegisters().size()];
        this.registerAllocationConfig = registerAllocationConfig;

        FrameMapBuilderTool frameMapBuilderTool = (FrameMapBuilderTool) frameMapBuilder;
        this.stackBlocked = new int[frameMapBuilderTool.getNumberOfStackSlots()];

        FrameMap frameMap = frameMapBuilderTool.getFrameMap();
        this.firstVirtualStackIndex = !frameMap.frameNeedsAllocating() ? 0 : frameMap.currentFrameSize() + 1;
        this.res = res;
        LIR lir = res.getLIR();
        this.options = lir.getOptions();
        this.debug = lir.getDebug();
    }

    private boolean checkEmpty() {
        for (int i = 0; i < stackBlocked.length; i++) {
            assert stackBlocked[i] == 0 : "stack map must be empty before and after processing";
        }
        assert mappingFrom.size() == 0 && mappingTo.size() == 0 && mappingFromStack.size() == 0 : "list must be empty before and after processing";
        for (int i = 0; i < getRegisters().size(); i++) {
            assert registerBlocked[i] == 0 : "register map must be empty before and after processing";
        }
        return true;
    }

    private boolean verifyBeforeResolve() {
        assert mappingFrom.size() == mappingTo.size() && mappingFrom.size() == mappingFromStack.size() : "length must be equal";
        assert insertIdx != -1 : "insert position not set";

        int i;
        int j;
        if (!areMultipleReadsAllowed()) {
            for (i = 0; i < mappingFrom.size(); i++) {
                for (j = i + 1; j < mappingFrom.size(); j++) {
                    assert mappingFrom.get(i) == null || mappingFrom.get(i) != mappingFrom.get(j) : "cannot read from same interval twice";
                }
            }
        }

        for (i = 0; i < mappingTo.size(); i++) {
            for (j = i + 1; j < mappingTo.size(); j++) {
                assert mappingTo.get(i) != mappingTo.get(j) : "cannot write to same interval twice";
            }
        }

        for (i = 0; i < mappingTo.size(); i++) {
            Value to = mappingTo.get(i);
            assert !isStackSlotValue(to) || getStackArrayIndex(to) != STACK_SLOT_IN_CALLER_FRAME_IDX : "Cannot move to in argument: " + to;
        }

        HashSet<Value> usedRegs = new HashSet<>();
        if (!areMultipleReadsAllowed()) {
            for (i = 0; i < mappingFrom.size(); i++) {
                Value from = mappingFrom.get(i);
                if (from != null && !isIllegal(from)) {
                    boolean unique = usedRegs.add(from);
                    assert unique : "cannot read from same register twice";
                }
            }
        }

        usedRegs.clear();
        for (i = 0; i < mappingTo.size(); i++) {
            Value to = mappingTo.get(i);
            if (isIllegal(to)) {
                // After insertion the location may become illegal, so don't check it since multiple
                // intervals might be illegal.
                continue;
            }
            boolean unique = usedRegs.add(to);
            assert unique : "cannot write to same register twice";
        }

        return true;
    }

    // mark assignedReg and assignedRegHi of the interval as blocked
    private void block(Value location) {
        if (mightBeBlocked(location)) {
            assert areMultipleReadsAllowed() || valueBlocked(location) == 0 : "location already marked as used: " + location;
            setValueBlocked(location, 1);
            debug.log("block %s", location);
        }
    }

    // mark assignedReg and assignedRegHi of the interval as unblocked
    private void unblock(Value location) {
        if (mightBeBlocked(location)) {
            assert valueBlocked(location) > 0 : "location already marked as unused: " + location;
            setValueBlocked(location, -1);
            debug.log("unblock %s", location);
        }
    }

    /**
     * Checks if {@code to} is not blocked or is only blocked by {@code from}.
     */
    private boolean safeToProcessMove(Value fromLocation, Value toLocation) {
        if (mightBeBlocked(toLocation)) {
            if ((valueBlocked(toLocation) > 1 || (valueBlocked(toLocation) == 1 && !isMoveToSelf(fromLocation, toLocation)))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isMoveToSelf(Value from, Value to) {
        assert to != null;
        if (to.equals(from)) {
            return true;
        }
        if (from == null) {
            return false;
        }
        if (isShadowedRegisterValue(from)) {
            /* From is a shadowed register. */
            if (isShadowedRegisterValue(to)) {
                // both shadowed but not equal
                return false;
            }
            ShadowedRegisterValue shadowed = asShadowedRegisterValue(from);
            if (isRegisterToRegisterMoveToSelf(shadowed.getRegister(), to)) {
                return true;
            }
            if (isStackSlotValue(to)) {
                return to.equals(shadowed.getStackSlot());
            }
        } else {
            /*
             * A shadowed destination value is never a self move it both values are not equal. Fall
             * through.
             */
            // if (isShadowedRegisterValue(to)) return false;

            return isRegisterToRegisterMoveToSelf(from, to);
        }
        return false;
    }

    private static boolean isRegisterToRegisterMoveToSelf(Value from, Value to) {
        if (to.equals(from)) {
            return true;
        }
        if (isRegister(from) && isRegister(to) && asRegister(from).equals(asRegister(to))) {
            // Values differ but Registers are the same
            return true;
        }
        return false;
    }

    private static boolean mightBeBlocked(Value location) {
        return isRegister(location) || isStackSlotValue(location);
    }

    private void createInsertionBuffer(ArrayList<LIRInstruction> list) {
        assert !insertionBuffer.initialized() : "overwriting existing buffer";
        insertionBuffer.init(list);
    }

    private void appendInsertionBuffer() {
        if (insertionBuffer.initialized()) {
            insertionBuffer.finish();
        }
        assert !insertionBuffer.initialized() : "must be uninitialized now";

        insertIdx = -1;
    }

    private LIRInstruction insertMove(Value fromOperand, AllocatableValue toOperand) {
        assert !fromOperand.equals(toOperand) : "from and to are equal: " + fromOperand + " vs. " + toOperand;
        assert LIRKind.verifyMoveKinds(fromOperand.getValueKind(), fromOperand.getValueKind(), registerAllocationConfig) : "move between different types";
        assert insertIdx != -1 : "must setup insert position first";

        LIRInstruction move = createMove(fromOperand, toOperand);
        insertionBuffer.append(insertIdx, move);

        if (debug.isLogEnabled()) {
            debug.log("insert move from %s to %s at %d", fromOperand, toOperand, insertIdx);
        }
        return move;
    }

    /**
     * @param fromOpr Operand of the {@code from} interval
     * @param toOpr Operand of the {@code to} interval
     */
    private LIRInstruction createMove(Value fromOpr, AllocatableValue toOpr) {
        if (isStackSlotValue(toOpr) && isStackSlotValue(fromOpr)) {
            return getSpillMoveFactory().createStackMove(toOpr, asAllocatableValue(fromOpr));
        }
        return getSpillMoveFactory().createMove(toOpr, fromOpr);
    }

    @SuppressWarnings("try")
    private void resolveMappings() {
        try (Indent indent = debug.logAndIndent("resolveMapping")) {
            assert verifyBeforeResolve();
            if (debug.isLogEnabled()) {
                printMapping();
            }

            // Block all registers that are used as input operands of a move.
            // When a register is blocked, no move to this register is emitted.
            // This is necessary for detecting cycles in moves.
            for (int i = mappingFrom.size() - 1; i >= 0; i--) {
                Value from = mappingFrom.get(i);
                block(from);
            }

            ArrayList<AllocatableValue> busySpillSlots = null;
            while (mappingFrom.size() > 0) {
                boolean processedInterval = false;

                int spillCandidate = -1;
                for (int i = mappingFrom.size() - 1; i >= 0; i--) {
                    Value fromLocation = mappingFrom.get(i);
                    AllocatableValue toLocation = mappingTo.get(i);
                    if (safeToProcessMove(fromLocation, toLocation)) {
                        // this interval can be processed because target is free
                        LIRInstruction move = insertMove(fromLocation, toLocation);
                        move.setComment(res, "TraceGlobalMoveResolver: resolveMapping");
                        unblock(fromLocation);
                        if (isStackSlotValue(toLocation)) {
                            if (busySpillSlots == null) {
                                busySpillSlots = new ArrayList<>(2);
                            }
                            busySpillSlots.add(toLocation);
                        }
                        mappingFrom.remove(i);
                        mappingFromStack.remove(i);
                        mappingTo.remove(i);

                        processedInterval = true;
                    } else if (fromLocation != null) {
                        if (isRegister(fromLocation) && (busySpillSlots == null || !busySpillSlots.contains(mappingFromStack.get(i)))) {
                            // this interval cannot be processed now because target is not free
                            // it starts in a register, so it is a possible candidate for spilling
                            spillCandidate = i;
                        } else if (isStackSlotValue(fromLocation) && spillCandidate == -1) {
                            // fall back to spill a stack slot in case no other candidate is found
                            spillCandidate = i;
                        }
                    }
                }

                if (!processedInterval) {
                    breakCycle(spillCandidate);
                }
            }
        }

        // check that all intervals have been processed
        assert checkEmpty();
    }

    @SuppressWarnings("try")
    private void breakCycle(int spillCandidate) {
        // no move could be processed because there is a cycle in the move list
        // (e.g. r1 . r2, r2 . r1), so one interval must be spilled to memory
        assert spillCandidate != -1 : "no interval in register for spilling found";

        // create a new spill interval and assign a stack slot to it
        Value from = mappingFrom.get(spillCandidate);
        try (Indent indent = debug.logAndIndent("BreakCycle: %s", from)) {
            AllocatableValue spillSlot = null;
            if (TraceRegisterAllocationPhase.Options.TraceRAreuseStackSlotsForMoveResolutionCycleBreaking.getValue(options) && !isStackSlotValue(from)) {
                // don't use the stack slot if from is already the stack slot
                Value fromStack = mappingFromStack.get(spillCandidate);
                if (fromStack != null) {
                    spillSlot = (AllocatableValue) fromStack;
                    cycleBreakingSlotsReused.increment(debug);
                    debug.log("reuse slot for spilling: %s", spillSlot);
                }
            }
            if (spillSlot == null) {
                spillSlot = frameMapBuilder.allocateSpillSlot(from.getValueKind());
                cycleBreakingSlotsAllocated.increment(debug);
                debug.log("created new slot for spilling: %s", spillSlot);
                // insert a move from register to stack and update the mapping
                LIRInstruction move = insertMove(from, spillSlot);
                move.setComment(res, "TraceGlobalMoveResolver: breakCycle");
            }
            block(spillSlot);
            mappingFrom.set(spillCandidate, spillSlot);
            unblock(from);
        }
    }

    @SuppressWarnings("try")
    private void printMapping() {
        try (Indent indent = debug.logAndIndent("Mapping")) {
            for (int i = mappingFrom.size() - 1; i >= 0; i--) {
                debug.log("move %s <- %s (%s)", mappingTo.get(i), mappingFrom.get(i), mappingFromStack.get(i));
            }
        }
    }

    public void setInsertPosition(ArrayList<LIRInstruction> insertList, int insertIdx) {
        assert this.insertIdx == -1 : "use moveInsertPosition instead of setInsertPosition when data already set";

        createInsertionBuffer(insertList);
        this.insertIdx = insertIdx;
    }

    @Override
    public void addMapping(Value from, AllocatableValue to, Value fromStack) {
        if (debug.isLogEnabled()) {
            debug.log("add move mapping from %s to %s", from, to);
        }

        assert !from.equals(to) : "from and to interval equal: " + from;
        assert LIRKind.verifyMoveKinds(to.getValueKind(), from.getValueKind(), registerAllocationConfig) : String.format("Kind mismatch: %s vs. %s, from=%s, to=%s", from.getValueKind(),
                        to.getValueKind(), from, to);
        assert fromStack == null || LIRKind.verifyMoveKinds(to.getValueKind(), fromStack.getValueKind(), registerAllocationConfig) : String.format("Kind mismatch: %s vs. %s, fromStack=%s, to=%s",
                        fromStack.getValueKind(),
                        to.getValueKind(), fromStack, to);
        mappingFrom.add(from);
        mappingFromStack.add(fromStack);
        mappingTo.add(to);
    }

    public void resolveAndAppendMoves() {
        if (hasMappings()) {
            resolveMappings();
        }
        appendInsertionBuffer();
    }

    private int getStackArrayIndex(Value stackSlotValue) {
        if (isStackSlot(stackSlotValue)) {
            return getStackArrayIndex(asStackSlot(stackSlotValue));
        }
        if (isVirtualStackSlot(stackSlotValue)) {
            return getStackArrayIndex(asVirtualStackSlot(stackSlotValue));
        }
        throw GraalError.shouldNotReachHere("value is not a stack slot: " + stackSlotValue);
    }

    private int getStackArrayIndex(StackSlot stackSlot) {
        int stackIdx;
        if (stackSlot.isInCallerFrame()) {
            // incoming stack arguments can be ignored
            stackIdx = STACK_SLOT_IN_CALLER_FRAME_IDX;
        } else {
            assert stackSlot.getRawAddFrameSize() : "Unexpected stack slot: " + stackSlot;
            int offset = -stackSlot.getRawOffset();
            assert 0 <= offset && offset < firstVirtualStackIndex : String.format("Wrong stack slot offset: %d (first virtual stack slot index: %d", offset, firstVirtualStackIndex);
            stackIdx = offset;
        }
        return stackIdx;
    }

    private int getStackArrayIndex(VirtualStackSlot virtualStackSlot) {
        return firstVirtualStackIndex + virtualStackSlot.getId();
    }

}
