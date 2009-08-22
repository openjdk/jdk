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
package com.sun.xml.internal.ws.message.jaxb;

import com.sun.istack.internal.FragmentContentHandler;
import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferResult;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.message.AbstractMessageImpl;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;
import com.sun.xml.internal.ws.message.RootElementSniffer;
import com.sun.xml.internal.ws.message.stream.StreamMessage;
import com.sun.xml.internal.ws.streaming.XMLStreamWriterUtil;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.streaming.MtomStreamWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.util.JAXBResult;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceException;
import java.io.OutputStream;
import java.util.Map;

/**
 * {@link Message} backed by a JAXB bean.
 *
 * @author Kohsuke Kawaguchi
 */
public final class JAXBMessage extends AbstractMessageImpl {
    private HeaderList headers;

    /**
     * The JAXB object that represents the header.
     */
    private final Object jaxbObject;

    private final AttachmentSetImpl attachmentSet;

    private final Bridge bridge;

    /**
     * Lazily sniffed payload element name
     */
    private String nsUri,localName;

    /**
     * If we have the infoset representation for the payload, this field is non-null.
     */
    private XMLStreamBuffer infoset;

    /**
     * Creates a {@link Message} backed by a JAXB bean.
     *
     * @param context
     *      The JAXBContext to be used for marshalling.
     * @param jaxbObject
     *      The JAXB object that represents the payload. must not be null. This object
     *      must be bound to an element (which means it either is a {@link JAXBElement} or
     *      an instanceof a class with {@link XmlRootElement}).
     * @param soapVersion
     *      The SOAP version of the message. Must not be null.
     */
    public static Message create(JAXBRIContext context, Object jaxbObject, SOAPVersion soapVersion) {
        if(!context.hasSwaRef()) {
            return new JAXBMessage(context,jaxbObject,soapVersion);
        }

        // If we have swaRef, then that means we might have attachments.
        // to comply with the packet API, we need to eagerly turn the JAXB object into infoset
        // to correctly find out about attachments.

        try {
            MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();

            Marshaller m = context.createMarshaller();
            AttachmentSetImpl attachments = new AttachmentSetImpl();
            AttachmentMarshallerImpl am = new AttachmentMarshallerImpl(attachments);
            m.setAttachmentMarshaller(am);
            am.cleanup();
            m.marshal(jaxbObject,xsb.createFromXMLStreamWriter());

            // any way to reuse this XMLStreamBuffer in StreamMessage?
            return new StreamMessage(null,attachments,xsb.readAsXMLStreamReader(),soapVersion);
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        }
    }

    private JAXBMessage( JAXBRIContext context, Object jaxbObject, SOAPVersion soapVer ) {
        super(soapVer);
        this.bridge = new MarshallerBridge(context);
        this.jaxbObject = jaxbObject;
        this.attachmentSet = new AttachmentSetImpl();
    }

    /**
     * Creates a {@link Message} backed by a JAXB bean.
     *
     * @param bridge
     *      Specify the payload tag name and how <tt>jaxbObject</tt> is bound.
     * @param jaxbObject
     */
    public static Message create(Bridge bridge, Object jaxbObject, SOAPVersion soapVer) {
        if(!bridge.getContext().hasSwaRef()) {
            return new JAXBMessage(bridge,jaxbObject,soapVer);
        }

        // If we have swaRef, then that means we might have attachments.
        // to comply with the packet API, we need to eagerly turn the JAXB object into infoset
        // to correctly find out about attachments.

        try {
            MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();

            AttachmentSetImpl attachments = new AttachmentSetImpl();
            AttachmentMarshallerImpl am = new AttachmentMarshallerImpl(attachments);
            bridge.marshal(jaxbObject,xsb.createFromXMLStreamWriter(), am);
            am.cleanup();

            // any way to reuse this XMLStreamBuffer in StreamMessage?
            return new StreamMessage(null,attachments,xsb.readAsXMLStreamReader(),soapVer);
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        }
    }

    private JAXBMessage(Bridge bridge, Object jaxbObject, SOAPVersion soapVer) {
        super(soapVer);
        // TODO: think about a better way to handle BridgeContext
        this.bridge = bridge;
        this.jaxbObject = jaxbObject;
        QName tagName = bridge.getTypeReference().tagName;
        this.nsUri = tagName.getNamespaceURI();
        this.localName = tagName.getLocalPart();
        this.attachmentSet = new AttachmentSetImpl();
    }

    /**
     * Copy constructor.
     */
    public JAXBMessage(JAXBMessage that) {
        super(that);
        this.headers = that.headers;
        if(this.headers!=null)
            this.headers = new HeaderList(this.headers);
        this.attachmentSet = that.attachmentSet;

        this.jaxbObject = that.jaxbObject;
        this.bridge = that.bridge;
    }

    @Override
    public @NotNull AttachmentSet getAttachments() {
        return attachmentSet;
    }

    public boolean hasHeaders() {
        return headers!=null && !headers.isEmpty();
    }

    public HeaderList getHeaders() {
        if(headers==null)
            headers = new HeaderList();
        return headers;
    }

    public String getPayloadLocalPart() {
        if(localName==null)
            sniff();
        return localName;
    }

    public String getPayloadNamespaceURI() {
        if(nsUri==null)
            sniff();
        return nsUri;
    }

    public boolean hasPayload() {
        return true;
    }

    /**
     * Obtains the tag name of the root element.
     */
    private void sniff() {
        RootElementSniffer sniffer = new RootElementSniffer(false);
        try {
            bridge.marshal(jaxbObject,sniffer);
        } catch (JAXBException e) {
            // if it's due to us aborting the processing after the first element,
            // we can safely ignore this exception.
            //
            // if it's due to error in the object, the same error will be reported
            // when the readHeader() method is used, so we don't have to report
            // an error right now.
            nsUri = sniffer.getNsUri();
            localName = sniffer.getLocalName();
        }
    }

    public Source readPayloadAsSource() {
        return new JAXBBridgeSource(bridge,jaxbObject);
    }

    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        JAXBResult out = new JAXBResult(unmarshaller);
        // since the bridge only produces fragments, we need to fire start/end document.
        try {
            out.getHandler().startDocument();
            bridge.marshal(jaxbObject,out);
            out.getHandler().endDocument();
        } catch (SAXException e) {
            throw new JAXBException(e);
        }
        return (T)out.getResult();
    }

    public XMLStreamReader readPayload() throws XMLStreamException {
       try {
            if(infoset==null) {
                XMLStreamBufferResult sbr = new XMLStreamBufferResult();
                bridge.marshal(jaxbObject,sbr);
                infoset = sbr.getXMLStreamBuffer();
            }
            XMLStreamReader reader = infoset.readAsXMLStreamReader();
            if(reader.getEventType()== START_DOCUMENT)
                XMLStreamReaderUtil.nextElementContent(reader);
            return reader;
        } catch (JAXBException e) {
           // bug 6449684, spec 4.3.4
           throw new WebServiceException(e);
        }
    }

    /**
     * Writes the payload as SAX events.
     */
    protected void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        try {
            if(fragment)
                contentHandler = new FragmentContentHandler(contentHandler);
            AttachmentMarshallerImpl am = new AttachmentMarshallerImpl(attachmentSet);
            bridge.marshal(jaxbObject,contentHandler, am);
            am.cleanup();
        } catch (JAXBException e) {
            // this is really more helpful but spec compliance
            // errorHandler.fatalError(new SAXParseException(e.getMessage(),NULL_LOCATOR,e));
            // bug 6449684, spec 4.3.4
            throw new WebServiceException(e.getMessage(),e);
        }
    }

    public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
        try {
            // MtomCodec sets its own AttachmentMarshaller
            AttachmentMarshaller am = (sw instanceof MtomStreamWriter)
                    ? ((MtomStreamWriter)sw).getAttachmentMarshaller()
                    : new AttachmentMarshallerImpl(attachmentSet);

            // Get output stream and use JAXB UTF-8 writer
            OutputStream os = XMLStreamWriterUtil.getOutputStream(sw);
            if (os != null) {
                bridge.marshal(jaxbObject, os, sw.getNamespaceContext(),am);
            } else {
                bridge.marshal(jaxbObject,sw,am);
            }
            //cleanup() is not needed since JAXB doesn't keep ref to AttachmentMarshaller
            //am.cleanup();
        } catch (JAXBException e) {
            // bug 6449684, spec 4.3.4
            throw new WebServiceException(e);
        }
    }

    public Message copy() {
        return new JAXBMessage(this);
    }
}
