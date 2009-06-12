/*
 * Copyright 2003-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.util.Arrays;
import sun.nio.cs.HistoricallyNamedCharset;

public class IBM949C extends Charset implements HistoricallyNamedCharset
{

    public IBM949C() {
        super("x-IBM949C", ExtendedCharsets.aliasesFor("x-IBM949C"));
    }

    public String historicalName() {
        return "Cp949C";
    }

    public boolean contains(Charset cs) {
        return ((cs.name().equals("US-ASCII"))
                || (cs instanceof IBM949C));
    }

    public CharsetDecoder newDecoder() {
        return new DoubleByte.Decoder(this,
                                      IBM949.b2c,
                                      b2cSB,
                                      0xa1,
                                      0xfe);
    }

    public CharsetEncoder newEncoder() {
        return new DoubleByte.Encoder(this, c2b, c2bIndex);
    }

    final static char[] b2cSB;
    final static char[] c2b;
    final static char[] c2bIndex;

    static {
        IBM949.initb2c();
        b2cSB = new char[0x100];
        for (int i = 0; i < 0x80; i++) {
            b2cSB[i] = (char)i;
        }
        for (int i = 0x80; i < 0x100; i++) {
            b2cSB[i] = IBM949.b2cSB[i];
        }
        IBM949.initc2b();
        c2b = Arrays.copyOf(IBM949.c2b, IBM949.c2b.length);
        c2bIndex = Arrays.copyOf(IBM949.c2bIndex, IBM949.c2bIndex.length);
        for (char c = '\0'; c < '\u0080'; ++c) {
            int index = c2bIndex[c >> 8];
            c2b[index + (c & 0xff)] = c;
        }
    }
}
