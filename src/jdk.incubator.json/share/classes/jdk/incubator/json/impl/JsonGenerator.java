/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonValue;

/**
 * Generates JSON text for JsonValue, either for toString() or toDisplayString().
 */
public final class JsonGenerator {

    private sealed interface StructureFrame permits ArrayFrame, ObjectFrame {}

    private static final class ArrayFrame implements StructureFrame {
        private final Iterator<JsonValue> values;
        private final int depth; // For indentation
        private boolean first; // Whether iterator points to first value

        private ArrayFrame(Iterator<JsonValue> values, int depth) {
            this.values = values;
            this.depth = depth;
            first = true;
        }
    }

    private static final class ObjectFrame implements StructureFrame {
        private final Iterator<Map.Entry<String, JsonValue>> members;
        private final int depth; // For indentation
        private boolean first; // Whether iterator points to first entry

        private ObjectFrame(Iterator<Map.Entry<String, JsonValue>> members, int depth) {
            this.members = members;
            this.depth = depth;
            first = true;
        }
    }

    // Generates JSON text for Json[Object|Array].toString()
    public static String toCompactString(JsonValue jv) {
        return generate(jv, "", false);
    }

    // Generates JSON text for Json.toDisplayString()
    public static String toDisplayString(JsonValue jv, String indent) {
        return generate(jv, indent, true);
    }

    private static String generate(JsonValue root, String indent, boolean isDisplay) {
        var sb = new StringBuilder();
        Deque<StructureFrame> stack = new ArrayDeque<>();
        enterValue(root, sb, stack, 0, isDisplay);

        while (!stack.isEmpty()) {
            switch (stack.peek()) {
                case ArrayFrame af -> {
                    var values = af.values;
                    if (values.hasNext()) {
                        if (af.first) {
                            af.first = false;
                        } else {
                            sb.append(isDisplay ? ",\n" : ",");
                        }
                        if (isDisplay) {
                            sb.repeat(indent, af.depth + 1);
                        }
                        enterValue(values.next(), sb, stack, af.depth + 1, isDisplay);
                    } else {
                        if (isDisplay) {
                            sb.append("\n");
                            sb.repeat(indent, af.depth);
                        }
                        sb.append("]");
                        stack.pop();
                    }
                }
                case ObjectFrame of -> {
                    var members = of.members;
                    if (members.hasNext()) {
                        if (of.first) {
                            of.first = false;
                        } else {
                            sb.append(isDisplay ? ",\n" : ",");
                        }
                        var entry = members.next();
                        if (isDisplay) {
                            sb.repeat(indent, of.depth + 1);
                        }
                        sb.append('"')
                                .append(Utils.escape(entry.getKey()))
                                .append("\":")
                                .append(isDisplay ? " " : "");
                        enterValue(entry.getValue(), sb, stack, of.depth + 1, isDisplay);
                    } else {
                        if (isDisplay) {
                            sb.append("\n");
                            sb.repeat(indent, of.depth);
                        }
                        sb.append("}");
                        stack.pop();
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void enterValue(JsonValue jv, StringBuilder sb, Deque<StructureFrame> stack,
                                   int depth, boolean isDisplay) {
        switch (jv) {
            case JsonArray ja -> {
                var values = ja.asList().iterator();
                if (!values.hasNext()) {
                    sb.append("[]");
                } else {
                    sb.append(isDisplay ? "[\n" : "[");
                    stack.push(new ArrayFrame(values, depth));
                }
            }
            case JsonObject jo -> {
                var members = jo.asMap().entrySet().iterator();
                if (!members.hasNext()) {
                    sb.append("{}");
                } else {
                    sb.append(isDisplay ? "{\n" : "{");
                    stack.push(new ObjectFrame(members, depth));
                }
            }
            default -> sb.append(jv);
        }
    }

    // Instantiation is not allowed
    private JsonGenerator() {}
}
