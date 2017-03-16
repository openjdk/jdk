/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.salver.serialize;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.salver.writer.DumpWriter;

public class JSONSerializer extends AbstractSerializer {

    public static final String MEDIA_TYPE = "application/json";
    public static final String FILE_EXTENSION = "json";

    public JSONSerializer() {
    }

    public JSONSerializer(DumpWriter writer) {
        super(writer);
    }

    @Override
    public JSONSerializer serialize(Object obj) throws IOException {
        if (writer != null) {
            writer.write(appendValue(new StringBuilder(), obj).append('\n'));
        }
        return this;
    }

    public static StringBuilder stringify(StringBuilder sb, Object obj) {
        return appendValue(sb, obj);
    }

    public static String stringify(Object obj) {
        return appendValue(new StringBuilder(), obj).toString();
    }

    public static String getMediaType() {
        return MEDIA_TYPE;
    }

    public static String getFileExtension() {
        return FILE_EXTENSION;
    }

    @SuppressWarnings("unchecked")
    private static StringBuilder appendValue(StringBuilder sb, Object val) {
        if (val instanceof Map<?, ?>) {
            return appendDict(sb, (Map<Object, Object>) val);
        }
        if (val instanceof List<?>) {
            return appendList(sb, (List<Object>) val);
        }
        if (val instanceof byte[]) {
            return appendByteArray(sb, (byte[]) val);
        }
        if (val instanceof Number) {
            return sb.append(val);
        }
        if (val instanceof Boolean) {
            return sb.append(val);
        }
        if (val == null) {
            return sb.append("null");
        }
        return appendString(sb, String.valueOf(val));
    }

    private static StringBuilder appendDict(StringBuilder sb, Map<Object, Object> dict) {
        sb.append('{');
        boolean comma = false;
        for (Map.Entry<Object, Object> entry : dict.entrySet()) {
            if (comma) {
                sb.append(',');
            } else {
                comma = true;
            }
            appendString(sb, String.valueOf(entry.getKey()));
            sb.append(':');
            appendValue(sb, entry.getValue());
        }
        return sb.append('}');
    }

    private static StringBuilder appendList(StringBuilder sb, List<Object> list) {
        sb.append('[');
        boolean comma = false;
        for (Object val : list) {
            if (comma) {
                sb.append(',');
            } else {
                comma = true;
            }
            appendValue(sb, val);
        }
        return sb.append(']');
    }

    private static StringBuilder appendString(StringBuilder sb, String str) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default: {
                    if (Character.isISOControl(c)) {
                        sb.append("\\u00");
                        sb.append(Character.forDigit((c >> 4) & 0xF, 16));
                        sb.append(Character.forDigit(c & 0xF, 16));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"');
    }

    private static StringBuilder appendByteArray(StringBuilder sb, byte[] arr) {
        if (arr.length > 0) {
            sb.append("0x");
            for (byte b : arr) {
                sb.append(String.format("%02x", b));
            }
            return sb;
        }
        return sb.append("null");
    }
}
