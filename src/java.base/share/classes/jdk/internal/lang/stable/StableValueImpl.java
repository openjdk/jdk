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

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * The implementation of StableValue.
 *
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <T> type of the holder value
 */
public final class StableValueImpl<T> implements StableValue<T> {

    // Unsafe allows StableValue to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Unsafe offsets for direct field access
    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "wrappedValue");

    // Generally, fields annotated with `@Stable` are accessed by the JVM using special
    // memory semantics rules (see `parse.hpp` and `parse(1|2|3).cpp`).
    //
    // This field is used directly and via Unsafe using explicit memory semantics.
    //
    // | Value          |  Meaning      |
    // | -------------- |  ------------ |
    // | null           |  Unset        |
    // | nullSentinel() |  Set(null)    |
    // | other          |  Set(other)   |
    //
    @Stable
    private volatile Object wrappedValue;

    @Stable
    private final Object mutex;

    // Only allow creation via the factory `StableValueImpl::newInstance`
    private StableValueImpl() {
        this.mutex = new Object();
    }

    @ForceInline
    @Override
    public boolean trySet(T newValue) {
        if (wrappedValue != null) {
            return false;
        }
        // Mutual exclusion is required here as `computeIfUnset` might also
        // attempt to modify the `wrappedValue`
        synchronized (mutex) {
            return wrapAndCas(newValue);
        }
    }

    @ForceInline
    @Override
    public T orElseThrow() {
        final Object t = wrappedValue;
        if (t != null) {
            return unwrap(t);
        }
        throw new NoSuchElementException("No holder value set");
    }

    @ForceInline
    @Override
    public T orElse(T other) {
        final Object t = wrappedValue;
        if (t != null) {
            return unwrap(t);
        }
        return other;
    }

    @ForceInline
    @Override
    public boolean isSet() {
        return wrappedValue != null;
    }

    // (p, _) -> p.get()
    private static final BiFunction<Supplier<Object>, Object, Object> SUPPLIER_EXTRACTOR = new BiFunction<>() {
        @Override public Object apply(Supplier<Object> supplier, Object unused) { return supplier.get(); }
    };

    // IntFunction::apply
    private static final BiFunction<IntFunction<Object>, Integer, Object> INT_FUNCTION_EXTRACTOR = new BiFunction<>() {
        @Override public Object apply(IntFunction<Object> mapper, Integer key) { return mapper.apply(key); }
    };

    // Function::apply
    private static final BiFunction<Function<Object, Object>, Object, Object> FUNCTION_EXTRACTOR = new BiFunction<>() {
        @Override  public Object apply(Function<Object, Object> mapper, Object key) { return mapper.apply(key); }
    };

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T computeIfUnset(Supplier<? extends T> supplier) {
        return computeIfUnset0(null, supplier, (BiFunction<? super Supplier<? extends T>, ?, T>) (BiFunction<?, ?, ?>) SUPPLIER_EXTRACTOR);
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T computeIfUnset(int key, IntFunction<? extends T> mapper) {
        return computeIfUnset0(key, mapper, (BiFunction<? super IntFunction<? extends T>, ? super Integer, T>) (BiFunction<?, ?, ?>) INT_FUNCTION_EXTRACTOR);
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public <K> T computeIfUnset(K key, Function<? super K, ? extends T> mapper) {
        return computeIfUnset0(key, mapper, (BiFunction<? super Function<? super K,? extends T>, ? super K, T>) (BiFunction<?, ?, ?>) FUNCTION_EXTRACTOR);
    }

    @ForceInline
    @Override
    public <K, L> T computeIfUnset(K firstKey, L secondKey, BiFunction<? super K, ? super L, ? extends T> mapper) {
        Object t = wrappedValue;
        if (t != null) {
            return unwrap(t);
        }
        synchronized (mutex) {
            t = wrappedValue;
            if (t != null) {
                return unwrap(t);
            }
            final T newValue = mapper.apply(firstKey, secondKey);
            // The mutex is reentrant so we need to check if the value was actually set.
            return wrapAndCas(newValue) ? newValue : orElseThrow();
        }
    }

    // A consolidated method for some computeIfUnset overloads
    @ForceInline
    private <K, P> T computeIfUnset0(K key, P provider, BiFunction<P, K, T> extractor) {
        Object t = wrappedValue;
        if (t != null) {
            return unwrap(t);
        }
        synchronized (mutex) {
            t = wrappedValue;
            if (t != null) {
                return unwrap(t);
            }
            final T newValue = extractor.apply(provider, key);
            // The mutex is reentrant so we need to check if the value was actually set.
            return wrapAndCas(newValue) ? newValue : orElseThrow();
        }
    }

    // The methods equals() and hashCode() should be based on identity (defaults from Object)

    @Override
    public String toString() {
        final Object t = wrappedValue;
        return t == this
                ? "(this StableValue)"
                : "StableValue" + renderWrapped(t);
    }

    // Internal methods shared with other internal classes

    @ForceInline
    public Object wrappedValue() {
        return wrappedValue;
    }

    static String renderWrapped(Object t) {
        return (t == null) ? ".unset" : "[" + unwrap(t) + "]";
    }

    // Private methods

    @ForceInline
    private boolean wrapAndCas(Object value) {
        // This upholds the invariant, a `@Stable` field is written to at most once
        return UNSAFE.compareAndSetReference(this, VALUE_OFFSET, null, wrap(value));
    }

    // Used to indicate a holder value is `null` (see field `value` below)
    // A wrapper method `nullSentinel()` is used for generic type conversion.
    private static final Object NULL_SENTINEL = new Object();

    // Wraps `null` values into a sentinel value
    @ForceInline
    private static <T> T wrap(T t) {
        return (t == null) ? nullSentinel() : t;
    }

    // Unwraps null sentinel values into `null`
    @SuppressWarnings("unchecked")
    @ForceInline
    private static <T> T unwrap(Object t) {
        return t != nullSentinel() ? (T) t : null;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    private static <T> T nullSentinel() {
        return (T) NULL_SENTINEL;
    }

    // Factory

    static <T> StableValueImpl<T> newInstance() {
        return new StableValueImpl<>();
    }

}
