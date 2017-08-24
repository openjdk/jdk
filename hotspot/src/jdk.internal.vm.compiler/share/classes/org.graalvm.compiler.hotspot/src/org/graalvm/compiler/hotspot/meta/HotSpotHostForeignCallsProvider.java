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
package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_DREM;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_FREM;
import static org.graalvm.compiler.hotspot.HotSpotBackend.INITIALIZE_KLASS_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BACKEDGE_EVENT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.INVOCATION_EVENT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_KLASS_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_METHOD_BY_SYMBOL_AND_LOAD_COUNTERS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_STRING_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_BLOCK_WITH_ORIGINAL_KEY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_WITH_ORIGINAL_KEY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ENCRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ENCRYPT_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.FETCH_UNROLL_INFO;
import static org.graalvm.compiler.hotspot.HotSpotBackend.IC_MISS_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MONTGOMERY_MULTIPLY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MONTGOMERY_SQUARE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MULTIPLY_TO_LEN;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MUL_ADD;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_ARRAY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_INSTANCE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA2_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA5_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SQUARE_TO_LEN;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UNCOMMON_TRAP;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UNPACK_FRAMES;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.VM_ERROR;
import static org.graalvm.compiler.hotspot.HotSpotBackend.WRONG_METHOD_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.Options.PreferGraalStubs;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_REGISTERS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.PRESERVES_REGISTERS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.LEAF;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.LEAF_NOFP;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.STACK_INSPECTABLE_LEAF;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.DEOPTIMIZATION_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.UNCOMMON_TRAP_HANDLER;
import static org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider.RuntimeCalls.CREATE_ARRAY_STORE_EXCEPTION;
import static org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider.RuntimeCalls.CREATE_CLASS_CAST_EXCEPTION;
import static org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider.RuntimeCalls.CREATE_NULL_POINTER_EXCEPTION;
import static org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider.RuntimeCalls.CREATE_OUT_OF_BOUNDS_EXCEPTION;
import static org.graalvm.compiler.hotspot.replacements.AssertionSnippets.ASSERTION_VM_MESSAGE_C;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_END_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_TOP_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.MonitorSnippets.MONITORENTER;
import static org.graalvm.compiler.hotspot.replacements.MonitorSnippets.MONITOREXIT;
import static org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.DYNAMIC_NEW_ARRAY;
import static org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.DYNAMIC_NEW_INSTANCE;
import static org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.INIT_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.ThreadSubstitutions.THREAD_IS_INTERRUPTED;
import static org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.G1WBPOSTCALL;
import static org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.G1WBPRECALL;
import static org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.VALIDATE_OBJECT;
import static org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub.EXCEPTION_HANDLER_FOR_PC;
import static org.graalvm.compiler.hotspot.stubs.NewArrayStub.NEW_ARRAY_C;
import static org.graalvm.compiler.hotspot.stubs.NewInstanceStub.NEW_INSTANCE_C;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.VM_MESSAGE_C;
import static org.graalvm.compiler.hotspot.stubs.UnwindExceptionToCallerStub.EXCEPTION_HANDLER_FOR_RETURN_ADDRESS;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.any;
import static org.graalvm.compiler.nodes.java.ForeignCallDescriptors.REGISTER_FINALIZER;
import static org.graalvm.compiler.replacements.Log.LOG_OBJECT;
import static org.graalvm.compiler.replacements.Log.LOG_PRIMITIVE;
import static org.graalvm.compiler.replacements.Log.LOG_PRINTF;
import static org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation.POW;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.COS;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.EXP;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.LOG10;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.SIN;
import static org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation.TAN;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.CompilerRuntimeHotSpotVMConfig;
import org.graalvm.compiler.hotspot.stubs.ArrayStoreExceptionStub;
import org.graalvm.compiler.hotspot.stubs.ClassCastExceptionStub;
import org.graalvm.compiler.hotspot.stubs.CreateExceptionStub;
import org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub;
import org.graalvm.compiler.hotspot.stubs.NewArrayStub;
import org.graalvm.compiler.hotspot.stubs.NewInstanceStub;
import org.graalvm.compiler.hotspot.stubs.NullPointerExceptionStub;
import org.graalvm.compiler.hotspot.stubs.OutOfBoundsExceptionStub;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.hotspot.stubs.UnwindExceptionToCallerStub;
import org.graalvm.compiler.hotspot.stubs.VerifyOopStub;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * HotSpot implementation of {@link ForeignCallsProvider}.
 */
public abstract class HotSpotHostForeignCallsProvider extends HotSpotForeignCallsProviderImpl {

    public static final ForeignCallDescriptor JAVA_TIME_MILLIS = new ForeignCallDescriptor("javaTimeMillis", long.class);
    public static final ForeignCallDescriptor JAVA_TIME_NANOS = new ForeignCallDescriptor("javaTimeNanos", long.class);

    public HotSpotHostForeignCallsProvider(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    WordTypes wordTypes) {
        super(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes);
    }

    protected static void link(Stub stub) {
        stub.getLinkage().setCompiledStub(stub);
    }

    public static ForeignCallDescriptor lookupCheckcastArraycopyDescriptor(boolean uninit) {
        return checkcastArraycopyDescriptors[uninit ? 1 : 0];
    }

    public static ForeignCallDescriptor lookupArraycopyDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny) {
        if (uninit) {
            assert kind == JavaKind.Object;
            assert !killAny : "unsupported";
            return uninitObjectArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0];
        }
        if (killAny) {
            assert kind == JavaKind.Object;
            return objectArraycopyDescriptorsKillAny[aligned ? 1 : 0][disjoint ? 1 : 0];
        }
        return arraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0].get(kind);
    }

    @SuppressWarnings({"unchecked"}) private static final EnumMap<JavaKind, ForeignCallDescriptor>[][] arraycopyDescriptors = (EnumMap<JavaKind, ForeignCallDescriptor>[][]) new EnumMap<?, ?>[2][2];

    private static final ForeignCallDescriptor[][] uninitObjectArraycopyDescriptors = new ForeignCallDescriptor[2][2];
    private static final ForeignCallDescriptor[] checkcastArraycopyDescriptors = new ForeignCallDescriptor[2];
    private static ForeignCallDescriptor[][] objectArraycopyDescriptorsKillAny = new ForeignCallDescriptor[2][2];

    static {
        // Populate the EnumMap instances
        for (int i = 0; i < arraycopyDescriptors.length; i++) {
            for (int j = 0; j < arraycopyDescriptors[i].length; j++) {
                arraycopyDescriptors[i][j] = new EnumMap<>(JavaKind.class);
            }
        }
    }

    private void registerArraycopyDescriptor(Map<Long, ForeignCallDescriptor> descMap, JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny, long routine) {
        ForeignCallDescriptor desc = descMap.get(routine);
        if (desc == null) {
            desc = buildDescriptor(kind, aligned, disjoint, uninit, killAny, routine);
            descMap.put(routine, desc);
        }
        if (uninit) {
            assert kind == JavaKind.Object;
            uninitObjectArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0] = desc;
        } else {
            arraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0].put(kind, desc);
        }
    }

    private ForeignCallDescriptor buildDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny, long routine) {
        assert !killAny || kind == JavaKind.Object;
        String name = kind + (aligned ? "Aligned" : "") + (disjoint ? "Disjoint" : "") + (uninit ? "Uninit" : "") + "Arraycopy" + (killAny ? "KillAny" : "");
        ForeignCallDescriptor desc = new ForeignCallDescriptor(name, void.class, Word.class, Word.class, Word.class);
        LocationIdentity killed = killAny ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(kind);
        registerForeignCall(desc, routine, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, killed);
        return desc;
    }

    private void registerCheckcastArraycopyDescriptor(boolean uninit, long routine) {
        String name = "Object" + (uninit ? "Uninit" : "") + "Checkcast";
        // Input:
        // c_rarg0 - source array address
        // c_rarg1 - destination array address
        // c_rarg2 - element count, treated as ssize_t, can be zero
        // c_rarg3 - size_t ckoff (super_check_offset)
        // c_rarg4 - oop ckval (super_klass)
        // return: 0 = success, n = number of copied elements xor'd with -1.
        ForeignCallDescriptor desc = new ForeignCallDescriptor(name, int.class, Word.class, Word.class, Word.class, Word.class, Word.class);
        LocationIdentity killed = NamedLocationIdentity.getArrayLocation(JavaKind.Object);
        registerForeignCall(desc, routine, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, killed);
        checkcastArraycopyDescriptors[uninit ? 1 : 0] = desc;
    }

    private void registerArrayCopy(JavaKind kind, long routine, long alignedRoutine, long disjointRoutine, long alignedDisjointRoutine) {
        registerArrayCopy(kind, routine, alignedRoutine, disjointRoutine, alignedDisjointRoutine, false);
    }

    private void registerArrayCopy(JavaKind kind, long routine, long alignedRoutine, long disjointRoutine, long alignedDisjointRoutine, boolean uninit) {
        /*
         * Sometimes the same function is used for multiple cases so share them when that's the case
         * but only within the same Kind. For instance short and char are the same copy routines but
         * they kill different memory so they still have to be distinct.
         */
        Map<Long, ForeignCallDescriptor> descMap = new HashMap<>();
        registerArraycopyDescriptor(descMap, kind, false, false, uninit, false, routine);
        registerArraycopyDescriptor(descMap, kind, true, false, uninit, false, alignedRoutine);
        registerArraycopyDescriptor(descMap, kind, false, true, uninit, false, disjointRoutine);
        registerArraycopyDescriptor(descMap, kind, true, true, uninit, false, alignedDisjointRoutine);

        if (kind == JavaKind.Object && !uninit) {
            objectArraycopyDescriptorsKillAny[0][0] = buildDescriptor(kind, false, false, uninit, true, routine);
            objectArraycopyDescriptorsKillAny[1][0] = buildDescriptor(kind, true, false, uninit, true, alignedRoutine);
            objectArraycopyDescriptorsKillAny[0][1] = buildDescriptor(kind, false, true, uninit, true, disjointRoutine);
            objectArraycopyDescriptorsKillAny[1][1] = buildDescriptor(kind, true, true, uninit, true, alignedDisjointRoutine);
        }
    }

    public void initialize(HotSpotProviders providers) {
        GraalHotSpotVMConfig c = runtime.getVMConfig();
        if (!PreferGraalStubs.getValue()) {
            registerForeignCall(DEOPTIMIZATION_HANDLER, c.handleDeoptStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
            registerForeignCall(UNCOMMON_TRAP_HANDLER, c.uncommonTrapStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        }
        registerForeignCall(IC_MISS_HANDLER, c.inlineCacheMissStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);

        registerForeignCall(JAVA_TIME_MILLIS, c.javaTimeMillisAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(JAVA_TIME_NANOS, c.javaTimeNanosAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(SIN.foreignCallDescriptor, c.arithmeticSinAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(COS.foreignCallDescriptor, c.arithmeticCosAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(TAN.foreignCallDescriptor, c.arithmeticTanAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(EXP.foreignCallDescriptor, c.arithmeticExpAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(LOG.foreignCallDescriptor, c.arithmeticLogAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(LOG10.foreignCallDescriptor, c.arithmeticLog10Address, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(POW.foreignCallDescriptor, c.arithmeticPowAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_FREM, c.fremAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_DREM, c.dremAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);

        registerForeignCall(LOAD_AND_CLEAR_EXCEPTION, c.loadAndClearExceptionAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, any());

        registerForeignCall(EXCEPTION_HANDLER_FOR_PC, c.exceptionHandlerForPcAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
        registerForeignCall(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, c.exceptionHandlerForReturnAddressAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
        registerForeignCall(FETCH_UNROLL_INFO, c.deoptimizationFetchUnrollInfo, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
        registerForeignCall(NEW_ARRAY_C, c.newArrayAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
        registerForeignCall(NEW_INSTANCE_C, c.newInstanceAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
        registerForeignCall(UNCOMMON_TRAP, c.deoptimizationUncommonTrap, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, NOT_REEXECUTABLE, any());

        /*
         * We cannot use LEAF_SP here because on some architectures we have to align the stack
         * manually before calling into the VM. See {@link
         * AMD64HotSpotEnterUnpackFramesStackFrameOp#emitCode}.
         */
        registerForeignCall(UNPACK_FRAMES, c.deoptimizationUnpackFrames, NativeCall, DESTROYS_REGISTERS, LEAF, NOT_REEXECUTABLE, any());

        CreateExceptionStub.registerForeignCalls(c, this);

        /*
         * This message call is registered twice, where the second one must only be used for calls
         * that do not return, i.e., that exit the VM.
         */
        registerForeignCall(VM_MESSAGE_C, c.vmMessageAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ASSERTION_VM_MESSAGE_C, c.vmMessageAddress, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);

        link(new NewInstanceStub(providers, registerStubCall(NEW_INSTANCE, REEXECUTABLE, SAFEPOINT, INIT_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION)));
        link(new NewArrayStub(providers, registerStubCall(NEW_ARRAY, REEXECUTABLE, SAFEPOINT, INIT_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION)));
        link(new ExceptionHandlerStub(providers, foreignCalls.get(EXCEPTION_HANDLER)));
        link(new UnwindExceptionToCallerStub(providers, registerStubCall(UNWIND_EXCEPTION_TO_CALLER, NOT_REEXECUTABLE, SAFEPOINT, any())));
        link(new VerifyOopStub(providers, registerStubCall(VERIFY_OOP, REEXECUTABLE, LEAF_NOFP, NO_LOCATIONS)));
        link(new ArrayStoreExceptionStub(providers, registerStubCall(CREATE_ARRAY_STORE_EXCEPTION, REEXECUTABLE, SAFEPOINT, any())));
        link(new ClassCastExceptionStub(providers, registerStubCall(CREATE_CLASS_CAST_EXCEPTION, REEXECUTABLE, SAFEPOINT, any())));
        link(new NullPointerExceptionStub(providers, registerStubCall(CREATE_NULL_POINTER_EXCEPTION, REEXECUTABLE, SAFEPOINT, any())));
        link(new OutOfBoundsExceptionStub(providers, registerStubCall(CREATE_OUT_OF_BOUNDS_EXCEPTION, REEXECUTABLE, SAFEPOINT, any())));

        linkForeignCall(providers, IDENTITY_HASHCODE, c.identityHashCodeAddress, PREPEND_THREAD, SAFEPOINT, NOT_REEXECUTABLE, MARK_WORD_LOCATION);
        linkForeignCall(providers, REGISTER_FINALIZER, c.registerFinalizerAddress, PREPEND_THREAD, SAFEPOINT, NOT_REEXECUTABLE, any());
        linkForeignCall(providers, MONITORENTER, c.monitorenterAddress, PREPEND_THREAD, SAFEPOINT, NOT_REEXECUTABLE, any());
        linkForeignCall(providers, MONITOREXIT, c.monitorexitAddress, PREPEND_THREAD, STACK_INSPECTABLE_LEAF, NOT_REEXECUTABLE, any());
        linkForeignCall(providers, NEW_MULTI_ARRAY, c.newMultiArrayAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, INIT_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
        linkForeignCall(providers, DYNAMIC_NEW_ARRAY, c.dynamicNewArrayAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, INIT_LOCATION);
        linkForeignCall(providers, DYNAMIC_NEW_INSTANCE, c.dynamicNewInstanceAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, INIT_LOCATION);
        linkForeignCall(providers, LOG_PRINTF, c.logPrintfAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, LOG_OBJECT, c.logObjectAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, LOG_PRIMITIVE, c.logPrimitiveAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, VM_ERROR, c.vmErrorAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, OSR_MIGRATION_END, c.osrMigrationEndAddress, DONT_PREPEND_THREAD, LEAF_NOFP, NOT_REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, G1WBPRECALL, c.writeBarrierPreAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, G1WBPOSTCALL, c.writeBarrierPostAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(providers, VALIDATE_OBJECT, c.validateObject, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);

        if (GeneratePIC.getValue()) {
            registerForeignCall(WRONG_METHOD_HANDLER, c.handleWrongMethodStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
            CompilerRuntimeHotSpotVMConfig cr = new CompilerRuntimeHotSpotVMConfig(HotSpotJVMCIRuntime.runtime().getConfigStore());
            linkForeignCall(providers, RESOLVE_STRING_BY_SYMBOL, cr.resolveStringBySymbol, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, INIT_LOCATION, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            linkForeignCall(providers, RESOLVE_KLASS_BY_SYMBOL, cr.resolveKlassBySymbol, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, any());
            linkForeignCall(providers, RESOLVE_METHOD_BY_SYMBOL_AND_LOAD_COUNTERS, cr.resolveMethodBySymbolAndLoadCounters, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, NO_LOCATIONS);
            linkForeignCall(providers, INITIALIZE_KLASS_BY_SYMBOL, cr.initializeKlassBySymbol, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, any());
            linkForeignCall(providers, INVOCATION_EVENT, cr.invocationEvent, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, NO_LOCATIONS);
            linkForeignCall(providers, BACKEDGE_EVENT, cr.backedgeEvent, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, NO_LOCATIONS);
        }

        // Cannot be a leaf as VM acquires Thread_lock which requires thread_in_vm state
        linkForeignCall(providers, THREAD_IS_INTERRUPTED, c.threadIsInterruptedAddress, PREPEND_THREAD, SAFEPOINT, NOT_REEXECUTABLE, any());

        linkForeignCall(providers, TEST_DEOPTIMIZE_CALL_INT, c.testDeoptimizeCallInt, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, any());

        registerArrayCopy(JavaKind.Byte, c.jbyteArraycopy, c.jbyteAlignedArraycopy, c.jbyteDisjointArraycopy, c.jbyteAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Boolean, c.jbyteArraycopy, c.jbyteAlignedArraycopy, c.jbyteDisjointArraycopy, c.jbyteAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Char, c.jshortArraycopy, c.jshortAlignedArraycopy, c.jshortDisjointArraycopy, c.jshortAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Short, c.jshortArraycopy, c.jshortAlignedArraycopy, c.jshortDisjointArraycopy, c.jshortAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Int, c.jintArraycopy, c.jintAlignedArraycopy, c.jintDisjointArraycopy, c.jintAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Float, c.jintArraycopy, c.jintAlignedArraycopy, c.jintDisjointArraycopy, c.jintAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Long, c.jlongArraycopy, c.jlongAlignedArraycopy, c.jlongDisjointArraycopy, c.jlongAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Double, c.jlongArraycopy, c.jlongAlignedArraycopy, c.jlongDisjointArraycopy, c.jlongAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Object, c.oopArraycopy, c.oopAlignedArraycopy, c.oopDisjointArraycopy, c.oopAlignedDisjointArraycopy);
        registerArrayCopy(JavaKind.Object, c.oopArraycopyUninit, c.oopAlignedArraycopyUninit, c.oopDisjointArraycopyUninit, c.oopAlignedDisjointArraycopyUninit, true);

        registerCheckcastArraycopyDescriptor(true, c.checkcastArraycopyUninit);
        registerCheckcastArraycopyDescriptor(false, c.checkcastArraycopy);

        if (c.useMultiplyToLenIntrinsic()) {
            registerForeignCall(MULTIPLY_TO_LEN, c.multiplyToLen, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }
        if (c.useSHA1Intrinsics()) {
            registerForeignCall(SHA_IMPL_COMPRESS, c.sha1ImplCompress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.any());
        }
        if (c.useSHA256Intrinsics()) {
            registerForeignCall(SHA2_IMPL_COMPRESS, c.sha256ImplCompress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.any());
        }
        if (c.useSHA512Intrinsics()) {
            registerForeignCall(SHA5_IMPL_COMPRESS, c.sha512ImplCompress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.any());
        }
        if (c.useMulAddIntrinsic()) {
            registerForeignCall(MUL_ADD, c.mulAdd, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }
        if (c.useMontgomeryMultiplyIntrinsic()) {
            registerForeignCall(MONTGOMERY_MULTIPLY, c.montgomeryMultiply, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }
        if (c.useMontgomerySquareIntrinsic()) {
            registerForeignCall(MONTGOMERY_SQUARE, c.montgomerySquare, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }
        if (c.useSquareToLenIntrinsic()) {
            registerForeignCall(SQUARE_TO_LEN, c.squareToLen, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }

        if (c.useAESIntrinsics) {
            /*
             * When the java.ext.dirs property is modified then the crypto classes might not be
             * found. If that's the case we ignore the ClassNotFoundException and continue since we
             * cannot replace a non-existing method anyway.
             */
            try {
                // These stubs do callee saving
                registerForeignCall(ENCRYPT_BLOCK, c.aescryptEncryptBlockStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
                registerForeignCall(DECRYPT_BLOCK, c.aescryptDecryptBlockStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
                registerForeignCall(DECRYPT_BLOCK_WITH_ORIGINAL_KEY, c.aescryptDecryptBlockStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
            } catch (GraalError e) {
                if (!(e.getCause() instanceof ClassNotFoundException)) {
                    throw e;
                }
            }
            try {
                // These stubs do callee saving
                registerForeignCall(ENCRYPT, c.cipherBlockChainingEncryptAESCryptStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
                registerForeignCall(DECRYPT, c.cipherBlockChainingDecryptAESCryptStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
                registerForeignCall(DECRYPT_WITH_ORIGINAL_KEY, c.cipherBlockChainingDecryptAESCryptStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
            } catch (GraalError e) {
                if (!(e.getCause() instanceof ClassNotFoundException)) {
                    throw e;
                }
            }
        }
    }

    public HotSpotForeignCallLinkage getForeignCall(ForeignCallDescriptor descriptor) {
        assert foreignCalls != null : descriptor;
        return foreignCalls.get(descriptor);
    }
}
