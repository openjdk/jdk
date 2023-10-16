/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Represents an operation on a single operand that produces a result of the
 * same type as its operand.  This is a specialization of {@code Function} for
 * the case where the operand and result are of the same type.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #apply(Object)}.
 *
 * @param <T> the type of the operand and result of the operator
 *
 * @see Function
 * @since 1.8
 */
@FunctionalInterface
public interface UnaryOperator<T> extends Function<T, T> {

    /**
     * Returns a unary operator that always returns its input argument.
     *
     * @param <T> the type of the input and output of the operator
     * @return a unary operator that always returns its input argument
     */
    static <T> UnaryOperator<T> identity() {
        return t -> t;
    }

    /**
     * Returns a composed function that first applies this unary operator to
     * its input, and then applies the {@code after} unary function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param after the unary operator to apply after this unary operator is applied
     * @return a composed unary operator that first applies this function and then
     * applies the {@code after} unary operator
     * @throws NullPointerException if after is null
     *
     * @see Function#andThen(Function)
     */
    default UnaryOperator<T> andThenUnary(UnaryOperator<T> after) {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(apply(t));
    }

    /**
     * {@return a representation of the provided {@code uncaptured} lambda or method reference
     * in the form of a {@code UnaryOperator}}
     * <p>
     * This method is useful in cases where there is an ambiguity of a lambda or method reference
     * or when using composition and/or fluent coding as shown in this example:
     * {@snippet :
     * // Resolve ambiguity
     * var function = Function.of(String::stripTrailing); // Function<String, String>
     * var unaryOperator = UnaryOperator.of(String::stripTrailing); // UnaryOperator<String>
     *
     * // Fluent composition
     * var composed = UnaryOperator.of(String::stripTrailing)
     *                    .andThenUnary(String::stripIndent);  // UnaryOperator<String>
     * }
     *
     * @param uncaptured to capture
     * @param <T> the type of the operand and result of the operator
     */
    static <T> UnaryOperator<T> of(UnaryOperator<T> uncaptured) {
        return uncaptured;
    }

}
