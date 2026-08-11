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

import java.util.Objects;

import jdk.incubator.json.impl.JsonStringImpl;
import jdk.incubator.json.impl.Utils;

/**
 * The interface that represents a JSON string.
 * <p>
 * A {@code JsonString} can be produced by a {@link Json#parse(String)}.
 * Within a valid JSON string, any character may be escaped using either a
 * two-character escape sequence (if applicable) or one or two Unicode escape
 * sequences. A supplementary character is represented by two Unicode escape
 * sequences corresponding to its surrogate pair.
 * Quotation Mark (U+0022), Backslash (Reverse Solidus, U+005C), and the control
 * characters (U+0000 through U+001F) must be escaped.
 * <p> Alternatively, {@link #of(String)} can be used to obtain a {@code JsonString}
 * directly from a {@code String}. The {@code String} values of {@code JsonString}
 * instances produced by the following expressions are all equivalent:
 * {@snippet lang = "java":
 *     Json.parse("\"foo\\t\"").asString();
 *     Json.parse("\"foo\\u0009\"").asString();
 *     JsonString.of("foo\t").asString();
 *}
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259#section-7 RFC 8259:
 *      The JavaScript Object Notation (JSON) Data Interchange Format - Strings
 * @since 28
 */
public non-sealed interface JsonString extends JsonValue {

    /**
     * {@return the {@code JsonString} created from the given
     * {@code String}}
     *
     * @param src the given source {@code String}. Non-null.
     * @throws NullPointerException if {@code src} is {@code null}
     */
    static JsonString of(String src) {
        var escaped = '"' + Utils.escape(Objects.requireNonNull(src)) + '"';
        return new JsonStringImpl(escaped.toCharArray(), 0, escaped.length(),
                escaped.length() != src.length() + 2);
    }

    /**
     * {@return the JSON string represented by this {@code JsonString}}
     * If this {@code JsonString} was created by parsing a JSON document, it
     * preserves the original text representation of the corresponding JSON
     * string. Otherwise, the source {@code String} passed to the factory method
     * {@link #of(String)} is used to generate the JSON string, with special
     * characters properly escaped.
     *
     * @see #asString()
     */
    @Override
    String toString();

    /**
     * {@return the {@code String} value represented by this {@code JsonString}}
     * If this {@code JsonString} was created by parsing a JSON document, any
     * escaped characters in the original JSON document are converted to their
     * unescaped form.
     *
     * @see #toString()
     */
    @Override
    String asString();
}
