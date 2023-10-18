/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Represents a function that accepts two arguments and produces a result.
 * This is the two-arity specialization of {@link Function}.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #apply(Object, Object)}.
 *
 * @param <T> the type of the first argument to the function
 * @param <U> the type of the second argument to the function
 * @param <R> the type of the result of the function
 *
 * @see Function
 * @since 1.8
 */
@FunctionalInterface
public interface BiFunction<T, U, R> {

    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param u the second function argument
     * @return the function result
     */
    R apply(T t, U u);

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of output of the {@code after} function, and of the
     *           composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     */
    default <V> BiFunction<T, U, V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);
        return (T t, U u) -> after.apply(apply(t, u));
    }

    /**
     * {@return a representation of the provided {@code source} construct
     * in the form of a {@code BiFunction}}
     * <p>
     * This method is particularly useful in cases where there is; an ambiguity
     * in a lambda or method reference, when inferring a local-variable type or
     * when using composition or fluent coding as shown in these examples:
     * {@snippet :
     * // Inferring and resolving ambiguity
     * var function = BiFunction.of(String::endsWith);   // BiFunction<String, String, Boolean>
     * var predicate = BiPredicate.of(String::endsWith); // BiPredicate<String, String>
     *
     * // Fluent composition
     * var chained = BiFunction.of(String::repeat)     // BiFunction<String, Integer, String>
     *                   .andThen(String::length);     // Function<String, Integer>
     * }
     * <p>
     * Note: While this method is useful in conjunction with method references,
     * care should be taken as code may become incompatible if an overload is
     * later added to the method reference. Here is an example for Function
     * showing the general problem (String::toLower has several overloads):
     * {@snippet :
     * // Works because the type is explicitly declared
     * Function<String, String> f0 = String::toLowerCase;
     * // Works because the type is explicitly declared
     * Function<String, String> f1 = Function.of(String::toLowerCase);
     * // Works because an override is explicitly picked by the lambda
     * var f2 = Function.of((String s) -> s.toLowerCase());
     * // Does NOT work as toLowerCase cannot be resolved
     * // var f3 = Function.of(String::toLowerCase);
     * }
     *
     * @param source to convert
     * @param <T> the type of the first argument to the function
     * @param <U> the type of the second argument to the function
     * @param <R> the type of the result of the function
     * @throws NullPointerException if source is null
     */
    @SuppressWarnings("unchecked")
    static <T, U, R> BiFunction<T, U, R> of(BiFunction<? super T, ? super U, ? extends R> source) {
        return (BiFunction<T, U, R>) Objects.requireNonNull(source);
    }

}
