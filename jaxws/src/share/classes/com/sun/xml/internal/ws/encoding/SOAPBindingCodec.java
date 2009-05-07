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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.StringTokenizer;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.soap.MTOMFeature;

import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.client.SelectOptimalEncodingFeature;
import com.sun.xml.internal.ws.api.fastinfoset.FastInfosetFeature;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.pipe.StreamSOAPCodec;
import com.sun.xml.internal.ws.api.pipe.Codecs;
import com.sun.xml.internal.ws.binding.SOAPBindingImpl;
import com.sun.xml.internal.ws.client.ContentNegotiation;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.resources.StreamingMessages;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.server.UnsupportedMediaException;
import com.sun.xml.internal.ws.transport.http.WSHTTPConnection;

/**
 * SOAP binding {@link Codec} that can handle MTOM, SwA, and SOAP messages
 * encoded using XML or Fast Infoset.
 *
 * <p>
 * This is used when we need to determine the encoding from what we received (for decoding)
 * and from configuration and {@link Message} contents (for encoding)
 *
 * <p>
 * TODO: Split this Codec into two, one that supports FI and one that does not.
 * Then further split the FI Codec into two, one for client and one for
 * server. This will simplify the logic and make it easier to understand/maintain.
 *
 * @author Vivek Pandey
 * @author Kohsuke Kawaguchi
 */
public class SOAPBindingCodec extends MimeCodec implements com.sun.xml.internal.ws.api.pipe.SOAPBindingCodec {
    /**
     * Base HTTP Accept request-header.
     */
    private static final String BASE_ACCEPT_VALUE =
            "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2";

    /**
     * True if Fast Infoset functionality has been
     * configured to be disabled, or the Fast Infoset
     * runtime is not available.
     */
    private boolean isFastInfosetDisabled;

    /**
     * True if the Fast Infoset codec should be used for encoding.
     */
    private boolean useFastInfosetForEncoding;

    /**
     * True if the content negotiation property should
     * be ignored by the client. This will be used in
     * the case of Fast Infoset being configured to be
     * disabled or automatically selected.
     */
    private boolean ignoreContentNegotiationProperty;

    // The XML SOAP codec
    private final StreamSOAPCodec xmlSoapCodec;

    // The Fast Infoset SOAP codec
    private final Codec fiSoapCodec;

    // The XML MTOM codec
    private final MimeCodec xmlMtomCodec;

    // The XML SWA codec
    private final MimeCodec xmlSwaCodec;

    // The Fast Infoset SWA codec
    private final MimeCodec fiSwaCodec;

    private final SOAPBindingImpl binding;

    /**
     * The XML SOAP MIME type
     */
    private final String xmlMimeType;

    /**
     * The Fast Infoset SOAP MIME type
     */
    private final String fiMimeType;

    /**
     * The Accept header for XML encodings
     */
    private final String xmlAccept;

    /**
     * The Accept header for Fast Infoset and XML encodings
     */
    private final String connegXmlAccept;

    public StreamSOAPCodec getXMLCodec() {
        return xmlSoapCodec;
    }

    private class AcceptContentType implements ContentType {
        private ContentType _c;
        private String _accept;

        public AcceptContentType set(Packet p, ContentType c) {
            if (!ignoreContentNegotiationProperty && p.contentNegotiation != ContentNegotiation.none) {
                _accept = connegXmlAccept;
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

    public SOAPBindingCodec(WSBinding binding) {
        this(binding, Codecs.createSOAPEnvelopeXmlCodec(binding.getSOAPVersion()));
    }

    public SOAPBindingCodec(WSBinding binding, StreamSOAPCodec xmlSoapCodec) {
        super(binding.getSOAPVersion());

        this.xmlSoapCodec = xmlSoapCodec;
        xmlMimeType = xmlSoapCodec.getMimeType();

        xmlMtomCodec = new MtomCodec(version, xmlSoapCodec, binding.getFeature(MTOMFeature.class));

        xmlSwaCodec = new SwACodec(version, xmlSoapCodec);

        String clientAcceptedContentTypes = xmlSoapCodec.getMimeType() + ", " +
                xmlMtomCodec.getMimeType() + ", " +
                BASE_ACCEPT_VALUE;

        WebServiceFeature fi = binding.getFeature(FastInfosetFeature.class);
        isFastInfosetDisabled = (fi != null && !fi.isEnabled());
        if (!isFastInfosetDisabled) {
            fiSoapCodec = getFICodec(xmlSoapCodec, version);
            if (fiSoapCodec != null) {
                fiMimeType = fiSoapCodec.getMimeType();
                fiSwaCodec = new SwACodec(version, fiSoapCodec);
                connegXmlAccept = fiMimeType + ", " + clientAcceptedContentTypes;

                /**
                 * This feature will only be present on the client side.
                 *
                 * Fast Infoset is enabled on the client if the service
                 * explicitly supports Fast Infoset.
                 */
                WebServiceFeature select = binding.getFeature(SelectOptimalEncodingFeature.class);
                if (select != null) { // if the client FI feature is set - ignore negotiation property
                    ignoreContentNegotiationProperty = true;
                    if (select.isEnabled()) {
                        // If the client's FI encoding feature is enabled, and server's is not disabled
                        if (fi != null) {  // if server's FI feature also enabled
                            useFastInfosetForEncoding = true;
                        }

                        clientAcceptedContentTypes = connegXmlAccept;
                    } else {  // If client FI feature is disabled
                        isFastInfosetDisabled = true;
                    }
                }
            } else {
                // Fast Infoset could not be loaded by the runtime
                isFastInfosetDisabled = true;
                fiSwaCodec = null;
                fiMimeType = "";
                connegXmlAccept = clientAcceptedContentTypes;
                ignoreContentNegotiationProperty = true;
            }
        } else {
            // Fast Infoset is explicitly not supported by the service
            fiSoapCodec = fiSwaCodec = null;
            fiMimeType = "";
            connegXmlAccept = clientAcceptedContentTypes;
            ignoreContentNegotiationProperty = true;
        }

        xmlAccept = clientAcceptedContentTypes;

        this.binding = (SOAPBindingImpl)binding;
    }

    public String getMimeType() {
        return null;
    }

    public ContentType getStaticContentType(Packet packet) {
        ContentType toAdapt = getEncoder(packet).getStaticContentType(packet);
        return (toAdapt != null) ? _adaptingContentType.set(packet, toAdapt) : null;
    }

    public ContentType encode(Packet packet, OutputStream out) throws IOException {
        return _adaptingContentType.set(packet, getEncoder(packet).encode(packet, out));
    }

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        return _adaptingContentType.set(packet, getEncoder(packet).encode(packet, buffer));
    }

    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        if (contentType == null) {
            throw new UnsupportedMediaException();
        }

        /**
         * Reset the encoding state when on the server side for each
         * decode/encode step.
         */
        if (packet.contentNegotiation == null)
            useFastInfosetForEncoding = false;

        if(isMultipartRelated(contentType))
            // parse the multipart portion and then decide whether it's MTOM or SwA
            super.decode(in, contentType, packet);
        else if(isFastInfoset(contentType)) {
            if (!ignoreContentNegotiationProperty && packet.contentNegotiation == ContentNegotiation.none)
                throw noFastInfosetForDecoding();

            useFastInfosetForEncoding = true;
            fiSoapCodec.decode(in, contentType, packet);
        } else
            xmlSoapCodec.decode(in, contentType, packet);

        if (!useFastInfosetForEncoding) {
            useFastInfosetForEncoding = isFastInfosetAcceptable(packet.acceptableMimeTypes);
        }
    }

    public void decode(ReadableByteChannel in, String contentType, Packet packet) {
        if (contentType == null) {
            throw new UnsupportedMediaException();
        }
        /**
         * Reset the encoding state when on the server side for each
         * decode/encode step.
         */
        if (packet.contentNegotiation == null)
            useFastInfosetForEncoding = false;

        if(isMultipartRelated(contentType))
            super.decode(in, contentType, packet);
        else if(isFastInfoset(contentType)) {
            if (packet.contentNegotiation == ContentNegotiation.none)
                throw noFastInfosetForDecoding();

            useFastInfosetForEncoding = true;
            fiSoapCodec.decode(in, contentType, packet);
        } else
            xmlSoapCodec.decode(in, contentType, packet);

//        checkDuplicateKnownHeaders(packet);
        if (!useFastInfosetForEncoding) {
            useFastInfosetForEncoding = isFastInfosetAcceptable(packet.acceptableMimeTypes);
        }
    }

    public SOAPBindingCodec copy() {
        return new SOAPBindingCodec(binding, (StreamSOAPCodec)xmlSoapCodec.copy());
    }

    @Override
    protected void decode(MimeMultipartParser mpp, Packet packet) throws IOException {
        // is this SwA or XOP?
        final String rootContentType = mpp.getRootPart().getContentType();

        if(isApplicationXopXml(rootContentType))
            xmlMtomCodec.decode(mpp,packet);
        else if (isFastInfoset(rootContentType)) {
            if (packet.contentNegotiation == ContentNegotiation.none)
                throw noFastInfosetForDecoding();

            useFastInfosetForEncoding = true;
            fiSwaCodec.decode(mpp,packet);
        } else if (isXml(rootContentType))
            xmlSwaCodec.decode(mpp,packet);
        else {
            // TODO localize exception
            throw new IOException("");
        }
//        checkDuplicateKnownHeaders(packet);
    }

    private boolean isMultipartRelated(String contentType) {
        return compareStrings(contentType, MimeCodec.MULTIPART_RELATED_MIME_TYPE);
    }

    private boolean isApplicationXopXml(String contentType) {
        return compareStrings(contentType, MtomCodec.XOP_XML_MIME_TYPE);
    }

    private boolean isXml(String contentType) {
        return compareStrings(contentType, xmlMimeType);
    }

    private boolean isFastInfoset(String contentType) {
        if (isFastInfosetDisabled) return false;

        return compareStrings(contentType, fiMimeType);
    }

    private boolean compareStrings(String a, String b) {
        return a.length() >= b.length() &&
                b.equalsIgnoreCase(
                a.substring(0,
                b.length()));
    }

    private boolean isFastInfosetAcceptable(String accept) {
        if (accept == null || isFastInfosetDisabled) return false;

        StringTokenizer st = new StringTokenizer(accept, ",");
        while (st.hasMoreTokens()) {
            final String token = st.nextToken().trim();
            if (token.equalsIgnoreCase(fiMimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines the encoding codec.
     */
    private Codec getEncoder(Packet p) {
        /**
         * The following logic is only for outbound packets
         * to be encoded by a client.
         * For a server the p.contentNegotiation == null.
         */
        if (!ignoreContentNegotiationProperty) {
            if (p.contentNegotiation == ContentNegotiation.none) {
                // The client may have changed the negotiation property from
                // pessismistic to none between invocations
                useFastInfosetForEncoding = false;
            } else if (p.contentNegotiation == ContentNegotiation.optimistic) {
                // Always encode using Fast Infoset if in optimisitic mode
                useFastInfosetForEncoding = true;
            }
        }

        // Override the MTOM binding for now
        // Note: Using FI with MTOM does not make sense
        if (useFastInfosetForEncoding) {
            final Message m = p.getMessage();
            if(m==null || m.getAttachments().isEmpty() || binding.isFeatureEnabled(MTOMFeature.class))
                return fiSoapCodec;
            else
                return fiSwaCodec;
        }

        if(binding.isFeatureEnabled(MTOMFeature.class))
            return xmlMtomCodec;

        Message m = p.getMessage();
        if(m==null || m.getAttachments().isEmpty())
            return xmlSoapCodec;
        else
            return xmlSwaCodec;
    }

    private RuntimeException noFastInfosetForDecoding() {
        return new RuntimeException(StreamingMessages.FASTINFOSET_DECODING_NOT_ACCEPTED());
    }

    /**
     * Obtain an FI SOAP codec instance using reflection.
     */
    private static Codec getFICodec(StreamSOAPCodec soapCodec, SOAPVersion version) {
        try {
            Class c = Class.forName("com.sun.xml.internal.ws.encoding.fastinfoset.FastInfosetStreamSOAPCodec");
            Method m = c.getMethod("create", StreamSOAPCodec.class, SOAPVersion.class);
            return (Codec)m.invoke(null, soapCodec, version);
        } catch (Exception e) {
            // TODO Log that FI cannot be loaded
            return null;
        }
    }
}
