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

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.istack.internal.XMLStreamReaderToContentHandler;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.stax.StreamReaderBufferCreator;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.encoding.TagInfoset;
import com.sun.xml.internal.ws.message.AbstractMessageImpl;
import com.sun.xml.internal.ws.message.AttachmentUnmarshallerImpl;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.xml.DummyLocation;
import com.sun.xml.internal.ws.util.xml.StAXSource;
import com.sun.xml.internal.ws.util.xml.XMLStreamReaderToXMLStreamWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.NamespaceSupport;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.*;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * {@link Message} implementation backed by {@link XMLStreamReader}.
 *
 * TODO: we need another message class that keeps {@link XMLStreamReader} that points
 * at the start of the envelope element.
 */
public final class StreamMessage extends AbstractMessageImpl {
    /**
     * The reader will be positioned at
     * the first child of the SOAP body
     */
    private @NotNull XMLStreamReader reader;

    // lazily created
    private @Nullable HeaderList headers;

    private final String payloadLocalName;

    private final String payloadNamespaceURI;

    /**
     * infoset about the SOAP envelope, header, and body.
     *
     * <p>
     * If the creater of this object didn't care about those,
     * we use stock values.
     */
    private /*almost final*/ @NotNull TagInfoset envelopeTag,headerTag,bodyTag;

    /**
     * Creates a {@link StreamMessage} from a {@link XMLStreamReader}
     * that points at the start element of the payload, and headers.
     *
     * <p>
     * This method creaets a {@link Message} from a payload.
     *
     * @param headers
     *      if null, it means no headers. if non-null,
     *      it will be owned by this message.
     * @param reader
     *      points at the start element/document of the payload (or the end element of the &lt;s:Body>
     *      if there's no payload)
     */
    public StreamMessage(@Nullable HeaderList headers, @NotNull AttachmentSet attachmentSet, @NotNull XMLStreamReader reader, @NotNull SOAPVersion soapVersion) {
        super(soapVersion);
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
        this.envelopeTag = DEFAULT_TAGS[base];
        this.headerTag = DEFAULT_TAGS[base+1];
        this.bodyTag = DEFAULT_TAGS[base+2];
    }

    /**
     * Creates a {@link StreamMessage} from a {@link XMLStreamReader}
     * and the complete infoset of the SOAP envelope.
     *
     * <p>
     * See {@link #StreamMessage(HeaderList, AttachmentSet, XMLStreamReader, SOAPVersion)} for
     * the description of the basic parameters.
     *
     * @param headerTag
     *      Null if the message didn't have a header tag.
     *
     */
    public StreamMessage(@NotNull TagInfoset envelopeTag, @Nullable TagInfoset headerTag, @NotNull AttachmentSet attachmentSet, @Nullable HeaderList headers, @NotNull TagInfoset bodyTag, @NotNull XMLStreamReader reader, @NotNull SOAPVersion soapVersion) {
        this(headers,attachmentSet,reader,soapVersion);
        assert envelopeTag!=null && bodyTag!=null;
        this.envelopeTag = envelopeTag;
        this.headerTag = headerTag!=null ? headerTag :
            new TagInfoset(envelopeTag.nsUri,"Header",envelopeTag.prefix,EMPTY_ATTS);
        this.bodyTag = bodyTag;
    }

    public boolean hasHeaders() {
        return headers!=null && !headers.isEmpty();
    }

    public HeaderList getHeaders() {
        if (headers == null) {
            headers = new HeaderList();
        }
        return headers;
    }

    @Override
    public @NotNull AttachmentSet getAttachments() {
        return attachmentSet;
    }

    public String getPayloadLocalPart() {
        return payloadLocalName;
    }

    public String getPayloadNamespaceURI() {
        return payloadNamespaceURI;
    }

    public boolean hasPayload() {
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

    @Override
    public void consume() {
        assert unconsumed();
        XMLStreamReaderUtil.readRest(reader);
        XMLStreamReaderUtil.close(reader);
        XMLStreamReaderFactory.recycle(reader);
    }

    public XMLStreamReader readPayload() {
        // TODO: What about access at and beyond </soap:Body>
        assert unconsumed();
        return this.reader;
    }

    public void writePayloadTo(XMLStreamWriter writer)throws XMLStreamException {
        if(payloadLocalName==null)
            return; // no body
        assert unconsumed();
        XMLStreamReaderToXMLStreamWriter conv = new XMLStreamReaderToXMLStreamWriter();
        while(reader.getEventType() != XMLStreamConstants.END_DOCUMENT){
            String name = reader.getLocalName();
            String nsUri = reader.getNamespaceURI();

            //after previous conv.bridge() call the cursor will be at
            //END_ELEMENT. Check if its not soapenv:Body then move to next
            // ELEMENT
            if(reader.getEventType() == XMLStreamConstants.END_ELEMENT){
                if(!name.equals("Body") || !nsUri.equals(soapVersion.nsUri)){
                    XMLStreamReaderUtil.nextElementContent(reader);
                    if(reader.getEventType() == XMLStreamConstants.END_DOCUMENT)
                        break;
                    name = reader.getLocalName();
                    nsUri = reader.getNamespaceURI();
                }
            }
            if(name.equals("Body") && nsUri.equals(soapVersion.nsUri) || (reader.getEventType() == XMLStreamConstants.END_DOCUMENT))
                break;
            conv.bridge(reader,writer);
        }
        XMLStreamReaderUtil.readRest(reader);
        XMLStreamReaderUtil.close(reader);
        XMLStreamReaderFactory.recycle(reader);
    }

    public void writeTo(XMLStreamWriter sw) throws XMLStreamException{
        writeEnvelope(sw);
    }

    /**
     * This method should be called when the StreamMessage is created with a payload
     * @param writer
     */
    private void writeEnvelope(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartDocument();
        envelopeTag.writeStart(writer);

        //write headers
        HeaderList hl = getHeaders();
        if(hl.size() > 0){
            headerTag.writeStart(writer);
            for(Header h:hl){
                h.writeTo(writer);
            }
            writer.writeEndElement();
        }
        bodyTag.writeStart(writer);
        if(hasPayload())
            writePayloadTo(writer);
        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    public void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        assert unconsumed();
        try {
            if(payloadLocalName==null)
                return; // no body

            XMLStreamReaderToContentHandler conv =
                new XMLStreamReaderToContentHandler(reader,contentHandler,true,fragment);

            while(reader.getEventType() != XMLStreamConstants.END_DOCUMENT){
                String name = reader.getLocalName();
                String nsUri = reader.getNamespaceURI();

                //after previous conv.bridge() call the cursor will be at
                //END_ELEMENT. Check if its not soapenv:Body then move to next
                // ELEMENT
                if(reader.getEventType() == XMLStreamConstants.END_ELEMENT){
                    if(!name.equals("Body") || !nsUri.equals(soapVersion.nsUri)){
                        XMLStreamReaderUtil.nextElementContent(reader);
                        if(reader.getEventType() == XMLStreamConstants.END_DOCUMENT)
                            break;
                        name = reader.getLocalName();
                        nsUri = reader.getNamespaceURI();
                    }
                }
                if(name.equals("Body") && nsUri.equals(soapVersion.nsUri) || (reader.getEventType() == XMLStreamConstants.END_DOCUMENT))
                    break;

                conv.bridge();
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

    public Message copy() {
        try {
            // copy the payload
            XMLStreamReader clone;
            if(hasPayload()) {
                assert unconsumed();
                consumedAt = null; // but we don't want to mark it as consumed
                MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();

                //the boolean value tells the first body part is written.
                //based on this we do the right thing
                StreamReaderBufferCreator c = new StreamReaderBufferCreator(xsb);

                // preserving inscope namespaces from envelope, and body. Other option
                // would be to create a filtering XMLStreamReader from reader+envelopeTag+bodyTag
                c.storeElement(envelopeTag.nsUri, envelopeTag.localName, envelopeTag.prefix, envelopeTag.ns);
                c.storeElement(bodyTag.nsUri, bodyTag.localName, bodyTag.prefix, bodyTag.ns);

                // Loop all the way for multi payload case
                while(reader.getEventType() != XMLStreamConstants.END_DOCUMENT){
                    String name = reader.getLocalName();
                    String nsUri = reader.getNamespaceURI();
                    if(name.equals("Body") && nsUri.equals(soapVersion.nsUri) || (reader.getEventType() == XMLStreamConstants.END_DOCUMENT))
                        break;
                    c.create(reader);
                    // Skip whitespaces in between payload and </Body> or between elements
                    if (reader.isWhiteSpace()) {
                        XMLStreamReaderUtil.nextElementContent(reader);
                    }
                }
                XMLStreamReaderUtil.readRest(reader);
                XMLStreamReaderUtil.close(reader);
                XMLStreamReaderFactory.recycle(reader);

                reader = xsb.readAsXMLStreamReader();
                clone = xsb.readAsXMLStreamReader();

                // advance to the start tag of the first element
                proceedToRootElement(reader);
                proceedToRootElement(clone);
            } else {
                // it's tempting to use EmptyMessageImpl, but it doesn't presere the infoset
                // of <envelope>,<header>, and <body>, so we need to stick to StreamMessage.
                clone = reader;
            }

            return new StreamMessage(envelopeTag, headerTag, attachmentSet, HeaderList.copy(headers), bodyTag, clone, soapVersion);
        } catch (XMLStreamException e) {
            throw new WebServiceException("Failed to copy a message",e);
        }
    }

    private void proceedToRootElement(XMLStreamReader xsr) throws XMLStreamException {
        assert xsr.getEventType()==START_DOCUMENT;
        xsr.nextTag();
        xsr.nextTag();
        xsr.nextTag();
        assert xsr.getEventType()==START_ELEMENT;
    }

    public void writeTo( ContentHandler contentHandler, ErrorHandler errorHandler ) throws SAXException {
        contentHandler.setDocumentLocator(NULL_LOCATOR);
        contentHandler.startDocument();
        envelopeTag.writeStart(contentHandler);
        headerTag.writeStart(contentHandler);
        if(hasHeaders()) {
            HeaderList headers = getHeaders();
            int len = headers.size();
            for( int i=0; i<len; i++ ) {
                // shouldn't JDK be smart enough to use array-style indexing for this foreach!?
                headers.get(i).writeTo(contentHandler,errorHandler);
            }
        }
        headerTag.writeEnd(contentHandler);
        bodyTag.writeStart(contentHandler);
        writePayloadTo(contentHandler,errorHandler, true);
        bodyTag.writeEnd(contentHandler);
        envelopeTag.writeEnd(contentHandler);

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

    /**
     * Used only for debugging. This records where the message was consumed.
     */
    private Throwable consumedAt;

    /**
     * Default s:Envelope, s:Header, and s:Body tag infoset definitions.
     *
     * We need 3 for SOAP 1.1, 3 for SOAP 1.2.
     */
    private static final TagInfoset[] DEFAULT_TAGS;

    static {
        DEFAULT_TAGS = new TagInfoset[6];
        create(SOAPVersion.SOAP_11);
        create(SOAPVersion.SOAP_12);
    }

    private static void create(SOAPVersion v) {
        int base = v.ordinal()*3;
        DEFAULT_TAGS[base  ] = new TagInfoset(v.nsUri,"Envelope","S",EMPTY_ATTS,"S",v.nsUri);
        DEFAULT_TAGS[base+1] = new TagInfoset(v.nsUri,"Header","S",EMPTY_ATTS);
        DEFAULT_TAGS[base+2] = new TagInfoset(v.nsUri,"Body","S",EMPTY_ATTS);
    }
}
