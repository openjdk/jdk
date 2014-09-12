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
package com.sun.tools.jdeps;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.jdeps.PlatformClassPath.LegacyImageHelper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

abstract class ModulesXmlReader {
    abstract ClassFileReader getClassFileReader(String modulename, Set<String> packages)
        throws IOException;

    static class ImageReader extends ModulesXmlReader {
        final LegacyImageHelper helper;
        ImageReader(LegacyImageHelper helper) {
            this.helper = helper;
        }
        ClassFileReader getClassFileReader(String modulename, Set<String> packages)
            throws IOException
        {
            return helper.getClassReader(modulename, packages);
        }
    }

    static class ModulePathReader extends ModulesXmlReader {
        final Path mpath;
        final ClassFileReader defaultReader;
        ModulePathReader(Path mp) throws IOException {
            this.mpath = mp;
            this.defaultReader = new NonExistModuleReader(mpath);
        }
        ClassFileReader getClassFileReader(String modulename, Set<String> packages)
            throws IOException
        {
            Path mdir = mpath.resolve(modulename);
            if (Files.exists(mdir) && Files.isDirectory(mdir)) {
                return ClassFileReader.newInstance(mdir);
            } else {
                // aggregator module or os-specific module in jdeps-modules.xml
                // mdir not exist
                return defaultReader;
            }
        }
        class NonExistModuleReader extends ClassFileReader {
            private final List<ClassFile> classes = Collections.emptyList();
            private NonExistModuleReader(Path mpath) {
                super(mpath);
            }

            public ClassFile getClassFile(String name) throws IOException {
                return null;
            }
            public Iterable<ClassFile> getClassFiles() throws IOException {
                return classes;
            }
        }
    }

    public static Set<Module> load(Path mpath, InputStream in)
        throws IOException
    {
        try {
            ModulePathReader reader = new ModulePathReader(mpath);
            return reader.load(in);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<Module> loadFromImage(LegacyImageHelper helper, InputStream in)
        throws IOException
    {
        try {
            ImageReader reader = new ImageReader(helper);
            return reader.load(in);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String MODULES   = "modules";
    private static final String MODULE    = "module";
    private static final String NAME      = "name";
    private static final String DEPEND    = "depend";
    private static final String EXPORT    = "export";
    private static final String TO        = "to";
    private static final String INCLUDE   = "include";
    private static final QName  REEXPORTS = new QName("re-exports");
    public Set<Module> load(InputStream in) throws XMLStreamException, IOException {
        Set<Module> modules = new HashSet<>();
        if (in == null) {
            throw new RuntimeException("jdeps-modules.xml doesn't exist");
        }
        XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLEventReader reader = factory.createXMLEventReader(in, "UTF-8");
        Module.Builder mb = null;
        String modulename = null;
        String exportedPackage = null;
        Set<String> permits = new HashSet<>();
        while (reader.hasNext()) {
            XMLEvent event = reader.nextEvent();
            if (event.isStartElement()) {
                String startTag = event.asStartElement().getName().getLocalPart();
                switch (startTag) {
                    case MODULES:
                        break;
                    case MODULE:
                        if (mb != null) {
                            throw new RuntimeException("end tag for module is missing");
                        }
                        modulename = getNextTag(reader, NAME);
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
                        mb.require(getData(reader), reexports);
                        break;
                    case INCLUDE:
                        mb.include(getData(reader));
                        break;
                    case EXPORT:
                        exportedPackage = getNextTag(reader, NAME);
                        break;
                    case TO:
                        permits.add(getData(reader));
                        break;
                    default:
                        throw new RuntimeException("invalid element: " + event);
                }
            } else if (event.isEndElement()) {
                String endTag = event.asEndElement().getName().getLocalPart();
                switch (endTag) {
                    case MODULE:
                        ClassFileReader cfr = getClassFileReader(modulename, mb.packages);
                        mb.classes(cfr);
                        modules.add(mb.build());
                        mb = null;
                        break;
                    case EXPORT:
                        if (exportedPackage == null) {
                            throw new RuntimeException("export's name is missing");
                        }
                        mb.export(exportedPackage, permits);
                        exportedPackage = null;
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
    private String getData(XMLEventReader reader) throws XMLStreamException {
        XMLEvent e = reader.nextEvent();
        if (e.isCharacters()) {
            return e.asCharacters().getData();
        }
        throw new RuntimeException(e.toString());
    }

    private String getNextTag(XMLEventReader reader, String tag) throws XMLStreamException {
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
