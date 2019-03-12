/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.NativeCall;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_DREM;
import static org.graalvm.compiler.core.target.Backend.ARITHMETIC_FREM;
import static org.graalvm.compiler.hotspot.HotSpotBackend.BACKEDGE_EVENT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.COUNTERMODE_IMPL_CRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_BLOCK_WITH_ORIGINAL_KEY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.DECRYPT_WITH_ORIGINAL_KEY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ENCRYPT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.ENCRYPT_BLOCK;
import static org.graalvm.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.GHASH_PROCESS_BLOCKS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.IC_MISS_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.INITIALIZE_KLASS_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.INVOCATION_EVENT;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MONTGOMERY_MULTIPLY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MONTGOMERY_SQUARE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MULTIPLY_TO_LEN;
import static org.graalvm.compiler.hotspot.HotSpotBackend.MUL_ADD;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_ARRAY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_ARRAY_OR_NULL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_INSTANCE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_INSTANCE_OR_NULL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY;
import static org.graalvm.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY_OR_NULL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_DYNAMIC_INVOKE;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_KLASS_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_METHOD_BY_SYMBOL_AND_LOAD_COUNTERS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.RESOLVE_STRING_BY_SYMBOL;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA2_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA2_IMPL_COMPRESS_MB;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA5_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA5_IMPL_COMPRESS_MB;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA_IMPL_COMPRESS;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SHA_IMPL_COMPRESS_MB;
import static org.graalvm.compiler.hotspot.HotSpotBackend.SQUARE_TO_LEN;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER;
import static org.graalvm.compiler.hotspot.HotSpotBackend.VECTORIZED_MISMATCHED;
import static org.graalvm.compiler.hotspot.HotSpotBackend.VM_ERROR;
import static org.graalvm.compiler.hotspot.HotSpotBackend.WRONG_METHOD_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Reexecutability.NOT_REEXECUTABLE;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Reexecutability.REEXECUTABLE_ONLY_AFTER_EXCEPTION;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_REGISTERS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.PRESERVES_REGISTERS;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.LEAF;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.LEAF_NOFP;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.Transition.STACK_INSPECTABLE_LEAF;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.DEOPTIMIZATION_HANDLER;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.ENABLE_STACK_RESERVED_ZONE;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.THROW_DELAYED_STACKOVERFLOW_ERROR;
import static org.graalvm.compiler.hotspot.HotSpotHostBackend.UNCOMMON_TRAP_HANDLER;
import static org.graalvm.compiler.hotspot.replacements.AssertionSnippets.ASSERTION_VM_MESSAGE_C;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_END_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_TOP_LOCATION;
import static org.graalvm.compiler.hotspot.replacements.MonitorSnippets.MONITORENTER;
import static org.graalvm.compiler.hotspot.replacements.MonitorSnippets.MONITOREXIT;
import static org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.DYNAMIC_NEW_INSTANCE;
import static org.graalvm.compiler.hotspot.replacements.NewObjectSnippets.DYNAMIC_NEW_INSTANCE_OR_NULL;
import static org.graalvm.compiler.hotspot.replacements.ThreadSubstitutions.THREAD_IS_INTERRUPTED;
import static org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.G1WBPOSTCALL;
import static org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.G1WBPRECALL;
import static org.graalvm.compiler.hotspot.replacements.WriteBarrierSnippets.VALIDATE_OBJECT;
import static org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub.EXCEPTION_HANDLER_FOR_PC;
import static org.graalvm.compiler.hotspot.stubs.StubUtil.VM_MESSAGE_C;
import static org.graalvm.compiler.hotspot.stubs.UnwindExceptionToCallerStub.EXCEPTION_HANDLER_FOR_RETURN_ADDRESS;
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
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import java.util.EnumMap;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.CompilerRuntimeHotSpotVMConfig;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.stubs.ArrayStoreExceptionStub;
import org.graalvm.compiler.hotspot.stubs.ClassCastExceptionStub;
import org.graalvm.compiler.hotspot.stubs.CreateExceptionStub;
import org.graalvm.compiler.hotspot.stubs.DivisionByZeroExceptionStub;
import org.graalvm.compiler.hotspot.stubs.ExceptionHandlerStub;
import org.graalvm.compiler.hotspot.stubs.IntegerExactOverflowExceptionStub;
import org.graalvm.compiler.hotspot.stubs.LongExactOverflowExceptionStub;
import org.graalvm.compiler.hotspot.stubs.NullPointerExceptionStub;
import org.graalvm.compiler.hotspot.stubs.OutOfBoundsExceptionStub;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.hotspot.stubs.UnwindExceptionToCallerStub;
import org.graalvm.compiler.hotspot.stubs.VerifyOopStub;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordTypes;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * HotSpot implementation of {@link ForeignCallsProvider}.
 */
public abstract class HotSpotHostForeignCallsProvider extends HotSpotForeignCallsProviderImpl implements ArrayCopyForeignCalls {

    public static final ForeignCallDescriptor JAVA_TIME_MILLIS = new ForeignCallDescriptor("javaTimeMillis", long.class);
    public static final ForeignCallDescriptor JAVA_TIME_NANOS = new ForeignCallDescriptor("javaTimeNanos", long.class);

    public static final ForeignCallDescriptor NOTIFY = new ForeignCallDescriptor("object_notify", boolean.class, Object.class);
    public static final ForeignCallDescriptor NOTIFY_ALL = new ForeignCallDescriptor("object_notifyAll", boolean.class, Object.class);

    public HotSpotHostForeignCallsProvider(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    WordTypes wordTypes) {
        super(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes);
    }

    protected static void link(Stub stub) {
        stub.getLinkage().setCompiledStub(stub);
    }

    @Override
    public ForeignCallDescriptor lookupCheckcastArraycopyDescriptor(boolean uninit) {
        return checkcastArraycopyDescriptors[uninit ? 1 : 0];
    }

    @Override
    public ForeignCallDescriptor lookupArraycopyDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny) {
        if (uninit) {
            assert kind == JavaKind.Object;
            assert !killAny : "unsupported";
            return uninitObjectArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0];
        }
        if (killAny) {
            return arraycopyDescriptorsKillAny[aligned ? 1 : 0][disjoint ? 1 : 0].get(kind);
        }
        return arraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0].get(kind);
    }

    @SuppressWarnings("unchecked") private static final EnumMap<JavaKind, ForeignCallDescriptor>[][] arraycopyDescriptors = (EnumMap<JavaKind, ForeignCallDescriptor>[][]) new EnumMap<?, ?>[2][2];
    @SuppressWarnings("unchecked") private static final EnumMap<JavaKind, ForeignCallDescriptor>[][] arraycopyDescriptorsKillAny = (EnumMap<JavaKind, ForeignCallDescriptor>[][]) new EnumMap<?, ?>[2][2];

    private static final ForeignCallDescriptor[][] uninitObjectArraycopyDescriptors = new ForeignCallDescriptor[2][2];
    private static final ForeignCallDescriptor[] checkcastArraycopyDescriptors = new ForeignCallDescriptor[2];

    static {
        // Populate the EnumMap instances
        for (int i = 0; i < arraycopyDescriptors.length; i++) {
            for (int j = 0; j < arraycopyDescriptors[i].length; j++) {
                arraycopyDescriptors[i][j] = new EnumMap<>(JavaKind.class);
                arraycopyDescriptorsKillAny[i][j] = new EnumMap<>(JavaKind.class);
            }
        }
    }

    private void registerArraycopyDescriptor(EconomicMap<Long, ForeignCallDescriptor> descMap, JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny, long routine) {
        ForeignCallDescriptor desc = descMap.get(routine);
        if (desc == null) {
            desc = buildDescriptor(kind, aligned, disjoint, uninit, killAny, routine);
            descMap.put(routine, desc);
        }
        if (uninit) {
            assert kind == JavaKind.Object;
            uninitObjectArraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0] = desc;
        } else if (killAny) {
            arraycopyDescriptorsKillAny[aligned ? 1 : 0][disjoint ? 1 : 0].put(kind, desc);
        } else {
            arraycopyDescriptors[aligned ? 1 : 0][disjoint ? 1 : 0].put(kind, desc);
        }
    }

    private ForeignCallDescriptor buildDescriptor(JavaKind kind, boolean aligned, boolean disjoint, boolean uninit, boolean killAny, long routine) {
        assert !uninit || kind == JavaKind.Object;
        String name = kind + (aligned ? "Aligned" : "") + (disjoint ? "Disjoint" : "") + (uninit ? "Uninit" : "") + "Arraycopy" + (killAny ? "KillAny" : "");
        ForeignCallDescriptor desc = new ForeignCallDescriptor(name, void.class, Word.class, Word.class, Word.class);
        LocationIdentity killed = killAny ? LocationIdentity.any() : NamedLocationIdentity.getArrayLocation(kind);
        registerForeignCall(desc, routine, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, killed);
        return desc;
    }

    private void registerCheckcastArraycopyDescriptor(boolean uninit, long routine) {
        String name = "Object" + (uninit ? "Uninit" : "") + "CheckcastArraycopy";
        // Input:
        // c_rarg0 - source array address
        // c_rarg1 - destination array address
        // c_rarg2 - element count, treated as ssize_t, can be zero
        // c_rarg3 - size_t ckoff (super_check_offset)
        // c_rarg4 - oop ckval (super_klass)
        // return: 0 = success, n = number of copied elements xor'd with -1.
        ForeignCallDescriptor desc = new ForeignCallDescriptor(name, int.class, Word.class, Word.class, Word.class, Word.class, Word.class);
        LocationIdentity killed = NamedLocationIdentity.any();
        registerForeignCall(desc, routine, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, NOT_REEXECUTABLE, killed);
        checkcastArraycopyDescriptors[uninit ? 1 : 0] = desc;
    }

    private void registerArrayCopy(JavaKind kind,
                    long routine,
                    long alignedRoutine,
                    long disjointRoutine,
                    long alignedDisjointRoutine) {
        registerArrayCopy(kind, routine, alignedRoutine, disjointRoutine, alignedDisjointRoutine, false);
    }

    private void registerArrayCopy(JavaKind kind,
                    long routine,
                    long alignedRoutine,
                    long disjointRoutine,
                    long alignedDisjointRoutine,
                    boolean uninit) {
        /*
         * Sometimes the same function is used for multiple cases so share them when that's the case
         * but only within the same Kind. For instance short and char are the same copy routines but
         * they kill different memory so they still have to be distinct.
         */
        EconomicMap<Long, ForeignCallDescriptor> descMap = EconomicMap.create();
        registerArraycopyDescriptor(descMap, kind, false, false, uninit, false, routine);
        registerArraycopyDescriptor(descMap, kind, true, false, uninit, false, alignedRoutine);
        registerArraycopyDescriptor(descMap, kind, false, true, uninit, false, disjointRoutine);
        registerArraycopyDescriptor(descMap, kind, true, true, uninit, false, alignedDisjointRoutine);

        if (!uninit) {
            EconomicMap<Long, ForeignCallDescriptor> killAnyDescMap = EconomicMap.create();
            registerArraycopyDescriptor(killAnyDescMap, kind, false, false, uninit, true, routine);
            registerArraycopyDescriptor(killAnyDescMap, kind, true, false, uninit, true, alignedRoutine);
            registerArraycopyDescriptor(killAnyDescMap, kind, false, true, uninit, true, disjointRoutine);
            registerArraycopyDescriptor(killAnyDescMap, kind, true, true, uninit, true, alignedDisjointRoutine);
        }
    }

    public void initialize(HotSpotProviders providers, OptionValues options) {
        GraalHotSpotVMConfig c = runtime.getVMConfig();
        registerForeignCall(DEOPTIMIZATION_HANDLER, c.handleDeoptStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(UNCOMMON_TRAP_HANDLER, c.uncommonTrapStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(IC_MISS_HANDLER, c.inlineCacheMissStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);

        if (c.enableStackReservedZoneAddress != 0) {
            assert c.throwDelayedStackOverflowErrorEntry != 0 : "both must exist";
            registerForeignCall(ENABLE_STACK_RESERVED_ZONE, c.enableStackReservedZoneAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
            registerForeignCall(THROW_DELAYED_STACKOVERFLOW_ERROR, c.throwDelayedStackOverflowErrorEntry, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        }

        registerForeignCall(JAVA_TIME_MILLIS, c.javaTimeMillisAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(JAVA_TIME_NANOS, c.javaTimeNanosAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);

        registerMathStubs(c, providers, options);

        registerForeignCall(ARITHMETIC_FREM, c.fremAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ARITHMETIC_DREM, c.dremAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);

        registerForeignCall(LOAD_AND_CLEAR_EXCEPTION, c.loadAndClearExceptionAddress, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());

        registerForeignCall(EXCEPTION_HANDLER_FOR_PC, c.exceptionHandlerForPcAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());
        registerForeignCall(EXCEPTION_HANDLER_FOR_RETURN_ADDRESS, c.exceptionHandlerForReturnAddressAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, any());

        CreateExceptionStub.registerForeignCalls(c, this);

        /*
         * This message call is registered twice, where the second one must only be used for calls
         * that do not return, i.e., that exit the VM.
         */
        registerForeignCall(VM_MESSAGE_C, c.vmMessageAddress, NativeCall, DESTROYS_REGISTERS, SAFEPOINT, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(ASSERTION_VM_MESSAGE_C, c.vmMessageAddress, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);

        linkForeignCall(options, providers, NEW_INSTANCE, c.newInstanceAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
        linkForeignCall(options, providers, NEW_ARRAY, c.newArrayAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
        linkForeignCall(options, providers, NEW_MULTI_ARRAY, c.newMultiArrayAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
        linkForeignCall(options, providers, DYNAMIC_NEW_INSTANCE, c.dynamicNewInstanceAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE);

        if (c.areNullAllocationStubsAvailable()) {
            linkForeignCall(options, providers, NEW_INSTANCE_OR_NULL, c.newInstanceOrNullAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            linkForeignCall(options, providers, NEW_ARRAY_OR_NULL, c.newArrayOrNullAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            linkForeignCall(options, providers, NEW_MULTI_ARRAY_OR_NULL, c.newMultiArrayOrNullAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            linkForeignCall(options, providers, DYNAMIC_NEW_INSTANCE_OR_NULL, c.dynamicNewInstanceOrNullAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE);
        }

        link(new ExceptionHandlerStub(options, providers, foreignCalls.get(EXCEPTION_HANDLER)));
        link(new UnwindExceptionToCallerStub(options, providers, registerStubCall(UNWIND_EXCEPTION_TO_CALLER, SAFEPOINT, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any())));
        link(new VerifyOopStub(options, providers, registerStubCall(VERIFY_OOP, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS)));

        EnumMap<BytecodeExceptionKind, ForeignCallDescriptor> exceptionRuntimeCalls = DefaultHotSpotLoweringProvider.RuntimeCalls.runtimeCalls;
        link(new ArrayStoreExceptionStub(options, providers, registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.ARRAY_STORE), SAFEPOINT, REEXECUTABLE, any())));
        link(new ClassCastExceptionStub(options, providers, registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.CLASS_CAST), SAFEPOINT, REEXECUTABLE, any())));
        link(new NullPointerExceptionStub(options, providers, registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.NULL_POINTER), SAFEPOINT, REEXECUTABLE, any())));
        link(new OutOfBoundsExceptionStub(options, providers, registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.OUT_OF_BOUNDS), SAFEPOINT, REEXECUTABLE, any())));
        link(new DivisionByZeroExceptionStub(options, providers, registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.DIVISION_BY_ZERO), SAFEPOINT, REEXECUTABLE, any())));
        link(new IntegerExactOverflowExceptionStub(options, providers, registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.INTEGER_EXACT_OVERFLOW), SAFEPOINT, REEXECUTABLE, any())));
        link(new LongExactOverflowExceptionStub(options, providers, registerStubCall(exceptionRuntimeCalls.get(BytecodeExceptionKind.LONG_EXACT_OVERFLOW), SAFEPOINT, REEXECUTABLE, any())));

        linkForeignCall(options, providers, IDENTITY_HASHCODE, c.identityHashCodeAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE_ONLY_AFTER_EXCEPTION, MARK_WORD_LOCATION);
        linkForeignCall(options, providers, REGISTER_FINALIZER, c.registerFinalizerAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());
        linkForeignCall(options, providers, MONITORENTER, c.monitorenterAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());
        linkForeignCall(options, providers, MONITOREXIT, c.monitorexitAddress, PREPEND_THREAD, STACK_INSPECTABLE_LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());
        linkForeignCall(options, providers, NOTIFY, c.notifyAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());
        linkForeignCall(options, providers, NOTIFY_ALL, c.notifyAllAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());
        linkForeignCall(options, providers, LOG_PRINTF, c.logPrintfAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(options, providers, LOG_OBJECT, c.logObjectAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(options, providers, LOG_PRIMITIVE, c.logPrimitiveAddress, PREPEND_THREAD, LEAF, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(options, providers, VM_ERROR, c.vmErrorAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(options, providers, OSR_MIGRATION_END, c.osrMigrationEndAddress, DONT_PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NO_LOCATIONS);
        linkForeignCall(options, providers, G1WBPRECALL, c.writeBarrierPreAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(options, providers, G1WBPOSTCALL, c.writeBarrierPostAddress, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
        linkForeignCall(options, providers, VALIDATE_OBJECT, c.validateObject, PREPEND_THREAD, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);

        if (GeneratePIC.getValue(options)) {
            registerForeignCall(WRONG_METHOD_HANDLER, c.handleWrongMethodStub, NativeCall, PRESERVES_REGISTERS, LEAF_NOFP, REEXECUTABLE, NO_LOCATIONS);
            CompilerRuntimeHotSpotVMConfig cr = new CompilerRuntimeHotSpotVMConfig(HotSpotJVMCIRuntime.runtime().getConfigStore());
            linkForeignCall(options, providers, RESOLVE_STRING_BY_SYMBOL, cr.resolveStringBySymbol, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, TLAB_TOP_LOCATION, TLAB_END_LOCATION);
            linkForeignCall(options, providers, RESOLVE_DYNAMIC_INVOKE, cr.resolveDynamicInvoke, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, any());
            linkForeignCall(options, providers, RESOLVE_KLASS_BY_SYMBOL, cr.resolveKlassBySymbol, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, any());
            linkForeignCall(options, providers, RESOLVE_METHOD_BY_SYMBOL_AND_LOAD_COUNTERS, cr.resolveMethodBySymbolAndLoadCounters, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, NO_LOCATIONS);
            linkForeignCall(options, providers, INITIALIZE_KLASS_BY_SYMBOL, cr.initializeKlassBySymbol, PREPEND_THREAD, SAFEPOINT, NOT_REEXECUTABLE, any());
            linkForeignCall(options, providers, INVOCATION_EVENT, cr.invocationEvent, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, NO_LOCATIONS);
            linkForeignCall(options, providers, BACKEDGE_EVENT, cr.backedgeEvent, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, NO_LOCATIONS);
        }

        // Cannot be a leaf as VM acquires Thread_lock which requires thread_in_vm state
        linkForeignCall(options, providers, THREAD_IS_INTERRUPTED, c.threadIsInterruptedAddress, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE_ONLY_AFTER_EXCEPTION, any());

        linkForeignCall(options, providers, TEST_DEOPTIMIZE_CALL_INT, c.testDeoptimizeCallInt, PREPEND_THREAD, SAFEPOINT, REEXECUTABLE, any());

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

        registerForeignCall(GENERIC_ARRAYCOPY, c.genericArraycopy, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());
        registerForeignCall(UNSAFE_ARRAYCOPY, c.unsafeArraycopy, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());

        if (c.useMultiplyToLenIntrinsic()) {
            registerForeignCall(MULTIPLY_TO_LEN, c.multiplyToLen, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                            NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }

        if (c.useSHA1Intrinsics()) {
            registerForeignCall(SHA_IMPL_COMPRESS, c.sha1ImplCompress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());
            registerForeignCall(SHA_IMPL_COMPRESS_MB, c.sha1ImplCompressMultiBlock, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());
        }
        if (c.useSHA256Intrinsics()) {
            registerForeignCall(SHA2_IMPL_COMPRESS, c.sha256ImplCompress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());
            registerForeignCall(SHA2_IMPL_COMPRESS_MB, c.sha256ImplCompressMultiBlock, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());
        }
        if (c.useSHA512Intrinsics()) {
            registerForeignCall(SHA5_IMPL_COMPRESS, c.sha512ImplCompress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());
            registerForeignCall(SHA5_IMPL_COMPRESS_MB, c.sha512ImplCompressMultiBlock, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());
        }
        if (c.useGHASHIntrinsics()) {
            registerForeignCall(GHASH_PROCESS_BLOCKS, c.ghashProcessBlocks, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.any());
        }
        if (c.useMulAddIntrinsic()) {
            registerForeignCall(MUL_ADD, c.mulAdd, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }
        if (c.useMontgomeryMultiplyIntrinsic()) {
            registerForeignCall(MONTGOMERY_MULTIPLY, c.montgomeryMultiply, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                            NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }
        if (c.useMontgomerySquareIntrinsic()) {
            registerForeignCall(MONTGOMERY_SQUARE, c.montgomerySquare, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                            NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }
        if (c.useSquareToLenIntrinsic()) {
            registerForeignCall(SQUARE_TO_LEN, c.squareToLen, NativeCall, DESTROYS_REGISTERS, LEAF_NOFP, REEXECUTABLE_ONLY_AFTER_EXCEPTION, NamedLocationIdentity.getArrayLocation(JavaKind.Int));
        }

        if (c.useAESIntrinsics) {
            /*
             * When the java.ext.dirs property is modified then the crypto classes might not be
             * found. If that's the case we ignore the ClassNotFoundException and continue since we
             * cannot replace a non-existing method anyway.
             */
            try {
                // These stubs do callee saving
                registerForeignCall(ENCRYPT_BLOCK, c.aescryptEncryptBlockStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
                registerForeignCall(DECRYPT_BLOCK, c.aescryptDecryptBlockStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
                registerForeignCall(DECRYPT_BLOCK_WITH_ORIGINAL_KEY, c.aescryptDecryptBlockStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
            } catch (GraalError e) {
                if (!(e.getCause() instanceof ClassNotFoundException)) {
                    throw e;
                }
            }
            try {
                // These stubs do callee saving
                registerForeignCall(ENCRYPT, c.cipherBlockChainingEncryptAESCryptStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
                registerForeignCall(DECRYPT, c.cipherBlockChainingDecryptAESCryptStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
                registerForeignCall(DECRYPT_WITH_ORIGINAL_KEY, c.cipherBlockChainingDecryptAESCryptStub, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                                NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
            } catch (GraalError e) {
                if (!(e.getCause() instanceof ClassNotFoundException)) {
                    throw e;
                }
            }
        }

        if (c.useAESCTRIntrinsics) {
            assert (c.counterModeAESCrypt != 0L);
            registerForeignCall(COUNTERMODE_IMPL_CRYPT, c.counterModeAESCrypt, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                            NamedLocationIdentity.any());
        }

        if (c.useVectorizedMismatchIntrinsic) {
            assert (c.vectorizedMismatch != 0L);
            registerForeignCall(VECTORIZED_MISMATCHED, c.vectorizedMismatch, NativeCall, PRESERVES_REGISTERS, LEAF, REEXECUTABLE_ONLY_AFTER_EXCEPTION,
                            NamedLocationIdentity.any());

        }
    }

    public HotSpotForeignCallLinkage getForeignCall(ForeignCallDescriptor descriptor) {
        assert foreignCalls != null : descriptor;
        return foreignCalls.get(descriptor);
    }

    @SuppressWarnings("unused")
    protected void registerMathStubs(GraalHotSpotVMConfig hotSpotVMConfig, HotSpotProviders providers, OptionValues options) {
        registerForeignCall(SIN.foreignCallDescriptor, hotSpotVMConfig.arithmeticSinAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(COS.foreignCallDescriptor, hotSpotVMConfig.arithmeticCosAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(TAN.foreignCallDescriptor, hotSpotVMConfig.arithmeticTanAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(EXP.foreignCallDescriptor, hotSpotVMConfig.arithmeticExpAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(LOG.foreignCallDescriptor, hotSpotVMConfig.arithmeticLogAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(LOG10.foreignCallDescriptor, hotSpotVMConfig.arithmeticLog10Address, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
        registerForeignCall(POW.foreignCallDescriptor, hotSpotVMConfig.arithmeticPowAddress, NativeCall, DESTROYS_REGISTERS, LEAF, REEXECUTABLE, NO_LOCATIONS);
    }
}
