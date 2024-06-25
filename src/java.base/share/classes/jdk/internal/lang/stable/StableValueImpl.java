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
import java.util.function.Function;
import java.util.function.Supplier;

public final class StableValueImpl<T> implements StableValue<T> {

    // Unsafe allows StableValue to be used early in the boot sequence
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Used to indicate a holder value is `null` (see field `value` below)
    // A wrapper method `nullSentinel()` is used for generic type conversion.
    private static final Object NULL_SENTINEL = new Object();

    // Used to indicate a mutex is not needed anymore.
    private static final Object TOMBSTONE = new Object();

    // Unsafe offsets for direct object access
    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "value");
    private static final long MUTEX_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "mutex");

    // Generally, fields annotated with `@Stable` are accessed by the JVM using special
    // memory semantics rules (see `parse.hpp` and `parse(1|2|3).cpp`).
    //
    // This field is reflectively accessed via Unsafe using explicit memory semantics.
    //
    // Value          Meaning
    // -------        -----
    // null           Unset
    // nullSentinel() Set(null)
    // other          Set(other)
    @Stable
    private T value;

    // This field is initialized on demand to a new distinct mutex object.
    // When synchronization is no longer needed (i.e. when a holder value is set),
    // the field is set to the `TOMBSTONE` singleton object to allow the previous,
    // now-redundant mutex object to be collected.
    private volatile Object mutex;

    // Only allow creation via the factory `StableValueImpl::newInstance`
    private StableValueImpl() {}

    @ForceInline
    @Override
    public boolean trySet(T value) {
        if (value() != null) {
            return false;
        }
        Object m = acquireMutex();
        if (m == TOMBSTONE) {
            // A holder value must already be set as a holder value store
            // happens before a mutex TOMBSTONE store
            return false;
        }
        synchronized (m) {
            // The one-and-only update of the `value` field is always made under
            // `mutex` synchronization meaning plain memory semantics is enough here.
            if (valuePlain() != null) {
                return false;
            }
            set0(value);
            // The holder value store must happen before the mutex release
            releaseMutex();
        }
        return true;
    }

    @ForceInline
    private void set0(T value) {
        // Prevents reordering of store operations with other store operations.
        // This means any stores made to fields in the `value` object prior to this
        // point cannot be reordered with the CAS operation of the reference to the
        // `value` field.
        // In other words, if a reader (using plain memory semantics) can observe a
        // `value` reference, any field updates made prior to this fence are
        // guaranteed to be seen.
        // See https://gee.cs.oswego.edu/dl/html/j9mm.html "Mixed Modes and Specializations",
        // Doug Lea, 2018
        UNSAFE.storeStoreFence();

        // We are alone here under the `mutex`
        // This upholds the invariant, the `@Stable value` field is written to
        // at most once.
        UNSAFE.putReferenceVolatile(this, VALUE_OFFSET, wrap(value));
    }

    @ForceInline
    @Override
    public T orElseThrow() {
        final T t = value();
        if (t != null) {
            return unwrap(t);
        }
        throw new NoSuchElementException("No value set");
    }

    @ForceInline
    @Override
    public T orElse(T other) {
        final T t = value();
        if (t != null) {
            return unwrap(t);
        }
        return other;
    }

    @ForceInline
    @Override
    public boolean isSet() {
        return value() != null;
    }

    @ForceInline
    @Override
    public T computeIfUnset(Supplier<? extends T> supplier) {
        final T t = value();
        if (t != null) {
            return unwrap(t);
        }
        return tryCompute(null, supplier);
    }

    @ForceInline
    @Override
    public <I> T mapIfUnset(I input, Function<? super I, ? extends T> function) {
        final T t = value();
        if (t != null) {
            return unwrap(t);
        }
        return tryCompute(input, function);
    }

    @SuppressWarnings("unchecked")
    @DontInline
    private <I> T tryCompute(I input, Object provider) {
        Object m = acquireMutex();
        if (m == TOMBSTONE) {
            // A holder value must already be set as a holder value store
            // happens before a mutex TOMBSTONE store
            return unwrap(value());
        }
        synchronized (m) {
            // The one-and-only update of the `value` field is always made under
            // `mutex` synchronization meaning plain memory semantics is enough here.
            T t = valuePlain();
            if (t != null) {
                return unwrap(t);
            }
            if (provider instanceof Supplier<?> supplier) {
                t = (T) supplier.get();
            } else {
                t = ((Function<I, T>) provider).apply(input);
            }
            set0(t);
            // The holder value store must happen before the mutex release
            releaseMutex();
            return t;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableValueImpl<?> other &&
                // Note that the returned `value()` will be `null` if the holder value
                // is unset and `nullSentinel()` if the holder value is `null`.
                Objects.equals(value(), other.value());
    }

    @Override
    public String toString() {
        return "StableValue" + render(value());
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    // First, try to read the value using plain memory semantics.
    // If not set, fall back to `volatile` memory semantics.
    private T value() {
        final T t = valuePlain();
        return t != null ? t : (T) UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
    }

    @ForceInline
    private T valuePlain() {
        // Appears to be faster than `(T) UNSAFE.getReference(this, VALUE_OFFSET)`
        return value;
    }

    // Wraps `null` values into a sentinel value
    private static <T> T wrap(T t) {
        return (t == null) ? nullSentinel() : t;
    }

    // Unwraps null sentinel values into `null`
    @ForceInline
    private static <T> T unwrap(T t) {
        return t != nullSentinel() ? t : null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullSentinel() {
        return (T) NULL_SENTINEL;
    }

    private static <T> String render(T t) {
        return (t == null) ? ".unset" : "[" + unwrap(t) + "]";
    }

    private Object acquireMutex() {
        if (mutex != null) {
            // We already have a mutex
            return mutex;
        }
        Object newMutex = new Object();
        // Guarantees, only one distinct mutex object per StableValue is ever exposed.
        Object witness = UNSAFE.compareAndExchangeReference(this, MUTEX_OFFSET, null, newMutex);
        return witness == null ? newMutex : witness;
    }

    private void releaseMutex() {
        mutex = TOMBSTONE;
    }

    // Factory for creating new StableValue instances
    public static <T> StableValue<T> newInstance() {
        return new StableValueImpl<>();
    }

}
