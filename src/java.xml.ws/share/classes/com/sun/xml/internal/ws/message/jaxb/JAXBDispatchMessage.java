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

package com.sun.xml.internal.ws.message.jaxb;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.MessageHeaders;
import com.sun.xml.internal.ws.encoding.SOAPBindingCodec;
import com.sun.xml.internal.ws.message.AbstractMessageImpl;
import com.sun.xml.internal.ws.message.PayloadElementSniffer;
import com.sun.xml.internal.ws.spi.db.BindingContext;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.org.jvnet.staxex.util.MtomStreamWriter;
import com.sun.xml.internal.ws.streaming.XMLStreamWriterUtil;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceException;
import java.io.OutputStream;

/**
 * {@link Message} backed by a JAXB bean; this implementation is used when client uses
 * Dispatch mechanism in JAXB/MESSAGE mode; difference from {@link JAXBMessage} is
 * that {@code jaxbObject} holds whole SOAP message including SOAP envelope;
 * it's the client who is responsible for preparing message content.
 *
 * @author Miroslav Kos (miroslav.kos at oracle.com)
 */
public class JAXBDispatchMessage extends AbstractMessageImpl {

    private final Object jaxbObject;

    private final XMLBridge bridge;

    /**
     * For the use case of a user-supplied JAXB context that is not
     * a known JAXB type, as when creating a Disaptch object with a
     * JAXB object parameter, we will marshal and unmarshal directly with
     * the context object, as there is no Bond available.  In this case,
     * swaRef is not supported.
     */
    private final JAXBContext rawContext;

    /**
     * Lazily sniffed payload element name
     */
    private QName payloadQName;

    /**
     * Copy constructor.
     */
    private JAXBDispatchMessage(JAXBDispatchMessage that) {
        super(that);
        jaxbObject = that.jaxbObject;
        rawContext = that.rawContext;
        bridge = that.bridge;
        copyFrom(that);
    }

    public JAXBDispatchMessage(JAXBContext rawContext, Object jaxbObject, SOAPVersion soapVersion) {
        super(soapVersion);
        this.bridge = null;
        this.rawContext = rawContext;
        this.jaxbObject = jaxbObject;
    }

    public JAXBDispatchMessage(BindingContext context, Object jaxbObject, SOAPVersion soapVersion) {
        super(soapVersion);
        this.bridge = context.createFragmentBridge();
        this.rawContext = null;
        this.jaxbObject = jaxbObject;
    }

    @Override
    protected void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasHeaders() {
        return false;
    }

    @Override
    public MessageHeaders getHeaders() {
        return null;
    }

    @Override
    public String getPayloadLocalPart() {
        if (payloadQName == null) {
            readPayloadElement();
        }
        return payloadQName.getLocalPart();
    }

    @Override
    public String getPayloadNamespaceURI() {
        if (payloadQName == null) {
            readPayloadElement();
        }
        return payloadQName.getNamespaceURI();
    }

    private void readPayloadElement() {
        PayloadElementSniffer sniffer = new PayloadElementSniffer();
        try {
            if (rawContext != null) {
                Marshaller m = rawContext.createMarshaller();
                m.setProperty("jaxb.fragment", Boolean.FALSE);
                m.marshal(jaxbObject, sniffer);
            } else {
                bridge.marshal(jaxbObject, sniffer, null);
            }

        } catch (JAXBException e) {
            // if it's due to us aborting the processing after the first element,
            // we can safely ignore this exception.
            //
            // if it's due to error in the object, the same error will be reported
            // when the readHeader() method is used, so we don't have to report
            // an error right now.
            payloadQName = sniffer.getPayloadQName();
        }
    }

    @Override
    public boolean hasPayload() {
        return true;
    }

    @Override
    public Source readPayloadAsSource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public XMLStreamReader readPayload() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Message copy() {
        return new JAXBDispatchMessage(this).copyFrom(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void writeTo(XMLStreamWriter sw) throws XMLStreamException {
        try {
            // MtomCodec sets its own AttachmentMarshaller
            AttachmentMarshaller am = (sw instanceof MtomStreamWriter)
                    ? ((MtomStreamWriter) sw).getAttachmentMarshaller()
                    : new AttachmentMarshallerImpl(attachmentSet);

            // Get the encoding of the writer
            String encoding = XMLStreamWriterUtil.getEncoding(sw);

            // Get output stream and use JAXB UTF-8 writer
            OutputStream os = bridge.supportOutputStream() ? XMLStreamWriterUtil.getOutputStream(sw) : null;
            if (rawContext != null) {
                Marshaller m = rawContext.createMarshaller();
                m.setProperty("jaxb.fragment", Boolean.FALSE);
                m.setAttachmentMarshaller(am);
                if (os != null) {
                    m.marshal(jaxbObject, os);
                } else {
                    m.marshal(jaxbObject, sw);
                }

            } else {

                if (os != null && encoding != null && encoding.equalsIgnoreCase(SOAPBindingCodec.UTF8_ENCODING)) {
                    bridge.marshal(jaxbObject, os, sw.getNamespaceContext(), am);
                } else {
                    bridge.marshal(jaxbObject, sw, am);
                }
            }
            //cleanup() is not needed since JAXB doesn't keep ref to AttachmentMarshaller
        } catch (JAXBException e) {
            // bug 6449684, spec 4.3.4
            throw new WebServiceException(e);
        }
    }
}
