/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.monotonic;

import jdk.internal.vm.annotation.Stable;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

public final class InternalMonotonic<V> implements Monotonic<V> {

    private static final long VALUE_OFFSET =
            MonotonicUtil.UNSAFE.objectFieldOffset(InternalMonotonic.class, "value");

    private static final long BOUND_OFFSET =
            MonotonicUtil.UNSAFE.objectFieldOffset(InternalMonotonic.class, "bound");

    /**
     * Holds the actual bound value.
     */
    @Stable
    private V value;

    /**
     * Indicates if a value is bound. This is needed for `null` values.
     */
    @Stable
    private boolean bound;

    @Override
    public boolean isPresent() {
        return bound || isBoundVolatile();
    }

    @Override
    public V get() {
        // Optimistically try plain semantics first
        V v = value;
        if (v != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a non-null value is bound.
            return v;
        }
        // Check the case if a `null` value is bound or if we are
        // in-between seeing updates to the two fields.
        if (bound) {
            // There is no _happens before_ relation between stores
            // and reads with normal semantics so, we need to do a
            // volatile read here. We could otherwise erroneously
            // return `null` when another value was actually present.
            // Todo: ensure happens-before while setting.
            return valueVolatile();
        }
        // Now, fall back to volatile semantics. Note that
        // `value` updates _happens before_ `bound` updates
        if (isBoundVolatile()) {
            return valueVolatile();
        }
        throw new NoSuchElementException();
    }

    @Override
    public void bind(V v) {
        Objects.requireNonNull(v);
        if (caeValue(v) != null || !casBound()) {
            throw valueAlreadyBound();
        }
    }

    @Override
    public V bindIfAbsent(V v) {
        Objects.requireNonNull(v);
        V witness = caeValue(v);
        if (witness == null) {
            // We might have bound `null` previously
            if (!casBound()) {
                return null;
            }
            return v;
        }
        return witness;
    }

    @Override
    public V computeIfAbsent(Supplier<? extends V> supplier) {
        // See the get() method for comments on how the
        // initial part of this method actually works.
        V v = value;
        if (v != null) {
            return v;
        }
        if (bound) {
            return valueVolatile();
        }
        if (isBoundVolatile()) {
            return valueVolatile();
        }
        V witness;
        // Make sure the supplier is only invoked at most once by this method.
        // Note that the fields might change at any time also in the synchronized
        // block due to other threads updating them freely.
        synchronized (supplier) {
            // Re-check
            if (isBoundVolatile()) {
                return valueVolatile();
            }
            v = supplier.get();
            witness = caeValue(v);
        }
        if (witness == null) {
            // We might have bound `null` previously
            if (!casBound()) {
                return null;
            }
            return v;
        }
        return witness;
    }

    @Override
    public String toString() {
        return "Monotonic" +
                (isPresent()
                        ? "[" + get() + "]"
                        : ".unbound");
    }

    private IllegalStateException valueAlreadyBound() {
        return new IllegalStateException("A value is already bound: " + get());
    }

    private boolean isBoundVolatile() {
        return MonotonicUtil.UNSAFE.getBooleanVolatile(this, BOUND_OFFSET);
    }

    private boolean casBound() {
        return MonotonicUtil.UNSAFE.compareAndSetBoolean(this, BOUND_OFFSET, false, true);
    }

    @SuppressWarnings("unchecked")
    private V valueVolatile() {
        return (V) MonotonicUtil.UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
    }

    @SuppressWarnings("unchecked")
    private V caeValue(V value) {
        // This prevents partially initialized objects to be observed
        // under normal memory semantics.
        if (value != null) {
            MonotonicUtil.freeze();
        }
        return (V) MonotonicUtil.UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, value);
    }

}
