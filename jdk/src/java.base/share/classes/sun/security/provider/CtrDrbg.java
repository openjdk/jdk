/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.*;
import java.util.Arrays;
import java.util.Locale;

public class CtrDrbg extends AbstractDrbg {

    private static final long serialVersionUID = 9L;
    private static final int AES_LIMIT;

    static {
        try {
            AES_LIMIT = Cipher.getMaxAllowedKeyLength("AES");
        } catch (Exception e) {
            // should not happen
            throw new AssertionError("Cannot detect AES", e);
        }
    }

    private transient Cipher cipher;

    private String cipherAlg;
    private String keyAlg;

    private int ctrLen;
    private int blockLen;
    private int keyLen;
    private int seedLen;

    private transient byte[] v;
    private transient byte[] k;

    public CtrDrbg(SecureRandomParameters params) {
        mechName = "CTR_DRBG";
        configure(params);
    }

    private static int alg2strength(String algorithm) {
        switch (algorithm.toUpperCase(Locale.ROOT)) {
            case "TDEA":
            case "3KEYTDEA":
            case "3 KEY TDEA":
            case "DESEDE":
                return 112;
            case "AES-128":
                return 128;
            case "AES-192":
                return 192;
            case "AES-256":
                return 256;
            default:
                throw new IllegalArgumentException(algorithm +
                        " not supported in CTR_DBRG");
        }
    }

    @Override
    protected void chooseAlgorithmAndStrength() {
        if (requestedAlgorithm != null) {
            algorithm = requestedAlgorithm.toUpperCase();
            int supportedStrength = alg2strength(algorithm);
            if (requestedInstantiationSecurityStrength >= 0) {
                int tryStrength = getStandardStrength(
                        requestedInstantiationSecurityStrength);
                if (tryStrength > supportedStrength) {
                    throw new IllegalArgumentException(algorithm +
                            " does not support strength " +
                            requestedInstantiationSecurityStrength);
                }
                this.securityStrength = tryStrength;
            } else {
                this.securityStrength = (DEFAULT_STRENGTH > supportedStrength) ?
                        supportedStrength : DEFAULT_STRENGTH;
            }
        } else {
            int tryStrength = (requestedInstantiationSecurityStrength < 0) ?
                    DEFAULT_STRENGTH : requestedInstantiationSecurityStrength;
            tryStrength = getStandardStrength(tryStrength);
            // Default algorithm, use AES-128 if AES-256 is not available.
            // Remember to sync with "securerandom.drbg.config" in java.security
            if (tryStrength <= 128 && AES_LIMIT < 256) {
                algorithm = "AES-128";
            } else if (AES_LIMIT >= 256) {
                algorithm = "AES-256";
            } else {
                throw new IllegalArgumentException("unsupported strength " +
                        requestedInstantiationSecurityStrength);
            }
            this.securityStrength = tryStrength;
        }
        switch (algorithm.toUpperCase(Locale.ROOT)) {
            case "TDEA":
            case "3KEYTDEA":
            case "3 KEY TDEA":
            case "DESEDE":
                algorithm = "DESede";
                this.keyAlg = "DESede";
                this.cipherAlg = "DESede/ECB/NoPadding";
                this.blockLen = 64 / 8;
                this.keyLen = 168 / 8;
                break;
            case "AES-128":
            case "AES-192":
            case "AES-256":
                this.keyAlg = "AES";
                this.cipherAlg = "AES/ECB/NoPadding";
                switch (algorithm) {
                    case "AES-128":
                        this.keyLen = 128 / 8;
                        break;
                    case "AES-192":
                        this.keyLen = 192 / 8;
                        if (AES_LIMIT < 192) {
                            throw new IllegalArgumentException(algorithm +
                                " not available (because policy) in CTR_DBRG");
                        }
                        break;
                    case "AES-256":
                        this.keyLen = 256 / 8;
                        if (AES_LIMIT < 256) {
                            throw new IllegalArgumentException(algorithm +
                                " not available (because policy) in CTR_DBRG");
                        }
                        break;
                    default:
                        throw new IllegalArgumentException(algorithm +
                            " not supported in CTR_DBRG");
                }
                this.blockLen = 128 / 8;
                break;
            default:
                throw new IllegalArgumentException(algorithm +
                        " not supported in CTR_DBRG");
        }
        this.seedLen = this.blockLen + this.keyLen;
        this.ctrLen = this.blockLen;    // TODO
        if (usedf) {
            this.minLength = this.securityStrength / 8;
        } else {
            this.minLength = this.maxLength =
                    this.maxPersonalizationStringLength =
                            this.maxAdditionalInputLength = seedLen;
        }
    }

    /**
     * This call, used by the constructors, instantiates the digest.
     */
    @Override
    protected void initEngine() {
        try {
            /*
             * Use the local SUN implementation to avoid native
             * performance overhead.
             */
            cipher = Cipher.getInstance(cipherAlg, "SunJCE");
        } catch (NoSuchProviderException | NoSuchAlgorithmException
                | NoSuchPaddingException e) {
            // Fallback to any available.
            try {
                cipher = Cipher.getInstance(cipherAlg);
            } catch (NoSuchAlgorithmException | NoSuchPaddingException exc) {
                throw new InternalError(
                    "internal error: " + cipherAlg + " not available.", exc);
            }
        }
    }

    private void status() {
        if (debug != null) {
            debug.println(this, "Key = " + hex(k));
            debug.println(this, "V   = " + hex(v));
            debug.println(this, "reseed counter = " + reseedCounter);
        }
    }

    // 800-90Ar1 10.2.1.2. CTR_DRBG_Update
    private void update(byte[] input) {
        if (input.length != seedLen) {
            // Should not happen
            throw new IllegalArgumentException("input length not seedLen: "
                    + input.length);
        }
        try {

            int m = (seedLen + blockLen - 1) / blockLen;
            byte[] temp = new byte[m * blockLen];

            // Step 1. temp = Null.

            // Step 2. Loop
            for (int i = 0; i < m; i++) {
                // Step 2.1. Increment
                addOne(v, ctrLen);
                // Step 2.2. Block_Encrypt
                cipher.init(Cipher.ENCRYPT_MODE, getKey(keyAlg, k));
                // Step 2.3. Encrypt into right position, no need to cat
                cipher.doFinal(v, 0, blockLen, temp, i * blockLen);
            }

            // Step 3. Truncate
            temp = Arrays.copyOf(temp, seedLen);

            // Step 4: Add
            for (int i = 0; i < seedLen; i++) {
                temp[i] ^= input[i];
            }

            // Step 5: leftmost
            k = Arrays.copyOf(temp, keyLen);

            // Step 6: rightmost
            v = Arrays.copyOfRange(temp, seedLen - blockLen, seedLen);

            // Step 7. Return
        } catch (GeneralSecurityException e) {
            throw new InternalError(e);
        }
    }

    @Override
    protected void instantiateAlgorithm(byte[] ei) {
        if (debug != null) {
            debug.println(this, "instantiate");
        }
        byte[] more;
        if (usedf) {
            // 800-90Ar1 10.2.1.3.2 Step 1-2. cat bytes
            if (personalizationString == null) {
                more = nonce;
            } else {
                more = Arrays.copyOf(
                        nonce, nonce.length + personalizationString.length);
                System.arraycopy(personalizationString, 0, more, nonce.length,
                        personalizationString.length);
            }
        } else {
            // 800-90Ar1 10.2.1.3.1
            // Step 1-2, no need to expand personalizationString, we only XOR
            // with shorter length
            more = personalizationString;
        }
        reseedAlgorithm(ei, more);
    }

    private byte[] df(byte[] input) {
        int l = input.length;
        int n = seedLen;
        int slen = 4 + 4 + l + 1;
        byte[] s = new byte[(slen + blockLen - 1) / blockLen * blockLen];
        s[0] = (byte)(l >> 24);
        s[1] = (byte)(l >> 16);
        s[2] = (byte)(l >> 8);
        s[3] = (byte)(l);
        s[4] = (byte)(n >> 24);
        s[5] = (byte)(n >> 16);
        s[6] = (byte)(n >> 8);
        s[7] = (byte)(n);
        System.arraycopy(input, 0, s, 8, l);
        s[8+l] = (byte)0x80;

        byte[] k = new byte[keyLen];
        for (int i = 0; i < k.length; i++) {
            k[i] = (byte)i;
        }

        byte[] temp = new byte[seedLen];

        for (int i = 0; i * blockLen < temp.length; i++) {
            byte[] iv = new byte[blockLen + s.length];
            iv[0] = (byte)(i >> 24);
            iv[1] = (byte)(i >> 16);
            iv[2] = (byte)(i >> 8);
            iv[3] = (byte)(i);
            System.arraycopy(s, 0, iv, blockLen, s.length);
            int tailLen = temp.length - blockLen*i;
            if (tailLen > blockLen) {
                tailLen = blockLen;
            }
            System.arraycopy(bcc(k, iv), 0, temp, blockLen*i, tailLen);
        }

        k = Arrays.copyOf(temp, keyLen);
        byte[] x = Arrays.copyOfRange(temp, keyLen, temp.length);

        for (int i = 0; i * blockLen < seedLen; i++) {
            try {
                cipher.init(Cipher.ENCRYPT_MODE, getKey(keyAlg, k));
                int tailLen = temp.length - blockLen*i;
                if (tailLen > blockLen) {
                    tailLen = blockLen;
                }
                x = cipher.doFinal(x);
                System.arraycopy(x, 0, temp, blockLen * i, tailLen);
            } catch (GeneralSecurityException e) {
                throw new InternalError(e);
            }
        }
        return temp;
    }

    private byte[] bcc(byte[] k, byte[] data) {
        byte[] chain = new byte[blockLen];
        int n = data.length / blockLen;
        for (int i = 0; i < n; i++) {
            byte[] inputBlock = Arrays.copyOfRange(
                    data, i * blockLen, i * blockLen + blockLen);
            for (int j = 0; j < blockLen; j++) {
                inputBlock[j] ^= chain[j];
            }
            try {
                cipher.init(Cipher.ENCRYPT_MODE, getKey(keyAlg, k));
                chain = cipher.doFinal(inputBlock);
            } catch (GeneralSecurityException e) {
                throw new InternalError(e);
            }
        }
        return chain;
    }

    @Override
    protected synchronized void reseedAlgorithm(
            byte[] ei,
            byte[] additionalInput) {
        if (usedf) {
            // 800-90Ar1 10.2.1.3.2 Instantiate.
            // 800-90Ar1 10.2.1.4.2 Reseed.

            // Step 1: cat bytes
            if (additionalInput != null) {
                byte[] temp = Arrays.copyOf(
                        ei, ei.length + additionalInput.length);
                System.arraycopy(additionalInput, 0, temp, ei.length,
                        additionalInput.length);
                ei = temp;
            }
            // Step 2. df (seed_material, seedlen).
            ei = df(ei);
        } else {
            // 800-90Ar1 10.2.1.3.1 Instantiate
            // 800-90Ar1 10.2.1.4.1 Reseed
            // Step 1-2. Needless
            // Step 3. seed_material = entropy_input XOR more
            if (additionalInput != null) {
                // additionalInput.length <= seedLen
                for (int i = 0; i < additionalInput.length; i++) {
                    ei[i] ^= additionalInput[i];
                }
            }
        }

        if (v == null) {
            // 800-90Ar1 10.2.1.3.2 Instantiate. Step 3-4
            // 800-90Ar1 10.2.1.3.1 Instantiate. Step 4-5
            k = new byte[keyLen];
            v = new byte[blockLen];
        }
        //status();

        // 800-90Ar1 10.2.1.3.1 Instantiate. Step 6
        // 800-90Ar1 10.2.1.3.2 Instantiate. Step 5
        // 800-90Ar1 10.2.1.4.1 Reseed. Step 4
        // 800-90Ar1 10.2.1.4.2 Reseed. Step 3
        update(ei);
        // 800-90Ar1 10.2.1.3.1 Instantiate. Step 7
        // 800-90Ar1 10.2.1.3.2 Instantiate. Step 6
        // 800-90Ar1 10.2.1.4.1 Reseed. Step 5
        // 800-90Ar1 10.2.1.4.2 Reseed. Step 4
        reseedCounter = 1;
        //status();

        // Whatever step. Return
    }

    /**
     * Add one to data, only touch the last len bytes.
     */
    private static void addOne(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            data[data.length - 1 - i]++;
            if (data[data.length - 1 - i] != 0) {
                break;
            }
        }
    }

    @Override
    public synchronized void generateAlgorithm(
            byte[] result, byte[] additionalInput) {

        if (debug != null) {
            debug.println(this, "generateAlgorithm");
        }

        // 800-90Ar1 10.2.1.5.1 Generate
        // 800-90Ar1 10.2.1.5.2 Generate

        // Step 1: Check reseed_counter. Will not fail. Already checked in
        // AbstractDrbg#engineNextBytes.

        if (additionalInput != null) {
            if (usedf) {
                // 10.2.1.5.2 Step 2.1
                additionalInput = df(additionalInput);
            } else {
                // 10.2.1.5.1 Step 2.1-2.2
                additionalInput = Arrays.copyOf(additionalInput, seedLen);
            }
            // 10.2.1.5.1 Step 2.3
            // 10.2.1.5.2 Step 2.2
            update(additionalInput);
        } else {
            // 10.2.1.5.1 Step 2 Else
            // 10.2.1.5.2 Step 2 Else
            additionalInput = new byte[seedLen];
        }

        // Step 3. temp = Null
        int pos = 0;

        // Step 4. Loop
        while (pos < result.length) {
            int tailLen = result.length - pos;
            // Step 4.1. Increment
            addOne(v, ctrLen);
            try {
                // Step 4.2. Encrypt
                cipher.init(Cipher.ENCRYPT_MODE, getKey(keyAlg, k));
                byte[] out = cipher.doFinal(v);

                // Step 4.3 and 5. Cat bytes and leftmost
                System.arraycopy(out, 0, result, pos,
                        (tailLen > blockLen) ? blockLen : tailLen);
            } catch (GeneralSecurityException e) {
                throw new InternalError(e);
            }
            pos += blockLen;
        }

        // Step 6. Update
        update(additionalInput);

        // Step 7. reseed_counter++
        reseedCounter++;

        //status();

        // Step 8. Return
    }

    private static void des7to8(
            byte[] key56, int off56, byte[] key64, int off64) {
        key64[off64 + 0] = (byte)
                (key56[off56 + 0] & 0xFE); // << 0
        key64[off64 + 1] = (byte)
                ((key56[off56 + 0] << 7) | ((key56[off56 + 1] & 0xFF) >>> 1));
        key64[off64 + 2] = (byte)
                ((key56[off56 + 1] << 6) | ((key56[off56 + 2] & 0xFF) >>> 2));
        key64[off64 + 3] = (byte)
                ((key56[off56 + 2] << 5) | ((key56[off56 + 3] & 0xFF) >>> 3));
        key64[off64 + 4] = (byte)
                ((key56[off56 + 3] << 4) | ((key56[off56 + 4] & 0xFF) >>> 4));
        key64[off64 + 5] = (byte)
                ((key56[off56 + 4] << 3) | ((key56[off56 + 5] & 0xFF) >>> 5));
        key64[off64 + 6] = (byte)
                ((key56[off56 + 5] << 2) | ((key56[off56 + 6] & 0xFF) >>> 6));
        key64[off64 + 7] = (byte)
                (key56[off56 + 6] << 1);

        for (int i = 0; i < 8; i++) {
            // if even # bits, make uneven, XOR with 1 (uneven & 1)
            // for uneven # bits, make even, XOR with 0 (even & 1)
            key64[off64 + i] ^= Integer.bitCount(key64[off64 + i] ^ 1) & 1;
        }
    }

    private static SecretKey getKey(String keyAlg, byte[] k) {
        if (keyAlg.equals("DESede")) {
            byte[] k2 = new byte[24];
            des7to8(k, 0, k2, 0);
            des7to8(k, 7, k2, 8);
            des7to8(k, 14, k2, 16);
            k = k2;
        }
        return new SecretKeySpec(k, keyAlg);
    }

    private void readObject(java.io.ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject ();
        initEngine();
    }

    @Override
    public String toString() {
        return super.toString() + "/"
                + (usedf ? "use_df" : "no_df");
    }
}
