/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.aarch64;

import static org.graalvm.compiler.core.common.GraalOptions.ZapStackOnMethodEntry;
import static java.lang.reflect.Modifier.isStatic;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.fp;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.hotspot.HotSpotDataBuilder;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotHostBackend;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerationResult;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.aarch64.AArch64Call;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMap;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMapBuilder;
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
import org.graalvm.util.EconomicSet;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot AArch64 specific backend.
 */
public class AArch64HotSpotBackend extends HotSpotHostBackend {

    public AArch64HotSpotBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(config, runtime, providers);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AArch64FrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfigNonNull);
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new AArch64FrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        return new AArch64HotSpotLIRGenerator(getProviders(), config, lirGenRes);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, StructuredGraph graph, Object stub) {
        return new HotSpotLIRGenerationResult(compilationId, lir, frameMapBuilder, makeCallingConvention(graph, (Stub) stub), stub);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new AArch64HotSpotNodeLIRBuilder(graph, lirGen, new AArch64NodeMatchRules(lirGen));
    }

    @Override
    protected void bangStackWithOffset(CompilationResultBuilder crb, int bangOffset) {
        AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            AArch64Address address = masm.makeAddress(sp, -bangOffset, scratch, 8, /* allowOverwrite */false);
            masm.str(64, zr, address);
        }
    }

    private class HotSpotFrameContext implements FrameContext {
        final boolean isStub;

        HotSpotFrameContext(boolean isStub) {
            this.isStub = isStub;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            FrameMap frameMap = crb.frameMap;
            final int frameSize = frameMap.frameSize();
            final int totalFrameSize = frameMap.totalFrameSize();
            assert frameSize + 2 * crb.target.arch.getWordSize() == totalFrameSize : "total framesize should be framesize + 2 words";
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            if (!isStub) {
                emitStackOverflowCheck(crb);
            }
            crb.blockComment("[method prologue]");

            try (ScratchRegister sc = masm.getScratchRegister()) {
                int wordSize = crb.target.arch.getWordSize();
                Register rscratch1 = sc.getRegister();
                assert totalFrameSize > 0;
                if (frameSize < 1 << 9) {
                    masm.sub(64, sp, sp, totalFrameSize);
                    masm.stp(64, fp, lr, AArch64Address.createScaledImmediateAddress(sp, frameSize / wordSize));
                } else {
                    masm.stp(64, fp, lr, AArch64Address.createPreIndexedImmediateAddress(sp, -2));
                    if (frameSize < 1 << 12) {
                        masm.sub(64, sp, sp, totalFrameSize - 2 * wordSize);
                    } else {
                        masm.mov(rscratch1, totalFrameSize - 2 * wordSize);
                        masm.sub(64, sp, sp, rscratch1);
                    }
                }
            }
            if (ZapStackOnMethodEntry.getValue(crb.getOptions())) {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch = sc.getRegister();
                    int longSize = 8;
                    masm.mov(64, scratch, sp);
                    AArch64Address address = AArch64Address.createPostIndexedImmediateAddress(scratch, longSize);
                    try (ScratchRegister sc2 = masm.getScratchRegister()) {
                        Register value = sc2.getRegister();
                        masm.mov(value, 0xBADDECAFFC0FFEEL);
                        for (int i = 0; i < frameSize; i += longSize) {
                            masm.str(64, value, address);
                        }
                    }

                }
            }
            crb.blockComment("[code body]");
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            FrameMap frameMap = crb.frameMap;
            final int totalFrameSize = frameMap.totalFrameSize();

            crb.blockComment("[method epilogue]");
            try (ScratchRegister sc = masm.getScratchRegister()) {
                int wordSize = crb.target.arch.getWordSize();
                Register rscratch1 = sc.getRegister();
                final int frameSize = frameMap.frameSize();
                assert totalFrameSize > 0;
                if (frameSize < 1 << 9) {
                    masm.ldp(64, fp, lr, AArch64Address.createScaledImmediateAddress(sp, frameSize / wordSize));
                    masm.add(64, sp, sp, totalFrameSize);
                } else {
                    if (frameSize < 1 << 12) {
                        masm.add(64, sp, sp, totalFrameSize - 2 * wordSize);
                    } else {
                        masm.mov(rscratch1, totalFrameSize - 2 * wordSize);
                        masm.add(64, sp, sp, rscratch1);
                    }
                    masm.ldp(64, fp, lr, AArch64Address.createPostIndexedImmediateAddress(sp, 2));
                }
            }

        }

        @Override
        public boolean hasFrame() {
            return true;
        }

    }

    @Override
    protected Assembler createAssembler(FrameMap frameMap) {
        return new AArch64MacroAssembler(getTarget());
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRen, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        HotSpotLIRGenerationResult gen = (HotSpotLIRGenerationResult) lirGenRen;
        LIR lir = gen.getLIR();
        assert gen.getDeoptimizationRescueSlot() == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";

        Stub stub = gen.getStub();
        Assembler masm = createAssembler(frameMap);
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null);

        DataBuilder dataBuilder = new HotSpotDataBuilder(getCodeCache().getTarget());
        CompilationResultBuilder crb = factory.createBuilder(getCodeCache(), getForeignCalls(), frameMap, masm, dataBuilder, frameContext, lir.getOptions(), lir.getDebug(), compilationResult);
        crb.setTotalFrameSize(frameMap.totalFrameSize());
        crb.setMaxInterpreterFrameSize(gen.getMaxInterpreterFrameSize());
        StackSlot deoptimizationRescueSlot = gen.getDeoptimizationRescueSlot();
        if (deoptimizationRescueSlot != null && stub == null) {
            crb.compilationResult.setCustomStackAreaOffset(deoptimizationRescueSlot);
        }

        if (stub != null) {
            EconomicSet<Register> destroyedCallerRegisters = gatherDestroyedCallerRegisters(lir);
            updateStub(stub, destroyedCallerRegisters, gen.getCalleeSaveInfo(), frameMap);
        }
        return crb;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.getRegisterConfig();
        Label verifiedStub = new Label();

        emitCodePrefix(crb, installedCodeOwner, masm, regConfig, verifiedStub);
        emitCodeBody(crb, lir, masm);
        emitCodeSuffix(crb, masm, frameMap);
    }

    private void emitCodePrefix(CompilationResultBuilder crb, ResolvedJavaMethod installedCodeOwner, AArch64MacroAssembler masm, RegisterConfig regConfig, Label verifiedStub) {
        HotSpotProviders providers = getProviders();
        if (installedCodeOwner != null && !isStatic(installedCodeOwner.getModifiers())) {
            crb.recordMark(config.MARKID_UNVERIFIED_ENTRY);
            CallingConvention cc = regConfig.getCallingConvention(HotSpotCallingConventionType.JavaCallee, null, new JavaType[]{providers.getMetaAccess().lookupJavaType(Object.class)}, this);
            // See definition of IC_Klass in c1_LIRAssembler_aarch64.cpp
            // equal to scratch(1) careful!
            Register inlineCacheKlass = AArch64HotSpotRegisterConfig.inlineCacheRegister;
            Register receiver = asRegister(cc.getArgument(0));
            int transferSize = config.useCompressedClassPointers ? 4 : 8;
            AArch64Address klassAddress = masm.makeAddress(receiver, config.hubOffset, transferSize);

            // Are r10 and r11 available scratch registers here? One would hope so.
            Register klass = r10;
            if (config.useCompressedClassPointers) {
                masm.ldr(32, klass, klassAddress);
                AArch64HotSpotMove.decodeKlassPointer(masm, klass, klass, providers.getRegisters().getHeapBaseRegister(), config.getKlassEncoding());
            } else {
                masm.ldr(64, klass, klassAddress);
            }
            masm.cmp(64, inlineCacheKlass, klass);
            /*
             * Conditional jumps have a much lower range than unconditional ones, which can be a
             * problem because the miss handler could be out of range.
             */
            masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, verifiedStub);
            AArch64Call.directJmp(crb, masm, getForeignCalls().lookupForeignCall(IC_MISS_HANDLER));
        }
        masm.align(config.codeEntryAlignment);
        crb.recordMark(config.MARKID_OSR_ENTRY);
        masm.bind(verifiedStub);
        crb.recordMark(config.MARKID_VERIFIED_ENTRY);
    }

    private static void emitCodeBody(CompilationResultBuilder crb, LIR lir, AArch64MacroAssembler masm) {
        /*
         * Insert a nop at the start of the prolog so we can patch in a branch if we need to
         * invalidate the method later.
         */
        crb.blockComment("[nop for method invalidation]");
        masm.nop();

        crb.emit(lir);
    }

    private void emitCodeSuffix(CompilationResultBuilder crb, AArch64MacroAssembler masm, FrameMap frameMap) {
        HotSpotProviders providers = getProviders();
        HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
        if (!frameContext.isStub) {
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                HotSpotForeignCallsProvider foreignCalls = providers.getForeignCalls();
                crb.recordMark(config.MARKID_EXCEPTION_HANDLER_ENTRY);
                ForeignCallLinkage linkage = foreignCalls.lookupForeignCall(EXCEPTION_HANDLER);
                Register helper = AArch64Call.isNearCall(linkage) ? null : scratch;
                AArch64Call.directCall(crb, masm, linkage, helper, null);

                crb.recordMark(config.MARKID_DEOPT_HANDLER_ENTRY);
                linkage = foreignCalls.lookupForeignCall(DEOPTIMIZATION_HANDLER);
                helper = AArch64Call.isNearCall(linkage) ? null : scratch;
                AArch64Call.directCall(crb, masm, linkage, helper, null);
            }
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries
            assert !frameMap.accessesCallerFrame();
        }
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AArch64HotSpotRegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo);
    }

    @Override
    public EconomicSet<Register> translateToCallerRegisters(EconomicSet<Register> calleeRegisters) {
        return calleeRegisters;
    }
}
