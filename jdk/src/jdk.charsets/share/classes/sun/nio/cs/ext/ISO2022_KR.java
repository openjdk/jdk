/*
 * Copyright (c) 2002, 2006, Oracle and/or its affiliates. All rights reserved.
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
import sun.nio.cs.*;

public class ISO2022_KR extends ISO2022
implements HistoricallyNamedCharset
{
    private static Charset ksc5601_cs;

    public ISO2022_KR() {
        super("ISO-2022-KR", ExtendedCharsets.aliasesFor("ISO-2022-KR"));
        ksc5601_cs = new EUC_KR();
    }

    public boolean contains(Charset cs) {
        // overlapping repertoire of EUC_KR, aka KSC5601
        return ((cs instanceof EUC_KR) ||
             (cs.name().equals("US-ASCII")) ||
             (cs instanceof ISO2022_KR));
    }

    public String historicalName() {
        return "ISO2022KR";
    }

    public CharsetDecoder newDecoder() {
        return new Decoder(this);
    }

    public CharsetEncoder newEncoder() {
        return new Encoder(this);
    }

    private static class Decoder extends ISO2022.Decoder {
        public Decoder(Charset cs)
        {
            super(cs);
            SODesig = new byte[][] {{(byte)'$', (byte)')', (byte)'C'}};
            SODecoder = new CharsetDecoder[1];

            try {
                SODecoder[0] = ksc5601_cs.newDecoder();
            } catch (Exception e) {};
        }
    }

    private static class Encoder extends ISO2022.Encoder {

        public Encoder(Charset cs)
        {
            super(cs);
            SODesig = "$)C";

            try {
                ISOEncoder = ksc5601_cs.newEncoder();
            } catch (Exception e) { }
        }

        public boolean canEncode(char c) {
            return (ISOEncoder.canEncode(c));
        }
    }
}
