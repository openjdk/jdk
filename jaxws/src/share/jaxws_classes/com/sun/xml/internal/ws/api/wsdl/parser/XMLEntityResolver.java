/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.wsdl.parser;

import com.sun.xml.internal.ws.api.server.SDDocumentSource;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.net.URL;

/**
 * Resolves a reference to {@link XMLStreamReader}.
 *
 * This is kinda like {@link EntityResolver} but works
 * at the XML infoset level.
 *
 * @author Kohsuke Kawaguchi
 */
public interface XMLEntityResolver {
    /**
     * See {@link EntityResolver#resolveEntity(String, String)} for the contract.
     */
    Parser resolveEntity(String publicId,String systemId)
        throws SAXException, IOException, XMLStreamException;

    public static final class Parser {
        /**
         * System ID of the document being parsed.
         */
        public final URL systemId;
        /**
         * The parser instance parsing the infoset.
         */
        public final XMLStreamReader parser;

        public Parser(URL systemId, XMLStreamReader parser) {
            assert parser!=null;
            this.systemId = systemId;
            this.parser = parser;
        }

        /**
         * Creates a {@link Parser} that reads from {@link SDDocumentSource}.
         */
        public Parser(SDDocumentSource doc) throws IOException, XMLStreamException {
            this.systemId = doc.getSystemId();
            this.parser = doc.read();
        }

    }
}
