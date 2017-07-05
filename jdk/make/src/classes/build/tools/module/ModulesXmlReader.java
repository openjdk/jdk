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
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class ModulesXmlReader {

    private ModulesXmlReader() {}

    public static Set<Module> readModules(Path modulesXml)
        throws XMLStreamException, IOException
    {
        Set<Module> modules = new HashSet<>();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(modulesXml))) {
            Set<Module> mods = ModulesXmlReader.load(in);
            modules.addAll(mods);
        }
        return modules;
    }

    private static final String MODULES   = "modules";
    private static final String MODULE    = "module";
    private static final String NAME      = "name";
    private static final String DEPEND    = "depend";
    private static final String EXPORT    = "export";
    private static final String TO        = "to";
    private static final String INCLUDE   = "include";
    private static final QName  REEXPORTS = new QName("re-exports");
    private static Set<Module> load(InputStream in)
        throws XMLStreamException, IOException
    {
        Set<Module> modules = new HashSet<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLEventReader stream = factory.createXMLEventReader(in);
        Module.Builder mb = null;
        String modulename = null;
        String pkg = null;
        Set<String> permits = new HashSet<>();
        while (stream.hasNext()) {
            XMLEvent event = stream.nextEvent();
            if (event.isStartElement()) {
                String startTag = event.asStartElement().getName().getLocalPart();
                switch (startTag) {
                    case MODULES:
                        break;
                    case MODULE:
                        if (mb != null) {
                            throw new RuntimeException("end tag for module is missing");
                        }
                        modulename = getNextTag(stream, NAME);
                        mb = new Module.Builder();
                        mb.name(modulename);
                        break;
                    case NAME:
                        throw new RuntimeException(event.toString());
                    case DEPEND:
                        boolean reexports = false;
                        Attribute attr = event.asStartElement().getAttributeByName(REEXPORTS);
                        if (attr != null) {
                            String value = attr.getValue();
                            if (value.equals("true") || value.equals("false")) {
                                reexports = Boolean.parseBoolean(value);
                            } else {
                                throw new RuntimeException("unexpected attribute " + attr.toString());
                            }
                        }
                        mb.require(getData(stream), reexports);
                        break;
                    case INCLUDE:
                        throw new RuntimeException("unexpected " + event);
                    case EXPORT:
                        pkg = getNextTag(stream, NAME);
                        break;
                    case TO:
                        permits.add(getData(stream));
                        break;
                    default:
                }
            } else if (event.isEndElement()) {
                String endTag = event.asEndElement().getName().getLocalPart();
                switch (endTag) {
                    case MODULE:
                        modules.add(mb.build());
                        mb = null;
                        break;
                    case EXPORT:
                        if (pkg == null) {
                            throw new RuntimeException("export-to is malformed");
                        }
                        mb.exportTo(pkg, permits);
                        pkg = null;
                        permits.clear();
                        break;
                    default:
                }
            } else if (event.isCharacters()) {
                String s = event.asCharacters().getData();
                if (!s.trim().isEmpty()) {
                    throw new RuntimeException("export-to is malformed");
                }
            }
        }
        return modules;
    }

    private static String getData(XMLEventReader reader)
        throws XMLStreamException
    {
        XMLEvent e = reader.nextEvent();
        if (e.isCharacters())
            return e.asCharacters().getData();

        throw new RuntimeException(e.toString());
    }

    private static String getNextTag(XMLEventReader reader, String tag)
        throws XMLStreamException
    {
        XMLEvent e = reader.nextTag();
        if (e.isStartElement()) {
            String t = e.asStartElement().getName().getLocalPart();
            if (!tag.equals(t)) {
                throw new RuntimeException(e + " expected: " + tag);
            }
            return getData(reader);
        }
        throw new RuntimeException("export-to name is missing:" + e);
    }
}
