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

import java.util.Arrays;
import java.util.Objects;

import jdk.incubator.json.impl.JsonParser;
import jdk.incubator.json.impl.Utils;

/**
 * This class provides static methods for parsing and generating JSON documents.
 *
 * <p>
 * {@link #parse(String)} and {@link #parse(char[])} produce a {@code JsonValue}
 * by parsing data adhering to the JSON syntax defined in RFC 8259.
 * {@snippet lang = java:
 * JsonValue root = Json.parse(jsonText);
 * }
 * Successful parsing guarantees there are no syntax errors. Unsuccessful
 * parsing throws a {@link JsonParseException}. Note that duplicate names in
 * a {@code JsonObject} also result in this exception.
 * <p>
 * {@link #toDisplayString(JsonValue, int)} produces a
 * JSON text representation of the given {@code JsonValue} suitable for display.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *      Object Notation (JSON) Data Interchange Format
 * @since 28
 */
public final class Json {

    /**
     * Parses and creates a {@code JsonValue} from the given JSON document.
     * If parsing succeeds, it guarantees that the input document conforms to
     * the JSON syntax. If the document contains any JSON object that has
     * duplicate names, a {@code JsonParseException} is thrown.
     * <p>
     * {@code JsonObject}s preserve the order of members in the input JSON
     * document.
     *
     * @implNote {@code JsonValue}s created by this method may produce their
     * underlying value representation lazily.
     *
     * @param in the input JSON document as {@code String}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format or a JSON object containing
     *      duplicate names is encountered.
     * @throws NullPointerException if {@code in} is {@code null}
     * @return the parsed {@code JsonValue}
     */
    public static JsonValue parse(String in) {
        Objects.requireNonNull(in);
        return new JsonParser(in.toCharArray()).parseRoot();
    }

    /**
     * Parses and creates a {@code JsonValue} from the given JSON document.
     * If parsing succeeds, it guarantees that the input document conforms to
     * the JSON syntax. If the document contains any JSON object that has
     * duplicate names, a {@code JsonParseException} is thrown. After parsing,
     * changes to the input array have no effect on the returned {@code JsonValue}.
     * <p>
     * {@code JsonObject}s preserve the order of their members declared in and parsed from
     * the JSON document.
     *
     * @implNote {@code JsonValue}s created by this method may produce their
     * underlying value representation lazily.
     *
     * @param in the input JSON document as {@code char[]}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format or a JSON object containing
     *      duplicate names is encountered.
     * @throws NullPointerException if {@code in} is {@code null}
     * @return the parsed {@code JsonValue}
     */
    public static JsonValue parse(char[] in) {
        Objects.requireNonNull(in);
        // Defensive copy on input. Ensure source is immutable.
        return new JsonParser(Arrays.copyOf(in, in.length)).parseRoot();
    }

    /**
     * {@return the String representation of the given {@code JsonValue} that conforms
     * to the JSON syntax} As opposed to the compact output returned by {@link
     * JsonValue#toString()}, this method returns a JSON string that is better
     * suited for display.
     *
     * @param value the {@code JsonValue} to create the display string from. Non-null.
     * @param indent the number of spaces used for the indentation. Zero or positive.
     * @throws NullPointerException if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code indent} is a negative number
     * @see JsonValue#toString()
     */
    public static String toDisplayString(JsonValue value, int indent) {
        Objects.requireNonNull(value);
        if (indent < 0) {
            throw new IllegalArgumentException("indent is negative");
        }
        var s = new StringBuilder();
        toDisplayString(value, s, 0, indent, false);
        return s.toString();
    }

    private static void toDisplayString(JsonValue jv, StringBuilder s, int col, int indent, boolean isField) {
        switch (jv) {
            case JsonObject jo -> toDisplayString(jo, s, col, indent, isField);
            case JsonArray ja -> toDisplayString(ja, s, col, indent, isField);
            default -> s.append(" ".repeat(isField ? 1 : col)).append(jv);
        }
    }

    private static void toDisplayString(JsonObject jo, StringBuilder s,
                                          int col, int indent, boolean isField) {
        var prefix = " ".repeat(col);
        if (isField) {
            s.append(" ");
        } else {
            s.append(prefix);
        }
        var map = jo.asMap();
        if (map.isEmpty()) {
            s.append("{}");
        } else {
            s.append("{\n");
            map.forEach((name, val) -> {
                s.append(prefix)
                        .append(" ".repeat(indent))
                        .append("\"")
                        .append(Utils.escape(name))
                        .append("\":");
                Json.toDisplayString(val, s, col + indent, indent, true);
                s.append(",\n");
            });
            s.setLength(s.length() - 2); // trim final comma
            s.append("\n").append(prefix).append("}");
        }
    }

    private static void toDisplayString(JsonArray ja, StringBuilder s,
                                          int col, int indent, boolean isField) {
        var prefix = " ".repeat(col);
        if (isField) {
            s.append(" ");
        } else {
            s.append(prefix);
        }
        var list = ja.asList();
        if (list.isEmpty()) {
            s.append("[]");
        } else {
            s.append("[\n");
            for (JsonValue v : list) {
                Json.toDisplayString(v, s, col + indent, indent, false);
                s.append(",\n");
            }
            s.setLength(s.length() - 2); // trim final comma/newline
            s.append("\n").append(prefix).append("]");
        }
    }

    // no instantiation is allowed for this class
    private Json() {}
}
