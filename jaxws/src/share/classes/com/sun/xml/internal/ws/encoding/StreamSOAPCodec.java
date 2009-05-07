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

package com.sun.xml.internal.ws.encoding;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferMark;
import com.sun.xml.internal.stream.buffer.stax.StreamReaderBufferCreator;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;
import com.sun.xml.internal.ws.message.stream.StreamHeader;
import com.sun.xml.internal.ws.message.stream.StreamMessage;
import com.sun.xml.internal.ws.protocol.soap.VersionMismatchException;
import com.sun.xml.internal.ws.server.UnsupportedMediaException;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.streaming.TidyXMLStreamReader;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A stream SOAP codec.
 *
 * @author Paul Sandoz
 */
@SuppressWarnings({"StringEquality"})
public abstract class StreamSOAPCodec implements com.sun.xml.internal.ws.api.pipe.StreamSOAPCodec {

    private static final String SOAP_ENVELOPE = "Envelope";
    private static final String SOAP_HEADER = "Header";
    private static final String SOAP_BODY = "Body";

    private final String SOAP_NAMESPACE_URI;
    private final SOAPVersion soapVersion;

    /*package*/ StreamSOAPCodec(SOAPVersion soapVersion) {
        SOAP_NAMESPACE_URI = soapVersion.nsUri;
        this.soapVersion = soapVersion;
    }

    // consider caching
    // private final XMLStreamReader reader;

    // consider caching
    // private final MutableXMLStreamBuffer buffer;

    public ContentType getStaticContentType(Packet packet) {
        return getContentType(packet.soapAction);
    }

    public ContentType encode(Packet packet, OutputStream out) {
        if (packet.getMessage() != null) {
            XMLStreamWriter writer = XMLStreamWriterFactory.create(out);
            try {
                packet.getMessage().writeTo(writer);
                writer.flush();
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
            XMLStreamWriterFactory.recycle(writer);
        }
        return getContentType(packet.soapAction);
    }

    protected abstract ContentType getContentType(String soapAction);

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        //TODO: not yet implemented
        throw new UnsupportedOperationException();
    }

    protected abstract List<String> getExpectedContentTypes();

    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        List<String> expectedContentTypes = getExpectedContentTypes();
        if (contentType != null && !isContentTypeSupported(contentType,expectedContentTypes)) {
            throw new UnsupportedMediaException(contentType, expectedContentTypes);
        }
        // TODO: we should definitely let Decode owns one XMLStreamReader instance
        // instead of going to this generic factory
        XMLStreamReader reader = new TidyXMLStreamReader(XMLStreamReaderFactory.create(null, in, true), in);
        packet.setMessage(decode(reader));
    }

    /*
     * Checks against expected Content-Type headers that is handled by a codec
     *
     * @param ct the Content-Type of the request
     * @param expected expected Content-Types for a codec
     * @return true if the codec supports this Content-Type
     *         false otherwise
     */
    private static boolean isContentTypeSupported(String ct, List<String> expected) {
        for(String contentType : expected) {
            if (ct.indexOf(contentType) != -1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decodes a message from {@link XMLStreamReader} that points to
     * the beginning of a SOAP infoset.
     *
     * @param reader
     *      can point to the start document or the start element.
     */
    public final @NotNull Message decode(@NotNull XMLStreamReader reader) {
        return decode(reader,new AttachmentSetImpl());
    }

    /**
     * Decodes a message from {@link XMLStreamReader} that points to
     * the beginning of a SOAP infoset.
     *
     * @param reader
     *      can point to the start document or the start element.
     * @param attachmentSet
     *      {@link StreamSOAPCodec} can take attachments parsed outside,
     *      so that this codec can be used as a part of a biggre codec
     *      (like MIME multipart codec.)
     */
    public final Message decode(XMLStreamReader reader, @NotNull AttachmentSet attachmentSet) {

        // Move to soap:Envelope and verify
        if(reader.getEventType()!=XMLStreamConstants.START_ELEMENT)
            XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader,XMLStreamConstants.START_ELEMENT);
        if (SOAP_ENVELOPE.equals(reader.getLocalName()) && !SOAP_NAMESPACE_URI.equals(reader.getNamespaceURI())) {
            throw new VersionMismatchException(soapVersion, SOAP_NAMESPACE_URI, reader.getNamespaceURI());
        }
        XMLStreamReaderUtil.verifyTag(reader, SOAP_NAMESPACE_URI, SOAP_ENVELOPE);

        TagInfoset envelopeTag = new TagInfoset(reader);

        // Collect namespaces on soap:Envelope
        Map<String,String> namespaces = new HashMap<String,String>();

        // Move to next element
        XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader,
                javax.xml.stream.XMLStreamConstants.START_ELEMENT);

        HeaderList headers = null;
        TagInfoset headerTag = null;

        if (reader.getLocalName().equals(SOAP_HEADER)
                && reader.getNamespaceURI().equals(SOAP_NAMESPACE_URI)) {
            headerTag = new TagInfoset(reader);

            // Collect namespaces on soap:Header
            for(int i=0; i< reader.getNamespaceCount();i++){
                namespaces.put(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
            }
            // skip <soap:Header>
            XMLStreamReaderUtil.nextElementContent(reader);

            // If SOAP header blocks are present (i.e. not <soap:Header/>)
            if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
                headers = new HeaderList();

                try {
                    // Cache SOAP header blocks
                    cacheHeaders(reader, namespaces, headers);
                } catch (XMLStreamException e) {
                    // TODO need to throw more meaningful exception
                    throw new WebServiceException(e);
                }
            }

            // Move to soap:Body
            XMLStreamReaderUtil.nextElementContent(reader);
        }

        // Verify that <soap:Body> is present
        XMLStreamReaderUtil.verifyTag(reader, SOAP_NAMESPACE_URI, SOAP_BODY);
        TagInfoset bodyTag = new TagInfoset(reader);

        XMLStreamReaderUtil.nextElementContent(reader);
        return new StreamMessage(envelopeTag,headerTag,attachmentSet,headers,bodyTag,reader,soapVersion);
        // when there's no payload,
        // it's tempting to use EmptyMessageImpl, but it doesn't presere the infoset
        // of <envelope>,<header>, and <body>, so we need to stick to StreamMessage.
    }

    public void decode(ReadableByteChannel in, String contentType, Packet packet ) {
        throw new UnsupportedOperationException();
    }

    public final StreamSOAPCodec copy() {
        return this;
    }

    private XMLStreamBuffer cacheHeaders(XMLStreamReader reader,
            Map<String, String> namespaces, HeaderList headers) throws XMLStreamException {
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
            headers.add(createHeader(reader, mark));


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

    protected abstract StreamHeader createHeader(XMLStreamReader reader, XMLStreamBuffer mark);

    private MutableXMLStreamBuffer createXMLStreamBuffer() {
        // TODO: Decode should own one MutableXMLStreamBuffer for reuse
        // since it is more efficient. ISSUE: possible issue with
        // lifetime of information in the buffer if accessed beyond
        // the pipe line.
        return new MutableXMLStreamBuffer();
    }




    /**
     * Creates a new {@link StreamSOAPCodec} instance.
     */
    public static StreamSOAPCodec create(SOAPVersion version) {
        if(version==null)
            // this decoder is for SOAP, not for XML/HTTP
            throw new IllegalArgumentException();
        switch(version) {
            case SOAP_11:
                return new StreamSOAP11Codec();
            case SOAP_12:
                return new StreamSOAP12Codec();
            default:
                throw new AssertionError();
        }
    }

}
