/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.io.IOException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.util.XmlUtils.XmlConsumer;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;

final class PListUtils {

    static void writeBoolean(XMLStreamWriter xml, String key, boolean value)
            throws XMLStreamException {
        writeKey(xml, key);
        xml.writeEmptyElement(Boolean.toString(value));
    }

    static void writeString(XMLStreamWriter xml, String key, Object value)
            throws XMLStreamException {
        writeKey(xml, key);
        writeString(xml, value);
    }

    static void writeStringArray(XMLStreamWriter xml, String key, Iterable<?> values)
            throws XMLStreamException, IOException {
        writeKey(xml, key);
        writeArray(xml, toXmlConsumer(() -> {
            for (var v : values) {
                writeString(xml, v);
            }
        }));
    }

    static void writeStringArray(XMLStreamWriter xml, String key, Object... values)
            throws XMLStreamException, IOException {
        writeKey(xml, key);
        writeArray(xml, toXmlConsumer(() -> {
            for (var v : values) {
                writeString(xml, v);
            }
        }));
    }

    static void writeDict(XMLStreamWriter xml, XmlConsumer content)
            throws XMLStreamException, IOException {
        writeElement(xml, "dict", content);
    }

    static void writeArray(XMLStreamWriter xml, XmlConsumer content)
            throws XMLStreamException, IOException {
        writeElement(xml, "array", content);
    }

    static void writePList(XMLStreamWriter xml, XmlConsumer content)
            throws XMLStreamException, IOException {
        xml.writeDTD("plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"https://www.apple.com/DTDs/PropertyList-1.0.dtd\"");
        xml.writeStartElement("plist");
        xml.writeAttribute("version", "1.0");
        content.accept(xml);
        xml.writeEndElement();
    }

    static void writeKey(XMLStreamWriter xml, String key)
            throws XMLStreamException {
        writeElement(xml, "key", key);
    }

    private static void writeString(XMLStreamWriter xml, Object value)
            throws XMLStreamException {
        writeElement(xml, "string", value.toString());
    }

    private static void writeElement(XMLStreamWriter xml, String name, String value)
            throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }

    private static void writeElement(XMLStreamWriter xml, String name, XmlConsumer content)
            throws XMLStreamException, IOException {
        xml.writeStartElement(name);
        content.accept(xml);
        xml.writeEndElement();
    }
}
