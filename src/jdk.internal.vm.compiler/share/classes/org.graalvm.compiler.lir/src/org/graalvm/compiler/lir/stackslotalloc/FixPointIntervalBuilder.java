/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.stackslotalloc;

import static org.graalvm.compiler.lir.LIRValueUtil.asVirtualStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.EnumSet;

import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.VirtualStackSlot;

import jdk.vm.ci.meta.Value;

/**
 * Calculates the stack intervals using a worklist-based backwards data-flow analysis.
 */
final class FixPointIntervalBuilder {
    private final BlockMap<BitSet> liveInMap;
    private final BlockMap<BitSet> liveOutMap;
    private final LIR lir;
    private final int maxOpId;
    private final StackInterval[] stackSlotMap;
    private final EconomicSet<LIRInstruction> usePos;

    /**
     * The number of allocated stack slots.
     */
    private static final CounterKey uninitializedSlots = DebugContext.counter("StackSlotAllocator[uninitializedSlots]");

    FixPointIntervalBuilder(LIR lir, StackInterval[] stackSlotMap, int maxOpId) {
        this.lir = lir;
        this.stackSlotMap = stackSlotMap;
        this.maxOpId = maxOpId;
        liveInMap = new BlockMap<>(lir.getControlFlowGraph());
        liveOutMap = new BlockMap<>(lir.getControlFlowGraph());
        this.usePos = EconomicSet.create(Equivalence.IDENTITY);
    }

    /**
     * Builds the lifetime intervals for {@link VirtualStackSlot virtual stack slots}, sets up
     * {@link #stackSlotMap} and returns a set of use positions, i.e. instructions that contain
     * virtual stack slots.
     */
    EconomicSet<LIRInstruction> build() {
        Deque<AbstractBlockBase<?>> worklist = new ArrayDeque<>();
        AbstractBlockBase<?>[] blocks = lir.getControlFlowGraph().getBlocks();
        for (int i = blocks.length - 1; i >= 0; i--) {
            worklist.add(blocks[i]);
        }
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            liveInMap.put(block, new BitSet(stackSlotMap.length));
        }
        while (!worklist.isEmpty()) {
            AbstractBlockBase<?> block = worklist.poll();
            processBlock(block, worklist);
        }
        return usePos;
    }

    /**
     * Merge outSet with in-set of successors.
     */
    private boolean updateOutBlock(AbstractBlockBase<?> block) {
        BitSet union = new BitSet(stackSlotMap.length);
        for (AbstractBlockBase<?> succ : block.getSuccessors()) {
            union.or(liveInMap.get(succ));
        }
        BitSet outSet = liveOutMap.get(block);
        // check if changed
        if (outSet == null || !union.equals(outSet)) {
            liveOutMap.put(block, union);
            return true;
        }
        return false;
    }

    @SuppressWarnings("try")
    private void processBlock(AbstractBlockBase<?> block, Deque<AbstractBlockBase<?>> worklist) {
        DebugContext debug = lir.getDebug();
        if (updateOutBlock(block)) {
            try (Indent indent = debug.logAndIndent("handle block %s", block)) {
                ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
                // get out set and mark intervals
                BitSet outSet = liveOutMap.get(block);
                markOutInterval(outSet, getBlockEnd(instructions));
                printLiveSet("liveOut", outSet);

                // process instructions
                BlockClosure closure = new BlockClosure((BitSet) outSet.clone());
                for (int i = instructions.size() - 1; i >= 0; i--) {
                    LIRInstruction inst = instructions.get(i);
                    closure.processInstructionBottomUp(inst);
                }

                // add predecessors to work list
                for (AbstractBlockBase<?> b : block.getPredecessors()) {
                    worklist.add(b);
                }
                // set in set and mark intervals
                BitSet inSet = closure.getCurrentSet();
                liveInMap.put(block, inSet);
                markInInterval(inSet, getBlockBegin(instructions));
                printLiveSet("liveIn", inSet);
            }
        }
    }

    @SuppressWarnings("try")
    private void printLiveSet(String label, BitSet liveSet) {
        DebugContext debug = lir.getDebug();
        if (debug.isLogEnabled()) {
            try (Indent indent = debug.logAndIndent(label)) {
                debug.log("%s", liveSetToString(liveSet));
            }
        }
    }

    private String liveSetToString(BitSet liveSet) {
        StringBuilder sb = new StringBuilder();
        for (int i = liveSet.nextSetBit(0); i >= 0; i = liveSet.nextSetBit(i + 1)) {
            StackInterval interval = getIntervalFromStackId(i);
            sb.append(interval.getOperand()).append(" ");
        }
        return sb.toString();
    }

    private void markOutInterval(BitSet outSet, int blockEndOpId) {
        DebugContext debug = lir.getDebug();
        for (int i = outSet.nextSetBit(0); i >= 0; i = outSet.nextSetBit(i + 1)) {
            StackInterval interval = getIntervalFromStackId(i);
            debug.log("mark live operand: %s", interval.getOperand());
            interval.addTo(blockEndOpId);
        }
    }

    private void markInInterval(BitSet inSet, int blockFirstOpId) {
        DebugContext debug = lir.getDebug();
        for (int i = inSet.nextSetBit(0); i >= 0; i = inSet.nextSetBit(i + 1)) {
            StackInterval interval = getIntervalFromStackId(i);
            debug.log("mark live operand: %s", interval.getOperand());
            interval.addFrom(blockFirstOpId);
        }
    }

    private final class BlockClosure {
        private final BitSet currentSet;

        private BlockClosure(BitSet set) {
            currentSet = set;
        }

        private BitSet getCurrentSet() {
            return currentSet;
        }

        /**
         * Process all values of an instruction bottom-up, i.e. definitions before usages. Values
         * that start or end at the current operation are not included.
         */
        @SuppressWarnings("try")
        private void processInstructionBottomUp(LIRInstruction op) {
            DebugContext debug = lir.getDebug();
            try (Indent indent = debug.logAndIndent("handle op %d, %s", op.id(), op)) {
                // kills
                op.visitEachTemp(defConsumer);
                op.visitEachOutput(defConsumer);

                // gen - values that are considered alive for this state
                op.visitEachAlive(useConsumer);
                op.visitEachState(useConsumer);
                // mark locations
                // gen
                op.visitEachInput(useConsumer);
            }
        }

        InstructionValueConsumer useConsumer = new InstructionValueConsumer() {
            @Override
            public void visitValue(LIRInstruction inst, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVirtualStackSlot(operand)) {
                    DebugContext debug = lir.getDebug();
                    VirtualStackSlot vslot = asVirtualStackSlot(operand);
                    addUse(vslot, inst, flags);
                    addRegisterHint(inst, vslot, mode, flags, false);
                    usePos.add(inst);
                    debug.log("set operand: %s", operand);
                    currentSet.set(vslot.getId());
                }
            }
        };

        InstructionValueConsumer defConsumer = new InstructionValueConsumer() {
            @Override
            public void visitValue(LIRInstruction inst, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVirtualStackSlot(operand)) {
                    DebugContext debug = lir.getDebug();
                    VirtualStackSlot vslot = asVirtualStackSlot(operand);
                    addDef(vslot, inst);
                    addRegisterHint(inst, vslot, mode, flags, true);
                    usePos.add(inst);
                    debug.log("clear operand: %s", operand);
                    currentSet.clear(vslot.getId());
                }

            }
        };

        private void addUse(VirtualStackSlot stackSlot, LIRInstruction inst, EnumSet<OperandFlag> flags) {
            StackInterval interval = getOrCreateInterval(stackSlot);
            if (flags.contains(OperandFlag.UNINITIALIZED)) {
                // Stack slot is marked uninitialized so we have to assume it is live all
                // the time.
                DebugContext debug = lir.getDebug();
                if (debug.isCountEnabled() && !(interval.from() == 0 && interval.to() == maxOpId)) {
                    uninitializedSlots.increment(debug);
                }
                interval.addFrom(0);
                interval.addTo(maxOpId);
            } else {
                interval.addTo(inst.id());
            }
        }

        private void addDef(VirtualStackSlot stackSlot, LIRInstruction inst) {
            StackInterval interval = getOrCreateInterval(stackSlot);
            interval.addFrom(inst.id());
        }

        void addRegisterHint(final LIRInstruction op, VirtualStackSlot targetValue, OperandMode mode, EnumSet<OperandFlag> flags, final boolean hintAtDef) {
            if (flags.contains(OperandFlag.HINT)) {
                InstructionValueProcedure proc = new InstructionValueProcedure() {
                    @Override
                    public Value doValue(LIRInstruction instruction, Value registerHint, OperandMode vaueMode, EnumSet<OperandFlag> valueFlags) {
                        if (isVirtualStackSlot(registerHint)) {
                            StackInterval from = getOrCreateInterval((VirtualStackSlot) registerHint);
                            StackInterval to = getOrCreateInterval(targetValue);

                            // hints always point from def to use
                            if (hintAtDef) {
                                to.setLocationHint(from);
                            } else {
                                from.setLocationHint(to);
                            }
                            DebugContext debug = lir.getDebug();
                            if (debug.isLogEnabled()) {
                                debug.log("operation %s at opId %d: added hint from interval %s to %s", op, op.id(), from, to);
                            }

                            return registerHint;
                        }
                        return null;
                    }
                };
                op.forEachRegisterHint(targetValue, mode, proc);
            }
        }

    }

    private StackInterval get(VirtualStackSlot stackSlot) {
        return stackSlotMap[stackSlot.getId()];
    }

    private void put(VirtualStackSlot stackSlot, StackInterval interval) {
        stackSlotMap[stackSlot.getId()] = interval;
    }

    private StackInterval getOrCreateInterval(VirtualStackSlot stackSlot) {
        StackInterval interval = get(stackSlot);
        if (interval == null) {
            interval = new StackInterval(stackSlot, stackSlot.getValueKind());
            put(stackSlot, interval);
        }
        return interval;
    }

    private StackInterval getIntervalFromStackId(int id) {
        return stackSlotMap[id];
    }

    private static int getBlockBegin(ArrayList<LIRInstruction> instructions) {
        return instructions.get(0).id();
    }

    private static int getBlockEnd(ArrayList<LIRInstruction> instructions) {
        return instructions.get(instructions.size() - 1).id() + 1;
    }

}
