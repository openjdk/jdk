/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.staxex;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

// for testing method
//import com.sun.xml.internal.stream.writers.XMLStreamWriterImpl;

/**
 * This class implements a BASE64 Encoder. It is implemented as
 * a FilterOutputStream, so one can just wrap this class around
 * any output stream and write bytes into this filter. The Encoding
 * is done as the bytes are written out.
 *
 * @author John Mani
 * @author Bill Shannon
 * @author Martin Grebac
 */

public class Base64EncoderStream extends FilterOutputStream {
    private byte[] buffer;      // cache of bytes that are yet to be encoded
    private int bufsize = 0;    // size of the cache

    private XMLStreamWriter outWriter;

    public Base64EncoderStream(OutputStream out) {
        super(out);
        buffer = new byte[3];
    }

    /**
     * Create a BASE64 encoder that encodes the specified input stream
     */
    public Base64EncoderStream(XMLStreamWriter outWriter, OutputStream out) {
        super(out);
        buffer = new byte[3];
        this.outWriter = outWriter;
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
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        for (int i = 0; i < len; i++)
            write(b[off + i]);
    }

    /**
     * Encodes <code>b.length</code> bytes to this output stream.
     * @param      b   the data to be written.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * Encodes the specified <code>byte</code> to this output stream.
     * @param      c   the <code>byte</code>.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void write(int c) throws IOException {
        buffer[bufsize++] = (byte)c;
        if (bufsize == 3) { // Encoding unit = 3 bytes
            encode();
            bufsize = 0;
        }
    }

    /**
     * Flushes this output stream and forces any buffered output bytes
     * to be encoded out to the stream.
     * @exception  IOException  if an I/O error occurs.
     */
    @Override
    public void flush() throws IOException {
        if (bufsize > 0) { // If there's unencoded characters in the buffer ..
            encode();      // .. encode them
            bufsize = 0;
        }
        out.flush();
        try {
            outWriter.flush();
        } catch (XMLStreamException ex) {
            Logger.getLogger(Base64EncoderStream.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
    }

    /**
     * Forces any buffered output bytes to be encoded out to the stream
     * and closes this output stream
     */
    @Override
    public void close() throws IOException {
        flush();
        out.close();
    }

    /** This array maps the characters to their 6 bit values */
    private final static char pem_array[] = {
        'A','B','C','D','E','F','G','H', // 0
        'I','J','K','L','M','N','O','P', // 1
        'Q','R','S','T','U','V','W','X', // 2
        'Y','Z','a','b','c','d','e','f', // 3
        'g','h','i','j','k','l','m','n', // 4
        'o','p','q','r','s','t','u','v', // 5
        'w','x','y','z','0','1','2','3', // 6
        '4','5','6','7','8','9','+','/'  // 7
    };

    private void encode() throws IOException {
        byte a, b, c;
        char[] buf = new char[4];
        if (bufsize == 1) {
            a = buffer[0];
            b = 0;
            c = 0;
            buf[0] = pem_array[(a >>> 2) & 0x3F];
            buf[1] = pem_array[((a << 4) & 0x30) + ((b >>> 4) & 0xf)];
            buf[2] = '='; // pad character
            buf[3] = '='; // pad character
        } else if (bufsize == 2) {
            a = buffer[0];
            b = buffer[1];
            c = 0;
            buf[0] = pem_array[(a >>> 2) & 0x3F];
            buf[1] = pem_array[((a << 4) & 0x30) + ((b >>> 4) & 0xf)];
            buf[2] = pem_array[((b << 2) & 0x3c) + ((c >>> 6) & 0x3)];
            buf[3] = '='; // pad character
        } else {
            a = buffer[0];
            b = buffer[1];
            c = buffer[2];
            buf[0] = pem_array[(a >>> 2) & 0x3F];
            buf[1] = pem_array[((a << 4) & 0x30) + ((b >>> 4) & 0xf)];
            buf[2] = pem_array[((b << 2) & 0x3c) + ((c >>> 6) & 0x3)];
            buf[3] = pem_array[c & 0x3F];
        }
        try {
            outWriter.writeCharacters(buf, 0, 4);
        } catch (XMLStreamException ex) {
            Logger.getLogger(Base64EncoderStream.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
    }

//    public static void main(String argv[]) throws Exception {
//      FileInputStream infile = new FileInputStream(new File(argv[0]));
//        StringWriter sw = new StringWriter();
//        XMLStreamWriterImpl wi = new XMLStreamWriterImpl(sw, null);
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//      Base64EncoderStream encoder = new Base64EncoderStream(wi, baos);
//      int c;
//
//      while ((c = infile.read()) != -1)
//          encoder.write(c);
//      encoder.close();
//
//        System.out.println("SW: " + sw.toString());
//        System.out.println("BAOS: " + baos.toString());
//
//    }
}
