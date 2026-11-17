/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.test.lib.json;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public sealed interface JSONValue
        permits JSONValue.JSONObject, JSONValue.JSONArray, JSONValue.JSONString,
                JSONValue.JSONNumber, JSONValue.JSONBoolean, JSONValue.JSONNull {

    public final class JSONObject implements JSONValue {
        private final Map<String, JSONValue> members;

        private JSONObject(Map<String, ? extends JSONValue> members) {
            this.members = Map.copyOf(members);
        }

        @Override
        public JSONValue get(String name) {
            JSONValue v = members.get(name);
            if (v == null) {
                throw new NoSuchElementException("member " + name + " does not exist");
            }
            return v;
        }

        @Override
        public Optional<JSONValue> getOrAbsent(String name) {
            return Optional.ofNullable(members.get(name));
        }

        @Override
        public Map<String, JSONValue> members() {
            return members;
        }

        @Override
        public String toString() {
            var builder = new StringBuilder();
            builder.append("{");
            for (String key : members.keySet()) {
                builder.append("\"");
                builder.append(key);
                builder.append("\":");
                builder.append(members.get(key).toString());
                builder.append(",");
            }

            int end = builder.length() - 1;
            if (builder.charAt(end) == ',') {
                builder.deleteCharAt(end);
            }

            builder.append("}");
            return builder.toString();
        }

        public static JSONObject of(Map<String, ? extends JSONValue> members) {
            return new JSONObject(members);
        }
    }

    public final class JSONString implements JSONValue {
        private final String value;

        private JSONString(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public String toString() {
            var builder = new StringBuilder();
            builder.append("\"");

            for (var i = 0; i < value.length(); i++) {
                var c = value.charAt(i);

                switch (c) {
                    case '"':
                        builder.append("\\\"");
                        break;
                    case '\\':
                        builder.append("\\\\");
                        break;
                    case '/':
                        builder.append("\\/");
                        break;
                    case '\b':
                        builder.append("\\b");
                        break;
                    case '\f':
                        builder.append("\\f");
                        break;
                    case '\n':
                        builder.append("\\n");
                        break;
                    case '\r':
                        builder.append("\\r");
                        break;
                    case '\t':
                        builder.append("\\t");
                        break;
                    default:
                        builder.append(c);
                        break;
                }
            }

            builder.append("\"");
            return builder.toString();
        }

        public static JSONString of(String value) {
            return new JSONString(value);
        }
    }

    public final class JSONArray implements JSONValue {
        private final List<JSONValue> elements;

        JSONArray(List<? extends JSONValue> elements) {
            this.elements = List.copyOf(elements);
        }

        @Override
        public String toString() {
            var builder = new StringBuilder();

            builder.append("[");
            int size = elements.size();
            for (int i = 0; i < size; i++) {
                builder.append(elements.get(i).toString());
                if (i != (size - 1)) {
                    builder.append(",");
                }
            }
            builder.append("]");
            return builder.toString();
        }

        @Override
        public List<JSONValue> elements() {
            return elements;
        }

        @Override
        public JSONValue element(int index) {
            return elements.get(index);
        }

        public static JSONArray of(List<? extends JSONValue> elements) {
            return new JSONArray(elements);
        }
    }

    public final class JSONNumber implements JSONValue {
        private final String value;

        private JSONNumber(String value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public int asInt() {
            return Integer.parseInt(value);
        }

        @Override
        public long asLong() {
            return Long.parseLong(value);
        }

        @Override
        public double asDouble() {
            return Double.parseDouble(value);
        }

        @Override
        public String toString() {
            return value;
        }

        public static JSONNumber of(String value) {
            return new JSONNumber(value);
        }

        public static JSONNumber of(int value) {
            return of(String.valueOf(value));
        }

        public static JSONNumber of(long value) {
            return of(String.valueOf(value));
        }

        public static JSONNumber of(double value) {
            return of(String.valueOf(value));
        }
    }

    public final class JSONBoolean implements JSONValue {
        private static JSONBoolean TRUE = new JSONBoolean(true);
        private static JSONBoolean FALSE = new JSONBoolean(false);

        private final boolean value;

        private JSONBoolean(boolean value) {
            this.value = value;
        }

        @Override
        public boolean asBoolean() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        public static JSONBoolean of(boolean value) {
            return value ? TRUE : FALSE;
        }
    }

    public final class JSONNull implements JSONValue {
        private static JSONNull NULL = new JSONNull();

        private JSONNull() {}

        @Override
        public Optional<JSONValue> valueOrNull() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "null";
        }

        public static JSONNull of() {
            return NULL;
        }
    }

    class JSONParser {
        private int pos = 0;
        private String input;

        JSONParser() {
        }

        private IllegalArgumentException failure(String message) {
            return new IllegalArgumentException(String.format("[%d]: %s : %s", pos, message, input));
        }

        private char current() {
            return input.charAt(pos);
        }

        private void advance() {
            pos++;
        }

        private boolean hasInput() {
            return pos < input.length();
        }

        private void expectMoreInput(String message) {
            if (!hasInput()) {
                throw failure(message);
            }
        }

        private char next(String message) {
            advance();
            if (!hasInput()) {
                throw failure(message);
            }
            return current();
        }

        private void expect(char c) {
            var msg = String.format("Expected character %c", c);

            var n = next(msg);
            if (n != c) {
                throw failure(msg);
            }
        }

        private JSONBoolean parseBoolean() {
            if (current() == 't') {
                expect('r');
                expect('u');
                expect('e');
                advance();
                return JSONBoolean.of(true);
            }

            if (current() == 'f') {
                expect('a');
                expect('l');
                expect('s');
                expect('e');
                advance();
                return JSONBoolean.of(false);
            }
            throw failure("a boolean can only be 'true' or 'false'");
        }

        private JSONValue parseNumber() {
            var isInteger = true;
            var builder = new StringBuilder();

            if (current() == '-') {
                builder.append(current());
                advance();
                expectMoreInput("a number cannot consist of only '-'");
            }

            if (current() == '0') {
                builder.append(current());
                advance();

                if (hasInput() && current() == '.') {
                    isInteger = false;
                    builder.append(current());
                    advance();

                    expectMoreInput("a number cannot end with '.'");

                    if (!isDigit(current())) {
                        throw failure("must be at least one digit after '.'");
                    }

                    while (hasInput() && isDigit(current())) {
                        builder.append(current());
                        advance();
                    }
                }
            } else {
                while (hasInput() && isDigit(current())) {
                    builder.append(current());
                    advance();
                }

                if (hasInput() && current() == '.') {
                    isInteger = false;
                    builder.append(current());
                    advance();

                    expectMoreInput("a number cannot end with '.'");

                    if (!isDigit(current())) {
                        throw failure("must be at least one digit after '.'");
                    }

                    while (hasInput() && isDigit(current())) {
                        builder.append(current());
                        advance();
                    }
                }
            }

            if (hasInput() && (current() == 'e' || current() == 'E')) {
                isInteger = false;

                builder.append(current());
                advance();
                expectMoreInput("a number cannot end with 'e' or 'E'");

                if (current() == '+' || current() == '-') {
                    builder.append(current());
                    advance();
                }

                if (!isDigit(current())) {
                    throw failure("a digit must follow {'e','E'}{'+','-'}");
                }

                while (hasInput() && isDigit(current())) {
                    builder.append(current());
                    advance();
                }
            }

            var value = builder.toString();
            if (isInteger) {
                new BigInteger(value);
            } else {
                Double.parseDouble(value);
            }
            return JSONNumber.of(value);
        }

        private JSONString parseString() {
            var missingEndChar = "string is not terminated with '\"'";
            var builder = new StringBuilder();
            for (var c = next(missingEndChar); c != '"'; c = next(missingEndChar)) {
                if (c == '\\') {
                    var n = next(missingEndChar);
                    switch (n) {
                        case '"':
                            builder.append("\"");
                            break;
                        case '\\':
                            builder.append("\\");
                            break;
                        case '/':
                            builder.append("/");
                            break;
                        case 'b':
                            builder.append("\b");
                            break;
                        case 'f':
                            builder.append("\f");
                            break;
                        case 'n':
                            builder.append("\n");
                            break;
                        case 'r':
                            builder.append("\r");
                            break;
                        case 't':
                            builder.append("\t");
                            break;
                        case 'u':
                            var u1 = next(missingEndChar);
                            var u2 = next(missingEndChar);
                            var u3 = next(missingEndChar);
                            var u4 = next(missingEndChar);
                            var cp = Integer.parseInt(String.format("%c%c%c%c", u1, u2, u3, u4), 16);
                            builder.append(new String(new int[]{cp}, 0, 1));
                            break;
                        default:
                            throw failure(String.format("Unexpected escaped character '%c'", n));
                    }
                } else {
                    builder.append(c);
                }
            }

            advance(); // step beyond closing "
            return JSONString.of(builder.toString());
        }

        private JSONArray parseArray() {
            var error = "array is not terminated with ']'";
            var list = new ArrayList<JSONValue>();

            advance(); // step beyond opening '['
            consumeWhitespace();
            expectMoreInput(error);

            while (current() != ']') {
                var val = parseValue();
                list.add(val);

                expectMoreInput(error);
                if (current() == ',') {
                    advance();
                }
                expectMoreInput(error);
            }

            advance(); // step beyond closing ']'
            return JSONArray.of(list);
        }

        public JSONNull parseNull() {
            expect('u');
            expect('l');
            expect('l');
            advance();
            return JSONNull.of();
        }

        public JSONObject parseObject() {
            var error = "object is not terminated with '}'";
            var map = new HashMap<String, JSONValue>();

            advance(); // step beyond opening '{'
            consumeWhitespace();
            expectMoreInput(error);

            while (current() != '}') {
                var key = parseValue();
                if (!(key instanceof JSONString)) {
                    throw failure("a field must of type string");
                }

                if (!hasInput() || current() != ':') {
                    throw failure("a field must be followed by ':'");
                }
                advance(); // skip ':'

                var val = parseValue();
                map.put(key.asString(), val);

                expectMoreInput(error);
                if (current() == ',') {
                    advance();
                }
                expectMoreInput(error);
            }

            advance(); // step beyond '}'
            return JSONObject.of(map);
        }

        private boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private boolean isStartOfNumber(char c) {
            return isDigit(c) || c == '-';
        }

        private boolean isStartOfString(char c) {
            return c == '"';
        }

        private boolean isStartOfBoolean(char c) {
            return c == 't' || c == 'f';
        }

        private boolean isStartOfArray(char c) {
            return c == '[';
        }

        private boolean isStartOfNull(char c) {
            return c == 'n';
        }

        private boolean isWhitespace(char c) {
            return c == '\r' ||
                   c == '\n' ||
                   c == '\t' ||
                   c == ' ';
        }

        private boolean isStartOfObject(char c) {
            return c == '{';
        }

        private void consumeWhitespace() {
            while (hasInput() && isWhitespace(current())) {
                advance();
            }
        }

        public JSONValue parseValue() {
            JSONValue ret = null;

            consumeWhitespace();
            if (hasInput()) {
                var c = current();

                if (isStartOfNumber(c)) {
                    ret = parseNumber();
                } else if (isStartOfString(c)) {
                    ret = parseString();
                } else if (isStartOfBoolean(c)) {
                    ret = parseBoolean();
                } else if (isStartOfArray(c)) {
                    ret = parseArray();
                } else if (isStartOfNull(c)) {
                    ret = parseNull();
                } else if (isStartOfObject(c)) {
                    ret = parseObject();
                } else {
                    throw failure("not a valid start of a JSON value");
                }
            }
            consumeWhitespace();

            return ret;
        }

        public JSONValue parse(String s) {
            if (s == null || s.equals("")) {
                return null;
            }

            pos = 0;
            input = s;

            var result = parseValue();
            if (hasInput()) {
                throw failure("can only have one top-level JSON value");
            }
            return result;
        }
    }

    static JSONValue parse(String s) {
        return new JSONParser().parse(s);
    }

    default JSONValue get(String name) {
        throw new UnsupportedOperationException("Unsupported conversion to object");
    }

    default Optional<JSONValue> getOrAbsent(String name) {
        throw new UnsupportedOperationException("Unsupported conversion to object");
    }

    default Optional<JSONValue> valueOrNull() {
        return Optional.of(this);
    }

    default Map<String, JSONValue> members() {
        throw new UnsupportedOperationException("Unsupported conversion to object");
    }

    default List<JSONValue> elements() {
        throw new UnsupportedOperationException("Unsupported conversion to array");
    }

    default JSONValue element(int index) {
        throw new UnsupportedOperationException("Unsupported conversion to array");
    }

    default String asString() {
        throw new UnsupportedOperationException("Unsupported conversion to string");
    }

    default int asInt() {
        throw new UnsupportedOperationException("Unsupported conversion to number");
    }

    default long asLong() {
        throw new UnsupportedOperationException("Unsupported conversion to number");
    }

    default double asDouble() {
        throw new UnsupportedOperationException("Unsupported conversion to number");
    }

    default boolean asBoolean() {
        throw new UnsupportedOperationException("Unsupported conversion to boolean");
    }
}
