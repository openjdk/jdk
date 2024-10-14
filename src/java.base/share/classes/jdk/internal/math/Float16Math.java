/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.math;

import jdk.internal.vm.annotation.IntrinsicCandidate;

import static java.lang.Float.*;
import static java.lang.Float16.*;

/**
 * The class {@code Float16Math} constains intrinsic entry points corresponding
 * to scalar numeric operations defined in Float16 class.
 * @author
 * @since   24
 */
public final class Float16Math {

    private Float16Math() {
    }

    /**
     * Intrinsic entry point for {@code Float16.max} operation.
     * Accepts unwrapped 'short' parameters holding IEEE 754 binary16
     * enocoded value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the greater of {@code a} and {@code b}.
     * @see Float16#max
     */
    @IntrinsicCandidate
    public static short max(short a, short b) {
        return floatToFloat16(Math.max(float16ToFloat(a), float16ToFloat(b)));
    }

    /**
     * Intrinsic entry point for {@code Float16.min} operation.
     * Accepts unwrapped 'short' parameters holding IEEE 754 binary16
     * enocoded value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the smaller of {@code a} and {@code b}.
     * @see Float16#min
     */
    @IntrinsicCandidate
    public static short min(short a, short b) {
        return floatToFloat16(Math.min(float16ToFloat(a), float16ToFloat(b)));
    }

    /**
     * Intrinsic entry point for {@code Float16.add} operation.
     * Accepts unwrapped 'short' parameters holding IEEE 754 binary16
     * enocoded value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the sum of {@code a} and {@code b}.
     * @see Float16#add
     */
    @IntrinsicCandidate
    public static short add(short a, short b) {
        return floatToFloat16(float16ToFloat(a) + float16ToFloat(b));
    }

    /**
     * Intrinsic entry point for {@code Float16.subtract} operation.
     * Accepts unwrapped 'short' parameters holding IEEE 754 binary16
     * enocoded value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the difference of {@code a} and {@code b}.
     * @see Float16#subtract
     */
    @IntrinsicCandidate
    public static short subtract(short a, short b) {
        return floatToFloat16(float16ToFloat(a) - float16ToFloat(b));
    }

    /**
     * Intrinsic entry point for {@code Float16.multiply} operation.
     * Accepts unwrapped 'short' parameters holding IEEE 754 binary16
     * enocoded value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the product of {@code a} and {@code b}.
     * @see Float16#multiply
     */
    @IntrinsicCandidate
    public static short multiply(short a, short b) {
        return floatToFloat16(float16ToFloat(a) * float16ToFloat(b));
    }

    /**
     * Intrinsic entry point for {@code Float16.divide} operation.
     * Accepts unwrapped 'short' parameters holding IEEE 754 binary16
     * enocoded value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @return the quotient of {@code a} and {@code b}.
     * @see Float16#divide
     */
    @IntrinsicCandidate
    public static short divide(short a, short b) {
        return floatToFloat16(float16ToFloat(a) / float16ToFloat(b));
    }

    /**
     * Intrinsic entry point for {@code Float16.sqrt} operation.
     * Accepts unwrapped 'short' parameter holding IEEE 754 binary16
     * enocoded value.
     *
     * @param a the first operand.
     * @return square root of a.
     * @see Float16#sqrt
     */
    @IntrinsicCandidate
    public static short sqrt(short a) {
        return float16ToRawShortBits(valueOf(Math.sqrt(float16ToFloat(a))));
    }

    /**
     * Intrinsic entry point for {@code Float16.fma} operation.
     * Accepts unwrapped 'short' parameters holding IEEE 754 binary16
     * enocoded value.
     *
     * @param a the first operand.
     * @param b the second operand.
     * @param c the third operand.
     * @return (<i>a</i>&nbsp;&times;&nbsp;<i>b</i>&nbsp;+&nbsp;<i>c</i>)
     * computed, as if with unlimited range and precision, and rounded
     * once to the nearest {@code Float16} value
     * @see Float16#fma
     */
    @IntrinsicCandidate
    public static short fma(short a, short b, short c) {
        // product is numerically exact in float before the cast to
        // double; not necessary to widen to double before the
        // multiply.
        double product = (double)(float16ToFloat(a) * float16ToFloat(b));
        return float16ToRawShortBits(valueOf(product + float16ToFloat(c)));
    }
}
