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
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.messaging.saaj.packaging.mime.MessagingException;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ContentType;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.InternetHeaders;
import com.sun.xml.internal.messaging.saaj.packaging.mime.internet.ParseException;
import com.sun.xml.internal.ws.message.stream.StreamAttachment;
import com.sun.xml.internal.ws.util.ASCIIUtility;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;

import javax.xml.ws.WebServiceException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses Mime multipart message into primary part and attachment parts. It
 * parses the stream lazily as and when required.
 *
 * TODO need a list to keep all the attachments so that even if Content-Id is
 * not there it is accounted
 *
 * @author Vivek Pandey
 * @author Jitendra Kotamraju
 */
public final class MimeMultipartParser {

    private final InputStream in;
    private final String start;
    private final byte[] boundaryBytes;

    private final BitSet lastPartFound = new BitSet(1);
    // current stream position, set to -1 on EOF
    private int b = 0;
    private final int[] bcs = new int[256];
    private int[] gss;
    private static final int BUFFER_SIZE = 4096;
    private byte[] buffer = new byte[BUFFER_SIZE];
    private byte[] prevBuffer = new byte[BUFFER_SIZE];
    private boolean firstPart = true;

    private final Map<String, StreamAttachment> attachments = new HashMap<String, StreamAttachment>();
    private StreamAttachment root;

    private int cidCounter = 0;

    public MimeMultipartParser(InputStream in, String contentType) {
        try {
            ContentType ct = new ContentType(contentType);
            String boundary = ct.getParameter("boundary");
            if (boundary == null || boundary.equals("")) {
                throw new WebServiceException("MIME boundary parameter not found" + contentType);
            }
            String bnd = "--" + boundary;
            boundaryBytes = ASCIIUtility.getBytes(bnd);
            start = ct.getParameter("start");
        } catch (ParseException e) {
            throw new WebServiceException(e);
        }

        //InputStream MUST support mark()
        if (!in.markSupported()) {
            this.in = new BufferedInputStream(in);
        } else {
            this.in = in;
        }
    }

    /**
     * Parses the stream and returns the root part. If start parameter is
     * present in Content-Type, it is used to determine the root part, otherwise
     * root part is the first part.
     *
     * @return StreamAttachment for root part
     *         null if root part cannot be found
     *
     */
    public @Nullable StreamAttachment getRootPart() {
        if (root != null) {
            return root;
        }
        while(!lastBodyPartFound() && (b != -1) && root == null) {
            getNextPart();
        }
        return root;
    }

    /**
     * Parses the entire stream and returns all MIME parts except root MIME part.
     *
     * @return Map<String, StreamAttachment> for all attachment parts
     */
    public @NotNull Map<String, StreamAttachment> getAttachmentParts() {
        while(!lastBodyPartFound() && (b != -1)) {
            getNextPart();
        }
        return attachments;
    }

    /**
     * This method can be called to get a matching MIME attachment part for the
     * given contentId. It parses the stream until it finds a matching part.
     *
     * @return StreamAttachment attachment for contentId
     *         null if there is no attachment for contentId
     */
    public @Nullable StreamAttachment getAttachmentPart(String contentId) throws IOException {
        //first see if this attachment is already parsed, if so return it
        StreamAttachment streamAttach = attachments.get(contentId);
        if (streamAttach != null) {
            return streamAttach;
        }
        //else parse the MIME parts till we get what we want
        while (!lastBodyPartFound() && (b != -1)) {
            streamAttach = getNextPart();
            String newContentId = streamAttach.getContentId();
            if (newContentId != null && newContentId.equals(contentId)){
                return streamAttach;
            }
        }
        return null;            // Attachment is not found
    }

    /**
     * Parses the stream and returns next available MIME part. This shouldn't
     * be called if there are no MIME parts in the stream. Attachment
     * part(not root part) is cached in the {@link Map}<{@link String},{@link StreamAttachment}>
     * before returning the MIME part. It also finds the root part of the MIME
     * package and assigns root variable.
     *
     * @return StreamAttachment next available MIME part
     *
     */
    private StreamAttachment getNextPart() {
        assert !lastBodyPartFound();

        try {
            if (firstPart) {
                compileBoundaryPattern();
                // skip the first boundary of the MIME package
                if (!skipPreamble()) {
                    throw new WebServiceException("Missing Start Boundary, or boundary does not start on a new line");
                }
            }
            InternetHeaders ih = new InternetHeaders(in);
            String[] contentTypes = ih.getHeader("content-type");
            String contentType = (contentTypes != null) ? contentTypes[0] : "application/octet-stream";
            String [] contentIds = ih.getHeader("content-id");
            String mimeContentId = (contentIds != null) ? contentIds[0] : null;
            String contentId = mimeContentId;
            if(mimeContentId!=null && mimeContentId.length()>2) {
                if(contentId.charAt(0)=='<')   contentId=mimeContentId.substring(1,mimeContentId.length()-1);
            }

            ByteArrayBuffer bos = new ByteArrayBuffer();
            b = readBody(bos);
            StreamAttachment as = new StreamAttachment(bos, contentId, contentType);
            if (start == null && firstPart) {
                root = as;      // Taking first part as root part
            } else if (mimeContentId != null && start != null && start.equals(mimeContentId)) {
                root = as;      // root part as identified by start parameter
            } else if (contentId != null) {
                attachments.put(contentId, as);     // Attachment part
            } else {
                ++cidCounter;
                attachments.put(""+cidCounter, as);
            }
            firstPart = false;
            return as;
        } catch(IOException ioe) {
            throw new WebServiceException(ioe);
        } catch(MessagingException me) {
            throw new WebServiceException(me);
        }
    }

    private int readBody(ByteArrayBuffer baos) throws IOException {
        if (!findMimeBody(baos)) {
            //TODO: i18n
            throw new WebServiceException("Missing boundary delimitier ");
        }
        return b;
    }

    private boolean findMimeBody(ByteArrayBuffer out) throws IOException {
        int i;
        int l = boundaryBytes.length;
        int lx = l - 1;
        int bufferLength;
        int s = 0;
        byte[] tmp;

        boolean first = true;
        BitSet eof = new BitSet(1);

        while (true) {
            in.mark(l);
            if (!first) {
                tmp = prevBuffer;
                prevBuffer = buffer;
                buffer = tmp;
            }
            bufferLength = readNext(in, l, eof);

            if (bufferLength == -1) {
                b = -1;
                if ((s == l)) {
                    out.write(prevBuffer, 0, s);
                }
                return true;
            }

            if (bufferLength < l) {
                out.write(buffer, 0, bufferLength);
                b = -1;
                return true;
            }

            for (i = lx; i >= 0; i--) {
                if (buffer[i] != boundaryBytes[i]) {
                    break;
                }
            }

            if (i < 0) {
                if (s > 0) {
                    // so if s == 1 : it must be an LF
                    // if s == 2 : it must be a CR LF
                    if (s <= 2) {
                        String crlf = new String(prevBuffer, 0, s);
                        if (!"\n".equals(crlf) && !"\r\n".equals(crlf)) {
                            throw new WebServiceException(
                                    "Boundary characters encountered in part Body " +
                                            "without a preceeding CRLF");
                        }
                    } else if (s > 2) {
                        if ((prevBuffer[s - 2] == '\r') && (prevBuffer[s - 1] == '\n')) {
                            out.write(prevBuffer, 0, s - 2);
                        } else if (prevBuffer[s - 1] == '\n') {
                            out.write(prevBuffer, 0, s - 1);
                        } else {
                            throw new WebServiceException(
                                    "Boundary characters encountered in part Body " +
                                            "without a preceeding CRLF");
                        }
                    }
                }
                // found the boundary, skip *LWSP-char and CRLF
                if (!skipLWSPAndCRLF(in)) {
                    //throw new Exception(
                    //   "Boundary does not terminate with CRLF");
                }
                return true;
            }

            if ((s > 0)) {
                if (prevBuffer[s - 1] == (byte) 13) {
                    // if buffer[0] == (byte)10
                    if (buffer[0] == (byte) 10) {
                        int j;
                        for (j = lx - 1; j > 0; j--) {
                            if (buffer[j + 1] != boundaryBytes[j]) {
                                break;
                            }
                        }
                        if (j == 0) {
                            // matched the boundaryBytes excluding the last char of the boundaryBytes
                            // so dont write the CR into stream
                            out.write(prevBuffer, 0, s - 1);
                        } else {
                            out.write(prevBuffer, 0, s);
                        }
                    } else {
                        out.write(prevBuffer, 0, s);
                    }
                } else {
                    out.write(prevBuffer, 0, s);
                }
            }

            s = Math.max(i + 1 - bcs[buffer[i] & 0x7f], gss[i]);
            in.reset();
            in.skip(s);
            if (first) {
                first = false;
            }
        }
    }


    private boolean lastBodyPartFound() {
        return lastPartFound.get(0);
    }

    private void compileBoundaryPattern() {
        int l = boundaryBytes.length;

        int i;
        int j;

        // Copied from J2SE 1.4 regex code
        // java.util.regex.Pattern.java

        // Initialise Bad Character Shift table
        for (i = 0; i < l; i++) {
            bcs[boundaryBytes[i]] = i + 1;
        }

        // Initialise Good Suffix Shift table
        gss = new int[l];

        NEXT:
        for (i = l; i > 0; i--) {
            // j is the beginning index of suffix being considered
            for (j = l - 1; j >= i; j--) {
                // Testing for good suffix
                if (boundaryBytes[j] == boundaryBytes[j - i]) {
                    // boundaryBytes[j..len] is a good suffix
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

    private boolean skipPreamble() throws IOException {
        if (!findBoundary()) {
            return false;
        }
        if (lastPartFound.get(0)) {
            throw new WebServiceException("Found closing boundary delimiter while trying to skip preamble");
        }
        return true;
    }

    private boolean findBoundary() throws IOException {
        int i;
        int l = boundaryBytes.length;
        int lx = l - 1;
        BitSet eof = new BitSet(1);

        while (true) {
            in.mark(l);
            readNext(in, l, eof);
            if (eof.get(0)) {
                // End of stream
                return false;
            }

            for (i = lx; i >= 0; i--) {
                if (buffer[i] != boundaryBytes[i]) {
                    break;
                }
            }

            if (i < 0) {
                // found the boundary, skip *LWSP-char and CRLF
                if (!skipLWSPAndCRLF(in)) {
                    throw new WebServiceException("Boundary does not terminate with CRLF");
                }
                return true;
            }

            int s = Math.max(i + 1 - bcs[buffer[i] & 0x7f], gss[i]);
            in.reset();
            in.skip(s);
        }
    }

    private boolean skipLWSPAndCRLF(InputStream is) throws IOException {

        b = is.read();
        //looks like old impl allowed just a \n as well
        if (b == '\n') {
            return true;
        }

        if (b == '\r') {
            b = is.read();
            if (b == '\n') {
                return true;
            } else {
                throw new WebServiceException(
                        "transport padding after a Mime Boundary  should end in a CRLF, found CR only");
            }
        }

        if (b == '-') {
            b = is.read();
            if (b != '-') {
                throw new WebServiceException(
                        "Unexpected singular '-' character after Mime Boundary");
            } else {
                lastPartFound.flip(0);
                // read the next char
                b = is.read();
            }
        }

        while ((b != -1) && ((b == ' ') || (b == '\t'))) {
            b = is.read();
            if (b == '\r') {
                b = is.read();
                if (b == '\n') {
                    return true;
                }
            }
        }

        if (b == -1) {
            // the last boundary need not have CRLF
            if (!lastPartFound.get(0)) {
                throw new WebServiceException(
                        "End of Multipart Stream before encountering  closing boundary delimiter");
            }
            return true;
        }
        return false;
    }


    private int readNext(InputStream is, int patternLength, BitSet eof) throws IOException {
        int bufferLength = is.read(buffer, 0, patternLength);
        if (bufferLength == -1) {
            eof.flip(0);
        } else if (bufferLength < patternLength) {
            //repeatedly read patternLength - bufferLength
            int temp ;
            int i = bufferLength;
            for (; i < patternLength; i++) {
                temp = is.read();
                if (temp == -1) {
                    eof.flip(0);
                    break;
                }
                buffer[i] = (byte) temp;
            }
            bufferLength = i;
        }
        return bufferLength;
    }
}
