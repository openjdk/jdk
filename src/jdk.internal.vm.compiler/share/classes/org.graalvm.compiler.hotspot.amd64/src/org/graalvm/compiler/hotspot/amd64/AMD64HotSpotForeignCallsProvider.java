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


package org.graalvm.compiler.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static jdk.vm.ci.meta.Value.ILLEGAL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.Options.GraalArithmeticStubs;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.JUMP_ADDRESS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Reexecutability.REEXECUTABLE_ONLY_AFTER_EXCEPTION;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.PRESERVES_REGISTERS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.LEAF;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.LEAF_NOFP;
import static org.graalvm.compiler.hotspot.replacements.CRC32CSubstitutions.UPDATE_BYTES_CRC32C;
import static org.graalvm.compiler.hotspot.replacements.CRC32Substitutions.UPDATE_BYTES_CRC32;
import static org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOf;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AMD64HotSpotForeignCallsProvider extends HotSpotHostForeignCallsProvider {

    private final Value[] nativeABICallerSaveRegisters;

    public AMD64HotSpotForeignCallsProvider(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    WordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        super(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes);
        this.nativeABICallerSaveRegisters = nativeABICallerSaveRegisters;
    }

    @Override
    public void initialize(HotSpotProviders providers, OptionValues options) {
        GraalHotSpotVMConfig config = runtime.getVMConfig();
        TargetDescription target = providers.getCodeCache().getTarget();
        PlatformKind word = target.arch.getWordKind();

        // The calling convention for the exception handler stub is (only?) defined in
        // TemplateInterpreterGenerator::generate_throw_exception()
        // in templateInterpreter_x86_64.cpp around line 1923
        RegisterValue exception = rax.asValue(LIRKind.reference(word));
        RegisterValue exceptionPc = rdx.asValue(LIRKind.value(word));
        CallingConvention exceptionCc = new CallingConvention(0, ILLEGAL, exception, exceptionPc);
        register(new HotSpotForeignCallLinkageImpl(EXCEPTION_HANDLER, 0L, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, exceptionCc, null, any()));
        register(new HotSpotForeignCallLinkageImpl(EXCEPTION_HANDLER_IN_CALLER, JUMP_ADDRESS, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, exceptionCc, null, any()));

        if (config.useCRC32Intrinsics) {
            // This stub does callee saving
            registerForeignCall(UPDATE_BYTES_CRC32, config.updateBytesCRC32Stub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());
        }
        if (config.useCRC32CIntrinsics) {
            registerForeignCall(UPDATE_BYTES_CRC32C, config.updateBytesCRC32C, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());
        }

        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_BYTES, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_TWO_CONSECUTIVE_CHARS, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_1_BYTE, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_1_BYTE, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_2_BYTES, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_2_BYTES, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_3_BYTES, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_3_BYTES, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_4_BYTES, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_4_BYTES, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_1_CHAR, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_1_CHAR, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_2_CHARS, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_2_CHARS, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_3_CHARS, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_3_CHARS, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        link(new AMD64ArrayIndexOfStub(AMD64ArrayIndexOf.STUB_INDEX_OF_4_CHARS, options, providers,
                        registerStubCall(AMD64ArrayIndexOf.STUB_INDEX_OF_4_CHARS, LEAF, REEXECUTABLE, NO_LOCATIONS)));

        super.initialize(providers, options);
    }

    @Override
    public Value[] getNativeABICallerSaveRegisters() {
        return nativeABICallerSaveRegisters;
    }

    @Override
    protected void registerMathStubs(GraalHotSpotVMConfig hotSpotVMConfig, HotSpotProviders providers, OptionValues options) {
        if (GraalArithmeticStubs.getValue(options)) {
            link(new AMD64MathStub(SIN, options, providers, registerStubCall(SIN.foreignCallDescriptor, LEAF, REEXECUTABLE, NO_LOCATIONS)));
            link(new AMD64MathStub(COS, options, providers, registerStubCall(COS.foreignCallDescriptor, LEAF, REEXECUTABLE, NO_LOCATIONS)));
            link(new AMD64MathStub(TAN, options, providers, registerStubCall(TAN.foreignCallDescriptor, LEAF, REEXECUTABLE, NO_LOCATIONS)));
            link(new AMD64MathStub(EXP, options, providers, registerStubCall(EXP.foreignCallDescriptor, LEAF, REEXECUTABLE, NO_LOCATIONS)));
            link(new AMD64MathStub(LOG, options, providers, registerStubCall(LOG.foreignCallDescriptor, LEAF, REEXECUTABLE, NO_LOCATIONS)));
            link(new AMD64MathStub(LOG10, options, providers, registerStubCall(LOG10.foreignCallDescriptor, LEAF, REEXECUTABLE, NO_LOCATIONS)));
            link(new AMD64MathStub(POW, options, providers, registerStubCall(POW.foreignCallDescriptor, LEAF, REEXECUTABLE, NO_LOCATIONS)));
        } else {
            super.registerMathStubs(hotSpotVMConfig, providers, options);
        }
    }

}
