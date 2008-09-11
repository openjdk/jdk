/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 4486841
 * @summary Test UTF-8 charset
 */

import java.nio.charset.*;
import java.nio.*;
import java.util.*;

public class TestUTF8 {
    static char[] decode(byte[] bb, String csn, boolean testDirect)
        throws Exception {
        CharsetDecoder dec = Charset.forName(csn).newDecoder();
        ByteBuffer bbf;
        CharBuffer cbf;
        if (testDirect) {
            bbf = ByteBuffer.allocateDirect(bb.length);
            cbf = ByteBuffer.allocateDirect(bb.length*2).asCharBuffer();
            bbf.put(bb).flip();
        } else {
            bbf = ByteBuffer.wrap(bb);
            cbf = CharBuffer.allocate(bb.length);
        }
        CoderResult cr = dec.decode(bbf, cbf, true);
        if (cr != CoderResult.UNDERFLOW)
            throw new RuntimeException("Decoding err: " + csn);
        char[] cc = new char[cbf.position()];
        cbf.flip(); cbf.get(cc);
        return cc;

    }

    static CoderResult decodeCR(byte[] bb, String csn, boolean testDirect)
        throws Exception {
        CharsetDecoder dec = Charset.forName(csn).newDecoder();
        ByteBuffer bbf;
        CharBuffer cbf;
        if (testDirect) {
            bbf = ByteBuffer.allocateDirect(bb.length);
            cbf = ByteBuffer.allocateDirect(bb.length*2).asCharBuffer();
            bbf.put(bb).flip();
        } else {
            bbf = ByteBuffer.wrap(bb);
            cbf = CharBuffer.allocate(bb.length);
        }
        return dec.decode(bbf, cbf, true);
    }

    static byte[] encode(char[] cc, String csn, boolean testDirect)
        throws Exception {
        ByteBuffer bbf;
        CharBuffer cbf;
        CharsetEncoder enc = Charset.forName(csn).newEncoder();
        if (testDirect) {
            bbf = ByteBuffer.allocateDirect(cc.length * 4);
            cbf = ByteBuffer.allocateDirect(cc.length * 2).asCharBuffer();
            cbf.put(cc).flip();
        } else {
            bbf = ByteBuffer.allocate(cc.length * 4);
            cbf = CharBuffer.wrap(cc);
        }

        CoderResult cr = enc.encode(cbf, bbf, true);
        if (cr != CoderResult.UNDERFLOW)
            throw new RuntimeException("Encoding err: " + csn);
        byte[] bb = new byte[bbf.position()];
        bbf.flip(); bbf.get(bb);
        return bb;
    }

    static CoderResult encodeCR(char[] cc, String csn, boolean testDirect)
        throws Exception {
        ByteBuffer bbf;
        CharBuffer cbf;
        CharsetEncoder enc = Charset.forName(csn).newEncoder();
        if (testDirect) {
            bbf = ByteBuffer.allocateDirect(cc.length * 4);
            cbf = ByteBuffer.allocateDirect(cc.length * 2).asCharBuffer();
            cbf.put(cc).flip();
        } else {
            bbf = ByteBuffer.allocate(cc.length * 4);
            cbf = CharBuffer.wrap(cc);
        }
        return enc.encode(cbf, bbf, true);
    }

    static char[] getUTFChars() {
        char[] cc = new char[0x10000 - 0xe000 + 0xd800 + //bmp
                             (0x110000 - 0x10000) * 2];    //supp
        int pos = 0;
        int i = 0;
        for (i = 0; i < 0xd800; i++)
            cc[pos++] = (char)i;
        for (i = 0xe000; i < 0x10000; i++)
            cc[pos++] = (char)i;
        for (i = 0x10000; i < 0x110000; i++) {
            pos += Character.toChars(i, cc, pos);
        }
        return cc;
    }

    static int to3ByteUTF8(char c, byte[] bb, int pos) {
        bb[pos++] = (byte)(0xe0 | ((c >> 12)));
        bb[pos++] = (byte)(0x80 | ((c >> 06) & 0x3f));
        bb[pos++] = (byte)(0x80 | ((c >> 00) & 0x3f));
        return 3;
    }

    static void checkRoundtrip(String csn) throws Exception {
        System.out.printf("    Check roundtrip <%s>...", csn);
        char[] cc = getUTFChars();
        byte[] bb = encode(cc, csn, false);
        char[] ccO = decode(bb, csn, false);

        if (!Arrays.equals(cc, ccO)) {
            System.out.printf("    non-direct failed");
        }
        bb = encode(cc, csn, true);
        ccO = decode(bb, csn, true);
        if (!Arrays.equals(cc, ccO)) {
            System.out.printf("    (direct) failed");
        }
        System.out.println();
    }

    static void check6ByteSurrs(String csn) throws Exception {
        System.out.printf("    Check 6-byte Surrogates <%s>...%n", csn);
        byte[] bb = new byte[(0x110000 - 0x10000) * 6];
        char[] cc = new char[(0x110000 - 0x10000) * 2];
        int bpos = 0;
        int cpos = 0;
        for (int i = 0x10000; i < 0x110000; i++) {
            Character.toChars(i, cc, cpos);
            bpos += to3ByteUTF8(cc[cpos], bb, bpos);
            bpos += to3ByteUTF8(cc[cpos + 1], bb, bpos);
            cpos += 2;
        }

        char[] ccO = decode(bb, csn, false);
        if (!Arrays.equals(cc, ccO)) {
            System.out.printf("    decoding failed%n");
        }
        ccO = decode(bb, csn, true);
        if (!Arrays.equals(cc, ccO)) {
            System.out.printf("    decoding(direct) failed%n");
        }
    }

    static void compare(String csn1, String csn2) throws Exception {
        System.out.printf("    Diff <%s> <%s>...%n", csn1, csn2);
        char[] cc = getUTFChars();

        byte[] bb1 = encode(cc, csn1, false);
        byte[] bb2 = encode(cc, csn2, false);
        if (!Arrays.equals(bb1, bb2))
            System.out.printf("        encoding failed%n");
        char[] cc1 = decode(bb1, csn1, false);
        char[] cc2 = decode(bb1, csn2, false);
        if (!Arrays.equals(cc1, cc2)) {
            System.out.printf("        decoding failed%n");
        }

        bb1 = encode(cc, csn1, true);
        bb2 = encode(cc, csn2, true);
        if (!Arrays.equals(bb1, bb2))
            System.out.printf("        encoding (direct) failed%n");
        cc1 = decode(bb1, csn1, true);
        cc2 = decode(bb1, csn2, true);
        if (!Arrays.equals(cc1, cc2)) {
            System.out.printf("        decoding (direct) failed%n");
        }
    }

    // The first byte is the length of malformed bytes
    static byte[][] malformed = {
        // One-byte sequences:
        {1, (byte)0xFF },
        {1, (byte)0xC0 },
        {1, (byte)0x80 },

        {1, (byte)0xFF, (byte)0xFF}, // all ones
        {1, (byte)0xA0, (byte)0x80}, // 101x first byte first nibble

        // Two-byte sequences:
        {1, (byte)0xC0, (byte)0x80}, // invalid first byte
        {1, (byte)0xC1, (byte)0xBF}, // invalid first byte
        {1, (byte)0xC2, (byte)0x00}, // invalid second byte
        {1, (byte)0xC2, (byte)0xC0}, // invalid second byte
        {1, (byte)0xD0, (byte)0x00}, // invalid second byte
        {1, (byte)0xD0, (byte)0xC0}, // invalid second byte
        {1, (byte)0xDF, (byte)0x00}, // invalid second byte
        {1, (byte)0xDF, (byte)0xC0}, // invalid second byte

        // Three-byte sequences
        {1, (byte)0xE0, (byte)0x80, (byte)0x80},  // 111x first byte first nibble
        {1, (byte)0xE0, (byte)0x80, (byte)0x80 }, // U+0000 zero-padded
        {1, (byte)0xE0, (byte)0x81, (byte)0xBF }, // U+007F zero-padded
        {1, (byte)0xE0, (byte)0x9F, (byte)0xBF }, // U+07FF zero-padded

        {1, (byte)0xE0, (byte)0xC0, (byte)0xBF }, // invalid second byte
        {2, (byte)0xE0, (byte)0xA0, (byte)0x7F }, // invalid third byte
        {2, (byte)0xE0, (byte)0xA0, (byte)0xC0 }, // invalid third byte
        {1, (byte)0xFF, (byte)0xFF, (byte)0xFF }, // all ones
        {1, (byte)0xE0, (byte)0xC0, (byte)0x80 }, // invalid second byte
        {1, (byte)0xE0, (byte)0x80, (byte)0xC0 }, // invalid first byte

        // Four-byte sequences
        {1, (byte)0xF0, (byte)0x80, (byte)0x80, (byte)0x80 }, // U+0000 zero-padded
        {1, (byte)0xF0, (byte)0x80, (byte)0x81, (byte)0xBF }, // U+007F zero-padded
        {1, (byte)0xF0, (byte)0x80, (byte)0x9F, (byte)0xBF }, // U+007F zero-padded
        {1, (byte)0xF0, (byte)0x8F, (byte)0xBF, (byte)0xBF }, // U+07FF zero-padded

        {1, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF }, // all ones
        {1, (byte)0xF0, (byte)0x80, (byte)0x80, (byte)0x80},  // invalid second byte
        {1, (byte)0xF0, (byte)0xC0, (byte)0x80, (byte)0x80 }, // invalid second byte
        {2, (byte)0xF0, (byte)0x90, (byte)0xC0, (byte)0x80 }, // invalid third byte
        {3, (byte)0xF0, (byte)0x90, (byte)0x80, (byte)0xC0 }, // invalid third byte

        {1, (byte)0xF1, (byte)0xC0, (byte)0x80, (byte)0x80 }, // invalid second byte
        {2, (byte)0xF1, (byte)0x80, (byte)0xC0, (byte)0x80 }, // invalid third byte
        {3, (byte)0xF1, (byte)0x80, (byte)0x80, (byte)0xC0 }, // invalid forth byte
        {1, (byte)0xF4, (byte)0x90, (byte)0x80, (byte)0xC0 }, // out-range 4-byte
        {1, (byte)0xF4, (byte)0xC0, (byte)0x80, (byte)0xC0 }, // out-range 4-byte
        {1, (byte)0xF5, (byte)0x80, (byte)0x80, (byte)0xC0 }, // out-range 4-byte

        // Five-byte sequences
        {5, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80},  // invalid first byte
        {5, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80 }, // U+0000 zero-padded
        {5, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x81, (byte)0xBF }, // U+007F zero-padded
        {5, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x9F, (byte)0xBF }, // U+07FF zero-padded
        {5, (byte)0xF8, (byte)0x80, (byte)0x8F, (byte)0xBF, (byte)0xBF }, // U+FFFF zero-padded

        {1, (byte)0xF8, (byte)0xC0, (byte)0x80, (byte)0x80, (byte)0x80},
        {2, (byte)0xF8, (byte)0x80, (byte)0xC0, (byte)0x80, (byte)0x80 },
        {3, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0xC1, (byte)0xBF },
        {4, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x9F, (byte)0xC0 },

        // Six-byte sequences
        {6, (byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80 }, // U+0000 zero-padded
        {6, (byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x81, (byte)0xBF }, // U+007F zero-padded
        {6, (byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x9F, (byte)0xBF }, // U+07FF zero-padded
        {6, (byte)0xFC, (byte)0x80, (byte)0x80, (byte)0x8F, (byte)0xBF, (byte)0xBF }, // U+FFFF zero-padded
        {1, (byte)0xF8, (byte)0xC0, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80 },
        {2, (byte)0xF8, (byte)0x80, (byte)0xC0, (byte)0x80, (byte)0x80, (byte)0x80 },
        {3, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0xC1, (byte)0xBF, (byte)0x80 },
        {4, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x9F, (byte)0xC0, (byte)0x80 },
        {5, (byte)0xF8, (byte)0x80, (byte)0x80, (byte)0x9F, (byte)0x80, (byte)0xC0 },
    };

    static void checkMalformed(String csn) throws Exception {
        boolean failed = false;
        System.out.printf("    Check malformed <%s>...%n", csn);
        for (boolean direct: new boolean[] {false, true}) {
            for (byte[] bins : malformed) {
                int mlen = bins[0];
                byte[] bin = Arrays.copyOfRange(bins, 1, bins.length);
                CoderResult cr = decodeCR(bin, csn, direct);
                String ashex = "";
                for (int i = 0; i < bin.length; i++) {
                    if (i > 0) ashex += " ";
                        ashex += Integer.toBinaryString((int)bin[i] & 0xff);
                }
                if (!cr.isMalformed()) {
                    System.out.printf("        FAIL(direct=%b): [%s] not malformed.\n", direct, ashex);
                    failed = true;
                } else if (cr.length() != mlen) {
                    System.out.printf("        FAIL(direct=%b): [%s] malformed[len=%d].\n", direct, ashex, cr.length());
                    failed = true;
                }
            }
        }
        if (failed)
            throw new RuntimeException("Check malformed failed " + csn);
    }

    static boolean check(CharsetDecoder dec, byte[] utf8s, boolean direct, int[] flow) {
        int inPos = flow[0];
        int inLen = flow[1];
        int outPos = flow[2];
        int outLen = flow[3];
        int expedInPos = flow[4];
        int expedOutPos = flow[5];
        CoderResult expedCR = (flow[6]==0)?CoderResult.UNDERFLOW
                                          :CoderResult.OVERFLOW;
        ByteBuffer bbf;
        CharBuffer cbf;
        if (direct) {
            bbf = ByteBuffer.allocateDirect(inPos + utf8s.length);
            cbf = ByteBuffer.allocateDirect((outPos + outLen)*2).asCharBuffer();
        } else {
            bbf = ByteBuffer.allocate(inPos + utf8s.length);
            cbf = CharBuffer.allocate(outPos + outLen);
        }
        bbf.position(inPos);
        bbf.put(utf8s).flip().position(inPos).limit(inPos + inLen);
        cbf.position(outPos);
        dec.reset();
        CoderResult cr = dec.decode(bbf, cbf, false);
        if (cr != expedCR ||
            bbf.position() != expedInPos ||
            cbf.position() != expedOutPos) {
            System.out.printf("Expected(direct=%5b): [", direct);
            for (int i:flow) System.out.print(" " + i);
            System.out.println("]  CR=" + cr +
                               ", inPos=" + bbf.position() +
                               ", outPos=" + cbf.position());
            return false;
        }
        return true;
    }

    static void checkUnderOverflow(String csn) throws Exception {
        System.out.printf("    Check under/overflow <%s>...%n", csn);
        CharsetDecoder dec = Charset.forName(csn).newDecoder();
        boolean failed = false;
        byte[] utf8s = new String("\u007f\u07ff\ue000\ud800\udc00").getBytes("UTF-8");
        int    inlen = utf8s.length;

        for (int inoff = 0; inoff < 20; inoff++) {
            for (int outoff = 0; outoff < 20; outoff++) {
        int[][] Flows = {
            //inpos, inLen, outPos,  outLen, inPosEP,   outposEP,   under(0)/over(1)
            {inoff,  inlen, outoff,  1,      inoff + 1, outoff + 1, 1},
            {inoff,  inlen, outoff,  2,      inoff + 3, outoff + 2, 1},
            {inoff,  inlen, outoff,  3,      inoff + 6, outoff + 3, 1},
            {inoff,  inlen, outoff,  4,      inoff + 6, outoff + 3, 1},
            {inoff,  inlen, outoff,  5,      inoff + 10,outoff + 5, 0},
             // underflow
            {inoff,  1,     outoff,  5,      inoff + 1, outoff + 1, 0},
            {inoff,  2,     outoff,  5,      inoff + 1, outoff + 1, 0},
            {inoff,  3,     outoff,  5,      inoff + 3, outoff + 2, 0},
            {inoff,  4,     outoff,  5,      inoff + 3, outoff + 2, 0},
            {inoff,  5,     outoff,  5,      inoff + 3, outoff + 2, 0},
            {inoff,  6,     outoff,  5,      inoff + 6, outoff + 3, 0},
            {inoff,  7,     outoff,  5,      inoff + 6, outoff + 3, 0},
            {inoff,  8,     outoff,  5,      inoff + 6, outoff + 3, 0},
            {inoff,  9,     outoff,  5,      inoff + 6, outoff + 3, 0},
            {inoff,  10,    outoff,  5,      inoff + 10,outoff + 5, 0},
             // 2-byte underflow/overflow
            {inoff,  2,     outoff,  1,      inoff + 1, outoff + 1, 0},
            {inoff,  3,     outoff,  1,      inoff + 1, outoff + 1, 1},
             // 3-byte underflow/overflow
            {inoff,  4,     outoff,  2,      inoff + 3, outoff + 2, 0},
            {inoff,  5,     outoff,  2,      inoff + 3, outoff + 2, 0},
            {inoff,  6,     outoff,  2,      inoff + 3, outoff + 2, 1},
             // 4-byte underflow/overflow
            {inoff,  7,     outoff,  4,      inoff + 6, outoff + 3, 0},
            {inoff,  8,     outoff,  4,      inoff + 6, outoff + 3, 0},
            {inoff,  9,     outoff,  4,      inoff + 6, outoff + 3, 0},
            {inoff,  10,    outoff,  4,      inoff + 6, outoff + 3, 1},
        };
        for (boolean direct: new boolean[] {false, true}) {
            for (int[] flow: Flows) {
                if (!check(dec, utf8s, direct, flow))
                    failed = true;
            }
        }}}
        if (failed)
            throw new RuntimeException("Check under/overflow failed " + csn);
    }

    public static void main(String[] args) throws Exception {
        checkRoundtrip("UTF-8");
        check6ByteSurrs("UTF-8");
        //compare("UTF-8", "UTF-8-OLD");
        checkMalformed("UTF-8");
        checkUnderOverflow("UTF-8");
    }
}
