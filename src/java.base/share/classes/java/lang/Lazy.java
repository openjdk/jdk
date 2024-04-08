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
import jdk.internal.javac.Restricted;
import jdk.internal.lang.lazy.LazyImpl;
import jdk.internal.lang.lazy.LazyList;
import jdk.internal.lang.lazy.LazyListElement;

import java.io.Serializable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static jdk.internal.javac.PreviewFeature.*;

/**
 * An atomic, thread-safe, lazy value holder for which the value can be set at most once.
 * <p>
 * Lazy values are eligible for constant folding and other optimizations by the JVM.
 * <p>
 * A Lazy value is said to be monotonic because the state of a lazy value can only go from
 * <em>unset</em> to <em>set</em> and consequently, a value can only be set
 * at most once.
 * <p>
 * To create collections of <em>wrapped Lazy elements</em>, that, in turn, are also
 * eligible for constant folding optimizations, the following patterns can be used:
 * <ul>
 *     <li>{@snippet lang=java :
 *     List<Lazy<V>> list = Stream.generate(Lazy::<V>of)
 *             .limit(size)
 *             .toList();
 *     }</li>
 *
 * <li>{@snippet lang=java :
 *     Map<K, Lazy<V>> map = Map.copyOf(keys.stream()
 *             .distinct()
 *             .map(Objects::requireNonNull)
 *             .collect(Collectors.toMap(Function.identity(), _ -> Lazy.of())));
 *     }</li>
 *</ul>
 *
 * @param <V> value type
 * @since 23
 */
@PreviewFeature(feature = Feature.LAZY_VALUES_AND_COLLECTIONS)
public sealed interface Lazy<V>
        permits LazyImpl,
        LazyListElement {

    /**
     * {@return the set value (nullable) if set, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no value is set
     */
    V orThrow();

    /**
     * {@return {@code true} if a value is set, otherwise {@code false}}
     */
    boolean isSet();

    /**
     * Sets the lazy value to the provided (nullable) {@code value} or throws an
     * {@linkplain IllegalStateException} if a value is already set.
     *
     * @param value to set
     * @throws IllegalStateException if a value is already set
     */
    void setOrThrow(V value);

    /**
     * If no value is set, sets the lazy value to the provided (nullable)
     * {@code value}, returning the (pre-existing or newly set) value.
     * <p>
     * If several threads invoke this method simultaneously, only one thread will succeed
     * in setting a value and that (witness) value will be returned to all threads.
     *
     * @param value to set
     * @return the bound value
     */
    V setIfUnset(V value);

    /**
     * If no value {@linkplain #isSet() is set}, attempts to compute and set a
     * new (nullable) value using the provided {@code supplier}, returning the
     * (pre-existing or newly set) value.
     *
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is rethrown, and no
     * value is set. The most common usage is to construct a new object serving as a
     * lazily computed value or memoized result, as in:
     *
     * <pre> {@code
     * Value witness = lazy.computeIfUnset(Value::new);
     * }</pre>
     *
     * @implSpec The implementation logic is equivalent to the following steps for this
     * {@code lazy}:
     *
     * <pre> {@code
     * if (lazy.isBound()) {
     *     return lazy.get();
     * } else {
     *     V newValue = supplier.get();
     *     lazy.setOrThrow(newValue);
     *     return newValue;
     * }
     * }</pre>
     * Except it is atomic, thread-safe and will only return the same witness value
     * regardless if invoked by several threads. Also, the provided {@code supplier}
     * will only be invoked once even if invoked from several threads.
     *
     * @param supplier to be used for computing a value
     * @return the current (pre-existing or computed) value
     */
    V computeIfUnset(Supplier<? extends V> supplier);

    // Factories

    /**
     * {@return a fresh lazy with an unset value}
     *
     * @param <V> the value type to set
     */
    static <V> Lazy<V> of() {
        return LazyImpl.of();
    }

    /**
     * {@return a fresh lazy with an unset value where the returned lazy's
     * value is computed in a separate fresh background thread using the provided
     * {@code supplier}}
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is ignored, and no
     * value is set.
     *
     * @param <V>      the value type to set
     * @param supplier to be used for computing a value
     * @see Lazy#of
     */
    static <V> Lazy<V> ofBackground(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return LazyImpl.ofBackground(supplier);
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, lazy,
     * {@linkplain List} containing {@code size} {@linkplain Lazy} elements}
     * <p>
     * Neither the returned list nor its elements are {@linkplain Serializable}.
     * <p>
     * The returned list and its elements are eligible for constant folding and other
     * optimizations by the JVM and is equivalent to:
     * {@snippet lang = java:
     * List<Lazy<V>> list = Stream.generate(Lazy::<V>of)
     *         .limit(size)
     *         .toList();
     * }
     * Except it require less storage, does not return Lazy instances with the same
     * identity, and is likely to exhibit better performance.
     * <p>
     * This static factory methods return list instances (and with all their elements)
     * that are <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>,
     * immutable and thread-safe. Programmers should not use list and element instances for
     * synchronization, or unpredictable behavior may occur. For example, in a
     * future release, synchronization may fail. Consequently,
     * query methods {@linkplain List#contains(Object)},
     * {@linkplain List#containsAll(Collection)} (if non-empty),
     * {@linkplain List#indexOf(Object)}, {@linkplain List#lastIndexOf(Object)}, and
     * methods that relies on these method or similar methods will always indicate no match.
     *
     * @param <V>  the generic type of the Lazy elements in the returned {@code List}
     * @param size the number of elements in the list
     * @throws IllegalArgumentException if the provided {@code size} is negative
     *
     * @since 23
     */
    static <V> List<Lazy<V>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return LazyImpl.ofList(size);
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, value-lazy,
     * {@linkplain Map } where the {@linkplain java.util.Map#keySet() keys}
     * contains precisely the distinct provided set of {@code keys} and where the
     * Lazy values are, in turn, lazily computed upon being accessed
     * (e.g. via {@linkplain Map#get(Object) get(key)})}
     * <p>
     * Neither the returned map nor its values are {@linkplain Serializable}.
     * <p>
     * The returned map and its values are eligible for constant folding and other
     * optimizations by the JVM and is equivalent to:
     * {@snippet lang = java:
     * Map<K, Lazy<V>> map = Map.copyOf(keys.stream()
     *         .distinct()
     *         .map(Objects::requireNonNull)
     *         .collect(Collectors.toMap(Function.identity(), _ -> Lazy.of())));
     * }
     * Except it require less storage, does not return Lazy instances with the same
     * identity, and is likely to exhibit better performance.
     * <p>
     * This static factory methods return map instances (and with all their values)
     * that are <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>,
     * immutable and thread-safe. Programmers should not use map and value instances for
     * synchronization, or unpredictable behavior may occur. For example, in a
     * future release, synchronization may fail. Consequently,
     * the query methods {@linkplain Map#containsValue(Object)} and methods that relies
     * on this and similar method will always indicate no match.
     * <p>
     * As an emerging property, providing an {@linkplain EnumSet} as {@code keys} will
     * make the returned Map eligible for certain additional optimizations.
     *
     * @param keys the keys in the map
     * @param <K>  the type of keys maintained by the returned map
     * @param <V>  the type of mapped values
     * @throws NullPointerException if the provided {@code keys} parameter is {@code null}
     */
    static <K, V> Map<K, Lazy<V>> ofMap(Set<? extends K> keys) {
        Objects.requireNonNull(keys);
        return LazyImpl.ofMap(keys);
    }

    /**
     * If no value {@linkplain #isSet() is set} for the Lazy at the provided
     * {@code index}, attempts to compute and set it as per
     * {@linkplain Lazy#computeIfUnset(Supplier)} by applying the given {@code mapper}.
     * <p>
     * This is equivalent to:
     * {@snippet lang=java:
     * Lazy<V> lazy = list.get(index);
     * if (lazy.isSet()) {
     *     return lazy.orThrow();
     * }
     * Supplier<V> supplier = () -> mapper.apply(index);
     * return lazy.computeIfUnset(supplier);
     * }
     * Except it might be more efficient and performant.
     *
     * @param list   from which to get a Lazy
     * @param index  for the Lazy
     * @param mapper to apply if the Lazy at the provided {@code index} is
     *               {@linkplain Lazy#isSet() not set}
     * @return the current (pre-existing or computed) value at the provided {@code index}
     * @param <V> the Lazy value type to set
     * @throws IndexOutOfBoundsException if the provided {@code index} is less than
     *         zero or {@code index >= list.size()}
     */
    static <V> V computeIfUnset(List<Lazy<V>> list,
                                int index,
                                IntFunction<? extends V> mapper) {
        Objects.requireNonNull(list);
        Objects.checkIndex(index, list.size());
        Objects.requireNonNull(mapper);
        return LazyImpl.computeIfUnset(list, index, mapper);
    }

    /**
     * If no value {@linkplain #isSet() is set} for the Lazy for the provided
     * {@code key}, attempts to compute and set it as per
     * {@linkplain Lazy#computeIfUnset(Supplier)} by applying the given {@code mapper}.
     * <p>
     * This is equivalent to:
     * {@snippet lang=java:
     * Lazy<V> lazy = map.get(key);
     * if (lazy == null) {
     *      throw new NoSuchElementException("Unknown key: "+key);
     * }
     * if (lazy.isSet()) {
     *     return lazy.orThrow();
     * }
     * Supplier<V> supplier = () -> mapper.apply(key);
     * return lazy.computeIfUnset(supplier);
     * }
     * Except it might be more efficient and performant.
     *
     * @param map    from which to get a Lazy
     * @param key    for the Lazy
     * @param mapper to apply if the Lazy at the provided {@code key} is
     *               {@linkplain Lazy#isSet() not set}
     * @return the current (pre-existing or computed) value for the provided {@code key}
     * @param <K> the type of keys maintained by this map
     * @param <V> the Lazy value type to set
     * @throws NoSuchElementException if the provided {@code map} does not
     *         {@linkplain Map#containsKey(Object) contain} the provided {@code key}
     */
    static <K, V> V computeIfUnset(Map<K, Lazy<V>> map,
                                   K key,
                                   Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(key);
        Objects.requireNonNull(mapper);
        return LazyImpl.computeIfUnset(map, key, mapper);
    }

}
