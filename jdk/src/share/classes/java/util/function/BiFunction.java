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
 * Apply a function to the input arguments, yielding an appropriate result. This
 * is the two-arity specialization of {@link Function}. A function may
 * variously provide a mapping between types, object instances or keys and
 * values or any other form of transformation upon the input.
 *
 * @param <T> the type of the first argument to the {@code apply} operation
 * @param <U> the type of the second argument to the {@code apply} operation
 * @param <R> the type of results returned by the {@code apply} operation
 *
 * @see Function
 * @since 1.8
 */
@FunctionalInterface
public interface BiFunction<T, U, R> {

    /**
     * Compute the result of applying the function to the input arguments
     *
     * @param t an input object
     * @param u an input object
     * @return the function result
     */
    R apply(T t, U u);

    /**
     * Returns a new function which applies this function followed by the
     * provided function.  If either function throws an exception, it is relayed
     * to the caller.
     *
     * @param <V> Type of output objects to the combined function. May be the
     * same type as {@code <T>}, {@code <U>} or {@code <R>}
     * @param after An additional function to be applied after this function is
     * applied
     * @return A function which performs this function followed by the provided
     * function
     * @throws NullPointerException if after is null
     */
    default <V> BiFunction<T, U, V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t, U u) -> after.apply(apply(t, u));
    }
}
