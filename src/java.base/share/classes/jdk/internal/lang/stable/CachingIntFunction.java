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

import java.util.Arrays;
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
 * @param <R> the return type
 */
public final class CachingIntFunction<R> implements IntFunction<R> {

    private final IntFunction<? extends R> original;
    private final Object[] mutexes;
    @Stable
    private final Object[] values;

    public CachingIntFunction(int size,
                              IntFunction<? extends R> original) {
        this.original = original;
        this.mutexes = new Object[size];
        for (int i = 0; i < size; i++) {
            mutexes[i] = new Object();
        }
        this.values = new Object[size];
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    @Override
    public R apply(int value) {
        try {
            Objects.checkIndex(value, values.length);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
        R r = StableValueUtil.getAcquire(values, StableValueUtil.arrayOffset(value));
        if (r != null) {
            return StableValueUtil.unwrap(r);
        }
        synchronized (mutexes[value]) {
            r = (R) values[value];
            if (r != null) {
                return StableValueUtil.unwrap(r);
            }
            r = original.apply(value);
            StableValueUtil.cas(values, StableValueUtil.arrayOffset(value), r);
        }
        return r;
    }

    public static <R> CachingIntFunction<R> of(int size, IntFunction<? extends R> original) {
        return new CachingIntFunction<>(size, original);
    }

    @Override
    public String toString() {
        return "CachingIntFunction[values=" +
                "[" + valuesAsString() + "]"
                + ", original=" + original + ']';
    }

    private String valuesAsString() {
        return IntStream.range(0, values.length)
                .mapToObj(i -> StableValueUtil.getAcquire(values, StableValueUtil.arrayOffset(i)))
                .map(v -> (v == this) ? "(this CachingIntFunction)" : StableValueUtil.render(v))
                .collect(Collectors.joining(", "));
    }

}
