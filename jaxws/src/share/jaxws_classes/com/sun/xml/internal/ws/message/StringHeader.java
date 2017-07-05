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

package com.sun.xml.internal.ws.message;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Header;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

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
 * @author Rama Pulavarthi
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

    protected boolean mustUnderstand = false;
    protected SOAPVersion soapVersion;

    public StringHeader(@NotNull QName name, @NotNull String value) {
        assert name != null;
        assert value != null;
        this.name = name;
        this.value = value;
    }

    public StringHeader(@NotNull QName name, @NotNull String value, @NotNull SOAPVersion soapVersion, boolean mustUnderstand ) {
        this.name = name;
        this.value = value;
        this.soapVersion = soapVersion;
        this.mustUnderstand = mustUnderstand;
    }

    public @NotNull String getNamespaceURI() {
        return name.getNamespaceURI();
    }

    public @NotNull String getLocalPart() {
        return name.getLocalPart();
    }

    @Nullable public String getAttribute(@NotNull String nsUri, @NotNull String localName) {
        if(mustUnderstand && soapVersion.nsUri.equals(nsUri) && MUST_UNDERSTAND.equals(localName)) {
            return getMustUnderstandLiteral(soapVersion);
        }
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
        if (mustUnderstand) {
            //Writing the ns declaration conditionally checking in the NSContext breaks XWSS. as readHeader() adds ns declaration,
            // where as writing alonf with the soap envelope does n't add it.
            //Looks like they expect the readHeader() and writeTo() produce the same infoset, Need to understand their usage

            //if(w.getNamespaceContext().getPrefix(soapVersion.nsUri) == null) {
            w.writeNamespace("S", soapVersion.nsUri);
            w.writeAttribute("S", soapVersion.nsUri, MUST_UNDERSTAND, getMustUnderstandLiteral(soapVersion));
            // } else {
            // w.writeAttribute(soapVersion.nsUri,MUST_UNDERSTAND, getMustUnderstandLiteral(soapVersion));
            // }
        }
        w.writeCharacters(value);
        w.writeEndElement();
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        SOAPHeader header = saaj.getSOAPHeader();
        if(header == null)
            header = saaj.getSOAPPart().getEnvelope().addHeader();
        SOAPHeaderElement she = header.addHeaderElement(name);
        if(mustUnderstand) {
            she.setMustUnderstand(true);
        }
        she.addTextNode(value);
    }

    public void writeTo(ContentHandler h, ErrorHandler errorHandler) throws SAXException {
        String nsUri = name.getNamespaceURI();
        String ln = name.getLocalPart();

        h.startPrefixMapping("",nsUri);
        if(mustUnderstand) {
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute(soapVersion.nsUri,MUST_UNDERSTAND,"S:"+MUST_UNDERSTAND,"CDATA", getMustUnderstandLiteral(soapVersion));
            h.startElement(nsUri,ln,ln,attributes);
        } else {
            h.startElement(nsUri,ln,ln,EMPTY_ATTS);
        }
        h.characters(value.toCharArray(),0,value.length());
        h.endElement(nsUri,ln,ln);
    }

    private static String getMustUnderstandLiteral(SOAPVersion sv) {
        if(sv == SOAPVersion.SOAP_12) {
            return S12_MUST_UNDERSTAND_TRUE;
        } else {
            return S11_MUST_UNDERSTAND_TRUE;
        }

    }

    protected static final String MUST_UNDERSTAND = "mustUnderstand";
    protected static final String S12_MUST_UNDERSTAND_TRUE ="true";
    protected static final String S11_MUST_UNDERSTAND_TRUE ="1";
}
