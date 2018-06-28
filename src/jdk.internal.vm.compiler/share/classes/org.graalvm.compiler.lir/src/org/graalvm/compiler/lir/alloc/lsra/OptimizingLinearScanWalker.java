/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterBinding;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterBindingLists;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterPriority;
import org.graalvm.compiler.lir.alloc.lsra.Interval.State;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

public class OptimizingLinearScanWalker extends LinearScanWalker {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable LSRA optimization", type = OptionType.Debug)
        public static final OptionKey<Boolean> LSRAOptimization = new OptionKey<>(false);
        @Option(help = "LSRA optimization: Only split but do not reassign", type = OptionType.Debug)
        public static final OptionKey<Boolean> LSRAOptSplitOnly = new OptionKey<>(false);
        // @formatter:on
    }

    OptimizingLinearScanWalker(LinearScan allocator, Interval unhandledFixedFirst, Interval unhandledAnyFirst) {
        super(allocator, unhandledFixedFirst, unhandledAnyFirst);
    }

    @SuppressWarnings("try")
    @Override
    protected void handleSpillSlot(Interval interval) {
        assert interval.location() != null : "interval  not assigned " + interval;
        if (interval.canMaterialize()) {
            assert !isStackSlotValue(interval.location()) : "interval can materialize but assigned to a stack slot " + interval;
            return;
        }
        assert isStackSlotValue(interval.location()) : "interval not assigned to a stack slot " + interval;
        DebugContext debug = allocator.getDebug();
        try (DebugContext.Scope s1 = debug.scope("LSRAOptimization")) {
            debug.log("adding stack to unhandled list %s", interval);
            unhandledLists.addToListSortedByStartAndUsePositions(RegisterBinding.Stack, interval);
        }
    }

    @SuppressWarnings("unused")
    private static void printRegisterBindingList(DebugContext debug, RegisterBindingLists list, RegisterBinding binding) {
        for (Interval interval = list.get(binding); !interval.isEndMarker(); interval = interval.next) {
            debug.log("%s", interval);
        }
    }

    @SuppressWarnings("try")
    @Override
    void walk() {
        try (DebugContext.Scope s = allocator.getDebug().scope("OptimizingLinearScanWalker")) {
            for (AbstractBlockBase<?> block : allocator.sortedBlocks()) {
                optimizeBlock(block);
            }
        }
        super.walk();
    }

    @SuppressWarnings("try")
    private void optimizeBlock(AbstractBlockBase<?> block) {
        if (block.getPredecessorCount() == 1) {
            int nextBlock = allocator.getFirstLirInstructionId(block);
            DebugContext debug = allocator.getDebug();
            try (DebugContext.Scope s1 = debug.scope("LSRAOptimization")) {
                debug.log("next block: %s (%d)", block, nextBlock);
            }
            try (Indent indent0 = debug.indent()) {
                walkTo(nextBlock);

                try (DebugContext.Scope s1 = debug.scope("LSRAOptimization")) {
                    boolean changed = true;
                    // we need to do this because the active lists might change
                    loop: while (changed) {
                        changed = false;
                        try (Indent indent1 = debug.logAndIndent("Active intervals: (block %s [%d])", block, nextBlock)) {
                            for (Interval active = activeLists.get(RegisterBinding.Any); !active.isEndMarker(); active = active.next) {
                                debug.log("active   (any): %s", active);
                                if (optimize(nextBlock, block, active, RegisterBinding.Any)) {
                                    changed = true;
                                    break loop;
                                }
                            }
                            for (Interval active = activeLists.get(RegisterBinding.Stack); !active.isEndMarker(); active = active.next) {
                                debug.log("active (stack): %s", active);
                                if (optimize(nextBlock, block, active, RegisterBinding.Stack)) {
                                    changed = true;
                                    break loop;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("try")
    private boolean optimize(int currentPos, AbstractBlockBase<?> currentBlock, Interval currentInterval, RegisterBinding binding) {
        // BEGIN initialize and sanity checks
        assert currentBlock != null : "block must not be null";
        assert currentInterval != null : "interval must not be null";

        assert currentBlock.getPredecessorCount() == 1 : "more than one predecessors -> optimization not possible";

        if (!currentInterval.isSplitChild()) {
            // interval is not a split child -> no need for optimization
            return false;
        }

        if (currentInterval.from() == currentPos) {
            // the interval starts at the current position so no need for splitting
            return false;
        }

        // get current location
        AllocatableValue currentLocation = currentInterval.location();
        assert currentLocation != null : "active intervals must have a location assigned!";

        // get predecessor stuff
        AbstractBlockBase<?> predecessorBlock = currentBlock.getPredecessors()[0];
        int predEndId = allocator.getLastLirInstructionId(predecessorBlock);
        Interval predecessorInterval = currentInterval.getIntervalCoveringOpId(predEndId);
        assert predecessorInterval != null : "variable not live at the end of the only predecessor! " + predecessorBlock + " -> " + currentBlock + " interval: " + currentInterval;
        AllocatableValue predecessorLocation = predecessorInterval.location();
        assert predecessorLocation != null : "handled intervals must have a location assigned!";

        // END initialize and sanity checks

        if (currentLocation.equals(predecessorLocation)) {
            // locations are already equal -> nothing to optimize
            return false;
        }

        if (!isStackSlotValue(predecessorLocation) && !isRegister(predecessorLocation)) {
            assert predecessorInterval.canMaterialize();
            // value is materialized -> no need for optimization
            return false;
        }

        assert isStackSlotValue(currentLocation) || isRegister(currentLocation) : "current location not a register or stack slot " + currentLocation;

        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("location differs: %s vs. %s", predecessorLocation, currentLocation)) {
            // split current interval at current position
            debug.log("splitting at position %d", currentPos);

            assert allocator.isBlockBegin(currentPos) && ((currentPos & 1) == 0) : "split pos must be even when on block boundary";

            Interval splitPart = currentInterval.split(currentPos, allocator);
            activeLists.remove(binding, currentInterval);

            assert splitPart.from() >= currentPosition : "cannot append new interval before current walk position";

            // the currentSplitChild is needed later when moves are inserted for reloading
            assert splitPart.currentSplitChild() == currentInterval : "overwriting wrong currentSplitChild";
            splitPart.makeCurrentSplitChild();

            if (debug.isLogEnabled()) {
                debug.log("left interval  : %s", currentInterval.logString(allocator));
                debug.log("right interval : %s", splitPart.logString(allocator));
            }

            if (Options.LSRAOptSplitOnly.getValue(allocator.getOptions())) {
                // just add the split interval to the unhandled list
                unhandledLists.addToListSortedByStartAndUsePositions(RegisterBinding.Any, splitPart);
            } else {
                if (isRegister(predecessorLocation)) {
                    splitRegisterInterval(splitPart, asRegister(predecessorLocation));
                } else {
                    assert isStackSlotValue(predecessorLocation);
                    debug.log("assigning interval %s to %s", splitPart, predecessorLocation);
                    splitPart.assignLocation(predecessorLocation);
                    // activate interval
                    activeLists.addToListSortedByCurrentFromPositions(RegisterBinding.Stack, splitPart);
                    splitPart.state = State.Active;

                    splitStackInterval(splitPart);
                }
            }
        }
        return true;
    }

    @SuppressWarnings("try")
    private void splitRegisterInterval(Interval interval, Register reg) {
        // collect current usage of registers
        initVarsForAlloc(interval);
        initUseLists(false);
        spillExcludeActiveFixed();
        // spillBlockUnhandledFixed(cur);
        assert unhandledLists.get(RegisterBinding.Fixed).isEndMarker() : "must not have unhandled fixed intervals because all fixed intervals have a use at position 0";
        spillBlockInactiveFixed(interval);
        spillCollectActiveAny(RegisterPriority.LiveAtLoopEnd);
        spillCollectInactiveAny(interval);

        DebugContext debug = allocator.getDebug();
        if (debug.isLogEnabled()) {
            try (Indent indent2 = debug.logAndIndent("state of registers:")) {
                for (Register register : availableRegs) {
                    int i = register.number;
                    try (Indent indent3 = debug.logAndIndent("reg %d: usePos: %d, blockPos: %d, intervals: ", i, usePos[i], blockPos[i])) {
                        for (int j = 0; j < spillIntervals[i].size(); j++) {
                            debug.log("%d ", spillIntervals[i].get(j).operandNumber);
                        }
                    }
                }
            }
        }

        // the register must be free at least until this position
        boolean needSplit = blockPos[reg.number] <= interval.to();

        int splitPos = blockPos[reg.number];

        assert splitPos > 0 : "invalid splitPos";
        assert needSplit || splitPos > interval.from() : "splitting interval at from";

        debug.log("assigning interval %s to %s", interval, reg);
        interval.assignLocation(reg.asValue(interval.kind()));
        if (needSplit) {
            // register not available for full interval : so split it
            splitWhenPartialRegisterAvailable(interval, splitPos);
        }

        // perform splitting and spilling for all affected intervals
        splitAndSpillIntersectingIntervals(reg);

        // activate interval
        activeLists.addToListSortedByCurrentFromPositions(RegisterBinding.Any, interval);
        interval.state = State.Active;

    }
}
