/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.lang.invoke;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Optional;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;

/**
 * A lazy initializing var handle. It lazily initializes the referenced class before
 * any invocation of the target var handle to prevent reading uninitialized static
 * field values.
 */
final class LazyInitializingVarHandle extends VarHandle {

    private final VarHandles.StaticFieldVarHandle target;
    private final boolean strictInit;
    private @Stable boolean fullyInitialized;

    LazyInitializingVarHandle(VarHandles.StaticFieldVarHandle target, boolean strictInit) {
        super(target.vform, target.exact);
        this.target = target;
        this.strictInit = strictInit;
    }

    @Override
    MethodType accessModeTypeUncached(AccessType at) {
        return target.accessModeType(at.ordinal());
    }

    @Override
    @ForceInline
    VarHandle onStaticFieldAccess(boolean reading) {
        if (!fullyInitialized) {
            initialize(reading);
        }
        return target;
    }

    @DontInline
    private void initialize(boolean reading) {
        var declaringClass = target.declaringClass;
        UNSAFE.ensureClassInitialized(declaringClass);
        boolean fullyInitialized = !UNSAFE.shouldBeInitialized(declaringClass);
        if (fullyInitialized) {
            this.fullyInitialized = true;
            return;
        }

        // Not fully initialized - strict static checking
        if (strictInit) {
            long offset = target.fieldOffset;
            // We only check for reading for CAS because they always access
            // reads first. We don't need the extra write check for CAS because
            // that check is only to avoid double writing final fields, and we
            // never allow creating VarHandle that CAS on final fields.
            UNSAFE.notifyStrictStaticAccess(declaringClass, offset, !reading);
        }
    }

    @Override
    public VarHandle withInvokeExactBehavior() {
        if (!fullyInitialized && hasInvokeExactBehavior())
            return this;
        var exactTarget = target.withInvokeExactBehavior();
        return fullyInitialized ? exactTarget : new LazyInitializingVarHandle(exactTarget, strictInit);
    }

    @Override
    public VarHandle withInvokeBehavior() {
        if (!fullyInitialized && !hasInvokeExactBehavior())
            return this;
        var nonExactTarget = target.withInvokeBehavior();
        return fullyInitialized ? nonExactTarget : new LazyInitializingVarHandle(nonExactTarget, strictInit);
    }

    @Override
    public Optional<VarHandleDesc> describeConstable() {
        return target.describeConstable();
    }
}
