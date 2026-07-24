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

package jdk.incubator.json.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

/**
 * Parses a JSON Document char[] into a tree of JsonValues. JsonObject and JsonArray
 * nodes create their data structures which maintain the connection to children.
 * JsonNumber and JsonString contain only a start and end offset, which
 * are used to lazily procure their underlying value/string on demand.
 */
public final class JsonParser {

    // Access to the underlying JSON contents
    private final char[] doc;
    // Lazily initialized for member names with escape sequences
    private final LazyConstant<StringBuilder> sb = LazyConstant.of(this::initSb);
    // Current offset during parsing
    private int offset;
    // For exception message on failure
    private int line;
    private int lineStart;
    // True if the most recently parsed member name contains escape sequences
    private boolean escapedMemberName;

    public JsonParser(char[] doc) {
        this.doc = doc;
    }

    // Parses the lone JsonValue root
    public JsonValue parseRoot() {
        JsonValue root = parseValue("");
        if (hasInput()) {
            throw failure("Additional value(s) were found after the JSON Value", "");
        }
        return root;
    }

    /*
     * Parse any one of the JSON value types: object, array, number, string,
     * true, false, or null.
     *      JSON-text = ws value ws
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-3
     */
    private JsonValue parseValue(String path) {
        skipWhitespaces();
        if (!hasInput()) {
            throw failure("Expected a JSON Object, Array, String, Number, Boolean, or Null", path);
        }
        var val = switch (doc[offset]) {
            case '{' -> parseObject(path + "{");
            case '[' -> parseArray(path + "[");
            case '"' -> parseString(path);
            case 't' -> parseTrue(path);
            case 'f' -> parseFalse(path);
            case 'n' -> parseNull(path);
            // While JSON Number does not support leading '+', '.', or 'e'
            // we still accept, so that we can provide a better error message
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', 'e', '.'
                    -> parseNumber(path);
            default -> throw failure(UNEXPECTED_VAL, path);
        };
        skipWhitespaces();
        return val;
    }

    /*
     * The parsed JsonObject contains a map which holds all lazy member mappings.
     * No offsets are required as member values hold their own offsets.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-4
     */
    private JsonObject parseObject(String path) {
        var startO = offset++; // Walk past the '{'
        skipWhitespaces();
        // Check for empty case
        if (charEquals('}')) {
            return new JsonObjectImpl(Map.of(), startO, doc);
        }
        var members = new LinkedHashMap<String, JsonValue>();
        while (hasInput()) {
            var nameStart = offset;
            // Get the member name, which should be unescaped
            // Why not parse the name as a JsonString and then return its value()?
            // Would require 2 passes; we should build the String as we parse.
            var name = parseName(path);
            var nameOffset = offset;
            var nameLine = line;
            var nameLineStart = lineStart;
            var unescaped = escapedMemberName ?
                new String(doc, nameStart + 1, nameOffset - nameStart - 2) : name;

            // Move from name to ':'
            skipWhitespaces();
            if (!charEquals(':')) {
                throw failure(
                        "Expected a colon after the member name", path);
            }

            if (members.putIfAbsent(name, parseValue(path + unescaped)) != null) {
                throw failure(nameOffset, nameLine, nameLineStart,
                    "The duplicate member name: \"%s\" was already parsed".formatted(name), path);
            }

            // Ensure current char is either ',' or '}'
            if (charEquals('}')) {
                return new JsonObjectImpl(members, startO, doc);
            } else if (charEquals(',')) {
                skipWhitespaces();
            } else {
                // Neither ',' nor '}' so fail
                break;
            }
        }
        throw failure("JSON Object is not closed with a brace", path);
    }

    /*
     * Member name equality and storage in the map should be done with the
     * unescaped value.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-8.3
     */
    private String parseName(String path) {
        if (!charEquals('"')) {
            throw failure("Expecting a JSON Object member name", path);
        }
        var escape = false;
        boolean useBldr = false;
        var start = offset;

        for (; hasInput(); offset++) {
            var c = doc[offset];
            if (escape) {
                var escapeLength = 0;
                switch (c) {
                    // Allowed JSON escapes
                    case '"', '\\', '/' -> {}
                    case 'b' -> c = '\b';
                    case 'f' -> c = '\f';
                    case 'n' -> c = '\n';
                    case 'r' -> c = '\r';
                    case 't' -> c = '\t';
                    case 'u' -> {
                        c = codeUnit(path);
                        escapeLength = 4;
                    }
                    default -> throw failure(UNRECOGNIZED_ESCAPE_SEQUENCE.formatted(c), path);
                }
                if (!useBldr) {
                    // Append everything up to the first escape sequence
                    sb.get().append(doc, start, offset - escapeLength - 1 - start);
                    useBldr = true;
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
                continue;
            } else if (c == '\"') {
                offset++;
                escapedMemberName = useBldr;
                if (useBldr) {
                    var name = sb.get().toString();
                    sb.get().setLength(0);
                    return name;
                } else {
                    return new String(doc, start, offset - start - 1);
                }
            } else if (c < ' ') {
                throw failure(UNESCAPED_CONTROL_CODE, path);
            }
            if (useBldr) {
                sb.get().append(c);
            }
        }
        throw failure(UNCLOSED_STRING.formatted("JSON Object member name"), path);
    }

    /*
     * The parsed JsonArray contains a List which holds all lazy children
     * elements. No offsets are required as children values hold their own offsets.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-5
     */
    private JsonArray parseArray(String path) {
        var startO = offset++; // Walk past the '['
        skipWhitespaces();
        // Check for empty case
        if (charEquals(']')) {
            return new JsonArrayImpl(List.of(), startO, doc);
        }
        var list = new ArrayList<JsonValue>();
        while (hasInput()) {
            // Get the JsonValue
            list.add(parseValue(path + list.size()));
            // Ensure current char is either ']' or ','
            if (charEquals(']')) {
                return new JsonArrayImpl(list, startO, doc);
            } else if (!charEquals(',')) {
                break;
            }
        }
        throw failure("JSON Array is not closed with a bracket", path);
    }

    /*
     * The parsed JsonString will contain offsets correlating to the beginning
     * and ending quotation marks. All Unicode characters are allowed except the
     * following that require escaping: quotation mark, reverse solidus, and the
     * control characters (U+0000 through U+001F). Any character may be escaped
     * either through a Unicode escape sequence or two-char sequence.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-7
     */
    private JsonString parseString(String path) {
        int start = offset++; // Move past the starting quote
        var escape = false;
        boolean hasEscape = false;
        for (; hasInput(); offset++) {
            var c = doc[offset];
            if (escape) {
                switch (c) {
                    // Allowed JSON escapes
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {}
                    case 'u' -> codeUnit(path);
                    default -> throw failure(UNRECOGNIZED_ESCAPE_SEQUENCE.formatted(c), path);
                }
                escape = false;
            } else if (c == '\\') {
                hasEscape = true;
                escape = true;
            } else if (c == '\"') {
                return new JsonStringImpl(doc, start, ++offset, hasEscape);
            } else if (c < ' ') {
                throw failure(UNESCAPED_CONTROL_CODE, path);
            }
        }
        throw failure(UNCLOSED_STRING.formatted("JSON String"), path);
    }

    private JsonBooleanImpl parseTrue(String path) {
        var start = offset++;
        if (charEquals('r') && charEquals('u') && charEquals('e')) {
            return new JsonBooleanImpl(true, doc, start);
        }
        throw failure(UNEXPECTED_VAL, path);
    }

    private JsonBooleanImpl parseFalse(String path) {
        var start = offset++;
        if (charEquals('a') && charEquals('l') && charEquals('s')
                && charEquals('e')) {
            return new JsonBooleanImpl(false, doc, start);
        }
        throw failure(UNEXPECTED_VAL, path);
    }

    private JsonNullImpl parseNull(String path) {
        var start = offset++;
        if (charEquals('u') && charEquals('l') && charEquals('l')) {
            return new JsonNullImpl(doc, start);
        }
        throw failure(UNEXPECTED_VAL, path);
    }

    /*
     * The parsed JsonNumber contains offsets correlating to the first and last
     * allowed chars permitted in the JSON numeric grammar:
     *      number = [ minus ] int [ frac ] [ exp ]
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-6
     */
    private JsonNumberImpl parseNumber(String path) {
        int decOff = -1;
        int expOff = -1;
        boolean sawZero = false;
        boolean havePart = false;
        boolean sawSign = false;
        var start = offset;

        endloop:
        for (; hasInput(); offset++) {
            var c = doc[offset];
            switch (c) {
                case '-' -> {
                    if ((offset != start && expOff == -1) || havePart || sawSign) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted(c), path);
                    }
                    sawSign = true;
                }
                case '+' -> {
                    if (expOff == -1 || havePart || sawSign) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted(c), path);
                    }
                    sawSign = true;
                }
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (decOff == -1 && expOff == -1 && sawZero) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted('0'), path);
                    }
                    if (doc[offset] == '0' && !havePart) {
                        sawZero = true;
                    }
                    havePart = true;
                }
                case '.' -> {
                    if (decOff != -1) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted(c), path);
                    } else {
                        if (!havePart) {
                            throw failure(INVALID_POSITION_IN_NUMBER.formatted(c), path);
                        }
                        decOff = offset;
                        havePart = false;
                    }
                }
                case 'e', 'E' -> {
                    if (expOff != -1) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted(c), path);
                    } else {
                        if (!havePart) {
                            throw failure(INVALID_POSITION_IN_NUMBER.formatted(c), path);
                        }
                        expOff = offset;
                        havePart = false;
                        sawSign = false;
                    }
                }
                default -> {
                    // break the loop for white space or invalid characters
                    break endloop;
                }
            }
        }
        if (!havePart) {
            throw failure("Input expected after '[.|e|E]'", path);
        }
        return new JsonNumberImpl(doc, start, offset, decOff, expOff);
    }

    // Utility functions

    private StringBuilder initSb() {
        return new StringBuilder();
    }

    // Unescapes the Unicode escape sequence and produces a char
    private char codeUnit(String path) {
        char val = 0;
        int end = offset + 4;
        if (end >= doc.length) {
            throw failure("Invalid Unicode escape sequence. Expected four hex digits", path);
        }
        while (offset < end) {
            char c = doc[++offset];
            val <<= 4;
            val += (char) (
                    switch (c) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                        case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
                        case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
                        default -> throw failure(
                                "Invalid Unicode escape sequence. '%c' is not a hex digit".formatted(c), path);
                    });
        }
        return val;
    }

    // Returns true if the parser has not yet reached the end of the Document
    private boolean hasInput() {
        return offset < doc.length;
    }

    // Walk to the next non-white space char from the current offset
    private void skipWhitespaces() {
        while (hasInput()) {
            if (notWhitespace()) {
                break;
            }
            offset++;
        }
    }

    // see https://datatracker.ietf.org/doc/html/rfc8259#section-2
    private boolean notWhitespace() {
        return switch (doc[offset]) {
            case ' ', '\t','\r' -> false;
            case '\n' -> {
                // Increments the line and lineStart
                line++;
                lineStart = offset + 1;
                yield false;
            }
            default -> true;
        };
    }

    // Returns true if within bounds and if the char at the current parser offset
    // is equivalent to the input one. If so, offset is incremented.
    private boolean charEquals(char c) {
        if (hasInput() && c == doc[offset]) {
            offset++;
            return true;
        }
        return false;
    }

    private JsonParseException failure(String message, String path) {
        return failure(offset, line, lineStart, message, path);
    }

    private JsonParseException failure(int off, int l, int ls, String message, String path) {
        // Non-revealing message does not produce input source String
        var pos = off - ls;
        return new JsonParseException("%s. Path: \"%s\". Location: line %d, position %d."
                .formatted(message, path, l, pos), l, pos);
    }

    // Parsing error messages ----------------------
    private static final String UNEXPECTED_VAL =
            "Unexpected value. Expected a JSON Object, Array, String, Number, Boolean, or Null";
    private static final String UNRECOGNIZED_ESCAPE_SEQUENCE =
            "Unrecognized escape sequence: \"\\%c\"";
    private static final String UNESCAPED_CONTROL_CODE =
            "Unescaped control code";
    private static final String UNCLOSED_STRING =
            "%s is not closed with a quotation mark";
    private static final String INVALID_POSITION_IN_NUMBER =
            "Invalid position of '%c' within JSON Number";
}
