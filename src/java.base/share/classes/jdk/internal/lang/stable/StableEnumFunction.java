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

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Optimized implementation of a stable Function with enums as keys.
 *
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param firstOrdinal the lowest ordinal used
 * @param delegates    a delegate array of inputs to StableValue mappings
 * @param original     the original Function
 * @param <E>          the type of the input to the function
 * @param <R>          the type of the result of the function
 */
record StableEnumFunction<E extends Enum<E>, R>(Class<E> enumType,
                                                int firstOrdinal,
                                                @Stable StableValueImpl<R>[] delegates,
                                                Function<? super E, ? extends R> original) implements Function<E, R> {
    @ForceInline
    @Override
    public R apply(E value) {
        final int index = value.ordinal() - firstOrdinal;
        try {
            return delegates[index]
                    .computeIfUnset(new Supplier<R>() {
                        @Override public R get() { return original.apply(value); }});
        } catch (ArrayIndexOutOfBoundsException ioob) {
            throw new IllegalArgumentException("Input not allowed: " + value, ioob);
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
        return "StableEnumFunction[values=" + renderElements() + ", original=" + original + "]";
    }

    private String renderElements() {
        final StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        int ordinal = firstOrdinal;
        final E[] enumElements = enumType.getEnumConstants();
        for (int i = 0; i < delegates.length; i++) {
            if (first) { first = false; } else { sb.append(", "); };
            final Object value = delegates[i].wrappedValue();
            sb.append(enumElements[ordinal++]).append('=');
            if (value == this) {
                sb.append("(this StableEnumFunction)");
            } else {
                sb.append(StableValueImpl.renderWrapped(value));
            }
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    static <T, E extends Enum<E>, R> Function<T, R> of(Set<? extends T> inputs,
                                                       Function<? super T, ? extends R> original) {
        // The input set is not empty
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (T t : inputs) {
            min = Math.min(min, ((E) t).ordinal());
            max = Math.max(max, ((E) t).ordinal());
        }
        final int size = max - min + 1;
        final Class<E> enumType = (Class<E>)inputs.iterator().next().getClass();
        return (Function<T, R>) new StableEnumFunction<E, R>(enumType, min, StableValueFactories.ofArray(size), (Function<E, R>) original);
    }

}
