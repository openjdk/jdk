/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.InputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.nio.ByteBuffer;
import java.util.logging.Level;

/**
 * Pull parser for the MIME messages. Applications can use pull API to continue
 * the parsing MIME messages lazily.
 *
 * <pre>
 * for e.g.:
 * <p>
 *
 * MIMEParser parser = ...
 * Iterator<MIMEEvent> it = parser.iterator();
 * while(it.hasNext()) {
 *   MIMEEvent event = it.next();
 *   ...
 * }
 * </pre>
 *
 * @author Jitendra Kotamraju
 */
class MIMEParser implements Iterable<MIMEEvent> {

    private static final Logger LOGGER = Logger.getLogger(MIMEParser.class.getName());

    private static final String HEADER_ENCODING = "ISO8859-1";

    // Actually, the grammar doesn't support whitespace characters
    // after boundary. But the mail implementation checks for it.
    // We will only check for these many whitespace characters after boundary
    private static final int NO_LWSP = 1000;
    private enum STATE {START_MESSAGE, SKIP_PREAMBLE, START_PART, HEADERS, BODY, END_PART, END_MESSAGE}
    private STATE state = STATE.START_MESSAGE;

    private final InputStream in;
    private final byte[] bndbytes;
    private final int bl;
    private final MIMEConfig config;
    private final int[] bcs = new int[128]; // BnM algo: Bad Character Shift table
    private final int[] gss;                // BnM algo : Good Suffix Shift table

    /**
     * Have we parsed the data from our InputStream yet?
     */
    private boolean parsed;

    /*
     * Read and process body partsList until we see the
     * terminating boundary line (or EOF).
     */
    private boolean done = false;

    private boolean eof;
    private final int capacity;
    private byte[] buf;
    private int len;
    private boolean bol;        // beginning of the line

    /*
     * Parses the MIME content. At the EOF, it also closes input stream
     */
    MIMEParser(InputStream in, String boundary, MIMEConfig config) {
        this.in = in;
        this.bndbytes = getBytes("--"+boundary);
        bl = bndbytes.length;
        this.config = config;
        gss = new int[bl];
        compileBoundaryPattern();

        // \r\n + boundary + "--\r\n" + lots of LWSP
        capacity = config.chunkSize+2+bl+4+NO_LWSP;
        createBuf(capacity);
    }

    /**
     * Returns iterator for the parsing events. Use the iterator to advance
     * the parsing.
     *
     * @return iterator for parsing events
     */
    @Override
    public Iterator<MIMEEvent> iterator() {
        return new MIMEEventIterator();
    }

    class MIMEEventIterator implements Iterator<MIMEEvent> {

        @Override
        public boolean hasNext() {
            return !parsed;
        }

        @Override
        public MIMEEvent next() {

            if (parsed) {
                throw new NoSuchElementException();
            }

            switch(state) {
                case START_MESSAGE :
                    if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "MIMEParser state={0}", STATE.START_MESSAGE);}
                    state = STATE.SKIP_PREAMBLE;
                    return MIMEEvent.START_MESSAGE;

                case SKIP_PREAMBLE :
                    if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "MIMEParser state={0}", STATE.SKIP_PREAMBLE);}
                    skipPreamble();
                    // fall through
                case START_PART :
                    if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "MIMEParser state={0}", STATE.START_PART);}
                    state = STATE.HEADERS;
                    return MIMEEvent.START_PART;

                case HEADERS :
                    if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "MIMEParser state={0}", STATE.HEADERS);}
                    InternetHeaders ih = readHeaders();
                    state = STATE.BODY;
                    bol = true;
                    return new MIMEEvent.Headers(ih);

                case BODY :
                    if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "MIMEParser state={0}", STATE.BODY);}
                    ByteBuffer buf = readBody();
                    bol = false;
                    return new MIMEEvent.Content(buf);

                case END_PART :
                    if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "MIMEParser state={0}", STATE.END_PART);}
                    if (done) {
                        state = STATE.END_MESSAGE;
                    } else {
                        state = STATE.START_PART;
                    }
                    return MIMEEvent.END_PART;

                case END_MESSAGE :
                    if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "MIMEParser state={0}", STATE.END_MESSAGE);}
                    parsed = true;
                    return MIMEEvent.END_MESSAGE;

                default :
                    throw new MIMEParsingException("Unknown Parser state = "+state);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Collects the headers for the current part by parsing mesage stream.
     *
     * @return headers for the current part
     */
    private InternetHeaders readHeaders() {
        if (!eof) {
            fillBuf();
        }
        return new InternetHeaders(new LineInputStream());
    }

    /**
     * Reads and saves the part of the current attachment part's content.
     * At the end of this method, buf should have the remaining data
     * at index 0.
     *
     * @return a chunk of the part's content
     *
     */
    private ByteBuffer readBody() {
        if (!eof) {
            fillBuf();
        }
        int start = match(buf, 0, len);     // matches boundary
        if (start == -1) {
            // No boundary is found
            assert eof || len >= config.chunkSize;
            int chunkSize = eof ? len : config.chunkSize;
            if (eof) {
                done = true;
                throw new MIMEParsingException("Reached EOF, but there is no closing MIME boundary.");
            }
            return adjustBuf(chunkSize, len-chunkSize);
        }
        // Found boundary.
        // Is it at the start of a line ?
        int chunkLen = start;
        if (bol && start == 0) {
            // nothing to do
        } else if (start > 0 && (buf[start-1] == '\n' || buf[start-1] =='\r')) {
            --chunkLen;
            if (buf[start-1] == '\n' && start >1 && buf[start-2] == '\r') {
                --chunkLen;
            }
        } else {
           return adjustBuf(start+1, len-start-1);  // boundary is not at beginning of a line
        }

        if (start+bl+1 < len && buf[start+bl] == '-' && buf[start+bl+1] == '-') {
            state = STATE.END_PART;
            done = true;
            return adjustBuf(chunkLen, 0);
        }

        // Consider all the whitespace in boundary+whitespace+"\r\n"
        int lwsp = 0;
        for(int i=start+bl; i < len && (buf[i] == ' ' || buf[i] == '\t'); i++) {
            ++lwsp;
        }

        // Check for \n or \r\n in boundary+whitespace+"\n" or boundary+whitespace+"\r\n"
        if (start+bl+lwsp < len && buf[start+bl+lwsp] == '\n') {
            state = STATE.END_PART;
            return adjustBuf(chunkLen, len-start-bl-lwsp-1);
        } else if (start+bl+lwsp+1 < len && buf[start+bl+lwsp] == '\r' && buf[start+bl+lwsp+1] == '\n') {
            state = STATE.END_PART;
            return adjustBuf(chunkLen, len-start-bl-lwsp-2);
        } else if (start+bl+lwsp+1 < len) {
            return adjustBuf(chunkLen+1, len-chunkLen-1);       // boundary string in a part data
        } else if (eof) {
            done = true;
            throw new MIMEParsingException("Reached EOF, but there is no closing MIME boundary.");
        }

        // Some more data needed to determine if it is indeed a proper boundary
        return adjustBuf(chunkLen, len-chunkLen);
    }

    /**
     * Returns a chunk from the original buffer. A new buffer is
     * created with the remaining bytes.
     *
     * @param chunkSize create a chunk with these many bytes
     * @param remaining bytes from the end of the buffer that need to be copied to
     *        the beginning of the new buffer
     * @return chunk
     */
    private ByteBuffer adjustBuf(int chunkSize, int remaining) {
        assert buf != null;
        assert chunkSize >= 0;
        assert remaining >= 0;

        byte[] temp = buf;
        // create a new buf and adjust it without this chunk
        createBuf(remaining);
        System.arraycopy(temp, len-remaining, buf, 0, remaining);
        len = remaining;

        return ByteBuffer.wrap(temp, 0, chunkSize);
    }

    private void createBuf(int min) {
        buf = new byte[min < capacity ? capacity : min];
    }

    /**
     * Skips the preamble to find the first attachment part
     */
    private void skipPreamble() {

        while(true) {
            if (!eof) {
                fillBuf();
            }
            int start = match(buf, 0, len);     // matches boundary
            if (start == -1) {
                // No boundary is found
                if (eof) {
                    throw new MIMEParsingException("Missing start boundary");
                } else {
                    adjustBuf(len-bl+1, bl-1);
                    continue;
                }
            }

            if (start > config.chunkSize) {
                adjustBuf(start, len-start);
                continue;
            }
            // Consider all the whitespace boundary+whitespace+"\r\n"
            int lwsp = 0;
            for(int i=start+bl; i < len && (buf[i] == ' ' || buf[i] == '\t'); i++) {
                ++lwsp;
            }
            // Check for \n or \r\n
            if (start+bl+lwsp < len && (buf[start+bl+lwsp] == '\n' || buf[start+bl+lwsp] == '\r') ) {
                if (buf[start+bl+lwsp] == '\n') {
                    adjustBuf(start+bl+lwsp+1, len-start-bl-lwsp-1);
                    break;
                } else if (start+bl+lwsp+1 < len && buf[start+bl+lwsp+1] == '\n') {
                    adjustBuf(start+bl+lwsp+2, len-start-bl-lwsp-2);
                    break;
                }
            }
            adjustBuf(start+1, len-start-1);
        }
        if (LOGGER.isLoggable(Level.FINE)) {LOGGER.log(Level.FINE, "Skipped the preamble. buffer len={0}", len);}
    }

    private static byte[] getBytes(String s) {
        char [] chars= s.toCharArray();
        int size = chars.length;
        byte[] bytes = new byte[size];

        for (int i = 0; i < size;) {
            bytes[i] = (byte) chars[i++];
        }
        return bytes;
    }

        /**
     * Boyer-Moore search method. Copied from java.util.regex.Pattern.java
     *
     * Pre calculates arrays needed to generate the bad character
     * shift and the good suffix shift. Only the last seven bits
     * are used to see if chars match; This keeps the tables small
     * and covers the heavily used ASCII range, but occasionally
     * results in an aliased match for the bad character shift.
     */
    private void compileBoundaryPattern() {
        int i, j;

        // Precalculate part of the bad character shift
        // It is a table for where in the pattern each
        // lower 7-bit value occurs
        for (i = 0; i < bndbytes.length; i++) {
            bcs[bndbytes[i]&0x7F] = i + 1;
        }

        // Precalculate the good suffix shift
        // i is the shift amount being considered
NEXT:   for (i = bndbytes.length; i > 0; i--) {
            // j is the beginning index of suffix being considered
            for (j = bndbytes.length - 1; j >= i; j--) {
                // Testing for good suffix
                if (bndbytes[j] == bndbytes[j-i]) {
                    // src[j..len] is a good suffix
                    gss[j-1] = i;
                } else {
                    // No match. The array has already been
                    // filled up with correct values before.
                    continue NEXT;
                }
            }
            // This fills up the remaining of optoSft
            // any suffix can not have larger shift amount
            // then its sub-suffix. Why???
            while (j > 0) {
                gss[--j] = i;
            }
        }
        // Set the guard value because of unicode compression
        gss[bndbytes.length -1] = 1;
    }

    /**
     * Finds the boundary in the given buffer using Boyer-Moore algo.
     * Copied from java.util.regex.Pattern.java
     *
     * @param mybuf boundary to be searched in this mybuf
     * @param off start index in mybuf
     * @param len number of bytes in mybuf
     *
     * @return -1 if there is no match or index where the match starts
     */
    private int match(byte[] mybuf, int off, int len) {
        int last = len - bndbytes.length;

        // Loop over all possible match positions in text
NEXT:   while (off <= last) {
            // Loop over pattern from right to left
            for (int j = bndbytes.length - 1; j >= 0; j--) {
                byte ch = mybuf[off+j];
                if (ch != bndbytes[j]) {
                    // Shift search to the right by the maximum of the
                    // bad character shift and the good suffix shift
                    off += Math.max(j + 1 - bcs[ch&0x7F], gss[j]);
                    continue NEXT;
                }
            }
            // Entire pattern matched starting at off
            return off;
        }
        return -1;
    }

    /**
     * Fills the remaining buf to the full capacity
     */
    private void fillBuf() {
        if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "Before fillBuf() buffer len={0}", len);}
        assert !eof;
        while(len < buf.length) {
            int read;
            try {
                read = in.read(buf, len, buf.length-len);
            } catch(IOException ioe) {
                throw new MIMEParsingException(ioe);
            }
            if (read == -1) {
                eof = true;
                try {
                    if (LOGGER.isLoggable(Level.FINE)) {LOGGER.fine("Closing the input stream.");}
                    in.close();
                } catch(IOException ioe) {
                    throw new MIMEParsingException(ioe);
                }
                break;
            } else {
                len += read;
            }
        }
        if (LOGGER.isLoggable(Level.FINER)) {LOGGER.log(Level.FINER, "After fillBuf() buffer len={0}", len);}
    }

    private void doubleBuf() {
        byte[] temp = new byte[2*len];
        System.arraycopy(buf, 0, temp, 0, len);
        buf = temp;
        if (!eof) {
            fillBuf();
        }
    }

    class LineInputStream {
        private int offset;

        /*
         * Read a line containing only ASCII characters from the input
         * stream. A line is terminated by a CR or NL or CR-NL sequence.
         * A common error is a CR-CR-NL sequence, which will also terminate
         * a line.
         * The line terminator is not returned as part of the returned
         * String. Returns null if no data is available. <p>
         *
         * This class is similar to the deprecated
         * <code>DataInputStream.readLine()</code>
         */
        public String readLine() throws IOException {

            int hdrLen = 0;
            int lwsp = 0;
            while(offset+hdrLen < len) {
                if (buf[offset+hdrLen] == '\n') {
                    lwsp = 1;
                    break;
                }
                if (offset+hdrLen+1 == len) {
                    doubleBuf();
                }
                if (offset+hdrLen+1 >= len) {   // No more data in the stream
                    assert eof;
                    return null;
                }
                if (buf[offset+hdrLen] == '\r' && buf[offset+hdrLen+1] == '\n') {
                    lwsp = 2;
                    break;
                }
                ++hdrLen;
            }
            if (hdrLen == 0) {
                adjustBuf(offset+lwsp, len-offset-lwsp);
                return null;
            }

            String hdr = new String(buf, offset, hdrLen, HEADER_ENCODING);
            offset += hdrLen+lwsp;
            return hdr;
        }

    }

}
