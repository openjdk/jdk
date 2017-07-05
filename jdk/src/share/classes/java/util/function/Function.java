/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

/**
 * Apply a function to the input argument, yielding an appropriate result.  A
 * function may variously provide a mapping between types, object instances or
 * keys and values or any other form of transformation upon the input.
 *
 * @param <T> the type of the input to the {@code apply} operation
 * @param <R> the type of the result of the {@code apply} operation
 *
 * @since 1.8
 */
@FunctionalInterface
public interface Function<T, R> {

    /**
     * Compute the result of applying the function to the input argument
     *
     * @param t the input object
     * @return the function result
     */
    R apply(T t);

    /**
     * Returns a new function which applies the provided function followed by
     * this function.  If either function throws an exception, it is relayed
     * to the caller.
     *
     * @param <V> type of input objects to the combined function. May be the
     * same type as {@code <T>} or {@code <R>}
     * @param before an additional function to be applied before this function
     * is applied
     * @return a function which performs the provided function followed by this
     * function
     * @throws NullPointerException if before is null
     */
    default <V> Function<V, R> compose(Function<? super V, ? extends T> before) {
        Objects.requireNonNull(before);
        return (V v) -> apply(before.apply(v));
    }

    /**
     * Returns a new function which applies this function followed by the
     * provided function.  If either function throws an exception, it is relayed
     * to the caller.
     *
     * @param <V> type of output objects to the combined function. May be the
     * same type as {@code <T>} or {@code <R>}
     * @param after an additional function to be applied after this function is
     * applied
     * @return a function which performs this function followed by the provided
     * function
     * @throws NullPointerException if after is null
     */
    default <V> Function<T, V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(apply(t));
    }

    /**
     * Returns a {@code Function} whose {@code apply} method returns its input.
     *
     * @param <T> the type of the input and output objects to the function
     * @return a {@code Function} whose {@code apply} method returns its input
     */
    static <T> Function<T, T> identity() {
        return t -> t;
    }
}
