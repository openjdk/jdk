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

import java.util.Objects;

/**
 * An operation on a {@code double} operand yielding a {@code double}
 * result. This is the primitive type specialization of {@link UnaryOperator}
 * for {@code double}.
 *
 * @see UnaryOperator
 * @since 1.8
 */
@FunctionalInterface
public interface DoubleUnaryOperator {

    /**
     * Returns the {@code double} result of the operation upon the
     * {@code double} operand.
     *
     * @param operand the operand value
     * @return the operation result value
     */
    double applyAsDouble(double operand);

    /**
     * Compose a new function which applies the provided function followed by
     * this function.  If either function throws an exception, it is relayed
     * to the caller.
     *
     * @param before An additional function to be applied before this function
     * is applied
     * @return A function which performs the provided function followed by this
     * function
     * @throws NullPointerException if before is null
     */
    default DoubleUnaryOperator compose(DoubleUnaryOperator before) {
        Objects.requireNonNull(before);
        return (double v) -> applyAsDouble(before.applyAsDouble(v));
    }

    /**
     * Compose a new function which applies this function followed by the
     * provided function.  If either function throws an exception, it is relayed
     * to the caller.
     *
     * @param after An additional function to be applied after this function is
     * applied
     * @return A function which performs this function followed by the provided
     * function followed
     * @throws NullPointerException if after is null
     */
    default DoubleUnaryOperator andThen(DoubleUnaryOperator after) {
        Objects.requireNonNull(after);
        return (double t) -> after.applyAsDouble(applyAsDouble(t));
    }

    /**
     * Returns a unary operator that provides its input value as the result.
     *
     * @return a unary operator that provides its input value as the result
     */
    static DoubleUnaryOperator identity() {
        return t -> t;
    }
}
