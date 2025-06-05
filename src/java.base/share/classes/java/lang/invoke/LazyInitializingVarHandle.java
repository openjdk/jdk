/*
 *  Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package java.lang.invoke;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Optional;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;
import static java.lang.invoke.MethodHandleStatics.uncaughtException;

/**
 * A lazy initializing var handle. It lazily initializes the referenced class before
 * any invocation of the target var handle to prevent reading uninitialized static
 * field values.
 */
final class LazyInitializingVarHandle extends VarHandle {

    // Implementation notes:
    // We put a barrier on both target() (for VH form impl direct invocation)
    // and on getMethodHandle() (for indirect VH invocation, toMethodHandle)
    private final VarHandle target;
    private final Class<?> refc;
    private @Stable boolean initialized;

    LazyInitializingVarHandle(VarHandle target, Class<?> refc) {
        super(target.vform, target.exact);
        this.target = target;
        this.refc = refc;
    }

    @Override
    MethodType accessModeTypeUncached(AccessType at) {
        return target.accessModeType(at.ordinal());
    }

    @Override
    @ForceInline
    VarHandle asDirect() {
        return target;
    }

    @Override
    @ForceInline
    VarHandle target() {
        ensureInitialized();
        return target;
    }

    @Override
    public VarHandle withInvokeExactBehavior() {
        if (!initialized && hasInvokeExactBehavior())
            return this;
        var exactTarget = target.withInvokeExactBehavior();
        return initialized ? exactTarget : new LazyInitializingVarHandle(exactTarget, refc);
    }

    @Override
    public VarHandle withInvokeBehavior() {
        if (!initialized && !hasInvokeExactBehavior())
            return this;
        var nonExactTarget = target.withInvokeBehavior();
        return initialized ? nonExactTarget : new LazyInitializingVarHandle(nonExactTarget, refc);
    }

    @Override
    public Optional<VarHandleDesc> describeConstable() {
        return target.describeConstable();
    }

    @Override
    public MethodHandle getMethodHandleUncached(int accessMode) {
        var mh = target.getMethodHandle(accessMode);
        if (this.initialized)
            return mh;

        return MethodHandles.collectArguments(mh, 0, ensureInitializedMh()).bindTo(this);
    }

    @ForceInline
    private void ensureInitialized() {
        if (this.initialized)
            return;

        initialize();
    }

    private void initialize() {
        UNSAFE.ensureClassInitialized(refc);
        this.initialized = true;

        this.methodHandleTable = target.methodHandleTable;
    }

    private static @Stable MethodHandle MH_ensureInitialized;

    private static MethodHandle ensureInitializedMh() {
        var mh = MH_ensureInitialized;
        if (mh != null)
            return mh;

        try {
            return MH_ensureInitialized = MethodHandles.lookup().findVirtual(
                    LazyInitializingVarHandle.class,
                    "ensureInitialized",
                    MethodType.methodType(void.class));
        } catch (Throwable ex) {
            throw uncaughtException(ex);
        }
    }
}
