/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package java.util;

import java.io.Serializable;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * This class consists of {@code static} utility methods for comparators. Mostly
 * factory method that returns a {@link Comparator}.
 *
 * <p> Unless otherwise noted, passing a {@code null} argument to a method in
 * this class will cause a {@link NullPointerException} to be thrown.
 *
 * @see Comparator
 * @since 1.8
 */
public class Comparators {
    private Comparators() {
        throw new AssertionError("no instances");
    }

    /**
     * Compares {@link Comparable} objects in natural order.
     *
     * @see Comparable
     */
    private enum NaturalOrderComparator implements Comparator<Comparable<Object>> {
        INSTANCE;

        @Override
        public int compare(Comparable<Object> c1, Comparable<Object> c2) {
            return c1.compareTo(c2);
        }
    }

    /**
     * Returns a comparator that imposes the reverse of the <em>natural
     * ordering</em>.
     *
     * <p>The returned comparator is serializable.
     *
     * @param <T> {@link Comparable} type
     *
     * @return A comparator that imposes the reverse of the <i>natural
     *         ordering</i> on a collection of objects that implement
     *         the {@link Comparable} interface.
     * @see Comparable
     */
    public static <T extends Comparable<? super T>> Comparator<T> reverseOrder() {
        return Collections.reverseOrder();
    }

    /**
     * Returns a comparator that imposes the reverse ordering of the specified
     * {@link Comparator}.
     *
     * <p>The returned comparator is serializable (assuming the specified
     * comparator is also serializable).
     *
     * @param <T> the element type to be compared
     * @param cmp a comparator whose ordering is to be reversed by the returned
     *            comparator
     * @return A comparator that imposes the reverse ordering of the
     *         specified comparator.
     */
    public static <T> Comparator<T> reverseOrder(Comparator<T> cmp) {
        Objects.requireNonNull(cmp);
        return Collections.reverseOrder(cmp);
    }

    /**
     * Gets a comparator compares {@link Comparable} type in natural order.
     *
     * @param <T> {@link Comparable} type
     */
    public static <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
        return (Comparator<T>) NaturalOrderComparator.INSTANCE;
    }

    /**
     * Gets a comparator compares {@link Map.Entry} in natural order on key.
     *
     * @param <K> {@link Comparable} key type
     * @param <V> value type
     */
    public static <K extends Comparable<? super K>, V> Comparator<Map.Entry<K,V>> naturalOrderKeys() {
        return (Comparator<Map.Entry<K, V>> & Serializable)
            (c1, c2) -> c1.getKey().compareTo(c2.getKey());
    }

    /**
     * Gets a comparator compares {@link Map.Entry} in natural order on value.
     *
     * @param <K> key type
     * @param <V> {@link Comparable} value type
     */
    public static <K, V extends Comparable<? super V>> Comparator<Map.Entry<K,V>> naturalOrderValues() {
        return (Comparator<Map.Entry<K, V>> & Serializable)
            (c1, c2) -> c1.getValue().compareTo(c2.getValue());
    }

    /**
     * Gets a comparator compares {@link Map.Entry} by key using the given
     * {@link Comparator}.
     *
     * <p>The returned comparator is serializable assuming the specified
     * comparators are also serializable.
     *
     * @param <K> key type
     * @param <V> value type
     * @param cmp the key {@link Comparator}
     */
    public static <K, V> Comparator<Map.Entry<K, V>> byKey(Comparator<? super K> cmp) {
        Objects.requireNonNull(cmp);
        return (Comparator<Map.Entry<K, V>> & Serializable)
            (c1, c2) -> cmp.compare(c1.getKey(), c2.getKey());
    }

    /**
     * Gets a comparator compares {@link Map.Entry} by value using the given
     * {@link Comparator}.
     *
     * @param <K> key type
     * @param <V> value type
     * @param cmp the value {@link Comparator}
     */
    public static <K, V> Comparator<Map.Entry<K, V>> byValue(Comparator<? super V> cmp) {
        Objects.requireNonNull(cmp);
        return (Comparator<Map.Entry<K, V>> & Serializable)
            (c1, c2) -> cmp.compare(c1.getValue(), c2.getValue());
    }

    /**
     * Accepts a function that extracts a {@link java.lang.Comparable
     * Comparable} sort key from a type {@code T}, and returns a {@code
     * Comparator<T>} that compares by that sort key.  For example, if a class
     * {@code Person} has a {@code String}-valued getter {@code getLastName},
     * then {@code comparing(Person::getLastName)} would return a {@code
     * Comparator<Person>} that compares {@code Person} objects by their last
     * name.
     *
     * @param <T> the original element type
     * @param <U> the {@link Comparable} type for comparison
     * @param keyExtractor the function used to extract the {@link Comparable} sort key
     */
    public static <T, U extends Comparable<? super U>> Comparator<T> comparing(Function<? super T, ? extends U> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable)
            (c1, c2) -> keyExtractor.apply(c1).compareTo(keyExtractor.apply(c2));
    }

    /**
     * Accepts a function that extracts an {@code int} value from a type {@code
     * T}, and returns a {@code Comparator<T>} that compares by that value.
     *
     * <p>The returned comparator is serializable assuming the specified
     * function is also serializable.
     *
     * @see #comparing(Function)
     * @param <T> the original element type
     * @param keyExtractor the function used to extract the integer value
     */
    public static <T> Comparator<T> comparing(ToIntFunction<? super T> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable)
            (c1, c2) -> Integer.compare(keyExtractor.applyAsInt(c1), keyExtractor.applyAsInt(c2));
    }

    /**
     * Accepts a function that extracts a {@code long} value from a type {@code
     * T}, and returns a {@code Comparator<T>} that compares by that value.
     *
     * <p>The returned comparator is serializable assuming the specified
     * function is also serializable.
     *
     * @see #comparing(Function)
     * @param <T> the original element type
     * @param keyExtractor the function used to extract the long value
     */
    public static <T> Comparator<T> comparing(ToLongFunction<? super T> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable)
            (c1, c2) -> Long.compare(keyExtractor.applyAsLong(c1), keyExtractor.applyAsLong(c2));
    }

    /**
     * Accepts a function that extracts a {@code double} value from a type
     * {@code T}, and returns a {@code Comparator<T>} that compares by that
     * value.
     *
     * <p>The returned comparator is serializable assuming the specified
     * function is also serializable.
     *
     * @see #comparing(Function)
     * @param <T> the original element type
     * @param keyExtractor the function used to extract the double value
     */
    public static<T> Comparator<T> comparing(ToDoubleFunction<? super T> keyExtractor) {
        Objects.requireNonNull(keyExtractor);
        return (Comparator<T> & Serializable)
            (c1, c2) -> Double.compare(keyExtractor.applyAsDouble(c1), keyExtractor.applyAsDouble(c2));
    }

    /**
     * Constructs a lexicographic order from two {@link Comparator}s.  For
     * example, if you have comparators {@code byLastName} and {@code
     * byFirstName}, each of type {@code Comparator<Person>}, then {@code
     * compose(byLastName, byFirstName)} creates a {@code Comparator<Person>}
     * which sorts by last name, and for equal last names sorts by first name.
     *
     * <p>The returned comparator is serializable assuming the specified
     * comparators are also serializable.
     *
     * @param <T> the element type to be compared
     * @param first the first comparator
     * @param second the secondary comparator used when equals on the first
     */
    public static<T> Comparator<T> compose(Comparator<? super T> first, Comparator<? super T> second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        return (Comparator<T> & Serializable) (c1, c2) -> {
            int res = first.compare(c1, c2);
            return (res != 0) ? res : second.compare(c1, c2);
        };
    }

    /**
     * Constructs a {@link BinaryOperator} which returns the lesser of two elements
     * according to the specified {@code Comparator}
     *
     * @param comparator A {@code Comparator} for comparing the two values
     * @param <T> the type of the elements to be compared
     * @return a {@code BinaryOperator} which returns the lesser of its operands,
     * according to the supplied {@code Comparator}
     */
    public static<T> BinaryOperator<T> lesserOf(Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator);
        return (a, b) -> comparator.compare(a, b) <= 0 ? a : b;
    }

    /**
     * Constructs a {@link BinaryOperator} which returns the greater of two elements
     * according to the specified {@code Comparator}
     *
     * @param comparator A {@code Comparator} for comparing the two values
     * @param <T> the type of the elements to be compared
     * @return a {@code BinaryOperator} which returns the greater of its operands,
     * according to the supplied {@code Comparator}
     */
    public static<T> BinaryOperator<T> greaterOf(Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator);
        return (a, b) -> comparator.compare(a, b) >= 0 ? a : b;
    }
}
