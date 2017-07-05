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

package com.sun.xml.internal.ws.api.message;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.spi.db.XMLBridge;

import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

/**
 * A <code>FilterMessageImpl</code> contains some other Message, which it uses
 * as its  basic source of message data, possibly transforming the data along
 * the way or providing  additional functionality.
 *
 * <p>
 * The class <code>FilterMessageImpl</code> itself simply overrides
 * all the methods of <code>Message</code> and invokes them on
 * contained Message delegate. Subclasses of <code>FilterMessageImpl</code>
 * may further override some of  these methods and may also provide
 * additional methods and fields.
 *
 * @author Jitendra Kotamraju
 */
public class FilterMessageImpl extends Message {
    private final Message delegate;

    protected FilterMessageImpl(Message delegate) {
        this.delegate = delegate;
    }

    public boolean hasHeaders() {
        return delegate.hasHeaders();
    }

    public @NotNull MessageHeaders getHeaders() {
        return delegate.getHeaders();
    }

    public @NotNull AttachmentSet getAttachments() {
        return delegate.getAttachments();
    }

    protected boolean hasAttachments() {
        return delegate.hasAttachments();
    }

    public boolean isOneWay(@NotNull WSDLPort port) {
        return delegate.isOneWay(port);
    }

    public @Nullable String getPayloadLocalPart() {
        return delegate.getPayloadLocalPart();
    }

    public String getPayloadNamespaceURI() {
        return delegate.getPayloadNamespaceURI();
    }

    public boolean hasPayload() {
        return delegate.hasPayload();
    }

    public boolean isFault() {
        return delegate.isFault();
    }

    public @Nullable QName getFirstDetailEntryName() {
        return delegate.getFirstDetailEntryName();
    }

    public Source readEnvelopeAsSource() {
        return delegate.readEnvelopeAsSource();
    }

    public Source readPayloadAsSource() {
        return delegate.readPayloadAsSource();
    }

    public SOAPMessage readAsSOAPMessage() throws SOAPException {
        return delegate.readAsSOAPMessage();
    }

    public SOAPMessage readAsSOAPMessage(Packet packet, boolean inbound) throws SOAPException {
        return delegate.readAsSOAPMessage(packet, inbound);
    }

    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        return (T)delegate.readPayloadAsJAXB(unmarshaller);
    }
    /** @deprecated */
    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        return delegate.readPayloadAsJAXB(bridge);
    }

    public <T> T readPayloadAsJAXB(XMLBridge<T> bridge) throws JAXBException {
        return delegate.readPayloadAsJAXB(bridge);
    }

    public XMLStreamReader readPayload() throws XMLStreamException {
        return delegate.readPayload();
    }

    public void consume() {
        delegate.consume();
    }

    public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
        delegate.writePayloadTo(sw);
    }

    public void writeTo(XMLStreamWriter sw) throws XMLStreamException {
        delegate.writeTo(sw);
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        delegate.writeTo(contentHandler, errorHandler);
    }

    public Message copy() {
        return delegate.copy();
    }

    public @NotNull String getID(@NotNull WSBinding binding) {
        return delegate.getID(binding);
    }

    public @NotNull String getID(AddressingVersion av, SOAPVersion sv) {
        return delegate.getID(av, sv);
    }

    public SOAPVersion getSOAPVersion() {
        return delegate.getSOAPVersion();
    }
}
