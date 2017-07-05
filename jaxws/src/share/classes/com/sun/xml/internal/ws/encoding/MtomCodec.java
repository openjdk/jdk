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
import com.sun.xml.internal.bind.DatatypeConverterImpl;
import com.sun.xml.internal.bind.v2.runtime.output.Encoded;
import com.sun.xml.internal.messaging.saaj.packaging.mime.util.OutputUtil;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.pipe.StreamSOAPCodec;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.message.MimeAttachmentSet;
import com.sun.xml.internal.ws.message.stream.StreamAttachment;
import com.sun.xml.internal.ws.util.ByteArrayDataSource;
import com.sun.xml.internal.ws.util.xml.XMLStreamReaderFilter;
import com.sun.xml.internal.ws.util.xml.XMLStreamWriterFilter;
import com.sun.xml.internal.org.jvnet.staxex.Base64Data;
import com.sun.xml.internal.org.jvnet.staxex.NamespaceContextEx;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamReaderEx;
import com.sun.xml.internal.org.jvnet.staxex.XMLStreamWriterEx;

import javax.activation.DataHandler;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.soap.MTOMFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Mtom messge Codec. It can be used even for non-soap message's mtom encoding.
 *
 * @author Vivek Pandey
 */
public class MtomCodec extends MimeCodec {
    public static final String XOP_XML_MIME_TYPE = "application/xop+xml";

    private final StreamSOAPCodec codec;

    // encoding related parameters
    private String boundary;
    private final String soapXopContentType;
    private String messageContentType;
    private final MTOMFeature mtomFeature;

    //This is the mtom attachment stream, we should write it just after the root part for decoder
    private final List<ByteArrayBuffer> mtomAttachmentStream = new ArrayList<ByteArrayBuffer>();

    MtomCodec(SOAPVersion version, StreamSOAPCodec codec, WebServiceFeature mtomFeature){
        super(version);
        this.codec = codec;
        createConteTypeHeader();
        this.soapXopContentType = XOP_XML_MIME_TYPE +";charset=utf-8;type=\""+version.contentType+"\"";
        if(mtomFeature == null)
            this.mtomFeature = new MTOMFeature();
        else
            this.mtomFeature = (MTOMFeature) mtomFeature;
    }

    private void createConteTypeHeader(){
        boundary = "uuid:" + UUID.randomUUID().toString();
        String boundaryParameter = "boundary=\"" + boundary +"\"";
        messageContentType = MULTIPART_RELATED_MIME_TYPE +
                ";type=\"" + XOP_XML_MIME_TYPE + "\";" +
                boundaryParameter +
                ";start-info=\"" + version.contentType + "\"";
    }

    /**
     * Return the soap 1.1 and soap 1.2 specific XOP packaged ContentType
     *
     * @return A non-null content type for soap11 or soap 1.2 content type
     */
    public ContentType getStaticContentType(Packet packet) {
        return getContentType(packet);
    }

    private ContentType getContentType(Packet packet){
        switch(version){
            case SOAP_11:
                return new ContentTypeImpl(messageContentType, (packet.soapAction == null)?"":packet.soapAction, null);
            case SOAP_12:
                if(packet.soapAction != null){
                    messageContentType += ";action=\""+packet.soapAction+"\"";
                }
                return new ContentTypeImpl(messageContentType, null, null);
        }
        //never happens
        return null;
    }

    public ContentType encode(Packet packet, OutputStream out) throws IOException {
        //get the current boundary thaat will be reaturned from this method
        mtomAttachmentStream.clear();
        ContentType contentType = getContentType(packet);

        if(packet.getMessage() != null){
            try {
                OutputUtil.writeln("--"+boundary, out);
                OutputUtil.writeln("Content-Type: "+ soapXopContentType,  out);
                OutputUtil.writeln("Content-Transfer-Encoding: binary", out);
                OutputUtil.writeln(out);
                MtomStreamWriter writer = new MtomStreamWriter(XMLStreamWriterFactory.create(out),out);
                packet.getMessage().writeTo(writer);
                XMLStreamWriterFactory.recycle(writer);
                OutputUtil.writeln(out);

                for(ByteArrayBuffer bos : mtomAttachmentStream){
                    bos.write(out);
                }

                //now write out the attachments in the message
                writeAttachments(packet.getMessage().getAttachments(),out);

                //write out the end boundary
                OutputUtil.writeAsAscii("--"+boundary, out);
                OutputUtil.writeAsAscii("--", out);

            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
        }
        //now create the boundary for next encode() call
        createConteTypeHeader();
        return contentType;
    }

    private class ByteArrayBuffer{
        final String contentId;

        private DataHandler dh;

        ByteArrayBuffer(@NotNull DataHandler dh) {
            this.dh = dh;
            this.contentId = encodeCid();
        }

        void write(OutputStream os) throws IOException {
            //build attachment frame
            OutputUtil.writeln("--"+boundary, os);
            writeMimeHeaders(dh.getContentType(), contentId, os);
            dh.writeTo(os);
            OutputUtil.writeln(os);
        }
    }

    private void writeMimeHeaders(String contentType, String contentId, OutputStream out) throws IOException {
        OutputUtil.writeln("Content-Type: " + contentType, out);
        String cid = contentId;
        if(cid != null && cid.length() >0 && cid.charAt(0) != '<')
            cid = '<' + cid + '>';
        OutputUtil.writeln("Content-Id: " + cid, out);
        OutputUtil.writeln("Content-Transfer-Encoding: binary", out);
        OutputUtil.writeln(out);
    }

    private void writeAttachments(AttachmentSet attachments, OutputStream out) throws IOException {
        for(Attachment att : attachments){
            //build attachment frame
            OutputUtil.writeln("--"+boundary, out);
            writeMimeHeaders(att.getContentType(), att.getContentId(), out);
            att.writeTo(out);
            OutputUtil.writeln(out);                    // write \r\n
        }
    }

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        throw new UnsupportedOperationException();
    }

    public MtomCodec copy() {
        return new MtomCodec(version, (StreamSOAPCodec)codec.copy(), mtomFeature);
    }

    private String encodeCid(){
        String cid="example.jaxws.sun.com";
        String name = UUID.randomUUID()+"@";
        return name + cid;
    }

    @Override
    protected void decode(MimeMultipartParser mpp, Packet packet) throws IOException {
        // we'd like to reuse those reader objects but unfortunately decoder may be reused
        // before the decoded message is completely used.

        XMLStreamReader mtomReader = new MtomXMLStreamReaderEx( mpp,
            XMLStreamReaderFactory.create(null, mpp.getRootPart().asInputStream(), true)
        );

        //TODO: remove this code after {@link StreamSOAPCodec#decode} is modified to
        //take AttachmentSet.
        if(codec instanceof com.sun.xml.internal.ws.encoding.StreamSOAPCodec){
            packet.setMessage(((com.sun.xml.internal.ws.encoding.StreamSOAPCodec)codec).decode(mtomReader, new MimeAttachmentSet(mpp)));
        }else{
            packet.setMessage(codec.decode(mtomReader));
        }
    }

    private class MtomStreamWriter extends XMLStreamWriterFilter implements XMLStreamWriterEx {
        private final OutputStream out;
        private final Encoded encoded = new Encoded();

        public MtomStreamWriter(XMLStreamWriter w, OutputStream out) {
            super(w);
            this.out = out;
        }

        public void writeBinary(byte[] data, int start, int len, String contentType) throws XMLStreamException {
            //check threshold and if less write as base64encoded value
            if(mtomFeature.getThreshold() > len){
                writeCharacters(DatatypeConverterImpl._printBase64Binary(data, start, len));
                return;
            }
            ByteArrayBuffer bab = new ByteArrayBuffer(new DataHandler(new ByteArrayDataSource(data, start, len, contentType)));
            writeBinary(bab);
        }

        public void writeBinary(DataHandler dataHandler) throws XMLStreamException {
            Base64Data data  = new Base64Data();
            data.set(dataHandler);
            writeBinary(new ByteArrayBuffer(data.getDataHandler()));
        }

        public OutputStream writeBinary(String contentType) throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        public void writePCDATA(CharSequence data) throws XMLStreamException {
            if(data == null)
                return;
            if(data instanceof Base64Data){
                Base64Data binaryData = (Base64Data)data;
                writeBinary(binaryData.getDataHandler());
                return;
            }
            writeCharacters(data.toString());
        }

        private void writeBinary(ByteArrayBuffer bab) {
            try {
                mtomAttachmentStream.add(bab);

                writer.writeCharacters("");   // Force completion of open elems
                writer.flush();
                //flush the underlying writer to write-out any cached data to the underlying
                // stream before writing directly to it
                //write out the xop reference
                out.write(XOP_PREF);
                encoded.set(bab.contentId);
                out.write(encoded.buf,0,encoded.len);
                out.write(XOP_SUFF);
            } catch (IOException e) {
                throw new WebServiceException(e);
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
        }

        private class MtomNamespaceContextEx implements NamespaceContextEx {
            private NamespaceContext nsContext;

            public MtomNamespaceContextEx(NamespaceContext nsContext) {
                this.nsContext = nsContext;
            }

            public Iterator<Binding> iterator() {
                throw new UnsupportedOperationException();
            }

            public String getNamespaceURI(String prefix) {
                return nsContext.getNamespaceURI(prefix);
            }

            public String getPrefix(String namespaceURI) {
                return nsContext.getPrefix(namespaceURI);
            }

            public Iterator getPrefixes(String namespaceURI) {
                return nsContext.getPrefixes(namespaceURI);
            }
        }

        public NamespaceContextEx getNamespaceContext() {
            NamespaceContext nsContext = writer.getNamespaceContext();
            return new MtomNamespaceContextEx(nsContext);
        }
    }

    private static class MtomXMLStreamReaderEx extends XMLStreamReaderFilter implements XMLStreamReaderEx {
        /**
         * The parser for the outer MIME 'shell'.
         */
        private final MimeMultipartParser mimeMP;

        private boolean xopReferencePresent = false;
        private Base64Data base64AttData;

        //values that will set to whether mtom or not as caller can call getPcData or getTextCharacters
        private int textLength;
        private int textStart;

        //To be used with #getTextCharacters
        private char[] base64EncodedText;

        public MtomXMLStreamReaderEx(MimeMultipartParser mimeMP, XMLStreamReader reader) {
            super(reader);
            this.mimeMP = mimeMP;
        }

        public CharSequence getPCDATA() throws XMLStreamException {
            if(xopReferencePresent){
                return base64AttData;
            }
            return reader.getText();
        }

        public NamespaceContextEx getNamespaceContext() {
            NamespaceContext nsContext = reader.getNamespaceContext();
            return new MtomNamespaceContextEx(nsContext);


        }

        public String getElementTextTrim() throws XMLStreamException {
            throw new UnsupportedOperationException();
        }

        private class MtomNamespaceContextEx implements NamespaceContextEx {
            private NamespaceContext nsContext;

            public MtomNamespaceContextEx(NamespaceContext nsContext) {
                this.nsContext = nsContext;
            }

            public Iterator<Binding> iterator() {
                throw new UnsupportedOperationException();
            }

            public String getNamespaceURI(String prefix) {
                return nsContext.getNamespaceURI(prefix);
            }

            public String getPrefix(String namespaceURI) {
                return nsContext.getPrefix(namespaceURI);
            }

            public Iterator getPrefixes(String namespaceURI) {
                return nsContext.getPrefixes(namespaceURI);
            }

        }

        public int getTextLength() {
            if (xopReferencePresent)
                return textLength;
            return reader.getTextLength();
        }

        public int getTextStart() {
            //TODO: check if this is correct
            if (xopReferencePresent)
                return 0;
            return reader.getTextStart();
        }

        public int getEventType() {
            if(xopReferencePresent)
                return XMLStreamConstants.CHARACTERS;
            return super.getEventType();
        }

        public int next() throws XMLStreamException {
            int event = reader.next();
            if ((event == XMLStreamConstants.START_ELEMENT) && reader.getLocalName().equals(XOP_LOCALNAME) && reader.getNamespaceURI().equals(XOP_NAMESPACEURI))
            {
                //its xop reference, take the URI reference
                String href = reader.getAttributeValue(null, "href");
                try {
                    StreamAttachment att = getAttachment(href);
                    if(att != null){
                        base64AttData = att.asBase64Data();
                        textLength = base64AttData.getDataLen();
                    }
                    textStart = 0;
                    xopReferencePresent = true;
                } catch (IOException e) {
                    throw new WebServiceException(e);
                }
                //move to the </xop:Include>
                try {
                    reader.next();
                } catch (XMLStreamException e) {
                    throw new WebServiceException(e);
                }
                return XMLStreamConstants.CHARACTERS;
            }
            if(xopReferencePresent){
                xopReferencePresent = false;
                textStart = 0;
                textLength = 0;
                base64EncodedText = null;
            }
            return event;
        }

        private String decodeCid(String cid) {
            try {
                cid = URLDecoder.decode(cid, "utf-8");
            } catch (UnsupportedEncodingException e) {
                //on recceiving side lets not fail now, try to look for it
                return cid;
            }
            return cid;
        }

        private boolean needToDecode(String cid){
            int numChars = cid.length();
            int i=0;
            char c;
            while (i < numChars) {
                c = cid.charAt(i++);
                switch (c) {
                    case '%':
                        return true;
                }
            }
            return false;
        }


        private StreamAttachment getAttachment(String cid) throws IOException {
            if (cid.startsWith("cid:"))
                cid = cid.substring(4, cid.length());
            StreamAttachment att = mimeMP.getAttachmentPart(cid);
            if(att == null && needToDecode(cid)){
                //try not be url decoding it - this is required for Indigo interop, they write content-id without escaping
                cid = decodeCid(cid);
                return mimeMP.getAttachmentPart(cid);
            }
            return att;
        }

        public char[] getTextCharacters() {
            if (xopReferencePresent) {
                char[] chars = new char[base64AttData.length()];
                base64AttData.writeTo(chars, 0);
                textLength = chars.length;
                return chars;
            }
            return reader.getTextCharacters();
        }

        public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) throws XMLStreamException {
            if(xopReferencePresent){
                int event = reader.getEventType();
                if(event != XMLStreamConstants.CHARACTERS){
                    //its invalid state - delegate it to underlying reader to throw the corrrect exception so that user
                    // always sees the uniform exception from the XMLStreamReader
                    throw new XMLStreamException("Invalid state: Expected CHARACTERS found :");
                }
                if(target == null){
                    throw new NullPointerException("target char array can't be null") ;
                }

                if(targetStart < 0 || length < 0 || sourceStart < 0 || targetStart >= target.length ||
                        (targetStart + length ) > target.length) {
                    throw new IndexOutOfBoundsException();
                }

                if(base64EncodedText != null){
                    base64EncodedText = new char[base64AttData.length()];
                    base64AttData.writeTo(base64EncodedText, 0);
                    textLength = base64EncodedText.length;
                    textStart = 0;
                }

                if((textStart + sourceStart) > textLength)
                    throw new IndexOutOfBoundsException();

                int available = textLength - sourceStart;
                if(available < 0){
                    throw new IndexOutOfBoundsException("sourceStart is greater than" +
                            "number of characters associated with this event");
                }

                int copiedLength = Math.min(available,length);

                System.arraycopy(base64EncodedText, getTextStart() + sourceStart , target, targetStart, copiedLength);
                textStart = sourceStart;
                return copiedLength;
            }
            return reader.getTextCharacters(sourceStart, target, targetStart, length);
        }

        public String getText() {
            if (xopReferencePresent) {
                String text =  base64AttData.toString();
                textLength = text.length();
            }
            return reader.getText();
        }
    }

    private static final byte[] XOP_PREF = encode("<Include xmlns=\"http://www.w3.org/2004/08/xop/include\" href=\"cid:");

    private static byte[] encode(String str) {
        try {
            return str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }
    }

    private static final byte[] XOP_SUFF = encode("\"/>");
    private static final String XOP_LOCALNAME = "Include";
    private static final String XOP_NAMESPACEURI = "http://www.w3.org/2004/08/xop/include";


    private static final Charset UTF8 = Charset.forName("UTF-8");
}
