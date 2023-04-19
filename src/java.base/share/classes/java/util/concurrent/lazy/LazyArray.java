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
import jdk.internal.util.concurrent.lazy.array.StandardLazyArray;

import java.util.NoSuchElementException;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A lazy references with a pre-set supplier...
 *
 * @param <V> The type of the value to be recorded
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface LazyArray<V>
        extends BaseLazyArray<V>, IntFunction<V>
        permits StandardLazyArray {

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the <em>pre-set {@linkplain Lazy#ofArray(int, IntFunction) mapper}</em>.
     * If no pre-set {@linkplain Lazy#ofArray(int, IntFunction) mapper} exists,
     * throws an IllegalStateException exception.
     * <p>
     * If the pre-set mapper itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyArray<V> lazy = Lazy.ofArray(64, Value::new);
     *    // ...
     *    V value = lazy.apply(42);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param index to the slot to be used
     * @return the value (pre-existing or newly computed)
     * @throws ArrayIndexOutOfBoundsException if the provided {@code index} is {@code < 0}
     *                                        or {@code index >= length()}
     * @throws IllegalStateException          if a value was not already present and no
     *                                        pre-set mapper was specified.
     * @throws NoSuchElementException         if a maper has previously thrown an exception for the
     *                                        provided {@code index}.
     */
    @Override
    public V apply(int index);

    /**
     * Forces computation of all {@link java.util.concurrent.lazy.Lazy.State#EMPTY} slots in
     * slot order.
     * <p>
     * If the pre-set mapper throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. This means, subsequent slots
     * are not computed.
     *
     */
    public void force();

    /**
     * A builder that can be used to configure a lazy array.
     *
     * @param <V> the type of the value.
     */
    public interface Builder<V>
            extends BaseLazyArray.Builder<V, LazyArray<V>, LazyArray.Builder<V>> {

        /**
         * {@return a builder that will use the provided {@code earliestEvaluation} when
         * eventially {@linkplain #build() building} a LazyArray}.
         * <p>
         * Any supplier configured with this builder must be referentially transparent
         * and thus must have no side-effect in order to allow transparent time-shifting of
         * evaluation.
         * <p>
         * No guarantees are made with respect to the latest time of evaluation and
         * consequently, the value might always be evaliate {@linkplain Lazy.Evaluation#AT_USE at use}.
         *
         * @param earliestEvaluation to use.
         */
        Builder<V> withEarliestEvaluation(Lazy.Evaluation earliestEvaluation);

    }
}
