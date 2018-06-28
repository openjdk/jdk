/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.alloc.trace.bu;

import static jdk.vm.ci.code.ValueUtil.asAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig.AllocatableRegisters;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.RedundantMoveElimination;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.alloc.OutOfRegistersException;
import org.graalvm.compiler.lir.alloc.trace.GlobalLivenessInfo;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase;
import org.graalvm.compiler.lir.alloc.trace.TraceAllocationPhase.TraceAllocationContext;
import org.graalvm.compiler.lir.alloc.trace.TraceGlobalMoveResolutionPhase;
import org.graalvm.compiler.lir.alloc.trace.TraceGlobalMoveResolver;
import org.graalvm.compiler.lir.alloc.trace.TraceRegisterAllocationPhase;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;
import org.graalvm.compiler.lir.ssa.SSAUtil;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

/**
 * Allocates registers within a trace in a greedy, bottom-up fashion. The liveness information is
 * computed on the fly as the instructions are traversed instead of computing it in a separate pass.
 * The goal of this allocator is to provide a simple and fast algorithm for situations where code
 * quality is not the primary target.
 *
 * This implementation does not (yet) exploit hinting information and might introduce multiple spill
 * moves to the same stack slot (which are likely to be remove by {@link RedundantMoveElimination}.
 *
 * The current implementation cannot deal with {@link AbstractBlockBase blocks} with edges to
 * compiled exception handlers since it might introduce spill code after the {@link LIRInstruction
 * instruction} that triggers the exception.
 */
public final class BottomUpAllocator extends TraceAllocationPhase<TraceAllocationContext> {
    private final TargetDescription target;
    private final LIRGenerationResult lirGenRes;
    private final MoveFactory spillMoveFactory;
    private final RegisterAllocationConfig registerAllocationConfig;
    private final RegisterArray callerSaveRegs;
    private final RegisterAttributes[] registerAttributes;
    private final BitSet allocatedBlocks;
    private final TraceBuilderResult resultTraces;
    private final TraceGlobalMoveResolver moveResolver;
    private final DebugContext debug;

    /**
     * Maps from {@link Variable#index} to a spill stack slot. If
     * {@linkplain org.graalvm.compiler.lir.alloc.trace.TraceRegisterAllocationPhase.Options#TraceRACacheStackSlots
     * enabled} a {@link Variable} is always assigned to the same stack slot.
     */
    private final AllocatableValue[] stackSlots;

    private final ArrayList<LIRInstruction> insertInstructionsBefore;
    private final ArrayList<LIRInstruction> insertInstructionsAfter;
    private final boolean neverSpillConstants;

    private final GlobalLivenessInfo livenessInfo;

    public BottomUpAllocator(TargetDescription target, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig,
                    AllocatableValue[] cachedStackSlots, TraceBuilderResult resultTraces, boolean neverSpillConstant, GlobalLivenessInfo livenessInfo) {
        this.target = target;
        this.debug = lirGenRes.getLIR().getDebug();
        this.lirGenRes = lirGenRes;
        this.spillMoveFactory = spillMoveFactory;
        this.registerAllocationConfig = registerAllocationConfig;
        this.callerSaveRegs = registerAllocationConfig.getRegisterConfig().getCallerSaveRegisters();
        this.registerAttributes = registerAllocationConfig.getRegisterConfig().getAttributesMap();
        this.allocatedBlocks = new BitSet(lirGenRes.getLIR().getControlFlowGraph().getBlocks().length);
        this.resultTraces = resultTraces;
        this.moveResolver = new TraceGlobalMoveResolver(lirGenRes, spillMoveFactory, registerAllocationConfig, target.arch);
        this.neverSpillConstants = neverSpillConstant;
        this.livenessInfo = livenessInfo;

        this.insertInstructionsBefore = new ArrayList<>(4);
        this.insertInstructionsAfter = new ArrayList<>(4);

        if (TraceRegisterAllocationPhase.Options.TraceRACacheStackSlots.getValue(lirGenRes.getLIR().getOptions())) {
            this.stackSlots = cachedStackSlots;
        } else {
            this.stackSlots = new AllocatableValue[lirGenRes.getLIR().numVariables()];
        }

    }

    private LIR getLIR() {
        return lirGenRes.getLIR();
    }

    /**
     * Gets an object describing the attributes of a given register according to this register
     * configuration.
     */
    private RegisterAttributes attributes(Register reg) {
        return registerAttributes[reg.number];
    }

    /**
     * Returns a new spill slot or a cached entry if there is already one for the variable.
     */
    private AllocatableValue allocateSpillSlot(Variable var) {
        int variableIndex = var.index;
        AllocatableValue cachedStackSlot = stackSlots[variableIndex];
        if (cachedStackSlot != null) {
            TraceRegisterAllocationPhase.globalStackSlots.increment(debug);
            assert cachedStackSlot.getValueKind().equals(var.getValueKind()) : "CachedStackSlot: kind mismatch? " + var.getValueKind() + " vs. " + cachedStackSlot.getValueKind();
            return cachedStackSlot;
        }
        VirtualStackSlot slot = lirGenRes.getFrameMapBuilder().allocateSpillSlot(var.getValueKind());
        stackSlots[variableIndex] = slot;
        TraceRegisterAllocationPhase.allocatedStackSlots.increment(debug);
        return slot;
    }

    @Override
    protected void run(@SuppressWarnings("hiding") TargetDescription target, @SuppressWarnings("hiding") LIRGenerationResult lirGenRes, Trace trace, TraceAllocationContext context) {
        allocate(trace);
    }

    private void allocate(Trace trace) {
        if (neverSpillConstants) {
            throw JVMCIError.unimplemented("NeverSpillConstant not supported!");
        }
        new Allocator().allocateTrace(trace);
        assert verify(trace);
    }

    private boolean verify(Trace trace) {
        for (AbstractBlockBase<?> block : trace.getBlocks()) {
            assert LIR.verifyBlock(lirGenRes.getLIR(), block);
        }
        return true;
    }

    private static boolean requiresRegisters(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        assert isVariable(value) : "Not a variable " + value;
        if (instruction instanceof LabelOp) {
            // phi and incoming values do not require a register
            return false;
        }
        if (mode == OperandMode.DEF || mode == OperandMode.TEMP) {
            return true;
        }
        return !flags.contains(OperandFlag.STACK);
    }

    private void resolveFindInsertPos(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock) {
        LIR lir = lirGenRes.getLIR();
        if (fromBlock.getSuccessorCount() <= 1) {
            if (debug.isLogEnabled()) {
                debug.log("inserting moves at end of fromBlock B%d", fromBlock.getId());
            }

            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(fromBlock);
            LIRInstruction instr = instructions.get(instructions.size() - 1);
            if (instr instanceof StandardOp.JumpOp) {
                // insert moves before branch
                moveResolver.setInsertPosition(instructions, instructions.size() - 1);
            } else {
                moveResolver.setInsertPosition(instructions, instructions.size());
            }

        } else {
            if (debug.isLogEnabled()) {
                debug.log("inserting moves at beginning of toBlock B%d", toBlock.getId());
            }

            if (Assertions.detailedAssertionsEnabled(getLIR().getOptions())) {
                assert lir.getLIRforBlock(fromBlock).get(0) instanceof StandardOp.LabelOp : "block does not start with a label";

                /*
                 * Because the number of predecessor edges matches the number of successor edges,
                 * blocks which are reached by switch statements may have be more than one
                 * predecessor but it will be guaranteed that all predecessors will be the same.
                 */
                for (AbstractBlockBase<?> predecessor : toBlock.getPredecessors()) {
                    assert fromBlock == predecessor : "all critical edges must be broken";
                }
            }

            moveResolver.setInsertPosition(lir.getLIRforBlock(toBlock), 1);
        }
    }

    private final class Allocator {

        /**
         * Maps from {@linkplain Register#number register} to the current {@linkplain Variable
         * variable}.
         */
        private final AllocatableValue[] currentRegisterMapping;
        /**
         * Maps from {@linkplain Variable#index variable} to its current location.
         */
        private final AllocatableValue[] locations;

        private final int[] lastRegisterUsage;
        private final int[] lastRegisterKill;

        private ArrayList<LIRInstruction> currentInstructions;
        private int currentInstructionIndex;
        private int currentOpId;

        private Allocator() {
            RegisterArray registers = target.arch.getRegisters();
            int numRegs = registers.size();
            int numVar = getLIR().numVariables();
            currentRegisterMapping = new AllocatableValue[numRegs];
            lastRegisterUsage = new int[numRegs];
            lastRegisterKill = new int[numRegs];
            locations = new AllocatableValue[numVar];
            // we start at offset 2 to distinguish if from the default value
            currentOpId = 2;
        }

        private void setCurrentValue(Register reg, AllocatableValue val) {
            currentRegisterMapping[reg.number] = val;
        }

        private AllocatableValue getCurrentValue(Register reg) {
            return currentRegisterMapping[reg.number];
        }

        private int getLastRegisterUsage(Register reg) {
            return lastRegisterUsage[reg.number];
        }

        private void setLastRegisterUsage(Register reg, int pos) {
            debug.log("Register %s last used %d", reg, pos);
            lastRegisterUsage[reg.number] = pos;
        }

        private int getLastRegisterKill(Register reg) {
            return lastRegisterKill[reg.number];
        }

        private void setLastRegisterKill(Register reg, int pos) {
            debug.log("Register %s killed %d", reg, pos);
            lastRegisterKill[reg.number] = pos;
        }

        private void setCurrentLocation(Variable var, AllocatableValue location) {
            locations[var.index] = location;
        }

        private AllocatableValue getCurrentLocation(Variable var) {
            return locations[var.index];
        }

        private void insertSpillMoveBefore(AllocatableValue dst, Value src) {
            LIRInstruction move = spillMoveFactory.createMove(dst, src);
            insertInstructionsBefore.add(move);
            move.setComment(lirGenRes, "BottomUp: spill move before");
            debug.log("insert before %s", move);
        }

        private void insertSpillMoveAfter(AllocatableValue dst, Value src) {
            LIRInstruction inst = currentInstructions.get(currentInstructionIndex);
            if (!(inst instanceof BlockEndOp)) {
                LIRInstruction move = spillMoveFactory.createMove(dst, src);
                insertInstructionsAfter.add(move);
                move.setComment(lirGenRes, "BottomUp: spill move after");
                debug.log("insert after %s", move);
            } else {
                debug.log("Block end op. No from %s to %s necessary.", src, dst);
                requireResolution = true;
            }
        }

        private void insertInstructions() {
            // TODO (je) this is can probably be improved
            currentInstructions.ensureCapacity(currentInstructions.size() + insertInstructionsBefore.size() + insertInstructionsAfter.size());
            LIRInstruction inst = currentInstructions.get(currentInstructionIndex);
            // insert after
            if (insertInstructionsAfter.size() != 0) {
                Collections.reverse(insertInstructionsAfter);
                assert !(inst instanceof BlockEndOp) : "Cannot insert instruction after the block end op: " + inst;
                currentInstructions.addAll(currentInstructionIndex + 1, insertInstructionsAfter);
                insertInstructionsAfter.clear();
            }
            // insert before
            if (insertInstructionsBefore.size() != 0) {
                assert !(inst instanceof LabelOp) : "Cannot insert instruction before the label op: " + inst;
                currentInstructions.addAll(currentInstructionIndex, insertInstructionsBefore);
                insertInstructionsBefore.clear();
            }
        }

        @SuppressWarnings("try")
        private void allocateTrace(Trace trace) {
            try (DebugContext.Scope s = debug.scope("BottomUpAllocator", trace.getBlocks()); Indent indent = debug.logAndIndent("%s (Trace%d)", trace, trace.getId())) {
                AbstractBlockBase<?>[] blocks = trace.getBlocks();
                int lastBlockIdx = blocks.length - 1;
                AbstractBlockBase<?> successorBlock = blocks[lastBlockIdx];
                // handle last block
                allocateBlock(successorBlock);
                // handle remaining blocks
                for (int i = lastBlockIdx - 1; i >= 0; i--) {
                    AbstractBlockBase<?> block = blocks[i];
                    // handle PHIs
                    resolvePhis(block, successorBlock);
                    boolean needResolution = allocateBlock(block);

                    if (needResolution) {
                        // resolve local data flow
                        resolveIntraTraceEdge(block, successorBlock);
                    }
                    successorBlock = block;
                }
                resolveLoopBackEdge(trace);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }

        private final ArrayList<LIRInstruction> phiResolutionMoves = new ArrayList<>();

        /**
         * Resolve phi values, i.e., set the current location of values in the predecessors block
         * (which is not yet allocated) to the location of the variable defined by the phi in the
         * successor (which is already allocated). For constant inputs we insert moves.
         */
        private void resolvePhis(AbstractBlockBase<?> from, AbstractBlockBase<?> to) {
            if (SSAUtil.isMerge(to)) {
                JumpOp jump = SSAUtil.phiOut(getLIR(), from);
                LabelOp label = SSAUtil.phiIn(getLIR(), to);

                assert phiResolutionMoves.isEmpty();

                for (int i = 0; i < label.getPhiSize(); i++) {
                    visitPhiValuePair(jump.getOutgoingValue(i), label.getIncomingValue(i));
                }
                if (!phiResolutionMoves.isEmpty()) {
                    ArrayList<LIRInstruction> instructions = getLIR().getLIRforBlock(from);
                    instructions.addAll(instructions.size() - 1, phiResolutionMoves);
                    phiResolutionMoves.clear();
                }
            }
        }

        private void visitPhiValuePair(Value phiOut, Value phiIn) {
            assert isStackSlotValue(phiIn) || isRegister(phiIn) : "PHI defined values is not a register or stack slot: " + phiIn;
            AllocatableValue in = asAllocatableValue(phiIn);

            AllocatableValue dest = isRegister(in) ? getCurrentValue(asRegister(in)) : in;
            final LIRInstruction move;
            if (isConstantValue(phiOut)) {
                // insert move from constant
                move = spillMoveFactory.createLoad(dest, LIRValueUtil.asConstant(phiOut));
            } else {
                assert isVariable(phiOut) : "Not a variable or constant: " + phiOut;
                // insert move from variable
                move = spillMoveFactory.createMove(dest, asVariable(phiOut));
            }
            debug.log("Inserting load %s", move);
            move.setComment(lirGenRes, "BottomUp: phi resolution");
            phiResolutionMoves.add(move);
        }

        private boolean requireResolution;

        /**
         * Intra-trace edges, i.e., edge where both, the source and the target block are in the same
         * trace, are either
         * <ul>
         * <li><em>immediate forward edges</em>, i.e., an edge from {@code i}th block of the trace
         * to the {@code (i+1)}th block, or
         * <li>a <em>loop back-edge</em> from the last block of the trace to the loop header.
         * </ul>
         * This property is guaranteed due to splitting of <em>critical edge</em>.
         *
         * Since forward edges are handled locally during bottom-up allocation we only need to check
         * for the second case.
         */
        private void resolveLoopBackEdge(Trace trace) {
            AbstractBlockBase<?>[] blocks = trace.getBlocks();
            AbstractBlockBase<?> endBlock = blocks[blocks.length - 1];
            if (endBlock.isLoopEnd()) {
                assert endBlock.getSuccessorCount() == 1;
                AbstractBlockBase<?> targetBlock = endBlock.getSuccessors()[0];
                assert targetBlock.isLoopHeader() : String.format("Successor %s or loop end %s is not a loop header?", targetBlock, endBlock);
                if (resultTraces.getTraceForBlock(targetBlock).equals(trace)) {
                    resolveLoopBackEdge(endBlock, targetBlock);
                }
            }
        }

        private void resolveLoopBackEdge(AbstractBlockBase<?> from, AbstractBlockBase<?> to) {
            assert resultTraces.getTraceForBlock(from).equals(resultTraces.getTraceForBlock(to)) : "Not on the same trace? " + from + " -> " + to;
            resolveFindInsertPos(from, to);
            LIR lir = getLIR();

            if (SSAUtil.isMerge(to)) {
                JumpOp blockEnd = SSAUtil.phiOut(lir, from);
                LabelOp label = SSAUtil.phiIn(lir, to);

                for (int i = 0; i < label.getPhiSize(); i++) {
                    Value incomingValue = label.getIncomingValue(i);
                    Value outgoingValue = blockEnd.getOutgoingValue(i);
                    resolveValuePair(incomingValue, outgoingValue);
                }
            }
            resolveTraceEdge(from, to);
            moveResolver.resolveAndAppendMoves();
        }

        private void resolveIntraTraceEdge(AbstractBlockBase<?> from, AbstractBlockBase<?> to) {
            assert resultTraces.getTraceForBlock(from).equals(resultTraces.getTraceForBlock(to)) : "Not on the same trace? " + from + " -> " + to;
            resolveFindInsertPos(from, to);
            resolveTraceEdge(from, to);
            moveResolver.resolveAndAppendMoves();
        }

        private void resolveTraceEdge(AbstractBlockBase<?> from, AbstractBlockBase<?> to) {
            Value[] out = livenessInfo.getOutLocation(from);
            Value[] in = livenessInfo.getInLocation(to);

            assert out != null;
            assert in != null;
            assert out.length == in.length;

            for (int i = 0; i < out.length; i++) {
                Value incomingValue = in[i];
                Value outgoingValue = out[i];
                resolveValuePair(incomingValue, outgoingValue);
            }
        }

        private void resolveValuePair(Value incomingValue, Value outgoingValue) {
            if (!isIllegal(incomingValue) && !TraceGlobalMoveResolver.isMoveToSelf(outgoingValue, incomingValue)) {
                TraceGlobalMoveResolutionPhase.addMapping(moveResolver, outgoingValue, incomingValue);
            }
        }

        /**
         * @return {@code true} if the block requires data-flow resolution.
         */
        @SuppressWarnings("try")
        private boolean allocateBlock(AbstractBlockBase<?> block) {
            // might be set in insertSpillMoveAfter
            requireResolution = false;

            try (Indent indent = debug.logAndIndent("handle block %s", block)) {
                currentInstructions = getLIR().getLIRforBlock(block);
                final int lastInstIdx = currentInstructions.size() - 1;
                for (currentInstructionIndex = lastInstIdx; currentInstructionIndex >= 0; currentInstructionIndex--) {
                    LIRInstruction inst = currentInstructions.get(currentInstructionIndex);
                    if (inst != null) {
                        inst.setId(currentOpId);
                        allocateInstruction(inst, block, currentInstructionIndex == 0, currentInstructionIndex == lastInstIdx);
                    }
                }
                allocatedBlocks.set(block.getId());
            }
            return requireResolution;
        }

        @SuppressWarnings("try")
        private void allocateInstruction(LIRInstruction op, AbstractBlockBase<?> block, boolean isLabel, boolean isBlockEnd) {
            assert op != null && op.id() == currentOpId;
            try (Indent indent = debug.logAndIndent("handle inst: %d: %s", op.id(), op)) {
                try (Indent indent1 = debug.logAndIndent("output pos")) {
                    // spill caller saved registers
                    if (op.destroysCallerSavedRegisters()) {
                        spillCallerSavedRegisters();
                    }

                    // fixed
                    op.forEachOutput(allocFixedRegisterProcedure);
                    op.forEachTemp(allocFixedRegisterProcedure);
                    op.forEachAlive(allocFixedRegisterProcedure);
                    // variable
                    op.forEachOutput(allocRegisterProcedure);
                    op.forEachTemp(allocRegisterProcedure);
                    op.forEachAlive(allocRegisterProcedure);
                    /* state do never require a register */
                    // op.forEachState(allocRegisterProcedure);

                    // should have
                    op.forEachTemp(allocStackOrRegisterProcedure);
                    op.forEachOutput(allocStackOrRegisterProcedure);
                    if (isLabel) {
                        assert op instanceof LabelOp;
                        processIncoming(block, op);
                    }
                }
                try (Indent indent1 = debug.logAndIndent("input pos")) {

                    currentOpId++;

                    // fixed
                    op.forEachInput(allocFixedRegisterProcedure);
                    // variable
                    op.forEachInput(allocRegisterProcedure);

                    op.forEachAlive(allocStackOrRegisterProcedure);
                    if (isBlockEnd) {
                        assert op instanceof BlockEndOp;
                        processOutgoing(block, op);
                    }
                    op.forEachState(allocStackOrRegisterProcedure);
                    op.forEachInput(allocStackOrRegisterProcedure);
                }

                // insert spill/load instructions
                insertInstructions();
                currentOpId++;
            }
        }

        private void processIncoming(AbstractBlockBase<?> block, LIRInstruction instruction) {
            int[] vars = livenessInfo.getBlockIn(block);
            Value[] locs = new Value[vars.length];
            for (int i = 0; i < vars.length; i++) {
                int varNum = vars[i];
                if (varNum >= 0) {
                    locs[i] = allocStackOrRegister(instruction, livenessInfo.getVariable(varNum), OperandMode.DEF, LabelOp.incomingFlags);
                }
            }
            livenessInfo.setInLocations(block, locs);
        }

        private void processOutgoing(AbstractBlockBase<?> block, LIRInstruction instruction) {
            int[] vars = livenessInfo.getBlockOut(block);
            Value[] locs = new Value[vars.length];
            for (int i = 0; i < vars.length; i++) {
                locs[i] = allocStackOrRegister(instruction, livenessInfo.getVariable(vars[i]), OperandMode.ALIVE, JumpOp.outgoingFlags);
            }
            livenessInfo.setOutLocations(block, locs);
        }

        private void spillCallerSavedRegisters() {
            for (Register reg : callerSaveRegs) {
                if (attributes(reg).isAllocatable()) {
                    evacuateRegisterAndSpill(reg);
                    assert checkRegisterUsage(reg);
                    setLastRegisterUsage(reg, currentOpId);
                }
            }
            if (debug.isLogEnabled()) {
                debug.log("operation destroys all caller-save registers");
            }
        }

        private final InstructionValueProcedure allocFixedRegisterProcedure = new InstructionValueProcedure() {
            @Override
            public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                return allocFixedRegister(value);
            }
        };
        private final InstructionValueProcedure allocRegisterProcedure = new InstructionValueProcedure() {
            @Override
            public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                return allocRegister(instruction, value, mode, flags);
            }
        };
        private final InstructionValueProcedure allocStackOrRegisterProcedure = new InstructionValueProcedure() {
            @Override
            public Value doValue(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                return allocStackOrRegister(instruction, value, mode, flags);
            }
        };

        private Value allocFixedRegister(Value value) {
            if (isRegister(value)) {
                Register reg = asRegister(value);
                assert checkRegisterUsage(reg);
                evacuateRegisterAndSpill(reg);
                setRegisterUsage(reg, asAllocatableValue(value));
            }
            return value;
        }

        private Value allocRegister(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (isVariable(value) && requiresRegisters(instruction, value, mode, flags)) {
                Variable var = asVariable(value);
                // check if available
                AllocatableValue currentLocation = getCurrentLocation(var);
                if (currentLocation == null) {
                    // nothing yet assigned
                    return allocRegister(var, mode);
                }
                // already a location assigned
                if (isRegister(currentLocation)) {
                    // register assigned -> nothing todo
                    setLastRegisterUsage(asRegister(currentLocation), currentOpId);
                    return currentLocation;
                }
                assert isStackSlotValue(currentLocation);
                // stackSlot assigned but need register -> spill
                Value allocatedRegister = allocRegister(var, mode);
                if (mode == OperandMode.USE) {
                    // input might be destroyed at the def position
                    // but it must be available before the instruction
                    insertSpillMoveBefore(currentLocation, allocatedRegister);
                } else {
                    insertSpillMoveAfter(currentLocation, allocatedRegister);
                }
                return allocatedRegister;
            }
            return value;
        }

        private Value allocRegister(Variable var, OperandMode mode) {
            PlatformKind platformKind = var.getPlatformKind();
            Register freeRegister = findFreeRegister(platformKind, mode);
            if (freeRegister == null) {
                // no free register found, looking for a blocked one
                freeRegister = findLockedRegister(platformKind, mode);
                if (freeRegister == null) {
                    throw new OutOfRegistersException("TraceRA[BottomUp]: no register found");
                }
            }
            // found a register
            setRegisterUsage(freeRegister, var);
            RegisterValue registerValue = freeRegister.asValue(var.getValueKind());
            setCurrentLocation(var, registerValue);
            debug.log("AllocateRegister[%5s] %s for %s", mode, freeRegister, var);
            return registerValue;
        }

        private Value allocStackOrRegister(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (isRegister(value)) {
                if ((mode == OperandMode.DEF || mode == OperandMode.TEMP) && !(instruction instanceof LabelOp)) {
                    freeRegister(asRegister(value));
                }
                return value;
            }
            if (isVariable(value)) {
                assert !requiresRegisters(instruction, value, mode, flags) : "Should have a register already: " + value;
                Variable var = asVariable(value);
                // check if available
                AllocatableValue currentLocation = getCurrentLocation(var);
                if (currentLocation != null) {
                    // already a location assigned -> nothing todo
                    if (isRegister(currentLocation)) {
                        Register reg = asRegister(currentLocation);
                        if (mode == OperandMode.ALIVE && killedAtDef(reg)) {
                            AllocatableValue spillSlot = allocateSpillSlot(var);
                            insertSpillMoveBefore(spillSlot, currentLocation);
                            debug.log("AllocateStackOrReg[%5s] temporary use %s for %s since current location %s is destroyed at def", mode, spillSlot, var, currentLocation);
                            return spillSlot;
                        }
                        // update register usage
                        setLastRegisterUsage(reg, currentOpId);
                    }
                    debug.log(3, "AllocateStackOrReg[%5s] %s already in %s", mode, var, currentLocation);
                    return currentLocation;
                }
                // no location available
                PlatformKind platformKind = var.getPlatformKind();
                Register freeRegister = findFreeRegister(platformKind, mode);
                if (freeRegister == null) {
                    // no free register available -> either spill current or free a register
                    AllocatableValue spillSlot = allocateSpillSlot(var);
                    setCurrentLocation(var, spillSlot);
                    return spillSlot;
                }
                assert freeRegister != null;
                // found a register
                setRegisterUsage(freeRegister, var);
                RegisterValue registerValue = freeRegister.asValue(var.getValueKind());
                setCurrentLocation(var, registerValue);
                debug.log("AllocateStackOrReg[%5s] %s for %s", mode, freeRegister, var);
                return registerValue;
            }
            return value;
        }

        private boolean killedAtDef(Register reg) {
            return getLastRegisterKill(reg) == currentOpId - 1;
        }

        /**
         * Searches for a free register.
         */
        @SuppressWarnings("try")
        private Register findFreeRegister(PlatformKind kind, OperandMode mode) {
            AllocatableRegisters allocatableRegisters = registerAllocationConfig.getAllocatableRegisters(kind);
            Register[] availableRegs = allocatableRegisters.allocatableRegisters;
            for (Register reg : availableRegs) {
                AllocatableValue currentVal = getCurrentValue(reg);
                if (currentVal == null && !isCurrentlyUsed(reg, mode)) {
                    return reg;
                }
            }
            if (debug.isLogEnabled()) {
                try (Indent i = debug.logAndIndent("All Registers occupied:")) {
                    for (Register reg : availableRegs) {
                        debug.log("%6s: last used %4d %s", reg, getLastRegisterUsage(reg), getCurrentValue(reg));
                    }
                }
            }
            return null;
        }

        /**
         * Searches for a occupied register to spill.
         */
        @SuppressWarnings("try")
        private Register findLockedRegister(PlatformKind kind, OperandMode mode) {
            AllocatableRegisters allocatableRegisters = registerAllocationConfig.getAllocatableRegisters(kind);
            Register[] availableRegs = allocatableRegisters.allocatableRegisters;
            // TODO (je): better strategies for spilling
            // TODO (je): we need to ensure that we do not use the register in the current
            // instruction!
            Register lockedReg = null;
            for (Register reg : availableRegs) {
                if (!isCurrentlyUsed(reg, mode) && !isActiveFixedRegister(reg)) {
                    lockedReg = reg;
                    break;
                }
            }
            if (lockedReg == null) {
                return null;
            }
            evacuateRegisterAndSpill(lockedReg);
            return lockedReg;
        }

        private boolean isActiveFixedRegister(Register reg) {
            return isRegister(getCurrentValue(reg));
        }

        private boolean isCurrentlyUsed(Register reg, OperandMode mode) {
            int lastRegUsage = getLastRegisterUsage(reg);
            if (lastRegUsage == currentOpId) {
                return true;
            }
            return mode == OperandMode.ALIVE && lastRegUsage == (currentOpId & ~1);
        }

        private void freeRegister(Register reg) {
            AllocatableValue val = getCurrentValue(reg);
            setCurrentValue(reg, null);
            setLastRegisterKill(reg, currentOpId);
            if (val != null && isVariable(val)) {
                Variable var = asVariable(val);
                setCurrentLocation(var, null);
                debug.log("Free Registers %s (was %s)", reg, var);
            } else {
                debug.log("Free Registers %s", reg);
            }
        }

        private void setRegisterUsage(Register reg, AllocatableValue currentValue) {
            assert checkRegisterUsage(reg);
            setCurrentValue(reg, currentValue);
            setLastRegisterUsage(reg, currentOpId);
        }

        private boolean checkRegisterUsage(Register reg) {
            AllocatableValue currentValue = getCurrentValue(reg);
            assert getLastRegisterUsage(reg) < currentOpId || currentValue == null || isRegister(currentValue) && asRegister(currentValue).equals(reg) : String.format("Register %s is occupied", reg);
            return true;
        }

        /**
         * Frees a registers and spill the variable that is currently occupying it.
         *
         * @return The value that currently occupies the register or {@code null} if there is none.
         */
        private AllocatableValue evacuateRegisterAndSpill(Register reg) {
            AllocatableValue val = evacuateRegister(reg);
            spillVariable(val, reg);
            return val;
        }

        /**
         * Frees a registers. The variable that is currently occupying it is <em>not</em> spilled.
         *
         * @return The value that currently occupies the register or {@code null} if there is none.
         */
        private AllocatableValue evacuateRegister(Register reg) {
            AllocatableValue val = getCurrentValue(reg);
            if (val == null) {
                return null;
            }
            setCurrentValue(reg, null);
            return val;
        }

        private void spillVariable(AllocatableValue val, Register reg) {
            if (val != null && isVariable(val)) {
                Variable var = asVariable(val);
                debug.log("Spill Variable %s from %s", var, reg);
                // insert reload
                AllocatableValue spillSlot = allocateSpillSlot(var);
                setCurrentLocation(var, spillSlot);
                insertSpillMoveAfter(reg.asValue(var.getValueKind()), spillSlot);
            }
        }
    }
}
