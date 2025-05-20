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

import jdk.internal.util.ImmutableBitSetPredicate;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Optimized implementation of a stable Function with enums as keys.
 *
 * @implNote This implementation can be used early in the boot sequence as it does not
 *           rely on reflection, MethodHandles, Streams etc.
 *
 * @param enumType     the class type of the Enum
 * @param firstOrdinal the lowest ordinal used
 * @param member       an int predicate that can be used to test if an enum is a member
 *                     of the valid inputs (as there might be "holes")
 * @param delegates    a delegate array of inputs to StableValue mappings
 * @param original     the original Function
 * @param <E>          the type of the input to the function
 * @param <R>          the type of the result of the function
 */
public record StableEnumFunction<E extends Enum<E>, R>(Class<E> enumType,
                                                       int firstOrdinal,
                                                       IntPredicate member,
                                                       @Stable StableValueImpl<R>[] delegates,
                                                       Function<? super E, ? extends R> original) implements Function<E, R> {
    @ForceInline
    @Override
    public R apply(E value) {
        if (!member.test(value.ordinal())) { // Implicit null-check of value
            throw new IllegalArgumentException("Input not allowed: " + value);
        }
        final int index = value.ordinal() - firstOrdinal;
        // Since we did the member.test above, we know the index is in bounds
        return delegates[index].orElseSet(new Supplier<R>() {
                    @Override public R get() { return original.apply(value); }});

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
        final Collection<Map.Entry<E, StableValueImpl<R>>> entries = new ArrayList<>(delegates.length);
        final E[] enumElements = enumType.getEnumConstants();
        int ordinal = firstOrdinal;
        for (int i = 0; i < delegates.length; i++, ordinal++) {
            if (member.test(ordinal)) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(enumElements[ordinal], delegates[i]));
            }
        }
        return StableUtil.renderMappings(this, "StableFunction", entries, true);
    }

    @SuppressWarnings("unchecked")
    public static <T, E extends Enum<E>, R> Function<T, R> of(Set<? extends T> inputs,
                                                              Function<? super T, ? extends R> original) {
        // The input set is not empty
        final Class<E> enumType = ((E) inputs.iterator().next()).getDeclaringClass();
        final BitSet bitSet = new BitSet(enumType.getEnumConstants().length);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (T t : inputs) {
            final int ordinal = ((E) t).ordinal();
            min = Math.min(min, ordinal);
            max = Math.max(max, ordinal);
            bitSet.set(ordinal);
        }
        final int size = max - min + 1;
        final IntPredicate member = ImmutableBitSetPredicate.of(bitSet);
        return (Function<T, R>) new StableEnumFunction<E, R>(enumType, min, member, StableUtil.array(size), (Function<E, R>) original);
    }

}
