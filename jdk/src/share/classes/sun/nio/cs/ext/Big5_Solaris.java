/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.util.Arrays;
import static sun.nio.cs.CharsetMapping.*;

public class Big5_Solaris extends Charset implements HistoricallyNamedCharset
{
    public Big5_Solaris() {
        super("x-Big5-Solaris", ExtendedCharsets.aliasesFor("x-Big5-Solaris"));
    }

    public String historicalName() {
        return "Big5_Solaris";
    }

    public boolean contains(Charset cs) {
        return ((cs.name().equals("US-ASCII"))
                || (cs instanceof Big5)
                || (cs instanceof Big5_Solaris));
    }

    public CharsetDecoder newDecoder() {
        initb2c();
        return new  DoubleByte.Decoder(this, b2c, b2cSB, 0x40, 0xfe);
    }

    public CharsetEncoder newEncoder() {
        initc2b();
        return new DoubleByte.Encoder(this, c2b, c2bIndex);
    }

    static char[][] b2c;
    static char[] b2cSB;
    private static volatile boolean b2cInitialized = false;

    static void initb2c() {
        if (b2cInitialized)
            return;
        synchronized (Big5_Solaris.class) {
            if (b2cInitialized)
                return;
            Big5.initb2c();
            b2c = Big5.b2c.clone();
            // Big5 Solaris implementation has 7 additional mappings
            int[] sol = new int[] {
                0xF9D6, 0x7881,
                0xF9D7, 0x92B9,
                0xF9D8, 0x88CF,
                0xF9D9, 0x58BB,
                0xF9DA, 0x6052,
                0xF9DB, 0x7CA7,
                0xF9DC, 0x5AFA };
            if (b2c[0xf9] == DoubleByte.B2C_UNMAPPABLE) {
                b2c[0xf9] = new char[0xfe - 0x40 + 1];
                Arrays.fill(b2c[0xf9], UNMAPPABLE_DECODING);
            }

            for (int i = 0; i < sol.length;) {
                b2c[0xf9][sol[i++] & 0xff - 0x40] = (char)sol[i++];
            }
            b2cSB = Big5.b2cSB;
            b2cInitialized = true;
        }
    }

    static char[] c2b;
    static char[] c2bIndex;
    private static volatile boolean c2bInitialized = false;

    static void initc2b() {
        if (c2bInitialized)
            return;
        synchronized (Big5_Solaris.class) {
            if (c2bInitialized)
                return;
            Big5.initc2b();
            c2b = Big5.c2b.clone();
            c2bIndex = Big5.c2bIndex.clone();
            int[] sol = new int[] {
                0x7881, 0xF9D6,
                0x92B9, 0xF9D7,
                0x88CF, 0xF9D8,
                0x58BB, 0xF9D9,
                0x6052, 0xF9DA,
                0x7CA7, 0xF9DB,
                0x5AFA, 0xF9DC };

            for (int i = 0; i < sol.length;) {
                int c = sol[i++];
                // no need to check c2bIndex[c >>8], we know it points
                // to the appropriate place.
                c2b[c2bIndex[c >> 8] + (c & 0xff)] = (char)sol[i++];
            }
            c2bInitialized = true;
        }
    }
}
