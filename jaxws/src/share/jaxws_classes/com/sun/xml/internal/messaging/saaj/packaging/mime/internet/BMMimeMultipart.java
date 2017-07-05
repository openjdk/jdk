/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.BitSet;

import javax.activation.DataSource;

import com.sun.xml.internal.messaging.saaj.packaging.mime.*;
import com.sun.xml.internal.messaging.saaj.packaging.mime.util.*;

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.messaging.saaj.util.FinalArrayList;

/**
 * The MimeMultipart class is an implementation of the abstract Multipart
 * class that uses MIME conventions for the multipart data. <p>
 *
 * A MimeMultipart is obtained from a MimePart whose primary type
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
 */

//TODO: cleanup the SharedInputStream handling
public  class BMMimeMultipart extends MimeMultipart {

    /*
     * When true it indicates parsing hasnt been done at all
     */
    private boolean begining = true;

    int[] bcs = new int[256];
    int[] gss = null;
    private static final int BUFFER_SIZE = 4096;
    private byte[] buffer = new byte[BUFFER_SIZE];
    private byte[] prevBuffer = new byte[BUFFER_SIZE];
    private BitSet lastPartFound = new BitSet(1);

    // cached inputstream which is possibly partially consumed
    private InputStream in = null;
    private String boundary = null;
    // current stream position, set to -1 on EOF
    int b = 0;

    // property to indicate if lazyAttachments is ON
    private boolean lazyAttachments = false;

    /**
     * Default constructor. An empty MimeMultipart object
     * is created. Its content type is set to "multipart/mixed".
     * A unique boundary string is generated and this string is
     * setup as the "boundary" parameter for the
     * <code>contentType</code> field. <p>
     *
     * MimeBodyParts may be added later.
     */
    public BMMimeMultipart() {
        super();
        //this("mixed");
    }

    /**
     * Construct a MimeMultipart object of the given subtype.
     * A unique boundary string is generated and this string is
     * setup as the "boundary" parameter for the
     * <code>contentType</code> field. <p>
     *
     * MimeBodyParts may be added later.
     */
    public BMMimeMultipart(String subtype) {
        super(subtype);
        /*
         * Compute a boundary string.
        String boundary = UniqueValue.getUniqueBoundaryValue();
        ContentType cType = new ContentType("multipart", subtype, null);
        contentType.setParameter("boundary", boundary);
         */
    }

    /**
     * Constructs a MimeMultipart object and its bodyparts from the
     * given DataSource. <p>
     *
     * This constructor handles as a special case the situation where the
     * given DataSource is a MultipartDataSource object.  In this case, this
     * method just invokes the superclass (i.e., Multipart) constructor
     * that takes a MultipartDataSource object. <p>
     *
     * Otherwise, the DataSource is assumed to provide a MIME multipart
     * byte stream.  The <code>parsed</code> flag is set to false.  When
     * the data for the body parts are needed, the parser extracts the
     * "boundary" parameter from the content type of this DataSource,
     * skips the 'preamble' and reads bytes till the terminating
     * boundary and creates MimeBodyParts for each part of the stream.
     *
     * @param   ds      DataSource, can be a MultipartDataSource
     */
    public BMMimeMultipart(DataSource ds, ContentType ct)
        throws MessagingException {
        super(ds,ct);
        boundary = ct.getParameter("boundary");
        /*
        if (ds instanceof MultipartDataSource) {
            // ask super to do this for us.
            setMultipartDataSource((MultipartDataSource)ds);
            return;
        }

        // 'ds' was not a MultipartDataSource, we have
        // to parse this ourself.
        parsed = false;
        this.ds = ds;
        if (ct==null)
            contentType = new ContentType(ds.getContentType());
        else
            contentType = ct;
       */

    }

    public InputStream initStream() throws MessagingException {

        if (in == null) {
            try {
                in = ds.getInputStream();
                if (!(in instanceof ByteArrayInputStream) &&
                    !(in instanceof BufferedInputStream) &&
                    !(in instanceof SharedInputStream))
                    in = new BufferedInputStream(in);
            } catch (Exception ex) {
                throw new MessagingException("No inputstream from datasource");
            }

            if (!in.markSupported()) {
                throw new MessagingException(
                    "InputStream does not support Marking");
            }
        }
        return in;
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
    protected  void parse() throws  MessagingException {
        if (parsed)
            return;

        initStream();

        SharedInputStream sin = null;
        if (in instanceof SharedInputStream) {
            sin = (SharedInputStream)in;
        }

        String bnd = "--" + boundary;
        byte[] bndbytes = ASCIIUtility.getBytes(bnd);
        try {
            parse(in, bndbytes, sin);
        } catch (IOException ioex) {
            throw new MessagingException("IO Error", ioex);
        } catch (Exception ex) {
            throw new MessagingException("Error", ex);
        }

        parsed = true;
    }

    public boolean lastBodyPartFound() {
        return lastPartFound.get(0);
    }

    public MimeBodyPart getNextPart(
        InputStream stream, byte[] pattern, SharedInputStream sin)
        throws Exception {

        if (!stream.markSupported()) {
            throw new Exception("InputStream does not support Marking");
        }

        if (begining) {
            compile(pattern);
            if (!skipPreamble(stream, pattern, sin)) {
                throw new Exception(
                    "Missing Start Boundary, or boundary does not start on a new line");
            }
            begining = false;
        }

        if (lastBodyPartFound()) {
            throw new Exception("No parts found in Multipart InputStream");
        }

        if (sin != null) {
            long start = sin.getPosition();
            b = readHeaders(stream);
            if (b == -1) {
                throw new Exception(
                    "End of Stream encountered while reading part headers");
            }
            long[] v = new long[1];
            v[0] = -1; // just to ensure the code later sets it correctly
            b = readBody(stream, pattern, v, null, sin);
            // looks like this check has to be disabled
            // it is allowed to have Mime Package without closing boundary
            if (!ignoreMissingEndBoundary) {
                if ((b == -1) && !lastBodyPartFound()) {
                    throw new MessagingException("Missing End Boundary for Mime Package : EOF while skipping headers");
                }
            }
            long end = v[0];
            MimeBodyPart mbp = createMimeBodyPart(sin.newStream(start, end));
            addBodyPart(mbp);
            return mbp;

        } else {
            InternetHeaders headers = createInternetHeaders(stream);
            ByteOutputStream baos = new ByteOutputStream();
            b = readBody(stream, pattern, null,baos, null);
            // looks like this check has to be disabled
            // in the old impl it is allowed to have Mime Package
            // without closing boundary
            if (!ignoreMissingEndBoundary) {
                if ((b == -1) && !lastBodyPartFound()) {
                    throw new MessagingException("Missing End Boundary for Mime Package : EOF while skipping headers");
                }
            }
            MimeBodyPart mbp = createMimeBodyPart(
                headers, baos.getBytes(), baos.getCount());
            addBodyPart(mbp);
            return mbp;
        }

    }

    public boolean parse(
        InputStream stream, byte[] pattern, SharedInputStream sin)
        throws Exception {

        while (!lastPartFound.get(0) && (b != -1)) {
           getNextPart(stream, pattern, sin);
        }
        return true;
    }

    private int readHeaders(InputStream is) throws Exception {
        // if the headers are to end properly then there has to be CRLF
        // actually we just need to mark the start and end positions
        int b = is.read();
        while(b != -1) {
            // when it is a shared input stream no need to copy
            if (b == '\r') {
                b = is.read();
                if (b == '\n') {
                    b = is.read();
                    if (b == '\r') {
                        b = is.read();
                        if (b == '\n') {
                           return b;
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
            }
            b = is.read();
        }
        if (b == -1) {
            throw new Exception(
            "End of inputstream while reading Mime-Part Headers");
        }
        return b;
    }

    private int readBody(
        InputStream is, byte[] pattern, long[] posVector,
        ByteOutputStream baos, SharedInputStream sin)
        throws Exception {
        if (!find(is, pattern, posVector, baos, sin)) {
            throw new Exception(
            "Missing boundary delimitier while reading Body Part");
        }
        return b;
    }

    private boolean skipPreamble(
        InputStream is, byte[] pattern, SharedInputStream sin)
        throws Exception {
        if (!find(is, pattern, sin)) {
            return false;
        }
        if (lastPartFound.get(0)) {
            throw new Exception(
            "Found closing boundary delimiter while trying to skip preamble");
        }
        return true;
    }


    public int  readNext(InputStream is, byte[] buff, int patternLength,
        BitSet eof, long[] posVector, SharedInputStream sin)
        throws Exception {

        int bufferLength = is.read(buffer, 0, patternLength);
        if (bufferLength == -1) {
           eof.flip(0);
        } else if (bufferLength < patternLength) {
            //repeatedly read patternLength - bufferLength
            int temp = 0;
            long pos = 0;
            int i = bufferLength;
            for (; i < patternLength; i++) {
                if (sin != null) {
                    pos = sin.getPosition();
                }
                temp = is.read();
                if (temp == -1) {
                    eof.flip(0);
                    if (sin != null) {
                        posVector[0] = pos;
                    }
                    break;
                }
                buffer[i] = (byte)temp;
            }
            bufferLength=i;
        }
        return bufferLength;
    }

    public boolean find(InputStream is, byte[] pattern, SharedInputStream sin)
        throws Exception {
        int i;
        int l = pattern.length;
        int lx = l -1;
        int bufferLength = 0;
        BitSet eof = new BitSet(1);
        long[] posVector = new long[1];

        while (true) {
            is.mark(l);
            bufferLength = readNext(is, buffer, l, eof, posVector, sin);
            if (eof.get(0)) {
                // End of stream
                return false;
            }

            /*
            if (bufferLength < l) {
                //is.reset();
                return false;
            }*/

            for(i = lx; i >= 0; i--) {
                if (buffer[i] != pattern[i]) {
                    break;
                }
            }

            if (i < 0) {
                // found the boundary, skip *LWSP-char and CRLF
                if (!skipLWSPAndCRLF(is)) {
                    throw new Exception("Boundary does not terminate with CRLF");
                }
                return true;
            }

            int s = Math.max(i + 1 - bcs[buffer[i] & 0x7f], gss[i]);
            is.reset();
            is.skip(s);
        }
    }

    public boolean find(
        InputStream is, byte[] pattern, long[] posVector,
        ByteOutputStream out, SharedInputStream sin) throws Exception {
        int i;
        int l = pattern.length;
        int lx = l -1;
        int bufferLength = 0;
        int s = 0;
        long endPos = -1;
        byte[] tmp = null;

        boolean first = true;
        BitSet eof = new BitSet(1);

        while (true) {
            is.mark(l);
            if (!first) {
                tmp = prevBuffer;
                prevBuffer = buffer;
                buffer = tmp;
            }
            if (sin != null) {
                endPos = sin.getPosition();
            }

            bufferLength = readNext(is, buffer, l, eof, posVector, sin);

            if (bufferLength == -1) {
                // End of stream
                // looks like it is allowed to not have a closing boundary
                //return false;
                //if (sin != null) {
                 //   posVector[0] = endPos;
                //}
                b = -1;
                if ((s == l) && (sin == null)) {
                    out.write(prevBuffer, 0, s);
                }
                return true;
            }

            if (bufferLength < l) {
                if (sin != null) {
                    //endPos = sin.getPosition();
                    //posVector[0] = endPos;
                } else {
                    // looks like it is allowed to not have a closing boundary
                    // in the old implementation
                        out.write(buffer, 0, bufferLength);
                }
                // looks like it is allowed to not have a closing boundary
                // in the old implementation
                //return false;
                b = -1;
                return true;
            }

            for(i = lx; i >= 0; i--) {
                if (buffer[i] != pattern[i]) {
                    break;
                }
            }

            if (i < 0) {
                if (s > 0) {
                    //looks like the earlier impl allowed just an LF
                    // so if s == 1 : it must be an LF
                    // if s == 2 : it must be a CR LF
                    if (s <= 2) {
                        //it could be "some-char\n" so write some-char
                        if (s == 2) {
                            if (prevBuffer[1] == '\n') {
                                if (prevBuffer[0] != '\r' && prevBuffer[0] != '\n') {
                                    out.write(prevBuffer,0,1);
                                }
                                if (sin != null) {
                                    posVector[0] = endPos;
                                }

                            } else {
                                throw new Exception(
                                        "Boundary characters encountered in part Body " +
                                        "without a preceeding CRLF");
                            }

                        } else if (s==1) {
                            if (prevBuffer[0] != '\n') {
                                throw new Exception(
                                        "Boundary characters encountered in part Body " +
                                        "without a preceeding CRLF");
                            }else {
                                if (sin != null) {
                                    posVector[0] = endPos;
                                }
                            }
                        }

                    } else if (s > 2) {
                        if ((prevBuffer[s-2] == '\r') && (prevBuffer[s-1] == '\n')) {
                            if (sin != null) {
                                posVector[0] = endPos - 2;
                            } else {
                                out.write(prevBuffer, 0, s - 2);
                            }
                        } else if (prevBuffer[s-1] == '\n') {
                            //old impl allowed just a \n
                            if (sin != null) {
                                posVector[0] = endPos - 1;
                            } else {
                                out.write(prevBuffer, 0, s - 1);
                            }
                        } else {
                            throw new Exception(
                                "Boundary characters encountered in part Body " +
                                "without a preceeding CRLF");
                        }
                    }
                }
                // found the boundary, skip *LWSP-char and CRLF
                if (!skipLWSPAndCRLF(is)) {
                    //throw new Exception(
                    //   "Boundary does not terminate with CRLF");
                }
                return true;
            }

            if ((s > 0) && (sin == null)) {
                if (prevBuffer[s-1] == (byte)13) {
                    // if buffer[0] == (byte)10
                    if (buffer[0] == (byte)10) {
                        int j=lx-1;
                        for(j = lx-1; j > 0; j--) {
                            if (buffer[j+1] != pattern[j]) {
                                break;
                             }
                         }
                         if (j == 0) {
                             // matched the pattern excluding the last char of the pattern
                             // so dont write the CR into stream
                             out.write(prevBuffer,0,s-1);
                         } else {
                             out.write(prevBuffer,0,s);
                         }
                    } else {
                        out.write(prevBuffer, 0, s);
                    }
                } else {
                    out.write(prevBuffer, 0, s);
                }
            }

            s = Math.max(i + 1 - bcs[buffer[i] & 0x7f], gss[i]);
            is.reset();
            is.skip(s);
            if (first) {
                first = false;
            }
        }
    }

    private boolean skipLWSPAndCRLF(InputStream is) throws Exception {

        b = is.read();
        //looks like old impl allowed just a \n as well
        if (b == '\n') {
            return true;
        }

        if (b == '\r') {
            b = is.read();
            //skip any multiple '\r' "\r\n" --> "\r\r\n" on Win2k
            if (b == '\r') {
                b = is.read();
            }
            if (b == '\n') {
                return true;
            } else {
                throw new Exception(
                    "transport padding after a Mime Boundary  should end in a CRLF, found CR only");
            }
        }

        if (b == '-') {
            b = is.read();
            if (b != '-') {
               throw new Exception(
                   "Unexpected singular '-' character after Mime Boundary");
            } else {
                //System.out.println("Last Part Found");
                lastPartFound.flip(0);
                // read the next char
                b  = is.read();
            }
        }

        while ((b != -1) && ((b == ' ') || (b == '\t'))) {
            b = is.read();
            if (b == '\n') {
                return true;
            }
            if (b == '\r') {
                b = is.read();
                //skip any multiple '\r': "\r\n" --> "\r\r\n" on Win2k
                if (b == '\r') {
                    b = is.read();
                }
                if (b == '\n') {
                   return true;
                }
            }
        }

        if (b == -1) {
            // the last boundary need not have CRLF
            if (!lastPartFound.get(0)) {
                throw new Exception(
                        "End of Multipart Stream before encountering  closing boundary delimiter");
            }
            return true;
        }
        return false;
    }

    private void compile(byte[] pattern) {
        int l = pattern.length;

        int i;
        int j;

        // Copied from J2SE 1.4 regex code
        // java.util.regex.Pattern.java

        // Initialise Bad Character Shift table
        for (i = 0; i < l; i++) {
            bcs[pattern[i]] = i + 1;
        }

        // Initialise Good Suffix Shift table
        gss = new int[l];
  NEXT: for (i = l; i > 0; i--) {
            // j is the beginning index of suffix being considered
            for (j = l - 1; j >= i; j--) {
                // Testing for good suffix
                if (pattern[j] == pattern[j - i]) {
                    // pattern[j..len] is a good suffix
                    gss[j - 1] = i;
                } else {
                   // No match. The array has already been
                   // filled up with correct values before.
                   continue NEXT;
                }
            }
            while (j > 0) {
                gss[--j] = i;
            }
        }
        gss[l - 1] = 1;
    }


    /**
     * Iterates through all the parts and outputs each Mime part
     * separated by a boundary.
     */
    byte[] buf = new byte[1024];

    public void writeTo(OutputStream os)
            throws IOException, MessagingException {

        // inputStream was not null
        if (in != null) {
            contentType.setParameter("boundary", this.boundary);
        }

        String bnd = "--" + contentType.getParameter("boundary");
        for (int i = 0; i < parts.size(); i++) {
            OutputUtil.writeln(bnd, os); // put out boundary
            ((MimeBodyPart)parts.get(i)).writeTo(os);
            OutputUtil.writeln(os); // put out empty line
        }

        if (in != null) {
            OutputUtil.writeln(bnd, os); // put out boundary
            if ((os instanceof ByteOutputStream) && lazyAttachments) {
                ((ByteOutputStream)os).write(in);
            } else {
                ByteOutputStream baos = new ByteOutputStream(in.available());
                baos.write(in);
                baos.writeTo(os);
                // reset the inputstream so that we can support a
                //getAttachment later
                in = baos.newInputStream();
            }

            // this will endup writing the end boundary
        } else {
        // put out last boundary
            OutputUtil.writeAsAscii(bnd, os);
            OutputUtil.writeAsAscii("--", os);
        }
    }

    public void setInputStream(InputStream is) {
        this.in = is;
    }

    public InputStream getInputStream() {
        return this.in;
    }

    public void setBoundary(String bnd) {
        this.boundary = bnd;
        if (this.contentType != null) {
            this.contentType.setParameter("boundary", bnd);
        }
    }
    public String getBoundary() {
        return this.boundary;
    }

    public boolean isEndOfStream() {
        return (b == -1);
    }

    public void setLazyAttachments(boolean flag) {
        lazyAttachments = flag;
    }

}
