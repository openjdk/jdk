/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.module;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public final class ModulesXmlWriter {

    private ModulesXmlWriter() {}

    public static void writeModules(Set<Module> modules, Path path)
        throws IOException, XMLStreamException
    {
        writeXML(modules, path);
    }

    private static final String MODULES   = "modules";
    private static final String MODULE    = "module";
    private static final String NAME      = "name";
    private static final String DEPEND    = "depend";
    private static final String EXPORT    = "export";
    private static final String TO        = "to";
    private static final String INCLUDE   = "include";
    private static final QName  REEXPORTS = new QName("re-exports");

    private static void writeXML(Set<Module> modules, Path path)
        throws IOException, XMLStreamException
    {
        XMLOutputFactory xof = XMLOutputFactory.newInstance();
        try (OutputStream out = Files.newOutputStream(path)) {
            int depth = 0;
            XMLStreamWriter xtw = xof.createXMLStreamWriter(out, "UTF-8");
            xtw.writeStartDocument("utf-8","1.0");
            writeStartElement(xtw, MODULES, depth);
            modules.stream()
                   .sorted(Comparator.comparing(Module::name))
                   .forEach(m -> writeModuleElement(xtw, m, depth+1));
            writeEndElement(xtw, depth);
            xtw.writeCharacters("\n");
            xtw.writeEndDocument();
            xtw.flush();
            xtw.close();
        }
    }

    private static void writeElement(XMLStreamWriter xtw,
                                     String element,
                                     String value,
                                     int depth) {
        try {
            writeStartElement(xtw, element, depth);
            xtw.writeCharacters(value);
            xtw.writeEndElement();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeDependElement(XMLStreamWriter xtw,
                                           Module.Dependence d,
                                           int depth) {
        try {
            writeStartElement(xtw, DEPEND, depth);
            if (d.reexport) {
                xtw.writeAttribute("re-exports", "true");
            }
            xtw.writeCharacters(d.name);
            xtw.writeEndElement();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeExportElement(XMLStreamWriter xtw,
                                           String pkg,
                                           int depth) {
        writeExportElement(xtw, pkg, Collections.emptySet(), depth);
    }

    private static void writeExportElement(XMLStreamWriter xtw,
                                           String pkg,
                                           Set<String> permits,
                                           int depth) {
        try {
            writeStartElement(xtw, EXPORT, depth);
            writeElement(xtw, NAME, pkg, depth+1);
            if (!permits.isEmpty()) {
                permits.stream().sorted()
                       .forEach(m -> writeElement(xtw, TO, m, depth + 1));
            }
            writeEndElement(xtw, depth);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }
    private static void writeModuleElement(XMLStreamWriter xtw,
                                           Module m,
                                           int depth) {
        try {
            writeStartElement(xtw, MODULE, depth);
            writeElement(xtw, NAME, m.name(), depth+1);
            m.requires().stream().sorted(Comparator.comparing(d -> d.name))
                        .forEach(d -> writeDependElement(xtw, d, depth+1));
            m.exports().keySet().stream()
                       .filter(pn -> m.exports().get(pn).isEmpty())
                       .sorted()
                       .forEach(pn -> writeExportElement(xtw, pn, depth+1));
            m.exports().entrySet().stream()
                       .filter(e -> !e.getValue().isEmpty())
                       .sorted(Map.Entry.comparingByKey())
                       .forEach(e -> writeExportElement(xtw, e.getKey(), e.getValue(), depth+1));
            m.packages().stream().sorted()
                        .forEach(p -> writeElement(xtw, INCLUDE, p, depth+1));
            writeEndElement(xtw, depth);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);

        }
    }

    /** Two spaces; the default indentation. */
    public static final String DEFAULT_INDENT = "  ";

    /** stack[depth] indicates what's been written into the current scope. */
    private static String[] stack = new String[] { "\n",
        "\n" + DEFAULT_INDENT,
        "\n" + DEFAULT_INDENT + DEFAULT_INDENT,
        "\n" + DEFAULT_INDENT + DEFAULT_INDENT + DEFAULT_INDENT};

    private static void writeStartElement(XMLStreamWriter xtw,
                                          String name,
                                          int depth)
        throws XMLStreamException
    {
        xtw.writeCharacters(stack[depth]);
        xtw.writeStartElement(name);
    }

    private static void writeEndElement(XMLStreamWriter xtw, int depth)
        throws XMLStreamException
    {
        xtw.writeCharacters(stack[depth]);
        xtw.writeEndElement();
    }
}
