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

import jdk.incubator.json.impl.Utils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The interface that represents a JSON value. A {@code JsonValue} represents
 * a syntactic element within a JSON text. The {@code JsonValue} subtypes
 * correspond to the JSON types, while {@code JsonValue} itself provides a uniform
 * interface for navigation, conversion, and generation.
 *
 * <p>{@code JsonValue} does not define any identity or value semantics.
 * Code that requires equality, hashing, or comparisons should use a
 * {@linkplain jdk.incubator.json/jdk.incubator.json##conversion conversion}
 * method to obtain a Java value upon which such operations are performed.
 *
 * <p>Instances of {@code JsonValue} are immutable and thread safe. See the
 * {@linkplain jdk.incubator.json/jdk.incubator.json package specification}
 * for an overview of parsing, accessing, converting, and generating JSON text.
 *
 * @since 28
 */
public sealed interface JsonValue permits JsonString, JsonNumber, JsonObject, JsonArray, JsonBoolean, JsonNull {

    /**
     * {@return a JSON syntax conformant String representation of this {@code JsonValue}}
     *
     * The returned string represents the same JSON value as this object and
     * does not contain insignificant whitespace or line separators. The returned
     * String is not a canonical representation of the JSON value. If this {@code JsonValue}
     * was obtained via one of the parsing methods on the {@link Json} class, the
     * returned String is not necessarily an exact lexical match of the JSON text that
     * was parsed. Subinterfaces may specify stronger preservation behavior for their
     * corresponding JSON type.
     * <p>
     * For a String representation suitable for display, use
     * {@link Json#toDisplayString(JsonValue, String)}.
     *
     * @see Json#toDisplayString(JsonValue, String)
     */
    String toString();

    // Conversion methods all throw exceptions by default in JsonValue.
    // Implementors of the sub-interfaces are expected to provide reasonable implementations.

    /**
     * {@return the {@code boolean} value represented by this {@code JsonValue} if
     * it is an instance of {@link JsonBoolean}; otherwise, throws a
     * {@code JsonValueException}}
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonBoolean} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance of {@code JsonBoolean}.
     */
    default boolean asBoolean() {
        throw Utils.composeTypeError(this, JsonBoolean.class);
    }

    /**
     * {@return an {@code int} if this {@code JsonValue} is an instance of {@link JsonNumber}
     * that can be converted exactly; otherwise, throws a {@code JsonValueException}}
     *
     * This {@code JsonValue} must be a JSON number that represents
     * a whole number and that is within the range
     * {@link Integer#MIN_VALUE} to {@link Integer#MAX_VALUE}, inclusive. This is true
     * even if the JSON number contains an exponent or a fractional part consisting of
     * all zeroes. For example, the JSON numbers "123.0" and "1.23e2" both
     * produce an {@code int} value of {@code 123}. A {@code JsonValueException}
     * is thrown when the numeric value cannot be represented as an {@code int};
     * for example, the JSON number "5.5".
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonNumber} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance
     *      of {@code JsonNumber} or is not representable as an {@code int}.
     */
    default int asInt() {
        throw Utils.composeTypeError(this, JsonNumber.class);
    }

    /**
     * {@return a {@code long} if this {@code JsonValue} is an instance of {@link JsonNumber}
     * that can be converted exactly; otherwise, throws a {@code JsonValueException}}
     *
     * This {@code JsonValue} must be a JSON number that represents
     * a whole number and that is within the range {@link Long#MIN_VALUE} to
     * {@link Long#MAX_VALUE}, inclusive. This is true even if the JSON number contains an
     * exponent or a fractional part consisting of all zeroes. For example,
     * the JSON numbers "123.0" and "1.23e2" both produce a {@code long} value of
     * {@code 123}. A {@code JsonValueException} is thrown when the numeric value
     * cannot be represented as a {@code long}; for example, the JSON number "5.5".
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonNumber} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance
     *      of {@code JsonNumber} or is not representable as a {@code long}.
     */
    default long asLong() {
        throw Utils.composeTypeError(this, JsonNumber.class);
    }

    /**
     * {@return a {@code double} if this {@code JsonValue} is an instance of {@link JsonNumber}
     * that can be converted, as if by {@link Double#parseDouble Double.parseDouble}, to a finite
     * {@code double} value; otherwise, throws a {@code JsonValueException}}
     *
     * @apiNote Callers of this method should be aware of the potential loss in precision or
     * magnitude when a {@code JsonNumber} is converted to a {@code double}. A JSON number
     * may be rounded to the nearest representable {@code double} value, and a JSON number
     * with more than about 15 decimal digits may lose precision. A JSON number with a
     * magnitude larger than about 1.8&nbsp;&times;&nbsp;10<sup>308</sup> cannot be
     * represented as a finite {@code double},
     * and attempting to convert such a number will result in {@code JsonValueException}.
     * (This differs from {@link Double#parseDouble Double.parseDouble}, which will return
     * {@link Double#POSITIVE_INFINITY} or {@link Double#NEGATIVE_INFINITY} for such cases.)
     * This method will never return {@link Double#NaN}. However, this method will
     * properly convert and return negative zero ({@code -0.0}). To handle numbers of almost
     * arbitrary precision and magnitude, consider converting to {@link java.math.BigDecimal
     * BigDecimal} using {@code new BigDecimal(jsonNumber.toString())}. Note that
     * {@code BigDecimal} cannot represent negative zero.
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonNumber} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance
     *      of {@code JsonNumber} or is not representable as a finite {@code double}.
     */
    default double asDouble() {
        throw Utils.composeTypeError(this, JsonNumber.class);
    }

    /**
     * {@return the {@code String} value represented by this {@code JsonValue} if
     * it is an instance of {@link JsonString}; otherwise, throws a
     * {@code JsonValueException}}
     * If this {@code JsonString} was created by parsing a JSON text, any
     * escaped characters in the original JSON text are converted to their
     * unescaped form.
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonString} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance of {@code JsonString}.
     */
    default String asString() {
        throw Utils.composeTypeError(this, JsonString.class);
    }

    /**
     * {@return an unmodifiable list of the {@code JsonValue}s if this
     * {@code JsonValue} is an instance of {@link JsonArray}; otherwise, throws a
     * {@code JsonValueException}}
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonArray} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance of {@code JsonArray}.
     */
    default List<JsonValue> asList() {
        throw Utils.composeTypeError(this, JsonArray.class);
    }

    /**
     * {@return an unmodifiable map of {@code String} to {@code JsonValue} if this
     * {@code JsonValue} is an instance of {@link JsonObject}; otherwise, throws a
     * {@code JsonValueException}}
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonObject} are expected to
     * provide an implementation of this method.
     * @implNote
     * The JDK platform implementation of {@code JsonObject} preserves the
     * encounter order of members. When a {@code JsonObject} is created by
     * parsing, this corresponds to the order of members in the source JSON
     * text. When created via the {@link JsonObject#of(Map)} factory method, the order
     * follows the encounter order of the provided map.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance of {@code JsonObject}.
     */
    default Map<String, JsonValue> asMap() {
        throw Utils.composeTypeError(this, JsonObject.class);
    }

    // Access methods are able to provide a suitable default implementation directly
    // in JsonValue, and as such are not specified to be implemented by sub-interfaces.
    // However, relevant sub-interfaces will override them to explicitly have them
    // declared in their Javadoc as well as make any needed specification alterations.

    /**
     * {@return the {@code JsonValue} associated with the given member name if this
     * {@code JsonValue} is an instance of {@link JsonObject}; otherwise, throws a
     * {@code JsonValueException}}
     *
     * @implSpec
     * The default implementation obtains a {@code JsonValue} which is the result
     * of invoking {@link #asMap()}{@code .get(name)}. If {@code name} is absent,
     * {@code JsonValueException} is thrown.
     *
     * @param name the member name
     * @throws NullPointerException if the member name is {@code null}
     * @throws JsonValueException if this {@code JsonValue} is not an instance of a {@code JsonObject} or
     * there is no association with the member name
     */
    default JsonValue get(String name) {
        Objects.requireNonNull(name);
        return switch (asMap().get(name)) {
            case JsonValue jv -> jv;
            case null -> throw Utils.composeError(this,
                    "JsonObject member \"%s\" does not exist.".formatted(name));
        };
    }

    /**
     * {@return an {@code Optional} containing the value of a given member of
     * this {@link JsonObject}, or an empty {@code Optional} if the member is
     * absent; throws {@code JsonValueException} if this {@code JsonValue} is
     * not a {@code JsonObject}}
     *
     * @implSpec
     * The default implementation obtains an {@code Optional<JsonValue>} by invoking {@link
     * #asMap()}{@code .get(name)}, which is then passed to {@link Optional#ofNullable}.
     *
     * @param name the member name
     * @throws NullPointerException if the member name is {@code null}
     * @throws JsonValueException if this {@code JsonValue} is not an instance of a {@code JsonObject}
     */
    default Optional<JsonValue> tryGet(String name) {
        Objects.requireNonNull(name);
        return Optional.ofNullable(asMap().get(name));
    }

    /**
     * {@return the {@code JsonValue} associated with the given index if this
     * {@code JsonValue} is an instance of {@link JsonArray}; otherwise, throws a
     * {@code JsonValueException}}
     *
     * @implSpec
     * The default implementation obtains a {@code JsonValue} which is the result
     * of invoking {@link #asList()}{@code .get(index)}. If {@code index} is
     * out of bounds, {@code JsonValueException} is thrown.
     *
     * @param index the index of the array
     * @throws JsonValueException if this {@code JsonValue} is not an instance of a {@code JsonArray}
     * or the given index is out of bounds
     */
    default JsonValue get(int index) {
        List<JsonValue> elements = asList();
        try {
            return elements.get(index);
        } catch (IndexOutOfBoundsException _) {
            throw Utils.composeError(this, String.format(Locale.ROOT,
                "JsonArray index %d out of bounds for length %d.",
                index, elements.size()));
        }
    }

    /**
     * {@return an {@code Optional} containing this {@code JsonValue} if it
     * is not an instance of {@code JsonNull}, otherwise an empty {@code Optional}}
     *
     * @implSpec
     * The default implementation returns {@link Optional#empty} if this
     * {@code JsonValue} is an instance of {@code JsonNull}; otherwise
     * {@link Optional#of} given this {@code JsonValue}.
     */
    default Optional<JsonValue> tryValue() {
        return switch (this) {
            case JsonNull _ -> Optional.empty();
            case JsonValue _ -> Optional.of(this);
        };
    }
}
