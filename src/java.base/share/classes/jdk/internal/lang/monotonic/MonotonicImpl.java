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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import static jdk.internal.lang.monotonic.MonotonicUtil.*;

public final class MonotonicImpl<V> implements Monotonic<V> {

    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(MonotonicImpl.class, "value");

    private static final Object NULL_SENTINEL = new Object();

    /**
     * If null, indicates a value is not present.
     * if non-null, holds the bound value or the NULL_SENTINEL.
     */
    @Stable
    private Object value;

    MonotonicImpl() {}

    @ForceInline
    @Override
    public boolean isPresent() {
        return value != null || valueVolatile() != null;
    }

    @ForceInline
    @Override
    public V get() {
        // Optimistically try plain semantics first
        Object v = value;
        if (v != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is present.
            // The non-null value could represent `null` or a value of type `V`
            return toV(v);
        }
        // Now, fall back to volatile semantics.
        v = valueVolatile();
        if (v != null) {
            // If we see a non-null value, we know a value is present.
            return toV(v);
        }
        throw new NoSuchElementException();
    }

    @ForceInline
    @Override
    public void bind(V value) {
        Object v = toObject(value);
        if (caeValue(v) != null) {
            throw new IllegalStateException("A value is already bound: " + get());
        }
    }

    @ForceInline
    @Override
    public V bindIfAbsent(V value) {
        if (isPresent()) {
           return get();
        }
        return caeWitness(value);
    }

    @ForceInline
    @Override
    public V computeIfAbsent(Supplier<? extends V> supplier) {
        // Optimistically try plain semantics first
        Object v = value;
        if (v != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is present.
            // The non-null value could represent `null` or a value of type `V`
            return toV(v);
        }
        // Now, fall back to volatile semantics.
        v = valueVolatile();
        if (v != null) {
            // If we see a non-null value, we know a value is present.
            return toV(v);
        }
        // A value is probably not present
        V newValue = supplier.get();
        return caeWitness(newValue);
    }

    @Override
    public String toString() {
        return "Monotonic" +
                (isPresent()
                        ? "[" + get() + "]"
                        : ".unbound");
    }

    private Object valueVolatile() {
        return UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
    }

    private Object caeValue(Object value) {
        // This prevents partially initialized objects to be observed
        // under normal memory semantics.
        if (value != NULL_SENTINEL) {
            freeze();
        }
        return UNSAFE.compareAndExchangeReference(this, VALUE_OFFSET, null, value);
    }

    @SuppressWarnings("unchecked")
    private V toV(Object o) {
        return o == NULL_SENTINEL ? null : (V) o;
    }

    private Object toObject(V value) {
        return value == null ? NULL_SENTINEL : value;
    }

    @SuppressWarnings("unchecked")
    private V caeWitness(V newValue) {
        Object witness = caeValue(toObject(newValue));
        return (V) (witness == null ? newValue : witness);
    }

    // Factories

    public static <V> Monotonic<V> of() {
        return new MonotonicImpl<>();
    }

    public static <V> Supplier<V> asMemoized(Supplier<? extends V> supplier) {
        Monotonic<V> monotonic = Monotonic.of();
        Supplier<V> guardedSupplier = new Supplier<>() {
            @Override
            public V get() {
                synchronized (monotonic) {
                    if (monotonic.isPresent()) {
                        return monotonic.get();
                    }
                }
                return supplier.get();
            }
        };
        return new Supplier<>() {
            @Override
            public V get() {
                return monotonic.computeIfAbsent(guardedSupplier);
            }
        };
    }

}
