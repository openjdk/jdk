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

package com.sun.xml.internal.ws.message.stream;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.message.AbstractMessageImpl;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.NotNull;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

/**
 * {@link Message} backed by {@link XMLStreamReader} as payload
 *
 * @author Jitendra Kotamraju
 */
public class PayloadStreamReaderMessage extends AbstractMessageImpl {
    private final StreamMessage message;

    public PayloadStreamReaderMessage(XMLStreamReader reader, SOAPVersion soapVer) {
        this(null, reader,new AttachmentSetImpl(), soapVer);
    }

    public PayloadStreamReaderMessage(@Nullable HeaderList headers, @NotNull XMLStreamReader reader,
                                      @NotNull AttachmentSet attSet, @NotNull SOAPVersion soapVersion) {
        super(soapVersion);
        message = new StreamMessage(headers, attSet, reader, soapVersion);
    }

    public boolean hasHeaders() {
        return message.hasHeaders();
    }

    public HeaderList getHeaders() {
        return message.getHeaders();
    }

    public AttachmentSet getAttachments() {
        return message.getAttachments();
    }

    public String getPayloadLocalPart() {
        return message.getPayloadLocalPart();
    }

    public String getPayloadNamespaceURI() {
        return message.getPayloadNamespaceURI();
    }

    public boolean hasPayload() {
        return true;
    }

    public Source readPayloadAsSource() {
        return message.readPayloadAsSource();
    }

    public XMLStreamReader readPayload() throws XMLStreamException {
        return message.readPayload();
    }

    public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
        message.writePayloadTo(sw);
    }

    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        return (T) message.readPayloadAsJAXB(unmarshaller);
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        message.writeTo(contentHandler, errorHandler);
    }

    protected void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        message.writePayloadTo(contentHandler, errorHandler, fragment);
    }

    public Message copy() {
        return message.copy();
    }
}
