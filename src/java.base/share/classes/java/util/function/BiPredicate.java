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
 * Represents a predicate (boolean-valued function) of two arguments.  This is
 * the two-arity specialization of {@link Predicate}.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #test(Object, Object)}.
 *
 * @param <T> the type of the first argument to the predicate
 * @param <U> the type of the second argument the predicate
 *
 * @see Predicate
 * @since 1.8
 */
@FunctionalInterface
public interface BiPredicate<T, U> {

    /**
     * Evaluates this predicate on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     * @return {@code true} if the input arguments match the predicate,
     * otherwise {@code false}
     */
    boolean test(T t, U u);

    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * AND of this predicate and another.  When evaluating the composed
     * predicate, if this predicate is {@code false}, then the {@code other}
     * predicate is not evaluated.
     *
     * <p>Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the
     * {@code other} predicate will not be evaluated.
     *
     * @param other a predicate that will be logically-ANDed with this
     *              predicate
     * @return a composed predicate that represents the short-circuiting logical
     * AND of this predicate and the {@code other} predicate
     * @throws NullPointerException if other is null
     */
    default BiPredicate<T, U> and(BiPredicate<? super T, ? super U> other) {
        Objects.requireNonNull(other);
        return (T t, U u) -> test(t, u) && other.test(t, u);
    }

    /**
     * Returns a predicate that represents the logical negation of this
     * predicate.
     *
     * @return a predicate that represents the logical negation of this
     * predicate
     */
    default BiPredicate<T, U> negate() {
        return (T t, U u) -> !test(t, u);
    }

    /**
     * Returns a composed predicate that represents a short-circuiting logical
     * OR of this predicate and another.  When evaluating the composed
     * predicate, if this predicate is {@code true}, then the {@code other}
     * predicate is not evaluated.
     *
     * <p>Any exceptions thrown during evaluation of either predicate are relayed
     * to the caller; if evaluation of this predicate throws an exception, the
     * {@code other} predicate will not be evaluated.
     *
     * @param other a predicate that will be logically-ORed with this
     *              predicate
     * @return a composed predicate that represents the short-circuiting logical
     * OR of this predicate and the {@code other} predicate
     * @throws NullPointerException if other is null
     */
    default BiPredicate<T, U> or(BiPredicate<? super T, ? super U> other) {
        Objects.requireNonNull(other);
        return (T t, U u) -> test(t, u) || other.test(t, u);
    }

    /**
     * {@return a representation of the provided {@code source} construct
     * in the form of a {@code BiPredicate}}
     * <p>
     * This method is particularly useful in cases where there is; an ambiguity
     * in a lambda or method reference, when inferring a local-variable type or
     * when using composition or fluent coding as shown in these examples:
     * {@snippet :
     * // Inferring and resolving ambiguity
     * var biFunction = BiFunction.of(String::equals);     // BiFunction<String, Object, Boolean>
     * var biPredicate = BiPredicate.of(String::equals);   // BiPredicate<Integer, Object>
     *
     * // Fluent composition
     * var composed = BiPredicate.of(String::endsWith)     // BiPredicate<String, String>
     *                    .or(String::startsWith);         // BiPredicate<String, String>
     *}
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
     * var f3 = Function.of(String::toLowerCase); // ERROR
     * }
     *
     * @param source to convert
     * @param <T> the type of the first argument to the predicate
     * @param <U> the type of the second argument the predicate
     * @throws NullPointerException if source is null
     */
    @SuppressWarnings("unchecked")
    static <T, U> BiPredicate<T, U> of(BiPredicate<? super T, ? super U> source) {
        return (BiPredicate<T, U>) Objects.requireNonNull(source);
    }

}
