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
 * This API supports processing of JSON text in a simple manner. It is organized around the {@link
 * JsonValue} interface which represents a JSON value, and the {@link Json} class which provides
 * methods to parse and generate JSON text. Typical usage of this API involves first
 * {@linkplain ##parsing parsing} JSON text into a {@code JsonValue}, {@linkplain ##access navigating}
 * the parsed JSON value to the desired JSON value using <i>access</i> methods, and lastly
 * {@linkplain ##conversion converting} the desired value using a <i>conversion</i> method.
 * For example:
 * {@snippet lang = java:
 * List<JsonValue> providers = Json.parse(text)
 *          .get("providers") // access
 *          .asList(); // conversion
 * }
 *
 * <h2 id="parsing">Parsing JSON text</h2>
 * Parsing JSON text can be done using either {@link Json#parse(java.lang.String)} or {@link Json#parse(char[])}.
 * {@snippet lang = java:
 * JsonValue json = Json.parse(text);
 * }
 * A successful parse indicates that the JSON text adheres to the JSON grammar.
 * Unsuccessful parsing throws a {@link JsonParseException}, which provides a detail message that includes
 * error details, a path to the root of the JSON text, and its location within the text.
 * The parsing APIs provided do not accept JSON text that contains JSON objects with duplicate member names.
 * <p>
 * The result of a successful parse is a {@code JsonValue}. The {@code JsonValue} interface has six
 * sub-interfaces: {@link JsonString}, {@link JsonNumber}, {@link JsonBoolean}, {@link JsonNull},
 * {@link JsonObject}, and {@link JsonArray}. These sub-interfaces correspond to each value in the
 * JSON syntax. This type hierarchy allows you to use pattern matching to determine the subtype
 * of a {@code JsonValue}. {@code JsonValue} is immutable and thread safe.
 *
 * <h2 id="access">Navigating JSON text</h2>
 * Once you have retrieved a {@code JsonValue} from parsing, use the access methods to navigate
 * to the desired JSON value. {@link JsonValue#get(String)} is provided for JSON object and {@link
 * JsonValue#get(int)} for JSON array.
 * Given the JSON text:
 * {@snippet lang=java:
 * JsonValue json = Json.parse("""
 *     { "providers": [ "SUN", "SunRsaSign", "SunEC" ], "version": 1 }
 *     """);
 * }
 * the JSON string {@code "SUN"} can be accessed as follows:
 * {@snippet lang=java:
 * JsonValue firstProvider = json.get("providers").get(0);
 * }
 * If an access method is invoked on an incompatible JSON type, for example,
 * calling {@code get(String)} on a JSON array, a {@link JsonValueException}
 * is thrown.
 *
 * <h3>Handling optional members</h3>
 * A member of a JSON object can be optional. In this scenario, use the access method
 * {@link JsonValue#tryGet(String)} which returns an {@code Optional} of {@code JsonValue}.
 * For example:
 * {@snippet lang=java:
 * json.tryGet("providers")
 *     .ifPresent(IO::println);
 * }
 * This example only prints the value if the member named "providers" exists.
 *
 * <h3>Handling null values</h3>
 * Sometimes, JSON null is used to signify absence of a member.
 * In this scenario, use the access method {@link JsonValue#tryValue()} which returns an
 * {@code Optional} of {@code JsonValue}. For example:
 * {@snippet lang=java:
 * json.get("providers")
 *     .tryValue()
 *     .ifPresent(IO::println);
 * }
 * This example only prints the value if the member named "providers" is not a JSON
 * null.
 *
 * <h3>Handling variance in type or structure</h3>
 * If the type for a JSON value is variable, it can be handled as follows:
 * {@snippet lang = java:
 * String firstProvider = switch (json.get("providers")) {
 *     case JsonString js -> js.asString(); // handle the value as JSON string
 *     case JsonArray ja -> ja.get(0).asString(); // handle the value as JSON array
 *     default -> throw new JsonValueException("unexpected type");
 * }
 * }
 * While the code above throws an exception if the type is neither {@code JsonString} or
 * {@code JsonArray}, there are times when you may prefer a fallback value instead.
 * For example:
 * {@snippet lang = java:
 * String firstProvider = Optional.of(json)
 *     .filter(j -> j instanceof JsonObject)
 *     .flatMap(j -> j.tryGet("providers"))
 *     .filter(j -> j instanceof JsonString)
 *     .map(JsonValue::asString)
 *     .orElse("None");
 * }
 * This code ensures that if the root JSON value is not an object,
 * the member "providers" does not exist, or if the value of "providers" is not a JSON String,
 * then the "None" fallback value is used over throwing an exception.
 *
 * <h2 id=conversion>Converting JSON values to Java values</h2>
 * Once you have accessed your desired {@code JsonValue}, use the conversion methods to produce
 * a corresponding Java value. Each conversion method corresponds to a JSON type:
 * <ul>
 *     <li>{@link JsonValue#asString() asString()} converts a {@code JsonString} instance into a Java
 *     {@code String} with RFC 8259 JSON escape sequences translated to their
 *     corresponding characters.</li>
 *     <li>{@link JsonValue#asInt() asInt()} converts a {@code JsonNumber} instance to a Java
 *     {@code int} if its numeric value can be represented exactly.</li>
 *     <li>{@link JsonValue#asLong() asLong()} converts a {@code JsonNumber} instance to a Java
 *     {@code long} if its numeric value can be represented exactly.</li>
 *     <li>{@link JsonValue#asDouble() asDouble()} converts a {@code JsonNumber} instance to a Java
 *     {@code double} if its numeric value can be rounded to a finite Java {@code double}.</li>
 *     <li>{@link JsonValue#asBoolean() asBoolean()} converts a {@code JsonBoolean} instance to a Java
 *     {@code boolean} value of {@code true} or {@code false}.</li>
 *     <li>{@link JsonValue#asMap() asMap()} converts a {@code JsonObject} instance into an
 *     unmodifiable Java {@code Map}. If the JSON object contains no members, an
 *     empty {@code Map} is returned.</li>
 *     <li>{@link JsonValue#asList() asList()} converts a {@code JsonArray} instance into an
 *     unmodifiable Java {@code List}. If the JSON array contains no elements,
 *     an empty {@code List} is returned.</li>
 * </ul>
 * For example:
 * {@snippet lang=java:
 * String sun = firstProvider.asString();
 * }
 * The code above retrieves the Java String "SUN" from the JSON value {@code firstProvider}.
 * If an incorrect conversion method is used, which does not correspond to the matching
 * JSON type, for example {@code firstProvider.asBoolean()}, a {@code JsonValueException} is thrown.
 * <p>
 * These conversion methods always return a value when the {@code JsonValue} is
 * of the correct JSON type. The exceptions are {@code asInt()}, {@code asLong()},
 * and {@code asDouble()}; they may throw a {@code JsonValueException} even
 * when the {@code JsonValue} is a JSON number, for example if it is outside
 * their supported ranges.
 *
 * <h2 id="generation">Generating JSON text</h2>
 * Generating JSON text is performed with either {@link
 * JsonValue#toString()} or {@link Json#toDisplayString(JsonValue, String)}.
 * These methods produce String representations of a {@code JsonValue} that adhere
 * to the JSON grammar defined in RFC 8259.
 * {@code JsonValue.toString()} produces compact JSON text which does not
 * include JSON insignificant whitespaces, preferable for network transmission
 * or storage. For example:
 * {@snippet lang=json:
 * {"providers":["SUN","SunRsaSign","SunEC"],"version":1}
 * }
 * {@code Json.toDisplayString(JsonValue, String)} produces pretty-printed
 * JSON text which is easier to read, preferable for debugging or logging.
 * For example:
 * {@snippet lang=json:
 * {
 *   "providers": [
 *     "SUN",
 *     "SunRsaSign",
 *     "SunEC"
 *   ],
 *   "version": 1
 * }
 * }
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *      Object Notation (JSON) Data Interchange Format
 * @since 28
 */
package jdk.incubator.json;
