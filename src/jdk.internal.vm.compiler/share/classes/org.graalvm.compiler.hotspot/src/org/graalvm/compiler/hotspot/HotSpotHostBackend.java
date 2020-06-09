/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.code.CodeUtil.K;
import static jdk.vm.ci.code.CodeUtil.getCallingConvention;
import static jdk.vm.ci.common.InitTimer.timer;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProviderImpl.NO_LOCATIONS;

import java.util.Collections;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.gen.LIRGenerationProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Common functionality of HotSpot host backends.
 */
public abstract class HotSpotHostBackend extends HotSpotBackend implements LIRGenerationProvider {

    /**
     * Descriptor for {@code SharedRuntime::deopt_blob()->unpack()}.
     */
    public static final HotSpotForeignCallDescriptor DEOPT_BLOB_UNPACK = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "deopt_blob()->unpack()", void.class);

    /**
     * Descriptor for {@code SharedRuntime::deopt_blob()->unpack_with_exception_in_tls()}.
     */
    public static final HotSpotForeignCallDescriptor DEOPT_BLOB_UNPACK_WITH_EXCEPTION_IN_TLS = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS,
                    "deopt_blob()->unpack_with_exception_in_tls()", void.class);

    /**
     * Descriptor for {@code SharedRuntime::deopt_blob()->uncommon_trap()}.
     */
    public static final HotSpotForeignCallDescriptor DEOPT_BLOB_UNCOMMON_TRAP = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "deopt_blob()->uncommon_trap()",
                    void.class);

    public static final HotSpotForeignCallDescriptor ENABLE_STACK_RESERVED_ZONE = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "enableStackReservedZoneEntry",
                    void.class, Word.class);

    public static final HotSpotForeignCallDescriptor THROW_DELAYED_STACKOVERFLOW_ERROR = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "throwDelayedStackoverflowError",
                    void.class);

    protected final GraalHotSpotVMConfig config;

    public HotSpotHostBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(runtime, providers);
        this.config = config;
    }

    @Override
    @SuppressWarnings("try")
    public void completeInitialization(HotSpotJVMCIRuntime jvmciRuntime, OptionValues options) {
        final HotSpotProviders providers = getProviders();
        HotSpotHostForeignCallsProvider foreignCalls = (HotSpotHostForeignCallsProvider) providers.getForeignCalls();
        final HotSpotLoweringProvider lowerer = (HotSpotLoweringProvider) providers.getLowerer();

        try (InitTimer st = timer("foreignCalls.initialize")) {
            foreignCalls.initialize(providers, options);
        }
        try (InitTimer st = timer("lowerer.initialize")) {
            Iterable<DebugHandlersFactory> factories = Collections.singletonList(new GraalDebugHandlersFactory(providers.getSnippetReflection()));
            lowerer.initialize(options, factories, providers, config);
        }
        providers.getReplacements().closeSnippetRegistration();
    }

    protected CallingConvention makeCallingConvention(StructuredGraph graph, Stub stub) {
        if (stub != null) {
            return stub.getLinkage().getIncomingCallingConvention();
        }

        CallingConvention cc = getCallingConvention(getCodeCache(), HotSpotCallingConventionType.JavaCallee, graph.method(), this);
        if (graph.getEntryBCI() != JVMCICompiler.INVOCATION_ENTRY_BCI) {
            // for OSR, only a pointer is passed to the method.
            JavaType[] parameterTypes = new JavaType[]{getMetaAccess().lookupJavaType(long.class)};
            CallingConvention tmp = getCodeCache().getRegisterConfig().getCallingConvention(HotSpotCallingConventionType.JavaCallee, getMetaAccess().lookupJavaType(void.class), parameterTypes, this);
            cc = new CallingConvention(cc.getStackSize(), cc.getReturn(), tmp.getArgument(0));
        }
        return cc;
    }

    public void emitStackOverflowCheck(CompilationResultBuilder crb) {
        if (config.useStackBanging) {
            // Each code entry causes one stack bang n pages down the stack where n
            // is configurable by StackShadowPages. The setting depends on the maximum
            // depth of VM call stack or native before going back into java code,
            // since only java code can raise a stack overflow exception using the
            // stack banging mechanism. The VM and native code does not detect stack
            // overflow.
            // The code in JavaCalls::call() checks that there is at least n pages
            // available, so all entry code needs to do is bang once for the end of
            // this shadow zone.
            // The entry code may need to bang additional pages if the framesize
            // is greater than a page.

            int pageSize = config.vmPageSize;
            int bangEnd = NumUtil.roundUp(config.stackShadowPages * 4 * K, pageSize);

            // This is how far the previous frame's stack banging extended.
            int bangEndSafe = bangEnd;

            int frameSize = Math.max(crb.frameMap.frameSize(), crb.compilationResult.getMaxInterpreterFrameSize());
            if (frameSize > pageSize) {
                bangEnd += frameSize;
            }

            int bangOffset = bangEndSafe;
            if (bangOffset <= bangEnd) {
                crb.blockComment("[stack overflow check]");
            }
            while (bangOffset <= bangEnd) {
                // Need at least one stack bang at end of shadow zone.
                bangStackWithOffset(crb, bangOffset);
                bangOffset += pageSize;
            }
        }
    }

    protected abstract void bangStackWithOffset(CompilationResultBuilder crb, int bangOffset);

    @Override
    public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
        int uncompressedReferenceSize = getTarget().arch.getPlatformKind(JavaKind.Object).getSizeInBytes();
        return new HotSpotReferenceMapBuilder(totalFrameSize, config.maxOopMapStackOffset, uncompressedReferenceSize);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, RegisterAllocationConfig registerAllocationConfig, StructuredGraph graph, Object stub) {
        return new HotSpotLIRGenerationResult(compilationId, lir, newFrameMapBuilder(registerAllocationConfig.getRegisterConfig()), registerAllocationConfig, makeCallingConvention(graph, (Stub) stub),
                        stub,
                        config.requiresReservedStackCheck(graph.getMethods()));
    }

    protected abstract FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig);
}
