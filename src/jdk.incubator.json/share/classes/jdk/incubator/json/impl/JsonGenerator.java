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
    private record ArrayFrame(Iterator<JsonValue> elements, int depth) implements StructureFrame {}
    private record ObjectFrame(Iterator<Map.Entry<String, JsonValue>> members, int depth) implements StructureFrame {}

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
        String delim = isDisplay ? ",\n" : ",";
        Deque<StructureFrame> stack = new ArrayDeque<>();
        enterValue(root, sb, stack, 0, isDisplay);

        while (!stack.isEmpty()) {
            switch (stack.peek()) {
                case ArrayFrame af -> {
                    var elements = af.elements;
                    if (elements.hasNext()) {
                        var stackSize = stack.size();
                        if (isDisplay) {
                            sb.repeat(indent, af.depth + 1);
                        }
                        enterValue(elements.next(), sb, stack, af.depth + 1, isDisplay);
                        if (stackSize == stack.size()) {
                            sb.append(delim); // Appends after non-structural
                        }
                    } else {
                        closeStructure(sb, stack, "]", indent, delim, af.depth, isDisplay);
                    }
                }
                case ObjectFrame of -> {
                    var members = of.members;
                    if (members.hasNext()) {
                        var stackSize = stack.size();
                        var entry = members.next();
                        if (isDisplay) {
                            sb.repeat(indent, of.depth + 1);
                        }
                        sb.append('"')
                                .append(Utils.escape(entry.getKey()))
                                .append("\":")
                                .append(isDisplay ? " " : "");
                        enterValue(entry.getValue(), sb, stack, of.depth + 1, isDisplay);
                        if (stackSize == stack.size()) {
                            sb.append(delim); // Appends after non-structural
                        }
                    } else {
                        closeStructure(sb, stack, "}", indent, delim, of.depth, isDisplay);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void closeStructure(StringBuilder sb, Deque<StructureFrame> stack, String close, String indent,
                                       String delim, int depth, boolean isDisplay) {
        // We always append delim for each child, so trim the final exccess
        sb.setLength(sb.length() - delim.length());
        if (isDisplay) {
            sb.append("\n");
            sb.repeat(indent, depth);
        }
        sb.append(close);
        stack.pop();
        if (!stack.isEmpty()) {
            sb.append(delim); // Need to append delim for structures
        }
    }

    private static void enterValue(JsonValue jv, StringBuilder sb, Deque<StructureFrame> stack,
                                   int depth, boolean isDisplay) {
        switch (jv) {
            case JsonArray ja -> {
                var elements = ja.asList().iterator();
                if (!elements.hasNext()) {
                    sb.append("[]");
                } else {
                    sb.append(isDisplay ? "[\n" : "[");
                    stack.push(new ArrayFrame(elements, depth));
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
