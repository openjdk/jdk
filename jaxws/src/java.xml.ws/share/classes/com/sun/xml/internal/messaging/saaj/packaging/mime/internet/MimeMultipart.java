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

/*
 * @(#)MimeMultipart.java     1.31 03/01/29
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.internet;

import java.io.*;

import javax.activation.DataSource;

import com.sun.xml.internal.messaging.saaj.packaging.mime.*;
import com.sun.xml.internal.messaging.saaj.packaging.mime.util.*;
import com.sun.xml.internal.messaging.saaj.util.FinalArrayList;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.messaging.saaj.util.SAAJUtil;

/**
 * The MimeMultipart class is an implementation
 * that uses MIME conventions for the multipart data. <p>
 *
 * A MimeMultipart is obtained from a MimeBodyPart whose primary type
 * is "multipart" (by invoking the part's <code>getContent()</code> method)
 * or it can be created by a client as part of creating a new MimeMessage. <p>
 *
 * The default multipart subtype is "mixed".  The other multipart
 * subtypes, such as "alternative", "related", and so on, can be
 * implemented as subclasses of MimeMultipart with additional methods
 * to implement the additional semantics of that type of multipart
 * content. The intent is that service providers, mail JavaBean writers
 * and mail clients will write many such subclasses and their Command
 * Beans, and will install them into the JavaBeans Activation
 * Framework, so that any JavaMail implementation and its clients can
 * transparently find and use these classes. Thus, a MIME multipart
 * handler is treated just like any other type handler, thereby
 * decoupling the process of providing multipart handlers from the
 * JavaMail API. Lacking these additional MimeMultipart subclasses,
 * all subtypes of MIME multipart data appear as MimeMultipart objects. <p>
 *
 * An application can directly construct a MIME multipart object of any
 * subtype by using the <code>MimeMultipart(String subtype)</code>
 * constructor.  For example, to create a "multipart/alternative" object,
 * use <code>new MimeMultipart("alternative")</code>.
 *
 * @version 1.31, 03/01/29
 * @author  John Mani
 * @author  Bill Shannon
 * @author  Max Spivak
 */

//BM MimeMultipart can extend this
public  class MimeMultipart {

    /**
     * The DataSource supplying our InputStream.
     */
    protected DataSource ds = null;

    /**
     * Have we parsed the data from our InputStream yet?
     * Defaults to true; set to false when our constructor is
     * given a DataSource with an InputStream that we need to
     * parse.
     */
    protected boolean parsed = true;

    /**
     * Vector of MimeBodyPart objects.
     */
    protected FinalArrayList<MimeBodyPart> parts = new FinalArrayList<MimeBodyPart>(); // Holds BodyParts

    /**
     * This field specifies the content-type of this multipart
     * object. It defaults to "multipart/mixed".
     */
    protected ContentType contentType;

    /**
     * The <code>MimeBodyPart</code> containing this <code>MimeMultipart</code>,
     * if known.
     * @since   JavaMail 1.1
     */
    protected MimeBodyPart parent;

    protected static final boolean ignoreMissingEndBoundary;
    static {
        ignoreMissingEndBoundary = SAAJUtil.getSystemBoolean("saaj.mime.multipart.ignoremissingendboundary");
    }

    /**
     * Default constructor. An empty MimeMultipart object
     * is created. Its content type is set to "multipart/mixed".
     * A unique boundary string is generated and this string is
     * setup as the "boundary" parameter for the
     * <code>contentType</code> field. <p>
     *
     * MimeBodyParts may be added later.
     */
    public MimeMultipart() {
        this("mixed");
    }

    /**
     * Construct a MimeMultipart object of the given subtype.
     * A unique boundary string is generated and this string is
     * setup as the "boundary" parameter for the
     * <code>contentType</code> field. <p>
     *
     * MimeBodyParts may be added later.
     */
    public MimeMultipart(String subtype) {
        //super();
        /*
         * Compute a boundary string.
         */
        String boundary = UniqueValue.getUniqueBoundaryValue();
        contentType = new ContentType("multipart", subtype, null);
        contentType.setParameter("boundary", boundary);
    }

    /**
     * Constructs a MimeMultipart object and its bodyparts from the
     * given DataSource. <p>
     *
     * This constructor handles as a special case the situation where the
     * given DataSource is a MultipartDataSource object.
     *
     * Otherwise, the DataSource is assumed to provide a MIME multipart
     * byte stream.  The <code>parsed</code> flag is set to false.  When
     * the data for the body parts are needed, the parser extracts the
     * "boundary" parameter from the content type of this DataSource,
     * skips the 'preamble' and reads bytes till the terminating
     * boundary and creates MimeBodyParts for each part of the stream.
     *
     * @param   ds      DataSource, can be a MultipartDataSource
     * @param ct
     *      This must be the same information as {@link DataSource#getContentType()}.
     *      All the callers of this method seem to have this object handy, so
     *      for performance reason this method accepts it. Can be null.
     */
    public MimeMultipart(DataSource ds, ContentType ct) throws MessagingException {
        // 'ds' was not a MultipartDataSource, we have
        // to parse this ourself.
        parsed = false;
        this.ds = ds;
        if (ct==null)
            contentType = new ContentType(ds.getContentType());
        else
            contentType = ct;
    }

    /**
     * Set the subtype. This method should be invoked only on a new
     * MimeMultipart object created by the client. The default subtype
     * of such a multipart object is "mixed". <p>
     *
     * @param   subtype         Subtype
     */
    public  void setSubType(String subtype) {
        contentType.setSubType(subtype);
    }

    /**
     * Return the number of enclosed MimeBodyPart objects.
     *
     * @return          number of parts
     */
    public  int getCount() throws MessagingException {
        parse();
        if (parts == null)
            return 0;

        return parts.size();
    }

    /**
     * Get the specified MimeBodyPart.  BodyParts are numbered starting at 0.
     *
     * @param index     the index of the desired MimeBodyPart
     * @return          the MimeBodyPart
     * @exception       MessagingException if no such MimeBodyPart exists
     */
    public  MimeBodyPart getBodyPart(int index)
                        throws MessagingException {
        parse();
        if (parts == null)
            throw new IndexOutOfBoundsException("No such BodyPart");

        return parts.get(index);
    }

    /**
     * Get the MimeBodyPart referred to by the given ContentID (CID).
     * Returns null if the part is not found.
     *
     * @param  CID      the ContentID of the desired part
     * @return          the MimeBodyPart
     */
    public  MimeBodyPart getBodyPart(String CID)
                        throws MessagingException {
        parse();

        int count = getCount();
        for (int i = 0; i < count; i++) {
           MimeBodyPart part = getBodyPart(i);
           String s = part.getContentID();
           // Old versions of AXIS2 put angle brackets around the content
           // id but not the start param
           String sNoAngle = (s!= null) ? s.replaceFirst("^<", "").replaceFirst(">$", "")
                   :null;
           if (s != null && (s.equals(CID) || CID.equals(sNoAngle)))
                return part;
        }
        return null;
    }

    /**
     * Update headers. The default implementation here just
     * calls the <code>updateHeaders</code> method on each of its
     * children BodyParts. <p>
     *
     * Note that the boundary parameter is already set up when
     * a new and empty MimeMultipart object is created. <p>
     *
     * This method is called when the <code>saveChanges</code>
     * method is invoked on the Message object containing this
     * MimeMultipart. This is typically done as part of the Message
     * send process, however note that a client is free to call
     * it any number of times. So if the header updating process is
     * expensive for a specific MimeMultipart subclass, then it
     * might itself want to track whether its internal state actually
     * did change, and do the header updating only if necessary.
     */
    protected void updateHeaders() throws MessagingException {
        for (int i = 0; i < parts.size(); i++)
            parts.get(i).updateHeaders();
    }

    /**
     * Iterates through all the parts and outputs each Mime part
     * separated by a boundary.
     */
    public void writeTo(OutputStream os)
            throws IOException, MessagingException {
        parse();

        String boundary = "--" + contentType.getParameter("boundary");

        for (int i = 0; i < parts.size(); i++) {
            OutputUtil.writeln(boundary, os); // put out boundary
            getBodyPart(i).writeTo(os);
            OutputUtil.writeln(os); // put out empty line
        }

        // put out last boundary
        OutputUtil.writeAsAscii(boundary, os);
        OutputUtil.writeAsAscii("--", os);
        os.flush();
    }

    /**
     * Parse the InputStream from our DataSource, constructing the
     * appropriate MimeBodyParts.  The <code>parsed</code> flag is
     * set to true, and if true on entry nothing is done.  This
     * method is called by all other methods that need data for
     * the body parts, to make sure the data has been parsed.
     *
     * @since   JavaMail 1.2
     */
    protected  void parse() throws MessagingException {
        if (parsed)
            return;

        InputStream in;
        SharedInputStream sin = null;
        long start = 0, end = 0;
        boolean foundClosingBoundary = false;

        try {
            in = ds.getInputStream();
            if (!(in instanceof ByteArrayInputStream) &&
                !(in instanceof BufferedInputStream) &&
                !(in instanceof SharedInputStream))
                in = new BufferedInputStream(in);
        } catch (Exception ex) {
            throw new MessagingException("No inputstream from datasource");
        }
        if (in instanceof SharedInputStream)
            sin = (SharedInputStream)in;

        String boundary = "--" + contentType.getParameter("boundary");
        byte[] bndbytes = ASCIIUtility.getBytes(boundary);
        int bl = bndbytes.length;

        ByteOutputStream buf = null;
        try {
            // Skip the preamble
            LineInputStream lin = new LineInputStream(in);
            String line;
            while ((line = lin.readLine()) != null) {
                /*
                 * Strip trailing whitespace.  Can't use trim method
                 * because it's too aggressive.  Some bogus MIME
                 * messages will include control characters in the
                 * boundary string.
                 */
                int i;
                for (i = line.length() - 1; i >= 0; i--) {
                    char c = line.charAt(i);
                    if (!(c == ' ' || c == '\t'))
                        break;
                }
                line = line.substring(0, i + 1);
                if (line.equals(boundary))
                    break;
            }
            if (line == null)
                throw new MessagingException("Missing start boundary");

            /*
             * Read and process body parts until we see the
             * terminating boundary line (or EOF).
             */
            boolean done = false;
        getparts:
            while (!done) {
                InternetHeaders headers = null;
                if (sin != null) {
                    start = sin.getPosition();
                    // skip headers
                    while ((line = lin.readLine()) != null && line.length() > 0)
                        ;
                    if (line == null) {
                        if (!ignoreMissingEndBoundary) {
                           throw new MessagingException("Missing End Boundary for Mime Package : EOF while skipping headers");
                        }
                        // assume there's just a missing end boundary
                        break getparts;
                    }
                } else {
                    // collect the headers for this body part
                    headers = createInternetHeaders(in);
                }

                if (!in.markSupported())
                    throw new MessagingException("Stream doesn't support mark");

                buf = null;
                // if we don't have a shared input stream, we copy the data
                if (sin == null)
                    buf = new ByteOutputStream();
                int b;
                boolean bol = true;    // beginning of line flag
                // the two possible end of line characters
                int eol1 = -1, eol2 = -1;

                /*
                 * Read and save the content bytes in buf.
                 */
                for (;;) {
                    if (bol) {
                        /*
                         * At the beginning of a line, check whether the
                         * next line is a boundary.
                         */
                        int i;
                        in.mark(bl + 4 + 1000); // bnd + "--\r\n" + lots of LWSP
                        // read bytes, matching against the boundary
                        for (i = 0; i < bl; i++)
                            if (in.read() != bndbytes[i])
                                break;
                        if (i == bl) {
                            // matched the boundary, check for last boundary
                            int b2 = in.read();
                            if (b2 == '-') {
                                if (in.read() == '-') {
                                    done = true;
                                    foundClosingBoundary = true;
                                    break;      // ignore trailing text
                                }
                            }
                            // skip linear whitespace
                            while (b2 == ' ' || b2 == '\t')
                                b2 = in.read();
                            // check for end of line
                            if (b2 == '\n')
                                break;  // got it!  break out of the loop
                            if (b2 == '\r') {
                                in.mark(1);
                                if (in.read() != '\n')
                                    in.reset();
                                break;  // got it!  break out of the loop
                            }
                        }
                        // failed to match, reset and proceed normally
                        in.reset();

                        // if this is not the first line, write out the
                        // end of line characters from the previous line
                        if (buf != null && eol1 != -1) {
                            buf.write(eol1);
                            if (eol2 != -1)
                                buf.write(eol2);
                            eol1 = eol2 = -1;
                        }
                    }

                    // read the next byte
                    if ((b = in.read()) < 0) {
                        done = true;
                        break;
                    }

                    /*
                     * If we're at the end of the line, save the eol characters
                     * to be written out before the beginning of the next line.
                     */
                    if (b == '\r' || b == '\n') {
                        bol = true;
                        if (sin != null)
                            end = sin.getPosition() - 1;
                        eol1 = b;
                        if (b == '\r') {
                            in.mark(1);
                            if ((b = in.read()) == '\n')
                                eol2 = b;
                            else
                                in.reset();
                        }
                    } else {
                        bol = false;
                        if (buf != null)
                            buf.write(b);
                    }
                }

                /*
                 * Create a MimeBody element to represent this body part.
                 */
                MimeBodyPart part;
                if (sin != null)
                    part = createMimeBodyPart(sin.newStream(start, end));
                else
                    part = createMimeBodyPart(headers, buf.getBytes(), buf.getCount());
                addBodyPart(part);
            }
        } catch (IOException ioex) {
            throw new MessagingException("IO Error", ioex);
        } finally {
            if (buf != null)
                buf.close();
        }

        if (!ignoreMissingEndBoundary && !foundClosingBoundary && sin== null) {
            throw new MessagingException("Missing End Boundary for Mime Package : EOF while skipping headers");
        }
        parsed = true;
    }

    /**
     * Create and return an InternetHeaders object that loads the
     * headers from the given InputStream.  Subclasses can override
     * this method to return a subclass of InternetHeaders, if
     * necessary.  This implementation simply constructs and returns
     * an InternetHeaders object.
     *
     * @param   is      the InputStream to read the headers from
     * @exception       MessagingException
     * @since           JavaMail 1.2
     */
    protected InternetHeaders createInternetHeaders(InputStream is)
                                throws MessagingException {
        return new InternetHeaders(is);
    }

    /**
     * Create and return a MimeBodyPart object to represent a
     * body part parsed from the InputStream.  Subclasses can override
     * this method to return a subclass of MimeBodyPart, if
     * necessary.  This implementation simply constructs and returns
     * a MimeBodyPart object.
     *
     * @param   headers         the headers for the body part
     * @param   content         the content of the body part
     * @since                   JavaMail 1.2
     */
    protected MimeBodyPart createMimeBodyPart(InternetHeaders headers, byte[] content, int len) {
            return new MimeBodyPart(headers, content,len);
    }

    /**
     * Create and return a MimeBodyPart object to represent a
     * body part parsed from the InputStream.  Subclasses can override
     * this method to return a subclass of MimeBodyPart, if
     * necessary.  This implementation simply constructs and returns
     * a MimeBodyPart object.
     *
     * @param   is              InputStream containing the body part
     * @exception               MessagingException
     * @since                   JavaMail 1.2
     */
    protected MimeBodyPart createMimeBodyPart(InputStream is) throws MessagingException {
            return new MimeBodyPart(is);
    }

    /**
     * Setup this MimeMultipart object from the given MultipartDataSource. <p>
     *
     * The method adds the MultipartDataSource's MimeBodyPart
     * objects into this MimeMultipart. This MimeMultipart's contentType is
     * set to that of the MultipartDataSource. <p>
     *
     * This method is typically used in those cases where one
     * has a multipart data source that has already been pre-parsed into
     * the individual body parts (for example, an IMAP datasource), but
     * needs to create an appropriate MimeMultipart subclass that represents
     * a specific multipart subtype.
     *
     * @param   mp      MimeMultipart datasource
     */

    protected void setMultipartDataSource(MultipartDataSource mp)
                        throws MessagingException {
        contentType = new ContentType(mp.getContentType());

        int count = mp.getCount();
        for (int i = 0; i < count; i++)
            addBodyPart(mp.getBodyPart(i));
    }

    /**
     * Return the content-type of this MimeMultipart. <p>
     *
     * This implementation just returns the value of the
     * <code>contentType</code> field.
     *
     * @return  content-type
     * @see     #contentType
     */
    public ContentType getContentType() {
            return contentType;
    }

    /**
     * Remove the specified part from the multipart message.
     * Shifts all the parts after the removed part down one.
     *
     * @param   part    The part to remove
     * @return          true if part removed, false otherwise
     * @exception       MessagingException if no such MimeBodyPart exists
     */
    public boolean removeBodyPart(MimeBodyPart part) throws MessagingException {
        if (parts == null)
            throw new MessagingException("No such body part");

        boolean ret = parts.remove(part);
        part.setParent(null);
        return ret;
    }

    /**
     * Remove the part at specified location (starting from 0).
     * Shifts all the parts after the removed part down one.
     *
     * @param   index   Index of the part to remove
     * @exception       IndexOutOfBoundsException if the given index
     *                  is out of range.
     */
    public void removeBodyPart(int index) {
        if (parts == null)
            throw new IndexOutOfBoundsException("No such BodyPart");

        MimeBodyPart part = parts.get(index);
        parts.remove(index);
        part.setParent(null);
    }

    /**
     * Adds a MimeBodyPart to the multipart.  The MimeBodyPart is appended to
     * the list of existing Parts.
     *
     * @param  part  The MimeBodyPart to be appended
     */
    public synchronized void addBodyPart(MimeBodyPart part) {
        if (parts == null)
            parts = new FinalArrayList<MimeBodyPart>();

        parts.add(part);
        part.setParent(this);
    }

    /**
     * Adds a MimeBodyPart at position <code>index</code>.
     * If <code>index</code> is not the last one in the list,
     * the subsequent parts are shifted up. If <code>index</code>
     * is larger than the number of parts present, the
     * MimeBodyPart is appended to the end.
     *
     * @param  part  The MimeBodyPart to be inserted
     * @param  index Location where to insert the part
     */
    public synchronized void addBodyPart(MimeBodyPart part, int index) {
        if (parts == null)
            parts = new FinalArrayList<MimeBodyPart>();

        parts.add(index,part);
        part.setParent(this);
    }

    /**
     * Return the <code>MimeBodyPart</code> that contains this <code>MimeMultipart</code>
     * object, or <code>null</code> if not known.
     * @since   JavaMail 1.1
     */
    MimeBodyPart getParent() {
        return parent;
    }

    /**
     * Set the parent of this <code>MimeMultipart</code> to be the specified
     * <code>MimeBodyPart</code>.  Normally called by the <code>Message</code>
     * or <code>MimeBodyPart</code> <code>setContent(MimeMultipart)</code> method.
     * <code>parent</code> may be <code>null</code> if the
     * <code>MimeMultipart</code> is being removed from its containing
     * <code>MimeBodyPart</code>.
     * @since   JavaMail 1.1
     */
    void setParent(MimeBodyPart parent) {
        this.parent = parent;
    }
}
