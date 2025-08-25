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

package jdk.internal.invoke.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;
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

    // Shadow contents that eventually will be the same as the field `contents` but is
    // guaranteed to respect any and all happens-before constraints but at the same
    // time, allows read operations under plain memory semantics.
    //
    // Typically, this fields is checked first and if it is non-null, it's contents
    // can safely be used directly. If it is null, its contents needs to be set by reading
    // from `contents` before being used.
    @Stable
    private Object contentsPlain;

    private StandardStableValue() {}

    private StandardStableValue(T contents) {
        this.contents = contents;
        this.contentsPlain = contents;
    }

    @ForceInline
    @Override
    public boolean trySet(T contents) {
        Objects.requireNonNull(contents);
        return !isSet() && trySetSlowPath(contents);
    }

    boolean trySetSlowPath(T contents) {
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
        Object t = contentsPlain;
        if (t != null || (t = contentsPlain = contentsAcquire()) != null) {
            return (T) t;
        }
        throw new NoSuchElementException("No contents set");
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T orElse(T other) {
        Object t = contentsPlain;
        if (t != null || (t = contentsPlain = contentsAcquire()) != null) {
            return (T) t;
        }
        return other;
    }

    @ForceInline
    @Override
    public boolean isSet() {
        return (contentsPlain != null || (contentsPlain = contentsAcquire()) != null);
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T orElseSet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        Object t = contentsPlain;
        if (t != null || (t = contentsPlain = contentsAcquire()) != null) {
            return (T) t;
        }
        return orElseSetSlowPath(supplier);
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

    public static String render(Object t) {
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

    // Factories

    public static <T> StandardStableValue<T> of() {
        return new StandardStableValue<>();
    }

    public static <T> StandardStableValue<T> ofPreset(T contents) {
        return new StandardStableValue<>(contents);
    }

}
