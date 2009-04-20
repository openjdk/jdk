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

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.client.ContentNegotiation;
import com.sun.xml.internal.ws.encoding.xml.XMLCodec;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage.MessageDataSource;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage.UnknownContent;
import com.sun.xml.internal.ws.encoding.xml.XMLMessage.XMLMultiPart;
import com.sun.xml.internal.ws.resources.StreamingMessages;

import javax.activation.DataSource;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.channels.WritableByteChannel;
import java.util.StringTokenizer;

/**
 * XML (infoset) over HTTP binding {@link Codec}.
 * <p>
 * TODO: Support FI for multipart/related
 *       Support FI for MessageDataSource
 *
 * @author Jitendra Kotamraju
 */
public final class XMLHTTPBindingCodec extends MimeCodec {
    /**
     * Base HTTP Accept request-header.
     */
    private static final String BASE_ACCEPT_VALUE =
        "*";

    /**
     * Fast Infoset MIME type.
     */
    private static final String APPLICATION_FAST_INFOSET_MIME_TYPE =
        "application/fastinfoset";

    /**
     * True if the Fast Infoset codec should be used
     */
    private boolean useFastInfosetForEncoding;

    /**
     * The XML codec
     */
    private final Codec xmlCodec;

    /**
     * The FI codec
     */
    private final Codec fiCodec;

    /**
     * The Accept header for XML encodings
     */
    private final String xmlAccept;

    /**
     * The Accept header for Fast Infoset and XML encodings
     */
    private final String fiXmlAccept;

    private class AcceptContentType implements ContentType {
        private ContentType _c;
        private String _accept;

        public AcceptContentType set(Packet p, ContentType c) {
            // TODO: need to compose based on underlying codecs
            if (p.contentNegotiation == ContentNegotiation.optimistic
                    || p.contentNegotiation == ContentNegotiation.pessimistic) {
                _accept = fiXmlAccept;
            } else {
                _accept = xmlAccept;
            }
            _c = c;
            return this;
        }

        public String getContentType() {
            return _c.getContentType();
        }

        public String getSOAPActionHeader() {
            return _c.getSOAPActionHeader();
        }

        public String getAcceptHeader() {
            return _accept;
        }
    }

    private AcceptContentType _adaptingContentType = new AcceptContentType();

    public XMLHTTPBindingCodec() {
        super(SOAPVersion.SOAP_11);

        xmlCodec = new XMLCodec();

        fiCodec = getFICodec();

        xmlAccept = null;

        fiXmlAccept = APPLICATION_FAST_INFOSET_MIME_TYPE + ", " +
                BASE_ACCEPT_VALUE;
    }

    public String getMimeType() {
        return null;
    }

    @Override
    public ContentType getStaticContentType(Packet packet) {
        setRootCodec(packet);

        ContentType ct = null;
        if (packet.getMessage() instanceof MessageDataSource) {
            final MessageDataSource mds = (MessageDataSource)packet.getMessage();
            if (mds.hasUnconsumedDataSource()) {
                ct = getStaticContentType(mds);
                return (ct != null)
                    ? _adaptingContentType.set(packet, ct)
                    : null;
            }
        }

        ct = super.getStaticContentType(packet);
        return (ct != null)
            ? _adaptingContentType.set(packet, ct)
            : null;
    }

    @Override
    public ContentType encode(Packet packet, OutputStream out) throws IOException {
        setRootCodec(packet);

        if (packet.getMessage() instanceof MessageDataSource) {
            final MessageDataSource mds = (MessageDataSource)packet.getMessage();
            if (mds.hasUnconsumedDataSource())
                return _adaptingContentType.set(packet, encode(mds, out));
        }

        return _adaptingContentType.set(packet, super.encode(packet, out));
    }

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        /**
         * Reset the encoding state when on the server side for each
         * decode/encode step.
         */
        if (packet.contentNegotiation == null)
            useFastInfosetForEncoding = false;

        if (contentType == null) {
            xmlCodec.decode(in, contentType, packet);
        } else if (isMultipartRelated(contentType)) {
            packet.setMessage(new XMLMultiPart(contentType, in));
        } else if(isFastInfoset(contentType)) {
            if (fiCodec == null) {
                throw new RuntimeException(StreamingMessages.FASTINFOSET_NO_IMPLEMENTATION());
            }

            useFastInfosetForEncoding = true;
            fiCodec.decode(in, contentType, packet);
        } else if (isXml(contentType)) {
            xmlCodec.decode(in, contentType, packet);
        } else {
            packet.setMessage(new UnknownContent(contentType, in));
        }

        if (!useFastInfosetForEncoding) {
            useFastInfosetForEncoding = isFastInfosetAcceptable(packet.acceptableMimeTypes);
        }
    }

    protected void decode(MimeMultipartParser mpp, Packet packet) throws IOException {
        // This method will never be invoked
    }

    public MimeCodec copy() {
        return new XMLHTTPBindingCodec();
    }

    private boolean isMultipartRelated(String contentType) {
        return compareStrings(contentType, MimeCodec.MULTIPART_RELATED_MIME_TYPE);
    }

    private boolean isApplicationXopXml(String contentType) {
        return compareStrings(contentType, MtomCodec.XOP_XML_MIME_TYPE);
    }

    private boolean isXml(String contentType) {
        return compareStrings(contentType, XMLCodec.XML_APPLICATION_MIME_TYPE)
                || compareStrings(contentType, XMLCodec.XML_TEXT_MIME_TYPE)
                || (compareStrings(contentType, "application/")&&(contentType.toLowerCase().indexOf("+xml") != -1));
    }

    private boolean isFastInfoset(String contentType) {
        return compareStrings(contentType, APPLICATION_FAST_INFOSET_MIME_TYPE);
    }

    private boolean compareStrings(String a, String b) {
        return a.length() >= b.length() &&
                b.equalsIgnoreCase(
                    a.substring(0,
                        b.length()));
    }

    private boolean isFastInfosetAcceptable(String accept) {
        if (accept == null) return false;

        StringTokenizer st = new StringTokenizer(accept, ",");
        while (st.hasMoreTokens()) {
            final String token = st.nextToken().trim();
            if (token.equalsIgnoreCase(APPLICATION_FAST_INFOSET_MIME_TYPE)) {
                return true;
            }
        }
        return false;
    }

    private ContentType getStaticContentType(MessageDataSource mds) {
        final String contentType = mds.getDataSource().getContentType();
        final boolean isFastInfoset = XMLMessage.isFastInfoset(contentType);

        if (!requiresTransformationOfDataSource(isFastInfoset,
                useFastInfosetForEncoding)) {
            return new ContentTypeImpl(contentType);
        } else {
            return null;
        }
    }

    private ContentType encode(MessageDataSource mds, OutputStream out) {
        try {
            final boolean isFastInfoset = XMLMessage.isFastInfoset(
                    mds.getDataSource().getContentType());
            DataSource ds = transformDataSource(mds.getDataSource(),
                    isFastInfoset, useFastInfosetForEncoding);

            InputStream is = ds.getInputStream();
            byte[] buf = new byte[1024];
            int count;
            while((count=is.read(buf)) != -1) {
                out.write(buf, 0, count);
            }
            return new ContentTypeImpl(ds.getContentType());
        } catch(IOException ioe) {
            throw new WebServiceException(ioe);
        }
    }

    private void setRootCodec(Packet p) {
        /**
         * The following logic is only for outbound packets
         * to be encoded by client.
         * On the server the p.contentNegotiation == null.
         */
        if (p.contentNegotiation == ContentNegotiation.none) {
            // The client may have changed the negotiation property from
            // pessismistic to none between invocations
            useFastInfosetForEncoding = false;
        } else if (p.contentNegotiation == ContentNegotiation.optimistic) {
            // Always encode using Fast Infoset if in optimisitic mode
            useFastInfosetForEncoding = true;
        }

        rootCodec = (useFastInfosetForEncoding && fiCodec != null)
            ? fiCodec : xmlCodec;
    }

    public static boolean requiresTransformationOfDataSource(
            boolean isFastInfoset, boolean useFastInfoset) {
        return (isFastInfoset && !useFastInfoset) || (!isFastInfoset && useFastInfoset);
    }

    public static DataSource transformDataSource(DataSource in,
            boolean isFastInfoset, boolean useFastInfoset) {
        try {
            if (isFastInfoset && !useFastInfoset) {
                // Convert from Fast Infoset to XML
                Codec codec = new XMLHTTPBindingCodec();
                Packet p = new Packet();
                codec.decode(in.getInputStream(), in.getContentType(), p);

                p.getMessage().getAttachments();
                codec.getStaticContentType(p);

                ByteOutputStream bos = new ByteOutputStream();
                ContentType ct = codec.encode(p, bos);
                return XMLMessage.createDataSource(ct.getContentType(), bos.newInputStream());
            } else if (!isFastInfoset && useFastInfoset) {
                // Convert from XML to Fast Infoset
                Codec codec = new XMLHTTPBindingCodec();
                Packet p = new Packet();
                codec.decode(in.getInputStream(), in.getContentType(), p);

                p.contentNegotiation = ContentNegotiation.optimistic;
                p.getMessage().getAttachments();
                codec.getStaticContentType(p);

                ByteOutputStream bos = new ByteOutputStream();
                com.sun.xml.internal.ws.api.pipe.ContentType ct = codec.encode(p, bos);
                return XMLMessage.createDataSource(ct.getContentType(), bos.newInputStream());
            }
        } catch(Exception ex) {
            throw new WebServiceException(ex);
        }

        return in;
    }

    /**
     * Obtain an FI SOAP codec instance using reflection.
     */
    private static Codec getFICodec() {
        try {
            Class c = Class.forName("com.sun.xml.internal.ws.encoding.fastinfoset.FastInfosetCodec");
            Method m = c.getMethod("create");
            return (Codec)m.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }
}
