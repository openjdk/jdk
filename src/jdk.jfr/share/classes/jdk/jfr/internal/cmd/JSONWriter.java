/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.cmd;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordingFile;

final class JSONWriter extends StructuredWriter {

    public JSONWriter(PrintWriter writer) {
        super(writer);
    }

    public void print(Path source) throws IOException {
        try (RecordingFile es = new RecordingFile(source)) {
            printObjectBegin();
            printRecording(es);
            printObjectEnd();
            flush();
        }
    }

    private void printRecording(RecordingFile es) throws IOException {
        printDataStructureName("recording");
        printObjectBegin();
        printEvents(es);
        printObjectEnd();
    }

    private void printEvents(RecordingFile es) throws IOException {
        printDataStructureName("events");
        printArrayBegin();
        boolean first = true;
        while (es.hasMoreEvents()) {
            RecordedEvent e = es.readEvent();
            printNewDataStructure(first, true, null);
            printEvent(e);
            flush();
            first = false;
        }
        printArrayEnd();
    }

    private void printEvent(RecordedEvent e) {
        printObjectBegin();
        EventType type = e.getEventType();
        printValue(true, false, "name", type.getName());
        printValue(false, false, "typeId", type.getId());
        printValue(false, false, "startTime", e.getStartTime());
        printValue(false, false, "duration", e.getDuration());
        printNewDataStructure(false, false, "values");
        printObject(e);
        printObjectEnd();
    }

    void printValue(boolean first, boolean arrayElement, String name, Object value) {
        printNewDataStructure(first, arrayElement, name);
        if (!printIfNull(value)) {
            if (value instanceof Boolean) {
                printAsString(value);
                return;
            }
            if (value instanceof Double) {
                Double dValue = (Double) value;
                if (Double.isNaN(dValue) || Double.isInfinite(dValue)) {
                    printNull();
                    return;
                }
                printAsString(value);
                return;
            }
            if (value instanceof Float) {
                Float fValue = (Float) value;
                if (Float.isNaN(fValue) || Float.isInfinite(fValue)) {
                    printNull();
                    return;
                }
                printAsString(value);
                return;
            }
            if (value instanceof Number) {
                printAsString(value);
                return;
            }
            print("\"");
            printEscaped(String.valueOf(value));
            print("\"");
        }
    }

    public void printObject(RecordedObject object) {
        printObjectBegin();
        boolean first = true;
        for (ValueDescriptor v : object.getFields()) {
            printValueDescriptor(first, false, v, object.getValue(v.getName()));
            first = false;
        }
        printObjectEnd();
    }

    private void printArray(ValueDescriptor v, Object[] array) {
        printArrayBegin();
        boolean first = true;
        for (Object arrayElement : array) {
            printValueDescriptor(first, true, v, arrayElement);
            first = false;
        }
        printArrayEnd();
    }

    private void printValueDescriptor(boolean first, boolean arrayElement, ValueDescriptor vd, Object value) {
        if (vd.isArray() && !arrayElement) {
            printNewDataStructure(first, arrayElement, vd.getName());
            if (!printIfNull(value)) {
                printArray(vd, (Object[]) value);
            }
            return;
        }
        if (!vd.getFields().isEmpty()) {
            printNewDataStructure(first, arrayElement, vd.getName());
            if (!printIfNull(value)) {
                printObject((RecordedObject) value);
            }
            return;
        }
        printValue(first, arrayElement, vd.getName(), value);
    }

    private void printNewDataStructure(boolean first, boolean arrayElement, String name) {
        if (!first) {
            print(", ");
            if (!arrayElement) {
                println();
            }
        }
        if (!arrayElement) {
            printDataStructureName(name);
        }
    }

    private boolean printIfNull(Object value) {
        if (value == null) {
            printNull();
            return true;
        }
        return false;
    }

    private void printNull() {
        print("null");
    }

    private void printDataStructureName(String text) {
        printIndent();
        print("\"");
        print(text);
        print("\": ");
    }

    private void printObjectEnd() {
        retract();
        println();
        printIndent();
        print("}");
    }

    private void printObjectBegin() {
        println("{");
        indent();
    }

    private void printArrayEnd() {
        print("]");
    }

    private void printArrayBegin() {
        print("[");
    }

    private void printEscaped(String text) {
        for (int i = 0; i < text.length(); i++) {
            printEscaped(text.charAt(i));
        }
    }

    private void printEscaped(char c) {
        if (c == '\b') {
            print("\\b");
            return;
        }
        if (c == '\n') {
            print("\\n");
            return;
        }
        if (c == '\t') {
            print("\\t");
            return;
        }
        if (c == '\f') {
            print("\\f");
            return;
        }
        if (c == '\r') {
            print("\\r");
            return;
        }
        if (c == '\"') {
            print("\\\"");
            return;
        }
        if (c == '\\') {
            print("\\\\");
            return;
        }
        if (c == '/') {
            print("\\/");
            return;
        }
        if (c > 0x7F || c < 32) {
            print("\\u");
            // 0x10000 will pad with zeros.
            print(Integer.toHexString(0x10000 + (int) c).substring(1));
            return;
        }
        print(c);
    }

}
