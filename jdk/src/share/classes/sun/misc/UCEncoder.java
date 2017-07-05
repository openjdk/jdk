/*
 * Copyright (c) 1995, 1997, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.PrintStream;
import java.io.IOException;

/**
 * This class implements a robust character encoder. The encoder is designed
 * to convert binary data into printable characters. The characters are
 * assumed to exist but they are not assumed to be ASCII, the complete set
 * is 0-9, A-Z, a-z, "(", and ")".
 *
 * The basic encoding unit is a 3 character atom. It encodes two bytes
 * of data. Bytes are encoded into a 64 character set, the characters
 * were chosen specifically because they appear in all codesets.
 * We don't care what their numerical equivalent is because
 * we use a character array to map them. This is like UUencoding
 * with the dependency on ASCII removed.
 *
 * The three chars that make up an atom are encoded as follows:
 * <pre>
 *      00xxxyyy 00axxxxx 00byyyyy
 *      00 = leading zeros, all values are 0 - 63
 *      xxxyyy - Top 3 bits of X, Top 3 bits of Y
 *      axxxxx - a = X parity bit, xxxxx lower 5 bits of X
 *      byyyyy - b = Y parity bit, yyyyy lower 5 bits of Y
 * </pre>
 *
 * The atoms are arranged into lines suitable for inclusion into an
 * email message or text file. The number of bytes that are encoded
 * per line is 48 which keeps the total line length  under 80 chars)
 *
 * Each line has the form(
 * <pre>
 *  *(LLSS)(DDDD)(DDDD)(DDDD)...(CRC)
 *  Where each (xxx) represents a three character atom.
 *  (LLSS) - 8 bit length (high byte), and sequence number
 *           modulo 256;
 *  (DDDD) - Data byte atoms, if length is odd, last data
 *           atom has (DD00) (high byte data, low byte 0)
 *  (CRC)  - 16 bit CRC for the line, includes length,
 *           sequence, and all data bytes. If there is a
 *           zero pad byte (odd length) it is _NOT_
 *           included in the CRC.
 * </pre>
 *
 * @author      Chuck McManis
 * @see         CharacterEncoder
 * @see         UCDecoder
 */
public class UCEncoder extends CharacterEncoder {

    /** this clase encodes two bytes per atom */
    protected int bytesPerAtom() {
        return (2);
    }

    /** this class encodes 48 bytes per line */
    protected int bytesPerLine() {
        return (48);
    }

    /* this is the UCE mapping of 0-63 to characters .. */
    private final static byte map_array[] = {
        //     0         1         2         3         4         5         6         7
        (byte)'0',(byte)'1',(byte)'2',(byte)'3',(byte)'4',(byte)'5',(byte)'6',(byte)'7', // 0
        (byte)'8',(byte)'9',(byte)'A',(byte)'B',(byte)'C',(byte)'D',(byte)'E',(byte)'F', // 1
        (byte)'G',(byte)'H',(byte)'I',(byte)'J',(byte)'K',(byte)'L',(byte)'M',(byte)'N', // 2
        (byte)'O',(byte)'P',(byte)'Q',(byte)'R',(byte)'S',(byte)'T',(byte)'U',(byte)'V', // 3
        (byte)'W',(byte)'X',(byte)'Y',(byte)'Z',(byte)'a',(byte)'b',(byte)'c',(byte)'d', // 4
        (byte)'e',(byte)'f',(byte)'g',(byte)'h',(byte)'i',(byte)'j',(byte)'k',(byte)'l', // 5
        (byte)'m',(byte)'n',(byte)'o',(byte)'p',(byte)'q',(byte)'r',(byte)'s',(byte)'t', // 6
        (byte)'u',(byte)'v',(byte)'w',(byte)'x',(byte)'y',(byte)'z',(byte)'(',(byte)')'  // 7
    };

    private int sequence;
    private byte tmp[] = new byte[2];
    private CRC16 crc = new CRC16();

    /**
     * encodeAtom - take two bytes and encode them into the correct
     * three characters. If only one byte is to be encoded, the other
     * must be zero. The padding byte is not included in the CRC computation.
     */
    protected void encodeAtom(OutputStream outStream, byte data[], int offset, int len) throws IOException
    {
        int     i;
        int     p1, p2; // parity bits
        byte    a, b;

        a = data[offset];
        if (len == 2) {
            b = data[offset+1];
        } else {
            b = 0;
        }
        crc.update(a);
        if (len == 2) {
            crc.update(b);
        }
        outStream.write(map_array[((a >>> 2) & 0x38) + ((b >>> 5) & 0x7)]);
        p1 = 0; p2 = 0;
        for (i = 1; i < 256; i = i * 2) {
            if ((a & i) != 0) {
                p1++;
            }
            if ((b & i) != 0) {
                p2++;
            }
        }
        p1 = (p1 & 1) * 32;
        p2 = (p2 & 1) * 32;
        outStream.write(map_array[(a & 31) + p1]);
        outStream.write(map_array[(b & 31) + p2]);
        return;
    }

    /**
     * Each UCE encoded line starts with a prefix of '*[XXX]', where
     * the sequence number and the length are encoded in the first
     * atom.
     */
    protected void encodeLinePrefix(OutputStream outStream, int length) throws IOException {
        outStream.write('*');
        crc.value = 0;
        tmp[0] = (byte) length;
        tmp[1] = (byte) sequence;
        sequence = (sequence + 1) & 0xff;
        encodeAtom(outStream, tmp, 0, 2);
    }


    /**
     * each UCE encoded line ends with YYY and encoded version of the
     * 16 bit checksum. The most significant byte of the check sum
     * is always encoded FIRST.
     */
    protected void encodeLineSuffix(OutputStream outStream) throws IOException {
        tmp[0] = (byte) ((crc.value >>> 8) & 0xff);
        tmp[1] = (byte) (crc.value & 0xff);
        encodeAtom(outStream, tmp, 0, 2);
        super.pStream.println();
    }

    /**
     * The buffer prefix code is used to initialize the sequence number
     * to zero.
     */
    protected void encodeBufferPrefix(OutputStream a) throws IOException {
        sequence = 0;
        super.encodeBufferPrefix(a);
    }
}
