/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stax.StAXResult;
import jdk.jpackage.internal.IOUtils;


public final class XmlUtils {

    public static void createXml(Path dstFile, XmlConsumer xmlConsumer) throws
            IOException {
        XMLOutputFactory xmlFactory = XMLOutputFactory.newInstance();
        Files.createDirectories(IOUtils.getParent(dstFile));
        try (Writer w = Files.newBufferedWriter(dstFile)) {
            // Wrap with pretty print proxy
            XMLStreamWriter xml = (XMLStreamWriter) Proxy.newProxyInstance(XMLStreamWriter.class.getClassLoader(),
                    new Class<?>[]{XMLStreamWriter.class},
                    new PrettyPrintHandler(xmlFactory.createXMLStreamWriter(w)));
            xml.writeStartDocument();
            xmlConsumer.accept(xml);
            xml.writeEndDocument();
            xml.flush();
            xml.close();
        } catch (XMLStreamException ex) {
            throw new IOException(ex);
        } catch (IOException ex) {
            throw ex;
        }
    }

    public static void mergeXmls(XMLStreamWriter xml, Collection<Source> sources)
            throws XMLStreamException, IOException {
        xml = (XMLStreamWriter) Proxy.newProxyInstance(XMLStreamWriter.class.getClassLoader(),
                new Class<?>[]{XMLStreamWriter.class},
                new SkipDocumentHandler(xml));
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Result result = new StAXResult(xml);
            for (Source src : sources) {
                tf.newTransformer().transform(src, result);
            }
        } catch (TransformerException ex) {
            // Should never happen
            throw new RuntimeException(ex);
        }
    }

    public static DocumentBuilder initDocumentBuilder() {
        try {
            return initDocumentBuilderFactory().newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static DocumentBuilderFactory initDocumentBuilderFactory() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
        try {
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException(ex);
        }
        return dbf;
    }
}
