/*
 * Copyright (c) 1995, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.PrintStream;

/**
 * This class implements a BASE64 Character decoder as specified in RFC1521.
 *
 * This RFC is part of the MIME specification which is published by the
 * Internet Engineering Task Force (IETF). Unlike some other encoding
 * schemes there is nothing in this encoding that tells the decoder
 * where a buffer starts or stops, so to use it you will need to isolate
 * your encoded data into a single chunk and then feed them this decoder.
 * The simplest way to do that is to read all of the encoded data into a
 * string and then use:
 * <pre>
 *      byte    mydata[];
 *      BASE64Decoder base64 = new BASE64Decoder();
 *
 *      mydata = base64.decodeBuffer(bufferString);
 * </pre>
 * This will decode the String in <i>bufferString</i> and give you an array
 * of bytes in the array <i>myData</i>.
 *
 * On errors, this class throws a CEFormatException with the following detail
 * strings:
 * <pre>
 *    "BASE64Decoder: Not enough bytes for an atom."
 * </pre>
 *
 * @author      Chuck McManis
 * @see         CharacterEncoder
 * @see         BASE64Decoder
 */

public class BASE64Decoder extends CharacterDecoder {

    /** This class has 4 bytes per atom */
    protected int bytesPerAtom() {
        return (4);
    }

    /** Any multiple of 4 will do, 72 might be common */
    protected int bytesPerLine() {
        return (72);
    }

    /**
     * This character array provides the character to value map
     * based on RFC1521.
     */
    private static final char pem_array[] = {
        //       0   1   2   3   4   5   6   7
                'A','B','C','D','E','F','G','H', // 0
                'I','J','K','L','M','N','O','P', // 1
                'Q','R','S','T','U','V','W','X', // 2
                'Y','Z','a','b','c','d','e','f', // 3
                'g','h','i','j','k','l','m','n', // 4
                'o','p','q','r','s','t','u','v', // 5
                'w','x','y','z','0','1','2','3', // 6
                '4','5','6','7','8','9','+','/'  // 7
        };

    private static final byte pem_convert_array[] = new byte[256];

    static {
        for (int i = 0; i < 255; i++) {
            pem_convert_array[i] = -1;
        }
        for (int i = 0; i < pem_array.length; i++) {
            pem_convert_array[pem_array[i]] = (byte) i;
        }
    }

    byte decode_buffer[] = new byte[4];

    /**
     * Decode one BASE64 atom into 1, 2, or 3 bytes of data.
     */
    @SuppressWarnings("fallthrough")
    protected void decodeAtom(PushbackInputStream inStream, OutputStream outStream, int rem)
        throws java.io.IOException
    {
        int     i;
        byte    a = -1, b = -1, c = -1, d = -1;

        if (rem < 2) {
            throw new CEFormatException("BASE64Decoder: Not enough bytes for an atom.");
        }
        do {
            i = inStream.read();
            if (i == -1) {
                throw new CEStreamExhausted();
            }
        } while (i == '\n' || i == '\r');
        decode_buffer[0] = (byte) i;

        i = readFully(inStream, decode_buffer, 1, rem-1);
        if (i == -1) {
            throw new CEStreamExhausted();
        }

        if (rem > 3 && decode_buffer[3] == '=') {
            rem = 3;
        }
        if (rem > 2 && decode_buffer[2] == '=') {
            rem = 2;
        }
        switch (rem) {
        case 4:
            d = pem_convert_array[decode_buffer[3] & 0xff];
            // NOBREAK
        case 3:
            c = pem_convert_array[decode_buffer[2] & 0xff];
            // NOBREAK
        case 2:
            b = pem_convert_array[decode_buffer[1] & 0xff];
            a = pem_convert_array[decode_buffer[0] & 0xff];
            break;
        }

        switch (rem) {
        case 2:
            outStream.write( (byte)(((a << 2) & 0xfc) | ((b >>> 4) & 3)) );
            break;
        case 3:
            outStream.write( (byte) (((a << 2) & 0xfc) | ((b >>> 4) & 3)) );
            outStream.write( (byte) (((b << 4) & 0xf0) | ((c >>> 2) & 0xf)) );
            break;
        case 4:
            outStream.write( (byte) (((a << 2) & 0xfc) | ((b >>> 4) & 3)) );
            outStream.write( (byte) (((b << 4) & 0xf0) | ((c >>> 2) & 0xf)) );
            outStream.write( (byte) (((c << 6) & 0xc0) | (d  & 0x3f)) );
            break;
        }
        return;
    }
}
