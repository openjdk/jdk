/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 1234567
 * @summary Test updated X11 charsets
 * @build X11GB2312_OLD X11GBK_OLD X11KSC5601_OLD
 */

import java.nio.charset.*;
import java.nio.*;
import java.util.*;

public class TestX11CS {

    static char[] decode(byte[] bb, Charset cs)
        throws Exception {
        CharsetDecoder dec = cs.newDecoder();
        ByteBuffer bbf = ByteBuffer.wrap(bb);
        CharBuffer cbf = CharBuffer.allocate(bb.length);
        CoderResult cr = dec.decode(bbf, cbf, true);
        if (cr != CoderResult.UNDERFLOW) {
            System.out.println("DEC-----------------");
            int pos = bbf.position();
            System.out.printf("  cr=%s, bbf.pos=%d, bb[pos]=%x,%x,%x,%x%n",
                              cr.toString(), pos,
                              bb[pos++]&0xff, bb[pos++]&0xff,bb[pos++]&0xff, bb[pos++]&0xff);
            throw new RuntimeException("Decoding err: " + cs.name());
        }
        char[] cc = new char[cbf.position()];
        cbf.flip(); cbf.get(cc);
        return cc;

    }

    static byte[] encode(char[] cc, Charset cs)
        throws Exception {
        ByteBuffer bbf = ByteBuffer.allocate(cc.length * 4);
        CharBuffer cbf = CharBuffer.wrap(cc);
        CharsetEncoder enc = cs.newEncoder();

        CoderResult cr = enc.encode(cbf, bbf, true);
        if (cr != CoderResult.UNDERFLOW) {
            System.out.println("ENC-----------------");
            int pos = cbf.position();
            System.out.printf("  cr=%s, cbf.pos=%d, cc[pos]=%x%n",
                              cr.toString(), pos, cc[pos]&0xffff);
            throw new RuntimeException("Encoding err: " + cs.name());
        }
        byte[] bb = new byte[bbf.position()];
        bbf.flip(); bbf.get(bb);
        return bb;
    }

    static char[] getChars(Charset newCS, Charset oldCS) {
        CharsetEncoder enc = oldCS.newEncoder();
        CharsetEncoder encNew = newCS.newEncoder();
        char[] cc = new char[0x10000];
        int pos = 0;
        int i = 0;
        while (i < 0x10000) {
            if (enc.canEncode((char)i) != encNew.canEncode((char)i)) {
                System.out.printf("  Err i=%x%n", i);
                //throw new RuntimeException("canEncode() err!");
            }
            if (enc.canEncode((char)i)) {
                cc[pos++] = (char)i;
            }
            i++;
        }
        return Arrays.copyOf(cc, pos);
    }

    static void compare(Charset newCS, Charset oldCS) throws Exception {
        System.out.printf("    Diff <%s> <%s>...%n", newCS.name(), oldCS.name());
        char[] cc = getChars(newCS, oldCS);

        byte[] bb1 = encode(cc, newCS);
        byte[] bb2 = encode(cc, oldCS);

        if (!Arrays.equals(bb1, bb2)) {
            System.out.printf("        encoding failed!%n");
        }
        char[] cc1 = decode(bb1, newCS);
        char[] cc2 = decode(bb1, oldCS);
        if (!Arrays.equals(cc1, cc2)) {
            for (int i = 0; i < cc1.length; i++) {
                if (cc1[i] != cc2[i]) {
                    System.out.printf("i=%d, cc1=%x cc2=%x,  bb=<%x%x>%n",
                                      i,
                                      cc1[i]&0xffff, cc2[i]&0xffff,
                                      bb1[i*2]&0xff, bb1[i*2+1]&0xff);
                }

            }

            System.out.printf("        decoding failed%n");
        }
    }

    public static void main(String[] args) throws Exception {
        compare(new sun.awt.motif.X11GBK(),
                new X11GBK_OLD());

        compare(new sun.awt.motif.X11GB2312(),
                new X11GB2312_OLD());

        compare(new sun.awt.motif.X11KSC5601(),
                new X11KSC5601_OLD());

    }
}
