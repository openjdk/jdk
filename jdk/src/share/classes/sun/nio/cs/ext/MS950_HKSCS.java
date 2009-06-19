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

package sun.nio.cs.ext;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import sun.nio.cs.HistoricallyNamedCharset;
import static sun.nio.cs.CharsetMapping.*;

public class MS950_HKSCS extends Charset implements HistoricallyNamedCharset
{
    public MS950_HKSCS() {
        super("x-MS950-HKSCS", ExtendedCharsets.aliasesFor("x-MS950-HKSCS"));
    }

    public String historicalName() {
        return "MS950_HKSCS";
    }

    public boolean contains(Charset cs) {
        return ((cs.name().equals("US-ASCII"))
                || (cs instanceof MS950)
                || (cs instanceof MS950_HKSCS));
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    private static class Decoder extends HKSCS.Decoder {

        private static DoubleByte.Decoder ms950Dec =
            (DoubleByte.Decoder)new MS950().newDecoder();

        /*
         * Note current decoder decodes 0x8BC2 --> U+F53A
         * ie. maps to Unicode PUA.
         * Unaccounted discrepancy between this mapping
         * inferred from MS950/windows-950 and the published
         * MS HKSCS mappings which maps 0x8BC2 --> U+5C22
         * a character defined with the Unified CJK block
         */

        protected char decodeDouble(int byte1, int byte2) {
            char c = super.decodeDouble(byte1, byte2);
            return (c != UNMAPPABLE_DECODING) ? c : ms950Dec.decodeDouble(byte1, byte2);
        }

        private Decoder(Charset cs) {
            super(cs);
        }
    }

    private static class Encoder extends HKSCS.Encoder {

        private static DoubleByte.Encoder ms950Enc =
            (DoubleByte.Encoder)new MS950().newEncoder();

        /*
         * Note current encoder encodes U+F53A --> 0x8BC2
         * Published MS HKSCS mappings show
         * U+5C22 <--> 0x8BC2
         */
        protected int encodeDouble(char ch) {
            int r = super.encodeDouble(ch);
            return (r != UNMAPPABLE_ENCODING) ? r : ms950Enc.encodeChar(ch);
        }

        private Encoder(Charset cs) {
            super(cs);
        }
    }
}
