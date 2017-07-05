/*
 * Copyright (c) 1995, 2001, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PushbackInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;

/**
 * This class implements a Berkeley uu character decoder. This decoder
 * was made famous by the uudecode program.
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
 * The last line of data in a uuencoded buffer is represented by a single
 * space character. This is translated by the decoding engine to a line
 * length of zero. This is immediately followed by a line which contains
 * the word 'end[newline]'
 *
 * If an error is encountered during decoding this class throws a
 * CEFormatException. The specific detail messages are:
 *
 * <pre>
 *      "UUDecoder: No begin line."
 *      "UUDecoder: Malformed begin line."
 *      "UUDecoder: Short Buffer."
 *      "UUDecoder: Bad Line Length."
 *      "UUDecoder: Missing 'end' line."
 * </pre>
 *
 * @author      Chuck McManis
 * @see         CharacterDecoder
 * @see         UUEncoder
 */
public class UUDecoder extends CharacterDecoder {

    /**
     * This string contains the name that was in the buffer being decoded.
     */
    public String bufferName;

    /**
     * Represents UNIX(tm) mode bits. Generally three octal digits
     * representing read, write, and execute permission of the owner,
     * group owner, and  others. They should be interpreted as the bit groups:
     * <pre>
     * (owner) (group) (others)
     *  rwx      rwx     rwx    (r = read, w = write, x = execute)
     *</pre>
     *
     */
    public int mode;


    /**
     * UU encoding specifies 3 bytes per atom.
     */
    protected int bytesPerAtom() {
        return (3);
    }

    /**
     * All UU lines have 45 bytes on them, for line length of 15*4+1 or 61
     * characters per line.
     */
    protected int bytesPerLine() {
        return (45);
    }

    /** This is used to decode the atoms */
    private byte decoderBuffer[] = new byte[4];

    /**
     * Decode a UU atom. Note that if l is less than 3 we don't write
     * the extra bits, however the encoder always encodes 4 character
     * groups even when they are not needed.
     */
    protected void decodeAtom(PushbackInputStream inStream, OutputStream outStream, int l)
        throws IOException {
        int i, c1, c2, c3, c4;
        int a, b, c;
        StringBuilder x = new StringBuilder();

        for (i = 0; i < 4; i++) {
            c1 = inStream.read();
            if (c1 == -1) {
                throw new CEStreamExhausted();
            }
            x.append((char)c1);
            decoderBuffer[i] = (byte) ((c1 - ' ') & 0x3f);
        }
        a = ((decoderBuffer[0] << 2) & 0xfc) | ((decoderBuffer[1] >>> 4) & 3);
        b = ((decoderBuffer[1] << 4) & 0xf0) | ((decoderBuffer[2] >>> 2) & 0xf);
        c = ((decoderBuffer[2] << 6) & 0xc0) | (decoderBuffer[3] & 0x3f);
        outStream.write((byte)(a & 0xff));
        if (l > 1) {
            outStream.write((byte)( b & 0xff));
        }
        if (l > 2) {
            outStream.write((byte)(c&0xff));
        }
    }

    /**
     * For uuencoded buffers, the data begins with a line of the form:
     *          begin MODE FILENAME
     * This line always starts in column 1.
     */
    protected void decodeBufferPrefix(PushbackInputStream inStream, OutputStream outStream) throws IOException {
        int     c;
        StringBuilder q = new StringBuilder(32);
        String r;
        boolean sawNewLine;

        /*
         * This works by ripping through the buffer until it finds a 'begin'
         * line or the end of the buffer.
         */
        sawNewLine = true;
        while (true) {
            c = inStream.read();
            if (c == -1) {
                throw new CEFormatException("UUDecoder: No begin line.");
            }
            if ((c == 'b')  && sawNewLine){
                c = inStream.read();
                if (c == 'e') {
                    break;
                }
            }
            sawNewLine = (c == '\n') || (c == '\r');
        }

        /*
         * Now we think its begin, (we've seen ^be) so verify it here.
         */
        while ((c != '\n') && (c != '\r')) {
            c = inStream.read();
            if (c == -1) {
                throw new CEFormatException("UUDecoder: No begin line.");
            }
            if ((c != '\n') && (c != '\r')) {
                q.append((char)c);
            }
        }
        r = q.toString();
        if (r.indexOf(' ') != 3) {
                throw new CEFormatException("UUDecoder: Malformed begin line.");
        }
        mode = Integer.parseInt(r.substring(4,7));
        bufferName = r.substring(r.indexOf(' ',6)+1);
        /*
         * Check for \n after \r
         */
        if (c == '\r') {
            c = inStream.read ();
            if ((c != '\n') && (c != -1))
                inStream.unread (c);
        }
    }

    /**
     * In uuencoded buffers, encoded lines start with a character that
     * represents the number of bytes encoded in this line. The last
     * line of input is always a line that starts with a single space
     * character, which would be a zero length line.
     */
    protected int decodeLinePrefix(PushbackInputStream inStream, OutputStream outStream) throws IOException {
        int     c;

        c = inStream.read();
        if (c == ' ') {
            c = inStream.read(); /* discard the (first)trailing CR or LF  */
            c = inStream.read(); /* check for a second one  */
            if ((c != '\n') && (c != -1))
                inStream.unread (c);
            throw new CEStreamExhausted();
        } else if (c == -1) {
            throw new CEFormatException("UUDecoder: Short Buffer.");
        }

        c = (c - ' ') & 0x3f;
        if (c > bytesPerLine()) {
            throw new CEFormatException("UUDecoder: Bad Line Length.");
        }
        return (c);
    }


    /**
     * Find the end of the line for the next operation.
     * The following sequences are recognized as end-of-line
     * CR, CR LF, or LF
     */
    protected void decodeLineSuffix(PushbackInputStream inStream, OutputStream outStream) throws IOException {
        int c;
        while (true) {
            c = inStream.read();
            if (c == -1) {
                throw new CEStreamExhausted();
            }
            if (c == '\n') {
                break;
            }
            if (c == '\r') {
                c = inStream.read();
                if ((c != '\n') && (c != -1)) {
                    inStream.unread (c);
                }
                break;
            }
        }
    }

    /**
     * UUencoded files have a buffer suffix which consists of the word
     * end. This line should immediately follow the line with a single
     * space in it.
     */
    protected void decodeBufferSuffix(PushbackInputStream inStream, OutputStream outStream) throws IOException  {
        int     c;

        c = inStream.read(decoderBuffer);
        if ((decoderBuffer[0] != 'e') || (decoderBuffer[1] != 'n') ||
            (decoderBuffer[2] != 'd')) {
            throw new CEFormatException("UUDecoder: Missing 'end' line.");
        }
    }

}
