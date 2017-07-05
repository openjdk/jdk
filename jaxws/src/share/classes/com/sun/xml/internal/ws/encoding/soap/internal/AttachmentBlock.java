/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.xml.internal.ws.encoding.soap.internal;

import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.soap.SerializationException;
import com.sun.xml.internal.ws.server.RuntimeContext;
import com.sun.xml.internal.ws.util.ASCIIUtility;
import com.sun.xml.internal.ws.util.ByteArrayDataSource;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.imageio.ImageIO;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.WebServiceException;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Attachment of {@link InternalMessage}.
 *
 * <p>
 * The key idea behind this class is to hide the actual data representation.
 * The producer of the object may choose the best format it wants, and then
 * various accessor methods provide the rest of the stack to chose the format
 * it wants.
 *
 * <p>
 * When receiving from a network, this allows an {@link AttachmentBlock} object
 * to be constructed without actually even parsing the attachment. When
 * sending to a network, this allows the conversion to the byte image to happen
 * lazily, and directly to the network.
 *
 * <p>
 * Even though most of the data access methods have default implementation,
 * Implementation classes of this class should override them
 * so that they can run faster, whenever possible. In particular, the default
 * implementation of {@link #asInputStream()} and {@link #writeTo(OutputStream)}
 * has a circular dependency, so at least one must be overridden.
 *
 * TODO:
 *   in the performance critical path of mapping an attachment to a Java type,
 *   most of the type the Java type to which it binds to is known in advance.
 *   for this reason, it's better to prepare an 'accessor' object for each
 *   kind of conversion. In that way we can avoid the computation of this
 *   in the pritical path, which is redundant.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AttachmentBlock {

    public static AttachmentBlock fromDataHandler(String cid,DataHandler dh) {
        return new DataHandlerImpl(cid,dh);
    }

    public static AttachmentBlock fromSAAJ(AttachmentPart part) {
        return new SAAJImpl(part);
    }

    public static AttachmentBlock fromByteArray(String cid,byte[] data, int start, int len, String mimeType ) {
        return new ByteArrayImpl(cid,data,start,len,mimeType);
    }

    public static AttachmentBlock fromByteArray(String cid,byte[] data, String mimeType) {
        return new ByteArrayImpl(cid,data,0,data.length,mimeType);
    }

    public static AttachmentBlock fromJAXB(String cid, JAXBBridgeInfo bridgeInfo, RuntimeContext rtContext, String mimeType) {
        return new JAXBImpl(cid,bridgeInfo,rtContext,mimeType);
    }

    /**
     * No derived class outside this class.
     */
    private AttachmentBlock() {}

    /**
     * Content ID of the attachment. Uniquely identifies an attachment.
     */
    public abstract String getId();

    /**
     * Gets the WSDL part name of this attachment.
     *
     * <p>
     * According to WSI AP 1.0
     * <PRE>
     * 3.8 Value-space of Content-Id Header
     *   Definition: content-id part encoding
     *   The "content-id part encoding" consists of the concatenation of:
     * The value of the name attribute of the wsdl:part element referenced by the mime:content, in which characters disallowed in content-id headers (non-ASCII characters as represented by code points above 0x7F) are escaped as follows:
     *     o Each disallowed character is converted to UTF-8 as one or more bytes.
     *     o Any bytes corresponding to a disallowed character are escaped with the URI escaping mechanism (that is, converted to %HH, where HH is the hexadecimal notation of the byte value).
     *     o The original character is replaced by the resulting character sequence.
     * The character '=' (0x3D).
     * A globally unique value such as a UUID.
     * The character '@' (0x40).
     * A valid domain name under the authority of the entity constructing the message.
     * </PRE>
     *
     * So a wsdl:part fooPart will be encoded as:
     *      <fooPart=somereallybignumberlikeauuid@example.com>
     *
     * @return null
     *      if the parsing fails.
     */
    public final String getWSDLPartName(){
        String cId = getId();

        int index = cId.lastIndexOf('@', cId.length());
        if(index == -1){
            return null;
        }
        String localPart = cId.substring(0, index);
        index = localPart.lastIndexOf('=', localPart.length());
        if(index == -1){
            return null;
        }
        try {
            return java.net.URLDecoder.decode(localPart.substring(0, index), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new SerializationException(e);
        }
    }

    /**
     * Gets the MIME content-type of this attachment.
     */
    public abstract String getContentType();

    /**
     * Gets the attachment as an exact-length byte array.
     */
    // not so fast but useful default implementation
    public byte[] asByteArray() {
        try {
            return ASCIIUtility.getBytes(asInputStream());
        } catch (IOException e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * Gets the attachment as a {@link DataHandler}.
     */
    public abstract DataHandler asDataHandler();

    /**
     * Gets the attachment as a {@link Source}.
     * Note that there's no guarantee that the attachment is actually an XML.
     */
    public Source asSource() {
        return new StreamSource(asInputStream());
    }

    /**
     * Obtains this attachment as an {@link InputStream}.
     */
    // not-so-efficient but useful default implementation.
    public InputStream asInputStream() {
        ByteOutputStream bos = new ByteOutputStream();
        try {
            writeTo(bos);
        } catch (IOException e) {
            throw new WebServiceException(e);
        }
        return bos.newInputStream();
    }

    /**
     * Deserializes this attachment by using JAXB into {@link JAXBBridgeInfo}.
     *
     * TODO: this abstraction is wrong.
     */
    public final void deserialize(BridgeContext bc, JAXBBridgeInfo bi) {
        bi.deserialize(asInputStream(),bc);
    }

    /**
     * Adds this attachment as an {@link AttachmentPart} into the given {@link SOAPMessage}.
     */
    // not so fast but useful default
    public void addTo(SOAPMessage msg) throws SOAPException {
        AttachmentPart part = msg.createAttachmentPart(asDataHandler());
        part.setContentId(getId());
        //it may be safe to say the encoding is binary meaning the bytes are not subjected any
        //specific encoding.
        part.setMimeHeader("Content-transfer-encoding", "binary");
        msg.addAttachmentPart(part);
    }

    /**
     * Writes the contents of the attachment into the given stream.
     */
    // not so fast but useful default
    public void writeTo(OutputStream os) throws IOException {
        ASCIIUtility.copyStream(asInputStream(),os);
    }

    /**
     * Deserializes this attachment into an {@link Image}.
     *
     * @return null if the decoding fails.
     */
    public final Image asImage() throws IOException {
        // technically we should check the MIME type here, but
        // normally images can be content-sniffed.
        // so the MIME type check will only make us slower and draconian, both of which
        // JAXB 2.0 isn't interested.
        return ImageIO.read(asInputStream());
    }


    /**
     * {@link AttachmentBlock} stored as SAAJ {@link AttachmentPart}.
     */
    private static final class SAAJImpl extends AttachmentBlock {
        private final AttachmentPart ap;

        public SAAJImpl(AttachmentPart part) {
            this.ap = part;
        }

        public String getId() {
            return ap.getContentId();
        }

        public String getContentType() {
            return ap.getContentType();
        }

        public byte[] asByteArray() {
            try {
                return ap.getRawContentBytes();
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        public DataHandler asDataHandler() {
            try {
                return ap.getDataHandler();
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        public Source asSource() {
            try {
                return new StreamSource(ap.getRawContent());
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        public InputStream asInputStream() {
            try {
                return ap.getRawContent();
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        public void addTo(SOAPMessage msg) {
            msg.addAttachmentPart(ap);
        }
    }

    /**
     * {@link AttachmentBlock} stored as a {@link DataHandler}.
     */
    private static final class DataHandlerImpl extends AttachmentBlock {
        private final String cid;
        private final DataHandler dh;

        public DataHandlerImpl(String cid, DataHandler dh) {
            this.cid = cid;
            this.dh = dh;
        }

        public String getId() {
            return cid;
        }

        public String getContentType() {
            return dh.getContentType();
        }

        public DataHandler asDataHandler() {
            return dh;
        }

        public InputStream asInputStream() {
            try {
                return dh.getInputStream();
            } catch (IOException e) {
                throw new WebServiceException(e);
            }
        }
    }

    private static final class ByteArrayImpl extends AttachmentBlock {
        private final String cid;
        private byte[] data;
        private int start;
        private int len;
        private final String mimeType;

        public ByteArrayImpl(String cid, byte[] data, int start, int len, String mimeType) {
            this.cid = cid;
            this.data = data;
            this.start = start;
            this.len = len;
            this.mimeType = mimeType;
        }

        public String getId() {
            return cid;
        }

        public String getContentType() {
            return mimeType;
        }

        public byte[] asByteArray() {
            if(start!=0 || len!=data.length) {
                // if our buffer isn't exact, switch to the exact one
                byte[] exact = new byte[len];
                System.arraycopy(data,start,exact,0,len);
                start = 0;
                data = exact;
            }
            return data;
        }

        public DataHandler asDataHandler() {
            return new DataHandler(new ByteArrayDataSource(data,start,len,getContentType()));
        }

        public InputStream asInputStream() {
            return new ByteArrayInputStream(data,start,len);
        }

        public void addTo(SOAPMessage msg) throws SOAPException {
            AttachmentPart part = msg.createAttachmentPart();
            part.setRawContentBytes(data,start,len,getContentType());
            part.setContentId(getId());
            msg.addAttachmentPart(part);
        }

        public void writeTo(OutputStream os) throws IOException {
            os.write(data,start,len);
        }
    }

    /**
     * {@link AttachmentPart} that stores the value as a JAXB object.
     *
     * TODO: if it's common for an attahchment to be written more than once,
     * it's better to cache the marshalled result, as it is expensive operation.
     */
    private static final class JAXBImpl extends AttachmentBlock implements DataSource {
        private final String id;
        private final JAXBBridgeInfo bridgeInfo;
        private final RuntimeContext rtContext;
        private final String type;

        public JAXBImpl(String id, JAXBBridgeInfo bridgeInfo, RuntimeContext rtContext, String type) {
            this.id = id;
            this.bridgeInfo = bridgeInfo;
            this.rtContext = rtContext;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public String getContentType() {
            return type;
        }

        public DataHandler asDataHandler() {
            return new DataHandler(this);
        }

        public InputStream getInputStream() {
            return asInputStream();
        }

        public String getName() {
            return null;
        }

        public OutputStream getOutputStream() {
            throw new UnsupportedOperationException();
        }

        public void writeTo(OutputStream os) {
            bridgeInfo.serialize(rtContext.getBridgeContext(),os,null);
        }
    }
}
