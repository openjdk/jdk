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

package com.sun.xml.internal.ws.message;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * WS-Addressing &lt;RelatesTo> header.
 *
 * Used for outbound only.
 *
 * @author Arun Gupta
 */
public final class RelatesToHeader extends StringHeader {
    protected String type;
    private final QName typeAttributeName;

    public RelatesToHeader(QName name, String messageId, String type) {
        super(name, messageId);
        this.type = type;
        this.typeAttributeName = new QName(name.getNamespaceURI(), "type");
    }

    public RelatesToHeader(QName name, String mid) {
        super(name, mid);
        this.typeAttributeName = new QName(name.getNamespaceURI(), "type");
    }

    public String getType() {
        return type;
    }

    @Override
    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        w.writeStartElement("", name.getLocalPart(), name.getNamespaceURI());
        w.writeDefaultNamespace(name.getNamespaceURI());
        if (type != null)
            w.writeAttribute("type", type);
        w.writeCharacters(value);
        w.writeEndElement();
    }

    @Override
    public void writeTo(SOAPMessage saaj) throws SOAPException {
        SOAPHeader header = saaj.getSOAPHeader();
        if (header == null)
            header = saaj.getSOAPPart().getEnvelope().addHeader();
        SOAPHeaderElement she = header.addHeaderElement(name);

        if (type != null)
            she.addAttribute(typeAttributeName, type);
        she.addTextNode(value);
    }
}
