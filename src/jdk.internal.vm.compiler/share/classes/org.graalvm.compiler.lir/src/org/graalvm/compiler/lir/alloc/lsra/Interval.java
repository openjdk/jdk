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

import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.util.IntList;
import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Variable;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Represents an interval in the {@linkplain LinearScan linear scan register allocator}.
 */
public final class Interval {

    /**
     * A set of interval lists, one per {@linkplain RegisterBinding binding} type.
     */
    static final class RegisterBindingLists {

        /**
         * List of intervals whose binding is currently {@link RegisterBinding#Fixed}.
         */
        public Interval fixed;

        /**
         * List of intervals whose binding is currently {@link RegisterBinding#Any}.
         */
        public Interval any;

        /**
         * List of intervals whose binding is currently {@link RegisterBinding#Stack}.
         */
        public Interval stack;

        RegisterBindingLists(Interval fixed, Interval any, Interval stack) {
            this.fixed = fixed;
            this.any = any;
            this.stack = stack;
        }

        /**
         * Gets the list for a specified binding.
         *
         * @param binding specifies the list to be returned
         * @return the list of intervals whose binding is {@code binding}
         */
        public Interval get(RegisterBinding binding) {
            switch (binding) {
                case Any:
                    return any;
                case Fixed:
                    return fixed;
                case Stack:
                    return stack;
            }
            throw GraalError.shouldNotReachHere();
        }

        /**
         * Sets the list for a specified binding.
         *
         * @param binding specifies the list to be replaced
         * @param list a list of intervals whose binding is {@code binding}
         */
        public void set(RegisterBinding binding, Interval list) {
            assert list != null;
            switch (binding) {
                case Any:
                    any = list;
                    break;
                case Fixed:
                    fixed = list;
                    break;
                case Stack:
                    stack = list;
                    break;
            }
        }

        /**
         * Adds an interval to a list sorted by {@linkplain Interval#currentFrom() current from}
         * positions.
         *
         * @param binding specifies the list to be updated
         * @param interval the interval to add
         */
        public void addToListSortedByCurrentFromPositions(RegisterBinding binding, Interval interval) {
            Interval list = get(binding);
            Interval prev = null;
            Interval cur = list;
            while (cur.currentFrom() < interval.currentFrom()) {
                prev = cur;
                cur = cur.next;
            }
            Interval result = list;
            if (prev == null) {
                // add to head of list
                result = interval;
            } else {
                // add before 'cur'
                prev.next = interval;
            }
            interval.next = cur;
            set(binding, result);
        }

        /**
         * Adds an interval to a list sorted by {@linkplain Interval#from() start} positions and
         * {@linkplain Interval#firstUsage(RegisterPriority) first usage} positions.
         *
         * @param binding specifies the list to be updated
         * @param interval the interval to add
         */
        public void addToListSortedByStartAndUsePositions(RegisterBinding binding, Interval interval) {
            Interval list = get(binding);
            Interval prev = null;
            Interval cur = list;
            while (cur.from() < interval.from() || (cur.from() == interval.from() && cur.firstUsage(RegisterPriority.None) < interval.firstUsage(RegisterPriority.None))) {
                prev = cur;
                cur = cur.next;
            }
            if (prev == null) {
                list = interval;
            } else {
                prev.next = interval;
            }
            interval.next = cur;
            set(binding, list);
        }

        /**
         * Removes an interval from a list.
         *
         * @param binding specifies the list to be updated
         * @param i the interval to remove
         */
        public void remove(RegisterBinding binding, Interval i) {
            Interval list = get(binding);
            Interval prev = null;
            Interval cur = list;
            while (cur != i) {
                assert cur != null && !cur.isEndMarker() : "interval has not been found in list: " + i;
                prev = cur;
                cur = cur.next;
            }
            if (prev == null) {
                set(binding, cur.next);
            } else {
                prev.next = cur.next;
            }
        }
    }

    /**
     * Constants denoting the register usage priority for an interval. The constants are declared in
     * increasing order of priority are are used to optimize spilling when multiple overlapping
     * intervals compete for limited registers.
     */
    public enum RegisterPriority {
        /**
         * No special reason for an interval to be allocated a register.
         */
        None,

        /**
         * Priority level for intervals live at the end of a loop.
         */
        LiveAtLoopEnd,

        /**
         * Priority level for intervals that should be allocated to a register.
         */
        ShouldHaveRegister,

        /**
         * Priority level for intervals that must be allocated to a register.
         */
        MustHaveRegister;

        public static final RegisterPriority[] VALUES = values();

        /**
         * Determines if this priority is higher than or equal to a given priority.
         */
        public boolean greaterEqual(RegisterPriority other) {
            return ordinal() >= other.ordinal();
        }

        /**
         * Determines if this priority is lower than a given priority.
         */
        public boolean lessThan(RegisterPriority other) {
            return ordinal() < other.ordinal();
        }
    }

    /**
     * Constants denoting whether an interval is bound to a specific register. This models platform
     * dependencies on register usage for certain instructions.
     */
    enum RegisterBinding {
        /**
         * Interval is bound to a specific register as required by the platform.
         */
        Fixed,

        /**
         * Interval has no specific register requirements.
         */
        Any,

        /**
         * Interval is bound to a stack slot.
         */
        Stack;

        public static final RegisterBinding[] VALUES = values();
    }

    /**
     * Constants denoting the linear-scan states an interval may be in with respect to the
     * {@linkplain Interval#from() start} {@code position} of the interval being processed.
     */
    enum State {
        /**
         * An interval that starts after {@code position}.
         */
        Unhandled,

        /**
         * An interval that {@linkplain Interval#covers covers} {@code position} and has an assigned
         * register.
         */
        Active,

        /**
         * An interval that starts before and ends after {@code position} but does not
         * {@linkplain Interval#covers cover} it due to a lifetime hole.
         */
        Inactive,

        /**
         * An interval that ends before {@code position} or is spilled to memory.
         */
        Handled;
    }

    /**
     * Constants used in optimization of spilling of an interval.
     */
    public enum SpillState {
        /**
         * Starting state of calculation: no definition found yet.
         */
        NoDefinitionFound,

        /**
         * One definition has already been found. Two consecutive definitions are treated as one
         * (e.g. a consecutive move and add because of two-operand LIR form). The position of this
         * definition is given by {@link Interval#spillDefinitionPos()}.
         */
        NoSpillStore,

        /**
         * One spill move has already been inserted.
         */
        OneSpillStore,

        /**
         * The interval is spilled multiple times or is spilled in a loop. Place the store somewhere
         * on the dominator path between the definition and the usages.
         */
        SpillInDominator,

        /**
         * The interval should be stored immediately after its definition to prevent multiple
         * redundant stores.
         */
        StoreAtDefinition,

        /**
         * The interval starts in memory (e.g. method parameter), so a store is never necessary.
         */
        StartInMemory,

        /**
         * The interval has more than one definition (e.g. resulting from phi moves), so stores to
         * memory are not optimized.
         */
        NoOptimization;

        public static final EnumSet<SpillState> ALWAYS_IN_MEMORY = EnumSet.of(SpillInDominator, StoreAtDefinition, StartInMemory);
    }

    /**
     * List of use positions. Each entry in the list records the use position and register priority
     * associated with the use position. The entries in the list are in descending order of use
     * position.
     *
     */
    public static final class UsePosList {

        private IntList list;

        /**
         * Creates a use list.
         *
         * @param initialCapacity the initial capacity of the list in terms of entries
         */
        public UsePosList(int initialCapacity) {
            list = new IntList(initialCapacity * 2);
        }

        private UsePosList(IntList list) {
            this.list = list;
        }

        /**
         * Splits this list around a given position. All entries in this list with a use position
         * greater or equal than {@code splitPos} are removed from this list and added to the
         * returned list.
         *
         * @param splitPos the position for the split
         * @return a use position list containing all entries removed from this list that have a use
         *         position greater or equal than {@code splitPos}
         */
        public UsePosList splitAt(int splitPos) {
            int i = size() - 1;
            int len = 0;
            while (i >= 0 && usePos(i) < splitPos) {
                --i;
                len += 2;
            }
            int listSplitIndex = (i + 1) * 2;
            IntList childList = list;
            list = IntList.copy(this.list, listSplitIndex, len);
            childList.setSize(listSplitIndex);
            UsePosList child = new UsePosList(childList);
            return child;
        }

        /**
         * Gets the use position at a specified index in this list.
         *
         * @param index the index of the entry for which the use position is returned
         * @return the use position of entry {@code index} in this list
         */
        public int usePos(int index) {
            return list.get(index << 1);
        }

        /**
         * Gets the register priority for the use position at a specified index in this list.
         *
         * @param index the index of the entry for which the register priority is returned
         * @return the register priority of entry {@code index} in this list
         */
        public RegisterPriority registerPriority(int index) {
            return RegisterPriority.VALUES[list.get((index << 1) + 1)];
        }

        public void add(int usePos, RegisterPriority registerPriority) {
            assert list.size() == 0 || usePos(size() - 1) > usePos;
            list.add(usePos);
            list.add(registerPriority.ordinal());
        }

        public int size() {
            return list.size() >> 1;
        }

        public void removeLowestUsePos() {
            list.setSize(list.size() - 2);
        }

        public void setRegisterPriority(int index, RegisterPriority registerPriority) {
            list.set((index << 1) + 1, registerPriority.ordinal());
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("[");
            for (int i = size() - 1; i >= 0; --i) {
                if (buf.length() != 1) {
                    buf.append(", ");
                }
                RegisterPriority prio = registerPriority(i);
                buf.append(usePos(i)).append(" -> ").append(prio.ordinal()).append(':').append(prio);
            }
            return buf.append("]").toString();
        }
    }

    protected static final int END_MARKER_OPERAND_NUMBER = Integer.MIN_VALUE;

    /**
     * The {@linkplain RegisterValue register} or {@linkplain Variable variable} for this interval
     * prior to register allocation.
     */
    public final AllocatableValue operand;

    /**
     * The operand number for this interval's {@linkplain #operand operand}.
     */
    public final int operandNumber;

    /**
     * The {@linkplain RegisterValue register} or {@linkplain StackSlot spill slot} assigned to this
     * interval. In case of a spilled interval which is re-materialized this is
     * {@link Value#ILLEGAL}.
     */
    private AllocatableValue location;

    /**
     * The stack slot to which all splits of this interval are spilled if necessary.
     */
    private AllocatableValue spillSlot;

    /**
     * The kind of this interval.
     */
    private ValueKind<?> kind;

    /**
     * The head of the list of ranges describing this interval. This list is sorted by
     * {@linkplain LIRInstruction#id instruction ids}.
     */
    private Range first;

    /**
     * List of (use-positions, register-priorities) pairs, sorted by use-positions.
     */
    private UsePosList usePosList;

    /**
     * Iterator used to traverse the ranges of an interval.
     */
    private Range current;

    /**
     * Link to next interval in a sorted list of intervals that ends with
     * LinearScan.intervalEndMarker.
     */
    Interval next;

    /**
     * The linear-scan state of this interval.
     */
    State state;

    private int cachedTo; // cached value: to of last range (-1: not cached)

    /**
     * The interval from which this one is derived. If this is a {@linkplain #isSplitParent() split
     * parent}, it points to itself.
     */
    private Interval splitParent;

    /**
     * List of all intervals that are split off from this interval. This is only used if this is a
     * {@linkplain #isSplitParent() split parent}.
     */
    private List<Interval> splitChildren = Collections.emptyList();

    /**
     * Current split child that has been active or inactive last (always stored in split parents).
     */
    private Interval currentSplitChild;

    /**
     * Specifies if move is inserted between currentSplitChild and this interval when interval gets
     * active the first time.
     */
    private boolean insertMoveWhenActivated;

    /**
     * For spill move optimization.
     */
    private SpillState spillState;

    /**
     * Position where this interval is defined (if defined only once).
     */
    private int spillDefinitionPos;

    /**
     * This interval should be assigned the same location as the hint interval.
     */
    private Interval locationHint;

    /**
     * The value with which a spilled child interval can be re-materialized. Currently this must be
     * a Constant.
     */
    private Constant materializedValue;

    /**
     * The number of times {@link #addMaterializationValue(Constant)} is called.
     */
    private int numMaterializationValuesAdded;

    void assignLocation(AllocatableValue newLocation) {
        if (isRegister(newLocation)) {
            assert this.location == null : "cannot re-assign location for " + this;
            if (newLocation.getValueKind().equals(LIRKind.Illegal) && !kind.equals(LIRKind.Illegal)) {
                this.location = asRegister(newLocation).asValue(kind);
                return;
            }
        } else if (isIllegal(newLocation)) {
            assert canMaterialize();
        } else {
            assert this.location == null || isRegister(this.location) || (isVirtualStackSlot(this.location) && isStackSlot(newLocation)) : "cannot re-assign location for " + this;
            assert isStackSlotValue(newLocation);
            assert !newLocation.getValueKind().equals(LIRKind.Illegal);
            assert newLocation.getValueKind().equals(this.kind);
        }
        this.location = newLocation;
    }

    /** Returns true is this is the sentinel interval that denotes the end of an interval list. */
    public boolean isEndMarker() {
        return operandNumber == END_MARKER_OPERAND_NUMBER;
    }

    /**
     * Gets the {@linkplain RegisterValue register} or {@linkplain StackSlot spill slot} assigned to
     * this interval.
     */
    public AllocatableValue location() {
        return location;
    }

    public ValueKind<?> kind() {
        assert !isRegister(operand) : "cannot access type for fixed interval";
        return kind;
    }

    public void setKind(ValueKind<?> kind) {
        assert isRegister(operand) || this.kind().equals(LIRKind.Illegal) || this.kind().equals(kind) : "overwriting existing type";
        this.kind = kind;
    }

    public Range first() {
        return first;
    }

    public int from() {
        return first.from;
    }

    int to() {
        if (cachedTo == -1) {
            cachedTo = calcTo();
        }
        assert cachedTo == calcTo() : "invalid cached value";
        return cachedTo;
    }

    int numUsePositions() {
        return usePosList.size();
    }

    public void setLocationHint(Interval interval) {
        locationHint = interval;
    }

    public boolean isSplitParent() {
        return splitParent == this;
    }

    boolean isSplitChild() {
        return splitParent != this;
    }

    /**
     * Gets the split parent for this interval.
     */
    public Interval splitParent() {
        assert splitParent.isSplitParent() : "not a split parent: " + this;
        return splitParent;
    }

    /**
     * Gets the canonical spill slot for this interval.
     */
    public AllocatableValue spillSlot() {
        return splitParent().spillSlot;
    }

    public void setSpillSlot(AllocatableValue slot) {
        assert isStackSlotValue(slot);
        assert splitParent().spillSlot == null || (isVirtualStackSlot(splitParent().spillSlot) && isStackSlot(slot)) : "connot overwrite existing spill slot";
        splitParent().spillSlot = slot;
    }

    Interval currentSplitChild() {
        return splitParent().currentSplitChild;
    }

    void makeCurrentSplitChild() {
        splitParent().currentSplitChild = this;
    }

    boolean insertMoveWhenActivated() {
        return insertMoveWhenActivated;
    }

    void setInsertMoveWhenActivated(boolean b) {
        insertMoveWhenActivated = b;
    }

    // for spill optimization
    public SpillState spillState() {
        return splitParent().spillState;
    }

    public int spillDefinitionPos() {
        return splitParent().spillDefinitionPos;
    }

    public void setSpillState(SpillState state) {
        assert state.ordinal() >= spillState().ordinal() : "state cannot decrease";
        splitParent().spillState = state;
    }

    public void setSpillDefinitionPos(int pos) {
        assert spillState() == SpillState.SpillInDominator || spillState() == SpillState.NoDefinitionFound || spillDefinitionPos() == -1 : "cannot set the position twice";
        splitParent().spillDefinitionPos = pos;
    }

    // returns true if this interval has a shadow copy on the stack that is always correct
    public boolean alwaysInMemory() {
        return SpillState.ALWAYS_IN_MEMORY.contains(spillState()) && !canMaterialize();
    }

    void removeFirstUsePos() {
        usePosList.removeLowestUsePos();
    }

    // test intersection
    boolean intersects(Interval i) {
        return first.intersects(i.first);
    }

    int intersectsAt(Interval i) {
        return first.intersectsAt(i.first);
    }

    // range iteration
    void rewindRange() {
        current = first;
    }

    void nextRange() {
        assert !this.isEndMarker() : "not allowed on sentinel";
        current = current.next;
    }

    int currentFrom() {
        return current.from;
    }

    int currentTo() {
        return current.to;
    }

    boolean currentAtEnd() {
        return current.isEndMarker();
    }

    boolean currentIntersects(Interval it) {
        return current.intersects(it.current);
    }

    int currentIntersectsAt(Interval it) {
        return current.intersectsAt(it.current);
    }

    Interval(AllocatableValue operand, int operandNumber, Interval intervalEndMarker, Range rangeEndMarker) {
        assert operand != null;
        this.operand = operand;
        this.operandNumber = operandNumber;
        if (isRegister(operand)) {
            location = operand;
        } else {
            assert isIllegal(operand) || isVariable(operand);
        }
        this.kind = LIRKind.Illegal;
        this.first = rangeEndMarker;
        this.usePosList = new UsePosList(4);
        this.current = rangeEndMarker;
        this.next = intervalEndMarker;
        this.cachedTo = -1;
        this.spillState = SpillState.NoDefinitionFound;
        this.spillDefinitionPos = -1;
        splitParent = this;
        currentSplitChild = this;
    }

    /**
     * Sets the value which is used for re-materialization.
     */
    public void addMaterializationValue(Constant value) {
        if (numMaterializationValuesAdded == 0) {
            materializedValue = value;
        } else {
            // Interval is defined on multiple places -> no materialization is possible.
            materializedValue = null;
        }
        numMaterializationValuesAdded++;
    }

    /**
     * Returns true if this interval can be re-materialized when spilled. This means that no
     * spill-moves are needed. Instead of restore-moves the {@link #materializedValue} is restored.
     */
    public boolean canMaterialize() {
        return getMaterializedValue() != null;
    }

    /**
     * Returns a value which can be moved to a register instead of a restore-move from stack.
     */
    public Constant getMaterializedValue() {
        return splitParent().materializedValue;
    }

    int calcTo() {
        assert !first.isEndMarker() : "interval has no range";

        Range r = first;
        while (!r.next.isEndMarker()) {
            r = r.next;
        }
        return r.to;
    }

    // consistency check of split-children
    boolean checkSplitChildren() {
        if (!splitChildren.isEmpty()) {
            assert isSplitParent() : "only split parents can have children";

            for (int i = 0; i < splitChildren.size(); i++) {
                Interval i1 = splitChildren.get(i);

                assert i1.splitParent() == this : "not a split child of this interval";
                assert i1.kind().equals(kind()) : "must be equal for all split children";
                assert (i1.spillSlot() == null && spillSlot == null) || i1.spillSlot().equals(spillSlot()) : "must be equal for all split children";

                for (int j = i + 1; j < splitChildren.size(); j++) {
                    Interval i2 = splitChildren.get(j);

                    assert !i1.operand.equals(i2.operand) : "same register number";

                    if (i1.from() < i2.from()) {
                        assert i1.to() <= i2.from() && i1.to() < i2.to() : "intervals overlapping";
                    } else {
                        assert i2.from() < i1.from() : "intervals start at same opId";
                        assert i2.to() <= i1.from() && i2.to() < i1.to() : "intervals overlapping";
                    }
                }
            }
        }

        return true;
    }

    public Interval locationHint(boolean searchSplitChild) {
        if (!searchSplitChild) {
            return locationHint;
        }

        if (locationHint != null) {
            assert locationHint.isSplitParent() : "ony split parents are valid hint registers";

            if (locationHint.location != null && isRegister(locationHint.location)) {
                return locationHint;
            } else if (!locationHint.splitChildren.isEmpty()) {
                // search the first split child that has a register assigned
                int len = locationHint.splitChildren.size();
                for (int i = 0; i < len; i++) {
                    Interval interval = locationHint.splitChildren.get(i);
                    if (interval.location != null && isRegister(interval.location)) {
                        return interval;
                    }
                }
            }
        }

        // no hint interval found that has a register assigned
        return null;
    }

    Interval getSplitChildAtOpId(int opId, LIRInstruction.OperandMode mode, LinearScan allocator) {
        assert isSplitParent() : "can only be called for split parents";
        assert opId >= 0 : "invalid opId (method cannot be called for spill moves)";

        if (splitChildren.isEmpty()) {
            assert this.covers(opId, mode) : this + " does not cover " + opId;
            return this;
        } else {
            Interval result = null;
            int len = splitChildren.size();

            // in outputMode, the end of the interval (opId == cur.to()) is not valid
            int toOffset = (mode == LIRInstruction.OperandMode.DEF ? 0 : 1);

            int i;
            for (i = 0; i < len; i++) {
                Interval cur = splitChildren.get(i);
                if (cur.from() <= opId && opId < cur.to() + toOffset) {
                    if (i > 0) {
                        // exchange current split child to start of list (faster access for next
                        // call)
                        Util.atPutGrow(splitChildren, i, splitChildren.get(0), null);
                        Util.atPutGrow(splitChildren, 0, cur, null);
                    }

                    // interval found
                    result = cur;
                    break;
                }
            }

            assert checkSplitChild(result, opId, allocator, toOffset, mode);
            return result;
        }
    }

    private boolean checkSplitChild(Interval result, int opId, LinearScan allocator, int toOffset, LIRInstruction.OperandMode mode) {
        if (result == null) {
            // this is an error
            StringBuilder msg = new StringBuilder(this.toString()).append(" has no child at ").append(opId);
            if (!splitChildren.isEmpty()) {
                Interval firstChild = splitChildren.get(0);
                Interval lastChild = splitChildren.get(splitChildren.size() - 1);
                msg.append(" (first = ").append(firstChild).append(", last = ").append(lastChild).append(")");
            }
            throw new GraalError("Linear Scan Error: %s", msg);
        }

        if (!splitChildren.isEmpty()) {
            for (Interval interval : splitChildren) {
                if (interval != result && interval.from() <= opId && opId < interval.to() + toOffset) {
                    /*
                     * Should not happen: Try another compilation as it is very unlikely to happen
                     * again.
                     */
                    throw new GraalError("two valid result intervals found for opId %d: %d and %d\n%s\n", opId, result.operandNumber, interval.operandNumber,
                                    result.logString(allocator), interval.logString(allocator));
                }
            }
        }
        assert result.covers(opId, mode) : "opId not covered by interval";
        return true;
    }

    // returns the interval that covers the given opId or null if there is none
    Interval getIntervalCoveringOpId(int opId) {
        assert opId >= 0 : "invalid opId";
        assert opId < to() : "can only look into the past";

        if (opId >= from()) {
            return this;
        }

        Interval parent = splitParent();
        Interval result = null;

        assert !parent.splitChildren.isEmpty() : "no split children available";
        int len = parent.splitChildren.size();

        for (int i = len - 1; i >= 0; i--) {
            Interval cur = parent.splitChildren.get(i);
            if (cur.from() <= opId && opId < cur.to()) {
                assert result == null : "covered by multiple split children " + result + " and " + cur;
                result = cur;
            }
        }

        return result;
    }

    // returns the last split child that ends before the given opId
    Interval getSplitChildBeforeOpId(int opId) {
        assert opId >= 0 : "invalid opId";

        Interval parent = splitParent();
        Interval result = null;

        assert !parent.splitChildren.isEmpty() : "no split children available";
        int len = parent.splitChildren.size();

        for (int i = len - 1; i >= 0; i--) {
            Interval cur = parent.splitChildren.get(i);
            if (cur.to() <= opId && (result == null || result.to() < cur.to())) {
                result = cur;
            }
        }

        assert result != null : "no split child found";
        return result;
    }

    // checks if opId is covered by any split child
    boolean splitChildCovers(int opId, LIRInstruction.OperandMode mode) {
        assert isSplitParent() : "can only be called for split parents";
        assert opId >= 0 : "invalid opId (method can not be called for spill moves)";

        if (splitChildren.isEmpty()) {
            // simple case if interval was not split
            return covers(opId, mode);

        } else {
            // extended case: check all split children
            int len = splitChildren.size();
            for (int i = 0; i < len; i++) {
                Interval cur = splitChildren.get(i);
                if (cur.covers(opId, mode)) {
                    return true;
                }
            }
            return false;
        }
    }

    private RegisterPriority adaptPriority(RegisterPriority priority) {
        /*
         * In case of re-materialized values we require that use-operands are registers, because we
         * don't have the value in a stack location. (Note that ShouldHaveRegister means that the
         * operand can also be a StackSlot).
         */
        if (priority == RegisterPriority.ShouldHaveRegister && canMaterialize()) {
            return RegisterPriority.MustHaveRegister;
        }
        return priority;
    }

    // Note: use positions are sorted descending . first use has highest index
    int firstUsage(RegisterPriority minRegisterPriority) {
        assert isVariable(operand) : "cannot access use positions for fixed intervals";

        for (int i = usePosList.size() - 1; i >= 0; --i) {
            RegisterPriority registerPriority = adaptPriority(usePosList.registerPriority(i));
            if (registerPriority.greaterEqual(minRegisterPriority)) {
                return usePosList.usePos(i);
            }
        }
        return Integer.MAX_VALUE;
    }

    int nextUsage(RegisterPriority minRegisterPriority, int from) {
        assert isVariable(operand) : "cannot access use positions for fixed intervals";

        for (int i = usePosList.size() - 1; i >= 0; --i) {
            int usePos = usePosList.usePos(i);
            if (usePos >= from && adaptPriority(usePosList.registerPriority(i)).greaterEqual(minRegisterPriority)) {
                return usePos;
            }
        }
        return Integer.MAX_VALUE;
    }

    int nextUsageExact(RegisterPriority exactRegisterPriority, int from) {
        assert isVariable(operand) : "cannot access use positions for fixed intervals";

        for (int i = usePosList.size() - 1; i >= 0; --i) {
            int usePos = usePosList.usePos(i);
            if (usePos >= from && adaptPriority(usePosList.registerPriority(i)) == exactRegisterPriority) {
                return usePos;
            }
        }
        return Integer.MAX_VALUE;
    }

    int previousUsage(RegisterPriority minRegisterPriority, int from) {
        assert isVariable(operand) : "cannot access use positions for fixed intervals";

        int prev = -1;
        for (int i = usePosList.size() - 1; i >= 0; --i) {
            int usePos = usePosList.usePos(i);
            if (usePos > from) {
                return prev;
            }
            if (adaptPriority(usePosList.registerPriority(i)).greaterEqual(minRegisterPriority)) {
                prev = usePos;
            }
        }
        return prev;
    }

    public void addUsePos(int pos, RegisterPriority registerPriority, boolean detailedAsserts) {
        assert covers(pos, LIRInstruction.OperandMode.USE) : String.format("use position %d not covered by live range of interval %s", pos, this);

        // do not add use positions for precolored intervals because they are never used
        if (registerPriority != RegisterPriority.None && isVariable(operand)) {
            if (detailedAsserts) {
                for (int i = 0; i < usePosList.size(); i++) {
                    assert pos <= usePosList.usePos(i) : "already added a use-position with lower position";
                    if (i > 0) {
                        assert usePosList.usePos(i) < usePosList.usePos(i - 1) : "not sorted descending";
                    }
                }
            }

            // Note: addUse is called in descending order, so list gets sorted
            // automatically by just appending new use positions
            int len = usePosList.size();
            if (len == 0 || usePosList.usePos(len - 1) > pos) {
                usePosList.add(pos, registerPriority);
            } else if (usePosList.registerPriority(len - 1).lessThan(registerPriority)) {
                assert usePosList.usePos(len - 1) == pos : "list not sorted correctly";
                usePosList.setRegisterPriority(len - 1, registerPriority);
            }
        }
    }

    public void addRange(int from, int to) {
        assert from < to : "invalid range";
        assert first().isEndMarker() || to < first().next.from : "not inserting at begin of interval";
        assert from <= first().to : "not inserting at begin of interval";

        if (first.from <= to) {
            assert !first.isEndMarker();
            // join intersecting ranges
            first.from = Math.min(from, first().from);
            first.to = Math.max(to, first().to);
        } else {
            // insert new range
            first = new Range(from, to, first());
        }
    }

    Interval newSplitChild(LinearScan allocator) {
        // allocate new interval
        Interval parent = splitParent();
        Interval result = allocator.createDerivedInterval(parent);
        result.setKind(kind());

        result.splitParent = parent;
        result.setLocationHint(parent);

        // insert new interval in children-list of parent
        if (parent.splitChildren.isEmpty()) {
            assert isSplitParent() : "list must be initialized at first split";

            // Create new non-shared list
            parent.splitChildren = new ArrayList<>(4);
            parent.splitChildren.add(this);
        }
        parent.splitChildren.add(result);

        return result;
    }

    /**
     * Splits this interval at a specified position and returns the remainder as a new <i>child</i>
     * interval of this interval's {@linkplain #splitParent() parent} interval.
     * <p>
     * When an interval is split, a bi-directional link is established between the original
     * <i>parent</i> interval and the <i>children</i> intervals that are split off this interval.
     * When a split child is split again, the new created interval is a direct child of the original
     * parent. That is, there is no tree of split children stored, just a flat list. All split
     * children are spilled to the same {@linkplain #spillSlot spill slot}.
     *
     * @param splitPos the position at which to split this interval
     * @param allocator the register allocator context
     * @return the child interval split off from this interval
     */
    Interval split(int splitPos, LinearScan allocator) {
        assert isVariable(operand) : "cannot split fixed intervals";

        // allocate new interval
        Interval result = newSplitChild(allocator);

        // split the ranges
        Range prev = null;
        Range cur = first;
        while (!cur.isEndMarker() && cur.to <= splitPos) {
            prev = cur;
            cur = cur.next;
        }
        assert !cur.isEndMarker() : "split interval after end of last range";

        if (cur.from < splitPos) {
            result.first = new Range(splitPos, cur.to, cur.next);
            cur.to = splitPos;
            cur.next = allocator.rangeEndMarker;

        } else {
            assert prev != null : "split before start of first range";
            result.first = cur;
            prev.next = allocator.rangeEndMarker;
        }
        result.current = result.first;
        cachedTo = -1; // clear cached value

        // split list of use positions
        result.usePosList = usePosList.splitAt(splitPos);

        if (Assertions.detailedAssertionsEnabled(allocator.getOptions())) {
            for (int i = 0; i < usePosList.size(); i++) {
                assert usePosList.usePos(i) < splitPos;
            }
            for (int i = 0; i < result.usePosList.size(); i++) {
                assert result.usePosList.usePos(i) >= splitPos;
            }
        }
        return result;
    }

    /**
     * Splits this interval at a specified position and returns the head as a new interval (this
     * interval is the tail).
     *
     * Currently, only the first range can be split, and the new interval must not have split
     * positions
     */
    Interval splitFromStart(int splitPos, LinearScan allocator) {
        assert isVariable(operand) : "cannot split fixed intervals";
        assert splitPos > from() && splitPos < to() : "can only split inside interval";
        assert splitPos > first.from && splitPos <= first.to : "can only split inside first range";
        assert firstUsage(RegisterPriority.None) > splitPos : "can not split when use positions are present";

        // allocate new interval
        Interval result = newSplitChild(allocator);

        // the new interval has only one range (checked by assertion above,
        // so the splitting of the ranges is very simple
        result.addRange(first.from, splitPos);

        if (splitPos == first.to) {
            assert !first.next.isEndMarker() : "must not be at end";
            first = first.next;
        } else {
            first.from = splitPos;
        }

        return result;
    }

    // returns true if the opId is inside the interval
    boolean covers(int opId, LIRInstruction.OperandMode mode) {
        Range cur = first;

        while (!cur.isEndMarker() && cur.to < opId) {
            cur = cur.next;
        }
        if (!cur.isEndMarker()) {
            assert cur.to != cur.next.from : "ranges not separated";

            if (mode == LIRInstruction.OperandMode.DEF) {
                return cur.from <= opId && opId < cur.to;
            } else {
                return cur.from <= opId && opId <= cur.to;
            }
        }
        return false;
    }

    // returns true if the interval has any hole between holeFrom and holeTo
    // (even if the hole has only the length 1)
    boolean hasHoleBetween(int holeFrom, int holeTo) {
        assert holeFrom < holeTo : "check";
        assert from() <= holeFrom && holeTo <= to() : "index out of interval";

        Range cur = first;
        while (!cur.isEndMarker()) {
            assert cur.to < cur.next.from : "no space between ranges";

            // hole-range starts before this range . hole
            if (holeFrom < cur.from) {
                return true;

                // hole-range completely inside this range . no hole
            } else {
                if (holeTo <= cur.to) {
                    return false;

                    // overlapping of hole-range with this range . hole
                } else {
                    if (holeFrom <= cur.to) {
                        return true;
                    }
                }
            }

            cur = cur.next;
        }

        return false;
    }

    @Override
    public String toString() {
        String from = "?";
        String to = "?";
        if (first != null && !first.isEndMarker()) {
            from = String.valueOf(from());
            // to() may cache a computed value, modifying the current object, which is a bad idea
            // for a printing function. Compute it directly instead.
            to = String.valueOf(calcTo());
        }
        String locationString = this.location == null ? "" : "@" + this.location;
        return operandNumber + ":" + operand + (isRegister(operand) ? "" : locationString) + "[" + from + "," + to + "]";
    }

    /**
     * Gets the use position information for this interval.
     */
    public UsePosList usePosList() {
        return usePosList;
    }

    /**
     * Gets a single line string for logging the details of this interval to a log stream.
     *
     * @param allocator the register allocator context
     */
    public String logString(LinearScan allocator) {
        StringBuilder buf = new StringBuilder(100);
        buf.append(operandNumber).append(':').append(operand).append(' ');
        if (!isRegister(operand)) {
            if (location != null) {
                buf.append("location{").append(location).append("} ");
            }
        }

        buf.append("hints{").append(splitParent.operandNumber);
        Interval hint = locationHint(false);
        if (hint != null && hint.operandNumber != splitParent.operandNumber) {
            buf.append(", ").append(hint.operandNumber);
        }
        buf.append("} ranges{");

        // print ranges
        Range cur = first;
        while (!cur.isEndMarker()) {
            if (cur != first) {
                buf.append(", ");
            }
            buf.append(cur);
            cur = cur.next;
            assert cur != null : "range list not closed with range sentinel";
        }
        buf.append("} uses{");

        // print use positions
        int prev = -1;
        for (int i = usePosList.size() - 1; i >= 0; --i) {
            assert prev < usePosList.usePos(i) : "use positions not sorted";
            if (i != usePosList.size() - 1) {
                buf.append(", ");
            }
            buf.append(usePosList.usePos(i)).append(':').append(usePosList.registerPriority(i));
            prev = usePosList.usePos(i);
        }
        buf.append("} spill-state{").append(spillState()).append("}");
        if (canMaterialize()) {
            buf.append(" (remat:").append(getMaterializedValue().toString()).append(")");
        }
        return buf.toString();
    }

    List<Interval> getSplitChildren() {
        return Collections.unmodifiableList(splitChildren);
    }
}
