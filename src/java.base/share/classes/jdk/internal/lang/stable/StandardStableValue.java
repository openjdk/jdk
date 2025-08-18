/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.Optional;
import java.lang.invoke.StableValue;
import java.util.function.Supplier;

/**
 * The standard (non-preset) implementation of StableValue.
 *
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <T> type of the contents
 */
public final class StandardStableValue<T> implements StableValue<T> {

    static final String UNSET_LABEL = ".unset";

    // Unsafe allows StableValue to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Unsafe offsets for direct field access

    private static final long CONTENTS_OFFSET =
            UNSAFE.objectFieldOffset(StandardStableValue.class, "contents");

    // Generally, fields annotated with `@Stable` are accessed by the JVM using special
    // memory semantics rules (see `parse.hpp` and `parse(1|2|3).cpp`).
    //
    // This field is used directly and reflectively via Unsafe using explicit memory semantics.
    //
    // | Value          |  Meaning      |
    // | -------------- |  ------------ |
    // | null           |  Unset        |
    // | other          |  Set(other)   |
    //
    @Stable
    private Object contents;

    // Only allow creation via the factory `StableValueImpl::newInstance`
    private StandardStableValue() {}

    @ForceInline
    @Override
    public boolean trySet(T contents) {
        Objects.requireNonNull(contents);
        if (contentsAcquire() != null) {
            return false;
        }
        // Prevent reentry via an orElseSet(supplier)
        preventReentry();
        // Mutual exclusion is required here as `orElseSet` might also
        // attempt to modify `this.contents`
        synchronized (this) {
            return set(contents);
        }
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T get() {
        final Object t = contentsAcquire();
        if (t == null) {
            throw new NoSuchElementException("No contents set");
        }
        return (T) t;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T orElse(T other) {
        final Object t = contentsAcquire();
        return t == null ? other : (T) t;
    }

    @ForceInline
    @Override
    public boolean isSet() {
        return contentsAcquire() != null;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T orElseSet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        final Object t = contentsAcquire();
        return (t == null) ? orElseSetSlowPath(supplier) : (T) t;
    }

    @SuppressWarnings("unchecked")
    private T orElseSetSlowPath(Supplier<? extends T> supplier) {
        preventReentry();
        synchronized (this) {
            final Object t = contents;  // Plain semantics suffice here
            if (t == null) {
                final T newValue = supplier.get();
                Objects.requireNonNull(newValue);
                // The mutex is not reentrant so we know newValue should be returned
                set(newValue);
                return newValue;
            }
            return (T) t;
        }
    }

    // The methods equals() and hashCode() should be based on identity (defaults from Object)

    @Override
    public String toString() {
        final Object t = contentsAcquire();
        return t == this
                ? "(this StableValue)"
                : render(t);
    }

    // Internal methods shared with other internal classes

    @ForceInline
    public Object contentsAcquire() {
        return UNSAFE.getReferenceAcquire(this, CONTENTS_OFFSET);
    }

    static String render(Object t) {
        return (t == null) ? UNSET_LABEL : Objects.toString(t);
    }

    // Private methods

    // This method is not annotated with @ForceInline as it is always called
    // in a slow path.
    private void preventReentry() {
        if (Thread.holdsLock(this)) {
            throw new IllegalStateException("Recursive initialization of a stable value is illegal");
        }
    }

    /**
     * Tries to set the contents to {@code newValue}.
     * <p>
     * This method ensures the {@link Stable} field is written to at most once.
     *
     * @param newValue to wrap and set
     * @return if the contents was set
     */
    @ForceInline
    private boolean set(T newValue) {
        assert Thread.holdsLock(this);
        // We know we hold the monitor here so plain semantic is enough
        if (contents == null) {
            UNSAFE.putReferenceRelease(this, CONTENTS_OFFSET, newValue);
            return true;
        }
        return false;
    }

    // Factory

    public static <T> StandardStableValue<T> of() {
        return new StandardStableValue<>();
    }

}
