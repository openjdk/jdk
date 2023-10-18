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
 * Represents an operation that accepts two input arguments and returns no
 * result.  This is the two-arity specialization of {@link Consumer}.
 * Unlike most other functional interfaces, {@code BiConsumer} is expected
 * to operate via side-effects.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #accept(Object, Object)}.
 *
 * @param <T> the type of the first argument to the operation
 * @param <U> the type of the second argument to the operation
 *
 * @see Consumer
 * @since 1.8
 */
@FunctionalInterface
public interface BiConsumer<T, U> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     */
    void accept(T t, U u);

    /**
     * Returns a composed {@code BiConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code BiConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default BiConsumer<T, U> andThen(BiConsumer<? super T, ? super U> after) {
        Objects.requireNonNull(after);

        return (l, r) -> {
            accept(l, r);
            after.accept(l, r);
        };
    }

    /**
     * {@return a representation of the provided {@code source} construct
     * in the form of a {@code BiConsumer}}
     * <p>
     * This method is particularly useful in cases where there is; an ambiguity
     * in a lambda or method reference, when inferring a local-variable type or
     * when using composition or fluent coding as shown in these examples:
     * {@snippet :
     * void toConsole(long id, String message) {
     *      System.out.format("%d = %s%n", id, message);
     * }
     *
     * void toLogger(long id, String message) {
     *      LOGGER.info(String.format("%d = %s", id, message));
     * }
     *
     * // Inferring
     * var con = BiConsumer.of(this::toConsole); // BiConsumer<Long, String>
     *
     *  // Fluent composition
     * var composed = BiConsumer.of(this::toConsole)
     *                    .andThen(this::toLogger);  // BiConsumer<Long, String>
     *
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
     * @param <T> the type of the first argument to the operation
     * @param <U> the type of the second argument to the operation
     * @throws NullPointerException if source is null
     */
    @SuppressWarnings("unchecked")
    static <T, U> BiConsumer<T, U> of(BiConsumer<? super T, ? super U> source) {
        return (BiConsumer<T, U>) Objects.requireNonNull(source);
    }

}
