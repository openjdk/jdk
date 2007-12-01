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

package com.sun.xml.internal.ws.encoding.xml;

import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ContentType;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.InternetHeaders;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeBodyPart;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeMultipart;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBTypeSerializer;
import com.sun.xml.internal.ws.protocol.xml.XMLMessageException;
import com.sun.xml.internal.ws.spi.runtime.WSConnection;
import com.sun.xml.internal.ws.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.util.FastInfosetReflection;
import com.sun.xml.internal.ws.util.FastInfosetUtil;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import java.io.BufferedInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.activation.DataHandler;

import javax.activation.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.soap.MimeHeaders;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.http.HTTPException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 *
 * @author WS Developement Team
 */
public final class XMLMessage {

    private static final Logger log = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".protocol.xml");

    // So that SAAJ registers DCHs for MIME types
    static {
        new com.sun.xml.internal.messaging.saaj.soap.AttachmentPartImpl();
    }

    private static final int PLAIN_XML_FLAG      = 1;       // 00001
    private static final int MIME_MULTIPART_FLAG = 2;       // 00010
    private static final int FI_ENCODED_FLAG     = 16;      // 10000

    private final DataRepresentation data;

    /**
     * Indicates when Fast Infoset should be used to serialize
     * this message.
     */
    protected boolean useFastInfoset = false;

    /**
     * Construct a message from an input stream. When messages are
     * received, there's two parts -- the transport headers and the
     * message content in a transport specific stream.
     */
    public XMLMessage(MimeHeaders headers, final InputStream in) {
        String ct = null;
        if (headers != null) {
            ct = getContentType(headers);
        }
        this.data = getData(ct, in);
        // TODO should headers be set on the data?
    }

    /**
     * Finds if the stream has some content or not
     *
     * @return null if there is no data
     *         else stream to be used
     */
    private InputStream hasSomeData(InputStream in) throws IOException {
        if (in != null) {
            if (in.available() < 1) {
                if (!in.markSupported()) {
                    in = new BufferedInputStream(in);
                }
                in.mark(1);
                if (in.read() != -1) {
                    in.reset();
                } else {
                    in = null;          // No data
                }
            }
        }
        return in;
    }

    private DataRepresentation getData(final String ct, InputStream in) {
        DataRepresentation data;
        try {
            in = hasSomeData(in);
            if (in == null) {
                return new NullContent();
            }
            if (ct != null) {
                ContentType contentType = new ContentType(ct);
                int contentTypeId = identifyContentType(contentType);
                boolean isFastInfoset = (contentTypeId & FI_ENCODED_FLAG) > 0;
                if ((contentTypeId & MIME_MULTIPART_FLAG) != 0) {
                    data = new XMLMultiPart(ct, in, isFastInfoset);
                } else if ((contentTypeId & PLAIN_XML_FLAG) != 0 || (contentTypeId & FI_ENCODED_FLAG) != 0) {
                    data = new XMLSource(in, isFastInfoset);
                } else {
                    data = new UnknownContent(ct, in);
                }
            } else {
                data = new NullContent();
            }
        } catch(Exception ex) {
            throw new WebServiceException(ex);
        }
        return data;
    }


    public XMLMessage(Source source, boolean useFastInfoset) {
        this.data = (source == null) ? new NullContent() : new XMLSource(source);
        this.useFastInfoset = useFastInfoset;

        this.data.getMimeHeaders().addHeader("Content-Type",
            useFastInfoset ? "application/fastinfoset" : "text/xml");
    }

    public XMLMessage(Exception err, boolean useFastInfoset) {
        this.data = new XMLErr(err);
        this.useFastInfoset = useFastInfoset;

        this.data.getMimeHeaders().addHeader("Content-Type",
            useFastInfoset ? "application/fastinfoset" : "text/xml");
    }

    public XMLMessage(DataSource ds, boolean useFastInfoset) {
        this.useFastInfoset = useFastInfoset;
        try {
            this.data = (ds == null) ? new NullContent() : getData(ds.getContentType(), ds.getInputStream());
        } catch(IOException ioe) {
            throw new WebServiceException(ioe);
        }

        String contentType = (ds != null) ? ds.getContentType() : null;
        contentType =  (contentType == null) ? contentType = "text/xml": contentType;
        this.data.getMimeHeaders().addHeader("Content-Type",
            !useFastInfoset ? contentType
                : contentType.replaceFirst("text/xml", "application/fastinfoset"));
    }

    public XMLMessage(Object object, JAXBContext context, boolean useFastInfoset) {
        this.data = (object == null) ? new NullContent() : new XMLJaxb(object, context);
        this.useFastInfoset = useFastInfoset;

        this.data.getMimeHeaders().addHeader("Content-Type",
            useFastInfoset ? "application/fastinfoset" : "text/xml");
    }


    public XMLMessage(Source source, Map<String, DataHandler> attachments, boolean useFastInfoset) {
        if (attachments == null) {
            this.data = (source == null) ? new NullContent() : new XMLSource(source);
        } else {
            if (source == null) {
                this.data = new UnknownContent(attachments);
            } else {
                this.data = new XMLMultiPart(source, attachments, useFastInfoset);
            }
        }

        this.useFastInfoset = useFastInfoset;
        this.data.getMimeHeaders().addHeader("Content-Type",
            useFastInfoset ? "application/fastinfoset" : "text/xml");
    }

    public XMLMessage(Object object, JAXBContext context, Map<String, DataHandler> attachments, boolean useFastInfoset) {
        if (attachments == null) {
            this.data = (object == null) ? new NullContent() : new XMLJaxb(object, context);
        } else {
            if (object == null) {
                this.data = new UnknownContent(attachments);
            } else {
                this.data = new XMLMultiPart(JAXBTypeSerializer.serialize(object, context), attachments, useFastInfoset);
            }
        }

        this.useFastInfoset = useFastInfoset;
        this.data.getMimeHeaders().addHeader("Content-Type",
            useFastInfoset ? "application/fastinfoset" : "text/xml");
    }

    /**
     * Returns true if the underlying encoding of this message is FI.
     */
    public boolean isFastInfoset() {
        return data.isFastInfoset();
    }

    /**
     * Returns true if the FI encoding should be used.
     */
    public boolean useFastInfoset() {
        return useFastInfoset;
    }

    /**
     * Returns true if the sender of this message accepts FI. Slow, but
     * should only be called once.
     */
    public boolean acceptFastInfoset() {
        return FastInfosetUtil.isFastInfosetAccepted(getMimeHeaders().getHeader("Accept"));
    }

    public Source getSource() {
        return data.getSource();
    }

    public DataSource getDataSource() {
        return data.getDataSource();
    }

    /**
     * Verify a contentType.
     *
     * @return
     * MIME_MULTIPART_FLAG | PLAIN_XML_FLAG
     * MIME_MULTIPART_FLAG | FI_ENCODED_FLAG;
     * PLAIN_XML_FLAG
     * FI_ENCODED_FLAG
     *
     */
    private static int identifyContentType(ContentType contentType) {
        String primary = contentType.getPrimaryType();
        String sub = contentType.getSubType();

        if (primary.equalsIgnoreCase("multipart") && sub.equalsIgnoreCase("related")) {
            String type = contentType.getParameter("type");
            if (type != null) {
                if (isXMLType(type)) {
                    return MIME_MULTIPART_FLAG | PLAIN_XML_FLAG;
                } else if (isFastInfosetType(type)) {
                    return MIME_MULTIPART_FLAG | FI_ENCODED_FLAG;
                }
            }
            return 0;
        } else if (isXMLType(primary, sub)) {
            return PLAIN_XML_FLAG;
        } else if (isFastInfosetType(primary, sub)) {
            return FI_ENCODED_FLAG;
        }
        return 0;
    }

    protected static boolean isXMLType(String primary, String sub) {
        return (primary.equalsIgnoreCase("text") || primary.equalsIgnoreCase("application")) && sub.equalsIgnoreCase("xml");
    }

    protected static boolean isXMLType(String type) {
        return type.toLowerCase().startsWith("text/xml") ||
            type.toLowerCase().startsWith("application/xml");
    }

    protected static boolean isFastInfosetType(String primary, String sub) {
        return primary.equalsIgnoreCase("application") && sub.equalsIgnoreCase("fastinfoset");
    }

    protected static boolean isFastInfosetType(String type) {
        return type.toLowerCase().startsWith("application/fastinfoset");
    }

    /**
     * Ideally this should be called just before writing the message
     */
    public MimeHeaders getMimeHeaders() {
        return data.getMimeHeaders();
    }

    private static String getContentType(MimeHeaders headers) {
        String[] values = headers.getHeader("Content-Type");
        return (values == null) ? null : values[0];
    }

    public int getStatus() {
        return data.getStatus();
    }

    public void writeTo(OutputStream out) throws IOException {
        data.writeTo(out,useFastInfoset);
    }

    public Source getPayload() {
        return data.getPayload();
    }

    public Map<String, DataHandler> getAttachments() {
        return data.getAttachments();
    }

    public Object getPayload(JAXBContext context) {
        // Get a copy of Source using getPayload() and use it to deserialize
        // to JAXB object
        return JAXBTypeSerializer.deserialize(getPayload(), context);
    }

    /**
     * Defines operations available regardless of the actual in-memory data representation.
     */
    private static abstract class DataRepresentation {
        /**
         * Can be called multiple times. Typically from XMLLogicalMessageImpl
         */
        abstract Source getPayload();

        /**
         * Should be called only once. Once this is called, don't use this object anymore
         */
        abstract void writeTo(OutputStream out,boolean useFastInfoset) throws IOException;
        /**
         * Returns true whenever the underlying representation of this message
         * is a Fast Infoset stream.
         */
        abstract boolean isFastInfoset();

        /**
         * Should be called only once. Once this is called, don't use this object anymore
         */
        abstract Source getSource();

        /**
         * Should be called only once. Once this is called, don't use this object anymore
         */
        abstract DataSource getDataSource();

        /**
         * Should be called only once. Once this is called, don't use this object anymore
         */
        abstract Map<String, DataHandler> getAttachments();

        /**
         * Should contain Content-Type for this message.
         */
        abstract MimeHeaders getMimeHeaders();
        int getStatus() {
            return WSConnection.OK;
        }
    }


    /**
     * Data represented as a multi-part MIME message. It also has XML as
     * root part
     *
     * This class parses {@link MimeMultipart} lazily.
     */
    private static final class XMLMultiPart extends DataRepresentation {
        private DataSource dataSource;
        private MimeMultipart multipart;
        private XMLSource xmlSource;
        private boolean isFastInfoset;
        private final MimeHeaders headers = new MimeHeaders();

        public XMLMultiPart(final String contentType, final InputStream is, boolean isFastInfoset) {
            this.isFastInfoset = isFastInfoset;
            dataSource = new DataSource() {
                public InputStream getInputStream() {
                    return is;
                }

                public OutputStream getOutputStream() {
                    return null;
                }

                public String getContentType() {
                    return contentType;
                }

                public String getName() {
                    return "";
                }
            };
        }

        public XMLMultiPart(Source source, final Map<String, DataHandler> atts, boolean isFastInfoset) {
            this.isFastInfoset = isFastInfoset;
            multipart = new MimeMultipart("related");
            multipart.getContentType().setParameter("type", "text/xml");

            // Creates Primary part
            ByteOutputStream bos = new ByteOutputStream();
            new XMLSource(source).writeTo(bos, isFastInfoset);
            InternetHeaders headers = new InternetHeaders();
            headers.addHeader("Content-Type",
                isFastInfoset ? "application/fastinfoset" : "text/xml");
            MimeBodyPart rootPart = new MimeBodyPart(headers, bos.getBytes(),bos.getCount());
            multipart.addBodyPart(rootPart, 0);

            for(Map.Entry<String, DataHandler> e : atts.entrySet()) {
                MimeBodyPart part = new MimeBodyPart();
                part.setDataHandler(e.getValue());
                multipart.addBodyPart(part);
            }
        }

        public XMLMultiPart(DataSource dataSource, boolean isFastInfoset) {
            this.dataSource = dataSource;
            this.isFastInfoset = isFastInfoset;
        }

        public boolean isFastInfoset() {
            return isFastInfoset;
        }

        public DataSource getDataSource() {
            if (dataSource != null) {
                return dataSource;
            }
            else if (multipart != null) {
                return new DataSource() {
                    public InputStream getInputStream() {
                        try {
                            if (xmlSource != null) {
                                replaceRootPart(false);
                            }
                            ByteOutputStream bos = new ByteOutputStream();
                            multipart.writeTo(bos);
                            return bos.newInputStream();
                        } catch(MessagingException me) {
                            throw new XMLMessageException("xml.get.ds.err",me);
                        } catch(IOException ioe) {
                            throw new XMLMessageException("xml.get.ds.err",ioe);
                        }
                    }

                    public OutputStream getOutputStream() {
                        return null;
                    }

                    public String getContentType() {
                        return multipart.getContentType().toString();
                    }

                    public String getName() {
                        return "";
                    }
                };
            }
            return null;
        }

        private MimeBodyPart getRootPart() {
            try {
                convertToMultipart();
                ContentType contentType = multipart.getContentType();
                String startParam = contentType.getParameter("start");
                MimeBodyPart sourcePart = (startParam == null)
                    ? (MimeBodyPart)multipart.getBodyPart(0)
                    : (MimeBodyPart)multipart.getBodyPart(startParam);
                return sourcePart;
            }
            catch (MessagingException ex) {
                throw new XMLMessageException("xml.get.source.err",ex);
            }
        }

        private void replaceRootPart(boolean useFastInfoset) {
            if (xmlSource == null) {
                return;
            }
            try {
                MimeBodyPart sourcePart = getRootPart();
                String ctype = sourcePart.getContentType();
                multipart.removeBodyPart(sourcePart);

                ByteOutputStream bos = new ByteOutputStream();
                xmlSource.writeTo(bos, useFastInfoset);
                InternetHeaders headers = new InternetHeaders();
                headers.addHeader("Content-Type",
                    useFastInfoset ? "application/fastinfoset" : ctype);

                sourcePart = new MimeBodyPart(headers, bos.getBytes(),bos.getCount());
                multipart.addBodyPart(sourcePart, 0);
            }
            catch (MessagingException ex) {
                throw new XMLMessageException("xml.get.source.err",ex);
            }
        }

        private void convertToMultipart() {
            if (dataSource != null) {
                try {
                    multipart = new MimeMultipart(dataSource,null);
                    dataSource = null;
                } catch (MessagingException ex) {
                    throw new XMLMessageException("xml.get.source.err",ex);
                }
            }
        }

        /**
         * Returns root part of the MIME message
         */
        public Source getSource() {
            try {
                // If there is an XMLSource, return that
                if (xmlSource != null) {
                    return xmlSource.getPayload();
                }

                // Otherwise, parse MIME package and find root part
                convertToMultipart();
                MimeBodyPart sourcePart = getRootPart();
                ContentType ctype = new ContentType(sourcePart.getContentType());
                String baseType = ctype.getBaseType();

                // Return a StreamSource or FastInfosetSource depending on type
                if (isXMLType(baseType)) {
                    return new StreamSource(sourcePart.getInputStream());
                }
                else if (isFastInfosetType(baseType)) {
                    return FastInfosetReflection.FastInfosetSource_new(
                        sourcePart.getInputStream());
                }
                else {
                    throw new XMLMessageException(
                            "xml.root.part.invalid.Content-Type",
                            new Object[] {baseType});
                }
            } catch (MessagingException ex) {
                throw new XMLMessageException("xml.get.source.err",ex);
            } catch (Exception ioe) {
                throw new XMLMessageException("xml.get.source.err",ioe);
            }
        }

        public Source getPayload() {
            return getSource();
        }

        public void writeTo(OutputStream out, boolean useFastInfoset) {
            try {
                // If a source has been set, ensure MIME parsing
                if (xmlSource != null) {
                    convertToMultipart();
                }

                // Try to use dataSource whenever possible
                if (dataSource != null) {
                    // If already encoded correctly, just copy the bytes
                    if (isFastInfoset == useFastInfoset) {
                        InputStream is = dataSource.getInputStream();
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                        return;     // we're done
                    }
                    else {
                        // Parse MIME and create source for root part
                        xmlSource = new XMLSource(getSource());
                    }
                }

                // Finally, possibly re-encode root part and write it out
                replaceRootPart(useFastInfoset);
                multipart.writeTo(out);
            }
            catch(Exception e) {
                throw new WebServiceException(e);
            }
        }

        public Map<String, DataHandler> getAttachments() {
            // If a source has been set, ensure MIME parsing
            if (xmlSource != null) {
                convertToMultipart();
            }
            try {
                MimeBodyPart rootPart = getRootPart();
                Map<String, DataHandler> map = new HashMap<String, DataHandler>();
                int count = multipart.getCount();
                for(int i=0; i < count; i++) {
                    MimeBodyPart part = multipart.getBodyPart(i);
                    if (part != rootPart) {
                        map.put(part.getContentID(), part.getDataHandler());
                    }
                }
                return map;
            } catch (MessagingException ex) {
                throw new XMLMessageException("xml.get.source.err",ex);
            }
        }

        MimeHeaders getMimeHeaders() {
            headers.removeHeader("Content-Type");
            if (dataSource != null) {
                headers.addHeader("Content-Type", dataSource.getContentType());
            } else {
                if (multipart != null ) {
                    headers.addHeader("Content-Type", multipart.getContentType().toString());
                }
            }
            return headers;
        }

    }

    /**
     * Data represented as {@link Source}.
     */
    public static class XMLSource extends DataRepresentation {

        private Source source;
        private boolean isFastInfoset;
        private final MimeHeaders headers = new MimeHeaders();

        public XMLSource(InputStream in, boolean isFastInfoset) throws Exception {
            this.source = isFastInfoset ?
                FastInfosetReflection.FastInfosetSource_new(in)
                : new StreamSource(in);
            this.isFastInfoset = isFastInfoset;
        }

        public XMLSource(Source source) {
            this.source = source;
            this.isFastInfoset =
                ((source != null)?(source.getClass() == FastInfosetReflection.fiFastInfosetSource):false);
        }

        public boolean isFastInfoset() {
           return isFastInfoset;
        }

        /*
         * If there is a ByteInputStream available, then write it to the output
         * stream. Otherwise, use Transformer to write Source to output stream.
         */
        public void writeTo(OutputStream out, boolean useFastInfoset) {
            try {
                InputStream is = null;
                boolean canAvoidTransform = false;
                if (source instanceof StreamSource) {
                    is = ((StreamSource)source).getInputStream();
                    // If use of FI is requested, need to transcode
                    canAvoidTransform = !useFastInfoset;
                }
                else if (source.getClass() == FastInfosetReflection.fiFastInfosetSource) {
                    is = FastInfosetReflection.FastInfosetSource_getInputStream(source);
                    // If use of FI is not requested, need to transcode
                    canAvoidTransform = useFastInfoset;
                }

                if (canAvoidTransform && is != null && is instanceof ByteInputStream) {
                    ByteInputStream bis = (ByteInputStream)is;
                    // Reset the stream
                    byte[] buf = bis.getBytes();
                    out.write(buf);
                    bis.close();
                    return;
                }

                // TODO: Use an efficient transformer from SAAJ that knows how to optimally
                // write to FI results
                Transformer transformer = XmlUtil.newTransformer();
                transformer.transform(source,
                    useFastInfoset ? FastInfosetReflection.FastInfosetResult_new(out)
                                   : new StreamResult(out));
            }
            catch (Exception e) {
                throw new WebServiceException(e);
            }
        }

        public Source getSource() {
            return source;
        }

        DataSource getDataSource() {
            return new DataSource() {
                public InputStream getInputStream() {
                    try {
                        InputStream is = null;
                        if (source instanceof StreamSource) {
                            is = ((StreamSource)source).getInputStream();
                        } else if (source.getClass() == FastInfosetReflection.fiFastInfosetSource) {
                            is = FastInfosetReflection.FastInfosetSource_getInputStream(source);
                        }
                        if (is != null) {
                            return is;
                        }
                        // Copy source to result respecting desired encoding
                        ByteArrayBuffer bab = new ByteArrayBuffer();
                        Transformer transformer = XmlUtil.newTransformer();
                        transformer.transform(source, isFastInfoset() ?
                            FastInfosetReflection.FastInfosetResult_new(bab)
                            : new StreamResult(bab));
                        bab.close();
                        return bab.newInputStream();
                    } catch(Exception e) {
                        throw new WebServiceException(e);
                    }
                }

                public OutputStream getOutputStream() {
                    return null;
                }

                public String getContentType() {
                    return isFastInfoset() ? "application/fastinfoset" : "text/xml";
                }

                public String getName() {
                    return "";
                }
            };

        }

        /*
        * Usually called from logical handler
        * If there is a DOMSource, return that. Otherwise, return a copy of
        * the existing source.
        */
        public Source getPayload() {
            try {

                if (source instanceof DOMSource) {
                    return source;
                }

                InputStream is = null;

                if (source instanceof StreamSource) {
                    is = ((StreamSource)source).getInputStream();
                }
                else if (source.getClass() == FastInfosetReflection.fiFastInfosetSource) {
                    is = FastInfosetReflection.FastInfosetSource_getInputStream(source);
                }

                if (is != null && is instanceof ByteInputStream) {
                    ByteInputStream bis = (ByteInputStream)is;
                                  // Reset the stream
                    byte[] buf = bis.getBytes();

                    ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                     bis.close();
                    return isFastInfoset ?
                        FastInfosetReflection.FastInfosetSource_new(is)
                    : new StreamSource(bais);

                }

                // Copy source to result respecting desired encoding
                ByteArrayBuffer bab = new ByteArrayBuffer();
                Transformer transformer = XmlUtil.newTransformer();
                // Adding this to work with empty source. Is it JAXP bug ?
                Properties oprops = new Properties();
                oprops.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
                transformer.setOutputProperties(oprops);
                transformer.transform(source, isFastInfoset ?
                    FastInfosetReflection.FastInfosetResult_new(bab)
                    : new StreamResult(bab));
                bab.close();

                // Set internal source
                InputStream bis = (bab.size() == 0) ? null : bab.newInputStream();
                source = isFastInfoset ?
                    FastInfosetReflection.FastInfosetSource_new(bis)
                    : new StreamSource(bis);

                // Return fresh source back to handler
                bis = bab.newInputStream();
                return isFastInfoset ?
                    FastInfosetReflection.FastInfosetSource_new(bis)
                    : new StreamSource(bis);
            }
            catch (Exception e) {
                throw new WebServiceException(e);
            }
        }

        public Map<String, DataHandler> getAttachments() {
            return null;
        }

        MimeHeaders getMimeHeaders() {
            return headers;
        }

    }

    /**
     * Data represented as a JAXB object.
     */
    public static class XMLJaxb extends DataRepresentation {
        private final Object object;
        private final JAXBContext jaxbContext;
        private final MimeHeaders headers = new MimeHeaders();

        public XMLJaxb(Object object, JAXBContext jaxbContext) {
            this.object = object;
            this.jaxbContext = jaxbContext;
        }

        public void writeTo(OutputStream out, boolean useFastInfoset) {
            if (useFastInfoset) {
                JAXBTypeSerializer.serializeDocument(object,
                    XMLStreamWriterFactory.createFIStreamWriter(out),
                    jaxbContext);
            }
            else {
                JAXBTypeSerializer.serialize(object, out, jaxbContext);
            }
        }

        boolean isFastInfoset() {
            return false;
        }

        public Source getSource() {
            return JAXBTypeSerializer.serialize(object, jaxbContext);
        }

        DataSource getDataSource() {
            return new DataSource() {
                public InputStream getInputStream() {
                    ByteOutputStream bos = new ByteOutputStream();
                    JAXBTypeSerializer.serialize(object, bos, jaxbContext);
                    return bos.newInputStream();
                }

                public OutputStream getOutputStream() {
                    return null;
                }

                public String getContentType() {
                    return isFastInfoset() ? "application/fastinfoset" : "text/xml";
                }

                public String getName() {
                    return "";
                }
            };
        }

        /*
        * Usually called from logical handler
        * If there is a DOMSource, return that. Otherwise, return a copy of
        * the existing source.
        */
        public Source getPayload() {
            return getSource();
        }

        public Map<String, DataHandler> getAttachments() {
            return null;
        }

        MimeHeaders getMimeHeaders() {
            return headers;
        }

    }


    /**
     * Don't know about this content. It's conent-type is NOT the XML types
     * we recognize(text/xml, application/xml, multipart/related;text/xml etc).
     *
     * This could be used to represent image/jpeg etc
     */
    public static class UnknownContent extends DataRepresentation {
        private final String ct;
        private final InputStream in;
        private final MimeMultipart multipart;
        private final MimeHeaders headers = new MimeHeaders();

        public UnknownContent(String ct, InputStream in) {
            this.ct = ct;
            this.in = in;
            this.multipart = null;
        }

        public UnknownContent(Map<String, DataHandler> atts) {
            this.in = null;
            multipart = new MimeMultipart("mixed");
            for(Map.Entry<String, DataHandler> e : atts.entrySet()) {
                MimeBodyPart part = new MimeBodyPart();
                part.setDataHandler(e.getValue());
                multipart.addBodyPart(part);
            }
            this.ct = multipart.getContentType().toString();
        }

        public void writeTo(OutputStream out, boolean useFastInfoset) {
            try {
                if (multipart != null) {
                    multipart.writeTo(out);
                }
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            } catch (Exception ex) {
                throw new WebServiceException(ex);
            }
        }

        boolean isFastInfoset() {
            return false;
        }

        /**
         * NO XML content so return null
         */
        public Source getSource() {
            return null;
        }

        DataSource getDataSource() {
            return new DataSource() {
                public InputStream getInputStream() {
                    if (multipart != null) {
                        try {
                            ByteOutputStream bos = new ByteOutputStream();
                            multipart.writeTo(bos);
                            return bos.newInputStream();
                        } catch(Exception ioe) {
                            throw new WebServiceException(ioe);
                        }
                    }
                    return in;
                }

                public OutputStream getOutputStream() {
                    return null;
                }

                public String getContentType() {
                    assert ct != null;
                    return ct;
                }

                public String getName() {
                    return "";
                }
            };
        }

        /**
         * NO XML content so return null
         */
        public Source getPayload() {
            return null;
        }

        /**
         * JAXWS doesn't know about this conent. So we treate the whole content
         * as one payload.
         */
        public Map<String, DataHandler> getAttachments() {
            return null;
        }

        MimeHeaders getMimeHeaders() {
            headers.removeHeader("Content-Type");
            headers.addHeader("Content-Type", ct);
            return headers;
        }

    }

    /**
     * Represents HTTPException or anyother exception
     */
    private static final class XMLErr extends DataRepresentation {
        private final Exception err;
        private final MimeHeaders headers = new MimeHeaders();

        XMLErr(Exception err) {
            this.err = err;
        }

        public Source getPayload() {
            return null;
        }

        public Map<String, DataHandler> getAttachments() {
            return null;
        }

        public void writeTo(OutputStream out, boolean useFastInfoset) throws IOException {
            String msg = err.getMessage();
            if (msg == null) {
                msg = err.toString();
            }
            msg = "<err>"+msg+"</err>";

            if (useFastInfoset) {
                FastInfosetUtil.transcodeXMLStringToFI(msg, out);
            } else {
                out.write(msg.getBytes());
            }
        }

        boolean isFastInfoset() {
            return false;
        }

        Source getSource() {
            return null;
        }

        DataSource getDataSource() {
            return null;
        }

        @Override
        int getStatus() {
            if (err instanceof HTTPException) {
                return ((HTTPException)err).getStatusCode();
            }
            return WSConnection.INTERNAL_ERR;
        }

        MimeHeaders getMimeHeaders() {
            return headers;
        }
    }


    /**
     * There is no content to write.
     */
    private static final class NullContent extends DataRepresentation {
        private final MimeHeaders headers = new MimeHeaders();

        public Source getPayload() {
            return null;
        }

        public Map<String, DataHandler> getAttachments() {
            return null;
        }

        public void writeTo(OutputStream out, boolean useFastInfoset) throws IOException {
            // Nothing to do
        }

        boolean isFastInfoset() {
            return false;
        }

        Source getSource() {
            return null;
        }

        DataSource getDataSource() {
            return null;
        }

        MimeHeaders getMimeHeaders() {
            return headers;
        }
    }
}
