/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.json;

import jdk.incubator.json.impl.JsonNumberImpl;

/**
 * The interface that represents JSON number, an arbitrary-precision
 * number represented in base 10 using decimal digits.
 * <p>
 * A {@code JsonNumber} can be produced by {@link Json#parse(String)}.
 * When a JSON number is parsed, a {@code JsonNumber} object is created
 * as long as the input number text adheres to the JSON number
 * <a href="https://datatracker.ietf.org/doc/html/rfc8259#section-6">
 * syntax</a>.
 * <p> Alternatively, {@link #of(int)}, {@link #of(long)}, {@link #of(double)},
 * or {@link #of(String)} can be used to obtain a {@code JsonNumber}.
 * The value of the {@code JsonNumber} can be retrieved as an {@code int} with
 * {@link #asInt()}, as a {@code long} with {@link #asLong()}, or as a
 * {@code double} with {@link #asDouble()}. {@link #toString()} can be used to
 * return the string representation of the {@code JsonNumber}.
 *
 * @apiNote
 * To avoid precision loss when converting {@code JsonNumber}s to Java types, or when
 * converting {@code JsonNumber}s outside the range of {@code long} or {@code double},
 * use {@link #toString()} to create arbitrary-precision Java objects, for
 * example,
 * {@snippet lang="java" :
 * new BigDecimal(jsonNumber.toString())
 * // or if an integral number is preferred
 * new BigInteger(jsonNumber.toString())
 * // for cases with an exponent or zero fractional part
 * new BigDecimal(jsonNumber.toString()).toBigIntegerExact()
 * }
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259#section-6 RFC 8259:
 *      The JavaScript Object Notation (JSON) Data Interchange Format - Numbers
 * @since 28
 */
public non-sealed interface JsonNumber extends JsonValue {

    /**
     * {@inheritDoc}
     *
     * @throws JsonValueException if this {@code JsonNumber} is not representable
     *      as an {@code int}.
     */
    @Override
    int asInt();

    /**
     * {@inheritDoc}
     *
     * @throws JsonValueException if this {@code JsonNumber} is not representable
     *      as a {@code long}.
     */
    @Override
    long asLong();

    /**
     * {@inheritDoc}
     *
     * @apiNote {@inheritDoc}
     *
     * @throws JsonValueException if this {@code JsonNumber} is not representable
     *      as a finite {@code double}.
     */
    @Override
    double asDouble();

    /**
     * Creates a {@code JsonNumber} from the given {@code double} value.
     * The string representation of the {@code JsonNumber} created is produced by applying
     * {@link Double#toString(double)} on {@code num}.
     *
     * @param num the given {@code double} value.
     * @return a {@code JsonNumber} created from the {@code double} value
     * @throws IllegalArgumentException if the given {@code double} value
     * is not a finite floating-point value ({@link Double#NaN NaN},
     * {@link Double#POSITIVE_INFINITY positive infinity}, or
     * {@link Double#NEGATIVE_INFINITY negative infinity}).
     */
    static JsonNumber of(double num) {
        if (!Double.isFinite(num)) {
            throw new IllegalArgumentException("Not a valid JSON number");
        }
        var str = Double.toString(num);
        return new JsonNumberImpl(str.toCharArray(), true, 0, str.length(), str.indexOf('.'), str.indexOf('E'));
    }

    /**
     * Creates a {@code JsonNumber} from the given {@code int} value.
     * The string representation of the {@code JsonNumber} created is produced by applying
     * {@link Integer#toString(int)} on {@code num}.
     *
     * @param num the given {@code int} value.
     * @return a {@code JsonNumber} created from the {@code int} value
     */
    static JsonNumber of(int num) {
        var str = Integer.toString(num);
        return new JsonNumberImpl(str.toCharArray(), true, 0, str.length(), -1, -1);
    }

    /**
     * Creates a {@code JsonNumber} from the given {@code long} value.
     * The string representation of the {@code JsonNumber} created is produced by applying
     * {@link Long#toString(long)} on {@code num}.
     *
     * @param num the given {@code long} value.
     * @return a {@code JsonNumber} created from the {@code long} value
     */
    static JsonNumber of(long num) {
        var str = Long.toString(num);
        return new JsonNumberImpl(str.toCharArray(), true, 0, str.length(), -1, -1);
    }

    /**
     * Creates a {@code JsonNumber} from the given {@code String} value.
     * The string representation of the {@code JsonNumber} created is equivalent to
     * {@code num} with any leading or trailing JSON insignificant whitespaces removed.
     *
     * @param num the given {@code String} value.
     * @throws IllegalArgumentException if {@code num} is not a valid string
     *      representation of a {@code JsonNumber}.
     * @throws NullPointerException if {@code num} is {@code null}
     * @return a {@code JsonNumber} created from the {@code String} value
     */
    static JsonNumber of(String num) {
        try {
            if (Json.parse(num) instanceof JsonNumberImpl jn) {
                return jn.toFactoryValue();
            }
        } catch (JsonParseException _) {}
        throw new IllegalArgumentException("Not a JSON number");
    }

    /**
     * {@return the string representation of this {@code JsonNumber}}
     *
     * If this {@code JsonNumber} is created by parsing a JSON number in a JSON text,
     * it preserves the string representation in the JSON text, regardless of its
     * precision or range. For example, a JSON number like
     * "3.141592653589793238462643383279" in the JSON text will be
     * returned exactly as it appears.
     * If this {@code JsonNumber} is created via one of the factory methods,
     * such as {@link JsonNumber#of(double)}, then the string representation is
     * specified by the factory method.
     */
    @Override
    String toString();
}
