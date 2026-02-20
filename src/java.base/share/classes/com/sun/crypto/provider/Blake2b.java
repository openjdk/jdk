/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.util.Arrays;
import java.util.Objects;
import java.io.ByteArrayInputStream;
import static sun.security.provider.ByteArrayAccess.*;

/**
 * This class implements the Secure Hash Algorithm Blake2b as specified
 * in <a href="https://datatracker.ietf.org/doc/html/rfc7693">RFC 7693</a>.
 *
 * <p>It is an internal implementation of Blake2b for supporting Argon2.
 *
 * @since 27
 */
public final class Blake2b {

    private static final long[] IV = {
       0x6A09E667F3BCC908L, 0xBB67AE8584CAA73BL,
       0x3C6EF372FE94F82BL, 0xA54FF53A5F1D36F1L,
       0x510E527FADE682D1L, 0x9B05688C2B3E6C1FL,
       0x1F83D9ABFB41BD6BL, 0x5BE0CD19137E2179L
    };

    private static final byte[] NULL_KEY = new byte[0];

    // message schedule, for BLAKE2b, SIGMA[10..11] = SIGMA[0..1].
    private static final int[][] SIGMA = {
        { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 },
        { 14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3 },
        { 11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4 },
        { 7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8 },
        { 9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13 },
        { 2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9 },
        { 12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11 },
        { 13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10 },
        { 6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5 },
        { 10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0 },
        { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 },
        { 14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3 }
    };

    private final byte[] key; // optional

    // internal buffers and counter, etc.
    private final byte[] b; // message block
    private final long[] h; // state vector
    private final long[] t; // offset counter, 0:low word, 1:high word
    private final int outLen;
    private int c;

    /**
     * Creates a new Blake2b object with NULL_KEY
     */
    public Blake2b(int outLen) {
        this(outLen, NULL_KEY);
    }

    /**
     * Creates a new Blake2b object with the specified key bytes
     */
    public Blake2b(int outLen, byte[] key) {
        if (outLen < 1 || outLen > 64) {
            throw new IllegalArgumentException("outLen must be between " +
                    "1 and 64 bytes: " + outLen);
        }

        if (key != null && key.length > 64) {
            throw new IllegalArgumentException("key length must be no " +
                    "longer than 64 bytes: " + key.length);
        }

        this.key = ((key == null || key.length == 0) ? NULL_KEY : key.clone());
        this.b = new byte[128];
        this.h = IV.clone();
        if (this.key == NULL_KEY) {
            this.h[0] ^= (0x01010000L ^ outLen);
            this.c = 0;
        } else {
            // need to process the key at the next update/doFinal call
            this.h[0] ^= (0x01010000L ^ (this.key.length << 8) ^ outLen);
            System.arraycopy(this.key, 0, this.b, 0, this.key.length);
            this.c = 128;
        }
        this.t = new long[2];
        this.outLen = outLen;
    }

    public synchronized void update(byte[] in) {
        Objects.requireNonNull(in);
        process(in, 0, in.length);
    }

    public synchronized void update(byte[] in, int inOfs, int inLen) {
        Objects.requireNonNull(in);
        Objects.checkFromToIndex(inOfs, inOfs + inLen, in.length);
        process(in, inOfs, inLen);
    }

    private void process(byte[] in, int inOfs, int inLen) {
        if (inLen == 0) {
            return;
        }
        ByteArrayInputStream bais =
                new ByteArrayInputStream(in, inOfs, inLen);
        while (bais.available() > 0) {
            if (c == 128) {           // buffer full
                processBlock(false);  // compress (not last)
                c = 0;
            }
            int n = bais.read(b, c, b.length - c);
            c += n;
        }
    }

    public synchronized byte[] doFinal() {
        byte[] out = new byte[outLen];
        doFinal(out, 0);
        return out;
    }

    public synchronized int doFinal(byte[] out, int outOfs) {
        Objects.checkFromToIndex(outOfs, outOfs + outLen, out.length);
        try {
            if (c < 128) { // fill up with zeros
                Arrays.fill(b, c, 128, (byte)0);
            }
            processBlock(true); // final block

            // little endian convert and store
            int lastChunk = outLen % 8;
            l2bLittle(h, 0, out, outOfs, outLen - lastChunk);
            // special handling if outLen isn't multiple of 8 bytes
            if (lastChunk > 0) {
                int allButLast = outLen - lastChunk;
                // use b as temp output buffer
                l2bLittle8(h[allButLast >>> 3], b, 0);
                System.arraycopy(b, 0, out, outOfs + allButLast, lastChunk);
            }
            return outLen;
        } finally {
            // reset state for more update()/doFinal() calls
            reset();
        }
    }

    // process 128-byte message block 'b'
    private void processBlock(boolean isLast) {
        int processed = isLast? c : 128;
        t[0] += processed;         // add counter
        if (t[0] < processed) {    // carry overflow, inc high word
            t[1]++;
        }
        compressF(b, h, t, isLast);
    }

    // reset fields to post-constructor
    private void reset() {
        Arrays.fill(b, (byte) 0);
        System.arraycopy(IV, 0, h, 0, IV.length);
        Arrays.fill(t, (long) 0);
        if (key != NULL_KEY) {
            // process the key at the next update/doFinal call
            h[0] ^= (0x01010000L ^ (key.length << 8) ^ outLen);
            System.arraycopy(key, 0, b, 0, key.length);
            c = 128;
        } else {
            h[0] ^= (0x01010000L ^ outLen);
            c = 0;
        }
    }

    // RFC 7693 sec 3.1 Mixing function G:
    // mixes x and y, storing the outputs into v[a], v[b], v[c], and v[d]
    private static void mixG(long[] v, int a, int b, int c, int d,
           long x, long y) {
       // assert a, b, c, d < 16
       Objects.checkIndex(a, v.length);
       Objects.checkIndex(b, v.length);
       Objects.checkIndex(c, v.length);
       Objects.checkIndex(d, v.length);

       v[a] = v[a] + v[b] + x;
       v[d] = Long.rotateRight(v[d] ^ v[a], 32);
       v[c] = v[c] + v[d];
       v[b] = Long.rotateRight(v[b] ^ v[c], 24);
       v[a] = v[a] + v[b] + y;
       v[d] = Long.rotateRight(v[d] ^ v[a], 16);
       v[c] = v[c] + v[d];
       v[b] = Long.rotateRight(v[b] ^ v[c], 63);
    }

    // RFC 7693 sec 3.2 Compress function F:
    // processes the message block 'b', state 'h', and offset counter 't' and
    // final block indicator flag 'last' into new state and stores into 'h'
    private static void compressF(byte[] b, long[] h, long[] t, boolean last) {
        // prep localV
        long[] localV = Arrays.copyOf(h, 16);
        System.arraycopy(IV, 0, localV, h.length, 8);
        localV[12] ^= t[0];
        localV[13] ^= t[1];
        if (last) {
            localV[14] = ~localV[14];
        }

        // convert the message block from byte[] to long[] in Little Endian
        long[] m = new long[16];
        b2lLittle(b, 0, m, 0, 128);

        for (int i = 0; i < 12; i++) {     // twelve rounds
            mixG(localV, 0, 4,  8, 12, m[SIGMA[i][ 0]], m[SIGMA[i][ 1]]);
            mixG(localV, 1, 5,  9, 13, m[SIGMA[i][ 2]], m[SIGMA[i][ 3]]);
            mixG(localV, 2, 6, 10, 14, m[SIGMA[i][ 4]], m[SIGMA[i][ 5]]);
            mixG(localV, 3, 7, 11, 15, m[SIGMA[i][ 6]], m[SIGMA[i][ 7]]);
            mixG(localV, 0, 5, 10, 15, m[SIGMA[i][ 8]], m[SIGMA[i][ 9]]);
            mixG(localV, 1, 6, 11, 12, m[SIGMA[i][10]], m[SIGMA[i][11]]);
            mixG(localV, 2, 7,  8, 13, m[SIGMA[i][12]], m[SIGMA[i][13]]);
            mixG(localV, 3, 4,  9, 14, m[SIGMA[i][14]], m[SIGMA[i][15]]);
        }

        // store result into 'h'
        for (int i = 0; i < 8; i++) {
            h[i] ^= (localV[i] ^ localV[i + 8]);
        }
    }
}
