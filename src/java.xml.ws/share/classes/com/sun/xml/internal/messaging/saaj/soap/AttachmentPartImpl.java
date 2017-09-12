/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.messaging.saaj.soap;

import com.sun.xml.internal.messaging.saaj.SOAPExceptionImpl;

import com.sun.xml.internal.messaging.saaj.packaging.mime.util.ASCIIUtility;

import com.sun.xml.internal.messaging.saaj.packaging.mime.Header;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimePartDataSource;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.InternetHeaders;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeBodyPart;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.MimeUtility;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.messaging.saaj.util.LogDomainConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.*;
import javax.xml.soap.*;
import com.sun.xml.internal.org.jvnet.mimepull.MIMEPart;

/**
 * Implementation of attachments.
 *
 * @author Anil Vijendran (akv@eng.sun.com)
 */
public class AttachmentPartImpl extends AttachmentPart {

    protected static final Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_DOMAIN,
                         "com.sun.xml.internal.messaging.saaj.soap.LocalStrings");

    private final MimeHeaders headers;
    private MimeBodyPart rawContent = null;
    private DataHandler dataHandler = null;

    //alternate impl that uses a MIMEPart
    private MIMEPart mimePart = null;

    public AttachmentPartImpl() {
        headers = new MimeHeaders();

        // initialization from here should cover most of cases;
        // if not, it would be necessary to call
        //   AttachmentPartImpl.initializeJavaActivationHandlers()
        // explicitly by programmer
        initializeJavaActivationHandlers();
    }

    public AttachmentPartImpl(MIMEPart part) {
        headers = new MimeHeaders();
        mimePart = part;
        List<? extends com.sun.xml.internal.org.jvnet.mimepull.Header> hdrs = part.getAllHeaders();
        for (com.sun.xml.internal.org.jvnet.mimepull.Header hd : hdrs) {
            headers.addHeader(hd.getName(), hd.getValue());
        }
    }

    @Override
    public int getSize() throws SOAPException {
        if (mimePart != null) {
            try {
                return mimePart.read().available();
            } catch (IOException e) {
                return -1;
            }
        }
        if ((rawContent == null) && (dataHandler == null))
            return 0;

        if (rawContent != null) {
            try {
                return rawContent.getSize();
            } catch (Exception ex) {
                log.log(
                    Level.SEVERE,
                    "SAAJ0573.soap.attachment.getrawbytes.ioexception",
                    new String[] { ex.getLocalizedMessage()});
                throw new SOAPExceptionImpl("Raw InputStream Error: " + ex);
            }
        } else {
            ByteOutputStream bout = new ByteOutputStream();
            try {
                dataHandler.writeTo(bout);
            } catch (IOException ex) {
                log.log(
                    Level.SEVERE,
                    "SAAJ0501.soap.data.handler.err",
                    new String[] { ex.getLocalizedMessage()});
                throw new SOAPExceptionImpl("Data handler error: " + ex);
            }
            return bout.size();
        }
    }

    @Override
    public void clearContent() {
        if (mimePart != null) {
            mimePart.close();
            mimePart = null;
        }
        dataHandler = null;
        rawContent = null;
    }

    @Override
    public Object getContent() throws SOAPException {
        try {
            if (mimePart != null) {
                //return an inputstream
                return mimePart.read();
            }
            if (dataHandler != null) {
                return getDataHandler().getContent();
            } else if (rawContent != null) {
                return rawContent.getContent();
            } else {
                log.severe("SAAJ0572.soap.no.content.for.attachment");
                throw new SOAPExceptionImpl("No data handler/content associated with this attachment");
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, "SAAJ0575.soap.attachment.getcontent.exception", ex);
            throw new SOAPExceptionImpl(ex.getLocalizedMessage());
        }
    }

    @Override
    public void setContent(Object object, String contentType)
        throws IllegalArgumentException {
        if (mimePart != null) {
            mimePart.close();
            mimePart = null;
        }
        DataHandler dh = new DataHandler(object, contentType);

        setDataHandler(dh);
    }


    @Override
    public DataHandler getDataHandler() throws SOAPException {
        if (mimePart != null) {
            //return an inputstream
            return new DataHandler(new DataSource() {

                @Override
                public InputStream getInputStream() throws IOException {
                    return mimePart.read();
                }

                @Override
                public OutputStream getOutputStream() throws IOException {
                    throw new UnsupportedOperationException("getOutputStream cannot be supported : You have enabled LazyAttachments Option");
                }

                @Override
                public String getContentType() {
                    return mimePart.getContentType();
                }

                @Override
                public String getName() {
                    return "MIMEPart Wrapper DataSource";
                }
            });
        }
        if (dataHandler == null) {
            if (rawContent != null) {
                return new DataHandler(new MimePartDataSource(rawContent));
            }
            log.severe("SAAJ0502.soap.no.handler.for.attachment");
            throw new SOAPExceptionImpl("No data handler associated with this attachment");
        }
        return dataHandler;
    }

    @Override
    public void setDataHandler(DataHandler dataHandler)
        throws IllegalArgumentException {
        if (mimePart != null) {
            mimePart.close();
            mimePart = null;
        }
        if (dataHandler == null) {
            log.severe("SAAJ0503.soap.no.null.to.dataHandler");
            throw new IllegalArgumentException("Null dataHandler argument to setDataHandler");
        }
        this.dataHandler = dataHandler;
        rawContent = null;

        if (log.isLoggable(Level.FINE))
            log.log(Level.FINE, "SAAJ0580.soap.set.Content-Type",
                    new String[] { dataHandler.getContentType() });
        setMimeHeader("Content-Type", dataHandler.getContentType());
    }

    @Override
    public void removeAllMimeHeaders() {
        headers.removeAllHeaders();
    }

    @Override
    public void removeMimeHeader(String header) {
        headers.removeHeader(header);
    }

    @Override
    public String[] getMimeHeader(String name) {
        return headers.getHeader(name);
    }

    @Override
    public void setMimeHeader(String name, String value) {
        headers.setHeader(name, value);
    }

    @Override
    public void addMimeHeader(String name, String value) {
        headers.addHeader(name, value);
    }

    @Override
    public Iterator<MimeHeader> getAllMimeHeaders() {
        return headers.getAllHeaders();
    }

    @Override
    public Iterator<MimeHeader> getMatchingMimeHeaders(String[] names) {
        return headers.getMatchingHeaders(names);
    }

    @Override
    public Iterator<MimeHeader> getNonMatchingMimeHeaders(String[] names) {
        return headers.getNonMatchingHeaders(names);
    }

    boolean hasAllHeaders(MimeHeaders hdrs) {
        if (hdrs != null) {
            Iterator i = hdrs.getAllHeaders();
            while (i.hasNext()) {
                MimeHeader hdr = (MimeHeader) i.next();
                String[] values = headers.getHeader(hdr.getName());
                boolean found = false;

                if (values != null) {
                    for (int j = 0; j < values.length; j++)
                        if (hdr.getValue().equalsIgnoreCase(values[j])) {
                            found = true;
                            break;
                        }
                }

                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }

    MimeBodyPart getMimePart() throws SOAPException {
        try {
            if (this.mimePart != null) {
                return new MimeBodyPart(mimePart);
            }
            if (rawContent != null) {
                copyMimeHeaders(headers, rawContent);
                return rawContent;
            }

            MimeBodyPart envelope = new MimeBodyPart();

            envelope.setDataHandler(dataHandler);
            copyMimeHeaders(headers, envelope);

            return envelope;
        } catch (Exception ex) {
            log.severe("SAAJ0504.soap.cannot.externalize.attachment");
            throw new SOAPExceptionImpl("Unable to externalize attachment", ex);
        }
    }

    public static void copyMimeHeaders(MimeHeaders headers, MimeBodyPart mbp)
        throws SOAPException {

        Iterator i = headers.getAllHeaders();

        while (i.hasNext())
            try {
                MimeHeader mh = (MimeHeader) i.next();

                mbp.setHeader(mh.getName(), mh.getValue());
            } catch (Exception ex) {
                log.severe("SAAJ0505.soap.cannot.copy.mime.hdr");
                throw new SOAPExceptionImpl("Unable to copy MIME header", ex);
            }
    }

    public static void copyMimeHeaders(MimeBodyPart mbp, AttachmentPartImpl ap)
        throws SOAPException {
        try {
            List hdr = mbp.getAllHeaders();
            int sz = hdr.size();
            for( int i=0; i<sz; i++ ) {
                Header h = (Header)hdr.get(i);
                if(h.getName().equalsIgnoreCase("Content-Type"))
                    continue;   // skip
                ap.addMimeHeader(h.getName(), h.getValue());
            }
        } catch (Exception ex) {
            log.severe("SAAJ0506.soap.cannot.copy.mime.hdrs.into.attachment");
            throw new SOAPExceptionImpl(
                "Unable to copy MIME headers into attachment",
                ex);
        }
    }

    @Override
    public  void setBase64Content(InputStream content, String contentType)
        throws SOAPException {

        if (mimePart != null) {
            mimePart.close();
            mimePart = null;
        }
        dataHandler = null;
        InputStream decoded = null;
        ByteOutputStream bos = null;
        try {
            decoded = MimeUtility.decode(content, "base64");
            InternetHeaders hdrs = new InternetHeaders();
            hdrs.setHeader("Content-Type", contentType);
            //TODO: reading the entire attachment here is ineffcient. Somehow the MimeBodyPart
            // Ctor with inputStream causes problems based on the InputStream
            // has markSupported()==true
            bos = new ByteOutputStream();
            bos.write(decoded);
            rawContent = new MimeBodyPart(hdrs, bos.getBytes(), bos.getCount());
            setMimeHeader("Content-Type", contentType);
        } catch (Exception e) {
            log.log(Level.SEVERE, "SAAJ0578.soap.attachment.setbase64content.exception", e);
            throw new SOAPExceptionImpl(e.getLocalizedMessage());
        } finally {
            if (bos != null)
                bos.close();
            try {
                if (decoded != null)
                decoded.close();
            } catch (IOException ex) {
                throw new SOAPException(ex);
            }
        }
    }

    @Override
    public  InputStream getBase64Content() throws SOAPException {
        InputStream stream;
        if (mimePart != null) {
            stream = mimePart.read();
        } else if (rawContent != null) {
            try {
                 stream = rawContent.getInputStream();
            } catch (Exception e) {
                log.log(Level.SEVERE,"SAAJ0579.soap.attachment.getbase64content.exception", e);
                throw new SOAPExceptionImpl(e.getLocalizedMessage());
            }
        } else if (dataHandler != null) {
            try {
                stream = dataHandler.getInputStream();
            } catch (IOException e) {
                log.severe("SAAJ0574.soap.attachment.datahandler.ioexception");
                throw new SOAPExceptionImpl("DataHandler error" + e);
            }
        } else {
            log.severe("SAAJ0572.soap.no.content.for.attachment");
            throw new SOAPExceptionImpl("No data handler/content associated with this attachment");
        }

        //TODO: Write a BASE64EncoderInputStream instead,
        // this code below is inefficient
        // where we are trying to read the whole attachment first
        int len;
        int size = 1024;
        byte [] buf;
        if (stream != null) {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(size);
                //TODO: try and optimize this on the same lines as
                // ByteOutputStream : to eliminate the temp buffer here
                OutputStream ret = MimeUtility.encode(bos, "base64");
                buf = new byte[size];
                while ((len = stream.read(buf, 0, size)) != -1) {
                    ret.write(buf, 0, len);
                }
                ret.flush();
                buf = bos.toByteArray();
                return new ByteArrayInputStream(buf);
            } catch (Exception e) {
                // throw new SOAPException
                log.log(Level.SEVERE,"SAAJ0579.soap.attachment.getbase64content.exception", e);
                throw new SOAPExceptionImpl(e.getLocalizedMessage());
            } finally {
                try {
                    stream.close();
                } catch (IOException ex) {
                  //close the stream
                }
            }
        } else {
          //throw  new SOAPException
          log.log(Level.SEVERE,"SAAJ0572.soap.no.content.for.attachment");
          throw new SOAPExceptionImpl("No data handler/content associated with this attachment");
        }
    }

    @Override
    public void setRawContent(InputStream content, String contentType)
        throws SOAPException {
        if (mimePart != null) {
            mimePart.close();
            mimePart = null;
        }
        dataHandler = null;
        ByteOutputStream bos = null;
        try {
            InternetHeaders hdrs = new InternetHeaders();
            hdrs.setHeader("Content-Type", contentType);
            //TODO: reading the entire attachment here is ineffcient. Somehow the MimeBodyPart
            // Ctor with inputStream causes problems based on whether the InputStream has
            // markSupported()==true or false
            bos = new ByteOutputStream();
            bos.write(content);
            rawContent = new MimeBodyPart(hdrs, bos.getBytes(), bos.getCount());
            setMimeHeader("Content-Type", contentType);
        } catch (Exception e) {
            log.log(Level.SEVERE, "SAAJ0576.soap.attachment.setrawcontent.exception", e);
            throw new SOAPExceptionImpl(e.getLocalizedMessage());
        } finally {
            if (bos != null)
                bos.close();
            try {
                content.close();
            } catch (IOException ex) {
                throw new SOAPException(ex);
            }
        }
    }

   /*
    public void setRawContentBytes(byte[] content, String contentType)
        throws SOAPException {
        if (content == null) {
            throw new SOAPExceptionImpl("Null content passed to setRawContentBytes");
        }
        dataHandler = null;
        try {
            InternetHeaders hdrs = new InternetHeaders();
            hdrs.setHeader("Content-Type", contentType);
            rawContent = new MimeBodyPart(hdrs, content, content.length);
            setMimeHeader("Content-Type", contentType);
        } catch (Exception e) {
            log.log(Level.SEVERE, "SAAJ0576.soap.attachment.setrawcontent.exception", e);
            throw new SOAPExceptionImpl(e.getLocalizedMessage());
        }
    } */

    @Override
    public void setRawContentBytes(
        byte[] content, int off, int len, String contentType)
        throws SOAPException {
        if (mimePart != null) {
            mimePart.close();
            mimePart = null;
        }
        if (content == null) {
            throw new SOAPExceptionImpl("Null content passed to setRawContentBytes");
        }
        dataHandler = null;
        try {
            InternetHeaders hdrs = new InternetHeaders();
            hdrs.setHeader("Content-Type", contentType);
            rawContent = new MimeBodyPart(hdrs, content, off, len);
            setMimeHeader("Content-Type", contentType);
        } catch (Exception e) {
            log.log(Level.SEVERE,
                "SAAJ0576.soap.attachment.setrawcontent.exception", e);
            throw new SOAPExceptionImpl(e.getLocalizedMessage());
        }
    }

    @Override
    public  InputStream getRawContent() throws SOAPException {
        if (mimePart != null) {
            return mimePart.read();
        }
        if (rawContent != null) {
            try {
                return rawContent.getInputStream();
            } catch (Exception e) {
                log.log(Level.SEVERE,"SAAJ0577.soap.attachment.getrawcontent.exception", e);
                throw new SOAPExceptionImpl(e.getLocalizedMessage());
            }
        } else if (dataHandler != null) {
            try {
                return dataHandler.getInputStream();
            } catch (IOException e) {
                log.severe("SAAJ0574.soap.attachment.datahandler.ioexception");
                throw new SOAPExceptionImpl("DataHandler error" + e);
            }
        } else {
            log.severe("SAAJ0572.soap.no.content.for.attachment");
            throw new SOAPExceptionImpl("No data handler/content associated with this attachment");
        }
    }

    @Override
    public  byte[] getRawContentBytes() throws SOAPException {
        InputStream ret;
        if (mimePart != null) {
            try {
                ret = mimePart.read();
                return ASCIIUtility.getBytes(ret);
            } catch (IOException ex) {
                log.log(Level.SEVERE,"SAAJ0577.soap.attachment.getrawcontent.exception", ex);
                throw new SOAPExceptionImpl(ex);
            }
        }
        if (rawContent != null) {
            try {
                ret = rawContent.getInputStream();
                return ASCIIUtility.getBytes(ret);
            } catch (Exception e) {
                log.log(Level.SEVERE,"SAAJ0577.soap.attachment.getrawcontent.exception", e);
                throw new SOAPExceptionImpl(e);
            }
        } else if (dataHandler != null) {
            try {
                ret = dataHandler.getInputStream();
                return ASCIIUtility.getBytes(ret);
            } catch (IOException e) {
                log.severe("SAAJ0574.soap.attachment.datahandler.ioexception");
                throw new SOAPExceptionImpl("DataHandler error" + e);
            }
        } else {
            log.severe("SAAJ0572.soap.no.content.for.attachment");
            throw new SOAPExceptionImpl("No data handler/content associated with this attachment");
        }
    }

    // attachments are equal if they are the same reference
    @Override
    public boolean equals(Object o) {
        return (this == o);
    }

    // In JDK 8 we get a warning if we implement equals() but not hashCode().
    // There is no intuitive value for this, the default one in Object is fine.
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public MimeHeaders getMimeHeaders() {
        return headers;
    }

    public static void initializeJavaActivationHandlers() {
        // DataHandler.writeTo() may search for DCH. So adding some default ones.
        try {
            CommandMap map = CommandMap.getDefaultCommandMap();
            if (map instanceof MailcapCommandMap) {
                MailcapCommandMap mailMap = (MailcapCommandMap) map;

                // registering our DCH since javamail's DCH doesn't handle
                if (!cmdMapInitialized(mailMap)) {
                    mailMap.addMailcap("text/xml;;x-java-content-handler=com.sun.xml.internal.messaging.saaj.soap.XmlDataContentHandler");
                    mailMap.addMailcap("application/xml;;x-java-content-handler=com.sun.xml.internal.messaging.saaj.soap.XmlDataContentHandler");
                    mailMap.addMailcap("application/fastinfoset;;x-java-content-handler=com.sun.xml.internal.messaging.saaj.soap.FastInfosetDataContentHandler");
                    //mailMap.addMailcap("multipart/*;;x-java-content-handler=com.sun.xml.internal.messaging.saaj.soap.MultipartDataContentHandler");
                    mailMap.addMailcap("image/*;;x-java-content-handler=com.sun.xml.internal.messaging.saaj.soap.ImageDataContentHandler");
                    mailMap.addMailcap("text/plain;;x-java-content-handler=com.sun.xml.internal.messaging.saaj.soap.StringDataContentHandler");
                }
            }
        } catch (Throwable t) {
            // ignore the exception.
        }
    }

    private static boolean cmdMapInitialized(MailcapCommandMap mailMap) {

        // checking fastinfoset handler, since this one is specific to SAAJ
        CommandInfo[] commands = mailMap.getAllCommands("application/fastinfoset");
        if (commands == null || commands.length == 0) {
            return false;
        }

        String saajClassName = "com.sun.xml.internal.ws.binding.FastInfosetDataContentHandler";
        for (CommandInfo command : commands) {
            String commandClass = command.getCommandClass();
            if (saajClassName.equals(commandClass)) {
                return true;
            }
        }
        return false;
    }
}
