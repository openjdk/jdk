/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.concurrent.lazy.array.StandardEmptyLazyArray;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.lazy.Lazy.State;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Base interface for lazy reference arrays , which are ... // Todo: write more here
 *
 * @param <V> The type of values to be recorded
 *
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface BaseLazyArray<V> permits EmptyLazyArray, LazyArray {

    /**
     * {@return the length of the array}.
     */
    public int length();

    /**
     * {@return The {@link State } of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link State#PRESENT} or
     * {@link State#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         // perform action on the value
     *     }
     *}
     * @param index to retrieve the State from
     */
    public State state(int index);

    /**
     * {@return the excption thrown by the supplier invoked or
     * {@link Optional#empty()} if no exception was thrown}.
     *
     * @param index to retrieve the exception from
     */
    public Optional<Throwable> exception(int index);

    /**
     * {@return the value at the provided {@code index} if the value is {@link State#PRESENT}
     * or {@code defaultValue} if the value is {@link State#EMPTY} or {@link State#CONSTRUCTING}}.
     *
     * @param index        for which the value shall be obtained.
     * @param defaultValue to use if no value is present
     * @throws NoSuchElementException if a provider for the provided {@code index} has previously
     *                                thrown an exception.
     */
    V getOr(int index, V defaultValue);

    /**
     * Returns an unmodifiable view of the elements in this lazy array
     * where the empty elements will be replaced with {@code null}.
     * <p>
     * If a mapper has previously thrown an exception for an
     * accessed element at a certain index, accessing that index will result in
     * a NoSuchElementException being thrown.
     *
     * @return a view of the elements
     */
    public List<V> asList();

    /**
     * Returns an unmodifiable view of the elements in this lazy array
     * where the empty elements will be replaced with the provided {@code defaulValue}.
     * <p>
     * If a mapper has previously thrown an exception for an
     * accessed element at a certain index, accessing that index will result in
     * a NoSuchElementException being thrown.
     *
     * @param defaulValue to use for elements not yet created
     * @return a view of the elements
     */
    public List<V> asList(V defaulValue);

    /**
     * {@return A Stream with the lazy elements in this lazy array}.
     * <p>
     * Upon encountering a state at position {@code index} in the array, the following actions
     * will be taken:
     * <ul>
     *     <li><b>EMPTY</b>
     *     <p>An Optional.empty() element is selected.</p></li>
     *     <li><b>CONSTRUCTING</b>
     *     <p>An Optional.empty() element is selected.</p></li>
     *     <li><b>PRESENT</b>
     *     <p>An Optional.ofNullable(lazy.get(index)) element is selected.</p></li>
     *     <li><b>ERROR</b>
     *     <p>A NoSuchElementException is thrown.</p></li>
     * </ul>
     *
     */
    public Stream<Optional<V>> stream();

    /**
     * {@return A Stream with the lazy elements in this LazyReferenceArray}.
     * <p>
     * Upon encountering a state at position {@code index} in the array, the following actions
     * will be taken:
     * <ul>
     *     <li><b>EMPTY</b>
     *     <p>The provided {@code defaultValue} is selected.</p></li>
     *     <li><b>CONSTRUCTING</b>
     *     <p>The provided {@code defaultValue} is selected.</p></li>
     *     <li><b>PRESENT</b>
     *     <p>lazy.get(index)) is selected.</p></li>
     *     <li><b>ERROR</b>
     *     <p>A NoSuchElementException is thrown.</p></li>
     * </ul>
     *
     * @param defaultValue the default value to use for empty/contructing slots.
     */
    public Stream<V> stream(V defaultValue);

    /**
     * A builder that can be used to configure a lazy array.
     *
     * @param <V> the type of the value.
     * @param <L> the lazy reference array type to build
     * @param <B> the builder type
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY)
    public interface Builder<V, L extends BaseLazyArray<V>, B extends Builder<V, L, B>> {

        /**
         * {@return a builder that will use the provided eagerly computed {@code value} when
         * eventially {@linkplain #build() building} a lazy array reference}.
         *
         * @param index to retrieve the State from
         * @param value to use
         */
        B withValue(int index, V value);

        /**
         * {@return a new lazy reference array with the builder's configured setting}.
         */
        L build();
    }

}
