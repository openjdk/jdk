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

final class XMLWriter extends StructuredWriter {

    public XMLWriter(PrintWriter destination) {
        super(destination);
    }

    public void print(Path source) throws IOException {
        try (RecordingFile es = new RecordingFile(source)) {
            println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            println("<recording>");
            indent();
            printIndent();
            println("<events>");
            indent();
            while (es.hasMoreEvents()) {
                printEvent(es.readEvent());
                flush();
            }
            retract();
            printIndent();
            println("</events>");
            retract();
            println("</recording>");
            flush();
        }
    }

    private void printEvent(RecordedEvent e) throws IOException {
        EventType type = e.getEventType();
        printIndent();
        print("<event");
        printAttribute("typeId", String.valueOf(type.getId()));
        printAttribute("name", type.getName());
        printAttribute("startTime",e.getStartTime().toString());
        printAttribute("duration", e.getDuration().toString());
        print(">");
        printObject(e);
        printIndent();
        println("</event>");
        println();
    }

    private void printAttribute(String name, String value) {
        print(" ", name, "=\"", value, "\"");
    }

    public void printObject(RecordedObject struct) {
        println();
        indent();
        for (ValueDescriptor v : struct.getFields()) {
            printValueDescriptor(v, struct.getValue(v.getName()), -1);
        }
        retract();
    }

    private void printArray(ValueDescriptor v, Object[] array) {
        println();
        indent();
        for (int index = 0; index < array.length; index++) {
            printValueDescriptor(v, array[index], index);
        }
        retract();
    }

    private void printValueDescriptor(ValueDescriptor vd, Object value, int index) {
        boolean arrayElement = index != -1;
        String name = arrayElement ? null : vd.getName();
        if (vd.isArray() && !arrayElement) {
            if (printBeginElement("array", name, value, index)) {
                printArray(vd, (Object[]) value);
                printIndent();
                printEndElement("array");
            }
            return;
        }
        if (!vd.getFields().isEmpty()) {
            if (printBeginElement("struct", name, value, index)) {
                printObject((RecordedObject) value);
                printIndent();
                printEndElement("struct");
            }
            return;
        }
        if (printBeginElement("value", name, value, index)) {
            printEscaped(String.valueOf(value));
            printEndElement("value");
        }
    }

    private boolean printBeginElement(String elementName, String name, Object value, int index) {
        printIndent();
        print("<", elementName);
        if (name != null) {
            printAttribute("name", name);
        }
        if (index != -1) {
            printAttribute("index", Integer.toString(index));
        }
        if (value == null) {
            print("><null/></");
            print(elementName);
            println(">");
            return false;
        }
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            printAttribute("size", Integer.toString(array.length));
        }
        print(">");
        return true;
    }

    private void printEndElement(String elementName) {
        print("</");
        print(elementName);
        println(">");
    }

    private void printEscaped(String text) {
        for (int i = 0; i < text.length(); i++) {
            printEscaped(text.charAt(i));
        }
    }

    private void printEscaped(char c) {
        if (c == 34) {
            print("&quot;");
            return;
        }
        if (c == 38) {
            print("&amp;");
            return;
        }
        if (c == 39) {
            print("&apos;");
            return;
        }
        if (c == 60) {
            print("&lt;");
            return;
        }
        if (c == 62) {
            print("&gt;");
            return;
        }
        if (c > 0x7F) {
            print("&#");
            print((int) c);
            print(';');
            return;
        }
        print(c);
    }
}
