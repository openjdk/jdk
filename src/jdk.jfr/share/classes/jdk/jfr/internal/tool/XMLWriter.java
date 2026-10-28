/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.tool;

import java.io.PrintWriter;
import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;

final class XMLWriter extends EventPrintWriter {

    public XMLWriter(PrintWriter destination) {
        super(destination);
    }

    @Override
    protected void printBegin() {
        println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        println("<recording xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">");
        indent();
        printIndent();
        println("<events>");
        indent();
    }

    @Override
    protected void printEnd() {
        retract();
        printIndent();
        println("</events>");
        retract();
        println("</recording>");
    }

    @Override
    protected void print(List<RecordedEvent> events) {
        for (RecordedEvent event : events) {
            printEvent(event);
        }
    }

    private void printEvent(RecordedEvent event) {
        EventType type = event.getEventType();
        printIndent();
        print("<event");
        printAttribute("type", type.getName());
        print(">");
        println();
        indent();
        for (ValueDescriptor v : event.getFields()) {
            printValueDescriptor(v, getValue(event, v), -1);
        }
        retract();
        printIndent();
        println("</event>");
        println();
    }

    private void printAttribute(String name, String value) {
        print(" ");
        print(name); // Only known strings
        print("=\"");
        printEscaped(value);
        print("\"");
    }

    public void printObject(RecordedObject struct) {
        println();
        indent();
        for (ValueDescriptor v : struct.getFields()) {
            printValueDescriptor(v, getValue(struct, v), -1);
        }
        retract();
    }

    private void printArray(ValueDescriptor v, Object[] array) {
        println();
        indent();
        int depth = 0;
        for (int index = 0; index < array.length; index++) {
            Object arrayElement = array[index];
            if (!(arrayElement instanceof RecordedFrame) || depth < getStackDepth()) {
                printValueDescriptor(v, array[index], index);
            }
            depth++;
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
            printAttribute("xsi:nil", "true");
            println("/>");
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
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            printEscaped(codePoint);
            i += Character.charCount(codePoint);
        }
    }

    // Valid XML 1.0 characters are taken from https://www.w3.org/TR/xml/#charsets
    // Section 2.2
    // Char ::= #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
    private static boolean isValid(int cp) {
        if (cp == 0x09 || cp == 0x0A || cp == 0x0D) {
            return true;
        }
        if (0x20 <= cp && cp <= 0xD7FF) {
            return true;
        }
        if (0xE000 <= cp && cp <= 0xFFFD) {
            return true;
        }
        if (0x10000 <= cp && cp <= 0x10FFFF) {
            return true;
        }
        return false;
    }

    private void printEscaped(int cp) {
        if (!isValid(cp)) {
            // Not a valid XML 1.0 character. Use UTF-8 replacement character
            print(EventPrintWriter.UTF8_REPLACEMENT);
            return;
        }
        if (cp == 34) {
            print("&quot;");
            return;
        }
        if (cp == 38) {
            print("&amp;");
            return;
        }
        if (cp == 39) {
            print("&apos;");
            return;
        }
        if (cp == 60) {
            print("&lt;");
            return;
        }
        if (cp == 62) {
            print("&gt;");
            return;
        }
        if (cp > 0x7F) {
            print("&#x");
            print(Integer.toHexString(cp));
            print(';');
            return;
        }
        print((char) cp);
    }
}
