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

import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.marshaller.SAX2DOMEx;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import javax.xml.soap.AttachmentPart;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.MimeHeader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.util.List;
import java.util.Map;

/**
 * Partial {@link Message} implementation.
 *
 * <p>
 * This class implements some of the {@link Message} methods.
 * The idea is that those implementations may be non-optimal but
 * it may save effort in implementing {@link Message} and reduce
 * the code size.
 *
 * <p>
 * {@link Message} classes that are used more commonly should
 * examine carefully which method can be implemented faster,
 * and override them accordingly.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractMessageImpl extends Message {
    /**
     * SOAP version of this message.
     * Used to implement some of the methods, but nothing more than that.
     *
     * <p>
     * So if you aren't using those methods that use this field,
     * this can be null.
     */
    protected final SOAPVersion soapVersion;

    protected AbstractMessageImpl(SOAPVersion soapVersion) {
        this.soapVersion = soapVersion;
    }

    /**
     * Copy constructor.
     */
    protected AbstractMessageImpl(AbstractMessageImpl that) {
        this.soapVersion = that.soapVersion;
    }

    public Source readEnvelopeAsSource() {
        return new SAXSource(new XMLReaderImpl(this), XMLReaderImpl.THE_SOURCE);
    }

    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        if(hasAttachments())
            unmarshaller.setAttachmentUnmarshaller(new AttachmentUnmarshallerImpl(getAttachments()));
        try {
            return (T) unmarshaller.unmarshal(readPayloadAsSource());
        } finally{
            unmarshaller.setAttachmentUnmarshaller(null);
        }
    }

    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        return bridge.unmarshal(readPayloadAsSource(),
            hasAttachments()? new AttachmentUnmarshallerImpl(getAttachments()) : null );
    }

    /**
     * Default implementation that relies on {@link #writePayloadTo(XMLStreamWriter)}
     */
    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        String soapNsUri = soapVersion.nsUri;
        w.writeStartDocument();
        w.writeStartElement("S","Envelope",soapNsUri);
        w.writeNamespace("S",soapNsUri);
        if(hasHeaders()) {
            w.writeStartElement("S","Header",soapNsUri);
            HeaderList headers = getHeaders();
            int len = headers.size();
            for( int i=0; i<len; i++ ) {
                headers.get(i).writeTo(w);
            }
            w.writeEndElement();
        }
        // write the body
        w.writeStartElement("S","Body",soapNsUri);

        writePayloadTo(w);

        w.writeEndElement();
        w.writeEndElement();
        w.writeEndDocument();
    }

    /**
     * Writes the whole envelope as SAX events.
     */
    public void writeTo( ContentHandler contentHandler, ErrorHandler errorHandler ) throws SAXException {
        String soapNsUri = soapVersion.nsUri;

        contentHandler.setDocumentLocator(NULL_LOCATOR);
        contentHandler.startDocument();
        contentHandler.startPrefixMapping("S",soapNsUri);
        contentHandler.startElement(soapNsUri,"Envelope","S:Envelope",EMPTY_ATTS);
        if(hasHeaders()) {
            contentHandler.startElement(soapNsUri,"Header","S:Header",EMPTY_ATTS);
            HeaderList headers = getHeaders();
            int len = headers.size();
            for( int i=0; i<len; i++ ) {
                // shouldn't JDK be smart enough to use array-style indexing for this foreach!?
                headers.get(i).writeTo(contentHandler,errorHandler);
            }
            contentHandler.endElement(soapNsUri,"Header","S:Header");
        }
        // write the body
        contentHandler.startElement(soapNsUri,"Body","S:Body",EMPTY_ATTS);
        writePayloadTo(contentHandler,errorHandler, true);
        contentHandler.endElement(soapNsUri,"Body","S:Body");
        contentHandler.endElement(soapNsUri,"Envelope","S:Envelope");
    }

    /**
     * Writes the payload to SAX events.
     *
     * @param fragment
     *      if true, this method will fire SAX events without start/endDocument events,
     *      suitable for embedding this into a bigger SAX event sequence.
     *      if false, this method generaets a completely SAX event sequence on its own.
     */
    protected abstract void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException;

    /**
     * Default implementation that uses {@link #writeTo(ContentHandler, ErrorHandler)}
     */
    public SOAPMessage readAsSOAPMessage() throws SOAPException {
        SOAPMessage msg = soapVersion.saajMessageFactory.createMessage();
        SAX2DOMEx s2d = new SAX2DOMEx(msg.getSOAPPart());
        try {
            writeTo(s2d, XmlUtil.DRACONIAN_ERROR_HANDLER);
        } catch (SAXException e) {
            throw new SOAPException(e);
        }
        for(Attachment att : getAttachments()) {
            AttachmentPart part = msg.createAttachmentPart();
            part.setDataHandler(att.asDataHandler());
            part.setContentId('<'+att.getContentId()+'>');
            msg.addAttachmentPart(part);
        }
        return msg;
    }

    /**
     *
     */
    public SOAPMessage readAsSOAPMessage(Packet packet, boolean inbound) throws SOAPException {
        SOAPMessage msg = readAsSOAPMessage();
        Map<String, List<String>> headers = null;
        String key = inbound ? Packet.INBOUND_TRANSPORT_HEADERS : Packet.OUTBOUND_TRANSPORT_HEADERS;
        if (packet.supports(key)) {
            headers = (Map<String, List<String>>)packet.get(key);
        }
        if (headers != null) {
            for(Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (!e.getKey().equalsIgnoreCase("Content-Type")) {
                    for(String value : e.getValue()) {
                        msg.getMimeHeaders().addHeader(e.getKey(), value);
                    }
                }
            }
        }
        msg.saveChanges();
        return msg;
    }


    protected static final AttributesImpl EMPTY_ATTS = new AttributesImpl();
    protected static final LocatorImpl NULL_LOCATOR = new LocatorImpl();
}
