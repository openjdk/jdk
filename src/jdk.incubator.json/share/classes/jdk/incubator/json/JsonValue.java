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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The interface that represents a JSON value. {@code JsonValue} is a wrapper
 * around a syntactic element within a JSON document. The {@code JsonValue} subtypes
 * correspond to the JSON types, while {@code JsonValue} itself provides a uniform
 * interface for navigation, conversion, and generation.
 * <p>
 * A root {@code JsonValue} can be produced by parsing a JSON document with
 * {@link Json#parse(String)}. Use the {@link ##access access} methods to navigate
 * to the desired {@code JsonValue}. From there, use the {@link ##conversion conversion}
 * methods to extract a corresponding Java value. Code that relies on equality
 * or hashing should utilize the results of a <i>conversion</i> method instead of
 * the {@code JsonValue} itself.
 * <p>
 * JSON text can be produced using the {@link ##generation generation} methods.
 *
 * <h2 id="access">Navigating JSON documents</h2>
 * Use the access methods to navigate to the desired JSON value. {@link
 * #get(String)} is provided for JSON object and {@link #get(int)} for JSON array.
 * Given the JSON document:
 * {@snippet lang=java:
 * JsonValue json = Json.parse("""
 *     { "foo": ["bar", true, 42], "baz": null }
 *     """);
 * }
 * the JSON String "bar" can be accessed as follows:
 * {@snippet lang=java:
 * JsonValue foo0 = json.get("foo").get(0);
 * }
 * If an access method is invoked on an incompatible JSON type (for example,
 * calling {@code get(String)} on a JSON array), a {@code JsonValueException}
 * is thrown.
 *
 * <h3>Missing Object Members</h3>
 * A member of a JSON object can be optional. In this scenario, use the access method
 * {@link #tryGet(String)} which returns an {@code Optional} of {@code JsonValue}.
 * For example:
 * {@snippet lang=java:
 * json.tryGet("foo")
 *     .ifPresent(IO::println)
 * }
 * This example only prints the value if the member named "foo" exists.
 *
 * <h3>Handling of null</h3>
 * JSON null can be used to signify absence.
 * In this scenario, use the access method {@link #tryValue()} which returns an
 * {@code Optional} of {@code JsonValue}. For example:
 * {@snippet lang=java:
 * json.get("baz")
 *     .tryValue()
 *     .ifPresent(IO::println)
 * }
 * This example only prints the value if the member named "baz" is not a JSON
 * null.
 *
 * <h2 id=conversion>Converting JSON values to Java values</h2>
 * Use the conversion methods to produce a Java value from the {@code
 * JsonValue}. Each conversion method corresponds to a JSON type:
 * <ul>
 *     <li>{@code asString()} converts a {@code JsonString} instance into a Java
 *     {@code String} with RFC 8259 JSON escape sequences translated to their
 *     corresponding characters.</li>
 *     <li>{@code asInt()} converts a {@code JsonNumber} instance to a Java
 *     {@code int} if its numeric value can be represented exactly.</li>
 *     <li>{@code asLong()} converts a {@code JsonNumber} instance to a Java
 *     {@code long} if its numeric value can be represented exactly.</li>
 *     <li>{@code asDouble()} converts a {@code JsonNumber} instance to a Java
 *     {@code double} if its numeric value can be rounded to a finite Java {@code double}.</li>
 *     <li>{@code asBoolean()} converts a {@code JsonBoolean} instance to a Java
 *     {@code boolean} value of {@code true} or {@code false}.</li>
 *     <li>{@code asMap()} converts a {@code JsonObject} instance into an
 *     unmodifiable Java {@code Map}. If the JSON object contains no members, an
 *     empty {@code Map} is returned.</li>
 *     <li>{@code asList()} converts a {@code JsonArray} instance into an
 *     unmodifiable Java {@code List}. If the JSON array contains no elements,
 *     an empty {@code List} is returned.</li>
 * </ul>
 * For example,
 * {@snippet lang=java:
 * String bar = foo0.asString();
 * }
 * The code above retrieves the Java String "bar" from the JSON value {@code foo0}.
 * If an incorrect conversion method is used, which does not correspond to the matching
 * JSON type, for example {@code foo0.asBoolean()}, a {@code JsonValueException} is thrown.
 * <p>
 * These conversion methods always return a value when the {@code JsonValue} is
 * of the correct JSON type. The exceptions are {@code asInt()}, {@code asLong()},
 * and {@code asDouble()}; they may throw a {@code JsonValueException} even
 * when the {@code JsonValue} is a JSON number, for example if it is outside
 * their supported ranges.
 *
 * <h2>Handling variance</h2>
 * If the type for a JSON value is variable, it can be handled as follows:
 * {@snippet lang = java:
 * switch (json.get("foo")) {
 *     case JsonString js -> js.asString(); // handle the value as JSON string
 *     case JsonArray ja -> ja.get(0).asString(); // handle the value as JSON array
 *     default -> throw new JsonValueException("unexpected type");
 * }
 *}
 * There may be times when a JSON document can vary, but providing a fallback
 * value is preferable to throwing an exception. For example:
 * {@snippet lang = java:
 * Optional.of(json)
 *     .filter(j -> j instanceof JsonObject)
 *     .flatMap(j -> j.tryGet("foo"))
 *     .filter(j -> j instanceof JsonString)
 *     .map(JsonValue::asString)
 *     .orElse("bar");
 *}
 * The code above ensures that if the root JSON document is not an object,
 * the member "foo" does not exist, or if "foo" is not a String, that the "bar"
 * fallback value is used over throwing an exception.
 *
 * <h2 id="generation">Generating JSON documents</h2>
 * {@code JsonValue} overrides {@link Object#toString()} to generate RFC 8259
 * compliant JSON text in a compact representation with JSON insignificant white
 * spaces eliminated.
 * For generating JSON documents suitable for display, use
 * the generation method {@link Json#toDisplayString(JsonValue, String)} instead.
 * <p>
 * Instances of {@code JsonValue} are immutable and thread safe.
 *
 * @since 28
 */
public sealed interface JsonValue permits JsonString, JsonNumber, JsonObject, JsonArray, JsonBoolean, JsonNull {

    /**
     * {@return the String representation of this {@code JsonValue} that conforms
     * to the JSON syntax}
     *
     * The returned string represents the same JSON value as this object and
     * does not contain insignificant whitespace or line separators. It is not
     * required to preserve the exact lexical representation of the input JSON
     * document or to produce a canonical representation. Subinterfaces may
     * specify stronger preservation behavior for their corresponding JSON type.
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
     * {@code JsonValueException}}.
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonBoolean} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance of {@code JsonBoolean}.
     */
    default boolean asBoolean() {
        throw Utils.composeTypeError(this, "JsonBoolean");
    }

    /**
     * {@return an {@code int} if this {@code JsonValue} is an instance of {@link JsonNumber}
     * and it can be converted from its string representation; otherwise, throws a
     * {@code JsonValueException}} That is, it can be
     * expressed exactly as a whole number and is within the range of
     * {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE}. This occurs,
     * even if the string contains an exponent or a fractional part consisting of
     * only zero digits. For example, both the JSON number "123.0" and "1.23e2"
     * produce an {@code int} value of "123". A {@code JsonValueException}
     * is thrown when the numeric value cannot be represented as an {@code int};
     * for example, the value "5.5".
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
        throw Utils.composeTypeError(this, "JsonNumber");
    }

    /**
     * {@return a {@code long} if this {@code JsonValue} is an instance of {@link JsonNumber} and
     * it can be converted from its string representation; otherwise, throws a
     * {@code JsonValueException}} That is, it can be expressed exactly
     * as a whole number and is within the range of {@link Long#MIN_VALUE} and
     * {@link Long#MAX_VALUE}. This occurs, even if the string contains an
     * exponent or a fractional part consisting of only zero digits. For example,
     * both the JSON number "123.0" and "1.23e2" produce a {@code long} value of
     * "123". A {@code JsonValueException} is thrown when the numeric value
     * cannot be represented as a {@code long}; for example, the value "5.5".
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
        throw Utils.composeTypeError(this, "JsonNumber");
    }

    /**
     * {@return a finite {@code double} if this {@code JsonValue} is an instance of
     * {@link JsonNumber} and it can be converted from its string representation using
     * {@link Double#parseDouble(String)}; otherwise, throws a {@code JsonValueException}}
     * If the converted {@code double} value is {@link Double#POSITIVE_INFINITY}
     * or {@link Double#NEGATIVE_INFINITY}, a {@code JsonValueException} is thrown.
     *
     * @apiNote Callers of this method should be aware of the potential loss in
     * precision when the string representation of the {@code JsonNumber} is converted
     * to a {@code double}.
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonNumber} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance
     *      of {@code JsonNumber} or is not representable as a {@code double}.
     */
    default double asDouble() {
        throw Utils.composeTypeError(this, "JsonNumber");
    }

    /**
     * {@return the {@code String} value represented by this {@code JsonValue} if
     * it is an instance of {@link JsonString}; otherwise, throws a
     * {@code JsonValueException}}.
     * If this {@code JsonString} was created by parsing a JSON document, any
     * escaped characters in the original JSON document are converted to their
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
        throw Utils.composeTypeError(this, "JsonString");
    }

    /**
     * {@return an unmodifiable list of the {@code JsonValue}s if this
     * {@code JsonValue} is an instance of {@link JsonArray}; otherwise, throws a
     * {@code JsonValueException}}.
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonArray} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance of {@code JsonArray}.
     */
    default List<JsonValue> asList() {
        throw Utils.composeTypeError(this, "JsonArray");
    }

    /**
     * {@return an unmodifiable map of {@code String} to {@code JsonValue} if this
     * {@code JsonValue} is an instance of {@link JsonObject}; otherwise, throws a
     * {@code JsonValueException}}.
     *
     * @implSpec
     * The default implementation provided by {@code JsonValue} throws {@code
     * JsonValueException}. As such, implementors of {@code JsonObject} are expected to
     * provide an implementation of this method.
     *
     * @throws JsonValueException if this {@code JsonValue} is not an instance of {@code JsonObject}.
     */
    default Map<String, JsonValue> asMap() {
        throw Utils.composeTypeError(this, "JsonObject");
    }

    // Access methods are able to provide a suitable default implementation directly
    // in JsonValue, and as such are not specified to be implemented by sub-interfaces.
    // However, relevant sub-interfaces will override them to explicitly have them
    // declared in their Javadoc as well as make any specification changes.
    // tryValue specification would be unchanged by all sub-interfaces, and as
    // a result is left un-overridden.

    /**
     * {@return the {@code JsonValue} associated with the given member name if this
     * {@code JsonValue} is an instance of {@link JsonObject}} Otherwise, throws a
     * {@code JsonValueException}.
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
     * {@return an {@code Optional} containing the {@code JsonValue} associated
     * with the given member name if this {@code JsonValue} is an instance of
     * {@link JsonObject}} Otherwise, throws a {@code JsonValueException}.
     * If there is no association with the given member name, an empty
     * {@code Optional} is returned.
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
     * {@code JsonValue} is an instance of {@link JsonArray}} Otherwise, throws a
     * {@code JsonValueException}.
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
            throw Utils.composeError(this,
                    "JsonArray index %d out of bounds for length %d."
                            .formatted(index, elements.size()));
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
