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
 */

package com.sun.xml.internal.ws.message;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.ws.api.message.Header;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

/**
 * {@link Header} that has a single text value in it
 * (IOW, of the form &lt;foo>text&lt;/foo>.)
 *
 * @author Arun Gupta
 */
public class StringHeader extends AbstractHeaderImpl {
    /**
     * Tag name.
     */
    protected final QName name;
    /**
     * Header value.
     */
    protected final String value;

    public StringHeader(QName name, String value) {
        assert name != null;

        this.name = name;
        this.value = value;
    }

    public @NotNull String getNamespaceURI() {
        return name.getNamespaceURI();
    }

    public @NotNull String getLocalPart() {
        return name.getLocalPart();
    }

    @Nullable public String getAttribute(@NotNull String nsUri, @NotNull String localName) {
        return null;
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        MutableXMLStreamBuffer buf = new MutableXMLStreamBuffer();
        XMLStreamWriter w = buf.createFromXMLStreamWriter();
        writeTo(w);
        return buf.readAsXMLStreamReader();
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        w.writeStartElement("", name.getLocalPart(), name.getNamespaceURI());
        w.writeDefaultNamespace(name.getNamespaceURI());
        w.writeCharacters(value);
        w.writeEndElement();
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        SOAPHeader header = saaj.getSOAPHeader();
        SOAPHeaderElement she = header.addHeaderElement(name);
        she.addTextNode(value);
    }

    public void writeTo(ContentHandler h, ErrorHandler errorHandler) throws SAXException {
        String nsUri = name.getNamespaceURI();
        String ln = name.getLocalPart();

        h.startPrefixMapping("",nsUri);
        h.startElement(nsUri,ln,ln,EMPTY_ATTS);
        h.characters(value.toCharArray(),0,value.length());
        h.endElement(nsUri,ln,ln);
    }
}
