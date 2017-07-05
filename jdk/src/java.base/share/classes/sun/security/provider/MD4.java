/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.security.*;

import static sun.security.provider.ByteArrayAccess.*;

/**
 * The MD4 class is used to compute an MD4 message digest over a given
 * buffer of bytes. It is an implementation of the RSA Data Security Inc
 * MD4 algorithim as described in internet RFC 1320.
 *
 * <p>The MD4 algorithm is very weak and should not be used unless it is
 * unavoidable. Therefore, it is not registered in our standard providers. To
 * obtain an implementation, call the static getInstance() method in this
 * class.
 *
 * @author      Andreas Sterbenz
 */
public final class MD4 extends DigestBase {

    // state of this object
    private int[] state;
    // temporary buffer, used by implCompress()
    private int[] x;

    // rotation constants
    private static final int S11 = 3;
    private static final int S12 = 7;
    private static final int S13 = 11;
    private static final int S14 = 19;
    private static final int S21 = 3;
    private static final int S22 = 5;
    private static final int S23 = 9;
    private static final int S24 = 13;
    private static final int S31 = 3;
    private static final int S32 = 9;
    private static final int S33 = 11;
    private static final int S34 = 15;

    private static final Provider md4Provider;

    static {
        md4Provider = new Provider("MD4Provider", 9.0d, "MD4 MessageDigest") {
            private static final long serialVersionUID = -8850464997518327965L;
        };
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                md4Provider.put("MessageDigest.MD4", "sun.security.provider.MD4");
                return null;
            }
        });
    }

    public static MessageDigest getInstance() {
        try {
            return MessageDigest.getInstance("MD4", md4Provider);
        } catch (NoSuchAlgorithmException e) {
            // should never occur
            throw new ProviderException(e);
        }
    }

    // Standard constructor, creates a new MD4 instance.
    public MD4() {
        super("MD4", 16, 64);
        state = new int[4];
        x = new int[16];
        implReset();
    }

    // clone this object
    public Object clone() throws CloneNotSupportedException {
        MD4 copy = (MD4) super.clone();
        copy.state = copy.state.clone();
        copy.x = new int[16];
        return copy;
    }

    /**
     * Reset the state of this object.
     */
    void implReset() {
        // Load magic initialization constants.
        state[0] = 0x67452301;
        state[1] = 0xefcdab89;
        state[2] = 0x98badcfe;
        state[3] = 0x10325476;
    }

    /**
     * Perform the final computations, any buffered bytes are added
     * to the digest, the count is added to the digest, and the resulting
     * digest is stored.
     */
    void implDigest(byte[] out, int ofs) {
        long bitsProcessed = bytesProcessed << 3;

        int index = (int)bytesProcessed & 0x3f;
        int padLen = (index < 56) ? (56 - index) : (120 - index);
        engineUpdate(padding, 0, padLen);

        i2bLittle4((int)bitsProcessed, buffer, 56);
        i2bLittle4((int)(bitsProcessed >>> 32), buffer, 60);
        implCompress(buffer, 0);

        i2bLittle(state, 0, out, ofs, 16);
    }

    private static int FF(int a, int b, int c, int d, int x, int s) {
        a += ((b & c) | ((~b) & d)) + x;
        return ((a << s) | (a >>> (32 - s)));
    }

    private static int GG(int a, int b, int c, int d, int x, int s) {
        a += ((b & c) | (b & d) | (c & d)) + x + 0x5a827999;
        return ((a << s) | (a >>> (32 - s)));
    }

    private static int HH(int a, int b, int c, int d, int x, int s) {
        a += ((b ^ c) ^ d) + x + 0x6ed9eba1;
        return ((a << s) | (a >>> (32 - s)));
    }

    /**
     * This is where the functions come together as the generic MD4
     * transformation operation. It consumes sixteen
     * bytes from the buffer, beginning at the specified offset.
     */
    void implCompress(byte[] buf, int ofs) {
        b2iLittle64(buf, ofs, x);

        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];

        /* Round 1 */
        a = FF (a, b, c, d, x[ 0], S11); /* 1 */
        d = FF (d, a, b, c, x[ 1], S12); /* 2 */
        c = FF (c, d, a, b, x[ 2], S13); /* 3 */
        b = FF (b, c, d, a, x[ 3], S14); /* 4 */
        a = FF (a, b, c, d, x[ 4], S11); /* 5 */
        d = FF (d, a, b, c, x[ 5], S12); /* 6 */
        c = FF (c, d, a, b, x[ 6], S13); /* 7 */
        b = FF (b, c, d, a, x[ 7], S14); /* 8 */
        a = FF (a, b, c, d, x[ 8], S11); /* 9 */
        d = FF (d, a, b, c, x[ 9], S12); /* 10 */
        c = FF (c, d, a, b, x[10], S13); /* 11 */
        b = FF (b, c, d, a, x[11], S14); /* 12 */
        a = FF (a, b, c, d, x[12], S11); /* 13 */
        d = FF (d, a, b, c, x[13], S12); /* 14 */
        c = FF (c, d, a, b, x[14], S13); /* 15 */
        b = FF (b, c, d, a, x[15], S14); /* 16 */

        /* Round 2 */
        a = GG (a, b, c, d, x[ 0], S21); /* 17 */
        d = GG (d, a, b, c, x[ 4], S22); /* 18 */
        c = GG (c, d, a, b, x[ 8], S23); /* 19 */
        b = GG (b, c, d, a, x[12], S24); /* 20 */
        a = GG (a, b, c, d, x[ 1], S21); /* 21 */
        d = GG (d, a, b, c, x[ 5], S22); /* 22 */
        c = GG (c, d, a, b, x[ 9], S23); /* 23 */
        b = GG (b, c, d, a, x[13], S24); /* 24 */
        a = GG (a, b, c, d, x[ 2], S21); /* 25 */
        d = GG (d, a, b, c, x[ 6], S22); /* 26 */
        c = GG (c, d, a, b, x[10], S23); /* 27 */
        b = GG (b, c, d, a, x[14], S24); /* 28 */
        a = GG (a, b, c, d, x[ 3], S21); /* 29 */
        d = GG (d, a, b, c, x[ 7], S22); /* 30 */
        c = GG (c, d, a, b, x[11], S23); /* 31 */
        b = GG (b, c, d, a, x[15], S24); /* 32 */

        /* Round 3 */
        a = HH (a, b, c, d, x[ 0], S31); /* 33 */
        d = HH (d, a, b, c, x[ 8], S32); /* 34 */
        c = HH (c, d, a, b, x[ 4], S33); /* 35 */
        b = HH (b, c, d, a, x[12], S34); /* 36 */
        a = HH (a, b, c, d, x[ 2], S31); /* 37 */
        d = HH (d, a, b, c, x[10], S32); /* 38 */
        c = HH (c, d, a, b, x[ 6], S33); /* 39 */
        b = HH (b, c, d, a, x[14], S34); /* 40 */
        a = HH (a, b, c, d, x[ 1], S31); /* 41 */
        d = HH (d, a, b, c, x[ 9], S32); /* 42 */
        c = HH (c, d, a, b, x[ 5], S33); /* 43 */
        b = HH (b, c, d, a, x[13], S34); /* 44 */
        a = HH (a, b, c, d, x[ 3], S31); /* 45 */
        d = HH (d, a, b, c, x[11], S32); /* 46 */
        c = HH (c, d, a, b, x[ 7], S33); /* 47 */
        b = HH (b, c, d, a, x[15], S34); /* 48 */

        state[0] += a;
        state[1] += b;
        state[2] += c;
        state[3] += d;
    }

}
