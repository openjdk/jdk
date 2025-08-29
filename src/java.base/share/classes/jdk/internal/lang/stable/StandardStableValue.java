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
import java.util.function.Supplier;

/**
 * The standard implementation of StableValue.
 *
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <T> type of the contents
 */
public final class StandardStableValue<T> implements InternalStableValue<T> {

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
    private T contents;

    private StandardStableValue() {}

    private StandardStableValue(T contents) {
        rawSetRelease(contents);
    }

    @ForceInline
    @Override
    public boolean trySet(T contents) {
        Objects.requireNonNull(contents);
        return !isSet() && trySetSlowPath(contents);
    }

    boolean trySetSlowPath(T contents) {
        // Prevent reentry via an orElseSet(supplier)
        preventReentry(this);
        // Mutual exclusion is required here as `orElseSet` might also
        // attempt to modify `this.contents`
        synchronized (this) {
            return set(contents);
        }
    }

    @ForceInline
    @Override
    public T get() {
        final T t = contentsAcquire();
        if (t != null) {
            return t;
        }
        throw new NoSuchElementException("No contents set");
    }

    @ForceInline
    @Override
    public T orElse(T other) {
        final T t = contentsAcquire();
        if (t != null) {
            return t;
        }
        return other;
    }

    @ForceInline
    @Override
    public boolean isSet() {
        return contentsAcquire() != null;
    }

    @ForceInline
    @Override
    public T orElseSet(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier);
        final T t = contentsAcquire();
        if (t != null) {
            return t;
        }
        return orElseSetSlowPath(this, supplier, null);
    }

    @ForceInline
    @Override
    public T orElseSet(final int input,
                       final FunctionHolder<?> functionHolder) {
        final T t = contentsAcquire();
        if (t != null) {
            return t;
        }
        return orElseSetSlowPath(this, input, functionHolder);
    }

    @ForceInline
    @Override
    public T orElseSet(final Object input,
                       final FunctionHolder<?> functionHolder) {
        final T t = contentsAcquire();
        if (t != null) {
            return t;
        }
        return orElseSetSlowPath(this, input, functionHolder);
    }

    @Override
    public Object contentsPlain() {
        return contents;
    }

    // The methods equals() and hashCode() should be based on identity (defaults from Object)

    @Override
    public String toString() {
        final T t = contentsAcquire();
        return t == this
                ? "(this StableValue)"
                : render(t);
    }

    // Internal methods shared with other internal classes

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public T contentsAcquire() {
        return (T) UNSAFE.getReferenceAcquire(this, CONTENTS_OFFSET);
    }

    public static String render(Object t) {
        return (t == null) ? UNSET_LABEL : Objects.toString(t);
    }

    // Private methods


    /**
     * Tries to set the contents to {@code newValue}.
     * <p>
     * This method ensures the {@link Stable} field is written to at most once.
     *
     * @param newValue to wrap and set
     * @return if the contents was set
     */
    @ForceInline
    public boolean set(T newValue) {
        assert Thread.holdsLock(this);
        // We know we hold the monitor here so plain semantic is enough
        if (contents == null) {
            rawSetRelease(newValue);
            return true;
        }
        return false;
    }

    @ForceInline
    private void rawSetRelease(T newValue) {
        UNSAFE.putReferenceRelease(this, CONTENTS_OFFSET, newValue);
    }

    // Factories

    public static <T> StandardStableValue<T> of() {
        return new StandardStableValue<>();
    }

    public static <T> StandardStableValue<T> ofPreset(T contents) {
        return new StandardStableValue<>(contents);
    }

}
