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
package com.sun.xml.internal.ws.message.saaj;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.XMLStreamException2;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.FragmentContentHandler;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.*;
import com.sun.xml.internal.ws.message.AttachmentUnmarshallerImpl;
import com.sun.xml.internal.ws.message.AbstractMessageImpl;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;
import com.sun.xml.internal.ws.streaming.DOMStreamReader;
import com.sun.xml.internal.ws.util.DOMUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * {@link Message} implementation backed by {@link SOAPMessage}.
 *
 * @author Vivek Pandey
 * @author Rama Pulavarthi
 */
public class SAAJMessage extends Message {
    // flag to switch between representations
    private boolean parsedMessage;
    // flag to check if Message API is exercised;
    private boolean accessedMessage;
    private final SOAPMessage sm;

    private HeaderList headers;
    private List<Element> bodyParts;
    private Element payload;

    private String payloadLocalName;
    private String payloadNamespace;
    private SOAPVersion soapVersion;

    public SAAJMessage(SOAPMessage sm) {
        this.sm = sm;
    }

    /**
     * This constructor is a convenience and called by the {@link #copy}
     *
     * @param headers
     * @param sm
     */
    private SAAJMessage(HeaderList headers, AttachmentSet as, SOAPMessage sm) {
        this.sm = sm;
        this.parse();
        if(headers == null)
            headers = new HeaderList();
        this.headers = headers;
        this.attachmentSet = as;
    }

    private void parse() {
        if (!parsedMessage) {
            try {
                access();
                if (headers == null)
                    headers = new HeaderList();
                SOAPHeader header = sm.getSOAPHeader();
                if (header != null) {
                    Iterator iter = header.examineAllHeaderElements();
                    while (iter.hasNext()) {
                        headers.add(new SAAJHeader((SOAPHeaderElement) iter.next()));
                    }
                }
                attachmentSet = new SAAJAttachmentSet(sm);

                parsedMessage = true;
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }
    }

    private void access() {
        if (!accessedMessage) {
            try {
                Node body = sm.getSOAPBody();
                soapVersion = SOAPVersion.fromNsUri(body.getNamespaceURI());
                //cature all the body elements
                bodyParts = DOMUtil.getChildElements(body);
                //we treat payload as the first body part
                payload = bodyParts.size() > 0 ? bodyParts.get(0) : null;
                // hope this is correct. Caching the localname and namespace of the payload should be fine
                // but what about if a Handler replaces the payload with something else? Weel, may be it
                // will be error condition anyway
                if (payload != null) {
                    payloadLocalName = payload.getLocalName();
                    payloadNamespace = payload.getNamespaceURI();
                }
                accessedMessage = true;
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }
    }

    public boolean hasHeaders() {
        parse();
        return headers.size() > 0;
    }

    public @NotNull HeaderList getHeaders() {
        parse();
        return headers;
    }
    /**
     * Gets the attachments of this message
     * (attachments live outside a message.)
     */
    @Override
    public @NotNull AttachmentSet getAttachments() {
        parse();
        return attachmentSet;
    }

    /**
     * Optimization hint for the derived class to check
     * if we may have some attachments.
     */
    @Override
    protected boolean hasAttachments() {
        parse();
        return attachmentSet!=null;
    }

    public @Nullable String getPayloadLocalPart() {
        access();
        return payloadLocalName;
    }

    public String getPayloadNamespaceURI() {
        access();
        return payloadNamespace;
    }

    public boolean hasPayload() {
        access();
        return payloadNamespace != null;
    }

    public Source readEnvelopeAsSource() {
        try {
            if (!parsedMessage) {
                SOAPEnvelope se = sm.getSOAPPart().getEnvelope();
                return new DOMSource(se);

            } else {
                SOAPMessage msg = soapVersion.saajMessageFactory.createMessage();
                SOAPBody newBody = msg.getSOAPPart().getEnvelope().getBody();
                for (Element part : bodyParts) {
                    Node n = newBody.getOwnerDocument().importNode(part, true);
                    newBody.appendChild(n);
                }
                for (Header header : headers) {
                    header.writeTo(msg);
                }
                SOAPEnvelope se = msg.getSOAPPart().getEnvelope();
                return new DOMSource(se);
            }
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    public SOAPMessage readAsSOAPMessage() throws SOAPException {
        if (!parsedMessage) {
            return sm;
        } else {
            SOAPMessage msg = soapVersion.saajMessageFactory.createMessage();
            SOAPBody newBody = msg.getSOAPPart().getEnvelope().getBody();
            for (Element part : bodyParts) {
                Node n = newBody.getOwnerDocument().importNode(part, true);
                newBody.appendChild(n);
            }
            for (Header header : headers) {
                header.writeTo(msg);
            }
            for (Attachment att : getAttachments()) {
                AttachmentPart part = msg.createAttachmentPart();
                part.setDataHandler(att.asDataHandler());
                part.setContentId('<' + att.getContentId() + '>');
                msg.addAttachmentPart(part);
            }
            msg.saveChanges();
            return msg;
        }
    }

    public Source readPayloadAsSource() {
        access();
        return (payload != null) ? new DOMSource(payload) : null;
    }

    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        access();
        if (payload != null) {
            if(hasAttachments())
                unmarshaller.setAttachmentUnmarshaller(new AttachmentUnmarshallerImpl(getAttachments()));
            return (T) unmarshaller.unmarshal(payload);

        }
        return null;
    }

    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        access();
        if (payload != null)
            return bridge.unmarshal(payload,hasAttachments()? new AttachmentUnmarshallerImpl(getAttachments()) : null);
        return null;
    }

    public XMLStreamReader readPayload() throws XMLStreamException {
        access();
        if (payload != null) {
            DOMStreamReader dss = new DOMStreamReader();
            dss.setCurrentNode(payload);
            dss.nextTag();
            assert dss.getEventType() == XMLStreamReader.START_ELEMENT;
            return dss;
        }
        return null;
    }

    public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
        access();
        try {
            for (Element part : bodyParts)
                DOMUtil.serializeNode(part, sw);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        }
    }

    public void writeTo(XMLStreamWriter writer) throws XMLStreamException {
        try {
            writer.writeStartDocument();
            if (!parsedMessage) {
                DOMUtil.serializeNode(sm.getSOAPPart().getEnvelope(), writer);
            } else {
                SOAPEnvelope env = sm.getSOAPPart().getEnvelope();
                DOMUtil.writeTagWithAttributes(env, writer);
                if (hasHeaders()) {
                    writer.writeStartElement(env.getPrefix(), "Header", env.getNamespaceURI());
                    int len = headers.size();
                    for (int i = 0; i < len; i++) {
                        headers.get(i).writeTo(writer);
                    }
                    writer.writeEndElement();
                }

                DOMUtil.serializeNode(sm.getSOAPBody(), writer);
                writer.writeEndElement();
            }
            writer.writeEndDocument();
            writer.flush();
        } catch (SOAPException ex) {
            throw new XMLStreamException2(ex);
            //for now. ask jaxws team what to do.
        }
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        String soapNsUri = soapVersion.nsUri;
        if (!parsedMessage) {
            DOMScanner ds = new DOMScanner();
            ds.setContentHandler(contentHandler);
            ds.scan(sm.getSOAPPart());
        } else {
            contentHandler.setDocumentLocator(NULL_LOCATOR);
            contentHandler.startDocument();
            contentHandler.startPrefixMapping("S", soapNsUri);
            contentHandler.startElement(soapNsUri, "Envelope", "S:Envelope", EMPTY_ATTS);
            if (hasHeaders()) {
                contentHandler.startElement(soapNsUri, "Header", "S:Header", EMPTY_ATTS);
                HeaderList headers = getHeaders();
                int len = headers.size();
                for (int i = 0; i < len; i++) {
                    // shouldn't JDK be smart enough to use array-style indexing for this foreach!?
                    headers.get(i).writeTo(contentHandler, errorHandler);
                }
                contentHandler.endElement(soapNsUri, "Header", "S:Header");
            }
            // write the body
            contentHandler.startElement(soapNsUri, "Body", "S:Body", EMPTY_ATTS);
            writePayloadTo(contentHandler, errorHandler, true);
            contentHandler.endElement(soapNsUri, "Body", "S:Body");
            contentHandler.endElement(soapNsUri, "Envelope", "S:Envelope");
        }
    }

    private void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        if(fragment)
            contentHandler = new FragmentContentHandler(contentHandler);
        DOMScanner ds = new DOMScanner();
        ds.setContentHandler(contentHandler);
        ds.scan(payload);
    }

    /**
     * Creates a copy of a {@link com.sun.xml.internal.ws.api.message.Message}.
     * <p/>
     * <p/>
     * This method creates a new {@link com.sun.xml.internal.ws.api.message.Message} whose header/payload/attachments/properties
     * are identical to this {@link com.sun.xml.internal.ws.api.message.Message}. Once created, the created {@link com.sun.xml.internal.ws.api.message.Message}
     * and the original {@link com.sun.xml.internal.ws.api.message.Message} behaves independently --- adding header/
     * attachment to one {@link com.sun.xml.internal.ws.api.message.Message} doesn't affect another {@link com.sun.xml.internal.ws.api.message.Message}
     * at all.
     * <p/>
     * <h3>Design Rationale</h3>
     * <p/>
     * Since a {@link com.sun.xml.internal.ws.api.message.Message} body is read-once, sometimes
     * (such as when you do fail-over, or WS-RM) you need to
     * create an idential copy of a {@link com.sun.xml.internal.ws.api.message.Message}.
     * <p/>
     * <p/>
     * The actual copy operation depends on the layout
     * of the data in memory, hence it's best to be done by
     * the {@link com.sun.xml.internal.ws.api.message.Message} implementation itself.
     */
    public Message copy() {
        try {
            if (!parsedMessage) {
                return new SAAJMessage(readAsSOAPMessage());
            } else {
                SOAPMessage msg = soapVersion.saajMessageFactory.createMessage();
                SOAPBody newBody = msg.getSOAPPart().getEnvelope().getBody();
                for (Element part : bodyParts) {
                    Node n = newBody.getOwnerDocument().importNode(part, true);
                    newBody.appendChild(n);
                }
                return new SAAJMessage(getHeaders(), getAttachments(), msg);
            }
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }
    private static final AttributesImpl EMPTY_ATTS = new AttributesImpl();
    private static final LocatorImpl NULL_LOCATOR = new LocatorImpl();

    private class SAAJAttachment implements Attachment {

        final AttachmentPart ap;

        public SAAJAttachment(AttachmentPart part) {
            this.ap = part;
        }

        /**
         * Content ID of the attachment. Uniquely identifies an attachment.
         */
        public String getContentId() {
            return ap.getContentId();
        }

        /**
         * Gets the MIME content-type of this attachment.
         */
        public String getContentType() {
            return ap.getContentType();
        }

        /**
         * Gets the attachment as an exact-length byte array.
         */
        public byte[] asByteArray() {
            try {
                return ap.getRawContentBytes();
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        /**
         * Gets the attachment as a {@link javax.activation.DataHandler}.
         */
        public DataHandler asDataHandler() {
            try {
                return ap.getDataHandler();
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        /**
         * Gets the attachment as a {@link javax.xml.transform.Source}.
         * Note that there's no guarantee that the attachment is actually an XML.
         */
        public Source asSource() {
            try {
                return new StreamSource(ap.getRawContent());
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        /**
         * Obtains this attachment as an {@link java.io.InputStream}.
         */
        public InputStream asInputStream() {
            try {
                return ap.getRawContent();
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        /**
         * Writes the contents of the attachment into the given stream.
         */
        public void writeTo(OutputStream os) throws IOException {
            os.write(asByteArray());
        }

        /**
         * Writes this attachment to the given {@link javax.xml.soap.SOAPMessage}.
         */
        public void writeTo(SOAPMessage saaj) {
            saaj.addAttachmentPart(ap);
        }

        AttachmentPart asAttachmentPart(){
            return ap;
        }
    }

    /**
     * {@link AttachmentSet} for SAAJ.
     *
     * SAAJ wants '&lt;' and '>' for the content ID, but {@link AttachmentSet}
     * doesn't. S this class also does the conversion between them.
     */
    private class SAAJAttachmentSet implements AttachmentSet {

        private Map<String, Attachment> attMap;
        private Iterator attIter;

        public SAAJAttachmentSet(SOAPMessage sm) {
            attIter = sm.getAttachments();
        }

        /**
         * Gets the attachment by the content ID.
         *
         * @return null
         *         if no such attachment exist.
         */
        public Attachment get(String contentId) {
            // if this is the first time then create the attachment Map
            if (attMap == null) {
                if (!attIter.hasNext())
                    return null;
                attMap = createAttachmentMap();
            }
            if(contentId.charAt(0) != '<'){
                return attMap.get('<'+contentId+'>');
            }
            return attMap.get(contentId);
        }

        public boolean isEmpty() {
            if(attMap!=null)
                return attMap.isEmpty();
            else
                return !attIter.hasNext();
        }

        /**
         * Returns an iterator over a set of elements of type T.
         *
         * @return an Iterator.
         */
        public Iterator<Attachment> iterator() {
            if (attMap == null) {
                attMap = createAttachmentMap();
            }
            return attMap.values().iterator();
        }

        private Map<String, Attachment> createAttachmentMap() {
            HashMap<String, Attachment> map = new HashMap<String, Attachment>();
            while (attIter.hasNext()) {
                AttachmentPart ap = (AttachmentPart) attIter.next();
                map.put(ap.getContentId(), new SAAJAttachment(ap));
            }
            return map;
        }

        public void add(Attachment att) {
            attMap.put('<'+att.getContentId()+'>', att);
        }
    }

}
