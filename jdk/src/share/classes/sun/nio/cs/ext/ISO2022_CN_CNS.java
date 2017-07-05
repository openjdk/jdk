/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 */

package sun.nio.cs.ext;

import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import sun.nio.cs.HistoricallyNamedCharset;

public class ISO2022_CN_CNS extends ISO2022 implements HistoricallyNamedCharset
{
    public ISO2022_CN_CNS() {
        super("x-ISO-2022-CN-CNS", ExtendedCharsets.aliasesFor("x-ISO-2022-CN-CNS"));
    }

    public boolean contains(Charset cs) {
        // overlapping repertoire of EUC_TW, CNS11643
        return ((cs instanceof EUC_TW) ||
                (cs.name().equals("US-ASCII")) ||
                (cs instanceof ISO2022_CN_CNS));
    }

    public String historicalName() {
        return "ISO2022CN_CNS";
    }

    public CharsetDecoder newDecoder() {
        return new ISO2022_CN.Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    private static class Encoder extends ISO2022.Encoder {

        public Encoder(Charset cs)
        {
            super(cs);
            SODesig = "$)G";
            SS2Desig = "$*H";
            SS3Desig = "$+I";

            try {
                Charset cset = Charset.forName("EUC_TW"); // CNS11643
                ISOEncoder = cset.newEncoder();
            } catch (Exception e) { }
        }

        private byte[] bb = new byte[4];
        public boolean canEncode(char c) {
            int n = 0;
            return (c <= '\u007f' ||
                    (n = ((EUC_TW.Encoder)ISOEncoder).toEUC(c, bb)) == 2 ||
                    (n == 4 && bb[0] == SS2 &&
                     (bb[1] == PLANE2 || bb[1] == PLANE3)));
        }

        /*
         * Since ISO2022-CN-CNS possesses a CharsetEncoder
         * without the corresponding CharsetDecoder half the
         * default replacement check needs to be overridden
         * since the parent class version attempts to
         * decode 0x3f (?).
         */

        public boolean isLegalReplacement(byte[] repl) {
            // 0x3f is OK as the replacement byte
            return (repl.length == 1 && repl[0] == (byte) 0x3f);
        }
    }
}
