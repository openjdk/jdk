/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.rbp;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.hotspot.HotSpotBackend.INITIALIZE_KLASS_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_DYNAMIC_INVOKE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_KLASS_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_METHOD_BY_SYMBOL_AND_LOAD_COUNTERS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_STRING_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.meta.HotSpotConstantLoadAction.INITIALIZE;
import static org.graalvm.compiler.hotspot.meta.HotSpotConstantLoadAction.LOAD_COUNTERS;
import static org.graalvm.compiler.hotspot.meta.HotSpotConstantLoadAction.RESOLVE;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.asm.amd64.AMD64Address.Scale;
import org.graalvm.compiler.core.amd64.AMD64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.amd64.AMD64LIRGenerator;
import org.graalvm.compiler.core.amd64.AMD64MoveFactoryBase.BackupSlotProvider;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotDebugInfoBuilder;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerationResult;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.hotspot.HotSpotLockStack;
import org.graalvm.compiler.hotspot.debug.BenchmarkCounters;
import org.graalvm.compiler.hotspot.meta.HotSpotConstantLoadAction;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.NoOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64CCall;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64FrameMapBuilder;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.amd64.AMD64Move.MoveFromRegOp;
import org.graalvm.compiler.lir.amd64.AMD64PrefetchOp;
import org.graalvm.compiler.lir.amd64.AMD64ReadTimestampCounter;
import org.graalvm.compiler.lir.amd64.AMD64RestoreRegistersOp;
import org.graalvm.compiler.lir.amd64.AMD64SaveRegistersOp;
import org.graalvm.compiler.lir.amd64.AMD64VZeroUpper;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotLIRGenerator extends AMD64LIRGenerator implements HotSpotLIRGenerator {

    final GraalHotSpotVMConfig config;
    private HotSpotDebugInfoBuilder debugInfoBuilder;

    protected AMD64HotSpotLIRGenerator(HotSpotProviders providers, GraalHotSpotVMConfig config, LIRGenerationResult lirGenRes) {
        this(providers, config, lirGenRes, new BackupSlotProvider(lirGenRes.getFrameMapBuilder()));
    }

    private AMD64HotSpotLIRGenerator(HotSpotProviders providers, GraalHotSpotVMConfig config, LIRGenerationResult lirGenRes, BackupSlotProvider backupSlotProvider) {
        this(new AMD64HotSpotLIRKindTool(), new AMD64ArithmeticLIRGenerator(null), new AMD64HotSpotMoveFactory(backupSlotProvider), providers, config, lirGenRes);
    }

    protected AMD64HotSpotLIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, moveFactory, providers, lirGenRes);
        assert config.basicLockSize == 8;
        this.config = config;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    @Override
    protected int getMaxVectorSize() {
        return config.maxVectorSize;
    }

    /**
     * Utility for emitting the instruction to save RBP.
     */
    class SaveRbp {

        final NoOp placeholder;

        /**
         * The slot reserved for saving RBP.
         */
        final StackSlot reservedSlot;

        SaveRbp(NoOp placeholder) {
            this.placeholder = placeholder;
            AMD64FrameMapBuilder frameMapBuilder = (AMD64FrameMapBuilder) getResult().getFrameMapBuilder();
            this.reservedSlot = frameMapBuilder.allocateRBPSpillSlot();
        }

        /**
         * Replaces this operation with the appropriate move for saving rbp.
         *
         * @param useStack specifies if rbp must be saved to the stack
         */
        public AllocatableValue finalize(boolean useStack) {
            AllocatableValue dst;
            if (useStack) {
                dst = reservedSlot;
            } else {
                ((AMD64FrameMapBuilder) getResult().getFrameMapBuilder()).freeRBPSpillSlot();
                dst = newVariable(LIRKind.value(AMD64Kind.QWORD));
            }

            placeholder.replace(getResult().getLIR(), new MoveFromRegOp(AMD64Kind.QWORD, dst, rbp.asValue(LIRKind.value(AMD64Kind.QWORD))));
            return dst;
        }
    }

    private SaveRbp saveRbp;

    protected void emitSaveRbp() {
        NoOp placeholder = new NoOp(getCurrentBlock(), getResult().getLIR().getLIRforBlock(getCurrentBlock()).size());
        append(placeholder);
        saveRbp = new SaveRbp(placeholder);
    }

    protected SaveRbp getSaveRbp() {
        return saveRbp;
    }

    /**
     * Helper instruction to reserve a stack slot for the whole method. Note that the actual users
     * of the stack slot might be inserted after stack slot allocation. This dummy instruction
     * ensures that the stack slot is alive and gets a real stack slot assigned.
     */
    private static final class RescueSlotDummyOp extends LIRInstruction {
        public static final LIRInstructionClass<RescueSlotDummyOp> TYPE = LIRInstructionClass.create(RescueSlotDummyOp.class);

        @Alive({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) private AllocatableValue slot;

        RescueSlotDummyOp(FrameMapBuilder frameMapBuilder, LIRKind kind) {
            super(TYPE);
            slot = frameMapBuilder.allocateSpillSlot(kind);
        }

        public AllocatableValue getSlot() {
            return slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    private RescueSlotDummyOp rescueSlotOp;

    private AllocatableValue getOrInitRescueSlot() {
        RescueSlotDummyOp op = getOrInitRescueSlotOp();
        return op.getSlot();
    }

    private RescueSlotDummyOp getOrInitRescueSlotOp() {
        if (rescueSlotOp == null) {
            // create dummy instruction to keep the rescue slot alive
            rescueSlotOp = new RescueSlotDummyOp(getResult().getFrameMapBuilder(), getLIRKindTool().getWordKind());
        }
        return rescueSlotOp;
    }

    /**
     * List of epilogue operations that need to restore RBP.
     */
    List<AMD64HotSpotRestoreRbpOp> epilogueOps = new ArrayList<>(2);

    @Override
    public <I extends LIRInstruction> I append(I op) {
        I ret = super.append(op);
        if (op instanceof AMD64HotSpotRestoreRbpOp) {
            epilogueOps.add((AMD64HotSpotRestoreRbpOp) op);
        }
        return ret;
    }

    @Override
    public VirtualStackSlot getLockSlot(int lockDepth) {
        return getLockStack().makeLockSlot(lockDepth);
    }

    private HotSpotLockStack getLockStack() {
        assert debugInfoBuilder != null && debugInfoBuilder.lockStack() != null;
        return debugInfoBuilder.lockStack();
    }

    private Register findPollOnReturnScratchRegister() {
        RegisterConfig regConfig = getProviders().getCodeCache().getRegisterConfig();
        for (Register r : regConfig.getAllocatableRegisters()) {
            if (!r.equals(regConfig.getReturnRegister(JavaKind.Long)) && !r.equals(AMD64.rbp)) {
                return r;
            }
        }
        throw GraalError.shouldNotReachHere();
    }

    private Register pollOnReturnScratchRegister;

    @Override
    public void emitReturn(JavaKind kind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(kind, input.getValueKind());
            emitMove(operand, input);
        }
        if (pollOnReturnScratchRegister == null) {
            pollOnReturnScratchRegister = findPollOnReturnScratchRegister();
        }
        Register thread = getProviders().getRegisters().getThreadRegister();
        append(new AMD64HotSpotReturnOp(operand, getStub() != null, thread, pollOnReturnScratchRegister, config, getResult().requiresReservedStackAccessCheck()));
    }

    @Override
    public boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return getResult().getStub() != null;
    }

    private LIRFrameState currentRuntimeCallInfo;

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        currentRuntimeCallInfo = info;
        HotSpotForeignCallLinkage hsLinkage = (HotSpotForeignCallLinkage) linkage;
        AMD64 arch = (AMD64) target().arch;
        if (arch.getFeatures().contains(AMD64.CPUFeature.AVX) && hsLinkage.mayContainFP() && !hsLinkage.isCompiledStub()) {
            /*
             * If the target may contain FP ops, and it is not compiled by us, we may have an
             * AVX-SSE transition.
             *
             * We exclude the argument registers from the zeroing LIR instruction since it violates
             * the LIR semantics of @Temp that values must not be live. Note that the emitted
             * machine instruction actually zeros _all_ XMM registers which is fine since we know
             * that their upper half is not used.
             */
            append(new AMD64VZeroUpper(arguments));
        }
        super.emitForeignCallOp(linkage, result, arguments, temps, info);
    }

    /**
     * @param savedRegisters the registers saved by this operation which may be subject to pruning
     * @param savedRegisterLocations the slots to which the registers are saved
     * @param supportsRemove determines if registers can be pruned
     */
    protected AMD64SaveRegistersOp emitSaveRegisters(Register[] savedRegisters, AllocatableValue[] savedRegisterLocations, boolean supportsRemove) {
        AMD64SaveRegistersOp save = new AMD64SaveRegistersOp(savedRegisters, savedRegisterLocations, supportsRemove);
        append(save);
        return save;
    }

    /**
     * Allocate a stack slot for saving a register.
     */
    protected VirtualStackSlot allocateSaveRegisterLocation(Register register) {
        PlatformKind kind = target().arch.getLargestStorableKind(register.getRegisterCategory());
        if (kind.getVectorLength() > 1) {
            // we don't use vector registers, so there is no need to save them
            kind = AMD64Kind.DOUBLE;
        }
        return getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(kind));
    }

    /**
     * Adds a node to the graph that saves all allocatable registers to the stack.
     *
     * @param supportsRemove determines if registers can be pruned
     * @return the register save node
     */
    private AMD64SaveRegistersOp emitSaveAllRegisters(Register[] savedRegisters, boolean supportsRemove) {
        AllocatableValue[] savedRegisterLocations = new AllocatableValue[savedRegisters.length];
        for (int i = 0; i < savedRegisters.length; i++) {
            savedRegisterLocations[i] = allocateSaveRegisterLocation(savedRegisters[i]);
        }
        return emitSaveRegisters(savedRegisters, savedRegisterLocations, supportsRemove);
    }

    protected void emitRestoreRegisters(AMD64SaveRegistersOp save) {
        append(new AMD64RestoreRegistersOp(save.getSlots().clone(), save));
    }

    /**
     * Gets the {@link Stub} this generator is generating code for or {@code null} if a stub is not
     * being generated.
     */
    public Stub getStub() {
        return getResult().getStub();
    }

    @Override
    public HotSpotLIRGenerationResult getResult() {
        return ((HotSpotLIRGenerationResult) super.getResult());
    }

    public void setDebugInfoBuilder(HotSpotDebugInfoBuilder debugInfoBuilder) {
        this.debugInfoBuilder = debugInfoBuilder;
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... args) {
        HotSpotForeignCallLinkage hotspotLinkage = (HotSpotForeignCallLinkage) linkage;
        boolean destroysRegisters = hotspotLinkage.destroysRegisters();

        AMD64SaveRegistersOp save = null;
        Stub stub = getStub();
        if (destroysRegisters) {
            if (stub != null && stub.preservesRegisters()) {
                Register[] savedRegisters = getRegisterConfig().getAllocatableRegisters().toArray();
                save = emitSaveAllRegisters(savedRegisters, true);
            }
        }

        Variable result;
        LIRFrameState debugInfo = null;
        if (hotspotLinkage.needsDebugInfo()) {
            debugInfo = state;
            assert debugInfo != null || stub != null;
        }

        if (hotspotLinkage.needsJavaFrameAnchor()) {
            Register thread = getProviders().getRegisters().getThreadRegister();
            append(new AMD64HotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), thread));
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
            append(new AMD64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaFpOffset(), config.threadLastJavaPcOffset(), thread));
        } else {
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
        }

        if (destroysRegisters) {
            if (stub != null) {
                if (stub.preservesRegisters()) {
                    HotSpotLIRGenerationResult generationResult = getResult();
                    LIRFrameState key = currentRuntimeCallInfo;
                    if (key == null) {
                        key = LIRFrameState.NO_STATE;
                    }
                    assert !generationResult.getCalleeSaveInfo().containsKey(key);
                    generationResult.getCalleeSaveInfo().put(key, save);
                    emitRestoreRegisters(save);
                }
            }
        }

        return result;
    }

    @Override
    public Value emitLoadObjectAddress(Constant constant) {
        HotSpotObjectConstant objectConstant = (HotSpotObjectConstant) constant;
        LIRKind kind = objectConstant.isCompressed() ? getLIRKindTool().getNarrowOopKind() : getLIRKindTool().getObjectKind();
        Variable result = newVariable(kind);
        append(new AMD64HotSpotLoadAddressOp(result, constant, HotSpotConstantLoadAction.RESOLVE));
        return result;
    }

    @Override
    public Value emitLoadMetaspaceAddress(Constant constant, HotSpotConstantLoadAction action) {
        HotSpotMetaspaceConstant metaspaceConstant = (HotSpotMetaspaceConstant) constant;
        LIRKind kind = metaspaceConstant.isCompressed() ? getLIRKindTool().getNarrowPointerKind() : getLIRKindTool().getWordKind();
        Variable result = newVariable(kind);
        append(new AMD64HotSpotLoadAddressOp(result, constant, action));
        return result;
    }

    private Value emitConstantRetrieval(ForeignCallDescriptor foreignCall, Object[] notes, Constant[] constants, AllocatableValue[] constantDescriptions, LIRFrameState frameState) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(foreignCall);
        append(new AMD64HotSpotConstantRetrievalOp(constants, constantDescriptions, frameState, linkage, notes));
        AllocatableValue result = linkage.getOutgoingCallingConvention().getReturn();
        return emitMove(result);
    }

    private Value emitConstantRetrieval(ForeignCallDescriptor foreignCall, HotSpotConstantLoadAction action, Constant constant, AllocatableValue[] constantDescriptions, LIRFrameState frameState) {
        Constant[] constants = new Constant[]{constant};
        Object[] notes = new Object[]{action};
        return emitConstantRetrieval(foreignCall, notes, constants, constantDescriptions, frameState);
    }

    private Value emitConstantRetrieval(ForeignCallDescriptor foreignCall, HotSpotConstantLoadAction action, Constant constant, Value constantDescription, LIRFrameState frameState) {
        AllocatableValue[] constantDescriptions = new AllocatableValue[]{asAllocatable(constantDescription)};
        return emitConstantRetrieval(foreignCall, action, constant, constantDescriptions, frameState);
    }

    @Override
    public Value emitObjectConstantRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        return emitConstantRetrieval(RESOLVE_STRING_BY_SYMBOL, RESOLVE, constant, constantDescription, frameState);
    }

    @Override
    public Value emitMetaspaceConstantRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        return emitConstantRetrieval(RESOLVE_KLASS_BY_SYMBOL, RESOLVE, constant, constantDescription, frameState);
    }

    @Override
    public Value emitKlassInitializationAndRetrieval(Constant constant, Value constantDescription, LIRFrameState frameState) {
        return emitConstantRetrieval(INITIALIZE_KLASS_BY_SYMBOL, INITIALIZE, constant, constantDescription, frameState);
    }

    @Override
    public Value emitResolveMethodAndLoadCounters(Constant method, Value klassHint, Value methodDescription, LIRFrameState frameState) {
        AllocatableValue[] constantDescriptions = new AllocatableValue[]{asAllocatable(klassHint), asAllocatable(methodDescription)};
        return emitConstantRetrieval(RESOLVE_METHOD_BY_SYMBOL_AND_LOAD_COUNTERS, LOAD_COUNTERS, method, constantDescriptions, frameState);
    }

    @Override
    public Value emitResolveDynamicInvoke(Constant appendix, LIRFrameState frameState) {
        AllocatableValue[] constantDescriptions = new AllocatableValue[0];
        return emitConstantRetrieval(RESOLVE_DYNAMIC_INVOKE, INITIALIZE, appendix, constantDescriptions, frameState);
    }

    @Override
    public Value emitLoadConfigValue(int markId, LIRKind kind) {
        Variable result = newVariable(kind);
        append(new AMD64HotSpotLoadConfigValueOp(markId, result));
        return result;
    }

    @Override
    public Value emitRandomSeed() {
        AMD64ReadTimestampCounter timestamp = new AMD64ReadTimestampCounter();
        append(timestamp);
        return emitMove(timestamp.getLowResult());
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        append(new AMD64TailcallOp(args, address));
    }

    @Override
    public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments) {
        Value[] argLocations = new Value[args.length];
        getResult().getFrameMapBuilder().callsMethod(nativeCallingConvention);
        // TODO(mg): in case a native function uses floating point varargs, the ABI requires that
        // RAX contains the length of the varargs
        PrimitiveConstant intConst = JavaConstant.forInt(numberOfFloatingPointArguments);
        AllocatableValue numberOfFloatingPointArgumentsRegister = AMD64.rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        emitMoveConstant(numberOfFloatingPointArgumentsRegister, intConst);
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = nativeCallingConvention.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }
        Value ptr = emitLoadConstant(LIRKind.value(AMD64Kind.QWORD), JavaConstant.forLong(address));
        append(new AMD64CCall(nativeCallingConvention.getReturn(), ptr, numberOfFloatingPointArgumentsRegister, argLocations));
    }

    @Override
    public void emitUnwind(Value exception) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionParameter = (RegisterValue) outgoingCc.getArgument(0);
        emitMove(exceptionParameter, exception);
        append(new AMD64HotSpotUnwindOp(exceptionParameter));
    }

    private void moveDeoptValuesToThread(Value actionAndReason, Value speculation) {
        moveValueToThread(actionAndReason, config.pendingDeoptimizationOffset);
        moveValueToThread(speculation, config.pendingFailedSpeculationOffset);
    }

    private void moveValueToThread(Value v, int offset) {
        LIRKind wordKind = LIRKind.value(target().arch.getWordKind());
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        AMD64AddressValue address = new AMD64AddressValue(wordKind, thread, offset);
        arithmeticLIRGen.emitStore(v.getValueKind(), address, v, null);
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new AMD64DeoptimizeOp(state));
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        Value actionAndReason = emitJavaConstant(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0));
        Value speculation = emitJavaConstant(getMetaAccess().encodeSpeculation(SpeculationLog.NO_SPECULATION));
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new AMD64HotSpotDeoptimizeCallerOp());
    }

    @Override
    public void beforeRegisterAllocation() {
        super.beforeRegisterAllocation();
        boolean hasDebugInfo = getResult().getLIR().hasDebugInfo();
        AllocatableValue savedRbp = saveRbp.finalize(hasDebugInfo);
        if (hasDebugInfo) {
            getResult().setDeoptimizationRescueSlot(((AMD64FrameMapBuilder) getResult().getFrameMapBuilder()).allocateDeoptimizationRescueSlot());
        }

        getResult().setMaxInterpreterFrameSize(debugInfoBuilder.maxInterpreterFrameSize());

        for (AMD64HotSpotRestoreRbpOp op : epilogueOps) {
            op.setSavedRbp(savedRbp);
        }
        if (BenchmarkCounters.enabled) {
            // ensure that the rescue slot is available
            LIRInstruction op = getOrInitRescueSlotOp();
            // insert dummy instruction into the start block
            LIR lir = getResult().getLIR();
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(lir.getControlFlowGraph().getStartBlock());
            instructions.add(1, op);
            lir.getDebug().dump(DebugContext.INFO_LEVEL, lir, "created rescue dummy op");
        }
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getValueKind(LIRKind.class);
        LIRKindTool lirKindTool = getLIRKindTool();
        assert inputKind.getPlatformKind() == lirKindTool.getObjectKind().getPlatformKind();
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(lirKindTool.getNarrowOopKind());
            append(new AMD64Move.CompressPointerOp(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        } else {
            // metaspace pointer
            Variable result = newVariable(lirKindTool.getNarrowPointerKind());
            AllocatableValue base = Value.ILLEGAL;
            OptionValues options = getResult().getLIR().getOptions();
            if (encoding.hasBase() || GeneratePIC.getValue(options)) {
                if (GeneratePIC.getValue(options)) {
                    Variable baseAddress = newVariable(lirKindTool.getWordKind());
                    AMD64HotSpotMove.BaseMove move = new AMD64HotSpotMove.BaseMove(baseAddress, config);
                    append(move);
                    base = baseAddress;
                } else {
                    base = emitLoadConstant(lirKindTool.getWordKind(), JavaConstant.forLong(encoding.getBase()));
                }
            }
            append(new AMD64Move.CompressPointerOp(result, asAllocatable(pointer), base, encoding, nonNull, getLIRKindTool()));
            return result;
        }
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getValueKind(LIRKind.class);
        LIRKindTool lirKindTool = getLIRKindTool();
        assert inputKind.getPlatformKind() == lirKindTool.getNarrowOopKind().getPlatformKind();
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(lirKindTool.getObjectKind());
            append(new AMD64Move.UncompressPointerOp(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull, lirKindTool));
            return result;
        } else {
            // metaspace pointer
            LIRKind uncompressedKind = lirKindTool.getWordKind();
            Variable result = newVariable(uncompressedKind);
            AllocatableValue base = Value.ILLEGAL;
            OptionValues options = getResult().getLIR().getOptions();
            if (encoding.hasBase() || GeneratePIC.getValue(options)) {
                if (GeneratePIC.getValue(options)) {
                    Variable baseAddress = newVariable(uncompressedKind);
                    AMD64HotSpotMove.BaseMove move = new AMD64HotSpotMove.BaseMove(baseAddress, config);
                    append(move);
                    base = baseAddress;
                } else {
                    base = emitLoadConstant(uncompressedKind, JavaConstant.forLong(encoding.getBase()));
                }
            }
            append(new AMD64Move.UncompressPointerOp(result, asAllocatable(pointer), base, encoding, nonNull, lirKindTool));
            return result;
        }
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        if (address.getValueKind().getPlatformKind() == getLIRKindTool().getNarrowOopKind().getPlatformKind()) {
            CompressEncoding encoding = config.getOopEncoding();
            Value uncompressed;
            if (encoding.getShift() <= 3) {
                LIRKind wordKind = LIRKind.unknownReference(target().arch.getWordKind());
                uncompressed = new AMD64AddressValue(wordKind, getProviders().getRegisters().getHeapBaseRegister().asValue(wordKind), asAllocatable(address), Scale.fromInt(1 << encoding.getShift()),
                                0);
            } else {
                uncompressed = emitUncompress(address, encoding, false);
            }
            append(new AMD64Move.NullCheckOp(asAddressValue(uncompressed), state));
            return;
        }
        super.emitNullCheck(address, state);
    }

    @Override
    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        if (BenchmarkCounters.enabled) {
            return new AMD64HotSpotCounterOp(name, group, increment, getProviders().getRegisters(), config, getOrInitRescueSlot());
        }
        throw GraalError.shouldNotReachHere("BenchmarkCounters are not enabled!");
    }

    @Override
    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        if (BenchmarkCounters.enabled) {
            return new AMD64HotSpotCounterOp(names, groups, increments, getProviders().getRegisters(), config, getOrInitRescueSlot());
        }
        throw GraalError.shouldNotReachHere("BenchmarkCounters are not enabled!");
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        append(new AMD64PrefetchOp(asAddressValue(address), config.allocatePrefetchInstr));
    }

    @Override
    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, AllocatableValue temp) {
        return new AMD64HotSpotStrategySwitchOp(strategy, keyTargets, defaultTarget, key, temp);
    }
}
