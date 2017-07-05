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
 * @(#)UUEncoderStream.java   1.3 02/03/27
 */



package com.sun.xml.internal.messaging.saaj.packaging.mime.util;

import java.io.*;

/**
 * This class implements a UUEncoder. It is implemented as
 * a FilterOutputStream, so one can just wrap this class around
 * any output stream and write bytes into this filter. The Encoding
 * is done as the bytes are written out.
 *
 * @author John Mani
 */

public class UUEncoderStream extends FilterOutputStream {
    private byte[] buffer;      // cache of bytes that are yet to be encoded
    private int bufsize = 0;    // size of the cache
    private boolean wrotePrefix = false;

    protected String name;      // name of file
    protected int mode;         // permissions mode

    /**
     * Create a UUencoder that encodes the specified input stream
     * @param out        the output stream
     */
    public UUEncoderStream(OutputStream out) {
        this(out, "encoder.buf", 644);
    }

    /**
     * Create a UUencoder that encodes the specified input stream
     * @param out        the output stream
     * @param name       Specifies a name for the encoded buffer
     */
    public UUEncoderStream(OutputStream out, String name) {
        this(out, name, 644);
    }

    /**
     * Create a UUencoder that encodes the specified input stream
     * @param out        the output stream
     * @param name       Specifies a name for the encoded buffer
     * @param mode       Specifies permission mode for the encoded buffer
     */
    public UUEncoderStream(OutputStream out, String name, int mode) {
        super(out);
        this.name = name;
        this.mode = mode;
        buffer = new byte[45];
    }

    /**
     * Set up the buffer name and permission mode.
     * This method has any effect only if it is invoked before
     * you start writing into the output stream
     */
    public void setNameMode(String name, int mode) {
        this.name = name;
        this.mode = mode;
    }

    public void write(byte[] b, int off, int len) throws IOException {
        for (int i = 0; i < len; i++)
            write(b[off + i]);
    }

    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    public void write(int c) throws IOException {
        /* buffer up characters till we get a line's worth, then encode
         * and write them out. Max number of characters allowed per
         * line is 45.
         */
        buffer[bufsize++] = (byte)c;
        if (bufsize == 45) {
            writePrefix();
            encode();
            bufsize = 0;
        }
    }

    public void flush() throws IOException {
        if (bufsize > 0) { // If there's unencoded characters in the buffer
            writePrefix();
            encode();      // .. encode them
        }
        writeSuffix();
        out.flush();
    }

    public void close() throws IOException {
        flush();
        out.close();
    }

    /**
     * Write out the prefix: "begin <mode> <name>"
     */
    private void writePrefix() throws IOException {
        if (!wrotePrefix) {
            PrintStream ps = new PrintStream(out);
            ps.println("begin " + mode + " " + name);
            ps.flush();
            wrotePrefix = true;
        }
    }

    /**
     * Write a single line containing space and the suffix line
     * containing the single word "end" (terminated by a newline)
     */
    private void writeSuffix() throws IOException {
        PrintStream ps = new PrintStream(out);
        ps.println(" \nend");
        ps.flush();
    }

    /**
     * Encode a line.
     * Start off with the character count, followed by the encoded atoms
     * and terminate with LF. (or is it CRLF or the local line-terminator ?)
     * Take three bytes and encodes them into 4 characters
     * If bufsize if not a multiple of 3, the remaining bytes are filled
     * with '1'. This insures that the last line won't end in spaces
     * and potentiallly be truncated.
     */
    private void encode() throws IOException {
        byte a, b, c;
        int c1, c2, c3, c4;
        int i = 0;

        // Start off with the count of characters in the line
        out.write((bufsize & 0x3f) + ' ');

        while (i < bufsize) {
            a = buffer[i++];
            if (i < bufsize) {
                b = buffer[i++];
                if (i < bufsize)
                    c = buffer[i++];
                else // default c to 1
                    c = 1;
            }
            else { // default b & c to 1
                b = 1;
                c = 1;
            }

            c1 = (a >>> 2) & 0x3f;
            c2 = ((a << 4) & 0x30) | ((b >>> 4) & 0xf);
            c3 = ((b << 2) & 0x3c) | ((c >>> 6) & 0x3);
            c4 = c & 0x3f;
            out.write(c1 + ' ');
            out.write(c2 + ' ');
            out.write(c3 + ' ');
            out.write(c4 + ' ');
        }
        // Terminate with LF. (should it be CRLF or local line-terminator ?)
        out.write('\n');
    }

    /**** begin TEST program *****
    public static void main(String argv[]) throws Exception {
        FileInputStream infile = new FileInputStream(argv[0]);
        UUEncoderStream encoder = new UUEncoderStream(System.out);
        int c;

        while ((c = infile.read()) != -1)
            encoder.write(c);
        encoder.close();
    }
    **** end TEST program *****/
}
