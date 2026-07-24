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

import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonValueException;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

/**
 * Shared utilities for Json classes.
 */
public class Utils {

    // Non instantiable
    private Utils() {}

    /*
     * Escapes a String to ensure it is a valid JSON String.
     * Backslash, double quote, and control chars are escaped.
     * Providing this method in Utils allows for a bypass of `JsonString.of(str).value()`
     * for the toString representation of JsonObject member names.
     */
    public static String escape(String str) {
        StringBuilder sb = null; // Lazy init
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // Does not require escaping
            if (c >= 32 && c != '\\' && c != '"') {
                if (sb != null) {
                    sb.append(c);
                }
            // Requires escaping
            } else {
                if (sb == null) {
                    sb = new StringBuilder().append(str, 0, i);
                }
                // 2 Char escapes (Non-control characters)
                if (c == '\\') {
                    sb.append('\\').append(c);
                } else if (c == '"') {
                    sb.append('\\').append(c);
                    // 2 Char escapes (Control characters)
                } else if (c == '\b') {
                    sb.append('\\').append('b');
                } else if (c == '\f') {
                    sb.append('\\').append('f');
                } else if (c == '\n') {
                    sb.append('\\').append('n');
                } else if (c == '\r') {
                    sb.append('\\').append('r');
                } else if (c == '\t') {
                    sb.append('\\').append('t');
                    // All other chars requiring Unicode escape sequence
                } else {
                    sb.append('\\').append('u').append(String.format("%04X", (int) c));
                }
            }
        }
        return sb == null ? str : sb.toString();
    }

    public static JsonValueException composeError(JsonValue jv, String message) {
        return new JsonValueException(message +
                (jv instanceof JsonValueImpl jvi && jvi.doc() != null ? JsonPath.getPath(jvi) : ""));
    }

    // Use to compose an exception when casting to an incorrect type
    public static JsonValueException composeTypeError(JsonValue jv, String expected) {
        var actual = switch (jv) {
            case JsonObject _ -> "JsonObject";
            case JsonArray _ -> "JsonArray";
            case JsonBoolean _ -> "JsonBoolean";
            case JsonNull _ -> "JsonNull";
            case JsonNumber _ -> "JsonNumber";
            case JsonString _ -> "JsonString";
        };
        return composeError(jv, "%s is not a %s.".formatted(actual, expected));
    }

    // This class is responsible for creating the path produced by JsonValueException.
    // Backtracks from the offset of the offending JSON element to the root.
    private static final class JsonPath {

        private final int offset;
        private final char[] doc;
        // Tracked and incremented during path creation
        private int line;
        private int pos;

        private JsonPath(JsonValueImpl jvi) {
            this.offset = jvi.offset();
            this.doc = jvi.doc();
        }

        private static String getPath(JsonValueImpl jvi) {
            return new JsonPath(jvi).parseToRoot();
        }

        private String parseToRoot() {
            var sb = new StringBuilder();
            // Updates the sb
            toPath(offset, sb);
            // If no new line encountered, pos is the starting offset value
            if (line == 0) {
                pos = offset;
            }
            return " Path: \"%s\". Location: line %d, position %d.".formatted(sb.toString(), line, pos);
        }

        private void addLine(int curr) {
            line++;
            if (line == 1) {
                pos = offset - curr - 1;
            }
        }

        // Void return type, builds the passed StringBuilder
        private void toPath(int offset, StringBuilder sb) {
            // Walk past starting char and white space
            offset = walkWhitespace(offset - 1);
            // If offset is -1, we found the root and are finished
            if (offset != -1) {
                // Node case
                offset = switch (doc[offset]) {
                    // Does the actual appending
                    // Walks to the node's starting [ or {
                    case ',', '[' -> arrayNode(offset, sb);
                    case ':' -> objectNode(offset, sb);
                    default -> throw new InternalError();
                };
                toPath(offset, sb);
            }
        }

        private int walkWhitespace(int offset) {
            while (offset >= 0) {
                var ws = switch (doc[offset]) {
                    case ' ', '\t','\r' -> true;
                    case '\n' -> {
                        addLine(offset);
                        yield true;
                    }
                    default -> false;
                };
                if (!ws) {
                    break;
                }
                offset--;
            }
            return offset;
        }

        // Backtracking from an element in a JsonArray either expects a ',' or '['
        // E.g. " [ val ... " or " [ foo, val "
        private int arrayNode(int offset, StringBuilder sb) {
            int aDepth = 0;
            int oDepth = 0;
            int values = 0;
            boolean inString = false;
            while (offset > 0) {
                var c = doc[offset];
                if (inString) {
                    if (c == '"' && doc[offset - 1] != '\\') {
                        inString = false;
                    }
                } else {
                    if (c == '[') {
                        aDepth++;
                    } else if (c == ']') {
                        aDepth--;
                    } else if (c == '{') {
                        oDepth++;
                    } else if (c == '}') {
                        oDepth--;
                    } else if (c == ',' && aDepth == 0 && oDepth == 0) {
                        values++;
                    } else if (c == '"') {
                        inString = true;
                    } else if (c == '\n') {
                        addLine(offset);
                    }
                    if (aDepth > 0) {
                        break;
                    }
                }
                offset--;
            }
            sb.insert(0, '[' + String.valueOf(values));
            return offset;
        }

        // Unlike arrayNode, always expects a ':'
        // Regardless of value position, always preceded by a member name and colon
        private int objectNode(int offset, StringBuilder sb) {
            offset--; // Walk past ':'
            int depth = 0;
            int nameStart = 0;
            int nameEnd = 0;
            boolean inName = false;

            // Append member name first
            while (offset > 0) {
                var c = doc[offset];
                if (c == '"' && !inName) {
                    nameEnd = offset;
                    inName = true;
                } else if (c == '"' && doc[offset - 1] != '\\') {
                    // Pre-escape check should not throw AIOOBE because guaranteed
                    // to have enclosing opening bracket
                    nameStart = offset + 1;
                    offset--; // Walk past quote
                    break;
                }
                offset--;
            }

            // Add the name
            sb.insert(0, '{' + new String(doc, nameStart, nameEnd - nameStart));

            boolean inString = false;
            // Move to parent offset
            while (offset > 0) {
                var c = doc[offset];
                if (inString) {
                    if (c == '"' && doc[offset - 1] != '\\') {
                        inString = false;
                    }
                } else {
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                    } else if (c == '"') {
                        inString = true;
                    } else if (c == '\n') {
                        addLine(offset);
                    }
                    if (depth > 0) {
                        break;
                    }
                }
                offset--;
            }
            return offset;
        }
    }
}
