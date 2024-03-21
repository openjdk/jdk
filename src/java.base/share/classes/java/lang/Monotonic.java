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

package java.lang;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.lang.monotonic.MonotonicImpl;
import jdk.internal.lang.monotonic.MonotonicList;
import jdk.internal.lang.monotonic.MonotonicMap;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static jdk.internal.javac.PreviewFeature.*;

/**
 * A <em>monotonic value</em> that can be atomically bound at most once.
 * <p>
 * Monotonic values are eligible for constant folding and other optimizations by the JVM.
 * <p>
 * The state of a monotonic value can only go from <em>absent</em> to <em>bound</em> and
 * consequently, a value can only be bound at most once.
 *
 * @implSpec The implementation of this interface is thread-safe, atomic, and non-blocking.
 *
 * @param <V> value type
 * @since 23
 */
@PreviewFeature(feature = Feature.MONOTONIC_VALUES)
public sealed interface Monotonic<V> permits MonotonicImpl {

    /**
     * {@return the (nullable) value if bound, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no value is present
     */
    V get();

    /**
     * {@return {@code true} if a value is bound, otherwise {@code false}}
     */
    boolean isBound();

    /**
     * Binds the monotonic value to the provided (nullable) {@code value} or throws an
     * exception if a value is already bound.
     *
     * @param value to bind
     * @throws IllegalStateException if a value is already bound
     */
    void bindOrThrow(V value);

    /**
     * If no value is present, binds the monotonic value to the provided (nullable)
     * {@code value}, returning the (pre-existing or newly bound) value.
     * <p>
     * If several threads invoke this method simultaneously, only one thread will succeed
     * in binding a value and that (witness) value will be returned to all threads.
     *
     * @param value to bind
     * @return the bound value
     */
    V bindIfAbsent(V value);

    /**
     * If no value {@linkplain #isBound() is bound}, attempts to compute and bind a
     * new (nullable) value using the provided {@code supplier}, returning the
     * (pre-existing or newly bound) value.
     *
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is rethrown, and no
     * value is bound. The most common usage is to construct a new object serving as a
     * lazily computed value or memoized result, as in:
     *
     * <pre> {@code
     * Value witness = monotonic.computeIfAbsent(Value::new);
     * }</pre>
     *
     * @implSpec The implementation logic is equivalent to the following steps for this
     * {@code monotonic}:
     *
     * <pre> {@code
     * if (monotonic.isPresent()) {
     *     return monotonic.get();
     * } else {
     *     V newValue = supplier.get();
     *     monotonic.put(newValue);
     *     return newValue;
     * }
     * }</pre>
     * Except it is atomic, thread-safe and will only return the same witness value
     * regardless if invoked by several threads.
     *
     * <p>
     * The implementation guarantees the provided {@code supplier} is invoked once
     * (if successful) even if invoked from several threads.
     *
     * @param supplier to be used for computing a value
     * @return the current (pre-existing or computed) value
     */
    V computeIfAbsent(Supplier<? extends V> supplier);

    // Factories

    /**
     * {@return a fresh Monotonic with an absent value}
     *
     * @param <V> the value type to bind
     */
    static <V> Monotonic<V> of() {
        return MonotonicImpl.of();
    }

    /**
     * {@return a new unmodifiable, shallowly immutable, thread-safe, lazy, non-blocking
     * {@linkplain List } of {@code size} distinct absent monotonic values}
     * <p>
     * The returned {@code List} is equivalent to the following List:
     * {@snippet lang=java :
     * List<Monotonic<V>> list = Stream.generate(Monotonic::<V>of)
     *         .limit(size)
     *         .toList();
     * }
     * except it creates the list's elements lazily as they are accessed.
     * <p>
     * The returned monotonic list is eligible for constant folding and other
     * optimizations by the JVM.
     *
     * @param size the size of the returned monotonic list
     * @param <V>  the value type for the Monotonic elements in this list
     * @see List#of()
     */
    static <V> List<Monotonic<V>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return MonotonicList.of(size);
    }

    /**
     * {@return a new unmodifiable, shallowly immutable, thread-safe, value-lazy,
     * non-blocking {@linkplain Map } where the {@linkplain java.util.Map#keySet() keys}
     * contains precisely the distinct provided {@code keys} and where the values are
     * distinct absent monotonic values}
     * <p>
     * The returned {@code Map} is equivalent to the following Map:
     * {@snippet lang=java :
     * Map<K, Monotonic<V>> map = Map.copyOf(keys.stream()
     *         .distinct()
     *         .map(Objects::requireNonNull)
     *         .collect(Collectors.toMap(Function.identity(), _ -> Monotonic.of())));
     * }
     * except it creates the map's values lazily as they are accessed.
     * <p>
     * The returned monotonic map is eligible for constant folding and other
     * optimizations by the JVM.
     *
     * @param keys the keys in the map
     * @param <K>  the type of keys maintained by the returned map
     * @param <V>  the value type for the Monotonic values in this map
     * @see Map#of()
     */
    // Set
    static <K, V> Map<K, Monotonic<V>> ofMap(Collection<? extends K> keys) {
        Objects.requireNonNull(keys);
        // Checks for null keys and removes any duplicates
        Object[] keyArray = Set.copyOf(keys)
                .toArray();
        return MonotonicMap.ofMap(keyArray);
    }

}
