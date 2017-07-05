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

/*
 * @(#)MimeBodyPart.java      1.52 03/02/12
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.internet;


import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import com.sun.xml.internal.messaging.saaj.packaging.mime.util.OutputUtil;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.messaging.saaj.util.FinalArrayList;

import javax.activation.DataHandler;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import javax.activation.DataSource;
import com.sun.xml.internal.org.jvnet.mimepull.MIMEPart;

/**
 * This class represents a MIME body part.
 * MimeBodyParts are contained in <code>MimeMultipart</code>
 * objects.
 * <p>
 * MimeBodyPart uses the <code>InternetHeaders</code> class to parse
 * and store the headers of that body part.
 *
 * <hr><strong>A note on RFC 822 and MIME headers</strong>
 *
 * RFC 822 header fields <strong>must</strong> contain only
 * US-ASCII characters. MIME allows non ASCII characters to be present
 * in certain portions of certain headers, by encoding those characters.
 * RFC 2047 specifies the rules for doing this. The MimeUtility
 * class provided in this package can be used to to achieve this.
 * Callers of the <code>setHeader</code>, <code>addHeader</code>, and
 * <code>addHeaderLine</code> methods are responsible for enforcing
 * the MIME requirements for the specified headers.  In addition, these
 * header fields must be folded (wrapped) before being sent if they
 * exceed the line length limitation for the transport (1000 bytes for
 * SMTP).  Received headers may have been folded.  The application is
 * responsible for folding and unfolding headers as appropriate.
 *
 * @author John Mani
 * @author Bill Shannon
 * @see MimeUtility
 */

public final class MimeBodyPart {

    /**
     * This part should be presented as an attachment.
     * @see #getDisposition
     * @see #setDisposition
     */
    public static final String ATTACHMENT = "attachment";

    /**
     * This part should be presented inline.
     * @see #getDisposition
     * @see #setDisposition
     */
    public static final String INLINE = "inline";


    // Paranoia:
    // allow this last minute change to be disabled if it causes problems
    private static boolean setDefaultTextCharset = true;

    static {
        try {
            String s = System.getProperty("mail.mime.setdefaulttextcharset");
            // default to true
            setDefaultTextCharset = s == null || !s.equalsIgnoreCase("false");
        } catch (SecurityException sex) {
            // ignore it
        }
    }

    /*
        Data is represented in one of three forms.
        Either we have a DataHandler, or byte[] as the raw content image, or the contentStream.
        It's OK to have more than one of them, provided that they are identical.
    */

    /**
     * The DataHandler object representing this MimeBodyPart's content.
     */
    private DataHandler dh;

    /**
     * Byte array that holds the bytes of the content of this MimeBodyPart.
     * Used in a pair with {@link #contentLength} to denote a regision of a buffer
     * as a valid data.
     */
    private byte[] content;
    private int contentLength;
    private int start = 0;

    /**
     * If the data for this body part was supplied by an
     * InputStream that implements the SharedInputStream interface,
     * <code>contentStream</code> is another such stream representing
     * the content of this body part.  In this case, <code>content</code>
     * will be null.
     *
     * @since   JavaMail 1.2
     */
    private InputStream contentStream;



    /**
     * The InternetHeaders object that stores all the headers
     * of this body part.
     */
    private final InternetHeaders headers;

    /**
     * The <code>MimeMultipart</code> object containing this <code>MimeBodyPart</code>,
     * if known.
     * @since   JavaMail 1.1
     */
    private MimeMultipart parent;

    private MIMEPart mimePart;

    /**
     * An empty MimeBodyPart object is created.
     * This body part maybe filled in by a client constructing a multipart
     * message.
     */
    public MimeBodyPart() {
        headers = new InternetHeaders();
    }

    /**
     * Constructs a MimeBodyPart by reading and parsing the data from
     * the specified input stream. The parser consumes data till the end
     * of the given input stream.  The input stream must start at the
     * beginning of a valid MIME body part and must terminate at the end
     * of that body part. <p>
     *
     * Note that the "boundary" string that delimits body parts must
     * <strong>not</strong> be included in the input stream. The intention
     * is that the MimeMultipart parser will extract each body part's bytes
     * from a multipart stream and feed them into this constructor, without
     * the delimiter strings.
     *
     * @param   is      the body part Input Stream
     *
     * @exception MessagingException in case of error
     */
    public MimeBodyPart(InputStream is) throws MessagingException {
        if (!(is instanceof ByteArrayInputStream) &&
                !(is instanceof BufferedInputStream) &&
                !(is instanceof SharedInputStream))
            is = new BufferedInputStream(is);

        headers = new InternetHeaders(is);

        if (is instanceof SharedInputStream) {
            SharedInputStream sis = (SharedInputStream) is;
            contentStream = sis.newStream(sis.getPosition(), -1);
        } else {
            ByteOutputStream bos = null;
            try {
                bos = new ByteOutputStream();
                bos.write(is);
                content = bos.getBytes();
                contentLength = bos.getCount();
            } catch (IOException ioex) {
                throw new MessagingException("Error reading input stream", ioex);
            } finally {
                if (bos != null)
                    bos.close();
            }
        }

    }

    /**
     * Constructs a MimeBodyPart using the given header and
     * content bytes. <p>
     *
     * Used by providers.
     *
     * @param   headers The header of this part
     * @param   content bytes representing the body of this part.
     * @param   len content length.
     */
    public MimeBodyPart(InternetHeaders headers, byte[] content, int len) {
        this.headers = headers;
        this.content = content;
        this.contentLength = len;
    }

    public MimeBodyPart(
        InternetHeaders headers, byte[] content, int start,  int len) {
        this.headers = headers;
        this.content = content;
        this.start = start;
        this.contentLength = len;
    }

    public MimeBodyPart(MIMEPart part) {
       mimePart = part;
       headers = new InternetHeaders();
       List<? extends com.sun.xml.internal.org.jvnet.mimepull.Header> hdrs = mimePart.getAllHeaders();
        for (com.sun.xml.internal.org.jvnet.mimepull.Header hd : hdrs) {
            headers.addHeader(hd.getName(), hd.getValue());
        }
    }
    /**
     * Return the containing <code>MimeMultipart</code> object,
     * or <code>null</code> if not known.
     * @return parent part.
     */
    public MimeMultipart getParent() {
        return parent;
    }

    /**
     * Set the parent of this <code>MimeBodyPart</code> to be the specified
     * <code>MimeMultipart</code>.  Normally called by <code>MimeMultipart</code>'s
     * <code>addBodyPart</code> method.  <code>parent</code> may be
     * <code>null</code> if the <code>MimeBodyPart</code> is being removed
     * from its containing <code>MimeMultipart</code>.
     * @param parent parent part
     * @since   JavaMail 1.1
     */
    public void setParent(MimeMultipart parent) {
        this.parent = parent;
    }

    /**
     * Return the size of the content of this body part in bytes.
     * Return -1 if the size cannot be determined. <p>
     *
     * Note that this number may not be an exact measure of the
     * content size and may or may not account for any transfer
     * encoding of the content. <p>
     *
     * This implementation returns the size of the <code>content</code>
     * array (if not null), or, if <code>contentStream</code> is not
     * null, and the <code>available</code> method returns a positive
     * number, it returns that number as the size.  Otherwise, it returns
     * -1.
     *
     * @return size in bytes, or -1 if not known
     */
    public int getSize() {

        if (mimePart != null) {
            try {
                return mimePart.read().available();
            } catch (IOException ex) {
                return -1;
            }
        }
        if (content != null)
            return contentLength;
        if (contentStream != null) {
            try {
                int size = contentStream.available();
                // only believe the size if it's greate than zero, since zero
                // is the default returned by the InputStream class itself
                if (size > 0)
                    return size;
            } catch (IOException ex) {
                // ignore it
            }
        }
        return -1;
    }

    /**
     * Return the number of lines for the content of this MimeBodyPart.
     * Return -1 if this number cannot be determined. <p>
     *
     * Note that this number may not be an exact measure of the
     * content length and may or may not account for any transfer
     * encoding of the content. <p>
     *
     * This implementation returns -1.
     *
     * @return number of lines, or -1 if not known
     */
     public int getLineCount() {
        return -1;
     }

    /**
     * Returns the value of the RFC 822 "Content-Type" header field.
     * This represents the content type of the content of this
     * body part. This value must not be null. If this field is
     * unavailable, "text/plain" should be returned. <p>
     *
     * This implementation uses <code>getHeader(name)</code>
     * to obtain the requisite header field.
     *
     * @return  Content-Type of this body part
     */
    public String getContentType() {
        if (mimePart != null) {
            return mimePart.getContentType();
        }
        String s = getHeader("Content-Type", null);
        if (s == null)
            s = "text/plain";

        return s;
    }

    /**
     * Is this MimeBodyPart of the specified MIME type?  This method
     * compares <strong>only the <code>primaryType</code> and
     * <code>subType</code></strong>.
     * The parameters of the content types are ignored. <p>
     *
     * For example, this method will return <code>true</code> when
     * comparing a MimeBodyPart of content type <strong>"text/plain"</strong>
     * with <strong>"text/plain; charset=foobar"</strong>. <p>
     *
     * If the <code>subType</code> of <code>mimeType</code> is the
     * special character '*', then the subtype is ignored during the
     * comparison.
     *
     * @param mimeType string
     * @return true if it is valid mime type
     */
    public boolean isMimeType(String mimeType) {
        boolean result;
        // XXX - lots of room for optimization here!
        try {
            ContentType ct = new ContentType(getContentType());
            result = ct.match(mimeType);
        } catch (ParseException ex) {
            result = getContentType().equalsIgnoreCase(mimeType);
        }
        return result;
    }

    /**
     * Returns the value of the "Content-Disposition" header field.
     * This represents the disposition of this part. The disposition
     * describes how the part should be presented to the user. <p>
     *
     * If the Content-Disposition field is unavailable,
     * null is returned. <p>
     *
     * This implementation uses <code>getHeader(name)</code>
     * to obtain the requisite header field.
     *
     * @return content disposition
     * @exception MessagingException in case of error
     *
     * @see #headers
     */
    public String getDisposition() throws MessagingException {
        String s = getHeader("Content-Disposition", null);

        if (s == null)
            return null;

        ContentDisposition cd = new ContentDisposition(s);
        return cd.getDisposition();
    }

    /**
     * Set the "Content-Disposition" header field of this body part.
     * If the disposition is null, any existing "Content-Disposition"
     * header field is removed.
     *
     * @param disposition value
     *
     * @exception MessagingException in case of error
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     */
    public void setDisposition(String disposition) throws MessagingException {
        if (disposition == null)
            removeHeader("Content-Disposition");
        else {
            String s = getHeader("Content-Disposition", null);
            if (s != null) {
                /* A Content-Disposition header already exists ..
                 *
                 * Override disposition, but attempt to retain
                 * existing disposition parameters
                 */
                ContentDisposition cd = new ContentDisposition(s);
                cd.setDisposition(disposition);
                disposition = cd.toString();
            }
            setHeader("Content-Disposition", disposition);
        }
    }

    /**
     * Returns the content transfer encoding from the
     * "Content-Transfer-Encoding" header
     * field. Returns <code>null</code> if the header is unavailable
     * or its value is absent. <p>
     *
     * This implementation uses <code>getHeader(name)</code>
     * to obtain the requisite header field.
     *
     * @return encoding
     * @exception MessagingException in case of error
     *
     * @see #headers
     */
    public String getEncoding() throws MessagingException {
        String s = getHeader("Content-Transfer-Encoding", null);

        if (s == null)
            return null;

        s = s.trim();   // get rid of trailing spaces
        // quick check for known values to avoid unnecessary use
        // of tokenizer.
        if (s.equalsIgnoreCase("7bit") || s.equalsIgnoreCase("8bit") ||
            s.equalsIgnoreCase("quoted-printable") ||
            s.equalsIgnoreCase("base64"))
            return s;

        // Tokenize the header to obtain the encoding (skip comments)
        HeaderTokenizer h = new HeaderTokenizer(s, HeaderTokenizer.MIME);

        HeaderTokenizer.Token tk;
        int tkType;

        for (;;) {
            tk = h.next(); // get a token
            tkType = tk.getType();
            if (tkType == HeaderTokenizer.Token.EOF)
            break; // done
            else if (tkType == HeaderTokenizer.Token.ATOM)
            return tk.getValue();
            else // invalid token, skip it.
            continue;
        }
        return s;
    }

    /**
     * Returns the value of the "Content-ID" header field. Returns
     * <code>null</code> if the field is unavailable or its value is
     * absent. <p>
     *
     * This implementation uses <code>getHeader(name)</code>
     * to obtain the requisite header field.
     *
     * @return conent id
     */
    public String getContentID() {
        return getHeader("Content-ID", null);
    }

    /**
     * Set the "Content-ID" header field of this body part.
     * If the <code>cid</code> parameter is null, any existing
     * "Content-ID" is removed.
     *
     * @param cid content id
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     * @since           JavaMail 1.3
     */
    public void setContentID(String cid) {
        if (cid == null)
            removeHeader("Content-ID");
        else
            setHeader("Content-ID", cid);
    }

    /**
     * Return the value of the "Content-MD5" header field. Returns
     * <code>null</code> if this field is unavailable or its value
     * is absent. <p>
     *
     * This implementation uses <code>getHeader(name)</code>
     * to obtain the requisite header field.
     *
     * @return content MD5 sum
     */
    public String getContentMD5() {
        return getHeader("Content-MD5", null);
    }

    /**
     * Set the "Content-MD5" header field of this body part.
     *
     * @param md5 content md5 sum
     *
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     */
    public void setContentMD5(String md5) {
        setHeader("Content-MD5", md5);
    }

    /**
     * Get the languages specified in the Content-Language header
     * of this MimeBodyPart. The Content-Language header is defined by
     * RFC 1766. Returns <code>null</code> if this header is not
     * available or its value is absent. <p>
     *
     * This implementation uses <code>getHeader(name)</code>
     * to obtain the requisite header field.
     *
     * @return array of language tags
     * @exception MessagingException in case of error
     */
    public String[] getContentLanguage() throws MessagingException {
        String s = getHeader("Content-Language", null);

        if (s == null)
            return null;

        // Tokenize the header to obtain the Language-tags (skip comments)
        HeaderTokenizer h = new HeaderTokenizer(s, HeaderTokenizer.MIME);
        FinalArrayList<String> v = new FinalArrayList<String>();

        HeaderTokenizer.Token tk;
        int tkType;

        while (true) {
            tk = h.next(); // get a language-tag
            tkType = tk.getType();
            if (tkType == HeaderTokenizer.Token.EOF)
            break; // done
            else if (tkType == HeaderTokenizer.Token.ATOM) v.add(tk.getValue());
            else // invalid token, skip it.
            continue;
        }

        if (v.size() == 0)
            return null;

        return v.toArray(new String[v.size()]);
    }

    /**
     * Set the Content-Language header of this MimeBodyPart. The
     * Content-Language header is defined by RFC 1766.
     *
     * @param languages         array of language tags
     */
    public void setContentLanguage(String[] languages) {
        StringBuilder sb = new StringBuilder(languages[0]);
        for (int i = 1; i < languages.length; i++)
            sb.append(',').append(languages[i]);
        setHeader("Content-Language", sb.toString());
    }

    /**
     * Returns the "Content-Description" header field of this body part.
     * This typically associates some descriptive information with
     * this part. Returns null if this field is unavailable or its
     * value is absent. <p>
     *
     * If the Content-Description field is encoded as per RFC 2047,
     * it is decoded and converted into Unicode. If the decoding or
     * conversion fails, the raw data is returned as is. <p>
     *
     * This implementation uses <code>getHeader(name)</code>
     * to obtain the requisite header field.
     *
     * @return  content description
     */
    public String getDescription() {
        String rawvalue = getHeader("Content-Description", null);

        if (rawvalue == null)
            return null;

        try {
            return MimeUtility.decodeText(MimeUtility.unfold(rawvalue));
        } catch (UnsupportedEncodingException ex) {
            return rawvalue;
        }
    }

    /**
     * Set the "Content-Description" header field for this body part.
     * If the description parameter is <code>null</code>, then any
     * existing "Content-Description" fields are removed. <p>
     *
     * If the description contains non US-ASCII characters, it will
     * be encoded using the platform's default charset. If the
     * description contains only US-ASCII characters, no encoding
     * is done and it is used as is. <p>
     *
     * Note that if the charset encoding process fails, a
     * MessagingException is thrown, and an UnsupportedEncodingException
     * is included in the chain of nested exceptions within the
     * MessagingException.
     *
     * @param description content description
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     * @exception       MessagingException An
     *                  UnsupportedEncodingException may be included
     *                  in the exception chain if the charset
     *                  conversion fails.
     */
    public void setDescription(String description) throws MessagingException {
        setDescription(description, null);
    }

    /**
     * Set the "Content-Description" header field for this body part.
     * If the description parameter is <code>null</code>, then any
     * existing "Content-Description" fields are removed. <p>
     *
     * If the description contains non US-ASCII characters, it will
     * be encoded using the specified charset. If the description
     * contains only US-ASCII characters, no encoding  is done and
     * it is used as is. <p>
     *
     * Note that if the charset encoding process fails, a
     * MessagingException is thrown, and an UnsupportedEncodingException
     * is included in the chain of nested exceptions within the
     * MessagingException.
     *
     * @param   description     Description
     * @param   charset         Charset for encoding
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     * @exception       MessagingException An
     *                  UnsupportedEncodingException may be included
     *                  in the exception chain if the charset
     *                  conversion fails.
     */
    public void setDescription(String description, String charset)
                throws MessagingException {
        if (description == null) {
            removeHeader("Content-Description");
            return;
        }

        try {
            setHeader("Content-Description", MimeUtility.fold(21,
            MimeUtility.encodeText(description, charset, null)));
        } catch (UnsupportedEncodingException uex) {
            throw new MessagingException("Encoding error", uex);
        }
    }

    /**
     * Get the filename associated with this body part. <p>
     *
     * Returns the value of the "filename" parameter from the
     * "Content-Disposition" header field of this body part. If its
     * not available, returns the value of the "name" parameter from
     * the "Content-Type" header field of this body part.
     * Returns <code>null</code> if both are absent.
     *
     * @return  filename
     * @exception MessagingException in case of error
     */
    public String getFileName() throws MessagingException {
        String filename = null;
        String s = getHeader("Content-Disposition", null);

        if (s != null) {
            // Parse the header ..
            ContentDisposition cd = new ContentDisposition(s);
            filename = cd.getParameter("filename");
        }
        if (filename == null) {
            // Still no filename ? Try the "name" ContentType parameter
            s = getHeader("Content-Type", null);
            if (s != null) {
            try {
                ContentType ct = new ContentType(s);
                filename = ct.getParameter("name");
            } catch (ParseException pex) { }    // ignore it
            }
        }
        return filename;
    }

    /**
     * Set the filename associated with this body part, if possible. <p>
     *
     * Sets the "filename" parameter of the "Content-Disposition"
     * header field of this body part.
     *
     * @param filename filename
     *
     * @exception MessagingException in case of error
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     */
    public void setFileName(String filename) throws MessagingException {
        // Set the Content-Disposition "filename" parameter
        String s = getHeader("Content-Disposition", null);
        ContentDisposition cd =
            new ContentDisposition(s == null ? ATTACHMENT : s);
        cd.setParameter("filename", filename);
        setHeader("Content-Disposition", cd.toString());

        /* Also attempt to set the Content-Type "name" parameter,
         * to satisfy ancient MUAs.
         * XXX: This is not RFC compliant, and hence should really
         * be conditional based on some property. Fix this once we
         * figure out how to get at Properties from here !
         */
        s = getHeader("Content-Type", null);
        if (s != null) {
            try {
            ContentType cType = new ContentType(s);
            cType.setParameter("name", filename);
            setHeader("Content-Type", cType.toString());
            } catch (ParseException pex) { }    // ignore it
        }
    }

    /**
     * Return a decoded input stream for this body part's "content". <p>
     *
     * This implementation obtains the input stream from the DataHandler.
     * That is, it invokes getDataHandler().getInputStream();
     *
     * @return          an InputStream
     * @exception       IOException this is typically thrown by the
     *                  DataHandler. Refer to the documentation for
     *                  javax.activation.DataHandler for more details.
     *
     * @see     #getContentStream
     * @see     DataHandler#getInputStream
     */
    public InputStream getInputStream()
                throws IOException {
        return getDataHandler().getInputStream();
    }

   /**
     * Produce the raw bytes of the content. This method is used
     * when creating a DataHandler object for the content. Subclasses
     * that can provide a separate input stream for just the MimeBodyPart
     * content might want to override this method. <p>
     *
     * @see #content
     */
    /*package*/ InputStream getContentStream() throws MessagingException {
        if (mimePart != null) {
            return mimePart.read();
        }
        if (contentStream != null)
            return ((SharedInputStream)contentStream).newStream(0, -1);
        if (content != null)
            return new ByteArrayInputStream(content,start,contentLength);

        throw new MessagingException("No content");
    }

    /**
     * Return an InputStream to the raw data with any Content-Transfer-Encoding
     * intact.  This method is useful if the "Content-Transfer-Encoding"
     * header is incorrect or corrupt, which would prevent the
     * <code>getInputStream</code> method or <code>getContent</code> method
     * from returning the correct data.  In such a case the application may
     * use this method and attempt to decode the raw data itself. <p>
     *
     * This implementation simply calls the <code>getContentStream</code>
     * method.
     *
     * @return input stream
     *
     * @exception MessagingException in case of error
     *
     * @see     #getInputStream
     * @see     #getContentStream
     * @since   JavaMail 1.2
     *
     */
    public InputStream getRawInputStream() throws MessagingException {
        return getContentStream();
    }

    /**
     * Return a DataHandler for this body part's content. <p>
     *
     * The implementation provided here works just like the
     * the implementation in MimeMessage.
     *
     * @return data handler
     */
    public DataHandler getDataHandler() {
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
                    return "MIMEPart Wrapped DataSource";
                }
            });
        }
        if (dh == null)
            dh = new DataHandler(new MimePartDataSource(this));
        return dh;
    }

    /**
     * Return the content as a java object. The type of the object
     * returned is of course dependent on the content itself. For
     * example, the native format of a text/plain content is usually
     * a String object. The native format for a "multipart"
     * content is always a MimeMultipart subclass. For content types that are
     * unknown to the DataHandler system, an input stream is returned
     * as the content. <p>
     *
     * This implementation obtains the content from the DataHandler.
     * That is, it invokes getDataHandler().getContent();
     *
     * @return          Object
     * @exception       IOException this is typically thrown by the
     *                  DataHandler. Refer to the documentation for
     *                  javax.activation.DataHandler for more details.
     */
    public Object getContent() throws IOException {
        return getDataHandler().getContent();
    }

    /**
     * This method provides the mechanism to set this body part's content.
     * The given DataHandler object should wrap the actual content.
     *
     * @param   dh      The DataHandler for the content
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     */
    public void setDataHandler(DataHandler dh) {
        if (mimePart != null) {
            mimePart = null;
        }
        this.dh = dh;
        this.content = null;
        this.contentStream = null;
        removeHeader("Content-Type");
        removeHeader("Content-Transfer-Encoding");
    }

    /**
     * A convenience method for setting this body part's content. <p>
     *
     * The content is wrapped in a DataHandler object. Note that a
     * DataContentHandler class for the specified type should be
     * available to the JavaMail implementation for this to work right.
     * That is, to do <code>setContent(foobar, "application/x-foobar")</code>,
     * a DataContentHandler for "application/x-foobar" should be installed.
     * Refer to the Java Activation Framework for more information.
     *
     * @param   o       the content object
     * @param   type    Mime type of the object
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     */
    public void setContent(Object o, String type) {
        if (mimePart != null) {
            mimePart = null;
        }
        if (o instanceof MimeMultipart) {
            setContent((MimeMultipart)o);
        } else {
            setDataHandler(new DataHandler(o, type));
        }
    }

    /**
     * Convenience method that sets the given String as this
     * part's content, with a MIME type of "text/plain". If the
     * string contains non US-ASCII characters, it will be encoded
     * using the platform's default charset. The charset is also
     * used to set the "charset" parameter. <p>
     *
     * Note that there may be a performance penalty if
     * <code>text</code> is large, since this method may have
     * to scan all the characters to determine what charset to
     * use. <p>
     * If the charset is already known, use the
     * setText() version that takes the charset parameter.
     *
     * @param text string
     *
     * @see     #setText(String text, String charset)
     */
    public void setText(String text) {
        setText(text, null);
    }

    /**
     * Convenience method that sets the given String as this part's
     * content, with a MIME type of "text/plain" and the specified
     * charset. The given Unicode string will be charset-encoded
     * using the specified charset. The charset is also used to set
     * the "charset" parameter.
     *
     * @param text string
     * @param charset character set
     */
    public void setText(String text, String charset) {
        if (charset == null) {
            if (MimeUtility.checkAscii(text) != MimeUtility.ALL_ASCII)
                charset = MimeUtility.getDefaultMIMECharset();
            else
                charset = "us-ascii";
        }
        setContent(text, "text/plain; charset=" +
                MimeUtility.quote(charset, HeaderTokenizer.MIME));
    }

    /**
     * This method sets the body part's content to a MimeMultipart object.
     *
     * @param  mp       The multipart object that is the Message's content
     * @exception       IllegalStateException if this body part is
     *                  obtained from a READ_ONLY folder.
     */
    public void setContent(MimeMultipart mp) {
        if (mimePart != null) {
            mimePart = null;
        }
        setDataHandler(new DataHandler(mp, mp.getContentType().toString()));
        mp.setParent(this);
    }

    /**
     * Output the body part as an RFC 822 format stream.
     *
     * @param os output stream
     *
     * @exception MessagingException in case of error
     * @exception IOException   if an error occurs writing to the
     *                          stream or if an error is generated
     *                          by the javax.activation layer.
     * @see DataHandler#writeTo
     */
    public void writeTo(OutputStream os)
                                throws IOException, MessagingException {

        // First, write out the header
        List<String> hdrLines = headers.getAllHeaderLines();
        int sz = hdrLines.size();
        for( int i=0; i<sz; i++ )
            OutputUtil.writeln(hdrLines.get(i),os);

        // The CRLF separator between header and content
        OutputUtil.writeln(os);

        // Finally, the content.
        // XXX: May need to account for ESMTP ?
        if (contentStream != null) {
            ((SharedInputStream)contentStream).writeTo(0,-1,os);
        } else
        if (content != null) {
            os.write(content,start,contentLength);
        } else
        if (dh!=null) {
            // this is the slowest route, so try it as the last resort
            OutputStream wos = MimeUtility.encode(os, getEncoding());
            getDataHandler().writeTo(wos);
            if(os!=wos)
                wos.flush(); // Needed to complete encoding
        } else if (mimePart != null) {
            OutputStream wos = MimeUtility.encode(os, getEncoding());
            getDataHandler().writeTo(wos);
            if(os!=wos)
                wos.flush(); // Needed to complete encoding
        }else {
            throw new MessagingException("no content");
        }
    }

    /**
     * Get all the headers for this header_name. Note that certain
     * headers may be encoded as per RFC 2047 if they contain
     * non US-ASCII characters and these should be decoded.
     *
     * @param   name    name of header
     * @return  array of headers
     * @see     MimeUtility
     */
    public String[] getHeader(String name) {
        return headers.getHeader(name);
    }

    /**
     * Get all the headers for this header name, returned as a single
     * String, with headers separated by the delimiter. If the
     * delimiter is <code>null</code>, only the first header is
     * returned.
     *
     * @param name              the name of this header
     * @param delimiter         delimiter between fields in returned string
     * @return                  the value fields for all headers with
     *                          this name
     */
    public String getHeader(String name, String delimiter) {
        return headers.getHeader(name, delimiter);
    }

    /**
     * Set the value for this header_name. Replaces all existing
     * header values with this new value. Note that RFC 822 headers
     * must contain only US-ASCII characters, so a header that
     * contains non US-ASCII characters must be encoded as per the
     * rules of RFC 2047.
     *
     * @param   name    header name
     * @param   value   header value
     * @see     MimeUtility
     */
    public void setHeader(String name, String value) {
        headers.setHeader(name, value);
    }

    /**
     * Add this value to the existing values for this header_name.
     * Note that RFC 822 headers must contain only US-ASCII
     * characters, so a header that contains non US-ASCII characters
     * must be encoded as per the rules of RFC 2047.
     *
     * @param   name    header name
     * @param   value   header value
     * @see     MimeUtility
     */
    public void addHeader(String name, String value) {
        headers.addHeader(name, value);
    }

    /**
     * Remove all headers with this name.
     *
     * @param name header name
     */
    public void removeHeader(String name) {
        headers.removeHeader(name);
    }

    /**
     * Return all the headers from this Message as an Enumeration of
     * Header objects.
     *
     * @return all headers
     */
    public FinalArrayList<hdr> getAllHeaders() {
        return headers.getAllHeaders();
    }


    /**
     * Add a header line to this body part
     *
     * @param line header line to add
     */
    public void addHeaderLine(String line) {
        headers.addHeaderLine(line);
    }

    /**
     * Examine the content of this body part and update the appropriate
     * MIME headers.  Typical headers that get set here are
     * <code>Content-Type</code> and <code>Content-Transfer-Encoding</code>.
     * Headers might need to be updated in two cases:
     *
     * <br>
     * - A message being crafted by a mail application will certainly
     * need to activate this method at some point to fill up its internal
     * headers.
     *
     * <br>
     * - A message read in from a Store will have obtained
     * all its headers from the store, and so doesn't need this.
     * However, if this message is editable and if any edits have
     * been made to either the content or message structure, we might
     * need to resync our headers.
     *
     * <br>
     * In both cases this method is typically called by the
     * <code>Message.saveChanges</code> method.
     *
     * @exception MessagingException in case of error.
     */
    protected void updateHeaders() throws MessagingException {
        DataHandler dh = getDataHandler();
        /*
         * Code flow indicates null is never returned from
         * getdataHandler() - findbugs
         */
        //if (dh == null) // Huh ?
        //    return;

        try {
            String type = dh.getContentType();
            boolean composite = false;
            boolean needCTHeader = getHeader("Content-Type") == null;

            ContentType cType = new ContentType(type);
            if (cType.match("multipart/*")) {
                // If multipart, recurse
                composite = true;
                Object o = dh.getContent();
                ((MimeMultipart) o).updateHeaders();
            } else if (cType.match("message/rfc822")) {
                composite = true;
            }

            // Content-Transfer-Encoding, but only if we don't
            // already have one
            if (!composite) {   // not allowed on composite parts
                if (getHeader("Content-Transfer-Encoding") == null)
                    setEncoding(MimeUtility.getEncoding(dh));

                if (needCTHeader && setDefaultTextCharset &&
                        cType.match("text/*") &&
                        cType.getParameter("charset") == null) {
                    /*
                     * Set a default charset for text parts.
                     * We really should examine the data to determine
                     * whether or not it's all ASCII, but that's too
                     * expensive so we make an assumption:  If we
                     * chose 7bit encoding for this data, it's probably
                     * ASCII.  (MimeUtility.getEncoding will choose
                     * 7bit only in this case, but someone might've
                     * set the Content-Transfer-Encoding header manually.)
                     */
                    String charset;
                    String enc = getEncoding();
                    if (enc != null && enc.equalsIgnoreCase("7bit"))
                        charset = "us-ascii";
                    else
                        charset = MimeUtility.getDefaultMIMECharset();
                    cType.setParameter("charset", charset);
                    type = cType.toString();
                }
            }

            // Now, let's update our own headers ...

            // Content-type, but only if we don't already have one
            if (needCTHeader) {
                /*
                 * Pull out "filename" from Content-Disposition, and
                 * use that to set the "name" parameter. This is to
                 * satisfy older MUAs (DtMail, Roam and probably
                 * a bunch of others).
                 */
                String s = getHeader("Content-Disposition", null);
                if (s != null) {
                    // Parse the header ..
                    ContentDisposition cd = new ContentDisposition(s);
                    String filename = cd.getParameter("filename");
                    if (filename != null) {
                        cType.setParameter("name", filename);
                        type = cType.toString();
                    }
                }

                setHeader("Content-Type", type);
            }
        } catch (IOException ex) {
            throw new MessagingException("IOException updating headers", ex);
        }
    }

    private void setEncoding(String encoding) {
            setHeader("Content-Transfer-Encoding", encoding);
    }
}
