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
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * This class encodes a buffer into the classic: "Hexadecimal Dump" format of
 * the past. It is useful for analyzing the contents of binary buffers.
 * The format produced is as follows:
 * <pre>
 * xxxx: 00 11 22 33 44 55 66 77   88 99 aa bb cc dd ee ff ................
 * </pre>
 * Where xxxx is the offset into the buffer in 16 byte chunks, followed
 * by ascii coded hexadecimal bytes followed by the ASCII representation of
 * the bytes or '.' if they are not valid bytes.
 *
 * @author      Chuck McManis
 */

public class HexDumpEncoder extends CharacterEncoder {

    private int offset;
    private int thisLineLength;
    private int currentByte;
    private byte thisLine[] = new byte[16];

    static void hexDigit(PrintStream p, byte x) {
        char c;

        c = (char) ((x >> 4) & 0xf);
        if (c > 9)
            c = (char) ((c-10) + 'A');
        else
            c = (char)(c + '0');
        p.write(c);
        c = (char) (x & 0xf);
        if (c > 9)
            c = (char)((c-10) + 'A');
        else
            c = (char)(c + '0');
        p.write(c);
    }

    protected int bytesPerAtom() {
        return (1);
    }

    protected int bytesPerLine() {
        return (16);
    }

    protected void encodeBufferPrefix(OutputStream o) throws IOException {
        offset = 0;
        super.encodeBufferPrefix(o);
    }

    protected void encodeLinePrefix(OutputStream o, int len) throws IOException {
        hexDigit(pStream, (byte)((offset >>> 8) & 0xff));
        hexDigit(pStream, (byte)(offset & 0xff));
        pStream.print(": ");
        currentByte = 0;
        thisLineLength = len;
    }

    protected void encodeAtom(OutputStream o, byte buf[], int off, int len) throws IOException {
        thisLine[currentByte] = buf[off];
        hexDigit(pStream, buf[off]);
        pStream.print(" ");
        currentByte++;
        if (currentByte == 8)
            pStream.print("  ");
    }

    protected void encodeLineSuffix(OutputStream o) throws IOException {
        if (thisLineLength < 16) {
            for (int i = thisLineLength; i < 16; i++) {
                pStream.print("   ");
                if (i == 7)
                    pStream.print("  ");
            }
        }
        pStream.print(" ");
        for (int i = 0; i < thisLineLength; i++) {
            if ((thisLine[i] < ' ') || (thisLine[i] > 'z')) {
                pStream.print(".");
            } else {
                pStream.write(thisLine[i]);
            }
        }
        pStream.println();
        offset += thisLineLength;
    }

}
