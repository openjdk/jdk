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

package com.sun.xml.internal.ws.encoding;

import static com.sun.xml.internal.ws.binding.WebServiceFeatureList.getSoapVersion;

import com.oracle.webservices.internal.impl.encoding.StreamDecoderImpl;
import com.oracle.webservices.internal.impl.internalspi.encoding.StreamDecoder;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferMark;
import com.sun.xml.internal.stream.buffer.stax.StreamReaderBufferCreator;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.developer.SerializationFeature;
import com.sun.xml.internal.ws.message.AttachmentSetImpl;
import com.sun.xml.internal.ws.message.stream.StreamMessage;
import com.sun.xml.internal.ws.protocol.soap.VersionMismatchException;
import com.sun.xml.internal.ws.server.UnsupportedMediaException;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.ServiceFinder;

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
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A stream SOAP codec.
 *
 * @author Paul Sandoz
 */
@SuppressWarnings({"StringEquality"})
public abstract class StreamSOAPCodec implements com.sun.xml.internal.ws.api.pipe.StreamSOAPCodec, RootOnlyCodec {

    private static final String SOAP_ENVELOPE = "Envelope";
    private static final String SOAP_HEADER = "Header";
    private static final String SOAP_BODY = "Body";

    private final SOAPVersion soapVersion;
    protected final SerializationFeature serializationFeature;

    private final StreamDecoder streamDecoder;

    // charset of last decoded message. Will be used for encoding server's
    // response messages with the request message's encoding
    // it will stored in the packet.invocationProperties
    private final static String DECODED_MESSAGE_CHARSET = "decodedMessageCharset";

    /*package*/ StreamSOAPCodec(SOAPVersion soapVersion) {
        this(soapVersion, null);
    }

    /*package*/ StreamSOAPCodec(WSBinding binding) {
        this(binding.getSOAPVersion(), binding.getFeature(SerializationFeature.class));
    }

    StreamSOAPCodec(WSFeatureList features) {
        this(getSoapVersion(features), features.get(SerializationFeature.class));
    }

    private StreamSOAPCodec(SOAPVersion soapVersion, @Nullable SerializationFeature sf) {
        this.soapVersion = soapVersion;
        this.serializationFeature = sf;
        this.streamDecoder = selectStreamDecoder();
    }

    private StreamDecoder selectStreamDecoder() {
        for (StreamDecoder sd : ServiceFinder.find(StreamDecoder.class)) {
            return sd;
        }

        return new StreamDecoderImpl();
    }

    public ContentType getStaticContentType(Packet packet) {
        return getContentType(packet);
    }

    public ContentType encode(Packet packet, OutputStream out) {
        if (packet.getMessage() != null) {
            String encoding = getPacketEncoding(packet);
            packet.invocationProperties.remove(DECODED_MESSAGE_CHARSET);
            XMLStreamWriter writer = XMLStreamWriterFactory.create(out, encoding);
            try {
                packet.getMessage().writeTo(writer);
                writer.flush();
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
            XMLStreamWriterFactory.recycle(writer);
        }
        return getContentType(packet);
    }

    protected abstract ContentType getContentType(Packet packet);

    protected abstract String getDefaultContentType();

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        //TODO: not yet implemented
        throw new UnsupportedOperationException();
    }

    protected abstract List<String> getExpectedContentTypes();

    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        decode(in, contentType, packet, new AttachmentSetImpl());
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
            if (ct.contains(contentType)) {
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
        return decode(soapVersion, reader, attachmentSet);
    }

    public static final Message decode(SOAPVersion soapVersion, XMLStreamReader reader, @NotNull AttachmentSet attachmentSet) {
        // Move to soap:Envelope and verify
        if(reader.getEventType()!=XMLStreamConstants.START_ELEMENT)
            XMLStreamReaderUtil.nextElementContent(reader);
        XMLStreamReaderUtil.verifyReaderState(reader,XMLStreamConstants.START_ELEMENT);
        if (SOAP_ENVELOPE.equals(reader.getLocalName()) && !soapVersion.nsUri.equals(reader.getNamespaceURI())) {
            throw new VersionMismatchException(soapVersion, soapVersion.nsUri, reader.getNamespaceURI());
        }
        XMLStreamReaderUtil.verifyTag(reader, soapVersion.nsUri, SOAP_ENVELOPE);
        return new StreamMessage(soapVersion, reader, attachmentSet);
    }

    public void decode(ReadableByteChannel in, String contentType, Packet packet ) {
        throw new UnsupportedOperationException();
    }

    public final StreamSOAPCodec copy() {
        return this;
    }

    public void decode(InputStream in, String contentType, Packet packet, AttachmentSet att ) throws IOException {
        List<String> expectedContentTypes = getExpectedContentTypes();
        if (contentType != null && !isContentTypeSupported(contentType,expectedContentTypes)) {
            throw new UnsupportedMediaException(contentType, expectedContentTypes);
        }
        com.oracle.webservices.internal.api.message.ContentType pct = packet.getInternalContentType();
        ContentTypeImpl cti = (pct != null && pct instanceof ContentTypeImpl) ?
                (ContentTypeImpl)pct : new ContentTypeImpl(contentType);
        String charset = cti.getCharSet();
        if (charset != null && !Charset.isSupported(charset)) {
            throw new UnsupportedMediaException(charset);
        }
        if (charset != null) {
            packet.invocationProperties.put(DECODED_MESSAGE_CHARSET, charset);
        } else {
            packet.invocationProperties.remove(DECODED_MESSAGE_CHARSET);
        }
        packet.setMessage(streamDecoder.decode(in, charset, att, soapVersion));
    }

    public void decode(ReadableByteChannel in, String contentType, Packet response, AttachmentSet att ) {
        throw new UnsupportedOperationException();
    }

    /*
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

    /*
     * Creates a new {@link StreamSOAPCodec} instance using binding
     */
    public static StreamSOAPCodec create(WSFeatureList features) {
        SOAPVersion version = getSoapVersion(features);
        if(version==null)
            // this decoder is for SOAP, not for XML/HTTP
            throw new IllegalArgumentException();
        switch(version) {
            case SOAP_11:
                return new StreamSOAP11Codec(features);
            case SOAP_12:
                return new StreamSOAP12Codec(features);
            default:
                throw new AssertionError();
        }
    }

    /**
     * Creates a new {@link StreamSOAPCodec} instance using binding
     *
     * @deprecated use {@link #create(WSFeatureList)}
     */
    public static StreamSOAPCodec create(WSBinding binding) {
        SOAPVersion version = binding.getSOAPVersion();
        if(version==null)
            // this decoder is for SOAP, not for XML/HTTP
            throw new IllegalArgumentException();
        switch(version) {
            case SOAP_11:
                return new StreamSOAP11Codec(binding);
            case SOAP_12:
                return new StreamSOAP12Codec(binding);
            default:
                throw new AssertionError();
        }
    }

    private String getPacketEncoding(Packet packet) {
        // If SerializationFeature is set, just use that encoding
        if (serializationFeature != null && serializationFeature.getEncoding() != null) {
            return serializationFeature.getEncoding().equals("")
                    ? SOAPBindingCodec.DEFAULT_ENCODING : serializationFeature.getEncoding();
        }

        if (packet != null && packet.endpoint != null) {
            // Use request message's encoding for Server-side response messages
            String charset = (String)packet.invocationProperties.get(DECODED_MESSAGE_CHARSET);
            return charset == null
                    ? SOAPBindingCodec.DEFAULT_ENCODING : charset;
        }

        // Use default encoding for client-side request messages
        return SOAPBindingCodec.DEFAULT_ENCODING;
    }

    protected ContentTypeImpl.Builder getContenTypeBuilder(Packet packet) {
        ContentTypeImpl.Builder b = new ContentTypeImpl.Builder();
        String encoding = getPacketEncoding(packet);
        if (SOAPBindingCodec.DEFAULT_ENCODING.equalsIgnoreCase(encoding)) {
            b.contentType = getDefaultContentType();
            b.charset = SOAPBindingCodec.DEFAULT_ENCODING;
            return b;
        }
        b.contentType = getMimeType()+" ;charset="+encoding;
        b.charset = encoding;
        return b;
    }

}
