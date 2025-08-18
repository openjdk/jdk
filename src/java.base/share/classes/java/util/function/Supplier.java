/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.util.function;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.lang.stable.StableSupplier;

import java.util.Objects;
import java.util.Optional;
import java.lang.invoke.StableValue;

/**
 * Represents a supplier of results.
 *
 * <p>There is no requirement that a new or distinct result be returned each
 * time the supplier is invoked.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #get()}.
 *
 * @param <T> the type of results supplied by this supplier
 *
 * @since 1.8
 */
@FunctionalInterface
public interface Supplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T get();

    /**
     * {@return a new lazy, stable, and final supplier}
     * <p>
     * The returned {@linkplain Supplier supplier} is a caching supplier that records
     * the value of the provided {@code underlying} supplier upon being first accessed via
     * the returned supplier's {@linkplain Supplier#get() get()} method.
     * <p>
     * The provided {@code underlying} supplier is guaranteed to be successfully invoked
     * at most once even in a multi-threaded environment. Competing threads invoking the
     * returned supplier's {@linkplain Supplier#get() get()} method when a value is
     * already under computation will block until a value is computed or an exception is
     * thrown by the computing thread. The competing threads will then observe the newly
     * computed value (if any) and will then never execute the {@code underlying} supplier.
     * <p>
     * If the provided {@code underlying} supplier returns {@code null}, no cached value
     * is recorded and a new attempt will be made to compute a value is
     * made upon accessing returned supplier again. Hence, a lazy supplier
     * cannot contain {@code null} values. Clients that want to use nullable values
     * can wrap the value into an {@linkplain Optional} holder.
     * <p>
     * If the provided {@code underlying} supplier throws an exception, it is rethrown
     * to the initial caller and no contents is recorded.
     * <p>
     * If the provided {@code underlying} supplier recursively calls the returned
     * supplier, an {@linkplain IllegalStateException} will be thrown.
     * <p>
     * Finality confers certain performance optimization available to the VM.
     *
     * @param underlying supplier used to compute a cached value
     * @param <T>        the type of results supplied by the returned supplier
     *
     * @see StableValue
     * @since 26
     */
    @PreviewFeature(feature = PreviewFeature.Feature.STABLE_VALUES)
    static <T> Supplier<T> ofLazyFinal(Supplier<? extends T> underlying) {
        Objects.requireNonNull(underlying);
        return StableSupplier.of(underlying);
    }

}
