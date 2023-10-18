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
import java.util.Comparator;

/**
 * Represents an operation upon two operands of the same type, producing a result
 * of the same type as the operands.  This is a specialization of
 * {@link BiFunction} for the case where the operands and the result are all of
 * the same type.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #apply(Object, Object)}.
 *
 * @param <T> the type of the operands and result of the operator
 *
 * @see BiFunction
 * @see UnaryOperator
 * @since 1.8
 */
@FunctionalInterface
public interface BinaryOperator<T> extends BiFunction<T,T,T> {
    /**
     * Returns a {@link BinaryOperator} which returns the lesser of two elements
     * according to the specified {@code Comparator}.
     *
     * @param <T> the type of the input arguments of the comparator
     * @param comparator a {@code Comparator} for comparing the two values
     * @return a {@code BinaryOperator} which returns the lesser of its operands,
     *         according to the supplied {@code Comparator}
     * @throws NullPointerException if the argument is null
     */
    public static <T> BinaryOperator<T> minBy(Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator);
        return (a, b) -> comparator.compare(a, b) <= 0 ? a : b;
    }

    /**
     * Returns a {@link BinaryOperator} which returns the greater of two elements
     * according to the specified {@code Comparator}.
     *
     * @param <T> the type of the input arguments of the comparator
     * @param comparator a {@code Comparator} for comparing the two values
     * @return a {@code BinaryOperator} which returns the greater of its operands,
     *         according to the supplied {@code Comparator}
     * @throws NullPointerException if the argument is null
     */
    public static <T> BinaryOperator<T> maxBy(Comparator<? super T> comparator) {
        Objects.requireNonNull(comparator);
        return (a, b) -> comparator.compare(a, b) >= 0 ? a : b;
    }

    /**
     * {@return a representation of the provided {@code source} construct
     * in the form of a {@code BinaryOperator}}
     * <p>
     * This method is particularly useful in cases where there is; an ambiguity
     * in a lambda or method reference, when inferring a local-variable type or
     * when using composition or fluent coding as shown in these examples:
     * {@snippet :
     * // Inferring and resolving ambiguity
     * var biFunction = BiFunction.of(Integer::sum);        // BiFunction<Integer, Integer, Integer>
     * var unaryOperator = BinaryOperator.of(Integer::sum); // BinaryOperator<Integer>
     *
     * // Fluent composition
     * var composed = BinaryOperator.of(Integer::sum)     // BinaryOperator<Integer>
     *                    .andThen(Integer::toHexString); // BiFunction<Integer, Integer, String>
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
     * var f3 = Function.of(String::toLowerCase); // ERROR
     * }
     *
     * @param source to convert
     * @param <T> the type of the operands and result of the operator
     * @throws NullPointerException if source is null
     */
    static <T> BinaryOperator<T> of(BinaryOperator<T> source) {
        return Objects.requireNonNull(source);
    }
}
