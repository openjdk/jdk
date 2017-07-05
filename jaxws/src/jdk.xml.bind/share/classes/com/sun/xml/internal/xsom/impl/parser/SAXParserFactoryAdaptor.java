/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.xsom.impl.parser;

import com.sun.xml.internal.xsom.parser.XMLParser;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderAdapter;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;


/**
 * {@link SAXParserFactory} implementation that ultimately
 * uses {@link XMLParser} to parse documents.
 *
 * @deprecated
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
public class SAXParserFactoryAdaptor extends SAXParserFactory {

    private final XMLParser parser;

    public SAXParserFactoryAdaptor( XMLParser _parser ) {
        this.parser = _parser;
    }

    public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        return new SAXParserImpl();
    }

    public void setFeature(String name, boolean value) {
        throw new UnsupportedOperationException("XSOM parser does not support JAXP features.");
    }

    public boolean getFeature(String name) {
        return false;
    }

    private class SAXParserImpl extends SAXParser
    {
        private final XMLReaderImpl reader = new XMLReaderImpl();

        /**
         * @deprecated
         */
        public org.xml.sax.Parser getParser() throws SAXException {
            return new XMLReaderAdapter(reader);
        }

        public XMLReader getXMLReader() throws SAXException {
            return reader;
        }

        public boolean isNamespaceAware() {
            return true;
        }

        public boolean isValidating() {
            return false;
        }

        public void setProperty(String name, Object value) {
        }

        public Object getProperty(String name) {
            return null;
        }
    }

    private class XMLReaderImpl extends XMLFilterImpl
    {
        public void parse(InputSource input) throws IOException, SAXException {
            parser.parse(input,this,this,this);
        }

        public void parse(String systemId) throws IOException, SAXException {
            parser.parse(new InputSource(systemId),this,this,this);
        }
    }
}
