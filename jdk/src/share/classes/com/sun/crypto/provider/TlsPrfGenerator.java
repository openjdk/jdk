/*
 * Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import sun.security.internal.spec.TlsPrfParameterSpec;

/**
 * KeyGenerator implementation for the TLS PRF function.
 *
 * @author  Andreas Sterbenz
 * @since   1.6
 */
public final class TlsPrfGenerator extends KeyGeneratorSpi {

    // magic constants and utility functions, also used by other files
    // in this package

    private final static byte[] B0 = new byte[0];

    final static byte[] LABEL_MASTER_SECRET = // "master secret"
        { 109, 97, 115, 116, 101, 114, 32, 115, 101, 99, 114, 101, 116 };

    final static byte[] LABEL_KEY_EXPANSION = // "key expansion"
        { 107, 101, 121, 32, 101, 120, 112, 97, 110, 115, 105, 111, 110 };

    final static byte[] LABEL_CLIENT_WRITE_KEY = // "client write key"
        { 99, 108, 105, 101, 110, 116, 32, 119, 114, 105, 116, 101, 32,
          107, 101, 121 };

    final static byte[] LABEL_SERVER_WRITE_KEY = // "server write key"
        { 115, 101, 114, 118, 101, 114, 32, 119, 114, 105, 116, 101, 32,
          107, 101, 121 };

    final static byte[] LABEL_IV_BLOCK = // "IV block"
        { 73, 86, 32, 98, 108, 111, 99, 107 };

    /*
     * TLS HMAC "inner" and "outer" padding.  This isn't a function
     * of the digest algorithm.
     */
    private static final byte[] HMAC_ipad = genPad((byte)0x36, 64);
    private static final byte[] HMAC_opad = genPad((byte)0x5c, 64);

    // SSL3 magic mix constants ("A", "BB", "CCC", ...)
    final static byte[][] SSL3_CONST = genConst();

    static byte[] genPad(byte b, int count) {
        byte[] padding = new byte[count];
        Arrays.fill(padding, b);
        return padding;
    }

    static byte[] concat(byte[] b1, byte[] b2) {
        int n1 = b1.length;
        int n2 = b2.length;
        byte[] b = new byte[n1 + n2];
        System.arraycopy(b1, 0, b, 0, n1);
        System.arraycopy(b2, 0, b, n1, n2);
        return b;
    }

    private static byte[][] genConst() {
        int n = 10;
        byte[][] arr = new byte[n][];
        for (int i = 0; i < n; i++) {
            byte[] b = new byte[i + 1];
            Arrays.fill(b, (byte)('A' + i));
            arr[i] = b;
        }
        return arr;
    }

    // PRF implementation

    private final static String MSG = "TlsPrfGenerator must be "
        + "initialized using a TlsPrfParameterSpec";

    private TlsPrfParameterSpec spec;

    public TlsPrfGenerator() {
    }

    protected void engineInit(SecureRandom random) {
        throw new InvalidParameterException(MSG);
    }

    protected void engineInit(AlgorithmParameterSpec params,
            SecureRandom random) throws InvalidAlgorithmParameterException {
        if (params instanceof TlsPrfParameterSpec == false) {
            throw new InvalidAlgorithmParameterException(MSG);
        }
        this.spec = (TlsPrfParameterSpec)params;
        SecretKey key = spec.getSecret();
        if ((key != null) && ("RAW".equals(key.getFormat()) == false)) {
            throw new InvalidAlgorithmParameterException
                ("Key encoding format must be RAW");
        }
    }

    protected void engineInit(int keysize, SecureRandom random) {
        throw new InvalidParameterException(MSG);
    }

    protected SecretKey engineGenerateKey() {
        if (spec == null) {
            throw new IllegalStateException
                ("TlsPrfGenerator must be initialized");
        }
        SecretKey key = spec.getSecret();
        byte[] secret = (key == null) ? null : key.getEncoded();
        try {
            byte[] labelBytes = spec.getLabel().getBytes("UTF8");
            int n = spec.getOutputLength();
            byte[] prfBytes = doPRF(secret, labelBytes, spec.getSeed(), n);
            return new SecretKeySpec(prfBytes, "TlsPrf");
        } catch (GeneralSecurityException e) {
            throw new ProviderException("Could not generate PRF", e);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new ProviderException("Could not generate PRF", e);
        }
    }

    static final byte[] doPRF(byte[] secret, byte[] labelBytes, byte[] seed,
            int outputLength) throws NoSuchAlgorithmException, DigestException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        MessageDigest sha = MessageDigest.getInstance("SHA1");
        return doPRF(secret, labelBytes, seed, outputLength, md5, sha);
    }

    static final byte[] doPRF(byte[] secret, byte[] labelBytes, byte[] seed,
            int outputLength, MessageDigest md5, MessageDigest sha)
            throws DigestException {
        /*
         * Split the secret into two halves S1 and S2 of same length.
         * S1 is taken from the first half of the secret, S2 from the
         * second half.
         * Their length is created by rounding up the length of the
         * overall secret divided by two; thus, if the original secret
         * is an odd number of bytes long, the last byte of S1 will be
         * the same as the first byte of S2.
         *
         * Note: Instead of creating S1 and S2, we determine the offset into
         * the overall secret where S2 starts.
         */

        if (secret == null) {
            secret = B0;
        }
        int off = secret.length >> 1;
        int seclen = off + (secret.length & 1);

        byte[] output = new byte[outputLength];

        // P_MD5(S1, label + seed)
        expand(md5, 16, secret, 0, seclen, labelBytes, seed, output);

        // P_SHA-1(S2, label + seed)
        expand(sha, 20, secret, off, seclen, labelBytes, seed, output);

        return output;
    }

    /*
     * @param digest the MessageDigest to produce the HMAC
     * @param hmacSize the HMAC size
     * @param secret the secret
     * @param secOff the offset into the secret
     * @param secLen the secret length
     * @param label the label
     * @param seed the seed
     * @param output the output array
     */
    private static final void expand(MessageDigest digest, int hmacSize,
            byte[] secret, int secOff, int secLen, byte[] label, byte[] seed,
            byte[] output) throws DigestException {
        /*
         * modify the padding used, by XORing the key into our copy of that
         * padding.  That's to avoid doing that for each HMAC computation.
         */
        byte[] pad1 = HMAC_ipad.clone();
        byte[] pad2 = HMAC_opad.clone();

        for (int i = 0; i < secLen; i++) {
            pad1[i] ^= secret[i + secOff];
            pad2[i] ^= secret[i + secOff];
        }

        byte[] tmp = new byte[hmacSize];
        byte[] aBytes = null;

        /*
         * compute:
         *
         *     P_hash(secret, seed) = HMAC_hash(secret, A(1) + seed) +
         *                            HMAC_hash(secret, A(2) + seed) +
         *                            HMAC_hash(secret, A(3) + seed) + ...
         * A() is defined as:
         *
         *     A(0) = seed
         *     A(i) = HMAC_hash(secret, A(i-1))
         */
        int remaining = output.length;
        int ofs = 0;
        while (remaining > 0) {
            /*
             * compute A() ...
             */
            // inner digest
            digest.update(pad1);
            if (aBytes == null) {
                digest.update(label);
                digest.update(seed);
            } else {
                digest.update(aBytes);
            }
            digest.digest(tmp, 0, hmacSize);

            // outer digest
            digest.update(pad2);
            digest.update(tmp);
            if (aBytes == null) {
                aBytes = new byte[hmacSize];
            }
            digest.digest(aBytes, 0, hmacSize);

            /*
             * compute HMAC_hash() ...
             */
            // inner digest
            digest.update(pad1);
            digest.update(aBytes);
            digest.update(label);
            digest.update(seed);
            digest.digest(tmp, 0, hmacSize);

            // outer digest
            digest.update(pad2);
            digest.update(tmp);
            digest.digest(tmp, 0, hmacSize);

            int k = Math.min(hmacSize, remaining);
            for (int i = 0; i < k; i++) {
                output[ofs++] ^= tmp[i];
            }
            remaining -= k;
        }

    }

}
