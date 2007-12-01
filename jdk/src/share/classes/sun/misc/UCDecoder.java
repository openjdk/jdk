/*
 * Copyright 1995-2000 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.misc;

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PushbackInputStream;
import java.io.PrintStream;
import java.io.IOException;

/**
 * This class implements a robust character decoder. The decoder will
 * converted encoded text into binary data.
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
 * If an error is encountered during decoding this class throws a
 * CEFormatException. The specific detail messages are:
 *
 * <pre>
 *    "UCDecoder: High byte parity error."
 *    "UCDecoder: Low byte parity error."
 *    "UCDecoder: Out of sequence line."
 *    "UCDecoder: CRC check failed."
 * </pre>
 *
 * @author      Chuck McManis
 * @see         CharacterEncoder
 * @see         UCEncoder
 */
public class UCDecoder extends CharacterDecoder {

    /** This class encodes two bytes per atom. */
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
     * Decode one atom - reads the characters from the input stream, decodes
     * them, and checks for valid parity.
     */
    protected void decodeAtom(PushbackInputStream inStream, OutputStream outStream, int l) throws IOException {
        int i, p1, p2, np1, np2;
        byte a = -1, b = -1, c = -1;
        byte high_byte, low_byte;
        byte tmp[] = new byte[3];

        i = inStream.read(tmp);
        if (i != 3) {
                throw new CEStreamExhausted();
        }
        for (i = 0; (i < 64) && ((a == -1) || (b == -1) || (c == -1)); i++) {
            if (tmp[0] == map_array[i]) {
                a = (byte) i;
            }
            if (tmp[1] == map_array[i]) {
                b = (byte) i;
            }
            if (tmp[2] == map_array[i]) {
                c = (byte) i;
            }
        }
        high_byte = (byte) (((a & 0x38) << 2) + (b & 0x1f));
        low_byte = (byte) (((a & 0x7) << 5) + (c & 0x1f));
        p1 = 0;
        p2 = 0;
        for (i = 1; i < 256; i = i * 2) {
            if ((high_byte & i) != 0)
                p1++;
            if ((low_byte & i) != 0)
                p2++;
        }
        np1 = (b & 32) / 32;
        np2 = (c & 32) / 32;
        if ((p1 & 1) != np1) {
            throw new CEFormatException("UCDecoder: High byte parity error.");
        }
        if ((p2 & 1) != np2) {
            throw new CEFormatException("UCDecoder: Low byte parity error.");
        }
        outStream.write(high_byte);
        crc.update(high_byte);
        if (l == 2) {
            outStream.write(low_byte);
            crc.update(low_byte);
        }
    }

    private ByteArrayOutputStream lineAndSeq = new ByteArrayOutputStream(2);

    /**
     * decodeBufferPrefix initializes the sequence number to zero.
     */
    protected void decodeBufferPrefix(PushbackInputStream inStream, OutputStream outStream) {
        sequence = 0;
    }

    /**
     * decodeLinePrefix reads the sequence number and the number of
     * encoded bytes from the line. If the sequence number is not the
     * previous sequence number + 1 then an exception is thrown.
     * UCE lines are line terminator immune, they all start with *
     * so the other thing this method does is scan for the next line
     * by looking for the * character.
     *
     * @exception CEFormatException out of sequence lines detected.
     */
    protected int decodeLinePrefix(PushbackInputStream inStream, OutputStream outStream)  throws IOException {
        int     i;
        int     nLen, nSeq;
        byte    xtmp[];
        int     c;

        crc.value = 0;
        while (true) {
            c = inStream.read(tmp, 0, 1);
            if (c == -1) {
                throw new CEStreamExhausted();
            }
            if (tmp[0] == '*') {
                break;
            }
        }
        lineAndSeq.reset();
        decodeAtom(inStream, lineAndSeq, 2);
        xtmp = lineAndSeq.toByteArray();
        nLen = xtmp[0] & 0xff;
        nSeq = xtmp[1] & 0xff;
        if (nSeq != sequence) {
            throw new CEFormatException("UCDecoder: Out of sequence line.");
        }
        sequence = (sequence + 1) & 0xff;
        return (nLen);
    }


    /**
     * this method reads the CRC that is at the end of every line and
     * verifies that it matches the computed CRC.
     *
     * @exception CEFormatException if CRC check fails.
     */
    protected void decodeLineSuffix(PushbackInputStream inStream, OutputStream outStream) throws IOException {
        int i;
        int lineCRC = crc.value;
        int readCRC;
        byte tmp[];

        lineAndSeq.reset();
        decodeAtom(inStream, lineAndSeq, 2);
        tmp = lineAndSeq.toByteArray();
        readCRC = ((tmp[0] << 8) & 0xFF00) + (tmp[1] & 0xff);
        if (readCRC != lineCRC) {
            throw new CEFormatException("UCDecoder: CRC check failed.");
        }
    }
}
