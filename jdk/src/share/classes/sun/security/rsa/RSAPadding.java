/*
 * Copyright (c) 2003, 2013 Oracle and/or its affiliates. All rights reserved.
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

package sun.security.rsa;

import java.math.BigInteger;
import java.util.*;

import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

import javax.crypto.BadPaddingException;
import javax.crypto.spec.PSource;
import javax.crypto.spec.OAEPParameterSpec;

import sun.security.jca.JCAUtil;

/**
 * RSA padding and unpadding.
 *
 * Format of PKCS#1 v1.5 padding is:
 *   0x00 | BT | PS...PS | 0x00 | data...data
 * where BT is the blocktype (1 or 2). The length of the entire string
 * must be the same as the size of the modulus (i.e. 128 byte for a 1024 bit
 * key). Per spec, the padding string must be at least 8 bytes long. That
 * leaves up to (length of key in bytes) - 11 bytes for the data.
 *
 * OAEP padding is a bit more complicated and has a number of options.
 * We support:
 *   . arbitrary hash functions ('Hash' in the specification), MessageDigest
 *     implementation must be available
 *   . MGF1 as the mask generation function
 *   . the empty string as the default value for label L and whatever
 *     specified in javax.crypto.spec.OAEPParameterSpec
 *
 * Note: RSA keys should be at least 512 bits long
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class RSAPadding {

    // NOTE: the constants below are embedded in the JCE RSACipher class
    // file. Do not change without coordinating the update

    // PKCS#1 v1.5 padding, blocktype 1 (signing)
    public final static int PAD_BLOCKTYPE_1    = 1;
    // PKCS#1 v1.5 padding, blocktype 2 (encryption)
    public final static int PAD_BLOCKTYPE_2    = 2;
    // nopadding. Does not do anything, but allows simpler RSACipher code
    public final static int PAD_NONE           = 3;
    // PKCS#1 v2.1 OAEP padding
    public final static int PAD_OAEP_MGF1 = 4;

    // type, one of PAD_*
    private final int type;

    // size of the padded block (i.e. size of the modulus)
    private final int paddedSize;

    // PRNG used to generate padding bytes (PAD_BLOCKTYPE_2, PAD_OAEP_MGF1)
    private SecureRandom random;

    // maximum size of the data
    private final int maxDataSize;

    // OAEP: main messagedigest
    private MessageDigest md;

    // OAEP: message digest for MGF1
    private MessageDigest mgfMd;

    // OAEP: value of digest of data (user-supplied or zero-length) using md
    private byte[] lHash;

    /**
     * Get a RSAPadding instance of the specified type.
     * Keys used with this padding must be paddedSize bytes long.
     */
    public static RSAPadding getInstance(int type, int paddedSize)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        return new RSAPadding(type, paddedSize, null, null);
    }

    /**
     * Get a RSAPadding instance of the specified type.
     * Keys used with this padding must be paddedSize bytes long.
     */
    public static RSAPadding getInstance(int type, int paddedSize,
            SecureRandom random) throws InvalidKeyException,
            InvalidAlgorithmParameterException {
        return new RSAPadding(type, paddedSize, random, null);
    }

    /**
     * Get a RSAPadding instance of the specified type, which must be
     * OAEP. Keys used with this padding must be paddedSize bytes long.
     */
    public static RSAPadding getInstance(int type, int paddedSize,
            SecureRandom random, OAEPParameterSpec spec)
        throws InvalidKeyException, InvalidAlgorithmParameterException {
        return new RSAPadding(type, paddedSize, random, spec);
    }

    // internal constructor
    private RSAPadding(int type, int paddedSize, SecureRandom random,
            OAEPParameterSpec spec) throws InvalidKeyException,
            InvalidAlgorithmParameterException {
        this.type = type;
        this.paddedSize = paddedSize;
        this.random = random;
        if (paddedSize < 64) {
            // sanity check, already verified in RSASignature/RSACipher
            throw new InvalidKeyException("Padded size must be at least 64");
        }
        switch (type) {
        case PAD_BLOCKTYPE_1:
        case PAD_BLOCKTYPE_2:
            maxDataSize = paddedSize - 11;
            break;
        case PAD_NONE:
            maxDataSize = paddedSize;
            break;
        case PAD_OAEP_MGF1:
            String mdName = "SHA-1";
            String mgfMdName = "SHA-1";
            byte[] digestInput = null;
            try {
                if (spec != null) {
                    mdName = spec.getDigestAlgorithm();
                    String mgfName = spec.getMGFAlgorithm();
                    if (!mgfName.equalsIgnoreCase("MGF1")) {
                        throw new InvalidAlgorithmParameterException
                            ("Unsupported MGF algo: " + mgfName);
                    }
                    mgfMdName = ((MGF1ParameterSpec)spec.getMGFParameters()).getDigestAlgorithm();
                    PSource pSrc = spec.getPSource();
                    String pSrcAlgo = pSrc.getAlgorithm();
                    if (!pSrcAlgo.equalsIgnoreCase("PSpecified")) {
                        throw new InvalidAlgorithmParameterException
                            ("Unsupported pSource algo: " + pSrcAlgo);
                    }
                    digestInput = ((PSource.PSpecified) pSrc).getValue();
                }
                md = MessageDigest.getInstance(mdName);
                mgfMd = MessageDigest.getInstance(mgfMdName);
            } catch (NoSuchAlgorithmException e) {
                throw new InvalidKeyException
                        ("Digest " + mdName + " not available", e);
            }
            lHash = getInitialHash(md, digestInput);
            int digestLen = lHash.length;
            maxDataSize = paddedSize - 2 - 2 * digestLen;
            if (maxDataSize <= 0) {
                throw new InvalidKeyException
                        ("Key is too short for encryption using OAEPPadding" +
                         " with " + mdName + " and MGF1" + mgfMdName);
            }
            break;
        default:
            throw new InvalidKeyException("Invalid padding: " + type);
        }
    }

    // cache of hashes of zero length data
    private static final Map<String,byte[]> emptyHashes =
        Collections.synchronizedMap(new HashMap<String,byte[]>());

    /**
     * Return the value of the digest using the specified message digest
     * <code>md</code> and the digest input <code>digestInput</code>.
     * if <code>digestInput</code> is null or 0-length, zero length
     * is used to generate the initial digest.
     * Note: the md object must be in reset state
     */
    private static byte[] getInitialHash(MessageDigest md,
        byte[] digestInput) {
        byte[] result = null;
        if ((digestInput == null) || (digestInput.length == 0)) {
            String digestName = md.getAlgorithm();
            result = emptyHashes.get(digestName);
            if (result == null) {
                result = md.digest();
                emptyHashes.put(digestName, result);
            }
        } else {
            result = md.digest(digestInput);
        }
        return result;
    }

    /**
     * Return the maximum size of the plaintext data that can be processed using
     * this object.
     */
    public int getMaxDataSize() {
        return maxDataSize;
    }

    /**
     * Pad the data and return the padded block.
     */
    public byte[] pad(byte[] data, int ofs, int len)
            throws BadPaddingException {
        return pad(RSACore.convert(data, ofs, len));
    }

    /**
     * Pad the data and return the padded block.
     */
    public byte[] pad(byte[] data) throws BadPaddingException {
        if (data.length > maxDataSize) {
            throw new BadPaddingException("Data must be shorter than "
                + (maxDataSize + 1) + " bytes");
        }
        switch (type) {
        case PAD_NONE:
            return data;
        case PAD_BLOCKTYPE_1:
        case PAD_BLOCKTYPE_2:
            return padV15(data);
        case PAD_OAEP_MGF1:
            return padOAEP(data);
        default:
            throw new AssertionError();
        }
    }

    /**
     * Unpad the padded block and return the data.
     */
    public byte[] unpad(byte[] padded, int ofs, int len)
            throws BadPaddingException {
        return unpad(RSACore.convert(padded, ofs, len));
    }

    /**
     * Unpad the padded block and return the data.
     */
    public byte[] unpad(byte[] padded) throws BadPaddingException {
        if (padded.length != paddedSize) {
            throw new BadPaddingException("Padded length must be " + paddedSize);
        }
        switch (type) {
        case PAD_NONE:
            return padded;
        case PAD_BLOCKTYPE_1:
        case PAD_BLOCKTYPE_2:
            return unpadV15(padded);
        case PAD_OAEP_MGF1:
            return unpadOAEP(padded);
        default:
            throw new AssertionError();
        }
    }

    /**
     * PKCS#1 v1.5 padding (blocktype 1 and 2).
     */
    private byte[] padV15(byte[] data) throws BadPaddingException {
        byte[] padded = new byte[paddedSize];
        System.arraycopy(data, 0, padded, paddedSize - data.length, data.length);
        int psSize = paddedSize - 3 - data.length;
        int k = 0;
        padded[k++] = 0;
        padded[k++] = (byte)type;
        if (type == PAD_BLOCKTYPE_1) {
            // blocktype 1: all padding bytes are 0xff
            while (psSize-- > 0) {
                padded[k++] = (byte)0xff;
            }
        } else {
            // blocktype 2: padding bytes are random non-zero bytes
            if (random == null) {
                random = JCAUtil.getSecureRandom();
            }
            // generate non-zero padding bytes
            // use a buffer to reduce calls to SecureRandom
            byte[] r = new byte[64];
            int i = -1;
            while (psSize-- > 0) {
                int b;
                do {
                    if (i < 0) {
                        random.nextBytes(r);
                        i = r.length - 1;
                    }
                    b = r[i--] & 0xff;
                } while (b == 0);
                padded[k++] = (byte)b;
            }
        }
        return padded;
    }

    /**
     * PKCS#1 v1.5 unpadding (blocktype 1 and 2).
     */
    private byte[] unpadV15(byte[] padded) throws BadPaddingException {
        int k = 0;
        if (padded[k++] != 0) {
            throw new BadPaddingException("Data must start with zero");
        }
        if (padded[k++] != type) {
            throw new BadPaddingException("Blocktype mismatch: " + padded[1]);
        }
        while (true) {
            int b = padded[k++] & 0xff;
            if (b == 0) {
                break;
            }
            if (k == padded.length) {
                throw new BadPaddingException("Padding string not terminated");
            }
            if ((type == PAD_BLOCKTYPE_1) && (b != 0xff)) {
                throw new BadPaddingException("Padding byte not 0xff: " + b);
            }
        }
        int n = padded.length - k;
        if (n > maxDataSize) {
            throw new BadPaddingException("Padding string too short");
        }
        byte[] data = new byte[n];
        System.arraycopy(padded, padded.length - n, data, 0, n);
        return data;
    }

    /**
     * PKCS#1 v2.0 OAEP padding (MGF1).
     * Paragraph references refer to PKCS#1 v2.1 (June 14, 2002)
     */
    private byte[] padOAEP(byte[] M) throws BadPaddingException {
        if (random == null) {
            random = JCAUtil.getSecureRandom();
        }
        int hLen = lHash.length;

        // 2.d: generate a random octet string seed of length hLen
        // if necessary
        byte[] seed = new byte[hLen];
        random.nextBytes(seed);

        // buffer for encoded message EM
        byte[] EM = new byte[paddedSize];

        // start and length of seed (as index into EM)
        int seedStart = 1;
        int seedLen = hLen;

        // copy seed into EM
        System.arraycopy(seed, 0, EM, seedStart, seedLen);

        // start and length of data block DB in EM
        // we place it inside of EM to reduce copying
        int dbStart = hLen + 1;
        int dbLen = EM.length - dbStart;

        // start of message M in EM
        int mStart = paddedSize - M.length;

        // build DB
        // 2.b: Concatenate lHash, PS, a single octet with hexadecimal value
        // 0x01, and the message M to form a data block DB of length
        // k - hLen -1 octets as DB = lHash || PS || 0x01 || M
        // (note that PS is all zeros)
        System.arraycopy(lHash, 0, EM, dbStart, hLen);
        EM[mStart - 1] = 1;
        System.arraycopy(M, 0, EM, mStart, M.length);

        // produce maskedDB
        mgf1(EM, seedStart, seedLen, EM, dbStart, dbLen);

        // produce maskSeed
        mgf1(EM, dbStart, dbLen, EM, seedStart, seedLen);

        return EM;
    }

    /**
     * PKCS#1 v2.1 OAEP unpadding (MGF1).
     */
    private byte[] unpadOAEP(byte[] padded) throws BadPaddingException {
        byte[] EM = padded;
        int hLen = lHash.length;

        if (EM[0] != 0) {
            throw new BadPaddingException("Data must start with zero");
        }

        int seedStart = 1;
        int seedLen = hLen;

        int dbStart = hLen + 1;
        int dbLen = EM.length - dbStart;

        mgf1(EM, dbStart, dbLen, EM, seedStart, seedLen);
        mgf1(EM, seedStart, seedLen, EM, dbStart, dbLen);

        // verify lHash == lHash'
        for (int i = 0; i < hLen; i++) {
            if (lHash[i] != EM[dbStart + i]) {
                throw new BadPaddingException("lHash mismatch");
            }
        }

        // skip over padding (0x00 bytes)
        int i = dbStart + hLen;
        while (EM[i] == 0) {
            i++;
            if (i >= EM.length) {
                throw new BadPaddingException("Padding string not terminated");
            }
        }

        if (EM[i++] != 1) {
            throw new BadPaddingException
                ("Padding string not terminated by 0x01 byte");
        }

        int mLen = EM.length - i;
        byte[] m = new byte[mLen];
        System.arraycopy(EM, i, m, 0, mLen);

        return m;
    }

    /**
     * Compute MGF1 using mgfMD as the message digest.
     * Note that we combine MGF1 with the XOR operation to reduce data
     * copying.
     *
     * We generate maskLen bytes of MGF1 from the seed and XOR it into
     * out[] starting at outOfs;
     */
    private void mgf1(byte[] seed, int seedOfs, int seedLen,
            byte[] out, int outOfs, int maskLen)  throws BadPaddingException {
        byte[] C = new byte[4]; // 32 bit counter
        byte[] digest = new byte[mgfMd.getDigestLength()];
        while (maskLen > 0) {
            mgfMd.update(seed, seedOfs, seedLen);
            mgfMd.update(C);
            try {
                mgfMd.digest(digest, 0, digest.length);
            } catch (DigestException e) {
                // should never happen
                throw new BadPaddingException(e.toString());
            }
            for (int i = 0; (i < digest.length) && (maskLen > 0); maskLen--) {
                out[outOfs++] ^= digest[i++];
            }
            if (maskLen > 0) {
                // increment counter
                for (int i = C.length - 1; (++C[i] == 0) && (i > 0); i--) {
                    // empty
                }
            }
        }
    }

}
