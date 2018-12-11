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

import static jdk.vm.ci.code.CodeUtil.isEven;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isLegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.phases.LIRPhase.Options.LIROptimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;

import jdk.internal.vm.compiler.collections.Pair;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.alloc.lsra.Interval.RegisterBinding;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;
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

/**
 * An implementation of the linear scan register allocator algorithm described in
 * <a href="http://doi.acm.org/10.1145/1064979.1064998" > "Optimized Interval Splitting in a Linear
 * Scan Register Allocator"</a> by Christian Wimmer and Hanspeter Moessenboeck.
 */
public class LinearScan {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable spill position optimization", type = OptionType.Debug)
        public static final OptionKey<Boolean> LIROptLSRAOptimizeSpillPosition = new NestedBooleanOptionKey(LIROptimization, true);
        // @formatter:on
    }

    public static class BlockData {

        /**
         * Bit map specifying which operands are live upon entry to this block. These are values
         * used in this block or any of its successors where such value are not defined in this
         * block. The bit index of an operand is its {@linkplain LinearScan#operandNumber(Value)
         * operand number}.
         */
        public BitSet liveIn;

        /**
         * Bit map specifying which operands are live upon exit from this block. These are values
         * used in a successor block that are either defined in this block or were live upon entry
         * to this block. The bit index of an operand is its
         * {@linkplain LinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveOut;

        /**
         * Bit map specifying which operands are used (before being defined) in this block. That is,
         * these are the values that are live upon entry to the block. The bit index of an operand
         * is its {@linkplain LinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveGen;

        /**
         * Bit map specifying which operands are defined/overwritten in this block. The bit index of
         * an operand is its {@linkplain LinearScan#operandNumber(Value) operand number}.
         */
        public BitSet liveKill;
    }

    public static final int DOMINATOR_SPILL_MOVE_ID = -2;
    private static final int SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT = 1;

    private final LIR ir;
    private final FrameMapBuilder frameMapBuilder;
    private final RegisterAttributes[] registerAttributes;
    private final RegisterArray registers;
    private final RegisterAllocationConfig regAllocConfig;
    private final MoveFactory moveFactory;

    private final BlockMap<BlockData> blockData;
    protected final DebugContext debug;

    /**
     * List of blocks in linear-scan order. This is only correct as long as the CFG does not change.
     */
    private final AbstractBlockBase<?>[] sortedBlocks;

    /**
     * @see #intervals()
     */
    private Interval[] intervals;

    /**
     * The number of valid entries in {@link #intervals}.
     */
    private int intervalsSize;

    /**
     * The index of the first entry in {@link #intervals} for a
     * {@linkplain #createDerivedInterval(Interval) derived interval}.
     */
    private int firstDerivedIntervalIndex = -1;

    /**
     * Intervals sorted by {@link Interval#from()}.
     */
    private Interval[] sortedIntervals;

    /**
     * Map from an instruction {@linkplain LIRInstruction#id id} to the instruction. Entries should
     * be retrieved with {@link #instructionForId(int)} as the id is not simply an index into this
     * array.
     */
    private LIRInstruction[] opIdToInstructionMap;

    /**
     * Map from an instruction {@linkplain LIRInstruction#id id} to the
     * {@linkplain AbstractBlockBase block} containing the instruction. Entries should be retrieved
     * with {@link #blockForId(int)} as the id is not simply an index into this array.
     */
    private AbstractBlockBase<?>[] opIdToBlockMap;

    /**
     * The {@linkplain #operandNumber(Value) number} of the first variable operand allocated.
     */
    private final int firstVariableNumber;
    /**
     * Number of variables.
     */
    private int numVariables;
    private final boolean neverSpillConstants;

    /**
     * Sentinel interval to denote the end of an interval list.
     */
    protected final Interval intervalEndMarker;
    public final Range rangeEndMarker;
    public final boolean detailedAsserts;
    private final LIRGenerationResult res;

    protected LinearScan(TargetDescription target, LIRGenerationResult res, MoveFactory spillMoveFactory, RegisterAllocationConfig regAllocConfig, AbstractBlockBase<?>[] sortedBlocks,
                    boolean neverSpillConstants) {
        this.ir = res.getLIR();
        this.res = res;
        this.debug = ir.getDebug();
        this.moveFactory = spillMoveFactory;
        this.frameMapBuilder = res.getFrameMapBuilder();
        this.sortedBlocks = sortedBlocks;
        this.registerAttributes = regAllocConfig.getRegisterConfig().getAttributesMap();
        this.regAllocConfig = regAllocConfig;

        this.registers = target.arch.getRegisters();
        this.firstVariableNumber = getRegisters().size();
        this.numVariables = ir.numVariables();
        this.blockData = new BlockMap<>(ir.getControlFlowGraph());
        this.neverSpillConstants = neverSpillConstants;
        this.rangeEndMarker = new Range(Integer.MAX_VALUE, Integer.MAX_VALUE, null);
        this.intervalEndMarker = new Interval(Value.ILLEGAL, Interval.END_MARKER_OPERAND_NUMBER, null, rangeEndMarker);
        this.intervalEndMarker.next = intervalEndMarker;
        this.detailedAsserts = Assertions.detailedAssertionsEnabled(ir.getOptions());
    }

    public LIRGenerationResult getLIRGenerationResult() {
        return res;
    }

    public Interval intervalEndMarker() {
        return intervalEndMarker;
    }

    public OptionValues getOptions() {
        return ir.getOptions();
    }

    public DebugContext getDebug() {
        return debug;
    }

    public int getFirstLirInstructionId(AbstractBlockBase<?> block) {
        int result = ir.getLIRforBlock(block).get(0).id();
        assert result >= 0;
        return result;
    }

    public int getLastLirInstructionId(AbstractBlockBase<?> block) {
        ArrayList<LIRInstruction> instructions = ir.getLIRforBlock(block);
        int result = instructions.get(instructions.size() - 1).id();
        assert result >= 0;
        return result;
    }

    public MoveFactory getSpillMoveFactory() {
        return moveFactory;
    }

    protected MoveResolver createMoveResolver() {
        MoveResolver moveResolver = new MoveResolver(this);
        assert moveResolver.checkEmpty();
        return moveResolver;
    }

    public static boolean isVariableOrRegister(Value value) {
        return isVariable(value) || isRegister(value);
    }

    /**
     * Converts an operand (variable or register) to an index in a flat address space covering all
     * the {@linkplain Variable variables} and {@linkplain RegisterValue registers} being processed
     * by this allocator.
     */
    int operandNumber(Value operand) {
        if (isRegister(operand)) {
            int number = asRegister(operand).number;
            assert number < firstVariableNumber;
            return number;
        }
        assert isVariable(operand) : operand;
        return firstVariableNumber + ((Variable) operand).index;
    }

    /**
     * Gets the number of operands. This value will increase by 1 for new variable.
     */
    int operandSize() {
        return firstVariableNumber + numVariables;
    }

    /**
     * Gets the highest operand number for a register operand. This value will never change.
     */
    int maxRegisterNumber() {
        return firstVariableNumber - 1;
    }

    public BlockData getBlockData(AbstractBlockBase<?> block) {
        return blockData.get(block);
    }

    void initBlockData(AbstractBlockBase<?> block) {
        blockData.put(block, new BlockData());
    }

    static final IntervalPredicate IS_PRECOLORED_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(Interval i) {
            return isRegister(i.operand);
        }
    };

    static final IntervalPredicate IS_VARIABLE_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(Interval i) {
            return isVariable(i.operand);
        }
    };

    static final IntervalPredicate IS_STACK_INTERVAL = new IntervalPredicate() {

        @Override
        public boolean apply(Interval i) {
            return !isRegister(i.operand);
        }
    };

    /**
     * Gets an object describing the attributes of a given register according to this register
     * configuration.
     */
    public RegisterAttributes attributes(Register reg) {
        return registerAttributes[reg.number];
    }

    void assignSpillSlot(Interval interval) {
        /*
         * Assign the canonical spill slot of the parent (if a part of the interval is already
         * spilled) or allocate a new spill slot.
         */
        if (interval.canMaterialize()) {
            interval.assignLocation(Value.ILLEGAL);
        } else if (interval.spillSlot() != null) {
            interval.assignLocation(interval.spillSlot());
        } else {
            VirtualStackSlot slot = frameMapBuilder.allocateSpillSlot(interval.kind());
            interval.setSpillSlot(slot);
            interval.assignLocation(slot);
        }
    }

    /**
     * Map from {@linkplain #operandNumber(Value) operand numbers} to intervals.
     */
    public Interval[] intervals() {
        return intervals;
    }

    void initIntervals() {
        intervalsSize = operandSize();
        intervals = new Interval[intervalsSize + (intervalsSize >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT)];
    }

    /**
     * Creates a new interval.
     *
     * @param operand the operand for the interval
     * @return the created interval
     */
    Interval createInterval(AllocatableValue operand) {
        assert isLegal(operand);
        int operandNumber = operandNumber(operand);
        Interval interval = new Interval(operand, operandNumber, intervalEndMarker, rangeEndMarker);
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
    Interval createDerivedInterval(Interval source) {
        if (firstDerivedIntervalIndex == -1) {
            firstDerivedIntervalIndex = intervalsSize;
        }
        if (intervalsSize == intervals.length) {
            intervals = Arrays.copyOf(intervals, intervals.length + (intervals.length >> SPLIT_INTERVALS_CAPACITY_RIGHT_SHIFT) + 1);
        }
        intervalsSize++;
        assert intervalsSize <= intervals.length;
        /*
         * Note that these variables are not managed and must therefore never be inserted into the
         * LIR
         */
        Variable variable = new Variable(source.kind(), numVariables++);

        Interval interval = createInterval(variable);
        assert intervals[intervalsSize - 1] == interval;
        return interval;
    }

    // access to block list (sorted in linear scan order)
    public int blockCount() {
        return sortedBlocks.length;
    }

    public AbstractBlockBase<?> blockAt(int index) {
        return sortedBlocks[index];
    }

    /**
     * Gets the size of the {@link BlockData#liveIn} and {@link BlockData#liveOut} sets for a basic
     * block. These sets do not include any operands allocated as a result of creating
     * {@linkplain #createDerivedInterval(Interval) derived intervals}.
     */
    public int liveSetSize() {
        return firstDerivedIntervalIndex == -1 ? operandSize() : firstDerivedIntervalIndex;
    }

    int numLoops() {
        return ir.getControlFlowGraph().getLoops().size();
    }

    Interval intervalFor(int operandNumber) {
        return intervals[operandNumber];
    }

    public Interval intervalFor(Value operand) {
        int operandNumber = operandNumber(operand);
        assert operandNumber < intervalsSize;
        return intervals[operandNumber];
    }

    public Interval getOrCreateInterval(AllocatableValue operand) {
        Interval ret = intervalFor(operand);
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
     * Converts an {@linkplain LIRInstruction#id instruction id} to an instruction index. All LIR
     * instructions in a method have an index one greater than their linear-scan order predecessor
     * with the first instruction having an index of 0.
     */
    private static int opIdToIndex(int opId) {
        return opId >> 1;
    }

    /**
     * Retrieves the {@link LIRInstruction} based on its {@linkplain LIRInstruction#id id}.
     *
     * @param opId an instruction {@linkplain LIRInstruction#id id}
     * @return the instruction whose {@linkplain LIRInstruction#id} {@code == id}
     */
    public LIRInstruction instructionForId(int opId) {
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
    public AbstractBlockBase<?> blockForId(int opId) {
        assert opIdToBlockMap.length > 0 && opId >= 0 && opId <= maxOpId() + 1 : "opId out of range";
        return opIdToBlockMap[opIdToIndex(opId)];
    }

    boolean isBlockBegin(int opId) {
        return opId == 0 || blockForId(opId) != blockForId(opId - 1);
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

    abstract static class IntervalPredicate {

        abstract boolean apply(Interval i);
    }

    public boolean isProcessed(Value operand) {
        return !isRegister(operand) || attributes(asRegister(operand)).isAllocatable();
    }

    // * Phase 5: actual register allocation

    private static boolean isSorted(Interval[] intervals) {
        int from = -1;
        for (Interval interval : intervals) {
            assert interval != null;
            assert from <= interval.from();
            from = interval.from();
        }
        return true;
    }

    static Interval addToList(Interval first, Interval prev, Interval interval) {
        Interval newFirst = first;
        if (prev != null) {
            prev.next = interval;
        } else {
            newFirst = interval;
        }
        return newFirst;
    }

    Pair<Interval, Interval> createUnhandledLists(IntervalPredicate isList1, IntervalPredicate isList2) {
        assert isSorted(sortedIntervals) : "interval list is not sorted";

        Interval list1 = intervalEndMarker;
        Interval list2 = intervalEndMarker;

        Interval list1Prev = null;
        Interval list2Prev = null;
        Interval v;

        int n = sortedIntervals.length;
        for (int i = 0; i < n; i++) {
            v = sortedIntervals[i];
            if (v == null) {
                continue;
            }

            if (isList1.apply(v)) {
                list1 = addToList(list1, list1Prev, v);
                list1Prev = v;
            } else if (isList2 == null || isList2.apply(v)) {
                list2 = addToList(list2, list2Prev, v);
                list2Prev = v;
            }
        }

        if (list1Prev != null) {
            list1Prev.next = intervalEndMarker;
        }
        if (list2Prev != null) {
            list2Prev.next = intervalEndMarker;
        }

        assert list1Prev == null || list1Prev.next.isEndMarker() : "linear list ends not with sentinel";
        assert list2Prev == null || list2Prev.next.isEndMarker() : "linear list ends not with sentinel";

        return Pair.create(list1, list2);
    }

    protected void sortIntervalsBeforeAllocation() {
        int sortedLen = 0;
        for (Interval interval : intervals) {
            if (interval != null) {
                sortedLen++;
            }
        }

        Interval[] sortedList = new Interval[sortedLen];
        int sortedIdx = 0;
        int sortedFromMax = -1;

        // special sorting algorithm: the original interval-list is almost sorted,
        // only some intervals are swapped. So this is much faster than a complete QuickSort
        for (Interval interval : intervals) {
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
        sortedIntervals = sortedList;
    }

    void sortIntervalsAfterAllocation() {
        if (firstDerivedIntervalIndex == -1) {
            // no intervals have been added during allocation, so sorted list is already up to date
            return;
        }

        Interval[] oldList = sortedIntervals;
        Interval[] newList = Arrays.copyOfRange(intervals, firstDerivedIntervalIndex, intervalsSize);
        int oldLen = oldList.length;
        int newLen = newList.length;

        // conventional sort-algorithm for new intervals
        Arrays.sort(newList, (Interval a, Interval b) -> a.from() - b.from());

        // merge old and new list (both already sorted) into one combined list
        Interval[] combinedList = new Interval[oldLen + newLen];
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

    // wrapper for Interval.splitChildAtOpId that performs a bailout in product mode
    // instead of returning null
    public Interval splitChildAtOpId(Interval interval, int opId, LIRInstruction.OperandMode mode) {
        Interval result = interval.getSplitChildAtOpId(opId, mode, this);

        if (result != null) {
            if (debug.isLogEnabled()) {
                debug.log("Split child at pos %d of interval %s is %s", opId, interval, result);
            }
            return result;
        }
        throw new GraalError("LinearScan: interval is null");
    }

    static AllocatableValue canonicalSpillOpr(Interval interval) {
        assert interval.spillSlot() != null : "canonical spill slot not set";
        return interval.spillSlot();
    }

    boolean isMaterialized(AllocatableValue operand, int opId, OperandMode mode) {
        Interval interval = intervalFor(operand);
        assert interval != null : "interval must exist";

        if (opId != -1) {
            /*
             * Operands are not changed when an interval is split during allocation, so search the
             * right interval here.
             */
            interval = splitChildAtOpId(interval, opId, mode);
        }

        return isIllegal(interval.location()) && interval.canMaterialize();
    }

    boolean isCallerSave(Value operand) {
        return attributes(asRegister(operand)).isCallerSave();
    }

    @SuppressWarnings("try")
    protected void allocate(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        /*
         * This is the point to enable debug logging for the whole register allocation.
         */
        try (Indent indent = debug.logAndIndent("LinearScan allocate")) {

            createLifetimeAnalysisPhase().apply(target, lirGenRes, context);

            try (DebugContext.Scope s = debug.scope("AfterLifetimeAnalysis", (Object) intervals)) {
                sortIntervalsBeforeAllocation();

                createRegisterAllocationPhase().apply(target, lirGenRes, context);

                if (LinearScan.Options.LIROptLSRAOptimizeSpillPosition.getValue(getOptions())) {
                    createOptimizeSpillPositionPhase().apply(target, lirGenRes, context);
                }
                createResolveDataFlowPhase().apply(target, lirGenRes, context);

                sortIntervalsAfterAllocation();

                if (detailedAsserts) {
                    verify();
                }
                beforeSpillMoveElimination();
                createSpillMoveEliminationPhase().apply(target, lirGenRes, context);
                createAssignLocationsPhase().apply(target, lirGenRes, context);

                if (detailedAsserts) {
                    verifyIntervals();
                }
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }
    }

    protected void beforeSpillMoveElimination() {
    }

    protected LinearScanLifetimeAnalysisPhase createLifetimeAnalysisPhase() {
        return new LinearScanLifetimeAnalysisPhase(this);
    }

    protected LinearScanRegisterAllocationPhase createRegisterAllocationPhase() {
        return new LinearScanRegisterAllocationPhase(this);
    }

    protected LinearScanOptimizeSpillPositionPhase createOptimizeSpillPositionPhase() {
        return new LinearScanOptimizeSpillPositionPhase(this);
    }

    protected LinearScanResolveDataFlowPhase createResolveDataFlowPhase() {
        return new LinearScanResolveDataFlowPhase(this);
    }

    protected LinearScanEliminateSpillMovePhase createSpillMoveEliminationPhase() {
        return new LinearScanEliminateSpillMovePhase(this);
    }

    protected LinearScanAssignLocationsPhase createAssignLocationsPhase() {
        return new LinearScanAssignLocationsPhase(this);
    }

    @SuppressWarnings("try")
    public void printIntervals(String label) {
        if (debug.isLogEnabled()) {
            try (Indent indent = debug.logAndIndent("intervals %s", label)) {
                for (Interval interval : intervals) {
                    if (interval != null) {
                        debug.log("%s", interval.logString(this));
                    }
                }

                try (Indent indent2 = debug.logAndIndent("Basic Blocks")) {
                    for (int i = 0; i < blockCount(); i++) {
                        AbstractBlockBase<?> block = blockAt(i);
                        debug.log("B%d [%d, %d, %s] ", block.getId(), getFirstLirInstructionId(block), getLastLirInstructionId(block), block.getLoop());
                    }
                }
            }
        }
        debug.dump(DebugContext.VERBOSE_LEVEL, new LinearScanIntervalDumper(Arrays.copyOf(intervals, intervalsSize)), label);
    }

    boolean verify() {
        // (check that all intervals have a correct register and that no registers are overwritten)
        verifyIntervals();

        verifyRegisters();

        debug.log("no errors found");

        return true;
    }

    @SuppressWarnings("try")
    private void verifyRegisters() {
        // Enable this logging to get output for the verification process.
        try (Indent indent = debug.logAndIndent("verifying register allocation")) {
            RegisterVerifier verifier = new RegisterVerifier(this);
            verifier.verify(blockAt(0));
        }
    }

    @SuppressWarnings("try")
    protected void verifyIntervals() {
        try (Indent indent = debug.logAndIndent("verifying intervals")) {
            int len = intervalsSize;

            for (int i = 0; i < len; i++) {
                Interval i1 = intervals[i];
                if (i1 == null) {
                    continue;
                }

                i1.checkSplitChildren();

                if (i1.operandNumber != i) {
                    debug.log("Interval %d is on position %d in list", i1.operandNumber, i);
                    debug.log(i1.logString(this));
                    throw new GraalError("");
                }

                if (isVariable(i1.operand) && i1.kind().equals(LIRKind.Illegal)) {
                    debug.log("Interval %d has no type assigned", i1.operandNumber);
                    debug.log(i1.logString(this));
                    throw new GraalError("");
                }

                if (i1.location() == null) {
                    debug.log("Interval %d has no register assigned", i1.operandNumber);
                    debug.log(i1.logString(this));
                    throw new GraalError("");
                }

                if (i1.first().isEndMarker()) {
                    debug.log("Interval %d has no Range", i1.operandNumber);
                    debug.log(i1.logString(this));
                    throw new GraalError("");
                }

                for (Range r = i1.first(); !r.isEndMarker(); r = r.next) {
                    if (r.from >= r.to) {
                        debug.log("Interval %d has zero length range", i1.operandNumber);
                        debug.log(i1.logString(this));
                        throw new GraalError("");
                    }
                }

                for (int j = i + 1; j < len; j++) {
                    Interval i2 = intervals[j];
                    if (i2 == null) {
                        continue;
                    }

                    // special intervals that are created in MoveResolver
                    // . ignore them because the range information has no meaning there
                    if (i1.from() == 1 && i1.to() == 2) {
                        continue;
                    }
                    if (i2.from() == 1 && i2.to() == 2) {
                        continue;
                    }
                    Value l1 = i1.location();
                    Value l2 = i2.location();
                    if (i1.intersects(i2) && !isIllegal(l1) && (l1.equals(l2))) {
                        throw GraalError.shouldNotReachHere(String.format("Intervals %d and %d overlap and have the same register assigned\n%s\n%s", i1.operandNumber, i2.operandNumber,
                                        i1.logString(this), i2.logString(this)));
                    }
                }
            }
        }
    }

    class CheckConsumer implements ValueConsumer {

        boolean ok;
        Interval curInterval;

        @Override
        public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (isRegister(operand)) {
                if (intervalFor(operand) == curInterval) {
                    ok = true;
                }
            }
        }
    }

    @SuppressWarnings("try")
    void verifyNoOopsInFixedIntervals() {
        try (Indent indent = debug.logAndIndent("verifying that no oops are in fixed intervals *")) {
            CheckConsumer checkConsumer = new CheckConsumer();

            Interval fixedIntervals;
            Interval otherIntervals;
            fixedIntervals = createUnhandledLists(IS_PRECOLORED_INTERVAL, null).getLeft();
            // to ensure a walking until the last instruction id, add a dummy interval
            // with a high operation id
            otherIntervals = new Interval(Value.ILLEGAL, -1, intervalEndMarker, rangeEndMarker);
            otherIntervals.addRange(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1);
            IntervalWalker iw = new IntervalWalker(this, fixedIntervals, otherIntervals);

            for (AbstractBlockBase<?> block : sortedBlocks) {
                ArrayList<LIRInstruction> instructions = ir.getLIRforBlock(block);

                for (int j = 0; j < instructions.size(); j++) {
                    LIRInstruction op = instructions.get(j);

                    if (op.hasState()) {
                        iw.walkBefore(op.id());
                        boolean checkLive = true;

                        /*
                         * Make sure none of the fixed registers is live across an oopmap since we
                         * can't handle that correctly.
                         */
                        if (checkLive) {
                            for (Interval interval = iw.activeLists.get(RegisterBinding.Fixed); !interval.isEndMarker(); interval = interval.next) {
                                if (interval.currentTo() > op.id() + 1) {
                                    /*
                                     * This interval is live out of this op so make sure that this
                                     * interval represents some value that's referenced by this op
                                     * either as an input or output.
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
        return ir;
    }

    public FrameMapBuilder getFrameMapBuilder() {
        return frameMapBuilder;
    }

    public AbstractBlockBase<?>[] sortedBlocks() {
        return sortedBlocks;
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

}
