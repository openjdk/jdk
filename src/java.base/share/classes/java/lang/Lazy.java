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
import jdk.internal.lang.monotonic.LazyImpl;

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
 * An atomic, thread-safe, non-blocking, lazy value holder for which the value can
 * be bound at most once.
 * <p>
 * Lazy values are eligible for constant folding and other optimizations by the JVM.
 * <p>
 * A Lazy value is monotonic because the state of a lazy value can only go from
 * <em>unbound</em> to <em>bound</em> and consequently, a value can only be bound
 * at most once.
 * <p>
 * Lazy collections, that are operating directly on element/values, are available via
 * the factories:
 * <ul>
 *     <li>{@linkplain List#ofLazy(int, IntFunction)}</li>
 *     <li>{@linkplain Set#ofLazy(Set, Predicate)}</li>
 *     <li>{@linkplain Set#ofLazyEnum(Set, Predicate)}</li>
 *     <li>{@linkplain Map#ofLazy(Set, Function)}</li>
 *     <li>{@linkplain Map#ofLazyEnum(Set, Function)}</li>
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
 *</ul>>
 *
 * @param <V> value type
 * @since 23
 */
@PreviewFeature(feature = Feature.LAZY_COLLECTIONS_AND_VALUES)
public sealed interface Lazy<V> permits LazyImpl {

    /**
     * {@return the bound value (nullable) if bound, otherwise throws
     * {@code NoSuchElementException}}
     *
     * @throws NoSuchElementException if no value is bound
     */
    V orThrow();

    /**
     * {@return {@code true} if a value is bound, otherwise {@code false}}
     */
    boolean isBound();

    /**
     * Binds the lazy value to the provided (nullable) {@code value} or throws an
     * exception if a value is already bound.
     *
     * @param value to bind
     * @throws IllegalStateException if a value is already bound
     */
    void bindOrThrow(V value);

    /**
     * If no value is bound, binds the lazy value to the provided (nullable)
     * {@code value}, returning the (pre-existing or newly bound) value.
     * <p>
     * If several threads invoke this method simultaneously, only one thread will succeed
     * in binding a value and that (witness) value will be returned to all threads.
     *
     * @param value to bind
     * @return the bound value
     */
    V bindIfUnbound(V value);

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
     * Value witness = lazy.computeIfUnbound(Value::new);
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
     *     lazy.put(newValue);
     *     return newValue;
     * }
     * }</pre>
     * Except it is atomic, thread-safe and will only return the same witness value
     * regardless if invoked by several threads. However, the provided {@code supplier}
     * may be invoked several times if invoked from several threads 
     *
     * @param supplier to be used for computing a value
     * @return the current (pre-existing or computed) value
     */
    V computeIfUnbound(Supplier<? extends V> supplier);

    // Factories

    /**
     * {@return a fresh lazy with an unbound value}
     *
     * @param <V> the value type to bind
     */
    static <V> Lazy<V> of() {
        return LazyImpl.of();
    }

    /**
     * {@return a new lazy with an unbound value where the returned lazy's
     * value is computed in a separate fresh background thread using the provided
     * (@code supplier}}
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is ignored, and no
     * value is bound.
     *
     * @param <V>      the value type to bind
     * @param supplier to be used for computing a value
     * @see Lazy#of
     */
    static <V> Lazy<V> ofBackground(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return LazyImpl.ofBackground(supplier);
    }

    /**
     * {@return a wrapped, thread-safe, memoized supplier backed by a new empty
     * lazy value where the memoized value is obtained by invoking the provided
     * {@code suppler} at most once}
     * <p>
     * The returned memoized {@code Supplier} is equivalent to the following supplier:
     * {@snippet lang = java:
     * Lazy<V> lazy = Lazy.of();
     * Supplier<V> memoized = () -> lazy.computeIfUnbound(supplier);
     *}
     * except it promises the provided {@code supplier} is invoked at most once once
     * even though the returned memoized Supplier is invoked simultaneously
     * by several threads. The method will block, if a computation is already in progress.
     *
     * @param supplier   to be used for computing a value
     * @param <V>        the type of the value to memoize
     * @see Lazy#computeIfUnbound(Supplier)
     */
    static <V> Supplier<V> asSupplier(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return LazyImpl.asMemoized(supplier);
    }

}
