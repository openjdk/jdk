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
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.message.AttachmentUnmarshallerImpl;
import com.sun.xml.internal.ws.streaming.DOMStreamReader;
import com.sun.xml.internal.ws.util.DOMUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

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
 */
public class SAAJMessage extends Message {
    private final SOAPMessage sm;
    private HeaderList headers;
    private String payloadLocalName;
    private String payloadNamspace;
    private List<Element> bodyParts;
    private Element payload;

    private boolean parsedHeader;

    public SAAJMessage(SOAPMessage sm) {
        this.sm = sm;

        try {
            Node body = sm.getSOAPBody();
            //cature all the body elements
            bodyParts = DOMUtil.getChildElements(body);

            //we treat payload as the first body part
            payload = bodyParts.size() > 0? bodyParts.get(0):null;
            // hope this is correct. Caching the localname and namespace of the payload should be fine
            // but what about if a Handler replaces the payload with something else? Weel, may be it
            // will be error condition anyway
            if (payload != null) {
                payloadLocalName = payload.getLocalName();
                payloadNamspace = payload.getNamespaceURI();
            }
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * This constructor is a convenience and called by the {@link #copy}
     * @param headers
     * @param sm
     */
    private SAAJMessage(HeaderList headers, AttachmentSet as, SOAPMessage sm) {
        this(sm);
        this.headers = headers;
        this.attachmentSet = as;
    }

    public boolean hasHeaders() {
        return getHeaders().size() > 0;
    }

    /**
     * Gets all the headers of this message.
     *
     * @return always return the same non-null object.
     */
    public HeaderList getHeaders() {
        if (parsedHeader)
            return headers;

        if (headers == null)
            headers = new HeaderList();

        try {
            SOAPHeader header = sm.getSOAPHeader();
            if(header!=null) {
                Iterator iter = header.examineAllHeaderElements();
                while (iter.hasNext()) {
                    headers.add(new SAAJHeader((SOAPHeaderElement) iter.next()));
                }
            }
            parsedHeader = true;
        } catch (SOAPException e) {
            e.printStackTrace();
        }
        return headers;
    }

    /**
     * Gets the attachments of this message
     * (attachments live outside a message.)
     */
    @Override
    @NotNull
    public AttachmentSet getAttachments() {
        if (attachmentSet == null)
            attachmentSet = new SAAJAttachmentSet(sm);
        return attachmentSet;
    }

    protected boolean hasAttachments() {
        return !getAttachments().isEmpty();
    }

    /**
     * Gets the local name of the payload element.
     */
    public String getPayloadLocalPart() {
        return payloadLocalName;
    }

    /**
     * Gets the namespace URI of the payload element.
     */
    public String getPayloadNamespaceURI() {
        return payloadNamspace;
    }

    public boolean hasPayload() {
        return payloadNamspace!=null;
    }

    /**
     * Consumes this message including the envelope.
     * returns it as a {@link javax.xml.transform.Source} object.
     */
    public Source readEnvelopeAsSource() {
        try {
            SOAPEnvelope se = sm .getSOAPPart().getEnvelope();
            return new DOMSource(se);
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * Returns the payload as a {@link javax.xml.transform.Source} object.
     *
     * Can't really give all the body parts inside soapenv:Body as Source
     * cant take only one part.
     *
     * <p/>
     * This consumes the message.
     */
    public Source readPayloadAsSource() {
        return (payload != null) ? new DOMSource(payload) : null;
    }

    /**
     * Creates the equivalent {@link javax.xml.soap.SOAPMessage} from this message.
     * <p/>
     * This consumes the message.
     */
    public SOAPMessage readAsSOAPMessage() {
        return sm;
    }

    public SOAPMessage readAsSOAPMessage(Packet packet, boolean inbound) throws SOAPException {
        return sm;
    }

    /**
     * Reads the payload as a JAXB object by using the given unmarshaller.
     * <p/>
     * This consumes the message.
     */
    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        try {
            Node pn = sm.getSOAPBody().getFirstChild();
            if (pn != null)
                return (T) unmarshaller.unmarshal(pn);
            return null;
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        try {
            Node pn = sm.getSOAPBody().getFirstChild();
            if (pn != null)
                return bridge.unmarshal(pn,
                    new AttachmentUnmarshallerImpl(getAttachments()));
            return null;
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * Reads the payload as a {@link javax.xml.stream.XMLStreamReader}
     * <p/>
     * This consumes the message.
     */
    public XMLStreamReader readPayload() throws XMLStreamException {
        if(payload==null)
            return null;

        DOMStreamReader dss = new DOMStreamReader();
        dss.setCurrentNode(payload);
        dss.nextTag();
        assert dss.getEventType()==XMLStreamReader.START_ELEMENT;
        return dss;
    }

    /**
     * Writes the payload to StAX.
     * <p/>
     * This method writes just the payload of the message to the writer.
     * This consumes the message.
     */
    public void writePayloadTo(XMLStreamWriter sw) {
        try {
            for(Element part: bodyParts)
                DOMUtil.serializeNode(part, sw);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        }
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        DOMScanner ds = new DOMScanner();
        ds.setContentHandler(contentHandler);
        ds.scan(sm.getSOAPPart());
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
            SOAPBody sb = sm.getSOAPPart().getEnvelope().getBody();
            SOAPMessage msg = SOAPVersion.fromNsUri(sb.getNamespaceURI()).saajMessageFactory.createMessage();
            SOAPBody newBody = msg.getSOAPPart().getEnvelope().getBody();
            for(Element part: bodyParts){
                Node n = newBody.getOwnerDocument().importNode(part, true);
                newBody.appendChild(n);
            }
            return new SAAJMessage(getHeaders(), getAttachments(), msg);
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }


    private class SAAJAttachment implements Attachment {

        AttachmentPart ap;

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


    public void writeTo( XMLStreamWriter writer ) throws XMLStreamException {
        try {
            writer.writeStartDocument();
            SOAPEnvelope env = sm.getSOAPPart().getEnvelope();
            DOMUtil.writeTagWithAttributes(env, writer);
            if(hasHeaders()) {
                writer.writeStartElement(env.getPrefix(),"Header",env.getNamespaceURI());
                int len = headers.size();
                for( int i=0; i<len; i++ ) {
                    headers.get(i).writeTo(writer);
                }
                writer.writeEndElement();
            }

            DOMUtil.serializeNode(sm.getSOAPBody(),writer);
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
        } catch (SOAPException ex) {
            throw new XMLStreamException2(ex);
            //for now. ask jaxws team what to do.
        }

    }

}
