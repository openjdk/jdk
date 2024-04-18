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
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.*;

public final class StableValueImpl<V> implements StableValue<V> {

    private static final long VALUE_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "value");

    private static final long SET_OFFSET =
            UNSAFE.objectFieldOffset(StableValueImpl.class, "set");

    /**
     * If non-null, holds a set value
     * If `null`  , may be unset or hold a set `null` value
     */
    @Stable
    private V value;

    /**
     * If StableUtil.NOT_SET, a value is not set
     * If StableUtil.SET    , a value is set (value may be the default value (e.g. `null` or 0))
     */
    // Todo: Rename this variable
    @Stable
    private byte set;

    StableValueImpl() {}

    @ForceInline
    @Override
    public boolean isSet() {
        return set() || setVolatile();
    }

    @ForceInline
    @Override
    public V orThrow() {
        // Optimistically try plain semantics first
        V v = value;
        if (v != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return v;
        }
        if (set()) {
            // The value is set to its default value (e.g. `null`)
            return null;
        }
        // Now, fall back to volatile semantics.
        v = valueVolatile();
        if (v != null) {
            // If we see a non-null value, we know a value is set.
            return v;
        }
        if (setVolatile()) {
            // The value is set
            return null;
        }
        throw new NoSuchElementException();
    }

    @ForceInline
    @Override
    public synchronized void setOrThrow(V value) {
        if (isSet()) {
            throw StableUtil.alreadySet(this);
        }
        setValue(value);
    }

    @ForceInline
    @Override
    public V setIfUnset(V value) {
        if (isSet()) {
           return orThrow();
        }
        synchronized (this) {
            if (isSet()) {
                return orThrow();
            }
            setValue(value);
            return value;
        }
    }

    @ForceInline
    @Override
    public V computeIfUnset(Supplier<? extends V> supplier) {
        // Optimistically try plain semantics first
        V v = value;
        if (v != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return v;
        }
        if (set()) {
            // The value is set to its default value (e.g. `null`)
            return null;
        }
        // Now, fall back to volatile semantics.
        v = valueVolatile();
        if (v != null) {
            // If we see a non-null value, we know a value is set.
            return v;
        }
        if (setVolatile()) {
            // The value is set
            return null;
        }
        synchronized (this) {
            if (set()) {
                return orThrow();
            }
            // A value is not set
            V newValue = supplier.get();
            setValue(newValue);
            return newValue;
        }
    }

    @Override
    public String toString() {
        return StableUtil.toString(this);
    }

    @SuppressWarnings("unchecked")
    private V valueVolatile() {
        return (V)UNSAFE.getReferenceVolatile(this, VALUE_OFFSET);
    }

    private void setValue(V value) {
        if (value != null) {
            casValue(value);
        }
        // This prevents `this.value` to be seen before `this.set` is seen
        freeze();
        // Crucially, indicate a value is set _after_ it has actually been set.
        casSet();
    }

    private void casValue(V value) {
        // This prevents partially initialized objects to be observed
        // under normal memory semantics.
        freeze();
        if (!UNSAFE.compareAndSetReference(this, VALUE_OFFSET, null, value)) {
            throw StableUtil.alreadySet(this);
        }
    }

    private boolean set() {
        return set == SET;
    }

    private boolean setVolatile() {
        return UNSAFE.getByteVolatile(this, SET_OFFSET) == SET;
    }

    private void casSet() {
        if (!UNSAFE.compareAndSetByte(this, SET_OFFSET, NOT_SET, SET)) {
            throw StableUtil.alreadySet(this);
        }
    }

    // Factories

    public static <V> StableValue<V> of() {
        return new StableValueImpl<>();
    }

    public static <V> StableValue<V> ofBackground(Supplier<? extends V> supplier) {
        StableValue<V> stable = StableValue.of();
        Thread.ofVirtual()
                .start(() -> {
                    try {
                        stable.computeIfUnset(supplier);
                    } catch (Throwable _) {}
                });
        return stable;
    }

}
