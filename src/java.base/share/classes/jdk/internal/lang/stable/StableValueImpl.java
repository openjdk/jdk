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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.Objects;

public final class StableValueImpl<T> implements StableValue<T> {

    // Unsafe offsets for direct field access
    private static final long VALUE_OFFSET =
            StableValueUtil.UNSAFE.objectFieldOffset(StableValueImpl.class, "wrappedValue");

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

    // Only allow creation via the factory `StableValueImpl::newInstance`
    private StableValueImpl() {}

    @ForceInline
    @Override
    public boolean trySet(T newValue) {
        if (wrappedValue != null) {
            return false;
        }
        return StableValueUtil.wrapAndCas(this, VALUE_OFFSET, newValue);
    }

    @ForceInline
    @Override
    public T orElseThrow() {
        final Object t = wrappedValue;
        if (t != null) {
            return StableValueUtil.unwrap(t);
        }
        throw new NoSuchElementException("No value set");
    }

    @ForceInline
    @Override
    public T orElse(T other) {
        final Object t = wrappedValue;
        if (t != null) {
            return StableValueUtil.unwrap(t);
        }
        return other;
    }

    @ForceInline
    @Override
    public boolean isSet() {
        return wrappedValue != null;
    }

    @Override
    public int hashCode() {
        final Object t = wrappedValue;
        return t == this
                ? 1
                : Objects.hashCode(t);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof StableValueImpl<?> other &&
                // Note that the returned `value()` will be `null` if the holder value
                // is unset and `nullSentinel()` if the holder value is `null`.
                Objects.equals(wrappedValue, other.wrappedValue);
    }

    @Override
    public String toString() {
        final Object t = wrappedValue;
        return t == this
                ? "(this StableValue)"
                : "StableValue" + StableValueUtil.render(t);
    }

    @ForceInline
    public Object wrappedValue() {
        return wrappedValue;
    }

    // Factory

    public static <T> StableValueImpl<T> newInstance() {
        return new StableValueImpl<>();
    }

}
