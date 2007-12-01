/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import sun.nio.cs.HistoricallyNamedCharset;

public class Big5_HKSCS extends Charset implements HistoricallyNamedCharset
{
    public Big5_HKSCS() {
        super("Big5-HKSCS", ExtendedCharsets.aliasesFor("Big5-HKSCS"));
    }

    public String historicalName() {
        return "Big5_HKSCS";
    }

    public boolean contains(Charset cs) {
        return ((cs.name().equals("US-ASCII"))
                || (cs instanceof Big5)
                || (cs instanceof Big5_HKSCS));
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    private static class Decoder extends HKSCS_2001.Decoder {

        Big5.Decoder big5Dec;

        protected char decodeDouble(int byte1, int byte2) {
            char c = super.decodeDouble(byte1, byte2);
            return (c != REPLACE_CHAR) ? c : big5Dec.decodeDouble(byte1, byte2);
        }

        private Decoder(Charset cs) {
            super(cs);
            big5Dec = new Big5.Decoder(cs);
        }
    }

    private static class Encoder extends HKSCS_2001.Encoder {

        private Big5.Encoder big5Enc;

        protected int encodeDouble(char ch) {
            int r = super.encodeDouble(ch);
            return (r != 0) ? r : big5Enc.encodeDouble(ch);
        }

        private Encoder(Charset cs) {
            super(cs);
            big5Enc = new Big5.Encoder(cs);
        }
    }
}
