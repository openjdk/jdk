/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.sparc;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.sparc.SPARC.g0;
import static jdk.vm.ci.sparc.SPARC.g5;
import static jdk.vm.ci.sparc.SPARC.i0;
import static jdk.vm.ci.sparc.SPARC.i7;
import static jdk.vm.ci.sparc.SPARC.l0;
import static jdk.vm.ci.sparc.SPARC.l7;
import static jdk.vm.ci.sparc.SPARC.o0;
import static jdk.vm.ci.sparc.SPARC.o7;
import static jdk.vm.ci.sparc.SPARC.sp;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BPCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isGlobalRegister;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.NOT_ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Xcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.NotEqual;
import static org.graalvm.compiler.core.common.GraalOptions.ZapStackOnMethodEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.DataSection;
import org.graalvm.compiler.code.DataSection.Data;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.core.sparc.SPARCNodeMatchRules;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotDataBuilder;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotHostBackend;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerationResult;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.sparc.SPARCCall;
import org.graalvm.compiler.lir.sparc.SPARCDelayedControlTransfer;
import org.graalvm.compiler.lir.sparc.SPARCFrameMap;
import org.graalvm.compiler.lir.sparc.SPARCFrameMapBuilder;
import org.graalvm.compiler.lir.sparc.SPARCLIRInstructionMixin;
import org.graalvm.compiler.lir.sparc.SPARCLIRInstructionMixin.SizeEstimate;
import org.graalvm.compiler.lir.sparc.SPARCTailDelayedLIRInstruction;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot SPARC specific backend.
 */
public class SPARCHotSpotBackend extends HotSpotHostBackend {

    private static final SizeEstimateStatistics CONSTANT_ESTIMATED_STATS = new SizeEstimateStatistics("ESTIMATE");
    private static final SizeEstimateStatistics CONSTANT_ACTUAL_STATS = new SizeEstimateStatistics("ACTUAL");

    public SPARCHotSpotBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(config, runtime, providers);
    }

    private static class SizeEstimateStatistics {
        private static final ConcurrentHashMap<String, CounterKey> counters = new ConcurrentHashMap<>();
        private final String suffix;

        SizeEstimateStatistics(String suffix) {
            super();
            this.suffix = suffix;
        }

        public void add(Class<?> c, int count, DebugContext debug) {
            String name = SizeEstimateStatistics.class.getSimpleName() + "_" + c.getSimpleName() + "." + suffix;
            CounterKey m = counters.computeIfAbsent(name, (n) -> DebugContext.counter(n));
            m.add(debug, count);
        }
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new SPARCFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfigNonNull);
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new SPARCFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        return new SPARCHotSpotLIRGenerator(getProviders(), getRuntime().getVMConfig(), lirGenRes);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, StructuredGraph graph, Object stub) {
        return new HotSpotLIRGenerationResult(compilationId, lir, frameMapBuilder, makeCallingConvention(graph, (Stub) stub), stub, config.requiresReservedStackCheck(graph.getMethods()));
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new SPARCHotSpotNodeLIRBuilder(graph, lirGen, new SPARCNodeMatchRules(lirGen));
    }

    @Override
    protected void bangStackWithOffset(CompilationResultBuilder crb, int bangOffset) {
        // Use SPARCAddress to get the final displacement including the stack bias.
        SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
        SPARCAddress address = new SPARCAddress(sp, -bangOffset);
        if (SPARCAssembler.isSimm13(address.getDisplacement())) {
            masm.stx(g0, address);
        } else {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                assert isGlobalRegister(scratch) : "Only global (g1-g7) registers are allowed if the frame was not initialized here. Got register " + scratch;
                masm.setx(address.getDisplacement(), scratch, false);
                masm.stx(g0, new SPARCAddress(sp, scratch));
            }
        }
    }

    public class HotSpotFrameContext implements FrameContext {

        final boolean isStub;

        HotSpotFrameContext(boolean isStub) {
            this.isStub = isStub;
        }

        @Override
        public boolean hasFrame() {
            return true;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            final int frameSize = crb.frameMap.totalFrameSize();
            final int stackpoinerChange = -frameSize;
            SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
            if (!isStub) {
                emitStackOverflowCheck(crb);
            }

            if (SPARCAssembler.isSimm13(stackpoinerChange)) {
                masm.save(sp, stackpoinerChange, sp);
            } else {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch = sc.getRegister();
                    assert isGlobalRegister(scratch) : "Only global registers are allowed before save. Got register " + scratch;
                    masm.setx(stackpoinerChange, scratch, false);
                    masm.save(sp, scratch, sp);
                }
            }

            if (ZapStackOnMethodEntry.getValue(crb.getOptions())) {
                final int slotSize = 8;
                for (int i = 0; i < frameSize / slotSize; ++i) {
                    // 0xC1C1C1C1
                    masm.stx(g0, new SPARCAddress(sp, i * slotSize));
                }
            }
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
            masm.restoreWindow();
        }
    }

    @Override
    protected Assembler createAssembler(FrameMap frameMap) {
        return new SPARCMacroAssembler(getTarget());
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        HotSpotLIRGenerationResult gen = (HotSpotLIRGenerationResult) lirGenRes;
        LIR lir = gen.getLIR();
        assert gen.getDeoptimizationRescueSlot() == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";

        Stub stub = gen.getStub();
        Assembler masm = createAssembler(frameMap);
        // On SPARC we always use stack frames.
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null);
        DataBuilder dataBuilder = new HotSpotDataBuilder(getCodeCache().getTarget());
        OptionValues options = lir.getOptions();
        DebugContext debug = lir.getDebug();
        CompilationResultBuilder crb = factory.createBuilder(getProviders().getCodeCache(), getProviders().getForeignCalls(), frameMap, masm, dataBuilder, frameContext, options, debug,
                        compilationResult, Register.None);
        crb.setTotalFrameSize(frameMap.totalFrameSize());
        crb.setMaxInterpreterFrameSize(gen.getMaxInterpreterFrameSize());
        StackSlot deoptimizationRescueSlot = gen.getDeoptimizationRescueSlot();
        if (deoptimizationRescueSlot != null && stub == null) {
            crb.compilationResult.setCustomStackAreaOffset(deoptimizationRescueSlot);
        }

        if (stub != null) {
            // Even on sparc we need to save floating point registers
            EconomicSet<Register> destroyedCallerRegisters = gatherDestroyedCallerRegisters(lir);
            EconomicMap<LIRFrameState, SaveRegistersOp> calleeSaveInfo = gen.getCalleeSaveInfo();
            updateStub(stub, destroyedCallerRegisters, calleeSaveInfo, frameMap);
        }
        assert registerSizePredictionValidator(crb, debug);
        return crb;
    }

    /**
     * Registers a verifier which checks if the LIRInstructions estimate of constants size is
     * greater or equal to the actual one.
     */
    private static boolean registerSizePredictionValidator(final CompilationResultBuilder crb, DebugContext debug) {
        /**
         * Used to hold state between beforeOp and afterOp
         */
        class ValidationState {
            LIRInstruction op;
            final DebugContext debug;
            int constantSizeBefore;

            ValidationState(DebugContext debug) {
                this.debug = debug;
            }

            public void before(LIRInstruction before) {
                assert op == null : "LIRInstruction " + op + " no after call received";
                op = before;
                constantSizeBefore = calculateDataSectionSize(crb.compilationResult.getDataSection());
            }

            public void after(LIRInstruction after) {
                assert after.equals(op) : "Instructions before/after don't match " + op + "/" + after;
                int constantSizeAfter = calculateDataSectionSize(crb.compilationResult.getDataSection());
                int actual = constantSizeAfter - constantSizeBefore;
                if (op instanceof SPARCLIRInstructionMixin) {
                    org.graalvm.compiler.lir.sparc.SPARCLIRInstructionMixin.SizeEstimate size = ((SPARCLIRInstructionMixin) op).estimateSize();
                    assert size != null : "No size prediction available for op: " + op;
                    Class<?> c = op.getClass();
                    CONSTANT_ESTIMATED_STATS.add(c, size.constantSize, debug);
                    CONSTANT_ACTUAL_STATS.add(c, actual, debug);
                    assert size.constantSize >= actual : "Op " + op + " exceeded estimated constant size; predicted: " + size.constantSize + " actual: " + actual;
                } else {
                    assert actual == 0 : "Op " + op + " emitted to DataSection without any estimate.";
                }
                op = null;
                constantSizeBefore = 0;
            }
        }
        final ValidationState state = new ValidationState(debug);
        crb.setOpCallback(op -> state.before(op), op -> state.after(op));
        return true;
    }

    private static int calculateDataSectionSize(DataSection ds) {
        int sum = 0;
        for (Data d : ds) {
            sum += d.getSize();
        }
        return sum;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
        // TODO: (sa) Fold the two traversals into one
        stuffDelayedControlTransfers(lir);
        int constantSize = calculateConstantSize(lir);
        boolean canUseImmediateConstantLoad = constantSize < (1 << 13);
        masm.setImmediateConstantLoad(canUseImmediateConstantLoad);
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.getRegisterConfig();
        Label unverifiedStub = installedCodeOwner == null || installedCodeOwner.isStatic() ? null : new Label();
        for (int i = 0; i < 2; i++) {
            if (i > 0) {
                crb.resetForEmittingCode();
                lir.resetLabels();
                resetDelayedControlTransfers(lir);
            }

            // Emit the prefix
            if (unverifiedStub != null) {
                crb.recordMark(config.MARKID_UNVERIFIED_ENTRY);
                // We need to use JavaCall here because we haven't entered the frame yet.
                CallingConvention cc = regConfig.getCallingConvention(HotSpotCallingConventionType.JavaCall, null, new JavaType[]{getProviders().getMetaAccess().lookupJavaType(Object.class)}, this);
                Register inlineCacheKlass = g5; // see MacroAssembler::ic_call

                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch = sc.getRegister();
                    Register receiver = asRegister(cc.getArgument(0));
                    SPARCAddress src = new SPARCAddress(receiver, config.hubOffset);

                    masm.ldx(src, scratch);
                    masm.cmp(scratch, inlineCacheKlass);
                }
                BPCC.emit(masm, Xcc, NotEqual, NOT_ANNUL, PREDICT_NOT_TAKEN, unverifiedStub);
                masm.nop();  // delay slot
            }

            masm.align(config.codeEntryAlignment);
            crb.recordMark(config.MARKID_OSR_ENTRY);
            crb.recordMark(config.MARKID_VERIFIED_ENTRY);

            // Emit code for the LIR
            crb.emit(lir);
        }
        profileInstructions(lir, crb);

        HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
        HotSpotForeignCallsProvider foreignCalls = getProviders().getForeignCalls();
        if (!frameContext.isStub) {
            crb.recordMark(config.MARKID_EXCEPTION_HANDLER_ENTRY);
            SPARCCall.directCall(crb, masm, foreignCalls.lookupForeignCall(EXCEPTION_HANDLER), null, null);
            crb.recordMark(config.MARKID_DEOPT_HANDLER_ENTRY);
            SPARCCall.directCall(crb, masm, foreignCalls.lookupForeignCall(DEOPTIMIZATION_HANDLER), null, null);
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries
        }

        if (unverifiedStub != null) {
            masm.bind(unverifiedStub);
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                SPARCCall.indirectJmp(crb, masm, scratch, foreignCalls.lookupForeignCall(IC_MISS_HANDLER));
            }
        }
        masm.peephole();
    }

    private static int calculateConstantSize(LIR lir) {
        int size = 0;
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            if (block == null) {
                continue;
            }
            for (LIRInstruction inst : lir.getLIRforBlock(block)) {
                if (inst instanceof SPARCLIRInstructionMixin) {
                    SizeEstimate pred = ((SPARCLIRInstructionMixin) inst).estimateSize();
                    if (pred != null) {
                        size += pred.constantSize;
                    }
                }
            }
        }
        return size;
    }

    private static void resetDelayedControlTransfers(LIR lir) {
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            if (block == null) {
                continue;
            }
            for (LIRInstruction inst : lir.getLIRforBlock(block)) {
                if (inst instanceof SPARCDelayedControlTransfer) {
                    ((SPARCDelayedControlTransfer) inst).resetState();
                }
            }
        }
    }

    /**
     * Fix-up over whole LIR.
     *
     * @see #stuffDelayedControlTransfers(LIR, AbstractBlockBase)
     * @param l
     */
    private static void stuffDelayedControlTransfers(LIR l) {
        for (AbstractBlockBase<?> b : l.codeEmittingOrder()) {
            if (b != null) {
                stuffDelayedControlTransfers(l, b);
            }
        }
    }

    /**
     * Tries to put DelayedControlTransfer instructions and DelayableLIRInstructions together. Also
     * it tries to move the DelayedLIRInstruction to the DelayedControlTransfer instruction, if
     * possible.
     */
    private static void stuffDelayedControlTransfers(LIR l, AbstractBlockBase<?> block) {
        ArrayList<LIRInstruction> instructions = l.getLIRforBlock(block);
        if (instructions.size() >= 2) {
            LIRDependencyAccumulator acc = new LIRDependencyAccumulator();
            SPARCDelayedControlTransfer delayedTransfer = null;
            int delayTransferPosition = -1;
            for (int i = instructions.size() - 1; i >= 0; i--) {
                LIRInstruction inst = instructions.get(i);
                boolean adjacent = delayTransferPosition - i == 1;
                if (!adjacent || inst.destroysCallerSavedRegisters() || leavesRegisterWindow(inst)) {
                    delayedTransfer = null;
                }
                if (inst instanceof SPARCDelayedControlTransfer) {
                    delayedTransfer = (SPARCDelayedControlTransfer) inst;
                    acc.start(inst);
                    delayTransferPosition = i;
                } else if (delayedTransfer != null) {
                    boolean overlap = acc.add(inst);
                    if (!overlap && inst instanceof SPARCTailDelayedLIRInstruction) {
                        // We have found a non overlapping LIR instruction which can be delayed
                        ((SPARCTailDelayedLIRInstruction) inst).setDelayedControlTransfer(delayedTransfer);
                        delayedTransfer = null;
                    }
                }
            }
        }
    }

    private static boolean leavesRegisterWindow(LIRInstruction inst) {
        return inst instanceof SPARCLIRInstructionMixin && ((SPARCLIRInstructionMixin) inst).leavesRegisterWindow();
    }

    /**
     * Accumulates inputs/outputs/temp/alive in a set along we walk back the LIRInstructions and
     * detects, if there is any overlap. In this way LIRInstructions can be detected, which can be
     * moved nearer to the DelayedControlTransfer instruction.
     */
    private static class LIRDependencyAccumulator {
        private final Set<Object> inputs = new HashSet<>(10);
        private boolean overlap = false;

        private final InstructionValueConsumer valueConsumer = (instruction, value, mode, flags) -> {
            Object valueObject = value;
            if (isRegister(value)) { // Canonicalize registers
                valueObject = asRegister(value);
            }
            if (!inputs.add(valueObject)) {
                overlap = true;
            }
        };

        public void start(LIRInstruction initial) {
            inputs.clear();
            overlap = false;
            initial.visitEachInput(valueConsumer);
            initial.visitEachTemp(valueConsumer);
            initial.visitEachAlive(valueConsumer);
        }

        /**
         * Adds the inputs of lir instruction to the accumulator and returns, true if there was any
         * overlap of parameters.
         *
         * @param inst
         * @return true if an overlap was found
         */
        public boolean add(LIRInstruction inst) {
            overlap = false;
            inst.visitEachOutput(valueConsumer);
            inst.visitEachTemp(valueConsumer);
            inst.visitEachInput(valueConsumer);
            inst.visitEachAlive(valueConsumer);
            return overlap;
        }
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new SPARCHotSpotRegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo);
    }

    @Override
    public EconomicSet<Register> translateToCallerRegisters(EconomicSet<Register> calleeRegisters) {
        EconomicSet<Register> callerRegisters = EconomicSet.create(Equivalence.IDENTITY, calleeRegisters.size());
        for (Register register : calleeRegisters) {
            if (l0.number <= register.number && register.number <= l7.number) {
                // do nothing
            } else if (o0.number <= register.number && register.number <= o7.number) {
                // do nothing
            } else if (i0.number <= register.number && register.number <= i7.number) {
                // translate input to output registers
                callerRegisters.add(translateInputToOutputRegister(register));
            } else {
                callerRegisters.add(register);
            }
        }
        return callerRegisters;
    }

    private Register translateInputToOutputRegister(Register register) {
        assert i0.number <= register.number && register.number <= i7.number : "Not an input register " + register;
        return getTarget().arch.getRegisters().get(o0.number + register.number - i0.number);
    }
}
