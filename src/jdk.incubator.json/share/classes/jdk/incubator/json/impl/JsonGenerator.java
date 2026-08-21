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
import java.util.ArrayList;
import java.util.Deque;

import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonValue;

/**
 * Generates JSON text for JsonValue, either for toString() or toDisplayString()
 */
public final class JsonGenerator {

    // Types of output items: either literal string or a JSON value to generate
    private sealed interface Output permits LiteralOutput, JsonValueOutput {}
    private record JsonValueOutput(JsonValue jv, int depth, boolean isField) implements Output {}
    private record LiteralOutput(String literal) implements Output {}

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
        Deque<Output> outputs = new ArrayDeque<>();
        outputs.push(new JsonValueOutput(root, 0, false));

        while (!outputs.isEmpty()) {
            switch (outputs.pop()) {
                case LiteralOutput(String literal) -> sb.append(literal);
                case JsonValueOutput(JsonValue jv, int depth, boolean isField) -> {
                    // append prefix
                    if (isDisplay) {
                        sb.append(isField ? " " : indent.repeat(depth));
                    }
                    switch (jv) {
                        case JsonObject jo -> generateObject(jo, sb, outputs, indent, depth, isDisplay);
                        case JsonArray ja -> generateArray(ja, sb, outputs, indent, depth, isDisplay);
                        default -> sb.append(jv);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void generateObject(JsonObject jo, StringBuilder sb, Deque<Output> outputs,
                                       String indent, int depth, boolean isDisplay) {
        // Needs a list to process members backward
        var members = new ArrayList<>(jo.asMap().entrySet());
        if (members.isEmpty()) {
            sb.append("{}");
            return;
        }

        sb.append(isDisplay ? "{\n" : "{");

        // push outputs backward
        outputs.push(new LiteralOutput((isDisplay ? "\n" + indent.repeat(depth) : "") + "}"));
        var iter = members.reversed().iterator();
        while(iter.hasNext()) {
            var member = iter.next();
            outputs.push(new JsonValueOutput(member.getValue(), depth + 1, true));
            outputs.push(new LiteralOutput((isDisplay ? indent.repeat(depth + 1) : "")
                + '"' + Utils.escape(member.getKey()) + "\":"));
            if (iter.hasNext()) {
                outputs.push(new LiteralOutput(isDisplay ? ",\n" : ","));
            }
        }
    }

    private static void generateArray(JsonArray ja, StringBuilder sb, Deque<Output> outputs,
                                      String indent, int depth, boolean isDisplay) {
        var elements = ja.asList();
        if (elements.isEmpty()) {
            sb.append("[]");
            return;
        }

        sb.append(isDisplay ? "[\n" : "[");

        // push outputs backward
        outputs.push(new LiteralOutput((isDisplay ? "\n" + indent.repeat(depth) : "") + "]"));
        var iter = elements.reversed().iterator();
        while(iter.hasNext()) {
            var element = iter.next();
            outputs.push(new JsonValueOutput(element, depth + 1, false));
            if (iter.hasNext()) {
                outputs.push(new LiteralOutput(isDisplay ? ",\n" : ","));
            }
        }
    }

    // Instantiation is not allowed
    private JsonGenerator() {}
}
