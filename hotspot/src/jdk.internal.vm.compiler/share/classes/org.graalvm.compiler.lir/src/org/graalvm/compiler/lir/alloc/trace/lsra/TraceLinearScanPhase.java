/*
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.DetailedAsserts;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static jdk.vm.ci.code.CodeUtil.isEven;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asRegisterValue;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import org.graalvm.compiler.lir.alloc.trace.TraceBuilderPhase;
import org.graalvm.compiler.lir.alloc.trace.TraceRegisterAllocationPhase;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceInterval.RegisterPriority;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanAllocationPhase.TraceLinearScanAllocationContext;
import org.graalvm.compiler.lir.debug.IntervalDumper;
import org.graalvm.compiler.lir.debug.IntervalDumper.IntervalVisitor;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.options.NestedBooleanOptionValue;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;

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
        public static final OptionValue<Boolean> LIROptTraceRAEliminateSpillMoves = new NestedBooleanOptionValue(LIRPhase.Options.LIROptimization, true);
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

    public TraceLinearScanPhase(TargetDescription target, LIRGenerationResult res, MoveFactory spillMoveFactory, RegisterAllocationConfig regAllocConfig, TraceBuilderResult traceBuilderResult,
                    boolean neverSpillConstants, AllocatableValue[] cachedStackSlots) {
        this.res = res;
        this.moveFactory = spillMoveFactory;
        this.frameMapBuilder = res.getFrameMapBuilder();
        this.registerAttributes = regAllocConfig.getRegisterConfig().getAttributesMap();
        this.regAllocConfig = regAllocConfig;

        this.registers = target.arch.getRegisters();
        this.traceBuilderResult = traceBuilderResult;
        this.neverSpillConstants = neverSpillConstants;
        this.cachedStackSlots = cachedStackSlots;

    }

    public static boolean isVariableOrRegister(Value value) {
        return isVariable(value) || isRegister(value);
    }

    abstract static class IntervalPredicate {

        abstract boolean apply(TraceInterval i);
    }

    static final IntervalPredicate IS_PRECOLORED_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(TraceInterval i) {
            return isRegister(i.operand);
        }
    };

    static final IntervalPredicate IS_VARIABLE_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(TraceInterval i) {
            return isVariable(i.operand);
        }
    };

    static final IntervalPredicate IS_STACK_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(TraceInterval i) {
            return !isRegister(i.operand);
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
            assert interval != null;
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

    private static <T extends IntervalHint> T[] sortIntervalsBeforeAllocation(T[] intervals, T[] sortedList) {
        int sortedIdx = 0;
        int sortedFromMax = -1;

        // special sorting algorithm: the original interval-list is almost sorted,
        // only some intervals are swapped. So this is much faster than a complete QuickSort
        for (T interval : intervals) {
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

        /**
         * Fixed intervals sorted by {@link FixedInterval#from()}.
         */
        private FixedInterval[] sortedFixedIntervals;

        private final Trace trace;

        public TraceLinearScan(Trace trace) {
            this.trace = trace;
            this.fixedIntervals = new FixedInterval[registers.size()];
        }

        /**
         * Converts an operand (variable or register) to an index in a flat address space covering
         * all the {@linkplain Variable variables} and {@linkplain RegisterValue registers} being
         * processed by this allocator.
         */
        int operandNumber(Value operand) {
            assert !isRegister(operand) : "Register do not have operand numbers: " + operand;
            assert isVariable(operand) : "Unsupported Value " + operand;
            return ((Variable) operand).index;
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
            List<LIRInstruction> instructions = getLIR().getLIRforBlock(block);
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
         * {@linkplain TraceInterval#operand variable}.
         */
        private AllocatableValue allocateSpillSlot(TraceInterval interval) {
            int variableIndex = LIRValueUtil.asVariable(interval.splitParent().operand).index;
            if (TraceRegisterAllocationPhase.Options.TraceRACacheStackSlots.getValue()) {
                AllocatableValue cachedStackSlot = cachedStackSlots[variableIndex];
                if (cachedStackSlot != null) {
                    TraceRegisterAllocationPhase.globalStackSlots.increment();
                    assert cachedStackSlot.getValueKind().equals(interval.kind()) : "CachedStackSlot: kind mismatch? " + interval.kind() + " vs. " + cachedStackSlot.getValueKind();
                    return cachedStackSlot;
                }
            }
            VirtualStackSlot slot = frameMapBuilder.allocateSpillSlot(interval.kind());
            if (TraceRegisterAllocationPhase.Options.TraceRACacheStackSlots.getValue()) {
                cachedStackSlots[variableIndex] = slot;
            }
            TraceRegisterAllocationPhase.allocatedStackSlots.increment();
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
            assert isSortedByFrom(sortedFixedIntervals) : "interval list is not sorted";

            FixedInterval list1 = FixedInterval.EndMarker;

            FixedInterval list1Prev = null;
            FixedInterval v;

            int n = sortedFixedIntervals.length;
            for (int i = 0; i < n; i++) {
                v = sortedFixedIntervals[i];
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

        protected void sortFixedIntervalsBeforeAllocation() {
            int sortedLen = 0;
            for (FixedInterval interval : fixedIntervals()) {
                if (interval != null) {
                    sortedLen++;
                }
            }
            sortedFixedIntervals = TraceLinearScanPhase.sortIntervalsBeforeAllocation(fixedIntervals(), new FixedInterval[sortedLen]);
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
            Arrays.sort(newList, (TraceInterval a, TraceInterval b) -> a.from() - b.from());

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
            Arrays.sort(sortedIntervals, (TraceInterval a, TraceInterval b) -> a.spillDefinitionPos() - b.spillDefinitionPos());
        }

        // wrapper for Interval.splitChildAtOpId that performs a bailout in product mode
        // instead of returning null
        public TraceInterval splitChildAtOpId(TraceInterval interval, int opId, LIRInstruction.OperandMode mode) {
            TraceInterval result = interval.getSplitChildAtOpId(opId, mode);

            if (result != null) {
                if (Debug.isLogEnabled()) {
                    Debug.log("Split child at pos %d of interval %s is %s", opId, interval, result);
                }
                return result;
            }
            throw new GraalError("LinearScan: interval is null");
        }

        AllocatableValue canonicalSpillOpr(TraceInterval interval) {
            assert interval.spillSlot() != null : "canonical spill slot not set";
            return interval.spillSlot();
        }

        boolean isMaterialized(AllocatableValue operand, int opId, OperandMode mode) {
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
            /*
             * This is the point to enable debug logging for the whole register allocation.
             */
            try (Indent indent = Debug.logAndIndent("LinearScan allocate")) {
                TraceLinearScanAllocationContext context = new TraceLinearScanAllocationContext(traceContext.spillMoveFactory, traceContext.registerAllocationConfig, traceBuilderResult, this);

                TRACE_LINEAR_SCAN_LIFETIME_ANALYSIS_PHASE.apply(target, lirGenRes, trace, context, false);

                try (Scope s = Debug.scope("AfterLifetimeAnalysis", this)) {

                    printLir("Before register allocation", true);
                    printIntervals("Before register allocation");

                    sortIntervalsBeforeAllocation();
                    sortFixedIntervalsBeforeAllocation();

                    TRACE_LINEAR_SCAN_REGISTER_ALLOCATION_PHASE.apply(target, lirGenRes, trace, context, false);
                    printIntervals("After register allocation");

                    // resolve intra-trace data-flow
                    TRACE_LINEAR_SCAN_RESOLVE_DATA_FLOW_PHASE.apply(target, lirGenRes, trace, context, false);
                    Debug.dump(TraceBuilderPhase.TRACE_DUMP_LEVEL, sortedBlocks(), "%s", TRACE_LINEAR_SCAN_RESOLVE_DATA_FLOW_PHASE.getName());

                    // eliminate spill moves
                    if (Options.LIROptTraceRAEliminateSpillMoves.getValue()) {
                        TRACE_LINEAR_SCAN_ELIMINATE_SPILL_MOVE_PHASE.apply(target, lirGenRes, trace, context, false);
                        Debug.dump(TraceBuilderPhase.TRACE_DUMP_LEVEL, sortedBlocks(), "%s", TRACE_LINEAR_SCAN_ELIMINATE_SPILL_MOVE_PHASE.getName());
                    }

                    TRACE_LINEAR_SCAN_ASSIGN_LOCATIONS_PHASE.apply(target, lirGenRes, trace, context, false);

                    if (DetailedAsserts.getValue()) {
                        verifyIntervals();
                    }
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }
        }

        public void printLir(String label, @SuppressWarnings("unused") boolean hirValid) {
            if (Debug.isDumpEnabled(TraceBuilderPhase.TRACE_DUMP_LEVEL)) {
                Debug.dump(TraceBuilderPhase.TRACE_DUMP_LEVEL, sortedBlocks(), label);
            }
        }

        boolean verify() {
            // (check that all intervals have a correct register and that no registers are
            // overwritten)
            verifyIntervals();

            verifyRegisters();

            Debug.log("no errors found");

            return true;
        }

        @SuppressWarnings("try")
        private void verifyRegisters() {
            // Enable this logging to get output for the verification process.
            try (Indent indent = Debug.logAndIndent("verifying register allocation")) {
                RegisterVerifier verifier = new RegisterVerifier(this);
                verifier.verify(blockAt(0));
            }
        }

        @SuppressWarnings("try")
        protected void verifyIntervals() {
            try (Indent indent = Debug.logAndIndent("verifying intervals")) {
                int len = intervalsSize();

                for (int i = 0; i < len; i++) {
                    final TraceInterval i1 = intervals()[i];
                    if (i1 == null) {
                        continue;
                    }

                    i1.checkSplitChildren();

                    if (i1.operandNumber != i) {
                        Debug.log("Interval %d is on position %d in list", i1.operandNumber, i);
                        Debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    if (isVariable(i1.operand) && i1.kind().equals(LIRKind.Illegal)) {
                        Debug.log("Interval %d has no type assigned", i1.operandNumber);
                        Debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    if (i1.location() == null) {
                        Debug.log("Interval %d has no register assigned", i1.operandNumber);
                        Debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    if (i1.isEmpty()) {
                        Debug.log("Interval %d has no Range", i1.operandNumber);
                        Debug.log(i1.logString());
                        throw new GraalError("");
                    }

                    if (i1.from() >= i1.to()) {
                        Debug.log("Interval %d has zero length range", i1.operandNumber);
                        Debug.log(i1.logString());
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

        class CheckConsumer implements ValueConsumer {

            boolean ok;
            FixedInterval curInterval;

            @Override
            public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isRegister(operand)) {
                    if (fixedIntervalFor(asRegisterValue(operand)) == curInterval) {
                        ok = true;
                    }
                }
            }
        }

        @SuppressWarnings("try")
        void verifyNoOopsInFixedIntervals() {
            try (Indent indent = Debug.logAndIndent("verifying that no oops are in fixed intervals *")) {
                CheckConsumer checkConsumer = new CheckConsumer();

                TraceInterval otherIntervals;
                FixedInterval fixedInts = createFixedUnhandledList();
                // to ensure a walking until the last instruction id, add a dummy interval
                // with a high operation id
                otherIntervals = new TraceInterval(Value.ILLEGAL, -1);
                otherIntervals.addRange(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1);
                TraceIntervalWalker iw = new TraceIntervalWalker(this, fixedInts, otherIntervals);

                for (AbstractBlockBase<?> block : sortedBlocks()) {
                    List<LIRInstruction> instructions = getLIR().getLIRforBlock(block);

                    for (int j = 0; j < instructions.size(); j++) {
                        LIRInstruction op = instructions.get(j);

                        if (op.hasState()) {
                            iw.walkBefore(op.id());
                            boolean checkLive = true;

                            /*
                             * Make sure none of the fixed registers is live across an oopmap since
                             * we can't handle that correctly.
                             */
                            if (checkLive) {
                                for (FixedInterval interval = iw.activeFixedList.getFixed(); interval != FixedInterval.EndMarker; interval = interval.next) {
                                    if (interval.to() > op.id() + 1) {
                                        /*
                                         * This interval is live out of this op so make sure that
                                         * this interval represents some value that's referenced by
                                         * this op either as an input or output.
                                         */
                                        checkConsumer.curInterval = interval;
                                        checkConsumer.ok = false;

                                        op.visitEachInput(checkConsumer);
                                        op.visitEachAlive(checkConsumer);
                                        op.visitEachTemp(checkConsumer);
                                        op.visitEachOutput(checkConsumer);

                                        assert checkConsumer.ok : "fixed intervals should never be live across an oopmap point";
                                    }
                                }
                            }
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
         * Map from {@linkplain #operandNumber(Value) operand numbers} to intervals.
         */
        TraceInterval[] intervals() {
            return intervals;
        }

        /**
         * Map from {@linkplain #operandNumber(Value) operand numbers} to intervals.
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
        private TraceInterval createInterval(AllocatableValue operand) {
            assert isLegal(operand);
            int operandNumber = operandNumber(operand);
            TraceInterval interval = new TraceInterval(operand, operandNumber);
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
            Variable variable = createVariable(source.kind());

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

        TraceInterval intervalFor(Value operand) {
            int operandNumber = operandNumber(operand);
            assert operandNumber < intervalsSize;
            return intervals[operandNumber];
        }

        TraceInterval getOrCreateInterval(AllocatableValue operand) {
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
            if (Debug.isDumpEnabled(TraceBuilderPhase.TRACE_DUMP_LEVEL)) {
                if (Debug.isLogEnabled()) {
                    try (Indent indent = Debug.logAndIndent("intervals %s", label)) {
                        for (FixedInterval interval : fixedIntervals) {
                            if (interval != null) {
                                Debug.log("%s", interval.logString());
                            }
                        }

                        for (TraceInterval interval : intervals) {
                            if (interval != null) {
                                Debug.log("%s", interval.logString());
                            }
                        }

                        try (Indent indent2 = Debug.logAndIndent("Basic Blocks")) {
                            for (AbstractBlockBase<?> block : trace.getBlocks()) {
                                Debug.log("B%d [%d, %d, %s] ", block.getId(), getFirstLirInstructionId(block), getLastLirInstructionId(block), block.getLoop());
                            }
                        }
                    }
                }
                Debug.dump(Debug.INFO_LOG_LEVEL, this, label);
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
        assert a.operand.equals(b.operand) : "Operand mismatch: " + a + " vs. " + b;
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

    private static void printInterval(TraceInterval interval, IntervalVisitor visitor) {
        Value hint = interval.locationHint(false) != null ? interval.locationHint(false).location() : null;
        AllocatableValue operand = interval.operand;
        String type = isRegister(operand) ? "fixed" : operand.getValueKind().getPlatformKind().toString();
        visitor.visitIntervalStart(interval.splitParent().operand, operand, interval.location(), hint, type);

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
}
