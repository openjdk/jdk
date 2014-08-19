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

package com.sun.xml.internal.ws.message.stream;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.XMLStreamReaderToContentHandler;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferMark;
import com.sun.xml.internal.stream.buffer.stax.StreamReaderBufferCreator;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.MessageHeaders;
import com.sun.xml.internal.ws.api.message.StreamingSOAP;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.encoding.TagInfoset;
import com.sun.xml.internal.ws.message.AbstractMessageImpl;
import com.sun.xml.internal.ws.message.AttachmentUnmarshallerImpl;
import com.sun.xml.internal.ws.protocol.soap.VersionMismatchException;
import com.sun.xml.internal.ws.spi.db.XMLBridge;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.xml.DummyLocation;
import com.sun.xml.internal.ws.util.xml.StAXSource;
import com.sun.xml.internal.ws.util.xml.XMLReaderComposite;
import com.sun.xml.internal.org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;
import com.sun.xml.internal.ws.util.xml.XMLReaderComposite.ElemInfo;

import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.*;

import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link Message} implementation backed by {@link XMLStreamReader}.
 *
 * TODO: we need another message class that keeps {@link XMLStreamReader} that points
 * at the start of the envelope element.
 */
public class StreamMessage extends AbstractMessageImpl implements StreamingSOAP {
    /**
     * The reader will be positioned at
     * the first child of the SOAP body
     */
    private @NotNull XMLStreamReader reader;

    // lazily created
    private @Nullable MessageHeaders headers;

    /**
     * Because the StreamMessage leaves out the white spaces around payload
     * when being instantiated the space characters between soap:Body opening and
     * payload is stored in this field to be reused later (necessary for message security);
     * Instantiated after StreamMessage creation
     */
    private String bodyPrologue = null;

    /**
     * instantiated after writing message to XMLStreamWriter
     */
    private String bodyEpilogue = null;

    private String payloadLocalName;

    private String payloadNamespaceURI;

    /**
     * Used only for debugging. This records where the message was consumed.
     */
    private Throwable consumedAt;

    private XMLStreamReader envelopeReader;

    public StreamMessage(SOAPVersion v) {
        super(v);
        payloadLocalName = null;
        payloadNamespaceURI = null;
    }

    public StreamMessage(SOAPVersion v, @NotNull XMLStreamReader envelope, @NotNull AttachmentSet attachments) {
        super(v);
        envelopeReader = envelope;
        attachmentSet = attachments;
    }

    public XMLStreamReader readEnvelope() {
        if (envelopeReader == null) {
            List<XMLStreamReader> hReaders = new java.util.ArrayList<XMLStreamReader>();
            ElemInfo envElem =  new ElemInfo(envelopeTag, null);
            ElemInfo hdrElem =  (headerTag != null) ? new ElemInfo(headerTag, envElem) : null;
            ElemInfo bdyElem =  new ElemInfo(bodyTag,   envElem);
            for (Header h : getHeaders().asList()) {
                try {
                    hReaders.add(h.readHeader());
                } catch (XMLStreamException e) {
                    throw new RuntimeException(e);
                }
            }
            XMLStreamReader soapHeader = (hdrElem != null) ? new XMLReaderComposite(hdrElem, hReaders.toArray(new XMLStreamReader[hReaders.size()])) : null;
            XMLStreamReader[] payload = {readPayload()};
            XMLStreamReader soapBody = new XMLReaderComposite(bdyElem, payload);
            XMLStreamReader[] soapContent = (soapHeader != null) ? new XMLStreamReader[]{soapHeader, soapBody} : new XMLStreamReader[]{soapBody};
            return new XMLReaderComposite(envElem, soapContent);
        }
        return envelopeReader;
    }

    /**
     * Creates a {@link StreamMessage} from a {@link XMLStreamReader}
     * that points at the start element of the payload, and headers.
     *
     * <p>
     * This method creates a {@link Message} from a payload.
     *
     * @param headers
     *      if null, it means no headers. if non-null,
     *      it will be owned by this message.
     * @param reader
     *      points at the start element/document of the payload (or the end element of the &lt;s:Body>
     *      if there's no payload)
     */
    public StreamMessage(@Nullable MessageHeaders headers, @NotNull AttachmentSet attachmentSet, @NotNull XMLStreamReader reader, @NotNull SOAPVersion soapVersion) {
        super(soapVersion);
        init(headers, attachmentSet, reader, soapVersion);
    }

    private void init(@Nullable MessageHeaders headers, @NotNull AttachmentSet attachmentSet, @NotNull XMLStreamReader reader, @NotNull SOAPVersion soapVersion) {
        this.headers = headers;
        this.attachmentSet = attachmentSet;
        this.reader = reader;

        if(reader.getEventType()== START_DOCUMENT)
            XMLStreamReaderUtil.nextElementContent(reader);

        //if the reader is pointing to the end element </soapenv:Body> then its empty message
        // or no payload
        if(reader.getEventType() == XMLStreamConstants.END_ELEMENT){
            String body = reader.getLocalName();
            String nsUri = reader.getNamespaceURI();
            assert body != null;
            assert nsUri != null;
            //if its not soapenv:Body then throw exception, we received malformed stream
            if(body.equals("Body") && nsUri.equals(soapVersion.nsUri)){
                this.payloadLocalName = null;
                this.payloadNamespaceURI = null;
            }else{ //TODO: i18n and also we should be throwing better message that this
                throw new WebServiceException("Malformed stream: {"+nsUri+"}"+body);
            }
        }else{
            this.payloadLocalName = reader.getLocalName();
            this.payloadNamespaceURI = reader.getNamespaceURI();
        }

        // use the default infoset representation for headers
        int base = soapVersion.ordinal()*3;
        this.envelopeTag = DEFAULT_TAGS.get(base);
        this.headerTag = DEFAULT_TAGS.get(base+1);
        this.bodyTag = DEFAULT_TAGS.get(base+2);
    }

    /**
     * Creates a {@link StreamMessage} from a {@link XMLStreamReader}
     * and the complete infoset of the SOAP envelope.
     *
     * <p>
     * See {@link #StreamMessage(MessageHeaders, AttachmentSet, XMLStreamReader, SOAPVersion)} for
     * the description of the basic parameters.
     *
     * @param headerTag
     *      Null if the message didn't have a header tag.
     *
     */
    public StreamMessage(@NotNull TagInfoset envelopeTag, @Nullable TagInfoset headerTag, @NotNull AttachmentSet attachmentSet, @Nullable MessageHeaders headers, @NotNull TagInfoset bodyTag, @NotNull XMLStreamReader reader, @NotNull SOAPVersion soapVersion) {
        this(envelopeTag, headerTag, attachmentSet, headers, null, bodyTag, null, reader, soapVersion);
    }

    public StreamMessage(@NotNull TagInfoset envelopeTag, @Nullable TagInfoset headerTag, @NotNull AttachmentSet attachmentSet, @Nullable MessageHeaders headers, @Nullable String bodyPrologue, @NotNull TagInfoset bodyTag, @Nullable String bodyEpilogue, @NotNull XMLStreamReader reader, @NotNull SOAPVersion soapVersion) {
        super(soapVersion);
        init(envelopeTag, headerTag, attachmentSet, headers, bodyPrologue, bodyTag, bodyEpilogue, reader, soapVersion);
    }

    private void init(@NotNull TagInfoset envelopeTag, @Nullable TagInfoset headerTag, @NotNull AttachmentSet attachmentSet, @Nullable MessageHeaders headers, @Nullable String bodyPrologue, @NotNull TagInfoset bodyTag, @Nullable String bodyEpilogue, @NotNull XMLStreamReader reader, @NotNull SOAPVersion soapVersion) {
        init(headers,attachmentSet,reader,soapVersion);
        if(envelopeTag == null ) {
            throw new IllegalArgumentException("EnvelopeTag TagInfoset cannot be null");
        }
        if(bodyTag == null ) {
            throw new IllegalArgumentException("BodyTag TagInfoset cannot be null");
        }
        this.envelopeTag = envelopeTag;
        this.headerTag = headerTag;
        this.bodyTag = bodyTag;
        this.bodyPrologue = bodyPrologue;
        this.bodyEpilogue = bodyEpilogue;
    }

    public boolean hasHeaders() {
        if ( envelopeReader != null ) readEnvelope(this);
        return headers!=null && headers.hasHeaders();
    }

    public MessageHeaders getHeaders() {
        if ( envelopeReader != null ) readEnvelope(this);
        if (headers == null) {
            headers = new HeaderList(getSOAPVersion());
        }
        return headers;
    }

    public String getPayloadLocalPart() {
        if ( envelopeReader != null ) readEnvelope(this);
        return payloadLocalName;
    }

    public String getPayloadNamespaceURI() {
        if ( envelopeReader != null ) readEnvelope(this);
        return payloadNamespaceURI;
    }

    public boolean hasPayload() {
        if ( envelopeReader != null ) readEnvelope(this);
        return payloadLocalName!=null;
    }

    public Source readPayloadAsSource() {
        if(hasPayload()) {
            assert unconsumed();
            return new StAXSource(reader, true, getInscopeNamespaces());
        } else
            return null;
    }

    /**
     * There is no way to enumerate inscope namespaces for XMLStreamReader. That means
     * namespaces declared in envelope, and body tags need to be computed using their
     * {@link TagInfoset}s.
     *
     * @return array of the even length of the form { prefix0, uri0, prefix1, uri1, ... }
     */
    private String[] getInscopeNamespaces() {
        NamespaceSupport nss = new NamespaceSupport();

        nss.pushContext();
        for(int i=0; i < envelopeTag.ns.length; i+=2) {
            nss.declarePrefix(envelopeTag.ns[i], envelopeTag.ns[i+1]);
        }

        nss.pushContext();
        for(int i=0; i < bodyTag.ns.length; i+=2) {
            nss.declarePrefix(bodyTag.ns[i], bodyTag.ns[i+1]);
        }

        List<String> inscope = new ArrayList<String>();
        for( Enumeration en = nss.getPrefixes(); en.hasMoreElements(); ) {
            String prefix = (String)en.nextElement();
            inscope.add(prefix);
            inscope.add(nss.getURI(prefix));
        }
        return inscope.toArray(new String[inscope.size()]);
    }

    public Object readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        if(!hasPayload())
            return null;
        assert unconsumed();
        // TODO: How can the unmarshaller process this as a fragment?
        if(hasAttachments())
            unmarshaller.setAttachmentUnmarshaller(new AttachmentUnmarshallerImpl(getAttachments()));
        try {
            return unmarshaller.unmarshal(reader);
        } finally{
            unmarshaller.setAttachmentUnmarshaller(null);
            XMLStreamReaderUtil.readRest(reader);
            XMLStreamReaderUtil.close(reader);
            XMLStreamReaderFactory.recycle(reader);
        }
    }
    /** @deprecated */
    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        if(!hasPayload())
            return null;
        assert unconsumed();
        T r = bridge.unmarshal(reader,
            hasAttachments() ? new AttachmentUnmarshallerImpl(getAttachments()) : null);
        XMLStreamReaderUtil.readRest(reader);
        XMLStreamReaderUtil.close(reader);
        XMLStreamReaderFactory.recycle(reader);
        return r;
    }

    public <T> T readPayloadAsJAXB(XMLBridge<T> bridge) throws JAXBException {
        if(!hasPayload())
            return null;
        assert unconsumed();
        T r = bridge.unmarshal(reader,
            hasAttachments() ? new AttachmentUnmarshallerImpl(getAttachments()) : null);
        XMLStreamReaderUtil.readRest(reader);
        XMLStreamReaderUtil.close(reader);
        XMLStreamReaderFactory.recycle(reader);
        return r;
    }

    @Override
    public void consume() {
        assert unconsumed();
        XMLStreamReaderUtil.readRest(reader);
        XMLStreamReaderUtil.close(reader);
        XMLStreamReaderFactory.recycle(reader);
    }

    public XMLStreamReader readPayload() {
        if(!hasPayload())
            return null;
        // TODO: What about access at and beyond </soap:Body>
        assert unconsumed();
        return this.reader;
    }

    public void writePayloadTo(XMLStreamWriter writer)throws XMLStreamException {
        if ( envelopeReader != null ) readEnvelope(this);
        assert unconsumed();

        if(payloadLocalName==null) {
            return; // no body
        }

        if (bodyPrologue != null) {
            writer.writeCharacters(bodyPrologue);
        }

        XMLStreamReaderToXMLStreamWriter conv = new XMLStreamReaderToXMLStreamWriter();

        while(reader.getEventType() != XMLStreamConstants.END_DOCUMENT){
            String name = reader.getLocalName();
            String nsUri = reader.getNamespaceURI();

            // After previous conv.bridge() call the cursor will be at END_ELEMENT.
            // Check if its not soapenv:Body then move to next ELEMENT
            if(reader.getEventType() == XMLStreamConstants.END_ELEMENT){

                if (!isBodyElement(name, nsUri)){
                    // closing payload element: store epilogue for further signing, if applicable
                    // however if there more than one payloads exist - the last one is stored
                    String whiteSpaces = XMLStreamReaderUtil.nextWhiteSpaceContent(reader);
                    if (whiteSpaces != null) {
                        this.bodyEpilogue = whiteSpaces;
                        // write it to the message too
                        writer.writeCharacters(whiteSpaces);
                    }
                } else {
                    // body closed > exit
                    break;
                }

            } else {
                // payload opening element: copy payload to writer
                conv.bridge(reader,writer);
            }
        }

        XMLStreamReaderUtil.readRest(reader);
        XMLStreamReaderUtil.close(reader);
        XMLStreamReaderFactory.recycle(reader);
    }

    private boolean isBodyElement(String name, String nsUri) {
        return name.equals("Body") && nsUri.equals(soapVersion.nsUri);
    }

    public void writeTo(XMLStreamWriter sw) throws XMLStreamException{
        if ( envelopeReader != null ) readEnvelope(this);
        writeEnvelope(sw);
    }

    public void writeToBodyStart(XMLStreamWriter writer) throws XMLStreamException {
        if ( envelopeReader != null ) readEnvelope(this);
        writer.writeStartDocument();
        envelopeTag.writeStart(writer);

        //write headers
        MessageHeaders hl = getHeaders();
        if (hl.hasHeaders() && headerTag == null) headerTag = new TagInfoset(envelopeTag.nsUri,"Header",envelopeTag.prefix,EMPTY_ATTS);
        if (headerTag != null) {
            headerTag.writeStart(writer);
            if (hl.hasHeaders()){
                for(Header h : hl.asList()){
                    h.writeTo(writer);
                }
            }
            writer.writeEndElement();
        }
        bodyTag.writeStart(writer);

    }

    /**
     * This method should be called when the StreamMessage is created with a payload
     * @param writer
     */
    private void writeEnvelope(XMLStreamWriter writer) throws XMLStreamException {
        writeToBodyStart(writer);
        if(hasPayload())
            writePayloadTo(writer);
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    public void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        if ( envelopeReader != null ) readEnvelope(this);
        assert unconsumed();

        try {
            if(payloadLocalName==null)
                return; // no body

            if (bodyPrologue != null) {
                char[] chars = bodyPrologue.toCharArray();
                contentHandler.characters(chars, 0, chars.length);
            }

            XMLStreamReaderToContentHandler conv = new XMLStreamReaderToContentHandler(reader,contentHandler,true,fragment,getInscopeNamespaces());

            while(reader.getEventType() != XMLStreamConstants.END_DOCUMENT){
                String name = reader.getLocalName();
                String nsUri = reader.getNamespaceURI();

                // After previous conv.bridge() call the cursor will be at END_ELEMENT.
                // Check if its not soapenv:Body then move to next ELEMENT
                if(reader.getEventType() == XMLStreamConstants.END_ELEMENT){

                    if (!isBodyElement(name, nsUri)){
                        // closing payload element: store epilogue for further signing, if applicable
                        // however if there more than one payloads exist - the last one is stored
                        String whiteSpaces = XMLStreamReaderUtil.nextWhiteSpaceContent(reader);
                        if (whiteSpaces != null) {
                            this.bodyEpilogue = whiteSpaces;
                            // write it to the message too
                            char[] chars = whiteSpaces.toCharArray();
                            contentHandler.characters(chars, 0, chars.length);
                        }
                    } else {
                        // body closed > exit
                        break;
                    }

                } else {
                    // payload opening element: copy payload to writer
                    conv.bridge();
                }
            }
            XMLStreamReaderUtil.readRest(reader);
            XMLStreamReaderUtil.close(reader);
            XMLStreamReaderFactory.recycle(reader);
        } catch (XMLStreamException e) {
            Location loc = e.getLocation();
            if(loc==null)   loc = DummyLocation.INSTANCE;

            SAXParseException x = new SAXParseException(
                e.getMessage(),loc.getPublicId(),loc.getSystemId(),loc.getLineNumber(),loc.getColumnNumber(),e);
            errorHandler.error(x);
        }
    }

    // TODO: this method should be probably rewritten to respect spaces between elements; is it used at all?
    @Override
    public Message copy() {
        if ( envelopeReader != null ) readEnvelope(this);
        try {
            assert unconsumed();
            consumedAt = null; // but we don't want to mark it as consumed
            MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();
            StreamReaderBufferCreator c = new StreamReaderBufferCreator(xsb);

            // preserving inscope namespaces from envelope, and body. Other option
            // would be to create a filtering XMLStreamReader from reader+envelopeTag+bodyTag
            c.storeElement(envelopeTag.nsUri, envelopeTag.localName, envelopeTag.prefix, envelopeTag.ns);
            c.storeElement(bodyTag.nsUri, bodyTag.localName, bodyTag.prefix, bodyTag.ns);

            if (hasPayload()) {
                // Loop all the way for multi payload case
                while(reader.getEventType() != XMLStreamConstants.END_DOCUMENT){
                    String name = reader.getLocalName();
                    String nsUri = reader.getNamespaceURI();
                    if(isBodyElement(name, nsUri) || (reader.getEventType() == XMLStreamConstants.END_DOCUMENT))
                        break;
                    c.create(reader);

                    // Skip whitespaces in between payload and </Body> or between elements
                    // those won't be in the message itself, but we store them in field bodyEpilogue
                    if (reader.isWhiteSpace()) {
                        bodyEpilogue = XMLStreamReaderUtil.currentWhiteSpaceContent(reader);
                    } else {
                        // clear it in case the existing was not the last one
                        // (we are interested only in the last one?)
                        bodyEpilogue = null;
                    }
                }
            }
            c.storeEndElement();        // create structure element for </Body>
            c.storeEndElement();        // create structure element for </Envelope>
            c.storeEndElement();        // create structure element for END_DOCUMENT

            XMLStreamReaderUtil.readRest(reader);
            XMLStreamReaderUtil.close(reader);
            XMLStreamReaderFactory.recycle(reader);

            reader = xsb.readAsXMLStreamReader();
            XMLStreamReader clone = xsb.readAsXMLStreamReader();

            // advance to the start tag of the <Body> first child element
            proceedToRootElement(reader);
            proceedToRootElement(clone);

            return new StreamMessage(envelopeTag, headerTag, attachmentSet, HeaderList.copy(headers), bodyPrologue, bodyTag, bodyEpilogue, clone, soapVersion).copyFrom(this);
        } catch (XMLStreamException e) {
            throw new WebServiceException("Failed to copy a message",e);
        }
    }

    private void proceedToRootElement(XMLStreamReader xsr) throws XMLStreamException {
        assert xsr.getEventType()==START_DOCUMENT;
        xsr.nextTag();
        xsr.nextTag();
        xsr.nextTag();
        assert xsr.getEventType()==START_ELEMENT || xsr.getEventType()==END_ELEMENT;
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler ) throws SAXException {
        if ( envelopeReader != null ) readEnvelope(this);
        contentHandler.setDocumentLocator(NULL_LOCATOR);
        contentHandler.startDocument();
        envelopeTag.writeStart(contentHandler);
        if (hasHeaders() && headerTag == null) headerTag = new TagInfoset(envelopeTag.nsUri,"Header",envelopeTag.prefix,EMPTY_ATTS);
        if (headerTag != null) {
            headerTag.writeStart(contentHandler);
            if (hasHeaders()) {
                MessageHeaders headers = getHeaders();
                for (Header h : headers.asList()) {
                    // shouldn't JDK be smart enough to use array-style indexing for this foreach!?
                    h.writeTo(contentHandler,errorHandler);
                }
            }
            headerTag.writeEnd(contentHandler);
        }
        bodyTag.writeStart(contentHandler);
        writePayloadTo(contentHandler,errorHandler, true);
        bodyTag.writeEnd(contentHandler);
        envelopeTag.writeEnd(contentHandler);
        contentHandler.endDocument();
    }

    /**
     * Used for an assertion. Returns true when the message is unconsumed,
     * or otherwise throw an exception.
     *
     * <p>
     * Calling this method also marks the stream as 'consumed'
     */
    private boolean unconsumed() {
        if(payloadLocalName==null)
            return true;    // no payload. can be consumed multiple times.

        if(reader.getEventType()!=XMLStreamReader.START_ELEMENT) {
            AssertionError error = new AssertionError("StreamMessage has been already consumed. See the nested exception for where it's consumed");
            error.initCause(consumedAt);
            throw error;
        }
        consumedAt = new Exception().fillInStackTrace();
        return true;
    }

    public String getBodyPrologue() {
        if ( envelopeReader != null ) readEnvelope(this);
        return bodyPrologue;
    }

    public String getBodyEpilogue() {
        if ( envelopeReader != null ) readEnvelope(this);
        return bodyEpilogue;
    }

    public XMLStreamReader getReader() {
        if ( envelopeReader != null ) readEnvelope(this);
        assert unconsumed();
        return reader;
    }


    private static final String SOAP_ENVELOPE = "Envelope";
    private static final String SOAP_HEADER = "Header";
    private static final String SOAP_BODY = "Body";

    protected interface StreamHeaderDecoder {
        public Header decodeHeader(XMLStreamReader reader, XMLStreamBuffer mark);
    }

    static final StreamHeaderDecoder SOAP12StreamHeaderDecoder = new StreamHeaderDecoder() {
        @Override
        public Header decodeHeader(XMLStreamReader reader, XMLStreamBuffer mark) {
            return new StreamHeader12(reader, mark);
        }
    };

    static final StreamHeaderDecoder SOAP11StreamHeaderDecoder = new StreamHeaderDecoder() {
        @Override
        public Header decodeHeader(XMLStreamReader reader, XMLStreamBuffer mark) {
            return new StreamHeader11(reader, mark);
        }
    };

    static private void readEnvelope(StreamMessage message) {
        if ( message.envelopeReader == null ) return;
        XMLStreamReader reader = message.envelopeReader;
        message.envelopeReader = null;
        SOAPVersion soapVersion = message.soapVersion;
        // Move to soap:Envelope and verify
        if(reader.getEventType()!=XMLStreamConstants.START_ELEMENT)
            XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader,XMLStreamConstants.START_ELEMENT);
        if (SOAP_ENVELOPE.equals(reader.getLocalName()) && !soapVersion.nsUri.equals(reader.getNamespaceURI())) {
            throw new VersionMismatchException(soapVersion, soapVersion.nsUri, reader.getNamespaceURI());
        }
        XMLStreamReaderUtil.verifyTag(reader, soapVersion.nsUri, SOAP_ENVELOPE);

        TagInfoset envelopeTag = new TagInfoset(reader);

        // Collect namespaces on soap:Envelope
        Map<String,String> namespaces = new HashMap<String,String>();
        for(int i=0; i< reader.getNamespaceCount();i++){
                namespaces.put(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
        }

        // Move to next element
        XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader,
                javax.xml.stream.XMLStreamConstants.START_ELEMENT);

        HeaderList headers = null;
        TagInfoset headerTag = null;

        if (reader.getLocalName().equals(SOAP_HEADER)
                && reader.getNamespaceURI().equals(soapVersion.nsUri)) {
            headerTag = new TagInfoset(reader);

            // Collect namespaces on soap:Header
            for(int i=0; i< reader.getNamespaceCount();i++){
                namespaces.put(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
            }
            // skip <soap:Header>
            XMLStreamReaderUtil.nextElementContent(reader);

            // If SOAP header blocks are present (i.e. not <soap:Header/>)
            if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
                headers = new HeaderList(soapVersion);

                try {
                    // Cache SOAP header blocks
                    StreamHeaderDecoder headerDecoder = SOAPVersion.SOAP_11.equals(soapVersion) ? SOAP11StreamHeaderDecoder : SOAP12StreamHeaderDecoder;
                    cacheHeaders(reader, namespaces, headers, headerDecoder);
                } catch (XMLStreamException e) {
                    // TODO need to throw more meaningful exception
                    throw new WebServiceException(e);
                }
            }

            // Move to soap:Body
            XMLStreamReaderUtil.nextElementContent(reader);
        }

        // Verify that <soap:Body> is present
        XMLStreamReaderUtil.verifyTag(reader, soapVersion.nsUri, SOAP_BODY);
        TagInfoset bodyTag = new TagInfoset(reader);

        String bodyPrologue = XMLStreamReaderUtil.nextWhiteSpaceContent(reader);
        message.init(envelopeTag,headerTag,message.attachmentSet,headers,bodyPrologue,bodyTag,null,reader,soapVersion);
        // when there's no payload,
        // it's tempting to use EmptyMessageImpl, but it doesn't preserve the infoset
        // of <envelope>,<header>, and <body>, so we need to stick to StreamMessage.
    }


    private static XMLStreamBuffer cacheHeaders(XMLStreamReader reader,
            Map<String, String> namespaces, HeaderList headers,
            StreamHeaderDecoder headerDecoder) throws XMLStreamException {
        MutableXMLStreamBuffer buffer = createXMLStreamBuffer();
        StreamReaderBufferCreator creator = new StreamReaderBufferCreator();
        creator.setXMLStreamBuffer(buffer);

        // Reader is positioned at the first header block
        while(reader.getEventType() == javax.xml.stream.XMLStreamConstants.START_ELEMENT) {
            Map<String,String> headerBlockNamespaces = namespaces;

            // Collect namespaces on SOAP header block
            if (reader.getNamespaceCount() > 0) {
                headerBlockNamespaces = new HashMap<String,String>(namespaces);
                for (int i = 0; i < reader.getNamespaceCount(); i++) {
                    headerBlockNamespaces.put(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
                }
            }

            // Mark
            XMLStreamBuffer mark = new XMLStreamBufferMark(headerBlockNamespaces, creator);
            // Create Header
            headers.add(headerDecoder.decodeHeader(reader, mark));


            // Cache the header block
            // After caching Reader will be positioned at next header block or
            // the end of the </soap:header>
            creator.createElementFragment(reader, false);
            if (reader.getEventType() != XMLStreamConstants.START_ELEMENT &&
                    reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
                XMLStreamReaderUtil.nextElementContent(reader);
            }
        }

        return buffer;
    }

    private static MutableXMLStreamBuffer createXMLStreamBuffer() {
        // TODO: Decode should own one MutableXMLStreamBuffer for reuse
        // since it is more efficient. ISSUE: possible issue with
        // lifetime of information in the buffer if accessed beyond
        // the pipe line.
        return new MutableXMLStreamBuffer();
    }

    public boolean isPayloadStreamReader() { return true; }

    public QName getPayloadQName() {
        return this.hasPayload() ? new QName(payloadNamespaceURI, payloadLocalName) : null;
    }

    public XMLStreamReader readToBodyStarTag() {
        if ( envelopeReader != null ) readEnvelope(this);
        List<XMLStreamReader> hReaders = new java.util.ArrayList<XMLStreamReader>();
        ElemInfo envElem =  new ElemInfo(envelopeTag, null);
        ElemInfo hdrElem =  (headerTag != null) ? new ElemInfo(headerTag, envElem) : null;
        ElemInfo bdyElem =  new ElemInfo(bodyTag, envElem);
        for (Header h : getHeaders().asList()) {
            try {
                hReaders.add(h.readHeader());
            } catch (XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }
        XMLStreamReader soapHeader = (hdrElem != null) ? new XMLReaderComposite(hdrElem, hReaders.toArray(new XMLStreamReader[hReaders.size()])) : null;
        XMLStreamReader[] payload = {};
        XMLStreamReader soapBody = new XMLReaderComposite(bdyElem, payload);
        XMLStreamReader[] soapContent = (soapHeader != null) ? new XMLStreamReader[]{soapHeader, soapBody} : new XMLStreamReader[]{soapBody};
        return new XMLReaderComposite(envElem, soapContent);
    }
}
