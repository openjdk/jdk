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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    // The "root" value
    private JsonValue root;
    // LIFO stack for objects/arrays partially parsed. Popped upon parsing completion
    private final Deque<Container> containers = new ArrayDeque<>();

    // Object/Array containers holding parsed child values
    private sealed interface Container permits ObjectContainer, ArrayContainer {}
    private static final class ObjectContainer implements Container {
        final int startOffset;
        final Map<String, JsonValue> members = new LinkedHashMap<>();
        String name;
        int nameStart;
        int nameLine;
        int nameLineStart;
        ObjectState state = ObjectState.NAME_OR_END;

        ObjectContainer(int startOffset) {
            this.startOffset = startOffset;
        }
    }
    private static final class ArrayContainer implements Container {
        final int startOffset;
        final List<JsonValue> elements = new ArrayList<>();
        ArrayState state = ArrayState.VALUE_OR_END;

        ArrayContainer(int startOffset) {
            this.startOffset = startOffset;
        }
    }

    // Object/Array container states for the next possible input
    private enum ObjectState {
        NAME_OR_END, COLON, VALUE, COMMA_OR_END
    }
    private enum ArrayState {
        VALUE_OR_END, COMMA_OR_END
    }

    public JsonParser(char[] doc) {
        this.doc = doc;
    }

    // Parses the lone JsonValue root
    public JsonValue parseRoot() {
        containers.clear();
        root = null;

        parseValue();

        while (!containers.isEmpty()) {
            switch (containers.peek()) {
                case ObjectContainer oc -> parseObject(oc);
                case ArrayContainer ac -> parseArray(ac);
            }
        }

        if (hasInput()) {
            throw valueFailure(0, "Additional value(s) were found after the JSON Value");
        }
        return root;
    }

    /*
     * Parse any one of the JSON value types: object, array, number, string,
     * true, false, or null.
     *      JSON-text = ws value ws
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-3
     */
    private void parseValue() {
        skipWhitespaces();
        int start = offset;

        if (!hasInput()) {
            throw valueFailure(start, "Expected a JSON Object, Array, String, Number, Boolean, or Null");
        }

        switch (doc[offset]) {
            case '{' -> {
                offset++;
                skipWhitespaces();
                containers.push(new ObjectContainer(start));
            }
            case '[' -> {
                offset++;
                skipWhitespaces();
                containers.push(new ArrayContainer(start));
            }
            case '"' -> finishValue(parseString(), start);
            case 't' -> finishValue(parseTrue(), start);
            case 'f' -> finishValue(parseFalse(), start);
            case 'n' -> finishValue(parseNull(), start);
            // While JSON Number does not support leading '+', '.', or 'e'
            // we still accept, so that we can provide a better error message
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                 '-', '+', 'e', '.' -> finishValue(parseNumber(), start);
            default -> throw valueFailure(start, UNEXPECTED_VAL);
        }
    }

    /*
     * The parsed JsonObject contains a map which holds all lazy member mappings.
     * No offsets are required as member values hold their own offsets.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-4
     */
    private void parseObject(ObjectContainer oc) {
        switch (oc.state) {
            case NAME_OR_END -> {
                if (!hasInput()) {
                    throw structureFailure(oc.startOffset, "JSON Object is not closed with a brace");
                }
                if (oc.members.isEmpty() && charEquals('}')) {
                    finishObject(oc);
                    return;
                }

                oc.nameStart = offset;
                oc.name = parseName(oc.startOffset);
                oc.nameLine = line;
                oc.nameLineStart = lineStart;
                oc.state = ObjectState.COLON;
            }
            case COLON -> {
                skipWhitespaces();
                if (!charEquals(':')) {
                    throw structureFailure(oc.startOffset, "Expected a colon after the member name");
                }
                if (oc.members.containsKey(oc.name)) {
                    throw failure(oc.nameStart, oc.nameLine, oc.nameLineStart,
                        "Duplicate member name: \"%s\" was already parsed".formatted(Utils.escape(oc.name)), oc.startOffset, true);
                }
                oc.state = ObjectState.VALUE;
            }
            case VALUE -> parseValue();
            case COMMA_OR_END -> {
                if (charEquals('}')) {
                    finishObject(oc);
                } else if (charEquals(',')) {
                    skipWhitespaces();
                    oc.state = ObjectState.NAME_OR_END;
                } else {
                    throw structureFailure(oc.startOffset, "JSON Object is not closed with a brace");
                }
            }
        }
    }

    private void finishObject(ObjectContainer oc) {
        containers.pop();

        var jo = oc.members.isEmpty()
            ? new JsonObjectImpl(Map.of(), oc.startOffset, doc)
            : new JsonObjectImpl(oc.members, oc.startOffset, doc);

        finishValue(jo, oc.startOffset);
    }

    /*
     * The parsed JsonArray contains a List which holds all lazy children
     * elements. No offsets are required as children values hold their own offsets.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-5
     */
    private void parseArray(ArrayContainer ac) {
        switch (ac.state) {
            case VALUE_OR_END -> {
                if (!hasInput()) {
                    throw structureFailure(ac.startOffset,
                        "JSON Array is not closed with a bracket");
                }
                if (ac.elements.isEmpty() && charEquals(']')) {
                    finishArray(ac);
                } else {
                    parseValue();
                }
            }
            case COMMA_OR_END -> {
                if (charEquals(']')) {
                    finishArray(ac);
                } else if (charEquals(',')) {
                    ac.state = ArrayState.VALUE_OR_END;
                } else {
                    throw structureFailure(ac.startOffset,
                        "JSON Array is not closed with a bracket");
                }
            }
        }
    }

    private void finishArray(ArrayContainer ac) {
        containers.pop();

        var ja = ac.elements.isEmpty()
            ? new JsonArrayImpl(List.of(), ac.startOffset, doc)
            : new JsonArrayImpl(ac.elements, ac.startOffset, doc);

        finishValue(ja, ac.startOffset);
    }

    // Place the value as either the root, an object member, or an array element.
    private void finishValue(JsonValue jv, int start) {
        if (hasInput()) {
            switch (doc[offset]) {
                // Attribute incorrect values appended directly on a valid value as
                // error on the value rather than its enclosing structure.
                case ']', '}', ',', ' ', '\t', '\r', '\n' -> {}
                default -> throw valueFailure(start, "Unexpected content after JSON value");
            }
        }
        skipWhitespaces();

        if (containers.isEmpty()) {
            root = jv;
        } else {
            switch (containers.peek()) {
                case ObjectContainer oc -> {
                    oc.members.put(oc.name, jv);
                    oc.name = null;
                    oc.state = ObjectState.COMMA_OR_END;
                }
                case ArrayContainer ac -> {
                    ac.elements.add(jv);
                    ac.state = ArrayState.COMMA_OR_END;
                }
            }
        }
    }

    /*
     * Member name equality and storage in the map should be done with the
     * unescaped value.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-8.3
     */
    private String parseName(int objStart) {
        if (!charEquals('"')) {
            throw structureFailure(objStart, "Expecting a JSON Object member name");
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
                        c = codeUnit(objStart, true);
                        escapeLength = 4;
                    }
                    default -> throw structureFailure(objStart, UNRECOGNIZED_ESCAPE_SEQUENCE.formatted((int)c));
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
            } else if (c == '"') {
                offset++;
                if (useBldr) {
                    var name = sb.get().toString();
                    sb.get().setLength(0);
                    return name;
                } else {
                    return new String(doc, start, offset - start - 1);
                }
            } else if (c < ' ') {
                throw structureFailure(objStart, UNESCAPED_CONTROL_CODE);
            }
            if (useBldr) {
                sb.get().append(c);
            }
        }
        throw structureFailure(objStart, UNCLOSED_STRING.formatted("JSON Object member name"));
    }

    /*
     * The parsed JsonString will contain offsets correlating to the beginning
     * and ending quotation marks. All Unicode characters are allowed except the
     * following that require escaping: quotation mark, reverse solidus, and the
     * control characters (U+0000 through U+001F). Any character may be escaped
     * either through a Unicode escape sequence or two-char sequence.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-7
     */
    private JsonString parseString() {
        int start = offset++; // Move past the starting quote
        var escape = false;
        boolean hasEscape = false;
        for (; hasInput(); offset++) {
            var c = doc[offset];
            if (escape) {
                switch (c) {
                    // Allowed JSON escapes
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {}
                    case 'u' -> codeUnit(start, false);
                    default -> throw valueFailure(start, UNRECOGNIZED_ESCAPE_SEQUENCE.formatted((int)c));
                }
                escape = false;
            } else if (c == '\\') {
                hasEscape = true;
                escape = true;
            } else if (c == '"') {
                return new JsonStringImpl(doc, false, start, ++offset, hasEscape);
            } else if (c < ' ') {
                throw valueFailure(start, UNESCAPED_CONTROL_CODE);
            }
        }
        throw valueFailure(start, UNCLOSED_STRING.formatted("JSON String"));
    }

    private JsonBooleanImpl parseTrue() {
        var start = offset++;
        if (charEquals('r') && charEquals('u') && charEquals('e')) {
            return new JsonBooleanImpl(true, doc, start);
        }
        throw valueFailure(start, UNEXPECTED_VAL);
    }

    private JsonBooleanImpl parseFalse() {
        var start = offset++;
        if (charEquals('a') && charEquals('l') && charEquals('s')
                && charEquals('e')) {
            return new JsonBooleanImpl(false, doc, start);
        }
        throw valueFailure(start, UNEXPECTED_VAL);
    }

    private JsonNullImpl parseNull() {
        var start = offset++;
        if (charEquals('u') && charEquals('l') && charEquals('l')) {
            return new JsonNullImpl(doc, start);
        }
        throw valueFailure(start, UNEXPECTED_VAL);
    }

    /*
     * The parsed JsonNumber contains offsets correlating to the first and last
     * allowed chars permitted in the JSON numeric grammar:
     *      number = [ minus ] int [ frac ] [ exp ]
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-6
     */
    private JsonNumberImpl parseNumber() {
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
                        throw valueFailure(start, INVALID_POSITION_IN_NUMBER.formatted(c));
                    }
                    sawSign = true;
                }
                case '+' -> {
                    if (expOff == -1 || havePart || sawSign) {
                        throw valueFailure(start, INVALID_POSITION_IN_NUMBER.formatted(c));
                    }
                    sawSign = true;
                }
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (decOff == -1 && expOff == -1 && sawZero) {
                        throw valueFailure(start, INVALID_POSITION_IN_NUMBER.formatted('0'));
                    }
                    if (doc[offset] == '0' && !havePart) {
                        sawZero = true;
                    }
                    havePart = true;
                }
                case '.' -> {
                    if (decOff != -1 || expOff != -1) {
                        throw valueFailure(start, INVALID_POSITION_IN_NUMBER.formatted(c));
                    } else {
                        if (!havePart) {
                            throw valueFailure(start, INVALID_POSITION_IN_NUMBER.formatted(c));
                        }
                        decOff = offset;
                        havePart = false;
                    }
                }
                case 'e', 'E' -> {
                    if (expOff != -1) {
                        throw valueFailure(start, INVALID_POSITION_IN_NUMBER.formatted(c));
                    } else {
                        if (!havePart) {
                            throw valueFailure(start, INVALID_POSITION_IN_NUMBER.formatted(c));
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
            throw valueFailure(start, "Input expected after '[.|e|E]'");
        }
        return new JsonNumberImpl(doc, false, start, offset, decOff, expOff);
    }

    // Utility functions

    private StringBuilder initSb() {
        return new StringBuilder();
    }

    // Unescapes the Unicode escape sequence and produces a char
    private char codeUnit(int start, boolean structural) {
        char val = 0;
        int end = offset + 4;
        if (end >= doc.length) {
            throw failure("Invalid Unicode escape sequence. Expected four hex digits",
                    start, structural);
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
                                "Invalid Unicode escape sequence. '\\u%04X' is not a hex digit".formatted((int)c),
                                start, structural);
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
            case ' ', '\t', '\r' -> false;
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

    // To be thrown when a structure is incorrect, which derives the path from the enclosing structure itself
    private JsonParseException structureFailure(int start, String message) {
        return failure(offset, line, lineStart, message, start, true);
    }

    // To be thrown when a "value" is incorrect, which derives the path from the value
    private JsonParseException valueFailure(int start, String message) {
        return failure(offset, line, lineStart, message, start, false);
    }

    private JsonParseException failure(String message, int recentStart, boolean structural) {
        return failure(offset, line, lineStart, message, recentStart, structural);
    }

    private JsonParseException failure(int off, int l, int ls, String message, int head, boolean structural) {
        // Non-revealing message does not produce input source String
        var pos = off - ls;
        var path = Utils.getParsingPath(head, doc, structural);
        return new JsonParseException(String.format(Locale.ROOT,
            "%s.%s Location: line %d, position %d.",
            message, path, l, pos), l, pos);
    }

    // Parsing error messages ----------------------
    private static final String UNEXPECTED_VAL =
            "Unexpected value. Expected a JSON Object, Array, String, Number, Boolean, or Null";
    private static final String UNRECOGNIZED_ESCAPE_SEQUENCE =
            "Unrecognized escape sequence: \"\\\\u%04X\"";
    private static final String UNESCAPED_CONTROL_CODE =
            "Unescaped control code";
    private static final String UNCLOSED_STRING =
            "%s is not closed with a quotation mark";
    private static final String INVALID_POSITION_IN_NUMBER =
            "Invalid position of '%c' within JSON Number";
}
