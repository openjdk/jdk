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

package com.sun.xml.internal.ws.message.stream;

import com.sun.istack.internal.FinalArrayList;
import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferException;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.message.AbstractHeaderImpl;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPHeader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.WebServiceException;

/**
 * Used to represent outbound header created from {@link XMLStreamBuffer}.
 *
 * <p>
 * This is optimized for outbound use, so it implements some of the methods lazily,
 * in a slow way.
 *
 * @author Kohsuke Kawaguchi
 */
public final class OutboundStreamHeader extends AbstractHeaderImpl {
    private final XMLStreamBuffer infoset;
    private final String nsUri,localName;

    /**
     * The attributes on the header element.
     * Lazily parsed.
     * Null if not parsed yet.
     */
    private FinalArrayList<Attribute> attributes;

    public OutboundStreamHeader(XMLStreamBuffer infoset, String nsUri, String localName) {
        this.infoset = infoset;
        this.nsUri = nsUri;
        this.localName = localName;
    }

    public @NotNull String getNamespaceURI() {
        return nsUri;
    }

    public @NotNull String getLocalPart() {
        return localName;
    }

    public String getAttribute(String nsUri, String localName) {
        if(attributes==null)
            parseAttributes();
        for(int i=attributes.size()-1; i>=0; i-- ) {
            Attribute a = attributes.get(i);
            if(a.localName.equals(localName) && a.nsUri.equals(nsUri))
                return a.value;
        }
        return null;
    }

    /**
     * We don't really expect this to be used, but just to satisfy
     * the {@link Header} contract.
     *
     * So this is rather slow.
     */
    private void parseAttributes() {
        try {
            XMLStreamReader reader = readHeader();

            attributes = new FinalArrayList<Attribute>();

            for (int i = 0; i < reader.getAttributeCount(); i++) {
                final String localName = reader.getAttributeLocalName(i);
                final String namespaceURI = reader.getAttributeNamespace(i);
                final String value = reader.getAttributeValue(i);

                attributes.add(new Attribute(namespaceURI,localName,value));
            }
        } catch (XMLStreamException e) {
            throw new WebServiceException("Unable to read the attributes for {"+nsUri+"}"+localName+" header",e);
        }
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        return infoset.readAsXMLStreamReader();
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        infoset.writeToXMLStreamWriter(w,true);
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        try {
            SOAPHeader header = saaj.getSOAPHeader();
            if (header == null)
                header = saaj.getSOAPPart().getEnvelope().addHeader();
            infoset.writeTo(header);
        } catch (XMLStreamBufferException e) {
            throw new SOAPException(e);
        }
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        infoset.writeTo(contentHandler,errorHandler);
    }


    /**
     * Keep the information about an attribute on the header element.
     */
    static final class Attribute {
        /**
         * Can be empty but never null.
         */
        final String nsUri;
        final String localName;
        final String value;

        public Attribute(String nsUri, String localName, String value) {
            this.nsUri = fixNull(nsUri);
            this.localName = localName;
            this.value = value;
        }

        /**
         * Convert null to "".
         */
        private static String fixNull(String s) {
            if(s==null) return "";
            else        return s;
        }
    }

    /**
     * We the performance paranoid people in the JAX-WS RI thinks
     * saving three bytes is worth while...
     */
    private static final String TRUE_VALUE = "1";
    private static final String IS_REFERENCE_PARAMETER = "IsReferenceParameter";
}
