/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.core.common.GraalOptions.CanOmitFrame;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.ZapStackOnMethodEntry;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.amd64.AMD64NodeMatchRules;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.gen.LIRGenerationProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotDataBuilder;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotHostBackend;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerationResult;
import org.graalvm.compiler.hotspot.HotSpotMarkId;
import org.graalvm.compiler.hotspot.meta.HotSpotConstantLoadAction;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.amd64.AMD64Call;
import org.graalvm.compiler.lir.amd64.AMD64FrameMap;
import org.graalvm.compiler.lir.amd64.AMD64FrameMapBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotSentinelConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot AMD64 specific backend.
 */
public class AMD64HotSpotBackend extends HotSpotHostBackend implements LIRGenerationProvider {

    public AMD64HotSpotBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(config, runtime, providers);
    }

    @Override
    protected FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        FrameMap frameMap = new AMD64FrameMap(getCodeCache(), registerConfigNonNull, this, config.preserveFramePointer);
        return new AMD64FrameMapBuilder(frameMap, getCodeCache(), registerConfigNonNull);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        return new AMD64HotSpotLIRGenerator(getProviders(), config, lirGenRes);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new AMD64HotSpotNodeLIRBuilder(graph, lirGen, new AMD64NodeMatchRules(lirGen));
    }

    @Override
    protected void bangStackWithOffset(CompilationResultBuilder crb, int bangOffset) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
        int pos = asm.position();
        asm.movl(new AMD64Address(rsp, -bangOffset), AMD64.rax);
        assert asm.position() - pos >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
    }

    /**
     * The size of the instruction used to patch the verified entry point of an nmethod when the
     * nmethod is made non-entrant or a zombie (e.g. during deopt or class unloading). The first
     * instruction emitted at an nmethod's verified entry point must be at least this length to
     * ensure mt-safe patching.
     */
    public static final int PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE = 5;

    /**
     * Emits code at the verified entry point and return point(s) of a method.
     */
    class HotSpotFrameContext implements FrameContext {

        final boolean isStub;
        final boolean omitFrame;
        final boolean useStandardFrameProlog;

        HotSpotFrameContext(boolean isStub, boolean omitFrame, boolean useStandardFrameProlog) {
            this.isStub = isStub;
            this.omitFrame = omitFrame;
            this.useStandardFrameProlog = useStandardFrameProlog;
        }

        @Override
        public boolean hasFrame() {
            return !omitFrame;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            FrameMap frameMap = crb.frameMap;
            int frameSize = frameMap.frameSize();
            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
            if (omitFrame) {
                if (!isStub) {
                    asm.nop(PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE);
                }
            } else {
                int verifiedEntryPointOffset = asm.position();
                if (!isStub) {
                    emitStackOverflowCheck(crb);
                    // assert asm.position() - verifiedEntryPointOffset >=
                    // PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
                }
                if (useStandardFrameProlog) {
                    // Stack-walking friendly instructions
                    asm.push(rbp);
                    asm.movq(rbp, rsp);
                }
                if (!isStub && asm.position() == verifiedEntryPointOffset) {
                    asm.subqWide(rsp, frameSize);
                    assert asm.position() - verifiedEntryPointOffset >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
                } else {
                    asm.decrementq(rsp, frameSize);
                }
                if (HotSpotMarkId.FRAME_COMPLETE.isAvailable()) {
                    crb.recordMark(HotSpotMarkId.FRAME_COMPLETE);
                }
                if (ZapStackOnMethodEntry.getValue(crb.getOptions())) {
                    final int intSize = 4;
                    for (int i = 0; i < frameSize / intSize; ++i) {
                        asm.movl(new AMD64Address(rsp, i * intSize), 0xC1C1C1C1);
                    }
                }
                assert frameMap.getRegisterConfig().getCalleeSaveRegisters() == null;
            }
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            if (!omitFrame) {
                AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
                assert crb.frameMap.getRegisterConfig().getCalleeSaveRegisters() == null;

                int frameSize = crb.frameMap.frameSize();
                if (useStandardFrameProlog) {
                    asm.movq(rsp, rbp);
                    asm.pop(rbp);
                } else {
                    asm.incrementq(rsp, frameSize);
                }
            }
        }
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRen, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        // Omit the frame if the method:
        // - has no spill slots or other slots allocated during register allocation
        // - has no callee-saved registers
        // - has no incoming arguments passed on the stack
        // - has no deoptimization points
        // - makes no foreign calls (which require an aligned stack)
        HotSpotLIRGenerationResult gen = (HotSpotLIRGenerationResult) lirGenRen;
        LIR lir = gen.getLIR();
        assert gen.getDeoptimizationRescueSlot() == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";
        OptionValues options = lir.getOptions();
        DebugContext debug = lir.getDebug();
        boolean omitFrame = CanOmitFrame.getValue(options) && !frameMap.frameNeedsAllocating() && !lir.hasArgInCallerFrame() && !gen.hasForeignCall() &&
                        !((AMD64FrameMap) frameMap).useStandardFrameProlog();

        Stub stub = gen.getStub();
        AMD64MacroAssembler masm = new AMD64MacroAssembler(getTarget(), options, config.CPU_HAS_INTEL_JCC_ERRATUM);
        masm.setCodePatchShifter(compilationResult::shiftCodePatch);
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null, omitFrame, config.preserveFramePointer);
        DataBuilder dataBuilder = new HotSpotDataBuilder(getCodeCache().getTarget());
        CompilationResultBuilder crb = factory.createBuilder(getCodeCache(), getForeignCalls(), frameMap, masm, dataBuilder, frameContext, options, debug, compilationResult, Register.None);
        crb.setTotalFrameSize(frameMap.totalFrameSize());
        crb.setMaxInterpreterFrameSize(gen.getMaxInterpreterFrameSize());
        StackSlot deoptimizationRescueSlot = gen.getDeoptimizationRescueSlot();
        if (deoptimizationRescueSlot != null && stub == null) {
            crb.compilationResult.setCustomStackAreaOffset(deoptimizationRescueSlot);
        }

        if (stub != null) {
            updateStub(stub, gen, frameMap);
        }

        return crb;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.getRegisterConfig();

        // Emit the prefix
        emitCodePrefix(installedCodeOwner, crb, asm, regConfig);

        // Emit code for the LIR
        emitCodeBody(installedCodeOwner, crb, lir);

        // Emit the suffix
        emitCodeSuffix(installedCodeOwner, crb, asm, frameMap);

        // Profile assembler instructions
        profileInstructions(lir, crb);
    }

    /**
     * Emits the code prior to the verified entry point.
     *
     * @param installedCodeOwner see {@link LIRGenerationProvider#emitCode}
     */
    public void emitCodePrefix(ResolvedJavaMethod installedCodeOwner, CompilationResultBuilder crb, AMD64MacroAssembler asm, RegisterConfig regConfig) {
        HotSpotProviders providers = getProviders();
        if (installedCodeOwner != null && !installedCodeOwner.isStatic()) {
            crb.recordMark(HotSpotMarkId.UNVERIFIED_ENTRY);
            CallingConvention cc = regConfig.getCallingConvention(HotSpotCallingConventionType.JavaCallee, null, new JavaType[]{providers.getMetaAccess().lookupJavaType(Object.class)}, this);
            Register inlineCacheKlass = rax; // see definition of IC_Klass in
                                             // c1_LIRAssembler_x86.cpp
            Register receiver = asRegister(cc.getArgument(0));
            AMD64Address src = new AMD64Address(receiver, config.hubOffset);
            int before;

            if (config.useCompressedClassPointers) {
                Register register = r10;
                Register heapBase = providers.getRegisters().getHeapBaseRegister();
                AMD64HotSpotMove.decodeKlassPointer(crb, asm, register, heapBase, src, config);
                if (GeneratePIC.getValue(crb.getOptions())) {
                    asm.movq(heapBase, asm.getPlaceholder(-1));
                    crb.recordMark(HotSpotMarkId.NARROW_OOP_BASE_ADDRESS);
                } else {
                    if (config.narrowKlassBase != 0) {
                        // The heap base register was destroyed above, so restore it
                        if (config.narrowOopBase == 0L) {
                            asm.xorq(heapBase, heapBase);
                        } else {
                            asm.movq(heapBase, config.narrowOopBase);
                        }
                    }
                }
                before = asm.cmpqAndJcc(inlineCacheKlass, register, ConditionFlag.NotEqual, null, false);
            } else {
                before = asm.cmpqAndJcc(inlineCacheKlass, src, ConditionFlag.NotEqual, null, false);
            }
            AMD64Call.recordDirectCall(crb, asm, getForeignCalls().lookupForeignCall(IC_MISS_HANDLER), before);
        }

        asm.align(config.codeEntryAlignment);
        crb.recordMark(crb.compilationResult.getEntryBCI() != -1 ? HotSpotMarkId.OSR_ENTRY : HotSpotMarkId.VERIFIED_ENTRY);

        if (GeneratePIC.getValue(crb.getOptions())) {
            // Check for method state
            HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
            if (!frameContext.isStub) {
                crb.recordInlineDataInCodeWithNote(new HotSpotSentinelConstant(LIRKind.value(AMD64Kind.QWORD), JavaKind.Long), HotSpotConstantLoadAction.MAKE_NOT_ENTRANT);
                asm.movq(AMD64.rax, asm.getPlaceholder(-1));
                int before = asm.testqAndJcc(AMD64.rax, AMD64.rax, ConditionFlag.NotZero, null, false);
                AMD64Call.recordDirectCall(crb, asm, getForeignCalls().lookupForeignCall(WRONG_METHOD_HANDLER), before);
            }
        }
    }

    /**
     * Emits the code which starts at the verified entry point.
     *
     * @param installedCodeOwner see {@link LIRGenerationProvider#emitCode}
     */
    public void emitCodeBody(ResolvedJavaMethod installedCodeOwner, CompilationResultBuilder crb, LIR lir) {
        crb.emit(lir);
    }

    /**
     * @param installedCodeOwner see {@link LIRGenerationProvider#emitCode}
     */
    public void emitCodeSuffix(ResolvedJavaMethod installedCodeOwner, CompilationResultBuilder crb, AMD64MacroAssembler asm, FrameMap frameMap) {
        HotSpotProviders providers = getProviders();
        HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
        if (!frameContext.isStub) {
            HotSpotForeignCallsProvider foreignCalls = providers.getForeignCalls();
            crb.recordMark(HotSpotMarkId.EXCEPTION_HANDLER_ENTRY);
            AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(EXCEPTION_HANDLER), null, false, null);
            crb.recordMark(HotSpotMarkId.DEOPT_HANDLER_ENTRY);
            AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(DEOPT_BLOB_UNPACK), null, false, null);
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries

            if (frameContext.omitFrame) {
                // Cannot access slots in caller's frame if my frame is omitted
                assert !frameMap.accessesCallerFrame();
            }
        }
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AMD64HotSpotRegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo, config.preserveFramePointer);
    }

    @Override
    public EconomicSet<Register> translateToCallerRegisters(EconomicSet<Register> calleeRegisters) {
        return calleeRegisters;
    }
}
