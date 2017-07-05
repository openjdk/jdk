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
package org.graalvm.compiler.lir.alloc.lsra;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRValueUtil;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

/**
 */
public class MoveResolver {

    private static final DebugCounter cycleBreakingSlotsAllocated = Debug.counter("LSRA[cycleBreakingSlotsAllocated]");

    private final LinearScan allocator;

    private int insertIdx;
    private LIRInsertionBuffer insertionBuffer; // buffer where moves are inserted

    private final List<Interval> mappingFrom;
    private final List<Constant> mappingFromOpr;
    private final List<Interval> mappingTo;
    private boolean multipleReadsAllowed;
    private final int[] registerBlocked;

    protected void setValueBlocked(Value location, int direction) {
        assert direction == 1 || direction == -1 : "out of bounds";
        if (isRegister(location)) {
            registerBlocked[asRegister(location).number] += direction;
        } else {
            throw GraalError.shouldNotReachHere("unhandled value " + location);
        }
    }

    protected Interval getMappingFrom(int i) {
        return mappingFrom.get(i);
    }

    protected int mappingFromSize() {
        return mappingFrom.size();
    }

    protected int valueBlocked(Value location) {
        if (isRegister(location)) {
            return registerBlocked[asRegister(location).number];
        }
        throw GraalError.shouldNotReachHere("unhandled value " + location);
    }

    void setMultipleReadsAllowed() {
        multipleReadsAllowed = true;
    }

    protected boolean areMultipleReadsAllowed() {
        return multipleReadsAllowed;
    }

    boolean hasMappings() {
        return mappingFrom.size() > 0;
    }

    protected LinearScan getAllocator() {
        return allocator;
    }

    protected MoveResolver(LinearScan allocator) {

        this.allocator = allocator;
        this.multipleReadsAllowed = false;
        this.mappingFrom = new ArrayList<>(8);
        this.mappingFromOpr = new ArrayList<>(8);
        this.mappingTo = new ArrayList<>(8);
        this.insertIdx = -1;
        this.insertionBuffer = new LIRInsertionBuffer();
        this.registerBlocked = new int[allocator.getRegisters().size()];
    }

    protected boolean checkEmpty() {
        assert mappingFrom.size() == 0 && mappingFromOpr.size() == 0 && mappingTo.size() == 0 : "list must be empty before and after processing";
        for (int i = 0; i < getAllocator().getRegisters().size(); i++) {
            assert registerBlocked[i] == 0 : "register map must be empty before and after processing";
        }
        checkMultipleReads();
        return true;
    }

    protected void checkMultipleReads() {
        assert !areMultipleReadsAllowed() : "must have default value";
    }

    private boolean verifyBeforeResolve() {
        assert mappingFrom.size() == mappingFromOpr.size() : "length must be equal";
        assert mappingFrom.size() == mappingTo.size() : "length must be equal";
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

        HashSet<Value> usedRegs = new HashSet<>();
        if (!areMultipleReadsAllowed()) {
            for (i = 0; i < mappingFrom.size(); i++) {
                Interval interval = mappingFrom.get(i);
                if (interval != null && !isIllegal(interval.location())) {
                    boolean unique = usedRegs.add(interval.location());
                    assert unique : "cannot read from same register twice";
                }
            }
        }

        usedRegs.clear();
        for (i = 0; i < mappingTo.size(); i++) {
            Interval interval = mappingTo.get(i);
            if (isIllegal(interval.location())) {
                // After insertion the location may become illegal, so don't check it since multiple
                // intervals might be illegal.
                continue;
            }
            boolean unique = usedRegs.add(interval.location());
            assert unique : "cannot write to same register twice";
        }

        verifyStackSlotMapping();

        return true;
    }

    protected void verifyStackSlotMapping() {
        HashSet<Value> usedRegs = new HashSet<>();
        for (int i = 0; i < mappingFrom.size(); i++) {
            Interval interval = mappingFrom.get(i);
            if (interval != null && !isRegister(interval.location())) {
                usedRegs.add(interval.location());
            }
        }
        for (int i = 0; i < mappingTo.size(); i++) {
            Interval interval = mappingTo.get(i);
            assert !usedRegs.contains(interval.location()) ||
                            checkIntervalLocation(mappingFrom.get(i), interval, mappingFromOpr.get(i)) : "stack slots used in mappingFrom must be disjoint to mappingTo";
        }
    }

    private static boolean checkIntervalLocation(Interval from, Interval to, Constant fromOpr) {
        if (from == null) {
            return fromOpr != null;
        } else {
            return to.location().equals(from.location());
        }
    }

    // mark assignedReg and assignedRegHi of the interval as blocked
    private void blockRegisters(Interval interval) {
        Value location = interval.location();
        if (mightBeBlocked(location)) {
            assert areMultipleReadsAllowed() || valueBlocked(location) == 0 : "location already marked as used: " + location;
            int direction = 1;
            setValueBlocked(location, direction);
            Debug.log("block %s", location);
        }
    }

    // mark assignedReg and assignedRegHi of the interval as unblocked
    private void unblockRegisters(Interval interval) {
        Value location = interval.location();
        if (mightBeBlocked(location)) {
            assert valueBlocked(location) > 0 : "location already marked as unused: " + location;
            setValueBlocked(location, -1);
            Debug.log("unblock %s", location);
        }
    }

    /**
     * Checks if the {@linkplain Interval#location() location} of {@code to} is not blocked or is
     * only blocked by {@code from}.
     */
    private boolean safeToProcessMove(Interval from, Interval to) {
        Value fromReg = from != null ? from.location() : null;

        Value location = to.location();
        if (mightBeBlocked(location)) {
            if ((valueBlocked(location) > 1 || (valueBlocked(location) == 1 && !isMoveToSelf(fromReg, location)))) {
                return false;
            }
        }

        return true;
    }

    protected boolean isMoveToSelf(Value from, Value to) {
        assert to != null;
        if (to.equals(from)) {
            return true;
        }
        if (from != null && isRegister(from) && isRegister(to) && asRegister(from).equals(asRegister(to))) {
            assert LIRKind.verifyMoveKinds(to.getValueKind(), from.getValueKind()) : String.format("Same register but Kind mismatch %s <- %s", to, from);
            return true;
        }
        return false;
    }

    protected boolean mightBeBlocked(Value location) {
        return isRegister(location);
    }

    private void createInsertionBuffer(List<LIRInstruction> list) {
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

    private void insertMove(Interval fromInterval, Interval toInterval) {
        assert !fromInterval.operand.equals(toInterval.operand) : "from and to interval equal: " + fromInterval;
        assert LIRKind.verifyMoveKinds(toInterval.kind(), fromInterval.kind()) : "move between different types";
        assert insertIdx != -1 : "must setup insert position first";

        insertionBuffer.append(insertIdx, createMove(fromInterval.operand, toInterval.operand, fromInterval.location(), toInterval.location()));

        if (Debug.isLogEnabled()) {
            Debug.log("insert move from %s to %s at %d", fromInterval, toInterval, insertIdx);
        }
    }

    /**
     * @param fromOpr {@link Interval#operand operand} of the {@code from} interval
     * @param toOpr {@link Interval#operand operand} of the {@code to} interval
     * @param fromLocation {@link Interval#location() location} of the {@code to} interval
     * @param toLocation {@link Interval#location() location} of the {@code to} interval
     */
    protected LIRInstruction createMove(AllocatableValue fromOpr, AllocatableValue toOpr, AllocatableValue fromLocation, AllocatableValue toLocation) {
        return getAllocator().getSpillMoveFactory().createMove(toOpr, fromOpr);
    }

    private void insertMove(Constant fromOpr, Interval toInterval) {
        assert insertIdx != -1 : "must setup insert position first";

        AllocatableValue toOpr = toInterval.operand;
        LIRInstruction move = getAllocator().getSpillMoveFactory().createLoad(toOpr, fromOpr);
        insertionBuffer.append(insertIdx, move);

        if (Debug.isLogEnabled()) {
            Debug.log("insert move from value %s to %s at %d", fromOpr, toInterval, insertIdx);
        }
    }

    @SuppressWarnings("try")
    private void resolveMappings() {
        try (Indent indent = Debug.logAndIndent("resolveMapping")) {
            assert verifyBeforeResolve();
            if (Debug.isLogEnabled()) {
                printMapping();
            }

            // Block all registers that are used as input operands of a move.
            // When a register is blocked, no move to this register is emitted.
            // This is necessary for detecting cycles in moves.
            int i;
            for (i = mappingFrom.size() - 1; i >= 0; i--) {
                Interval fromInterval = mappingFrom.get(i);
                if (fromInterval != null) {
                    blockRegisters(fromInterval);
                }
            }

            ArrayList<AllocatableValue> busySpillSlots = null;
            while (mappingFrom.size() > 0) {
                boolean processedInterval = false;

                int spillCandidate = -1;
                for (i = mappingFrom.size() - 1; i >= 0; i--) {
                    Interval fromInterval = mappingFrom.get(i);
                    Interval toInterval = mappingTo.get(i);

                    if (safeToProcessMove(fromInterval, toInterval)) {
                        // this interval can be processed because target is free
                        if (fromInterval != null) {
                            insertMove(fromInterval, toInterval);
                            unblockRegisters(fromInterval);
                        } else {
                            insertMove(mappingFromOpr.get(i), toInterval);
                        }
                        if (LIRValueUtil.isStackSlotValue(toInterval.location())) {
                            if (busySpillSlots == null) {
                                busySpillSlots = new ArrayList<>(2);
                            }
                            busySpillSlots.add(toInterval.location());
                        }
                        mappingFrom.remove(i);
                        mappingFromOpr.remove(i);
                        mappingTo.remove(i);

                        processedInterval = true;
                    } else if (fromInterval != null && isRegister(fromInterval.location()) &&
                                    (busySpillSlots == null || !busySpillSlots.contains(fromInterval.spillSlot()))) {
                        // this interval cannot be processed now because target is not free
                        // it starts in a register, so it is a possible candidate for spilling
                        spillCandidate = i;
                    }
                }

                if (!processedInterval) {
                    breakCycle(spillCandidate);
                }
            }
        }

        // reset to default value
        multipleReadsAllowed = false;

        // check that all intervals have been processed
        assert checkEmpty();
    }

    protected void breakCycle(int spillCandidate) {
        // no move could be processed because there is a cycle in the move list
        // (e.g. r1 . r2, r2 . r1), so one interval must be spilled to memory
        assert spillCandidate != -1 : "no interval in register for spilling found";

        // create a new spill interval and assign a stack slot to it
        Interval fromInterval = mappingFrom.get(spillCandidate);
        // do not allocate a new spill slot for temporary interval, but
        // use spill slot assigned to fromInterval. Otherwise moves from
        // one stack slot to another can happen (not allowed by LIRAssembler
        AllocatableValue spillSlot = fromInterval.spillSlot();
        if (spillSlot == null) {
            spillSlot = getAllocator().getFrameMapBuilder().allocateSpillSlot(fromInterval.kind());
            fromInterval.setSpillSlot(spillSlot);
            cycleBreakingSlotsAllocated.increment();
        }
        spillInterval(spillCandidate, fromInterval, spillSlot);
    }

    protected void spillInterval(int spillCandidate, Interval fromInterval, AllocatableValue spillSlot) {
        assert mappingFrom.get(spillCandidate).equals(fromInterval);
        Interval spillInterval = getAllocator().createDerivedInterval(fromInterval);
        spillInterval.setKind(fromInterval.kind());

        // add a dummy range because real position is difficult to calculate
        // Note: this range is a special case when the integrity of the allocation is
        // checked
        spillInterval.addRange(1, 2);

        spillInterval.assignLocation(spillSlot);

        if (Debug.isLogEnabled()) {
            Debug.log("created new Interval for spilling: %s", spillInterval);
        }
        blockRegisters(spillInterval);

        // insert a move from register to stack and update the mapping
        insertMove(fromInterval, spillInterval);
        mappingFrom.set(spillCandidate, spillInterval);
        unblockRegisters(fromInterval);
    }

    @SuppressWarnings("try")
    private void printMapping() {
        try (Indent indent = Debug.logAndIndent("Mapping")) {
            for (int i = mappingFrom.size() - 1; i >= 0; i--) {
                Interval fromInterval = mappingFrom.get(i);
                Interval toInterval = mappingTo.get(i);
                String from;
                Value to = toInterval.location();
                if (fromInterval == null) {
                    from = mappingFromOpr.get(i).toString();
                } else {
                    from = fromInterval.location().toString();
                }
                Debug.log("move %s <- %s", from, to);
            }
        }
    }

    void setInsertPosition(List<LIRInstruction> insertList, int insertIdx) {
        assert this.insertIdx == -1 : "use moveInsertPosition instead of setInsertPosition when data already set";

        createInsertionBuffer(insertList);
        this.insertIdx = insertIdx;
    }

    void moveInsertPosition(List<LIRInstruction> newInsertList, int newInsertIdx) {
        if (insertionBuffer.lirList() != null && (insertionBuffer.lirList() != newInsertList || this.insertIdx != newInsertIdx)) {
            // insert position changed . resolve current mappings
            resolveMappings();
        }

        if (insertionBuffer.lirList() != newInsertList) {
            // block changed . append insertionBuffer because it is
            // bound to a specific block and create a new insertionBuffer
            appendInsertionBuffer();
            createInsertionBuffer(newInsertList);
        }

        this.insertIdx = newInsertIdx;
    }

    public void addMapping(Interval fromInterval, Interval toInterval) {

        if (isIllegal(toInterval.location()) && toInterval.canMaterialize()) {
            if (Debug.isLogEnabled()) {
                Debug.log("no store to rematerializable interval %s needed", toInterval);
            }
            return;
        }
        if (isIllegal(fromInterval.location()) && fromInterval.canMaterialize()) {
            // Instead of a reload, re-materialize the value
            Constant rematValue = fromInterval.getMaterializedValue();
            addMapping(rematValue, toInterval);
            return;
        }
        if (Debug.isLogEnabled()) {
            Debug.log("add move mapping from %s to %s", fromInterval, toInterval);
        }

        assert !fromInterval.operand.equals(toInterval.operand) : "from and to interval equal: " + fromInterval;
        assert LIRKind.verifyMoveKinds(toInterval.kind(), fromInterval.kind()) : String.format("Kind mismatch: %s vs. %s, from=%s, to=%s", fromInterval.kind(), toInterval.kind(), fromInterval,
                        toInterval);
        mappingFrom.add(fromInterval);
        mappingFromOpr.add(null);
        mappingTo.add(toInterval);
    }

    public void addMapping(Constant fromOpr, Interval toInterval) {
        if (Debug.isLogEnabled()) {
            Debug.log("add move mapping from %s to %s", fromOpr, toInterval);
        }

        mappingFrom.add(null);
        mappingFromOpr.add(fromOpr);
        mappingTo.add(toInterval);
    }

    void resolveAndAppendMoves() {
        if (hasMappings()) {
            resolveMappings();
        }
        appendInsertionBuffer();
    }
}
