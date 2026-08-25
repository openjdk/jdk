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

/**
 * Provides APIs for parsing JSON text, accessing JSON values in the text, and
 * generating JSON text. This package implements
 * <a href="https://datatracker.ietf.org/doc/html/rfc8259">RFC 8259: The JavaScript
 * Object Notation (JSON) Data Interchange Format</a>. The API is organized around
 * the {@link JsonValue} interface, which represents a JSON value, and the
 * {@link Json} class, which offers static methods for manipulating JSON texts.
 *
 * <h2>Parsing JSON text</h2>
 *
 * Parsing produces a {@code JsonValue} from JSON text and is done using either
 * {@link Json#parse(java.lang.String)} or {@link Json#parse(char[])}.
 * {@snippet lang = java:
 * JsonValue root = Json.parse(jsonText);
 * }
 * A successful parse indicates that the JSON text adheres to the JSON grammar and
 * contains no objects with duplicate member names. The parsing APIs provided
 * do not accept JSON text that contains JSON objects with duplicate member names.
 *
 * <h2 id="access">Navigating JSON text</h2>
 * Use the access methods to navigate to the desired JSON value. {@link
 * JsonValue#get(String)} is provided for JSON object and {@link JsonValue#get(int)} for JSON array.
 * Given the JSON text:
 * {@snippet lang=java:
 * JsonValue json = Json.parse("""
 *     { "foo": ["bar", true, 42], "baz": null }
 *     """);
 * }
 * the JSON string {@code "bar"} can be accessed as follows:
 * {@snippet lang=java:
 * JsonValue foo0 = json.get("foo").get(0);
 * }
 * If an access method is invoked on an incompatible JSON type (for example,
 * calling {@code get(String)} on a JSON array), a {@code JsonValueException}
 * is thrown.
 *
 * <h3>Missing Object Members</h3>
 * A member of a JSON object can be optional. In this scenario, use the access method
 * {@link JsonValue#tryGet(String)} which returns an {@code Optional} of {@code JsonValue}.
 * For example:
 * {@snippet lang=java:
 * json.tryGet("foo")
 *     .ifPresent(IO::println);
 * }
 * This example only prints the value if the member named "foo" exists.
 *
 * <h3>Handling of null</h3>
 * JSON null can be used to signify absence.
 * In this scenario, use the access method {@link JsonValue#tryValue()} which returns an
 * {@code Optional} of {@code JsonValue}. For example:
 * {@snippet lang=java:
 * json.get("baz")
 *     .tryValue()
 *     .ifPresent(IO::println);
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
 * }
 * There may be times when a JSON text can vary, but providing a fallback
 * value is preferable to throwing an exception. For example:
 * {@snippet lang = java:
 * java.util.Optional.of(json)
 *     .filter(j -> j instanceof JsonObject)
 *     .flatMap(j -> j.tryGet("foo"))
 *     .filter(j -> j instanceof JsonString)
 *     .map(JsonValue::asString)
 *     .orElse("bar");
 * }
 * The code above ensures that if the root JSON text is not an object,
 * the member "foo" does not exist, or if "foo" is not a String, that the "bar"
 * fallback value is used over throwing an exception.
 *
 * <h2 id="generation">Generating JSON text</h2>
 * Generating JSON text is performed with either {@link
 * JsonValue#toString()} or {@link Json#toDisplayString(JsonValue, String)}.
 * These methods produce String representations of a {@code JsonValue}.
 * The returned text adheres to the JSON grammar defined in RFC 8259.
 * {@code JsonValue.toString()} produces the compact representation which does not
 * include JSON insignificant whitespaces, preferable for network transmission
 * or storage. {@code Json.toDisplayString(JsonValue, String)} produces a text which
 * is human friendly, preferable for debugging or logging.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *      Object Notation (JSON) Data Interchange Format
 * @since 28
 */
package jdk.incubator.json;
