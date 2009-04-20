/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 * THIS FILE WAS MODIFIED BY SUN MICROSYSTEMS, INC.
 */


package com.sun.xml.internal.fastinfoset.tools;

import com.sun.xml.internal.fastinfoset.sax.SAXDocumentSerializer;
import java.io.InputStream;
import java.io.OutputStream;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.Reader;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

public class XML_SAX_FI extends TransformInputOutput {

    public XML_SAX_FI() {
    }

    public void parse(InputStream xml, OutputStream finf, String workingDirectory) throws Exception {
        SAXParser saxParser = getParser();
        SAXDocumentSerializer documentSerializer = getSerializer(finf);

        XMLReader reader = saxParser.getXMLReader();
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", documentSerializer);
        reader.setContentHandler(documentSerializer);

        if (workingDirectory != null) {
            reader.setEntityResolver(createRelativePathResolver(workingDirectory));
        }
        reader.parse(new InputSource(xml));
    }

    public void parse(InputStream xml, OutputStream finf) throws Exception {
        parse(xml, finf, null);
    }

    public void convert(Reader reader, OutputStream finf) throws Exception {
        InputSource is = new InputSource(reader);

        SAXParser saxParser = getParser();
        SAXDocumentSerializer documentSerializer = getSerializer(finf);

        saxParser.setProperty("http://xml.org/sax/properties/lexical-handler", documentSerializer);
        saxParser.parse(is, documentSerializer);
    }

    private SAXParser getParser() {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setNamespaceAware(true);
        try {
            return saxParserFactory.newSAXParser();
        } catch (Exception e) {
            return null;
        }
    }

    private SAXDocumentSerializer getSerializer(OutputStream finf) {
        SAXDocumentSerializer documentSerializer = new SAXDocumentSerializer();
        documentSerializer.setOutputStream(finf);
        return documentSerializer;
    }

    public static void main(String[] args) throws Exception {
        XML_SAX_FI s = new XML_SAX_FI();
        s.parse(args);
    }
}
