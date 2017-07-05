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
 * @(#)QPEncoderStream.java   1.6 02/03/27
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.util;

import java.io.*;

/**
 * This class implements a Quoted Printable Encoder. It is implemented as
 * a FilterOutputStream, so one can just wrap this class around
 * any output stream and write bytes into this filter. The Encoding
 * is done as the bytes are written out.
 *
 * @author John Mani
 */

public class QPEncoderStream extends FilterOutputStream {
    private int count = 0;      // number of bytes that have been output
    private int bytesPerLine;   // number of bytes per line
    private boolean gotSpace = false;
    private boolean gotCR = false;

    /**
     * Create a QP encoder that encodes the specified input stream
     * @param out        the output stream
     * @param bytesPerLine  the number of bytes per line. The encoder
     *                   inserts a CRLF sequence after this many number
     *                   of bytes.
     */
    public QPEncoderStream(OutputStream out, int bytesPerLine) {
        super(out);
        // Subtract 1 to account for the '=' in the soft-return
        // at the end of a line
        this.bytesPerLine = bytesPerLine - 1;
    }

    /**
     * Create a QP encoder that encodes the specified input stream.
     * Inserts the CRLF sequence after outputting 76 bytes.
     * @param out        the output stream
     */
    public QPEncoderStream(OutputStream out) {
        this(out, 76);
    }

    /**
     * Encodes <code>len</code> bytes from the specified
     * <code>byte</code> array starting at offset <code>off</code> to
     * this output stream.
     *
     * @param      b     the data.
     * @param      off   the start offset in the data.
     * @param      len   the number of bytes to write.
     * @exception  IOException  if an I/O error occurs.
     */
    public void write(byte[] b, int off, int len) throws IOException {
        for (int i = 0; i < len; i++)
            write(b[off + i]);
    }

    /**
     * Encodes <code>b.length</code> bytes to this output stream.
     * @param      b   the data to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Encodes the specified <code>byte</code> to this output stream.
     * @param      c   the <code>byte</code>.
     * @exception  IOException  if an I/O error occurs.
     */
    public void write(int c) throws IOException {
        c = c & 0xff; // Turn off the MSB.
        if (gotSpace) { // previous character was <SPACE>
            if (c == '\r' || c == '\n')
                // if CR/LF, we need to encode the <SPACE> char
                output(' ', true);
            else // no encoding required, just output the char
                output(' ', false);
            gotSpace = false;
        }

        if (c == '\r') {
            gotCR = true;
            outputCRLF();
        } else {
            if (c == '\n') {
                if (gotCR)
                    // This is a CRLF sequence, we already output the
                    // corresponding CRLF when we got the CR, so ignore this
                    ;
                else
                    outputCRLF();
            } else if (c == ' ') {
                gotSpace = true;
            } else if (c < 040 || c >= 0177 || c == '=')
                // Encoding required.
                output(c, true);
            else // No encoding required
                output(c, false);
            // whatever it was, it wasn't a CR
            gotCR = false;
        }
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be encoded out to the stream.
     * @exception  IOException  if an I/O error occurs.
     */
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Forces any buffered output bytes to be encoded out to the stream
     * and closes this output stream
     */
    public void close() throws IOException {
        out.close();
    }

    private void outputCRLF() throws IOException {
        out.write('\r');
        out.write('\n');
        count = 0;
    }

    // The encoding table
    private final static char hex[] = {
        '0','1', '2', '3', '4', '5', '6', '7',
        '8','9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    protected void output(int c, boolean encode) throws IOException {
        if (encode) {
            if ((count += 3) > bytesPerLine) {
                out.write('=');
                out.write('\r');
                out.write('\n');
                count = 3; // set the next line's length
            }
            out.write('=');
            out.write(hex[c >> 4]);
            out.write(hex[c & 0xf]);
        } else {
            if (++count > bytesPerLine) {
                out.write('=');
                out.write('\r');
                out.write('\n');
                count = 1; // set the next line's length
            }
            out.write(c);
        }
    }

    /**** begin TEST program ***
    public static void main(String argv[]) throws Exception {
        FileInputStream infile = new FileInputStream(argv[0]);
        QPEncoderStream encoder = new QPEncoderStream(System.out);
        int c;

        while ((c = infile.read()) != -1)
            encoder.write(c);
        encoder.close();
    }
    *** end TEST program ***/
}
