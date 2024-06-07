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

package jdk.internal.lang;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

/**
 * A number of static methods returning constructs with StableValues.
 *
 * <p>The methods of this class all throw {@code NullPointerException}
 * if provided with {@code null} arguments.
 * <p>
 * The constructs returned are eligible for similar JVM optimizations as the
 * {@linkplain StableValue} itself.
 *
 * @since 24
 */
public final class StableValues {

    // Suppresses default constructor, ensuring non-instantiability.
    private StableValues() {}

    /**
     * {@return a fresh unset StableValue who's value is computed by a new Thread created
     * via the provided {@code factory} where the new Thread will invoke the provided
     * {@code supplier}}.
     * <p>
     * If an Exception is thrown by the supplier, it be caught by the new Thread's
     * {@linkplain Thread#getUncaughtExceptionHandler() uncaught exception handler} and
     * no value will be set in the returned StableValue.
     * <p>
     * The method is equivalent to the following for a given non-null {@code factory} and
     * {@code supplier}:
     * {@snippet lang = java :
     *     StableValue<T> stable = StableValue.newInstance();
     *     Thread thread = factory.newThread(() -> stable.computeIfUnset(supplier));
     *     thread.start();
     * }
     *
     * @param factory  to create new background threads from
     * @param supplier that can provide a value for the returned StableValue
     * @param <T>      type for the returned StableValue
     */
    public static <T> StableValue<T> ofBackground(ThreadFactory factory,
                                                  Supplier<? extends T> supplier) {
        Objects.requireNonNull(factory);
        Objects.requireNonNull(supplier);
        final StableValue<T> stable = StableValue.newInstance();
        final Thread thread = factory.newThread(new Runnable() {
            @Override public void run() { stable.computeIfUnset(supplier); }
        });
        thread.start();
        return stable;
    }

    /**
     * {@return a shallowly immutable, stable List of distinct fresh stable values}
     * <p>
     * The method is equivalent to the following for a given non-negative {@code size}:
     * {@snippet lang = java :
     *     List<StableValue<T>> list = Stream.generate(StableValue::<T>newInstance)
     *                 .limit(size)
     *                 .toList();
     * }
     * @param size the size of the returned list
     * @param <T>  the {@code StableValue}s' element type
     */
    public static <T> List<StableValue<T>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        if (size == 0) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        final StableValue<T>[] stableValues = (StableValue<T>[]) new StableValue<?>[size];
        for (int i = 0; i < size; i++) {
            stableValues[i] = StableValue.newInstance();
        }
        return List.of(stableValues);
    }

    /**
     * {@return a shallowly immutable, stable Map with the provided {@code keys}
     * and associated distinct fresh stable values}
     * <p>
     * The method is equivalent to the following for a given non-null {@code keys}:
     * {@snippet lang = java :
     *     Map<K, StableValue<T>> map = keys.stream()
     *                 .collect(Collectors.toMap(
     *                     Function.identity(),
     *                     _ -> StableValue.newInstance()));
     * }
     * @param keys   the keys in the {@code Map}
     * @param <K> the {@code Map}'s key type
     * @param <T> the StableValue's type for the {@code Map}'s value type
     */
    public static <K, T> Map<K, StableValue<T>> ofMap(Set<K> keys) {
        Objects.requireNonNull(keys);
        if (keys.isEmpty()) {
            return Map.of();
        }
        @SuppressWarnings("unchecked")
        Map.Entry<K, StableValue<T>>[] entries = (Map.Entry<K, StableValue<T>>[]) new Map.Entry<?, ?>[keys.size()];
        int i = 0;
        for (K key : keys) {
            entries[i++] = Map.entry(key, StableValue.newInstance());
        }
        return Map.ofEntries(entries);
    }

}
