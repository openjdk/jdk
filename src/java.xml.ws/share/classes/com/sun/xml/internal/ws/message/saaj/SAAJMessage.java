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

package com.sun.xml.internal.ws.message.saaj;

import com.sun.istack.internal.FragmentContentHandler;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.XMLStreamException2;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.*;
import com.sun.xml.internal.ws.message.AttachmentUnmarshallerImpl;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.ws.streaming.DOMStreamReader;
import com.sun.xml.internal.ws.util.ASCIIUtility;
import com.sun.xml.internal.ws.util.DOMUtil;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import javax.activation.DataHandler;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.soap.*;
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
 * @author Rama Pulavarthi
 */
public class SAAJMessage extends Message {
    // flag to switch between representations
    private boolean parsedMessage;
    // flag to check if Message API is exercised;
    private boolean accessedMessage;
    private final SOAPMessage sm;

    private MessageHeaders headers;
    private List<Element> bodyParts;
    private Element payload;

    private String payloadLocalName;
    private String payloadNamespace;
    private SOAPVersion soapVersion;

    //Collect the attrbutes on the enclosing elements so that the same message can be reproduced without loss of any
    // valuable info
    private NamedNodeMap bodyAttrs, headerAttrs, envelopeAttrs;

    public SAAJMessage(SOAPMessage sm) {
        this.sm = sm;
    }

    /**
     * This constructor is a convenience and called by the {@link #copy}
     *
     * @param headers
     * @param sm
     */
    private SAAJMessage(MessageHeaders headers, AttachmentSet as, SOAPMessage sm, SOAPVersion version) {
        this.sm = sm;
        this.parse();
        if(headers == null)
            headers = new HeaderList(version);
        this.headers = headers;
        this.attachmentSet = as;
    }

    private void parse() {
        if (!parsedMessage) {
            try {
                access();
                if (headers == null)
                    headers = new HeaderList(getSOAPVersion());
                SOAPHeader header = sm.getSOAPHeader();
                if (header != null) {
                    headerAttrs = header.getAttributes();
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

    protected void access() {
        if (!accessedMessage) {
            try {
                envelopeAttrs = sm.getSOAPPart().getEnvelope().getAttributes();
                Node body = sm.getSOAPBody();
                bodyAttrs = body.getAttributes();
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
        return headers.hasHeaders();
    }

    public @NotNull MessageHeaders getHeaders() {
        parse();
        return headers;
    }

    /**
     * Gets the attachments of this message
     * (attachments live outside a message.)
     */
    @Override
    public @NotNull AttachmentSet getAttachments() {
        if (attachmentSet == null) attachmentSet = new SAAJAttachmentSet(sm);
        return attachmentSet;
    }

    /**
     * Optimization hint for the derived class to check
     * if we may have some attachments.
     */
    @Override
    protected boolean hasAttachments() {
        return !getAttachments().isEmpty();
    }

    public @Nullable String getPayloadLocalPart() {
        soapBodyFirstChild();
        return payloadLocalName;
    }

    public String getPayloadNamespaceURI() {
        soapBodyFirstChild();
        return payloadNamespace;
    }

    public boolean hasPayload() {
        return soapBodyFirstChild() != null;
    }

    private void addAttributes(Element e, NamedNodeMap attrs) {
        if(attrs == null)
            return;
        String elPrefix = e.getPrefix();
        for(int i=0; i < attrs.getLength();i++) {
            Attr a = (Attr)attrs.item(i);
            //check if attr is ns declaration
            if("xmlns".equals(a.getPrefix()) || "xmlns".equals(a.getLocalName())) {
                if(elPrefix == null && a.getLocalName().equals("xmlns")) {
                    // the target element has already default ns declaration, dont' override it
                    continue;
                } else if(elPrefix != null && "xmlns".equals(a.getPrefix()) && elPrefix.equals(a.getLocalName())) {
                    //dont bind the prefix to ns again, its already in the target element.
                    continue;
                }
                e.setAttributeNS(a.getNamespaceURI(),a.getName(),a.getValue());
                continue;
            }
            e.setAttributeNS(a.getNamespaceURI(),a.getName(),a.getValue());
        }
    }

    public Source readEnvelopeAsSource() {
        try {
            if (!parsedMessage) {
                SOAPEnvelope se = sm.getSOAPPart().getEnvelope();
                return new DOMSource(se);

            } else {
                                SOAPMessage msg = soapVersion.getMessageFactory().createMessage();
                addAttributes(msg.getSOAPPart().getEnvelope(),envelopeAttrs);

                SOAPBody newBody = msg.getSOAPPart().getEnvelope().getBody();
                addAttributes(newBody, bodyAttrs);
                for (Element part : bodyParts) {
                    Node n = newBody.getOwnerDocument().importNode(part, true);
                    newBody.appendChild(n);
                }
                addAttributes(msg.getSOAPHeader(),headerAttrs);
                for (Header header : headers.asList()) {
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
            SOAPMessage msg = soapVersion.getMessageFactory().createMessage();
            addAttributes(msg.getSOAPPart().getEnvelope(),envelopeAttrs);
            SOAPBody newBody = msg.getSOAPPart().getEnvelope().getBody();
            addAttributes(newBody, bodyAttrs);
            for (Element part : bodyParts) {
                Node n = newBody.getOwnerDocument().importNode(part, true);
                newBody.appendChild(n);
            }
            addAttributes(msg.getSOAPHeader(),headerAttrs);
            for (Header header : headers.asList()) {
              header.writeTo(msg);
            }
            for (Attachment att : getAttachments()) {
              AttachmentPart part = msg.createAttachmentPart();
              part.setDataHandler(att.asDataHandler());
              part.setContentId('<' + att.getContentId() + '>');
              addCustomMimeHeaders(att, part);
              msg.addAttachmentPart(part);
            }
            msg.saveChanges();
            return msg;
        }
    }

        private void addCustomMimeHeaders(Attachment att, AttachmentPart part) {
                if (att instanceof AttachmentEx) {
                        Iterator<AttachmentEx.MimeHeader> allMimeHeaders = ((AttachmentEx) att).getMimeHeaders();
                        while (allMimeHeaders.hasNext()) {
                                AttachmentEx.MimeHeader mh = allMimeHeaders.next();
                                String name = mh.getName();
                                if (!"Content-Type".equalsIgnoreCase(name)
                                                && !"Content-Id".equalsIgnoreCase(name)) {
                                        part.addMimeHeader(name, mh.getValue());
                                }
                        }
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

    /** @deprecated */
    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        access();
        if (payload != null)
            return bridge.unmarshal(payload,hasAttachments()? new AttachmentUnmarshallerImpl(getAttachments()) : null);
        return null;
    }
    public <T> T readPayloadAsJAXB(XMLBridge<T> bridge) throws JAXBException {
        access();
        if (payload != null)
            return bridge.unmarshal(payload,hasAttachments()? new AttachmentUnmarshallerImpl(getAttachments()) : null);
        return null;
    }

    public XMLStreamReader readPayload() throws XMLStreamException {
        return soapBodyFirstChildReader();
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
                    if(env.getHeader() != null) {
                        DOMUtil.writeTagWithAttributes(env.getHeader(), writer);
                    } else {
                        writer.writeStartElement(env.getPrefix(), "Header", env.getNamespaceURI());
                    }
                    for (Header h : headers.asList()) {
                        h.writeTo(writer);
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
            startPrefixMapping(contentHandler, envelopeAttrs,"S");
            contentHandler.startElement(soapNsUri, "Envelope", "S:Envelope", getAttributes(envelopeAttrs));
            if (hasHeaders()) {
                startPrefixMapping(contentHandler, headerAttrs,"S");
                contentHandler.startElement(soapNsUri, "Header", "S:Header", getAttributes(headerAttrs));
                MessageHeaders headers = getHeaders();
                for (Header h : headers.asList()) {
                    h.writeTo(contentHandler, errorHandler);
                }
                endPrefixMapping(contentHandler, headerAttrs,"S");
                contentHandler.endElement(soapNsUri, "Header", "S:Header");

            }
            startPrefixMapping(contentHandler, bodyAttrs,"S");
            // write the body
            contentHandler.startElement(soapNsUri, "Body", "S:Body", getAttributes(bodyAttrs));
            writePayloadTo(contentHandler, errorHandler, true);
            endPrefixMapping(contentHandler, bodyAttrs,"S");
            contentHandler.endElement(soapNsUri, "Body", "S:Body");
            endPrefixMapping(contentHandler, envelopeAttrs,"S");
            contentHandler.endElement(soapNsUri, "Envelope", "S:Envelope");
        }
    }
    /**
     * Gets the Attributes that are not namesapce declarations
     * @param attrs
     * @return
     */
    private AttributesImpl getAttributes(NamedNodeMap attrs) {
        AttributesImpl atts = new AttributesImpl();
        if(attrs == null)
            return EMPTY_ATTS;
        for(int i=0; i < attrs.getLength();i++) {
            Attr a = (Attr)attrs.item(i);
            //check if attr is ns declaration
            if("xmlns".equals(a.getPrefix()) || "xmlns".equals(a.getLocalName())) {
              continue;
            }
            atts.addAttribute(fixNull(a.getNamespaceURI()),a.getLocalName(),a.getName(),a.getSchemaTypeInfo().getTypeName(),a.getValue());
        }
        return atts;
    }

    /**
     * Collects the ns declarations and starts the prefix mapping, consequently the associated endPrefixMapping needs to be called.
     * @param contentHandler
     * @param attrs
     * @param excludePrefix , this is to excldue the global prefix mapping "S" used at the start
     * @throws SAXException
     */
    private void startPrefixMapping(ContentHandler contentHandler, NamedNodeMap attrs, String excludePrefix) throws SAXException {
        if(attrs == null)
            return;
        for(int i=0; i < attrs.getLength();i++) {
            Attr a = (Attr)attrs.item(i);
            //check if attr is ns declaration
            if("xmlns".equals(a.getPrefix()) || "xmlns".equals(a.getLocalName())) {
                if(!fixNull(a.getPrefix()).equals(excludePrefix)) {
                    contentHandler.startPrefixMapping(fixNull(a.getPrefix()), a.getNamespaceURI());
                }
            }
        }
    }

    private void endPrefixMapping(ContentHandler contentHandler, NamedNodeMap attrs, String excludePrefix) throws SAXException {
        if(attrs == null)
            return;
        for(int i=0; i < attrs.getLength();i++) {
            Attr a = (Attr)attrs.item(i);
            //check if attr is ns declaration
            if("xmlns".equals(a.getPrefix()) || "xmlns".equals(a.getLocalName())) {
                if(!fixNull(a.getPrefix()).equals(excludePrefix)) {
                    contentHandler.endPrefixMapping(fixNull(a.getPrefix()));
                }
            }
        }
    }

    private static String fixNull(String s) {
        if(s==null) return "";
        else        return s;
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
        Message result = null;
        try {
            access();
            if (!parsedMessage) {
                result = new SAAJMessage(readAsSOAPMessage());
            } else {
                SOAPMessage msg = soapVersion.getMessageFactory().createMessage();
                SOAPBody newBody = msg.getSOAPPart().getEnvelope().getBody();
                for (Element part : bodyParts) {
                    Node n = newBody.getOwnerDocument().importNode(part, true);
                    newBody.appendChild(n);
                }
                addAttributes(newBody, bodyAttrs);
                result = new SAAJMessage(getHeaders(), getAttachments(), msg, soapVersion);
            }
            return result.copyFrom(this);
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }
    private static final AttributesImpl EMPTY_ATTS = new AttributesImpl();
    private static final LocatorImpl NULL_LOCATOR = new LocatorImpl();

    protected static class SAAJAttachment implements AttachmentEx {

        final AttachmentPart ap;

        String contentIdNoAngleBracket;

        public SAAJAttachment(AttachmentPart part) {
            this.ap = part;
        }

        /**
         * Content ID of the attachment. Uniquely identifies an attachment.
         */
        public String getContentId() {
            if (contentIdNoAngleBracket == null) {
                contentIdNoAngleBracket = ap.getContentId();
                if (contentIdNoAngleBracket != null && contentIdNoAngleBracket.charAt(0) == '<')
                    contentIdNoAngleBracket = contentIdNoAngleBracket.substring(1, contentIdNoAngleBracket.length()-1);
            }
            return contentIdNoAngleBracket;
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
            try {
                ASCIIUtility.copyStream(ap.getRawContent(), os);
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
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

                public Iterator<MimeHeader> getMimeHeaders() {
                        final Iterator it = ap.getAllMimeHeaders();
                        return new Iterator<MimeHeader>() {
                                public boolean hasNext() {
                                        return it.hasNext();
                                }

                                public MimeHeader next() {
                                        final javax.xml.soap.MimeHeader mh = (javax.xml.soap.MimeHeader) it.next();
                                        return new MimeHeader() {
                                                public String getName() {
                                                        return mh.getName();
                                                }

                                                public String getValue() {
                                                        return mh.getValue();
                                                }
                                        };
                                }

                                public void remove() {
                                        throw new UnsupportedOperationException();
                                }
                        };
                }
    }

    /**
     * {@link AttachmentSet} for SAAJ.
     *
     * SAAJ wants '&lt;' and '>' for the content ID, but {@link AttachmentSet}
     * doesn't. S this class also does the conversion between them.
     */
    protected static class SAAJAttachmentSet implements AttachmentSet {

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

    public SOAPVersion getSOAPVersion() {
        return soapVersion;
    }

    private XMLStreamReader soapBodyFirstChildReader;

    /**
     * This allow the subclass to retain the XMLStreamReader.
     */
    protected XMLStreamReader getXMLStreamReader(SOAPElement soapElement) {
        return null;
    }

    protected XMLStreamReader createXMLStreamReader(SOAPElement soapElement) {
        DOMStreamReader dss = new DOMStreamReader();
        dss.setCurrentNode(soapElement);
        return dss;
    }

    protected XMLStreamReader soapBodyFirstChildReader() {
        if (soapBodyFirstChildReader != null) return soapBodyFirstChildReader;
        soapBodyFirstChild();
        if (soapBodyFirstChild != null) {
            soapBodyFirstChildReader = getXMLStreamReader(soapBodyFirstChild);
            if (soapBodyFirstChildReader == null) soapBodyFirstChildReader =
                createXMLStreamReader(soapBodyFirstChild);
            if (soapBodyFirstChildReader.getEventType() == XMLStreamReader.START_DOCUMENT) {
                try {
                    while(soapBodyFirstChildReader.getEventType() != XMLStreamReader.START_ELEMENT)
                        soapBodyFirstChildReader.next();
                } catch (XMLStreamException e) {
                    throw new RuntimeException(e);
                }
            }
            return soapBodyFirstChildReader;
        } else {
            payloadLocalName = null;
            payloadNamespace = null;
            return null;
        }
    }

    private SOAPElement soapBodyFirstChild;

    SOAPElement soapBodyFirstChild() {
        if (soapBodyFirstChild != null) return soapBodyFirstChild;
        try {
            boolean foundElement = false;
            for (Node n = sm.getSOAPBody().getFirstChild(); n != null && !foundElement; n = n.getNextSibling()) {
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    foundElement = true;
                    if (n instanceof SOAPElement) {
                        soapBodyFirstChild = (SOAPElement) n;
                        payloadLocalName = soapBodyFirstChild.getLocalName();
                        payloadNamespace = soapBodyFirstChild.getNamespaceURI();
                        return soapBodyFirstChild;
                    }
                }
            }
            if(foundElement) for(Iterator i = sm.getSOAPBody().getChildElements(); i.hasNext();){
                Object o = i.next();
                if (o instanceof SOAPElement) {
                    soapBodyFirstChild = (SOAPElement)o;
                    payloadLocalName = soapBodyFirstChild.getLocalName();
                    payloadNamespace = soapBodyFirstChild.getNamespaceURI();
                    return soapBodyFirstChild;
                }
            }
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
        return soapBodyFirstChild;
    }
}
