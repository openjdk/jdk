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

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.namespace.QName;

import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * @author Arun Gupta
 */
public class FaultDetailHeader extends AbstractHeaderImpl {

    private AddressingVersion av;
    private String wrapper;
    private String problemValue = null;

    public FaultDetailHeader(AddressingVersion av, String wrapper, QName problemHeader) {
        this.av = av;
        this.wrapper = wrapper;
        this.problemValue = problemHeader.toString();
    }

    public FaultDetailHeader(AddressingVersion av, String wrapper, String problemValue) {
        this.av = av;
        this.wrapper = wrapper;
        this.problemValue = problemValue;
    }

    public
    @NotNull
    String getNamespaceURI() {
        return av.nsUri;
    }

    public
    @NotNull
    String getLocalPart() {
        return av.faultDetailTag.getLocalPart();
    }

    @Nullable
    public String getAttribute(@NotNull String nsUri, @NotNull String localName) {
        return null;
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        MutableXMLStreamBuffer buf = new MutableXMLStreamBuffer();
        XMLStreamWriter w = buf.createFromXMLStreamWriter();
        writeTo(w);
        return buf.readAsXMLStreamReader();
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        w.writeStartElement("", av.faultDetailTag.getLocalPart(), av.faultDetailTag.getNamespaceURI());
        w.writeDefaultNamespace(av.nsUri);
        w.writeStartElement("", wrapper, av.nsUri);
        w.writeCharacters(problemValue);
        w.writeEndElement();
        w.writeEndElement();
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        SOAPHeader header = saaj.getSOAPHeader();
        if (header == null)
                header = saaj.getSOAPPart().getEnvelope().addHeader();
        SOAPHeaderElement she = header.addHeaderElement(av.faultDetailTag);
        she = header.addHeaderElement(new QName(av.nsUri, wrapper));
        she.addTextNode(problemValue);
    }

    public void writeTo(ContentHandler h, ErrorHandler errorHandler) throws SAXException {
        String nsUri = av.nsUri;
        String ln = av.faultDetailTag.getLocalPart();

        h.startPrefixMapping("",nsUri);
        h.startElement(nsUri,ln,ln,EMPTY_ATTS);
        h.startElement(nsUri,wrapper,wrapper,EMPTY_ATTS);
        h.characters(problemValue.toCharArray(),0,problemValue.length());
        h.endElement(nsUri,wrapper,wrapper);
        h.endElement(nsUri,ln,ln);
    }
}
