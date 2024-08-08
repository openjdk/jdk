/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.openjdk.bench.java.lang.stable;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

final class CustomCachingFunctions {

    private CustomCachingFunctions() {}

    record Pair<L, R>(L left, R right){}

    static <T> Predicate<T> cachingPredicate(Set<? extends T> inputs,
                                             Predicate<? super T> original) {

        final Function<T, Boolean> delegate = StableValue.newCachingFunction(inputs, original::test, null);
        return delegate::apply;
    }

    static <T, U, R> BiFunction<T, U, R> cachingBiFunction(Set<Pair<T, U>> inputs,
                                                           BiFunction<? super T, ? super U, ? extends R> original) {

        final Map<T, Set<U>> tToUs = inputs.stream()
                .collect(Collectors.groupingBy(Pair::left,
                        Collectors.mapping(Pair::right, Collectors.toSet())));

        // Collectors.toUnmodifiableMap() is crucial!
        final Map<T, Function<U, R>> map = tToUs.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), StableValue.<U, R>newCachingFunction(e.getValue(), (U u) -> original.apply(e.getKey(), u), null)))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        return (T t, U u) -> {
            final Function<U,R> function = map.get(t);
            if (function != null) {
                try {
                    return function.apply(u);
                } catch (IllegalArgumentException iae) {
                    // The original function might throw
                    throw new IllegalArgumentException(t.toString() + ", " + u.toString(), iae);
                }
            }
            throw new IllegalArgumentException(t.toString() + ", " + u.toString());
        };
    }

    static <T> BinaryOperator<T> cachingBinaryOperator(Set<Pair<T, T>> inputs,
                                                       BinaryOperator<T> original) {

        final BiFunction<T, T, T> biFunction = cachingBiFunction(inputs, original);
        return biFunction::apply;
    }

    static <T> UnaryOperator<T> cachingUnaryOperator(Set<? extends T> inputs,
                                                     UnaryOperator<T> original) {

        final Function<T, T> function = StableValue.newCachingFunction(inputs, original, null);
        return function::apply;

    }

    static IntSupplier cachingIntSupplier(IntSupplier original) {
        final Supplier<Integer> delegate = StableValue.newCachingSupplier(original::getAsInt, null);
        return delegate::get;
    }

}
