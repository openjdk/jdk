/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.message;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.message.saaj.SAAJMessage;
import com.sun.xml.internal.ws.message.stream.StreamMessage;
import com.sun.xml.internal.ws.spi.db.XMLBridge;

/**
 * A <code>MessageWrapper</code> wraps the Message for the access through Packet.
 *
 * @author shih-chang.chen@oracle.com
 */
class MessageWrapper extends StreamMessage {

    Packet packet;
    Message delegate;
    StreamMessage streamDelegate;

    @Override
    public void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        streamDelegate.writePayloadTo(contentHandler, errorHandler, fragment);
    }

    @Override
    public String getBodyPrologue() {
        return streamDelegate.getBodyPrologue();
    }

    @Override
    public String getBodyEpilogue() {
        return streamDelegate.getBodyEpilogue();
    }

    MessageWrapper(Packet p, Message m) {
        super(m.getSOAPVersion());
        packet = p;
        delegate = m;
        streamDelegate = (m instanceof StreamMessage) ? (StreamMessage) m : null;
        setMessageMedadata(p);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    boolean  isProtocolMessage() { return delegate.isProtocolMessage(); }
    @Override
    void  setIsProtocolMessage() { delegate.setIsProtocolMessage(); }

    @Override
    public boolean hasHeaders() {
        return delegate.hasHeaders();
    }

    @Override
    public AttachmentSet getAttachments() {
        return delegate.getAttachments();
    }

    @Override
    public String toString() {
        return "{MessageWrapper: " + delegate.toString() + "}";
    }

    @Override
    public boolean isOneWay(WSDLPort port) {
        return delegate.isOneWay(port);
    }

    @Override
    public String getPayloadLocalPart() {
        return delegate.getPayloadLocalPart();
    }

    @Override
    public String getPayloadNamespaceURI() {
        return delegate.getPayloadNamespaceURI();
    }

    @Override
    public boolean hasPayload() {
        return delegate.hasPayload();
    }

    @Override
    public boolean isFault() {
        return delegate.isFault();
    }

    @Override
    public QName getFirstDetailEntryName() {
        return delegate.getFirstDetailEntryName();
    }

    @Override
    public Source readEnvelopeAsSource() {
        //TODO if (delegate instanceof SAAJMessage)
        return delegate.readEnvelopeAsSource();
    }

    @Override
    public Source readPayloadAsSource() {
        //TODO if (delegate instanceof SAAJMessage)
        return delegate.readPayloadAsSource();
    }

    @Override
    public SOAPMessage readAsSOAPMessage() throws SOAPException {
        if (!(delegate instanceof SAAJMessage)) {
            delegate = toSAAJ(packet, null);
        }
        return delegate.readAsSOAPMessage();
    }

    @Override
    public SOAPMessage readAsSOAPMessage(Packet p, boolean inbound) throws SOAPException {
        if (!(delegate instanceof SAAJMessage)) {
            delegate = toSAAJ(p, inbound);
        }
        return delegate.readAsSOAPMessage();
    }

    @Override
    public Object readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        return delegate.readPayloadAsJAXB(unmarshaller);
    }

    @Override
    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        return delegate.readPayloadAsJAXB(bridge);
    }

    @Override
    public <T> T readPayloadAsJAXB(XMLBridge<T> bridge) throws JAXBException {
        return delegate.readPayloadAsJAXB(bridge);
    }

    @Override
    public XMLStreamReader readPayload() {
        try {
            return delegate.readPayload();
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void consume() {
        delegate.consume();
    }

    @Override
    public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
        delegate.writePayloadTo(sw);
    }

    @Override
    public void writeTo(XMLStreamWriter sw) throws XMLStreamException {
        delegate.writeTo(sw);
    }

    @Override
    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler)
            throws SAXException {
        delegate.writeTo(contentHandler, errorHandler);
    }

    @Override
    public Message copy() {
        return delegate.copy().copyFrom(delegate);
    }

    @Override
    public String getID(WSBinding binding) {
        return delegate.getID(binding);
    }

    @Override
    public String getID(AddressingVersion av, SOAPVersion sv) {
        return delegate.getID(av, sv);
    }

    @Override
    public SOAPVersion getSOAPVersion() {
        return delegate.getSOAPVersion();
    }

    @Override
    public @NotNull MessageHeaders getHeaders() {
        return delegate.getHeaders();
    }

    @Override
    public void setMessageMedadata(MessageMetadata metadata) {
        super.setMessageMedadata(metadata);
        delegate.setMessageMedadata(metadata);
    }
}
