/*
 * Copyright (c) 1995, 2004, Oracle and/or its affiliates. All rights reserved.
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
package sun.misc;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;

/**
 * This class implements a Berkeley uu character encoder. This encoder
 * was made famous by uuencode program.
 *
 * The basic character coding is algorithmic, taking 6 bits of binary
 * data and adding it to an ASCII ' ' (space) character. This converts
 * these six bits into a printable representation. Note that it depends
 * on the ASCII character encoding standard for english. Groups of three
 * bytes are converted into 4 characters by treating the three bytes
 * a four 6 bit groups, group 1 is byte 1's most significant six bits,
 * group 2 is byte 1's least significant two bits plus byte 2's four
 * most significant bits. etc.
 *
 * In this encoding, the buffer prefix is:
 * <pre>
 *     begin [mode] [filename]
 * </pre>
 *
 * This is followed by one or more lines of the form:
 * <pre>
 *      (len)(data)(data)(data) ...
 * </pre>
 * where (len) is the number of bytes on this line. Note that groupings
 * are always four characters, even if length is not a multiple of three
 * bytes. When less than three characters are encoded, the values of the
 * last remaining bytes is undefined and should be ignored.
 *
 * The last line of data in a uuencoded file is represented by a single
 * space character. This is translated by the decoding engine to a line
 * length of zero. This is immediately followed by a line which contains
 * the word 'end[newline]'
 *
 * @author      Chuck McManis
 * @see         CharacterEncoder
 * @see         UUDecoder
 */
public class UUEncoder extends CharacterEncoder {

    /**
     * This name is stored in the begin line.
     */
    private String bufferName;

    /**
     * Represents UNIX(tm) mode bits. Generally three octal digits representing
     * read, write, and execute permission of the owner, group owner, and
     * others. They should be interpreted as the bit groups:
     * (owner) (group) (others)
     *  rwx      rwx     rwx    (r = read, w = write, x = execute)
     *
     * By default these are set to 644 (UNIX rw-r--r-- permissions).
     */
    private int mode;


    /**
     * Default - buffer begin line will be:
     * <pre>
     *  begin 644 encoder.buf
     * </pre>
     */
    public UUEncoder() {
        bufferName = "encoder.buf";
        mode = 644;
    }

    /**
     * Specifies a name for the encoded buffer, begin line will be:
     * <pre>
     *  begin 644 [FNAME]
     * </pre>
     */
    public UUEncoder(String fname) {
        bufferName = fname;
        mode = 644;
    }

    /**
     * Specifies a name and mode for the encoded buffer, begin line will be:
     * <pre>
     *  begin [MODE] [FNAME]
     * </pre>
     */
    public UUEncoder(String fname, int newMode) {
        bufferName = fname;
        mode = newMode;
    }

    /** number of bytes per atom in uuencoding is 3 */
    protected int bytesPerAtom() {
        return (3);
    }

    /** number of bytes per line in uuencoding is 45 */
    protected int bytesPerLine() {
        return (45);
    }

    /**
     * encodeAtom - take three bytes and encodes them into 4 characters
     * If len is less than 3 then remaining bytes are filled with '1'.
     * This insures that the last line won't end in spaces and potentiallly
     * be truncated.
     */
    protected void encodeAtom(OutputStream outStream, byte data[], int offset, int len)
        throws IOException {
        byte    a, b = 1, c = 1;
        int     c1, c2, c3, c4;

        a = data[offset];
        if (len > 1) {
            b = data[offset+1];
        }
        if (len > 2) {
            c = data[offset+2];
        }

        c1 = (a >>> 2) & 0x3f;
        c2 = ((a << 4) & 0x30) | ((b >>> 4) & 0xf);
        c3 = ((b << 2) & 0x3c) | ((c >>> 6) & 0x3);
        c4 = c & 0x3f;
        outStream.write(c1 + ' ');
        outStream.write(c2 + ' ');
        outStream.write(c3 + ' ');
        outStream.write(c4 + ' ');
        return;
    }

    /**
     * Encode the line prefix which consists of the single character. The
     * lenght is added to the value of ' ' (32 decimal) and printed.
     */
    protected void encodeLinePrefix(OutputStream outStream, int length)
        throws IOException {
        outStream.write((length & 0x3f) + ' ');
    }


    /**
     * The line suffix for uuencoded files is simply a new line.
     */
    protected void encodeLineSuffix(OutputStream outStream) throws IOException {
        pStream.println();
    }

    /**
     * encodeBufferPrefix writes the begin line to the output stream.
     */
    protected void encodeBufferPrefix(OutputStream a) throws IOException {
        super.pStream = new PrintStream(a);
        super.pStream.print("begin "+mode+" ");
        if (bufferName != null) {
            super.pStream.println(bufferName);
        } else {
            super.pStream.println("encoder.bin");
        }
        super.pStream.flush();
    }

    /**
     * encodeBufferSuffix writes the single line containing space (' ') and
     * the line containing the word 'end' to the output stream.
     */
    protected void encodeBufferSuffix(OutputStream a) throws IOException {
        super.pStream.println(" \nend");
        super.pStream.flush();
    }

}
