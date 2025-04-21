/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import java.security.*;
import java.security.spec.*;

import javax.crypto.spec.PSource;
import javax.crypto.spec.OAEPParameterSpec;

import sun.security.jca.JCAUtil;

/**
 * RSA padding and unpadding.
 *
 * The various PKCS#1 versions can be found in the IETF RFCs
 * tracking the corresponding PKCS#1 standards.
 *
 *     RFC 2313: PKCS#1 v1.5
 *     RFC 2437: PKCS#1 v2.0
 *     RFC 3447: PKCS#1 v2.1
 *     RFC 8017: PKCS#1 v2.2
 *
 * The format of PKCS#1 v1.5 padding is:
 *
 *   0x00 | BT | PS...PS | 0x00 | data...data
 *
 * where BT is the blocktype (1 or 2). The length of the entire string
 * must be the same as the size of the modulus (i.e. 128 byte for a 1024-bit
 * key). Per spec, the padding string must be at least 8 bytes long. That
 * leaves up to (length of key in bytes) - 11 bytes for the data.
 *
 * OAEP padding was introduced in PKCS#1 v2.0 and is a bit more complicated
 * and has a number of options. We support:
 *
 *   . arbitrary hash functions ('Hash' in the specification), MessageDigest
 *     implementation must be available
 *   . MGF1 as the mask generation function
 *   . the empty string as the default value for label L and whatever
 *     specified in javax.crypto.spec.OAEPParameterSpec
 *
 * The algorithms (representations) are forwards-compatible: that is,
 * the algorithm described in previous releases are in later releases.
 * However, additional comments/checks/clarifications were added to the
 * latter versions based on real-world experience (e.g. stricter v1.5
 * format checking.)
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
    public static final int PAD_BLOCKTYPE_1    = 1;
    // PKCS#1 v1.5 padding, blocktype 2 (encryption)
    public static final int PAD_BLOCKTYPE_2    = 2;
    // nopadding. Does not do anything, but allows simpler RSACipher code
    public static final int PAD_NONE           = 3;
    // PKCS#1 v2.1 OAEP padding
    public static final int PAD_OAEP_MGF1 = 4;

    // type, one of PAD_*
    private final int type;

    // size of the padded block (i.e. size of the modulus)
    private final int paddedSize;

    // PRNG used to generate padding bytes (PAD_BLOCKTYPE_2, PAD_OAEP_MGF1)
    private SecureRandom random;

    // maximum size of the data
    private final int maxDataSize;

    // OAEP: MGF1
    private MGF1 mgf;

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
        // OAEP: main message digest
        MessageDigest md;
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
            String mgfMdName = mdName;
            byte[] digestInput = null;
            try {
                if (spec != null) {
                    mdName = spec.getDigestAlgorithm();
                    String mgfName = spec.getMGFAlgorithm();
                    if (!mgfName.equalsIgnoreCase("MGF1")) {
                        throw new InvalidAlgorithmParameterException
                            ("Unsupported MGF algo: " + mgfName);
                    }
                    mgfMdName = ((MGF1ParameterSpec)spec.getMGFParameters())
                            .getDigestAlgorithm();
                    PSource pSrc = spec.getPSource();
                    String pSrcAlgo = pSrc.getAlgorithm();
                    if (!pSrcAlgo.equalsIgnoreCase("PSpecified")) {
                        throw new InvalidAlgorithmParameterException
                            ("Unsupported pSource algo: " + pSrcAlgo);
                    }
                    digestInput = ((PSource.PSpecified) pSrc).getValue();
                }
                md = MessageDigest.getInstance(mdName);
                mgf = new MGF1(mgfMdName);
            } catch (NoSuchAlgorithmException e) {
                throw new InvalidKeyException("Digest not available", e);
            }
            lHash = getInitialHash(md, digestInput);
            int digestLen = lHash.length;
            maxDataSize = paddedSize - 2 - 2 * digestLen;
            if (maxDataSize <= 0) {
                throw new InvalidKeyException
                        ("Key is too short for encryption using OAEPPadding" +
                         " with " + mdName + " and " + mgf.getName());
            }
            break;
        default:
            throw new InvalidKeyException("Invalid padding: " + type);
        }
    }

    // cache of hashes of zero length data
    private static final Map<String,byte[]> emptyHashes =
        Collections.synchronizedMap(new HashMap<>());

    /**
     * Return the value of the digest using the specified message digest
     * <code>md</code> and the digest input <code>digestInput</code>.
     * if <code>digestInput</code> is null or 0-length, zero length
     * is used to generate the initial digest.
     * Note: the md object must be in reset state
     */
    private static byte[] getInitialHash(MessageDigest md,
        byte[] digestInput) {
        byte[] result;
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
     * Return the maximum size of the plaintext data that can be processed
     * using this object.
     */
    public int getMaxDataSize() {
        return maxDataSize;
    }

    /**
     * Pad the data and return the result or null if error occurred.
     */
    public byte[] pad(byte[] data) {
        return pad(data, 0, data.length);
    }

    /**
     * Pad the data and return the result or null if error occurred.
     */
    public byte[] pad(byte[] data, int ofs, int len) {
        if (len > maxDataSize) {
            return null;
        }
        switch (type) {
        case PAD_NONE:
            // assert len == paddedSize and data.length - ofs > len?
            return RSACore.convert(data, ofs, len);
        case PAD_BLOCKTYPE_1:
        case PAD_BLOCKTYPE_2:
            return padV15(data, ofs, len);
        case PAD_OAEP_MGF1:
            return padOAEP(data, ofs, len);
        default:
            throw new AssertionError();
        }
    }

    /**
     * Unpad the padded block and return the result or null if error occurred.
     */
    public byte[] unpad(byte[] padded) {
        if (padded.length == paddedSize) {
            return switch(type) {
                case PAD_NONE -> padded;
                case PAD_BLOCKTYPE_1, PAD_BLOCKTYPE_2 -> unpadV15(padded);
                case PAD_OAEP_MGF1 -> unpadOAEP(padded);
                default -> throw new AssertionError();
            };
        } else {
            return null;
        }
    }

    /**
     * PKCS#1 v1.5 padding (blocktype 1 and 2).
     */
    private byte[] padV15(byte[] data, int ofs, int len) {
        byte[] padded = new byte[paddedSize];
        System.arraycopy(data, ofs, padded, paddedSize - len, len);
        int psSize = paddedSize - 3 - len;
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
            while (psSize > 0) {
                // extra bytes to avoid zero bytes,
                // number of zero bytes <= 4 in 98% cases
                byte[] r = new byte[psSize + 4];
                random.nextBytes(r);
                for (int i = 0; i < r.length && psSize > 0; i++) {
                    if (r[i] != 0) {
                        padded[k++] = r[i];
                        psSize--;
                    }
                }
            }
        }
        return padded;
    }

    /**
     * PKCS#1 v1.5 unpadding (blocktype 1 (signature) and 2 (encryption)).
     * Return the result or null if error occurred.
     * Note that we want to make it a constant-time operation
     */
    private byte[] unpadV15(byte[] padded) {
        int paddedLength = padded.length;

        if (paddedLength < 2) {
            return null;
        }

        // The following check ensures that the lead byte is zero and
        // the second byte is equivalent to the padding type.  The
        // bp (bad padding) variable throughout this unpadding process will
        // be updated and remain 0 if good padding, 1 if bad.
        int p0 = padded[0];
        int p1 = padded[1];
        int bp = (-(p0 & 0xff) | ((p1 - type) | (type - p1))) >>> 31;

        int padLen = 0;
        int k = 2;
        // Walk through the random, nonzero padding bytes.  For each padding
        // byte bp and padLen will remain zero.  When the end-of-padding
        // byte (0x00) is reached then padLen will be set to the index of the
        // first byte of the message content.
        while (k < paddedLength) {
            int b = padded[k++] & 0xff;
            padLen += (k * (1 - ((-(b | padLen)) >>> 31)));
            if (k == paddedLength) {
                bp = bp | (1 - ((-padLen) >>> 31));
            }
            bp = bp | (1 - (-(((type - PAD_BLOCKTYPE_1) & 0xff) |
                    padLen | (1 - ((b - 0xff) >>> 31))) >>> 31));
        }
        int n = paddedLength - padLen;
        // So long as n <= maxDataSize, bp will remain zero
        bp = bp | ((maxDataSize - n) >>> 31);

        // copy useless padding array for a constant-time method
        byte[] padding = new byte[padLen + 2];
        for (int i = 0; i < padLen; i++) {
            padding[i] = padded[i];
        }

        byte[] data = new byte[n];
        for (int i = 0; i < n; i++) {
            data[i] = padded[padLen + i];
        }

        if ((bp | padding[bp]) != 0) {
            // using the array padding here hoping that this way
            // the compiler does not eliminate the above useless copy
            return null;
        } else {
            return data;
        }
    }

    public byte[] unpadForTls(byte[] padded, int clientVersion,
            int serverVersion) {
        int paddedLength = padded.length;

        // bp is positive if the padding is bad and 0 if it is good so far
        int bp = (((int) padded[0] | ((int)padded[1] - PAD_BLOCKTYPE_2)) &
                0xFFF);

        int k = 2;
        while (k < paddedLength - 49) {
            int b = padded[k++] & 0xFF;
            bp = bp | (1 - (-b >>> 31)); // if (padded[k] == 0) bp |= 1;
        }
        bp |= ((int)padded[k++] & 0xFF);
        int encodedVersion = ((padded[k] & 0xFF) << 8) | (padded[k + 1] & 0xFF);

        int bv1 = clientVersion - encodedVersion;
        bv1 |= -bv1;
        int bv3 = serverVersion - encodedVersion;
        bv3 |= -bv3;
        int bv2 = (0x301 - clientVersion);

        bp |= ((bv1 & (bv2 | bv3)) >>> 28);

        byte[] data = Arrays.copyOfRange(padded, paddedLength - 48,
                paddedLength);
        if (random == null) {
            random = JCAUtil.getSecureRandom();
        }

        byte[] fake = new byte[48];
        random.nextBytes(fake);

        bp = (-bp >> 24);

        // Now bp is 0 if the padding and version number were good and
        // -1 otherwise.
        for (int i = 0; i < 48; i++) {
            data[i] = (byte)((~bp & data[i]) | (bp & fake[i]));
        }

        return data;
    }

    /**
     * PKCS#1 v2.0 OAEP padding (MGF1).
     * Paragraph references refer to PKCS#1 v2.1 (June 14, 2002)
     * Return the result or null if error occurred.
     */
    private byte[] padOAEP(byte[] M, int ofs, int len) {
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
        int mStart = paddedSize - len;

        // build DB
        // 2.b: Concatenate lHash, PS, a single octet with hexadecimal value
        // 0x01, and the message M to form a data block DB of length
        // k - hLen -1 octets as DB = lHash || PS || 0x01 || M
        // (note that PS is all zeros)
        System.arraycopy(lHash, 0, EM, dbStart, hLen);
        EM[mStart - 1] = 1;
        System.arraycopy(M, ofs, EM, mStart, len);

        // produce maskedDB
        mgf.generateAndXor(EM, seedStart, seedLen, dbLen, EM, dbStart);

        // produce maskSeed
        mgf.generateAndXor(EM, dbStart, dbLen, seedLen, EM, seedStart);

        return EM;
    }

    /**
     * PKCS#1 v2.1 OAEP unpadding (MGF1).
     * Return the result or null if error occurred.
     */
    private byte[] unpadOAEP(byte[] padded) {
        byte[] EM = padded;
        boolean bp = false;
        int hLen = lHash.length;

        if (EM[0] != 0) {
            bp = true;
        }

        int seedStart = 1;
        int seedLen = hLen;

        int dbStart = hLen + 1;
        int dbLen = EM.length - dbStart;

        mgf.generateAndXor(EM, dbStart, dbLen, seedLen, EM, seedStart);
        mgf.generateAndXor(EM, seedStart, seedLen, dbLen, EM, dbStart);

        // verify lHash == lHash'
        for (int i = 0; i < hLen; i++) {
            if (lHash[i] != EM[dbStart + i]) {
                bp = true;
            }
        }

        int padStart = dbStart + hLen;
        int onePos = -1;

        for (int i = padStart; i < EM.length; i++) {
            int value = EM[i];
            if (onePos == -1) {
                if (value == 0x00) {
                    // continue;
                } else if (value == 0x01) {
                    onePos = i;
                } else {  // Anything other than {0,1} is bad.
                    bp = true;
                }
            }
        }

        // We either ran off the rails or found something other than 0/1.
        if (onePos == -1) {
            bp = true;
            onePos = EM.length - 1;  // Don't inadvertently return any data.
        }

        int mStart = onePos + 1;

        // copy useless padding array for a constant-time method
        byte [] tmp = new byte[mStart - padStart];
        System.arraycopy(EM, padStart, tmp, 0, tmp.length);

        byte [] m = new byte[EM.length - mStart];
        System.arraycopy(EM, mStart, m, 0, m.length);

        return (bp? null : m);
    }
}
