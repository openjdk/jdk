/*
 * Copyright 1996-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.provider;

import static sun.security.provider.ByteArrayAccess.*;

/**
 * This class implements the Secure Hash Algorithm (SHA) developed by
 * the National Institute of Standards and Technology along with the
 * National Security Agency.  This is the updated version of SHA
 * fip-180 as superseded by fip-180-1.
 *
 * <p>It implement JavaSecurity MessageDigest, and can be used by in
 * the Java Security framework, as a pluggable implementation, as a
 * filter for the digest stream classes.
 *
 * @author      Roger Riggs
 * @author      Benjamin Renaud
 * @author      Andreas Sterbenz
 */
public final class SHA extends DigestBase {

    // Buffer of int's and count of characters accumulated
    // 64 bytes are included in each hash block so the low order
    // bits of count are used to know how to pack the bytes into ints
    // and to know when to compute the block and start the next one.
    private final int[] W;

    // state of this
    private final int[] state;

    /**
     * Creates a new SHA object.
     */
    public SHA() {
        super("SHA-1", 20, 64);
        state = new int[5];
        W = new int[80];
        implReset();
    }

    /**
     * Creates a SHA object.with state (for cloning) */
    private SHA(SHA base) {
        super(base);
        this.state = base.state.clone();
        this.W = new int[80];
    }

    /*
     * Clones this object.
     */
    public Object clone() {
        return new SHA(this);
    }

    /**
     * Resets the buffers and hash value to start a new hash.
     */
    void implReset() {
        state[0] = 0x67452301;
        state[1] = 0xefcdab89;
        state[2] = 0x98badcfe;
        state[3] = 0x10325476;
        state[4] = 0xc3d2e1f0;
    }

    /**
     * Computes the final hash and copies the 20 bytes to the output array.
     */
    void implDigest(byte[] out, int ofs) {
        long bitsProcessed = bytesProcessed << 3;

        int index = (int)bytesProcessed & 0x3f;
        int padLen = (index < 56) ? (56 - index) : (120 - index);
        engineUpdate(padding, 0, padLen);

        i2bBig4((int)(bitsProcessed >>> 32), buffer, 56);
        i2bBig4((int)bitsProcessed, buffer, 60);
        implCompress(buffer, 0);

        i2bBig(state, 0, out, ofs, 20);
    }

    // Constants for each round
    private final static int round1_kt = 0x5a827999;
    private final static int round2_kt = 0x6ed9eba1;
    private final static int round3_kt = 0x8f1bbcdc;
    private final static int round4_kt = 0xca62c1d6;

    /**
     * Compute a the hash for the current block.
     *
     * This is in the same vein as Peter Gutmann's algorithm listed in
     * the back of Applied Cryptography, Compact implementation of
     * "old" NIST Secure Hash Algorithm.
     */
    void implCompress(byte[] buf, int ofs) {
        b2iBig64(buf, ofs, W);

        // The first 16 ints have the byte stream, compute the rest of
        // the buffer
        for (int t = 16; t <= 79; t++) {
            int temp = W[t-3] ^ W[t-8] ^ W[t-14] ^ W[t-16];
            W[t] = (temp << 1) | (temp >>> 31);
        }

        int a = state[0];
        int b = state[1];
        int c = state[2];
        int d = state[3];
        int e = state[4];

        // Round 1
        for (int i = 0; i < 20; i++) {
            int temp = ((a<<5) | (a>>>(32-5))) +
                ((b&c)|((~b)&d))+ e + W[i] + round1_kt;
            e = d;
            d = c;
            c = ((b<<30) | (b>>>(32-30)));
            b = a;
            a = temp;
        }

        // Round 2
        for (int i = 20; i < 40; i++) {
            int temp = ((a<<5) | (a>>>(32-5))) +
                (b ^ c ^ d) + e + W[i] + round2_kt;
            e = d;
            d = c;
            c = ((b<<30) | (b>>>(32-30)));
            b = a;
            a = temp;
        }

        // Round 3
        for (int i = 40; i < 60; i++) {
            int temp = ((a<<5) | (a>>>(32-5))) +
                ((b&c)|(b&d)|(c&d)) + e + W[i] + round3_kt;
            e = d;
            d = c;
            c = ((b<<30) | (b>>>(32-30)));
            b = a;
            a = temp;
        }

        // Round 4
        for (int i = 60; i < 80; i++) {
            int temp = ((a<<5) | (a>>>(32-5))) +
                (b ^ c ^ d) + e + W[i] + round4_kt;
            e = d;
            d = c;
            c = ((b<<30) | (b>>>(32-30)));
            b = a;
            a = temp;
        }
        state[0] += a;
        state[1] += b;
        state[2] += c;
        state[3] += d;
        state[4] += e;
    }

}
