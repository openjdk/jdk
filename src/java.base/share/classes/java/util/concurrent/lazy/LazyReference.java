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
import jdk.internal.util.concurrent.lazy.PreComputedLazyReference;
import jdk.internal.util.concurrent.lazy.StandardLazyReference;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * A lazy reference with a pre-set supplier which will be invoken at most once,
 * for example when {@link LazyReference#get() get()} is invoked.
 *
 * @param <V> The type of the value to be recorded
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface LazyReference<V>
        extends BaseLazyReference<V>, Supplier<V>
        permits PreComputedLazyReference, StandardLazyReference {

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>pre-set {@linkplain Lazy#of(Supplier)} supplier}</em>.
     * <p>
     * If the pre-set supplier itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = Lazy.of(Value::new);
     *    // ...
     *    V value = lazy.get();
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the pre-set supplier returns {@code null}.
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set supplier was specified.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    @SuppressWarnings("unchecked")
    @Override
    public V get();

    /**
     * A builder that can be used to configure a LazyReference.
     *
     * @param <V> the type of the value.
     */
    public interface Builder<V>
            extends BaseLazyReference.Builder<V, LazyReference<V>, LazyReference.Builder<V>> {

        /**
         * {@return a builder that will use the provided {@code earliestEvaluation} when
         * eventially {@linkplain #build() building} a LazyReference}.
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
