/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.util.concurrent.lazy;

import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.lazy.BaseLazyReference;
import java.util.concurrent.lazy.Lazy;

public abstract sealed class AbstractBaseLazyReference<V>
        implements BaseLazyReference<V>
        permits StandardEmptyLazyReference, StandardLazyReference {

    // Allows access to the "value" field with arbitary memory semantics
    static final VarHandle VALUE_HANDLE;

    // Allows access to the "value" field with arbitary memory semantics
    static final VarHandle AUX_HANDLE;

    static {
        try {
            VALUE_HANDLE = MethodHandles.lookup()
                    .findVarHandle(AbstractBaseLazyReference.class, "value", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
            AUX_HANDLE = MethodHandles.lookup()
                    .findVarHandle(AbstractBaseLazyReference.class, "auxilaryObject", Object.class);
            // .withInvokeExactBehavior(); // Make sure no boxing is made?
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // This field holds the lazy value. If != null, a valid value exist
    @Stable
    V value;

    // This field has two purposes:
    // 1) Flag if the lazy is being constucted.
    // 2) Holds a Throwable, if the computation of the value failed.
    Object auxilaryObject;

    public AbstractBaseLazyReference() {
    }

    @Override
    public final Lazy.State state() {
        // Try normal memory semantics first
        Object o = value;
        if (o != null) {
            return Lazy.State.PRESENT;
        }

        Object aux = auxilaryObject;
        if (aux == LazyUtil.CONSTRUCTING) {
            return Lazy.State.CONSTRUCTING;
        }
        if (aux instanceof Throwable throwable) {
            return Lazy.State.ERROR;
        }

        // Retry with volatile semantics
        o = VALUE_HANDLE.getVolatile(this);
        if (o != null) {
            return Lazy.State.PRESENT;
        }

        aux = AUX_HANDLE.getVolatile(this);
        if (aux == LazyUtil.CONSTRUCTING) {
            return Lazy.State.CONSTRUCTING;
        }
        if (aux instanceof Throwable throwable) {
            return Lazy.State.ERROR;
        }

        return Lazy.State.EMPTY;
    }

    @Override
    public final Optional<Throwable> exception() {

        // Try normal memory semantics first
        Object aux = auxilaryObject;
        if (auxilaryObject instanceof Throwable throwable) {
            return Optional.of(throwable);
        }

        // Retry with volatile semantics
        aux = AUX_HANDLE.getVolatile(this);
        return (auxilaryObject instanceof Throwable throwable)
                ? Optional.of(throwable)
                : Optional.empty();
    }

    @Override
    public V getOr(V defaultValue) {
        V v = value;
        if (v != null) {
            return v;
        }
        v = (V) VALUE_HANDLE.getVolatile(this);
        if (v != null) {
            return v;
        }

        // No use trying normal semantics as we have to try volatile semantics anyhow
        if (AUX_HANDLE.getVolatile(this) instanceof Throwable throwable) {
            throw new NoSuchElementException(throwable);
        }
        return defaultValue;
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "[" +
                switch (state()) {
                    case EMPTY -> Lazy.State.EMPTY;
                    case CONSTRUCTING -> Lazy.State.CONSTRUCTING;
                    case PRESENT -> value;
                    case ERROR -> Lazy.State.ERROR + " [" + value + "]";
                }
                + "]";
    }

    abstract void afterSupplying();

}
