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

import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// Note: It would be possible to just use `LazyList::get` instead of this
// class but explicitly providing a class like this provides better
// debug capability, exception handling, and may provide better performance.
/**
 * Implementation of a cached IntFunction.
 * <p>
 * For performance reasons (~10%), we are not delegating to a StableList but are using
 * the more primitive functions in StableValueUtil that are shared with StableList/StableValueImpl.
 *
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param <R> the return type
 */
record CachingIntFunction<R>(IntFunction<? extends R> original,
                             Object[] mutexes,
                             Object[] wrappedValues) implements IntFunction<R> {

    @ForceInline
    @Override
    public R apply(int index) {
        try {
            Objects.checkIndex(index, wrappedValues.length);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
        Object r = wrappedValue(index);
        if (r != null) {
            return StableValueUtil.unwrap(r);
        }
        synchronized (mutexes[index]) {
            r = wrappedValues[index];
            if (r != null) {
                return StableValueUtil.unwrap(r);
            }
            final R newValue = original.apply(index);
            StableValueUtil.wrapAndCas(wrappedValues, StableValueUtil.arrayOffset(index), newValue);
            return newValue;
        }
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public String toString() {
        return "CachingIntFunction[values=" +
                renderElements() +
                ", original=" + original + ']';
    }

    private String renderElements() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (int i = 0; i < wrappedValues.length; i++) {
            if (first) { first = false; } else { sb.append(", "); };
            final Object value = wrappedValue(i);
            if (value == this) {
                sb.append("(this CachingIntFunction)");
            } else {
                sb.append(StableValueUtil.renderWrapped(value));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @ForceInline
    private Object wrappedValue(int i) {
        return StableValueUtil.UNSAFE.getReferenceVolatile(wrappedValues, StableValueUtil.arrayOffset(i));
    }

    static <R> CachingIntFunction<R> of(int size, IntFunction<? extends R> original) {
        var mutexes = new Object[size];
        for (int i = 0; i < size; i++) {
            mutexes[i] = new Object();
        }
        return new CachingIntFunction<>(original, mutexes, new Object[size]);
    }

}
