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
import jdk.internal.lang.lazy.LazyImpl;
import jdk.internal.lang.lazy.LazyList;
import jdk.internal.lang.lazy.LazyListElement;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
 * Lazy collections, that are operating directly on element/values, are available via
 * the factories:
 * <ul>
 *     <li>{@linkplain #ofList(int, IntFunction)}</li>
 *     <li>{@linkplain #ofSet(Set, Predicate)}</li>
 *     <li>{@linkplain #ofSet(Class, Predicate)}</li>
 *     <li>{@linkplain #ofMap(Set, Function)}</li>
 *     <li>{@linkplain #ofMap(Class, Function)}</li>
 * </ul>
 * The returned collections above are all eligible for constant folding optimizations.
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
public sealed interface Lazy<V> permits LazyImpl, LazyListElement {

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
     * @param <V> the value type to bind
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
     * {@linkplain List} containing {@code size} elements which are
     * lazily computed upon being first accessed (e.g. via
     * {@linkplain List#get(int) List::get}) by invoking the provided {@code mapper}
     * at most once per element}
     * <p>
     * The provided {@code mapper} must not return {@code null} values.
     * <p>
     * The returned List is not {@linkplain Serializable}.
     * <p>
     * The returned lazy map is eligible for constant folding and other
     * optimizations by the JVM.
     *
     * @param <E>    the {@code List}'s element type
     * @param size   the number of elements in the list
     * @param mapper to invoke upon lazily computing element values
     * @throws IllegalArgumentException if the provided {@code size} is negative
     * @throws NullPointerException if the provided {@code mapper} is {@code null}
     *
     * @since 23
     */
    static <E> List<E> ofList(int size, IntFunction<? extends E> mapper) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(mapper);
        return LazyImpl.ofList(size, mapper);
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, lazy,
     * {@linkplain Set } where the computation of each distinct
     * {@linkplain java.util.Set#contains(Object) contains(candidate)} operation is
     * deferred to when first being called and can only be made for the distinct provided
     * set of {@code candidates} and where the elements' existence is lazily computed upon
     * being first queried by invoking the provided {@code predicate}
     * at most once per candidate}
     * <p>
     * The returned set is not {@linkplain Serializable}.
     * <p>
     * The returned set is eligible for constant folding and other
     * optimizations by the JVM.
     *
     * @param candidates the potential elements in the set
     * @param predicate  to apply when lazily computing containment
     * @param <E>        the type of elements maintained by this set
     * @throws NullPointerException if the provided {@code keys} or the provided
     *         {@code mapper} is null
     */
    static <E> Set<E> ofSet(Set<? extends E> candidates,
                            Predicate<? super E> predicate) {
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(predicate);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, lazy,
     * {@linkplain Set } where the computation of each distinct
     * {@linkplain java.util.Set#contains(Object) contains(candidate)} operation is
     * deferred to when first being called and can only be made for the enum elements
     * of the provided {@code enumType} and where the elements' existence is lazily
     * computed upon being first queried by invoking the provided {@code predicate}
     * at most once per candidate}
     * <p>
     * The returned set is not {@linkplain Serializable}.
     * <p>
     * The returned set is eligible for constant folding and other
     * optimizations by the JVM.
     *
     * @param enumType  the enum type signifying the potential enum elements
     * @param predicate to apply when lazily computing containment
     * @param <E>       the type of elements maintained by this set
     * @throws NullPointerException if the provided {@code enumType} or the provided
     *         {@code mapper} is null
     */
    static <E extends Enum<E>> Set<E> ofSet(Class<E> enumType,
                                            Predicate<? super E> predicate) {
        Objects.requireNonNull(enumType);
        Objects.requireNonNull(predicate);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, value-lazy,
     * {@linkplain Map } where the {@linkplain java.util.Map#keySet() keys}
     * contains precisely the distinct provided set of {@code keys} and where the
     * values are lazily computed upon being first accessed
     * (e.g. via {@linkplain Map#get(Object) get(key)}) by invoking the provided
     * {@code mapper} at most once per key}
     * <p>
     * The provided {@code mapper} must not return {@code null} values.
     * <p>
     * The returned map is not {@linkplain Serializable}.
     * <p>
     * The returned map is eligible for constant folding and other
     * optimizations by the JVM.
     *
     * @param keys   the keys in the map
     * @param mapper to apply when lazily computing values
     * @param <K>    the type of keys maintained by the returned map
     * @param <V>    the type of mapped values
     * @throws NullPointerException if the provided {@code keys} or the provided
     *         {@code mapper} is null
     */
    static <K, V> Map<K, V> ofMap(Set<? extends K> keys,
                                  Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, value-lazy,
     * {@linkplain Map } where the {@linkplain java.util.Map#keySet() keys}
     * contains the enum elements of the provided {@code enumType} and where the
     * values are lazily computed upon being first accessed
     * (e.g. via {@linkplain Map#get(Object) get(key)}) by invoking the provided
     * {@code mapper} at most once per key}
     * <p>
     * The provided {@code mapper} must not return {@code null} values.
     * <p>
     * The returned map is not {@linkplain Serializable}.
     * <p>
     * The returned map is eligible for constant folding and other
     * optimizations by the JVM.
     *
     * @param enumType the enum type signifying the enum key elements
     * @param mapper   to apply when lazily computing values
     * @param <K>      the type of enum keys maintained by the returned map
     * @param <V>      the type of mapped values
     * @throws NullPointerException if the provided {@code enumType} or the provided
     *         {@code mapper} is null
     */
    static <K extends Enum<K>, V> Map<K, V> ofMap(Class<K> enumType,
                                                  Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(enumType);
        Objects.requireNonNull(mapper);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return an unmodifiable, shallowly immutable, thread-safe, lazy,
     * {@linkplain List} containing {@code size} {@linkplain Lazy} elements}
     * <p>
     * The returned List is not {@linkplain Serializable}.
     * <p>
     * The returned lazy map is eligible for constant folding and other
     * optimizations by the JVM.
     *
     * @param <E>    the {@code List}'s element type
     * @param size   the number of elements in the list
     * @throws IllegalArgumentException if the provided {@code size} is negative
     *
     * @since 23
     */
    static <E> List<Lazy<E>> ofWrappedList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        return LazyList.of(size);
    }

}
