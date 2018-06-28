/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.code.CodeUtil.isEven;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.alloc.trace.GlobalLivenessInfo;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import org.graalvm.compiler.lir.alloc.trace.TraceRegisterAllocationPhase;
import org.graalvm.compiler.lir.alloc.trace.TraceUtil;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceInterval.RegisterPriority;
import org.graalvm.compiler.lir.debug.IntervalDumper;
import org.graalvm.compiler.lir.debug.IntervalDumper.IntervalVisitor;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.options.NestedBooleanOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Implementation of the Linear Scan allocation approach for traces described in
 * <a href="http://dx.doi.org/10.1145/2972206.2972211">"Trace-based Register Allocation in a JIT
 * Compiler"</a> by Josef Eisl et al. It is derived from
 * <a href="http://doi.acm.org/10.1145/1064979.1064998" > "Optimized Interval Splitting in a Linear
 * Scan Register Allocator"</a> by Christian Wimmer and Hanspeter Moessenboeck.
 */
public final class TraceLinearScanPhase extends TraceAllocationPhase<TraceAllocationContext> {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable spill position optimization", type = OptionType.Debug)
        public static final OptionKey<Boolean> LIROptTraceRAEliminateSpillMoves = new NestedBooleanOptionKey(LIRPhase.Options.LIROptimization, true);
        // @formatter:on
    }

    private static final TraceLinearScanRegisterAllocationPhase TRACE_LINEAR_SCAN_REGISTER_ALLOCATION_PHASE = new TraceLinearScanRegisterAllocationPhase();
    private static final TraceLinearScanAssignLocationsPhase TRACE_LINEAR_SCAN_ASSIGN_LOCATIONS_PHASE = new TraceLinearScanAssignLocationsPhase();
    private static final TraceLinearScanEliminateSpillMovePhase TRACE_LINEAR_SCAN_ELIMINATE_SPILL_MOVE_PHASE = new TraceLinearScanEliminateSpillMovePhase();
    private static final TraceLinearScanResolveDataFlowPhase TRACE_LINEAR_SCAN_RESOLVE_DATA_FLOW_PHASE = new TraceLinearScanResolveDataFlowPhase();
    private static final TraceLinearScanLifetimeAnalysisPhase TRACE_LINEAR_SCAN_LIFETIME_ANALYSIS_PHASE = new TraceLinearScanLifetimeAnalysisPhase();

    public static final int DOMINATOR_SPILL_MOVE_ID = -2;

    private final FrameMapBuilder frameMapBuilder;
    private final RegisterAttributes[] registerAttributes;
    private final RegisterArray registers;
    private final RegisterAllocationConfig regAllocConfig;
    private final MoveFactory moveFactory;

    protected final TraceBuilderResult traceBuilderResult;

    private final boolean neverSpillConstants;

    /**
     * Maps from {@link Variable#index} to a spill stack slot. If
     * {@linkplain org.graalvm.compiler.lir.alloc.trace.TraceRegisterAllocationPhase.Options#TraceRACacheStackSlots
     * enabled} a {@link Variable} is always assigned to the same stack slot.
     */
    private final AllocatableValue[] cachedStackSlots;

    private final LIRGenerationResult res;
    private final GlobalLivenessInfo livenessInfo;

    public TraceLinearScanPhase(TargetDescription target, LIRGenerationResult res, MoveFactory spillMoveFactory, RegisterAllocationConfig regAllocConfig, TraceBuilderResult traceBuilderResult,
                    boolean neverSpillConstants, AllocatableValue[] cachedStackSlots, GlobalLivenessInfo livenessInfo) {
        this.res = res;
        this.moveFactory = spillMoveFactory;
        this.frameMapBuilder = res.getFrameMapBuilder();
        this.registerAttributes = regAllocConfig.getRegisterConfig().getAttributesMap();
        this.regAllocConfig = regAllocConfig;

        this.registers = target.arch.getRegisters();
        this.traceBuilderResult = traceBuilderResult;
        this.neverSpillConstants = neverSpillConstants;
        this.cachedStackSlots = cachedStackSlots;
        this.livenessInfo = livenessInfo;
        assert livenessInfo != null;
    }

    protected DebugContext getDebug() {
        return res.getLIR().getDebug();
    }

    public static boolean isVariableOrRegister(Value value) {
        return isVariable(value) || isRegister(value);
    }

    abstract static class IntervalPredicate {

        abstract boolean apply(TraceInterval i);
    }

    static final IntervalPredicate IS_VARIABLE_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(TraceInterval i) {
            // all TraceIntervals are variable intervals
            return !i.preSpilledAllocated();
        }
    };
    private static final Comparator<TraceInterval> SORT_BY_FROM_COMP = new Comparator<TraceInterval>() {

        @Override
        public int compare(TraceInterval a, TraceInterval b) {
            return a.from() - b.from();
        }
    };
    private static final Comparator<TraceInterval> SORT_BY_SPILL_POS_COMP = new Comparator<TraceInterval>() {

        @Override
        public int compare(TraceInterval a, TraceInterval b) {
            return a.spillDefinitionPos() - b.spillDefinitionPos();
        }
    };

    public TraceLinearScan createAllocator(Trace trace) {
        return new TraceLinearScan(trace);
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, TraceAllocationContext traceContext) {
        createAllocator(trace).allocate(target, lirGenRes, traceContext);
    }

    private static <T extends IntervalHint> boolean isSortedByFrom(T[] intervals) {
        int from = -1;
        for (T interval : intervals) {
            if (interval == null) {
                continue;
            }
            assert from <= interval.from();
            from = interval.from();
        }
        return true;
    }

    private static boolean isSortedBySpillPos(TraceInterval[] intervals) {
        int from = -1;
        for (TraceInterval interval : intervals) {
            assert interval != null;
            assert from <= interval.spillDefinitionPos();
            from = interval.spillDefinitionPos();
        }
        return true;
    }

    private static TraceInterval[] sortIntervalsBeforeAllocation(TraceInterval[] intervals, TraceInterval[] sortedList) {
        int sortedIdx = 0;
        int sortedFromMax = -1;

        // special sorting algorithm: the original interval-list is almost sorted,
        // only some intervals are swapped. So this is much faster than a complete QuickSort
        for (TraceInterval interval : intervals) {
            if (interval != null) {
                int from = interval.from();

                if (sortedFromMax <= from) {
                    sortedList[sortedIdx++] = interval;
                    sortedFromMax = interval.from();
                } else {
                    // the assumption that the intervals are already sorted failed,
                    // so this interval must be sorted in manually
                    int j;
                    for (j = sortedIdx - 1; j >= 0 && from < sortedList[j].from(); j--) {
                        sortedList[j + 1] = sortedList[j];
                    }
                    sortedList[j + 1] = interval;
                    sortedIdx++;
                }
            }
        }
        return sortedList;
    }

    public final class TraceLinearScan implements IntervalDumper {

        /**
         * Intervals sorted by {@link TraceInterval#from()}.
         */
        private TraceInterval[] sortedIntervals;

        private final Trace trace;

        public TraceLinearScan(Trace trace) {
            this.trace = trace;
            this.fixedIntervals = new FixedInterval[registers.size()];
        }

        GlobalLivenessInfo getGlobalLivenessInfo() {
            return livenessInfo;
        }

        /**
         * @return {@link Variable#index}
         */
        int operandNumber(Variable operand) {
            return operand.index;
        }

        OptionValues getOptions() {
            return getLIR().getOptions();
        }

        DebugContext getDebug() {
            return getLIR().getDebug();
        }

        /**
         * Gets the number of operands. This value will increase by 1 for new variable.
         */
        int operandSize() {
            return getLIR().numVariables();
        }

        /**
         * Gets the number of registers. This value will never change.
         */
        int numRegisters() {
            return registers.size();
        }

        public int getFirstLirInstructionId(AbstractBlockBase<?> block) {
            int result = getLIR().getLIRforBlock(block).get(0).id();
            assert result >= 0;
            return result;
        }

        public int getLastLirInstructionId(AbstractBlockBase<?> block) {
            ArrayList<LIRInstruction> instructions = getLIR().getLIRforBlock(block);
            int result = instructions.get(instructions.size() - 1).id();
            assert result >= 0;
            return result;
        }

        /**
         * Gets an object describing the attributes of a given register according to this register
         * configuration.
         */
        public RegisterAttributes attributes(Register reg) {
            return registerAttributes[reg.number];
        }

        public boolean isAllocatable(RegisterValue register) {
            return attributes(register.getRegister()).isAllocatable();
        }

        public MoveFactory getSpillMoveFactory() {
            return moveFactory;
        }

        protected TraceLocalMoveResolver createMoveResolver() {
            TraceLocalMoveResolver moveResolver = new TraceLocalMoveResolver(this);
            assert moveResolver.checkEmpty();
            return moveResolver;
        }

        void assignSpillSlot(TraceInterval interval) {
            /*
             * Assign the canonical spill slot of the parent (if a part of the interval is already
             * spilled) or allocate a new spill slot.
             */
            if (interval.canMaterialize()) {
                interval.assignLocation(Value.ILLEGAL);
            } else if (interval.spillSlot() != null) {
                interval.assignLocation(interval.spillSlot());
            } else {
                AllocatableValue slot = allocateSpillSlot(interval);
                interval.setSpillSlot(slot);
                interval.assignLocation(slot);
            }
        }

        /**
         * Returns a new spill slot or a cached entry if there is already one for the
         * {@linkplain TraceInterval variable}.
         */
        private AllocatableValue allocateSpillSlot(TraceInterval interval) {
            DebugContext debug = res.getLIR().getDebug();
            int variableIndex = interval.splitParent().operandNumber;
            OptionValues options = getOptions();
            if (TraceRegisterAllocationPhase.Options.TraceRACacheStackSlots.getValue(options)) {
                AllocatableValue cachedStackSlot = cachedStackSlots[variableIndex];
                if (cachedStackSlot != null) {
                    TraceRegisterAllocationPhase.globalStackSlots.increment(debug);
                    assert cachedStackSlot.getValueKind().equals(getKind(interval)) : "CachedStackSlot: kind mismatch? " + getKind(interval) + " vs. " + cachedStackSlot.getValueKind();
                    return cachedStackSlot;
                }
            }
            VirtualStackSlot slot = frameMapBuilder.allocateSpillSlot(getKind(interval));
            if (TraceRegisterAllocationPhase.Options.TraceRACacheStackSlots.getValue(options)) {
                cachedStackSlots[variableIndex] = slot;
            }
            TraceRegisterAllocationPhase.allocatedStackSlots.increment(debug);
            return slot;
        }

        // access to block list (sorted in linear scan order)
        public int blockCount() {
            return sortedBlocks().length;
        }

        public AbstractBlockBase<?> blockAt(int index) {
            return sortedBlocks()[index];
        }

        int numLoops() {
            return getLIR().getControlFlowGraph().getLoops().size();
        }

        boolean isBlockBegin(int opId) {
            return opId == 0 || blockForId(opId) != blockForId(opId - 1);
        }

        boolean isBlockEnd(int opId) {
            boolean isBlockBegin = isBlockBegin(opId + 2);
            assert isBlockBegin == (instructionForId(opId & (~1)) instanceof BlockEndOp);
            return isBlockBegin;
        }

        boolean coversBlockBegin(int opId1, int opId2) {
            return blockForId(opId1) != blockForId(opId2);
        }

        /**
         * Determines if an {@link LIRInstruction} destroys all caller saved registers.
         *
         * @param opId an instruction {@linkplain LIRInstruction#id id}
         * @return {@code true} if the instruction denoted by {@code id} destroys all caller saved
         *         registers.
         */
        boolean hasCall(int opId) {
            assert isEven(opId) : "opId not even";
            return instructionForId(opId).destroysCallerSavedRegisters();
        }

        public boolean isProcessed(Value operand) {
            return !isRegister(operand) || attributes(asRegister(operand)).isAllocatable();
        }

        // * Phase 5: actual register allocation

        private TraceInterval addToList(TraceInterval first, TraceInterval prev, TraceInterval interval) {
            TraceInterval newFirst = first;
            if (prev != null) {
                prev.next = interval;
            } else {
                newFirst = interval;
            }
            return newFirst;
        }

        TraceInterval createUnhandledListByFrom(IntervalPredicate isList1) {
            assert isSortedByFrom(sortedIntervals) : "interval list is not sorted";
            return createUnhandledList(isList1);
        }

        TraceInterval createUnhandledListBySpillPos(IntervalPredicate isList1) {
            assert isSortedBySpillPos(sortedIntervals) : "interval list is not sorted";
            return createUnhandledList(isList1);
        }

        private TraceInterval createUnhandledList(IntervalPredicate isList1) {

            TraceInterval list1 = TraceInterval.EndMarker;

            TraceInterval list1Prev = null;
            TraceInterval v;

            int n = sortedIntervals.length;
            for (int i = 0; i < n; i++) {
                v = sortedIntervals[i];
                if (v == null) {
                    continue;
                }

                if (isList1.apply(v)) {
                    list1 = addToList(list1, list1Prev, v);
                    list1Prev = v;
                }
            }

            if (list1Prev != null) {
                list1Prev.next = TraceInterval.EndMarker;
            }

            assert list1Prev == null || list1Prev.next == TraceInterval.EndMarker : "linear list ends not with sentinel";

            return list1;
        }

        private FixedInterval addToList(FixedInterval first, FixedInterval prev, FixedInterval interval) {
            FixedInterval newFirst = first;
            if (prev != null) {
                prev.next = interval;
            } else {
                newFirst = interval;
            }
            return newFirst;
        }

        FixedInterval createFixedUnhandledList() {
            assert isSortedByFrom(fixedIntervals) : "interval list is not sorted";

            FixedInterval list1 = FixedInterval.EndMarker;

            FixedInterval list1Prev = null;
            for (FixedInterval v : fixedIntervals) {

                if (v == null) {
                    continue;
                }

                v.rewindRange();
                list1 = addToList(list1, list1Prev, v);
                list1Prev = v;
            }

            if (list1Prev != null) {
                list1Prev.next = FixedInterval.EndMarker;
            }

            assert list1Prev == null || list1Prev.next == FixedInterval.EndMarker : "linear list ends not with sentinel";

            return list1;
        }

        // SORTING

        protected void sortIntervalsBeforeAllocation() {
            int sortedLen = 0;
            for (TraceInterval interval : intervals()) {
                if (interval != null) {
                    sortedLen++;
                }
            }
            sortedIntervals = TraceLinearScanPhase.sortIntervalsBeforeAllocation(intervals(), new TraceInterval[sortedLen]);
        }

        void sortIntervalsAfterAllocation() {
            if (hasDerivedIntervals()) {
                // no intervals have been added during allocation, so sorted list is already up to
                // date
                return;
            }

            TraceInterval[] oldList = sortedIntervals;
            TraceInterval[] newList = Arrays.copyOfRange(intervals(), firstDerivedIntervalIndex(), intervalsSize());
            int oldLen = oldList.length;
            int newLen = newList.length;

            // conventional sort-algorithm for new intervals
            Arrays.sort(newList, SORT_BY_FROM_COMP);

            // merge old and new list (both already sorted) into one combined list
            TraceInterval[] combinedList = new TraceInterval[oldLen + newLen];
            int oldIdx = 0;
            int newIdx = 0;

            while (oldIdx + newIdx < combinedList.length) {
                if (newIdx >= newLen || (oldIdx < oldLen && oldList[oldIdx].from() <= newList[newIdx].from())) {
                    combinedList[oldIdx + newIdx] = oldList[oldIdx];
                    oldIdx++;
                } else {
                    combinedList[oldIdx + newIdx] = newList[newIdx];
                    newIdx++;
                }
            }

            sortedIntervals = combinedList;
        }

        void sortIntervalsBySpillPos() {
            // TODO (JE): better algorithm?
            // conventional sort-algorithm for new intervals
            Arrays.sort(sortedIntervals, SORT_BY_SPILL_POS_COMP);
        }

        // wrapper for Interval.splitChildAtOpId that performs a bailout in product mode
        // instead of returning null
        public TraceInterval splitChildAtOpId(TraceInterval interval, int opId, LIRInstruction.OperandMode mode) {
            TraceInterval result = interval.getSplitChildAtOpId(opId, mode);

            if (result != null) {
                if (getDebug().isLogEnabled()) {
                    getDebug().log("Split child at pos %d of interval %s is %s", opId, interval, result);
                }
                return result;
            }
            throw new GraalError("LinearScan: interval is null");
        }

        AllocatableValue canonicalSpillOpr(TraceInterval interval) {
            assert interval.spillSlot() != null : "canonical spill slot not set";
            return interval.spillSlot();
        }

        boolean isMaterialized(Variable operand, int opId, OperandMode mode) {
            TraceInterval interval = intervalFor(operand);
            assert interval != null : "interval must exist";

            if (opId != -1) {
                /*
                 * Operands are not changed when an interval is split during allocation, so search
                 * the right interval here.
                 */
                interval = splitChildAtOpId(interval, opId, mode);
            }

            return isIllegal(interval.location()) && interval.canMaterialize();
        }

        boolean isCallerSave(Value operand) {
            return attributes(asRegister(operand)).isCallerSave();
        }

        @SuppressWarnings("try")
        protected void allocate(TargetDescription target, LIRGenerationResult lirGenRes, TraceAllocationContext traceContext) {
            MoveFactory spillMoveFactory = traceContext.spillMoveFactory;
            RegisterAllocationConfig registerAllocationConfig = traceContext.registerAllocationConfig;
            /*
             * This is the point to enable debug logging for the whole register allocation.
             */
            DebugContext debug = res.getLIR().getDebug();
            try (Indent indent = debug.logAndIndent("LinearScan allocate")) {
                TRACE_LINEAR_SCAN_LIFETIME_ANALYSIS_PHASE.apply(target, lirGenRes, trace, spillMoveFactory, registerAllocationConfig, traceBuilderResult, this, false);

                try (DebugContext.Scope s = debug.scope("AfterLifetimeAnalysis", this)) {

                    printLir("After instruction numbering");
                    printIntervals("Before register allocation");

                    sortIntervalsBeforeAllocation();

                    TRACE_LINEAR_SCAN_REGISTER_ALLOCATION_PHASE.apply(target, lirGenRes, trace, spillMoveFactory, registerAllocationConfig, traceBuilderResult, this, false);
                    printIntervals("After register allocation");

                    // resolve intra-trace data-flow
                    TRACE_LINEAR_SCAN_RESOLVE_DATA_FLOW_PHASE.apply(target, lirGenRes, trace, spillMoveFactory, registerAllocationConfig, traceBuilderResult, this);

                    // eliminate spill moves
                    OptionValues options = getOptions();
                    if (Options.LIROptTraceRAEliminateSpillMoves.getValue(options)) {
                        TRACE_LINEAR_SCAN_ELIMINATE_SPILL_MOVE_PHASE.apply(target, lirGenRes, trace, spillMoveFactory, registerAllocationConfig, traceBuilderResult, this);
                    }

                    TRACE_LINEAR_SCAN_ASSIGN_LOCATIONS_PHASE.apply(target, lirGenRes, trace, spillMoveFactory, registerAllocationConfig, traceBuilderResult, this, false);

                    if (Assertions.detailedAssertionsEnabled(options)) {
                        verifyIntervals();
                    }
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
            }
        }

        public void printLir(String label) {
            if (getDebug().isDumpEnabled(DebugContext.DETAILED_LEVEL)) {
                getDebug().dump(DebugContext.DETAILED_LEVEL, sortedBlocks(), "%s (Trace%d)", label, trace.getId());
            }
        }

        boolean verify() {
            // (check that all intervals have a correct register and that no registers are
            // overwritten)
            verifyIntervals();

            verifyRegisters();

            getDebug().log("no errors found");

            return true;
        }

        @SuppressWarnings("try")
        private void verifyRegisters() {
            // Enable this logging to get output for the verification process.
            try (Indent indent = getDebug().logAndIndent("verifying register allocation")) {
                RegisterVerifier verifier = new RegisterVerifier(this);
                verifier.verify(blockAt(0));
            }
        }

        @SuppressWarnings("try")
        protected void verifyIntervals() {
            DebugContext debug = getDebug();
            try (Indent indent = debug.logAndIndent("verifying intervals")) {
                int len = intervalsSize();

                for (int i = 0; i < len; i++) {
                    final TraceInterval i1 = intervals()[i];
                    if (i1 == null) {
                        continue;
                    }

                    i1.checkSplitChildren();

                    if (i1.operandNumber != i) {
                        debug.log("Interval %d is on position %d in list", i1.operandNumber, i);
                        debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    if (getKind(i1).equals(LIRKind.Illegal)) {
                        debug.log("Interval %d has no type assigned", i1.operandNumber);
                        debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    if (i1.location() == null) {
                        debug.log("Interval %d has no register assigned", i1.operandNumber);
                        debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    if (i1.isEmpty()) {
                        debug.log("Interval %d has no Range", i1.operandNumber);
                        debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    if (i1.from() >= i1.to()) {
                        debug.log("Interval %d has zero length range", i1.operandNumber);
                        debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    // special intervals that are created in MoveResolver
                    // . ignore them because the range information has no meaning there
                    if (i1.from() == 1 && i1.to() == 2) {
                        continue;
                    }
                    // check any intervals
                    for (int j = i + 1; j < len; j++) {
                        final TraceInterval i2 = intervals()[j];
                        if (i2 == null) {
                            continue;
                        }

                        // special intervals that are created in MoveResolver
                        // . ignore them because the range information has no meaning there
                        if (i2.from() == 1 && i2.to() == 2) {
                            continue;
                        }
                        Value l1 = i1.location();
                        Value l2 = i2.location();
                        boolean intersects = i1.intersects(i2);
                        if (intersects && !isIllegal(l1) && (l1.equals(l2))) {
                            throw GraalError.shouldNotReachHere(String.format("Intervals %s and %s overlap and have the same register assigned\n%s\n%s", i1, i2, i1.logString(), i2.logString()));
                        }
                    }
                    // check fixed intervals
                    for (FixedInterval i2 : fixedIntervals()) {
                        if (i2 == null) {
                            continue;
                        }

                        Value l1 = i1.location();
                        Value l2 = i2.location();
                        boolean intersects = i2.intersects(i1);
                        if (intersects && !isIllegal(l1) && (l1.equals(l2))) {
                            throw GraalError.shouldNotReachHere(String.format("Intervals %s and %s overlap and have the same register assigned\n%s\n%s", i1, i2, i1.logString(), i2.logString()));
                        }
                    }
                }
            }
        }

        public LIR getLIR() {
            return res.getLIR();
        }

        public FrameMapBuilder getFrameMapBuilder() {
            return frameMapBuilder;
        }

        public AbstractBlockBase<?>[] sortedBlocks() {
            return trace.getBlocks();
        }

        public RegisterArray getRegisters() {
            return registers;
        }

        public RegisterAllocationConfig getRegisterAllocationConfig() {
            return regAllocConfig;
        }

        public boolean callKillsRegisters() {
            return regAllocConfig.getRegisterConfig().areAllAllocatableRegistersCallerSaved();
        }

        boolean neverSpillConstants() {
            return neverSpillConstants;
        }

        // IntervalData

        private static final int SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT = 1;

        /**
         * The index of the first entry in {@link #intervals} for a
         * {@linkplain #createDerivedInterval(TraceInterval) derived interval}.
         */
        private int firstDerivedIntervalIndex = -1;

        /**
         * @see #fixedIntervals()
         */
        private final FixedInterval[] fixedIntervals;

        /**
         * @see #intervals()
         */
        private TraceInterval[] intervals;

        /**
         * The number of valid entries in {@link #intervals}.
         */
        private int intervalsSize;

        /**
         * Map from an instruction {@linkplain LIRInstruction#id id} to the instruction. Entries
         * should be retrieved with {@link #instructionForId(int)} as the id is not simply an index
         * into this array.
         */
        private LIRInstruction[] opIdToInstructionMap;

        /**
         * Map from an instruction {@linkplain LIRInstruction#id id} to the
         * {@linkplain AbstractBlockBase block} containing the instruction. Entries should be
         * retrieved with {@link #blockForId(int)} as the id is not simply an index into this array.
         */
        private AbstractBlockBase<?>[] opIdToBlockMap;

        /**
         * Map from {@linkplain #operandNumber operand numbers} to intervals.
         */
        TraceInterval[] intervals() {
            return intervals;
        }

        /**
         * Map from {@linkplain Register#number} to fixed intervals.
         */
        FixedInterval[] fixedIntervals() {
            return fixedIntervals;
        }

        void initIntervals() {
            intervalsSize = operandSize();
            intervals = new TraceInterval[intervalsSize + (intervalsSize >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT)];
        }

        /**
         * Creates a new fixed interval.
         *
         * @param reg the operand for the interval
         * @return the created interval
         */
        private FixedInterval createFixedInterval(RegisterValue reg) {
            FixedInterval interval = new FixedInterval(reg);
            int operandNumber = reg.getRegister().number;
            assert fixedIntervals[operandNumber] == null;
            fixedIntervals[operandNumber] = interval;
            return interval;
        }

        /**
         * Creates a new interval.
         *
         * @param operand the operand for the interval
         * @return the created interval
         */
        private TraceInterval createInterval(Variable operand) {
            assert isLegal(operand);
            int operandNumber = operandNumber(operand);
            assert operand.index == operandNumber;
            TraceInterval interval = new TraceInterval(operand);
            assert operandNumber < intervalsSize;
            assert intervals[operandNumber] == null;
            intervals[operandNumber] = interval;
            return interval;
        }

        /**
         * Creates an interval as a result of splitting or spilling another interval.
         *
         * @param source an interval being split of spilled
         * @return a new interval derived from {@code source}
         */
        TraceInterval createDerivedInterval(TraceInterval source) {
            if (firstDerivedIntervalIndex == -1) {
                firstDerivedIntervalIndex = intervalsSize;
            }
            if (intervalsSize == intervals.length) {
                intervals = Arrays.copyOf(intervals, intervals.length + (intervals.length >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT) + 1);
            }
            // increments intervalsSize
            Variable variable = createVariable(getKind(source));

            assert intervalsSize <= intervals.length;

            TraceInterval interval = createInterval(variable);
            assert intervals[intervalsSize - 1] == interval;
            return interval;
        }

        /**
         * Creates a new variable for a derived interval. Note that the variable is not
         * {@linkplain LIR#numVariables() managed} so it must not be inserted into the {@link LIR}.
         */
        private Variable createVariable(ValueKind<?> kind) {
            return new Variable(kind, intervalsSize++);
        }

        boolean hasDerivedIntervals() {
            return firstDerivedIntervalIndex != -1;
        }

        int firstDerivedIntervalIndex() {
            return firstDerivedIntervalIndex;
        }

        public int intervalsSize() {
            return intervalsSize;
        }

        FixedInterval fixedIntervalFor(RegisterValue reg) {
            return fixedIntervals[reg.getRegister().number];
        }

        FixedInterval getOrCreateFixedInterval(RegisterValue reg) {
            FixedInterval ret = fixedIntervalFor(reg);
            if (ret == null) {
                return createFixedInterval(reg);
            } else {
                return ret;
            }
        }

        TraceInterval intervalFor(Variable operand) {
            return intervalFor(operandNumber(operand));
        }

        TraceInterval intervalFor(int operandNumber) {
            assert operandNumber < intervalsSize;
            return intervals[operandNumber];
        }

        TraceInterval getOrCreateInterval(Variable operand) {
            TraceInterval ret = intervalFor(operand);
            if (ret == null) {
                return createInterval(operand);
            } else {
                return ret;
            }
        }

        void initOpIdMaps(int numInstructions) {
            opIdToInstructionMap = new LIRInstruction[numInstructions];
            opIdToBlockMap = new AbstractBlockBase<?>[numInstructions];
        }

        void putOpIdMaps(int index, LIRInstruction op, AbstractBlockBase<?> block) {
            opIdToInstructionMap[index] = op;
            opIdToBlockMap[index] = block;
        }

        /**
         * Gets the highest instruction id allocated by this object.
         */
        int maxOpId() {
            assert opIdToInstructionMap.length > 0 : "no operations";
            return (opIdToInstructionMap.length - 1) << 1;
        }

        /**
         * Converts an {@linkplain LIRInstruction#id instruction id} to an instruction index. All
         * LIR instructions in a method have an index one greater than their linear-scan order
         * predecessor with the first instruction having an index of 0.
         */
        private int opIdToIndex(int opId) {
            return opId >> 1;
        }

        /**
         * Retrieves the {@link LIRInstruction} based on its {@linkplain LIRInstruction#id id}.
         *
         * @param opId an instruction {@linkplain LIRInstruction#id id}
         * @return the instruction whose {@linkplain LIRInstruction#id} {@code == id}
         */
        LIRInstruction instructionForId(int opId) {
            assert isEven(opId) : "opId not even";
            LIRInstruction instr = opIdToInstructionMap[opIdToIndex(opId)];
            assert instr.id() == opId;
            return instr;
        }

        /**
         * Gets the block containing a given instruction.
         *
         * @param opId an instruction {@linkplain LIRInstruction#id id}
         * @return the block containing the instruction denoted by {@code opId}
         */
        AbstractBlockBase<?> blockForId(int opId) {
            assert opIdToBlockMap.length > 0 && opId >= 0 && opId <= maxOpId() + 1 : "opId out of range: " + opId;
            return opIdToBlockMap[opIdToIndex(opId)];
        }

        @SuppressWarnings("try")
        public void printIntervals(String label) {
            DebugContext debug = getDebug();
            if (debug.isDumpEnabled(DebugContext.DETAILED_LEVEL)) {
                if (debug.isLogEnabled()) {
                    try (Indent indent = debug.logAndIndent("intervals %s", label)) {
                        for (FixedInterval interval : fixedIntervals) {
                            if (interval != null) {
                                debug.log("%s", interval.logString());
                            }
                        }

                        for (TraceInterval interval : intervals) {
                            if (interval != null) {
                                debug.log("%s", interval.logString());
                            }
                        }

                        try (Indent indent2 = debug.logAndIndent("Basic Blocks")) {
                            for (AbstractBlockBase<?> block : trace.getBlocks()) {
                                debug.log("B%d [%d, %d, %s] ", block.getId(), getFirstLirInstructionId(block), getLastLirInstructionId(block), block.getLoop());
                            }
                        }
                    }
                }
                debug.dump(DebugContext.DETAILED_LEVEL, this, "%s (Trace%d)", label, trace.getId());
            }
        }

        @Override
        public void visitIntervals(IntervalVisitor visitor) {
            for (FixedInterval interval : fixedIntervals) {
                if (interval != null) {
                    printFixedInterval(interval, visitor);
                }
            }
            for (TraceInterval interval : intervals) {
                if (interval != null) {
                    printInterval(interval, visitor);
                }
            }
        }

        boolean hasInterTracePredecessor(AbstractBlockBase<?> block) {
            return TraceUtil.hasInterTracePredecessor(traceBuilderResult, trace, block);
        }

        boolean hasInterTraceSuccessor(AbstractBlockBase<?> block) {
            return TraceUtil.hasInterTraceSuccessor(traceBuilderResult, trace, block);
        }

        private void printInterval(TraceInterval interval, IntervalVisitor visitor) {
            Value hint = interval.locationHint(false) != null ? interval.locationHint(false).location() : null;
            AllocatableValue operand = getOperand(interval);
            String type = getKind(interval).getPlatformKind().toString();
            visitor.visitIntervalStart(getOperand(interval.splitParent()), operand, interval.location(), hint, type);

            // print ranges
            visitor.visitRange(interval.from(), interval.to());

            // print use positions
            int prev = -1;
            for (int i = interval.numUsePos() - 1; i >= 0; --i) {
                assert prev < interval.getUsePos(i) : "use positions not sorted";
                visitor.visitUsePos(interval.getUsePos(i), interval.getUsePosRegisterPriority(i));
                prev = interval.getUsePos(i);
            }

            visitor.visitIntervalEnd(interval.spillState());
        }

        AllocatableValue getOperand(TraceInterval interval) {
            return interval.operand;
        }

        ValueKind<?> getKind(TraceInterval interval) {
            return getOperand(interval).getValueKind();
        }

    }

    public static boolean verifyEquals(TraceLinearScan a, TraceLinearScan b) {
        assert compareFixed(a.fixedIntervals(), b.fixedIntervals());
        assert compareIntervals(a.intervals(), b.intervals());
        return true;
    }

    private static boolean compareIntervals(TraceInterval[] a, TraceInterval[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            if (i >= a.length) {
                assert b[i] == null : "missing a interval: " + i + " b: " + b[i];
                continue;
            }
            if (i >= b.length) {
                assert a[i] == null : "missing b interval: " + i + " a: " + a[i];
                continue;
            }
            compareInterval(a[i], b[i]);
        }
        return true;
    }

    private static void compareInterval(TraceInterval a, TraceInterval b) {
        if (a == null) {
            assert b == null : "First interval is null but second is: " + b;
            return;
        }
        assert b != null : "Second interval is null but forst is: " + a;
        assert a.operandNumber == b.operandNumber : "Operand mismatch: " + a + " vs. " + b;
        assert a.from() == b.from() : "From mismatch: " + a + " vs. " + b;
        assert a.to() == b.to() : "To mismatch: " + a + " vs. " + b;
        assert verifyIntervalsEquals(a, b);
    }

    private static boolean verifyIntervalsEquals(TraceInterval a, TraceInterval b) {
        for (int i = 0; i < Math.max(a.numUsePos(), b.numUsePos()); i++) {
            assert i < a.numUsePos() : "missing a usepos: " + i + " b: " + b;
            assert i < b.numUsePos() : "missing b usepos: " + i + " a: " + a;
            int aPos = a.getUsePos(i);
            int bPos = b.getUsePos(i);
            assert aPos == bPos : "Use Positions differ: " + aPos + " vs. " + bPos;
            RegisterPriority aReg = a.getUsePosRegisterPriority(i);
            RegisterPriority bReg = b.getUsePosRegisterPriority(i);
            assert aReg == bReg : "Register priority differ: " + aReg + " vs. " + bReg;
        }
        return true;
    }

    private static boolean compareFixed(FixedInterval[] a, FixedInterval[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            if (i >= a.length) {
                assert b[i] == null : "missing a interval: " + i + " b: " + b[i];
                continue;
            }
            if (i >= b.length) {
                assert a[i] == null : "missing b interval: " + i + " a: " + a[i];
                continue;
            }
            compareFixedInterval(a[i], b[i]);
        }
        return true;
    }

    private static void compareFixedInterval(FixedInterval a, FixedInterval b) {
        if (a == null) {
            assert b == null || isEmptyInterval(b) : "First interval is null but second is: " + b;
            return;
        }
        if (b == null) {
            assert isEmptyInterval(a) : "Second interval is null but first is: " + a;
            return;
        }
        assert a.operand.equals(b.operand) : "Operand mismatch: " + a + " vs. " + b;
        assert a.from() == b.from() : "From mismatch: " + a + " vs. " + b;
        assert a.to() == b.to() : "To mismatch: " + a + " vs. " + b;
        assert verifyFixeEquas(a, b);
    }

    private static boolean verifyFixeEquas(FixedInterval a, FixedInterval b) {
        a.rewindRange();
        b.rewindRange();
        while (!a.currentAtEnd()) {
            assert !b.currentAtEnd() : "Fixed range mismatch: " + a + " vs. " + b;
            assert a.currentFrom() == b.currentFrom() : "From range mismatch: " + a + " vs. " + b + " from: " + a.currentFrom() + " vs. " + b.currentFrom();
            assert a.currentTo() == b.currentTo() : "To range mismatch: " + a + " vs. " + b + " from: " + a.currentTo() + " vs. " + b.currentTo();
            a.nextRange();
            b.nextRange();
        }
        assert b.currentAtEnd() : "Fixed range mismatch: " + a + " vs. " + b;
        return true;
    }

    private static boolean isEmptyInterval(FixedInterval fixed) {
        return fixed.from() == -1 && fixed.to() == 0;
    }

    private static void printFixedInterval(FixedInterval interval, IntervalVisitor visitor) {
        Value hint = null;
        AllocatableValue operand = interval.operand;
        String type = "fixed";
        visitor.visitIntervalStart(operand, operand, operand, hint, type);

        // print ranges
        for (FixedRange range = interval.first(); range != FixedRange.EndMarker; range = range.next) {
            visitor.visitRange(range.from, range.to);
        }

        // no use positions

        visitor.visitIntervalEnd("NOT_SUPPORTED");

    }

}
