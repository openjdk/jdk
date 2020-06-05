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


package org.graalvm.compiler.hotspot.meta;

import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCall;
import static jdk.vm.ci.hotspot.HotSpotCallingConventionType.JavaCallee;
import static org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect.DESTROYS_ALL_CALLER_SAVE_REGISTERS;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.NOT_REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability.REEXECUTABLE;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.LEAF_NO_VZERO;
import static org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition.SAFEPOINT;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static jdk.internal.vm.compiler.word.LocationIdentity.any;

import java.util.ArrayList;
import java.util.List;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage.RegisterEffect;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkageImpl;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Reexecutability;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallDescriptor.Transition;
import org.graalvm.compiler.hotspot.stubs.ForeignCallStub;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordTypes;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * HotSpot implementation of {@link HotSpotForeignCallsProvider}.
 */
public abstract class HotSpotForeignCallsProviderImpl implements HotSpotForeignCallsProvider {

    public static final LocationIdentity[] NO_LOCATIONS = {};

    public static final HotSpotForeignCallDescriptor OSR_MIGRATION_END = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, NO_LOCATIONS, "OSR_migration_end", void.class, long.class);
    public static final HotSpotForeignCallDescriptor IDENTITY_HASHCODE = new HotSpotForeignCallDescriptor(SAFEPOINT, NOT_REEXECUTABLE, MARK_WORD_LOCATION, "identity_hashcode", int.class,
                    Object.class);
    public static final HotSpotForeignCallDescriptor VERIFY_OOP = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, REEXECUTABLE, NO_LOCATIONS, "verify_oop", Object.class,
                    Object.class);
    public static final HotSpotForeignCallDescriptor LOAD_AND_CLEAR_EXCEPTION = new HotSpotForeignCallDescriptor(LEAF_NO_VZERO, NOT_REEXECUTABLE, any(), "load_and_clear_exception", Object.class,
                    Word.class);

    public static final HotSpotForeignCallDescriptor TEST_DEOPTIMIZE_CALL_INT = new HotSpotForeignCallDescriptor(SAFEPOINT, REEXECUTABLE, any(), "test_deoptimize_call_int", int.class, int.class);

    protected final HotSpotJVMCIRuntime jvmciRuntime;
    protected final HotSpotGraalRuntimeProvider runtime;

    protected final EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> foreignCalls = EconomicMap.create();
    protected final EconomicMap<ForeignCallSignature, HotSpotForeignCallDescriptor> signatureMap = EconomicMap.create();
    protected final MetaAccessProvider metaAccess;
    protected final CodeCacheProvider codeCache;
    protected final WordTypes wordTypes;

    public HotSpotForeignCallsProviderImpl(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, CodeCacheProvider codeCache,
                    WordTypes wordTypes) {
        this.jvmciRuntime = jvmciRuntime;
        this.runtime = runtime;
        this.metaAccess = metaAccess;
        this.codeCache = codeCache;
        this.wordTypes = wordTypes;
    }

    /**
     * Registers the linkage for a foreign call.
     */
    public HotSpotForeignCallLinkage register(HotSpotForeignCallLinkage linkage) {
        assert !foreignCalls.containsKey(linkage.getDescriptor().getSignature()) : "already registered linkage for " + linkage.getDescriptor();
        foreignCalls.put(linkage.getDescriptor().getSignature(), linkage);
        return linkage;
    }

    /**
     * Creates and registers the details for linking a foreign call to a {@link Stub}.
     *
     * @param descriptor the signature of the call to the stub
     */
    public HotSpotForeignCallLinkage registerStubCall(
                    HotSpotForeignCallDescriptor descriptor,
                    RegisterEffect effect) {
        return register(HotSpotForeignCallLinkageImpl.create(metaAccess,
                        codeCache,
                        wordTypes,
                        this,
                        descriptor,
                        0L, effect,
                        JavaCall,
                        JavaCallee));
    }

    public HotSpotForeignCallLinkage registerStubCall(
                    ForeignCallSignature signature,
                    Transition transition,
                    Reexecutability reexecutability,
                    RegisterEffect effect,
                    LocationIdentity... killedLocations) {
        HotSpotForeignCallDescriptor descriptor = new HotSpotForeignCallDescriptor(signature, transition, reexecutability, killedLocations);
        signatureMap.put(signature, descriptor);
        return registerStubCall(descriptor, effect);
    }

    /**
     * Creates and registers the linkage for a foreign call. All foreign calls are assumed to have
     * the effect {@link RegisterEffect#DESTROYS_ALL_CALLER_SAVE_REGISTERS} since they are outside
     * of Graal's knowledge.
     *
     * @param descriptor the signature of the foreign call
     * @param address the address of the code to call (must be non-zero)
     * @param outgoingCcType outgoing (caller) calling convention type
     */
    public HotSpotForeignCallLinkage registerForeignCall(
                    HotSpotForeignCallDescriptor descriptor,
                    long address,
                    CallingConvention.Type outgoingCcType) {
        if (address == 0) {
            throw new IllegalArgumentException("address must be non-zero");
        }
        Class<?> resultType = descriptor.getResultType();
        assert descriptor.getTransition() != SAFEPOINT || resultType.isPrimitive() || Word.class.isAssignableFrom(resultType) : "non-leaf foreign calls must return objects in thread local storage: " +
                        descriptor;
        return register(HotSpotForeignCallLinkageImpl.create(metaAccess,
                        codeCache,
                        wordTypes,
                        this,
                        descriptor,
                        address,
                        DESTROYS_ALL_CALLER_SAVE_REGISTERS,
                        outgoingCcType,
                        null // incomingCcType
        ));
    }

    /**
     * Creates a {@linkplain ForeignCallStub stub} for the foreign call described by
     * {@code descriptor} if {@code address != 0}.
     *
     * @param descriptor the signature of the call to the stub
     * @param address the address of the foreign code to call
     * @param prependThread true if the JavaThread value for the current thread is to be prepended
     *            to the arguments for the call to {@code address}
     */
    public void linkForeignCall(OptionValues options,
                    HotSpotProviders providers,
                    HotSpotForeignCallDescriptor descriptor,
                    long address,
                    boolean prependThread) {
        if (address != 0) {
            ForeignCallStub stub = new ForeignCallStub(options, jvmciRuntime, providers, address, descriptor, prependThread);
            HotSpotForeignCallLinkage linkage = stub.getLinkage();
            HotSpotForeignCallLinkage targetLinkage = stub.getTargetLinkage();
            linkage.setCompiledStub(stub);
            register(linkage);
            register(targetLinkage);
        }
    }

    public static final boolean PREPEND_THREAD = true;
    public static final boolean DONT_PREPEND_THREAD = !PREPEND_THREAD;

    @Override
    public HotSpotForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
        assert foreignCalls != null : descriptor;
        HotSpotForeignCallLinkage callTarget = foreignCalls.get(descriptor.getSignature());
        if (callTarget == null) {
            throw GraalError.shouldNotReachHere("missing implementation for runtime call: " + descriptor);
        }
        callTarget.finalizeAddress(runtime.getHostBackend());
        return callTarget;
    }

    @Override
    public HotSpotForeignCallDescriptor getDescriptor(ForeignCallSignature signature) {
        HotSpotForeignCallDescriptor descriptor = signatureMap.get(signature);
        assert descriptor != null : signature;
        return descriptor;
    }

    HotSpotForeignCallDescriptor createDescriptor(ForeignCallSignature signature, Transition transition, Reexecutability reexecutability, LocationIdentity... killLocations) {
        assert signatureMap.get(signature) == null;
        HotSpotForeignCallDescriptor descriptor = new HotSpotForeignCallDescriptor(signature, transition, reexecutability, killLocations);
        signatureMap.put(signature, descriptor);
        return descriptor;
    }

    @Override
    public LIRKind getValueKind(JavaKind javaKind) {
        return LIRKind.fromJavaKind(codeCache.getTarget().arch, javaKind);
    }

    @Override
    public List<Stub> getStubs() {
        List<Stub> stubs = new ArrayList<>();
        for (HotSpotForeignCallLinkage linkage : foreignCalls.getValues()) {
            if (linkage.isCompiledStub()) {
                Stub stub = linkage.getStub();
                assert stub != null;
                stubs.add(stub);
            }
        }
        return stubs;
    }
}
