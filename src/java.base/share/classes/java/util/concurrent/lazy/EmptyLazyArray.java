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
import jdk.internal.util.concurrent.lazy.array.TranslatedEmptyLazyArray;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * An empty lazy references with no pre-set supplier...
 *
 * @param <V> The type of the value to be recorded
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface EmptyLazyArray<V>
        extends BaseLazyArray<V>
        permits StandardEmptyLazyArray, TranslatedEmptyLazyArray {

    /**
     * Returns the present value at the provided {@code index} or, if no present value exists,
     * atomically attempts to compute the value using the provided {@code mappper}.
     *
     * <p>If the mapper returns {@code null}, an exception is thrown.  If the
     * provided {@code mapper} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    EmptyLazyArray<V> lazy = Lazy.ofEmptyArray(64);
     *    // ...
     *    V value = lazy.supplyIfAbsent(42, Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param index   to the slot to be used
     * @param mappper to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws ArrayIndexOutOfBoundsException if {@code index < 0} or {@code index >= length()}
     * @throws NullPointerException           if the provided {@code mappper} is {@code null} or if
     *                                        the provided {@code mapper} itself returns {@code null}.
     * @throws NoSuchElementException         if a maper has previously thrown an exception for the
     *                                        provided {@code index}.
     */
    public V computeIfEmpty(int index,
                            IntFunction<? extends V> mappper);

/*    *//**
     * A builder that can be used to configure a LazyReference.
     *
     * @param <V> the type of the value.
     *//*
    public interface Builder<V>
            extends BaseLazyReference.Builder<V, EmptyLazyArray<V>, EmptyLazyArray.Builder<V>> {

        *//**
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
         *//*
        Builder<V> withEarliestEvaluation(Lazy.Evaluation earliestEvaluation);

    }*/
}
