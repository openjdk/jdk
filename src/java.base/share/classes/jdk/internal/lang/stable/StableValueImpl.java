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

package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

public final class StableValueImpl<T> implements StableValue<T> {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final Object NULL_SENTINEL = new Object();
    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "value");

    private final Object mutex = new Object();

    // This field is reflectively accessed via Unsafe using acquire/release semantics.
    // Unset:         null
    // Set(non-null): The set value (!= nullSentinel())
    // Set(null):     nullSentinel()
    @Stable
    private T value;

    private StableValueImpl() {}

    @ForceInline
    @Override
    public boolean trySet(T value) {
        synchronized (mutex) {
            return UNSAFE.weakCompareAndSetReferenceRelease(this, VALUE_OFFSET, null, wrap(value));
        }
    }

    @ForceInline
    @Override
    public T orElseThrow() {
        final T t = valueAcquire();
        if (t != null) {
            return unwrap(t);
        }
        throw new NoSuchElementException("No value set");
    }

    @ForceInline
    @Override
    public T orElse(T other) {
        final T t = valueAcquire();
        if (t != null) {
            return unwrap(t);
        }
        return other;
    }

    @ForceInline
    @Override
    public boolean isSet() {
        return valueAcquire() != null;
    }

    @ForceInline
    @Override
    public T computeIfUnset(Supplier<? extends T> supplier) {
        final T t = valueAcquire();
        if (t != null) {
            return unwrap(t);
        }
        return compute(supplier);
    }

    @DontInline
    private T compute(Supplier<? extends T> supplier) {
        synchronized (mutex) {
            T t = valueAcquire();
            if (t != null) {
                return unwrap(t);
            }
            t = supplier.get();
            trySet(t);
            return orElseThrow();
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orElse(null));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableValueImpl<?> other &&
                Objects.equals(valueAcquire(), other.valueAcquire());
    }

    @Override
    public String toString() {
        return "StableValue" + render(valueAcquire());
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    private T valueAcquire() {
        return (T) UNSAFE.getReferenceAcquire(this, VALUE_OFFSET);
    }

    // Wraps null values into a sentinel value
    private static <T> T wrap(T t) {
        return (t == null) ? nullSentinel() : t;
    }

    // Unwraps null sentinel values into null
    @ForceInline
    private static <T> T unwrap(T t) {
        return t == nullSentinel() ? null : t;
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullSentinel() {
        return (T) NULL_SENTINEL;
    }

    private static <T> String render(T t) {
        if (t != null) {
            return t == nullSentinel() ? "[null]" : "[" + t + "]";
        }
        return ".unset";
    }


    // Factory
    public static <T> StableValueImpl<T> newInstance() {
        return new StableValueImpl<>();
    }

}
