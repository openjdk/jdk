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

import static jdk.vm.ci.code.CodeUtil.isOdd;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig.AllocatableRegisters;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.alloc.OutOfRegistersException;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterBinding;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterPriority;
import org.graalvm.compiler.lir.alloc.lsra.Interval.SpillState;
import org.graalvm.compiler.lir.alloc.lsra.Interval.State;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.Value;

/**
 */
class LinearScanWalker extends IntervalWalker {

    protected Register[] availableRegs;

    protected final int[] usePos;
    protected final int[] blockPos;

    protected List<Interval>[] spillIntervals;

    private MoveResolver moveResolver; // for ordering spill moves

    private int minReg;

    private int maxReg;

    /**
     * Only 10% of the lists in {@link #spillIntervals} are actually used. But when they are used,
     * they can grow quite long. The maximum length observed was 45 (all numbers taken from a
     * bootstrap run of Graal). Therefore, we initialize {@link #spillIntervals} with this marker
     * value, and allocate a "real" list only on demand in {@link #setUsePos}.
     */
    private static final List<Interval> EMPTY_LIST = Collections.emptyList();

    // accessors mapped to same functions in class LinearScan
    int blockCount() {
        return allocator.blockCount();
    }

    AbstractBlockBase<?> blockAt(int idx) {
        return allocator.blockAt(idx);
    }

    AbstractBlockBase<?> blockOfOpWithId(int opId) {
        return allocator.blockForId(opId);
    }

    LinearScanWalker(LinearScan allocator, Interval unhandledFixedFirst, Interval unhandledAnyFirst) {
        super(allocator, unhandledFixedFirst, unhandledAnyFirst);

        moveResolver = allocator.createMoveResolver();
        spillIntervals = Util.uncheckedCast(new List<?>[allocator.getRegisters().size()]);
        for (int i = 0; i < allocator.getRegisters().size(); i++) {
            spillIntervals[i] = EMPTY_LIST;
        }
        usePos = new int[allocator.getRegisters().size()];
        blockPos = new int[allocator.getRegisters().size()];
    }

    void initUseLists(boolean onlyProcessUsePos) {
        for (Register register : availableRegs) {
            int i = register.number;
            usePos[i] = Integer.MAX_VALUE;

            if (!onlyProcessUsePos) {
                blockPos[i] = Integer.MAX_VALUE;
                spillIntervals[i].clear();
            }
        }
    }

    int maxRegisterNumber() {
        return maxReg;
    }

    int minRegisterNumber() {
        return minReg;
    }

    boolean isRegisterInRange(int reg) {
        return reg >= minRegisterNumber() && reg <= maxRegisterNumber();
    }

    void excludeFromUse(Interval i) {
        Value location = i.location();
        int i1 = asRegister(location).number;
        if (isRegisterInRange(i1)) {
            usePos[i1] = 0;
        }
    }

    void setUsePos(Interval interval, int usePos, boolean onlyProcessUsePos) {
        if (usePos != -1) {
            assert usePos != 0 : "must use excludeFromUse to set usePos to 0";
            int i = asRegister(interval.location()).number;
            if (isRegisterInRange(i)) {
                if (this.usePos[i] > usePos) {
                    this.usePos[i] = usePos;
                }
                if (!onlyProcessUsePos) {
                    List<Interval> list = spillIntervals[i];
                    if (list == EMPTY_LIST) {
                        list = new ArrayList<>(2);
                        spillIntervals[i] = list;
                    }
                    list.add(interval);
                }
            }
        }
    }

    void setBlockPos(Interval i, int blockPos) {
        if (blockPos != -1) {
            int reg = asRegister(i.location()).number;
            if (isRegisterInRange(reg)) {
                if (this.blockPos[reg] > blockPos) {
                    this.blockPos[reg] = blockPos;
                }
                if (usePos[reg] > blockPos) {
                    usePos[reg] = blockPos;
                }
            }
        }
    }

    void freeExcludeActiveFixed() {
        Interval interval = activeLists.get(RegisterBinding.Fixed);
        while (!interval.isEndMarker()) {
            assert isRegister(interval.location()) : "active interval must have a register assigned";
            excludeFromUse(interval);
            interval = interval.next;
        }
    }

    void freeExcludeActiveAny() {
        Interval interval = activeLists.get(RegisterBinding.Any);
        while (!interval.isEndMarker()) {
            assert isRegister(interval.location()) : "active interval must have a register assigned";
            excludeFromUse(interval);
            interval = interval.next;
        }
    }

    void freeCollectInactiveFixed(Interval current) {
        Interval interval = inactiveLists.get(RegisterBinding.Fixed);
        while (!interval.isEndMarker()) {
            if (current.to() <= interval.currentFrom()) {
                assert interval.currentIntersectsAt(current) == -1 : "must not intersect";
                setUsePos(interval, interval.currentFrom(), true);
            } else {
                setUsePos(interval, interval.currentIntersectsAt(current), true);
            }
            interval = interval.next;
        }
    }

    void freeCollectInactiveAny(Interval current) {
        Interval interval = inactiveLists.get(RegisterBinding.Any);
        while (!interval.isEndMarker()) {
            setUsePos(interval, interval.currentIntersectsAt(current), true);
            interval = interval.next;
        }
    }

    void freeCollectUnhandled(RegisterBinding kind, Interval current) {
        Interval interval = unhandledLists.get(kind);
        while (!interval.isEndMarker()) {
            setUsePos(interval, interval.intersectsAt(current), true);
            if (kind == RegisterBinding.Fixed && current.to() <= interval.from()) {
                setUsePos(interval, interval.from(), true);
            }
            interval = interval.next;
        }
    }

    void spillExcludeActiveFixed() {
        Interval interval = activeLists.get(RegisterBinding.Fixed);
        while (!interval.isEndMarker()) {
            excludeFromUse(interval);
            interval = interval.next;
        }
    }

    void spillBlockUnhandledFixed(Interval current) {
        Interval interval = unhandledLists.get(RegisterBinding.Fixed);
        while (!interval.isEndMarker()) {
            setBlockPos(interval, interval.intersectsAt(current));
            interval = interval.next;
        }
    }

    void spillBlockInactiveFixed(Interval current) {
        Interval interval = inactiveLists.get(RegisterBinding.Fixed);
        while (!interval.isEndMarker()) {
            if (current.to() > interval.currentFrom()) {
                setBlockPos(interval, interval.currentIntersectsAt(current));
            } else {
                assert interval.currentIntersectsAt(current) == -1 : "invalid optimization: intervals intersect";
            }

            interval = interval.next;
        }
    }

    void spillCollectActiveAny(RegisterPriority registerPriority) {
        Interval interval = activeLists.get(RegisterBinding.Any);
        while (!interval.isEndMarker()) {
            setUsePos(interval, Math.min(interval.nextUsage(registerPriority, currentPosition), interval.to()), false);
            interval = interval.next;
        }
    }

    void spillCollectInactiveAny(Interval current) {
        Interval interval = inactiveLists.get(RegisterBinding.Any);
        while (!interval.isEndMarker()) {
            if (interval.currentIntersects(current)) {
                setUsePos(interval, Math.min(interval.nextUsage(RegisterPriority.LiveAtLoopEnd, currentPosition), interval.to()), false);
            }
            interval = interval.next;
        }
    }

    void insertMove(int operandId, Interval srcIt, Interval dstIt) {
        // output all moves here. When source and target are equal, the move is
        // optimized away later in assignRegNums

        int opId = (operandId + 1) & ~1;
        AbstractBlockBase<?> opBlock = allocator.blockForId(opId);
        assert opId > 0 && allocator.blockForId(opId - 2) == opBlock : "cannot insert move at block boundary";

        // calculate index of instruction inside instruction list of current block
        // the minimal index (for a block with no spill moves) can be calculated because the
        // numbering of instructions is known.
        // When the block already contains spill moves, the index must be increased until the
        // correct index is reached.
        ArrayList<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(opBlock);
        int index = (opId - instructions.get(0).id()) >> 1;
        assert instructions.get(index).id() <= opId : "error in calculation";

        while (instructions.get(index).id() != opId) {
            index++;
            assert 0 <= index && index < instructions.size() : "index out of bounds";
        }
        assert 1 <= index && index < instructions.size() : "index out of bounds";
        assert instructions.get(index).id() == opId : "error in calculation";

        // insert new instruction before instruction at position index
        moveResolver.moveInsertPosition(instructions, index);
        moveResolver.addMapping(srcIt, dstIt);
    }

    int findOptimalSplitPos(AbstractBlockBase<?> minBlock, AbstractBlockBase<?> maxBlock, int maxSplitPos) {
        int fromBlockNr = minBlock.getLinearScanNumber();
        int toBlockNr = maxBlock.getLinearScanNumber();

        assert 0 <= fromBlockNr && fromBlockNr < blockCount() : "out of range";
        assert 0 <= toBlockNr && toBlockNr < blockCount() : "out of range";
        assert fromBlockNr < toBlockNr : "must cross block boundary";

        // Try to split at end of maxBlock. If this would be after
        // maxSplitPos, then use the begin of maxBlock
        int optimalSplitPos = allocator.getLastLirInstructionId(maxBlock) + 2;
        if (optimalSplitPos > maxSplitPos) {
            optimalSplitPos = allocator.getFirstLirInstructionId(maxBlock);
        }

        int minLoopDepth = maxBlock.getLoopDepth();
        for (int i = toBlockNr - 1; minLoopDepth > 0 && i >= fromBlockNr; i--) {
            AbstractBlockBase<?> cur = blockAt(i);

            if (cur.getLoopDepth() < minLoopDepth) {
                // block with lower loop-depth found . split at the end of this block
                minLoopDepth = cur.getLoopDepth();
                optimalSplitPos = allocator.getLastLirInstructionId(cur) + 2;
            }
        }
        assert optimalSplitPos > allocator.maxOpId() || allocator.isBlockBegin(optimalSplitPos) : "algorithm must move split pos to block boundary";

        return optimalSplitPos;
    }

    int findOptimalSplitPos(Interval interval, int minSplitPos, int maxSplitPos, boolean doLoopOptimization) {
        DebugContext debug = allocator.getDebug();
        int optimalSplitPos = -1;
        if (minSplitPos == maxSplitPos) {
            // trivial case, no optimization of split position possible
            if (debug.isLogEnabled()) {
                debug.log("min-pos and max-pos are equal, no optimization possible");
            }
            optimalSplitPos = minSplitPos;

        } else {
            assert minSplitPos < maxSplitPos : "must be true then";
            assert minSplitPos > 0 : "cannot access minSplitPos - 1 otherwise";

            // reason for using minSplitPos - 1: when the minimal split pos is exactly at the
            // beginning of a block, then minSplitPos is also a possible split position.
            // Use the block before as minBlock, because then minBlock.lastLirInstructionId() + 2 ==
            // minSplitPos
            AbstractBlockBase<?> minBlock = allocator.blockForId(minSplitPos - 1);

            // reason for using maxSplitPos - 1: otherwise there would be an assert on failure
            // when an interval ends at the end of the last block of the method
            // (in this case, maxSplitPos == allocator().maxLirOpId() + 2, and there is no
            // block at this opId)
            AbstractBlockBase<?> maxBlock = allocator.blockForId(maxSplitPos - 1);

            assert minBlock.getLinearScanNumber() <= maxBlock.getLinearScanNumber() : "invalid order";
            if (minBlock == maxBlock) {
                // split position cannot be moved to block boundary : so split as late as possible
                if (debug.isLogEnabled()) {
                    debug.log("cannot move split pos to block boundary because minPos and maxPos are in same block");
                }
                optimalSplitPos = maxSplitPos;

            } else {
                if (interval.hasHoleBetween(maxSplitPos - 1, maxSplitPos) && !allocator.isBlockBegin(maxSplitPos)) {
                    // Do not move split position if the interval has a hole before maxSplitPos.
                    // Intervals resulting from Phi-Functions have more than one definition (marked
                    // as mustHaveRegister) with a hole before each definition. When the register is
                    // needed
                    // for the second definition : an earlier reloading is unnecessary.
                    if (debug.isLogEnabled()) {
                        debug.log("interval has hole just before maxSplitPos, so splitting at maxSplitPos");
                    }
                    optimalSplitPos = maxSplitPos;

                } else {
                    // seach optimal block boundary between minSplitPos and maxSplitPos
                    if (debug.isLogEnabled()) {
                        debug.log("moving split pos to optimal block boundary between block B%d and B%d", minBlock.getId(), maxBlock.getId());
                    }

                    if (doLoopOptimization) {
                        // Loop optimization: if a loop-end marker is found between min- and
                        // max-position :
                        // then split before this loop
                        int loopEndPos = interval.nextUsageExact(RegisterPriority.LiveAtLoopEnd, allocator.getLastLirInstructionId(minBlock) + 2);
                        if (debug.isLogEnabled()) {
                            debug.log("loop optimization: loop end found at pos %d", loopEndPos);
                        }

                        assert loopEndPos > minSplitPos : "invalid order";
                        if (loopEndPos < maxSplitPos) {
                            // loop-end marker found between min- and max-position
                            // if it is not the end marker for the same loop as the min-position :
                            // then move
                            // the max-position to this loop block.
                            // Desired result: uses tagged as shouldHaveRegister inside a loop cause
                            // a reloading
                            // of the interval (normally, only mustHaveRegister causes a reloading)
                            AbstractBlockBase<?> loopBlock = allocator.blockForId(loopEndPos);

                            if (debug.isLogEnabled()) {
                                debug.log("interval is used in loop that ends in block B%d, so trying to move maxBlock back from B%d to B%d", loopBlock.getId(), maxBlock.getId(), loopBlock.getId());
                            }
                            assert loopBlock != minBlock : "loopBlock and minBlock must be different because block boundary is needed between";

                            int maxSpillPos = allocator.getLastLirInstructionId(loopBlock) + 2;
                            optimalSplitPos = findOptimalSplitPos(minBlock, loopBlock, maxSpillPos);
                            if (optimalSplitPos == maxSpillPos) {
                                optimalSplitPos = -1;
                                if (debug.isLogEnabled()) {
                                    debug.log("loop optimization not necessary");
                                }
                            } else {
                                if (debug.isLogEnabled()) {
                                    debug.log("loop optimization successful");
                                }
                            }
                        }
                    }

                    if (optimalSplitPos == -1) {
                        // not calculated by loop optimization
                        optimalSplitPos = findOptimalSplitPos(minBlock, maxBlock, maxSplitPos);
                    }
                }
            }
        }
        if (debug.isLogEnabled()) {
            debug.log("optimal split position: %d", optimalSplitPos);
        }

        return optimalSplitPos;
    }

    // split an interval at the optimal position between minSplitPos and
    // maxSplitPos in two parts:
    // 1) the left part has already a location assigned
    // 2) the right part is sorted into to the unhandled-list
    @SuppressWarnings("try")
    void splitBeforeUsage(Interval interval, int minSplitPos, int maxSplitPos) {
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("splitting interval %s between %d and %d", interval, minSplitPos, maxSplitPos)) {

            assert interval.from() < minSplitPos : "cannot split at start of interval";
            assert currentPosition < minSplitPos : "cannot split before current position";
            assert minSplitPos <= maxSplitPos : "invalid order";
            assert maxSplitPos <= interval.to() : "cannot split after end of interval";

            int optimalSplitPos = findOptimalSplitPos(interval, minSplitPos, maxSplitPos, true);

            assert minSplitPos <= optimalSplitPos && optimalSplitPos <= maxSplitPos : "out of range";
            assert optimalSplitPos <= interval.to() : "cannot split after end of interval";
            assert optimalSplitPos > interval.from() : "cannot split at start of interval";

            if (optimalSplitPos == interval.to() && interval.nextUsage(RegisterPriority.MustHaveRegister, minSplitPos) == Integer.MAX_VALUE) {
                // the split position would be just before the end of the interval
                // . no split at all necessary
                if (debug.isLogEnabled()) {
                    debug.log("no split necessary because optimal split position is at end of interval");
                }
                return;
            }

            // must calculate this before the actual split is performed and before split position is
            // moved to odd opId
            boolean moveNecessary = !allocator.isBlockBegin(optimalSplitPos) && !interval.hasHoleBetween(optimalSplitPos - 1, optimalSplitPos);

            if (!allocator.isBlockBegin(optimalSplitPos)) {
                // move position before actual instruction (odd opId)
                optimalSplitPos = (optimalSplitPos - 1) | 1;
            }

            if (debug.isLogEnabled()) {
                debug.log("splitting at position %d", optimalSplitPos);
            }

            assert allocator.isBlockBegin(optimalSplitPos) || ((optimalSplitPos & 1) == 1) : "split pos must be odd when not on block boundary";
            assert !allocator.isBlockBegin(optimalSplitPos) || ((optimalSplitPos & 1) == 0) : "split pos must be even on block boundary";

            Interval splitPart = interval.split(optimalSplitPos, allocator);

            splitPart.setInsertMoveWhenActivated(moveNecessary);

            assert splitPart.from() >= currentPosition : "cannot append new interval before current walk position";
            unhandledLists.addToListSortedByStartAndUsePositions(RegisterBinding.Any, splitPart);

            if (debug.isLogEnabled()) {
                debug.log("left interval  %s: %s", moveNecessary ? "      " : "", interval.logString(allocator));
                debug.log("right interval %s: %s", moveNecessary ? "(move)" : "", splitPart.logString(allocator));
            }
        }
    }

    // split an interval at the optimal position between minSplitPos and
    // maxSplitPos in two parts:
    // 1) the left part has already a location assigned
    // 2) the right part is always on the stack and therefore ignored in further processing
    @SuppressWarnings("try")
    void splitForSpilling(Interval interval) {
        DebugContext debug = allocator.getDebug();
        // calculate allowed range of splitting position
        int maxSplitPos = currentPosition;
        int previousUsage = interval.previousUsage(RegisterPriority.ShouldHaveRegister, maxSplitPos);
        if (previousUsage == currentPosition) {
            /*
             * If there is a usage with ShouldHaveRegister priority at the current position fall
             * back to MustHaveRegister priority. This only happens if register priority was
             * downgraded to MustHaveRegister in #allocLockedRegister.
             */
            previousUsage = interval.previousUsage(RegisterPriority.MustHaveRegister, maxSplitPos);
        }
        int minSplitPos = Math.max(previousUsage + 1, interval.from());

        try (Indent indent = debug.logAndIndent("splitting and spilling interval %s between %d and %d", interval, minSplitPos, maxSplitPos)) {

            assert interval.state == State.Active : "why spill interval that is not active?";
            assert interval.from() <= minSplitPos : "cannot split before start of interval";
            assert minSplitPos <= maxSplitPos : "invalid order";
            assert maxSplitPos < interval.to() : "cannot split at end end of interval";
            assert currentPosition < interval.to() : "interval must not end before current position";

            if (minSplitPos == interval.from()) {
                // the whole interval is never used, so spill it entirely to memory

                try (Indent indent2 = debug.logAndIndent("spilling entire interval because split pos is at beginning of interval (use positions: %d)", interval.usePosList().size())) {

                    assert interval.firstUsage(RegisterPriority.MustHaveRegister) > currentPosition : String.format("interval %s must not have use position before currentPosition %d", interval,
                                    currentPosition);

                    allocator.assignSpillSlot(interval);
                    handleSpillSlot(interval);
                    changeSpillState(interval, minSplitPos);

                    // Also kick parent intervals out of register to memory when they have no use
                    // position. This avoids short interval in register surrounded by intervals in
                    // memory . avoid useless moves from memory to register and back
                    Interval parent = interval;
                    while (parent != null && parent.isSplitChild()) {
                        parent = parent.getSplitChildBeforeOpId(parent.from());

                        if (isRegister(parent.location())) {
                            if (parent.firstUsage(RegisterPriority.ShouldHaveRegister) == Integer.MAX_VALUE) {
                                // parent is never used, so kick it out of its assigned register
                                if (debug.isLogEnabled()) {
                                    debug.log("kicking out interval %d out of its register because it is never used", parent.operandNumber);
                                }
                                allocator.assignSpillSlot(parent);
                                handleSpillSlot(parent);
                            } else {
                                // do not go further back because the register is actually used by
                                // the interval
                                parent = null;
                            }
                        }
                    }
                }

            } else {
                // search optimal split pos, split interval and spill only the right hand part
                int optimalSplitPos = findOptimalSplitPos(interval, minSplitPos, maxSplitPos, false);

                assert minSplitPos <= optimalSplitPos && optimalSplitPos <= maxSplitPos : "out of range";
                assert optimalSplitPos < interval.to() : "cannot split at end of interval";
                assert optimalSplitPos >= interval.from() : "cannot split before start of interval";

                if (!allocator.isBlockBegin(optimalSplitPos)) {
                    // move position before actual instruction (odd opId)
                    optimalSplitPos = (optimalSplitPos - 1) | 1;
                }

                try (Indent indent2 = debug.logAndIndent("splitting at position %d", optimalSplitPos)) {
                    assert allocator.isBlockBegin(optimalSplitPos) || ((optimalSplitPos & 1) == 1) : "split pos must be odd when not on block boundary";
                    assert !allocator.isBlockBegin(optimalSplitPos) || ((optimalSplitPos & 1) == 0) : "split pos must be even on block boundary";

                    Interval spilledPart = interval.split(optimalSplitPos, allocator);
                    allocator.assignSpillSlot(spilledPart);
                    handleSpillSlot(spilledPart);
                    changeSpillState(spilledPart, optimalSplitPos);

                    if (!allocator.isBlockBegin(optimalSplitPos)) {
                        if (debug.isLogEnabled()) {
                            debug.log("inserting move from interval %d to %d", interval.operandNumber, spilledPart.operandNumber);
                        }
                        insertMove(optimalSplitPos, interval, spilledPart);
                    }

                    // the currentSplitChild is needed later when moves are inserted for reloading
                    assert spilledPart.currentSplitChild() == interval : "overwriting wrong currentSplitChild";
                    spilledPart.makeCurrentSplitChild();

                    if (debug.isLogEnabled()) {
                        debug.log("left interval: %s", interval.logString(allocator));
                        debug.log("spilled interval   : %s", spilledPart.logString(allocator));
                    }
                }
            }
        }
    }

    // called during register allocation
    private void changeSpillState(Interval interval, int spillPos) {
        switch (interval.spillState()) {
            case NoSpillStore: {
                int defLoopDepth = allocator.blockForId(interval.spillDefinitionPos()).getLoopDepth();
                int spillLoopDepth = allocator.blockForId(spillPos).getLoopDepth();

                if (defLoopDepth < spillLoopDepth) {
                    /*
                     * The loop depth of the spilling position is higher then the loop depth at the
                     * definition of the interval. Move write to memory out of loop.
                     */
                    if (LinearScan.Options.LIROptLSRAOptimizeSpillPosition.getValue(allocator.getOptions())) {
                        // find best spill position in dominator the tree
                        interval.setSpillState(SpillState.SpillInDominator);
                    } else {
                        // store at definition of the interval
                        interval.setSpillState(SpillState.StoreAtDefinition);
                    }
                } else {
                    /*
                     * The interval is currently spilled only once, so for now there is no reason to
                     * store the interval at the definition.
                     */
                    interval.setSpillState(SpillState.OneSpillStore);
                }
                break;
            }

            case OneSpillStore: {
                int defLoopDepth = allocator.blockForId(interval.spillDefinitionPos()).getLoopDepth();
                int spillLoopDepth = allocator.blockForId(spillPos).getLoopDepth();

                if (defLoopDepth <= spillLoopDepth) {
                    if (LinearScan.Options.LIROptLSRAOptimizeSpillPosition.getValue(allocator.getOptions())) {
                        // the interval is spilled more then once
                        interval.setSpillState(SpillState.SpillInDominator);
                    } else {
                        // It is better to store it to memory at the definition.
                        interval.setSpillState(SpillState.StoreAtDefinition);
                    }
                }
                break;
            }

            case SpillInDominator:
            case StoreAtDefinition:
            case StartInMemory:
            case NoOptimization:
            case NoDefinitionFound:
                // nothing to do
                break;

            default:
                throw GraalError.shouldNotReachHere("other states not allowed at this time");
        }
    }

    /**
     * This is called for every interval that is assigned to a stack slot.
     */
    protected void handleSpillSlot(Interval interval) {
        assert interval.location() != null && (interval.canMaterialize() || isStackSlotValue(interval.location())) : "interval not assigned to a stack slot " + interval;
        // Do nothing. Stack slots are not processed in this implementation.
    }

    void splitStackInterval(Interval interval) {
        int minSplitPos = currentPosition + 1;
        int maxSplitPos = Math.min(interval.firstUsage(RegisterPriority.ShouldHaveRegister), interval.to());

        splitBeforeUsage(interval, minSplitPos, maxSplitPos);
    }

    void splitWhenPartialRegisterAvailable(Interval interval, int registerAvailableUntil) {
        int minSplitPos = Math.max(interval.previousUsage(RegisterPriority.ShouldHaveRegister, registerAvailableUntil), interval.from() + 1);
        splitBeforeUsage(interval, minSplitPos, registerAvailableUntil);
    }

    void splitAndSpillInterval(Interval interval) {
        assert interval.state == State.Active || interval.state == State.Inactive : "other states not allowed";

        int currentPos = currentPosition;
        if (interval.state == State.Inactive) {
            // the interval is currently inactive, so no spill slot is needed for now.
            // when the split part is activated, the interval has a new chance to get a register,
            // so in the best case no stack slot is necessary
            assert interval.hasHoleBetween(currentPos - 1, currentPos + 1) : "interval can not be inactive otherwise";
            splitBeforeUsage(interval, currentPos + 1, currentPos + 1);

        } else {
            // search the position where the interval must have a register and split
            // at the optimal position before.
            // The new created part is added to the unhandled list and will get a register
            // when it is activated
            int minSplitPos = currentPos + 1;
            int maxSplitPos = Math.min(interval.nextUsage(RegisterPriority.MustHaveRegister, minSplitPos), interval.to());

            splitBeforeUsage(interval, minSplitPos, maxSplitPos);

            assert interval.nextUsage(RegisterPriority.MustHaveRegister, currentPos) == Integer.MAX_VALUE : "the remaining part is spilled to stack and therefore has no register";
            splitForSpilling(interval);
        }
    }

    @SuppressWarnings("try")
    boolean allocFreeRegister(Interval interval) {
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("trying to find free register for %s", interval)) {

            initUseLists(true);
            freeExcludeActiveFixed();
            freeExcludeActiveAny();
            freeCollectInactiveFixed(interval);
            freeCollectInactiveAny(interval);
            // freeCollectUnhandled(fixedKind, cur);
            assert unhandledLists.get(RegisterBinding.Fixed).isEndMarker() : "must not have unhandled fixed intervals because all fixed intervals have a use at position 0";

            // usePos contains the start of the next interval that has this register assigned
            // (either as a fixed register or a normal allocated register in the past)
            // only intervals overlapping with cur are processed, non-overlapping invervals can be
            // ignored safely
            if (debug.isLogEnabled()) {
                // Enable this logging to see all register states
                try (Indent indent2 = debug.logAndIndent("state of registers:")) {
                    for (Register register : availableRegs) {
                        int i = register.number;
                        debug.log("reg %d: usePos: %d", register.number, usePos[i]);
                    }
                }
            }

            Register hint = null;
            Interval locationHint = interval.locationHint(true);
            if (locationHint != null && locationHint.location() != null && isRegister(locationHint.location())) {
                hint = asRegister(locationHint.location());
                if (debug.isLogEnabled()) {
                    debug.log("hint register %d from interval %s", hint.number, locationHint);
                }
            }
            assert interval.location() == null : "register already assigned to interval";

            // the register must be free at least until this position
            int regNeededUntil = interval.from() + 1;
            int intervalTo = interval.to();

            boolean needSplit = false;
            int splitPos = -1;

            Register reg = null;
            Register minFullReg = null;
            Register maxPartialReg = null;

            for (Register availableReg : availableRegs) {
                int number = availableReg.number;
                if (usePos[number] >= intervalTo) {
                    // this register is free for the full interval
                    if (minFullReg == null || availableReg.equals(hint) || (usePos[number] < usePos[minFullReg.number] && !minFullReg.equals(hint))) {
                        minFullReg = availableReg;
                    }
                } else if (usePos[number] > regNeededUntil) {
                    // this register is at least free until regNeededUntil
                    if (maxPartialReg == null || availableReg.equals(hint) || (usePos[number] > usePos[maxPartialReg.number] && !maxPartialReg.equals(hint))) {
                        maxPartialReg = availableReg;
                    }
                }
            }

            if (minFullReg != null) {
                reg = minFullReg;
            } else if (maxPartialReg != null) {
                needSplit = true;
                reg = maxPartialReg;
            } else {
                return false;
            }

            splitPos = usePos[reg.number];
            interval.assignLocation(reg.asValue(interval.kind()));
            if (debug.isLogEnabled()) {
                debug.log("selected register %d", reg.number);
            }

            assert splitPos > 0 : "invalid splitPos";
            if (needSplit) {
                // register not available for full interval, so split it
                splitWhenPartialRegisterAvailable(interval, splitPos);
            }
            // only return true if interval is completely assigned
            return true;
        }
    }

    void splitAndSpillIntersectingIntervals(Register reg) {
        assert reg != null : "no register assigned";

        for (int i = 0; i < spillIntervals[reg.number].size(); i++) {
            Interval interval = spillIntervals[reg.number].get(i);
            removeFromList(interval);
            splitAndSpillInterval(interval);
        }
    }

    // Split an Interval and spill it to memory so that cur can be placed in a register
    @SuppressWarnings("try")
    void allocLockedRegister(Interval interval) {
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("alloc locked register: need to split and spill to get register for %s", interval)) {

            // the register must be free at least until this position
            int firstUsage = interval.firstUsage(RegisterPriority.MustHaveRegister);
            int firstShouldHaveUsage = interval.firstUsage(RegisterPriority.ShouldHaveRegister);
            int regNeededUntil = Math.min(firstUsage, interval.from() + 1);
            int intervalTo = interval.to();
            assert regNeededUntil >= 0 && regNeededUntil < Integer.MAX_VALUE : "interval has no use";

            Register reg;
            Register ignore;
            /*
             * In the common case we don't spill registers that have _any_ use position that is
             * closer than the next use of the current interval, but if we can't spill the current
             * interval we weaken this strategy and also allow spilling of intervals that have a
             * non-mandatory requirements (no MustHaveRegister use position).
             */
            for (RegisterPriority registerPriority = RegisterPriority.LiveAtLoopEnd; true; registerPriority = RegisterPriority.MustHaveRegister) {
                // collect current usage of registers
                initUseLists(false);
                spillExcludeActiveFixed();
                // spillBlockUnhandledFixed(cur);
                assert unhandledLists.get(RegisterBinding.Fixed).isEndMarker() : "must not have unhandled fixed intervals because all fixed intervals have a use at position 0";
                spillBlockInactiveFixed(interval);
                spillCollectActiveAny(registerPriority);
                spillCollectInactiveAny(interval);
                if (debug.isLogEnabled()) {
                    printRegisterState();
                }

                reg = null;
                ignore = interval.location() != null && isRegister(interval.location()) ? asRegister(interval.location()) : null;

                for (Register availableReg : availableRegs) {
                    int number = availableReg.number;
                    if (availableReg.equals(ignore)) {
                        // this register must be ignored
                    } else if (usePos[number] > regNeededUntil) {
                        if (reg == null || (usePos[number] > usePos[reg.number])) {
                            reg = availableReg;
                        }
                    }
                }

                int regUsePos = (reg == null ? 0 : usePos[reg.number]);
                if (regUsePos <= firstShouldHaveUsage) {
                    if (debug.isLogEnabled()) {
                        debug.log("able to spill current interval. firstUsage(register): %d, usePos: %d", firstUsage, regUsePos);
                    }

                    if (firstUsage <= interval.from() + 1) {
                        if (registerPriority.equals(RegisterPriority.LiveAtLoopEnd)) {
                            /*
                             * Tool of last resort: we can not spill the current interval so we try
                             * to spill an active interval that has a usage but do not require a
                             * register.
                             */
                            debug.log("retry with register priority must have register");
                            continue;
                        }
                        String description = generateOutOfRegErrorMsg(interval, firstUsage, availableRegs);
                        /*
                         * assign a reasonable register and do a bailout in product mode to avoid
                         * errors
                         */
                        allocator.assignSpillSlot(interval);
                        debug.dump(DebugContext.INFO_LEVEL, allocator.getLIR(), description);
                        allocator.printIntervals(description);
                        throw new OutOfRegistersException("LinearScan: no register found", description);
                    }

                    splitAndSpillInterval(interval);
                    return;
                }
                break;
            }

            boolean needSplit = blockPos[reg.number] <= intervalTo;

            int splitPos = blockPos[reg.number];

            if (debug.isLogEnabled()) {
                debug.log("decided to use register %d", reg.number);
            }
            assert splitPos > 0 : "invalid splitPos";
            assert needSplit || splitPos > interval.from() : "splitting interval at from";

            interval.assignLocation(reg.asValue(interval.kind()));
            if (needSplit) {
                // register not available for full interval : so split it
                splitWhenPartialRegisterAvailable(interval, splitPos);
            }

            // perform splitting and spilling for all affected intervals
            splitAndSpillIntersectingIntervals(reg);
            return;
        }
    }

    private static String generateOutOfRegErrorMsg(Interval interval, int firstUsage, Register[] availableRegs) {
        return "Cannot spill interval (" + interval + ") that is used in first instruction (possible reason: no register found) firstUsage=" + firstUsage +
                        ", interval.from()=" + interval.from() + "; already used candidates: " + Arrays.toString(availableRegs);
    }

    @SuppressWarnings("try")
    void printRegisterState() {
        DebugContext debug = allocator.getDebug();
        try (Indent indent2 = debug.logAndIndent("state of registers:")) {
            for (Register reg : availableRegs) {
                int i = reg.number;
                try (Indent indent3 = debug.logAndIndent("reg %d: usePos: %d, blockPos: %d, intervals: ", i, usePos[i], blockPos[i])) {
                    for (int j = 0; j < spillIntervals[i].size(); j++) {
                        debug.log("%s ", spillIntervals[i].get(j));
                    }
                }
            }
        }
    }

    boolean noAllocationPossible(Interval interval) {
        if (allocator.callKillsRegisters()) {
            // fast calculation of intervals that can never get a register because the
            // the next instruction is a call that blocks all registers
            // Note: this only works if a call kills all registers

            // check if this interval is the result of a split operation
            // (an interval got a register until this position)
            int pos = interval.from();
            if (isOdd(pos)) {
                // the current instruction is a call that blocks all registers
                if (pos < allocator.maxOpId() && allocator.hasCall(pos + 1) && interval.to() > pos + 1) {
                    DebugContext debug = allocator.getDebug();
                    if (debug.isLogEnabled()) {
                        debug.log("free register cannot be available because all registers blocked by following call");
                    }

                    // safety check that there is really no register available
                    assert !allocFreeRegister(interval) : "found a register for this interval";
                    return true;
                }
            }
        }
        return false;
    }

    void initVarsForAlloc(Interval interval) {
        AllocatableRegisters allocatableRegisters = allocator.getRegisterAllocationConfig().getAllocatableRegisters(interval.kind().getPlatformKind());
        availableRegs = allocatableRegisters.allocatableRegisters;
        minReg = allocatableRegisters.minRegisterNumber;
        maxReg = allocatableRegisters.maxRegisterNumber;
    }

    static boolean isMove(LIRInstruction op, Interval from, Interval to) {
        if (ValueMoveOp.isValueMoveOp(op)) {
            ValueMoveOp move = ValueMoveOp.asValueMoveOp(op);
            if (isVariable(move.getInput()) && isVariable(move.getResult())) {
                return move.getInput() != null && move.getInput().equals(from.operand) && move.getResult() != null && move.getResult().equals(to.operand);
            }
        }
        return false;
    }

    // optimization (especially for phi functions of nested loops):
    // assign same spill slot to non-intersecting intervals
    void combineSpilledIntervals(Interval interval) {
        if (interval.isSplitChild()) {
            // optimization is only suitable for split parents
            return;
        }

        Interval registerHint = interval.locationHint(false);
        if (registerHint == null) {
            // cur is not the target of a move : otherwise registerHint would be set
            return;
        }
        assert registerHint.isSplitParent() : "register hint must be split parent";

        if (interval.spillState() != SpillState.NoOptimization || registerHint.spillState() != SpillState.NoOptimization) {
            // combining the stack slots for intervals where spill move optimization is applied
            // is not benefitial and would cause problems
            return;
        }

        int beginPos = interval.from();
        int endPos = interval.to();
        if (endPos > allocator.maxOpId() || isOdd(beginPos) || isOdd(endPos)) {
            // safety check that lirOpWithId is allowed
            return;
        }

        if (!isMove(allocator.instructionForId(beginPos), registerHint, interval) || !isMove(allocator.instructionForId(endPos), interval, registerHint)) {
            // cur and registerHint are not connected with two moves
            return;
        }

        Interval beginHint = registerHint.getSplitChildAtOpId(beginPos, LIRInstruction.OperandMode.USE, allocator);
        Interval endHint = registerHint.getSplitChildAtOpId(endPos, LIRInstruction.OperandMode.DEF, allocator);
        if (beginHint == endHint || beginHint.to() != beginPos || endHint.from() != endPos) {
            // registerHint must be split : otherwise the re-writing of use positions does not work
            return;
        }

        assert beginHint.location() != null : "must have register assigned";
        assert endHint.location() == null : "must not have register assigned";
        assert interval.firstUsage(RegisterPriority.MustHaveRegister) == beginPos : "must have use position at begin of interval because of move";
        assert endHint.firstUsage(RegisterPriority.MustHaveRegister) == endPos : "must have use position at begin of interval because of move";

        if (isRegister(beginHint.location())) {
            // registerHint is not spilled at beginPos : so it would not be benefitial to
            // immediately spill cur
            return;
        }
        assert registerHint.spillSlot() != null : "must be set when part of interval was spilled";

        // modify intervals such that cur gets the same stack slot as registerHint
        // delete use positions to prevent the intervals to get a register at beginning
        interval.setSpillSlot(registerHint.spillSlot());
        interval.removeFirstUsePos();
        endHint.removeFirstUsePos();
    }

    // allocate a physical register or memory location to an interval
    @Override
    @SuppressWarnings("try")
    protected boolean activateCurrent(Interval interval) {
        boolean result = true;
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("activating interval %s,  splitParent: %d", interval, interval.splitParent().operandNumber)) {

            final Value operand = interval.operand;
            if (interval.location() != null && isStackSlotValue(interval.location())) {
                // activating an interval that has a stack slot assigned . split it at first use
                // position
                // used for method parameters
                if (debug.isLogEnabled()) {
                    debug.log("interval has spill slot assigned (method parameter) . split it before first use");
                }
                splitStackInterval(interval);
                result = false;

            } else {
                if (interval.location() == null) {
                    // interval has not assigned register . normal allocation
                    // (this is the normal case for most intervals)
                    if (debug.isLogEnabled()) {
                        debug.log("normal allocation of register");
                    }

                    // assign same spill slot to non-intersecting intervals
                    combineSpilledIntervals(interval);

                    initVarsForAlloc(interval);
                    if (noAllocationPossible(interval) || !allocFreeRegister(interval)) {
                        // no empty register available.
                        // split and spill another interval so that this interval gets a register
                        allocLockedRegister(interval);
                    }

                    // spilled intervals need not be move to active-list
                    if (!isRegister(interval.location())) {
                        result = false;
                    }
                }
            }

            // load spilled values that become active from stack slot to register
            if (interval.insertMoveWhenActivated()) {
                assert interval.isSplitChild();
                assert interval.currentSplitChild() != null;
                assert !interval.currentSplitChild().operand.equals(operand) : "cannot insert move between same interval";
                if (debug.isLogEnabled()) {
                    debug.log("Inserting move from interval %d to %d because insertMoveWhenActivated is set", interval.currentSplitChild().operandNumber, interval.operandNumber);
                }

                insertMove(interval.from(), interval.currentSplitChild(), interval);
            }
            interval.makeCurrentSplitChild();

        }

        return result; // true = interval is moved to active list
    }

    public void finishAllocation() {
        // must be called when all intervals are allocated
        moveResolver.resolveAndAppendMoves();
    }
}
