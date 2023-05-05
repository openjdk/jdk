/*
 *  Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.Stable;

import java.util.Optional;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;
import static java.lang.invoke.MethodHandleStatics.uncaughtException;
import static java.lang.invoke.MethodHandles.Lookup.IMPL_LOOKUP;
import static java.util.Objects.requireNonNull;

/**
 * A lazy static field var handle. It lazily initializes the referenced class and the delegate
 * static field var handle upon invocation. Its var form is that of the static field var handle.
 */
final class LazyStaticVarHandle extends VarHandle {

    private final Class<?> refc;
    private final MemberName field;
    private final Class<?> fieldType;
    private final boolean writeAllowedOnFinalFields;
    private @Stable VarHandle delegate;
    private final @Stable MethodHandle[] handleMap = new MethodHandle[AccessMode.COUNT];

    private static VarForm findVarForm(MemberName field, boolean writeAllowedOnFinalFields) {
        Class<?> type = requireNonNull(field.getFieldType());
        boolean readOnly = field.isFinal() && !writeAllowedOnFinalFields;
        if (!type.isPrimitive()) {
            return readOnly ? VarHandleReferences.FieldStaticReadOnly.FORM : VarHandleReferences.FieldStaticReadWrite.FORM;
        }
        else if (type == boolean.class) {
            return readOnly ? VarHandleBooleans.FieldStaticReadOnly.FORM : VarHandleBooleans.FieldStaticReadWrite.FORM;
        }
        else if (type == byte.class) {
            return readOnly ? VarHandleBytes.FieldStaticReadOnly.FORM : VarHandleBytes.FieldStaticReadWrite.FORM;
        }
        else if (type == short.class) {
            return readOnly ? VarHandleShorts.FieldStaticReadOnly.FORM : VarHandleShorts.FieldStaticReadWrite.FORM;
        }
        else if (type == char.class) {
            return readOnly ? VarHandleChars.FieldStaticReadOnly.FORM : VarHandleChars.FieldStaticReadWrite.FORM;
        }
        else if (type == int.class) {
            return readOnly ? VarHandleInts.FieldStaticReadOnly.FORM : VarHandleInts.FieldStaticReadWrite.FORM;
        }
        else if (type == long.class) {
            return readOnly ? VarHandleLongs.FieldStaticReadOnly.FORM : VarHandleLongs.FieldStaticReadWrite.FORM;
        }
        else if (type == float.class) {
            return readOnly ? VarHandleFloats.FieldStaticReadOnly.FORM : VarHandleFloats.FieldStaticReadWrite.FORM;
        }
        else if (type == double.class) {
            return readOnly ? VarHandleDoubles.FieldStaticReadOnly.FORM : VarHandleDoubles.FieldStaticReadWrite.FORM;
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    LazyStaticVarHandle(Class<?> refc, MemberName field, boolean writeAllowedOnFinalFields, boolean exact) {
        super(findVarForm(field, writeAllowedOnFinalFields), exact);
        this.refc = refc;
        this.field = field;
        this.fieldType = requireNonNull(field.getFieldType());
        this.writeAllowedOnFinalFields = writeAllowedOnFinalFields;
    }

    @Override
    VarHandle asDirect() {
        return delegate();
    }

    @Override
    boolean checkAccessModeThenIsDirect(AccessDescriptor ad) {
        super.checkAccessModeThenIsDirect(ad);
        return false;
    }

    private VarHandle delegate() {
        var delegate = this.delegate;
        if (delegate != null)
            return delegate;

        synchronized (this) {
            delegate = this.delegate;
            if (delegate != null)
                return delegate;

            UNSAFE.ensureClassInitialized(refc);
            Object base = MethodHandleNatives.staticFieldBase(field);
            long offset = MethodHandleNatives.staticFieldOffset(field);
            return this.delegate = VarHandles.makeInitializedStaticFieldVarHandle(field, refc, base, offset, writeAllowedOnFinalFields);
        }
    }

    @Override
    public VarHandle withInvokeExactBehavior() {
        var delegate = this.delegate;
        return delegate == null ? (hasInvokeExactBehavior() ? this
                : new LazyStaticVarHandle(refc, field, writeAllowedOnFinalFields, false))
                : delegate.withInvokeExactBehavior();
    }

    @Override
    public VarHandle withInvokeBehavior() {
        var delegate = this.delegate;
        return delegate == null ? (!hasInvokeExactBehavior() ? this
                : new LazyStaticVarHandle(refc, field, writeAllowedOnFinalFields, true))
                : delegate.withInvokeBehavior();
    }

    @Override
    MethodType accessModeTypeUncached(AccessType at) {
        return at.accessModeType(null, fieldType);
    }

    @Override
    public Optional<VarHandleDesc> describeConstable() {
        var receiverTypeRef = field.getDeclaringClass().describeConstable();
        var fieldTypeRef = fieldType.describeConstable();
        if (!receiverTypeRef.isPresent() || !fieldTypeRef.isPresent())
            return Optional.empty();

        String name = field.getName();
        return Optional.of(VarHandleDesc.ofStaticField(receiverTypeRef.get(), name, fieldTypeRef.get()));
    }

    // Note: getMethodHandle() result anticipates a static field var handle, instead of
    // this lazy var handle, as its first argument

    @Override
    public MethodHandle toMethodHandle(AccessMode accessMode) {
        var delegate = this.delegate;
        if (delegate != null)
            return delegate.toMethodHandle(accessMode);

        class Holder {
            static final MethodHandle MH_delegate;

            static {
                try {
                    MH_delegate = IMPL_LOOKUP.findVirtual(LazyStaticVarHandle.class, "delegate",
                            MethodType.methodType(VarHandle.class));
                } catch (Throwable ex) {
                    throw uncaughtException(ex);
                }
            }
        }

        // not yet initialized, filter with delegate() call
        var mh = handleMap[accessMode.ordinal()];
        if (mh == null) {
            return handleMap[accessMode.ordinal()] = MethodHandles.
                    filterArgument(getMethodHandle(accessMode.ordinal()), 0, Holder.MH_delegate).bindTo(this);
        }

        return mh;
    }
}
