/*
 * Copyright (c) 1996, 2015, Oracle and/or its affiliates. All rights reserved.
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


package sun.security.ssl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Arrays;

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.GCMParameterSpec;

import java.nio.*;

import sun.security.ssl.CipherSuite.*;
import static sun.security.ssl.CipherSuite.*;
import static sun.security.ssl.CipherSuite.CipherType.*;

import sun.security.util.HexDumpEncoder;


/**
 * This class handles bulk data enciphering/deciphering for each SSLv3
 * message.  This provides data confidentiality.  Stream ciphers (such
 * as RC4) don't need to do padding; block ciphers (e.g. DES) need it.
 *
 * Individual instances are obtained by calling the static method
 * newCipherBox(), which should only be invoked by BulkCipher.newCipher().
 *
 * In RFC 2246, with bock ciphers in CBC mode, the Initialization
 * Vector (IV) for the first record is generated with the other keys
 * and secrets when the security parameters are set.  The IV for
 * subsequent records is the last ciphertext block from the previous
 * record.
 *
 * In RFC 4346, the implicit Initialization Vector (IV) is replaced
 * with an explicit IV to protect against CBC attacks.  RFC 4346
 * recommends two algorithms used to generated the per-record IV.
 * The implementation uses the algorithm (2)(b), as described at
 * section 6.2.3.2 of RFC 4346.
 *
 * The usage of IV in CBC block cipher can be illustrated in
 * the following diagrams.
 *
 *   (random)
 *        R         P1                    IV        C1
 *        |          |                     |         |
 *  SIV---+    |-----+    |-...            |-----    |------
 *        |    |     |    |                |    |    |     |
 *     +----+  |  +----+  |             +----+  |  +----+  |
 *     | Ek |  |  + Ek +  |             | Dk |  |  | Dk |  |
 *     +----+  |  +----+  |             +----+  |  +----+  |
 *        |    |     |    |                |    |    |     |
 *        |----|     |----|           SIV--+    |----|     |-...
 *        |          |                     |       |
 *       IV         C1                     R      P1
 *                                     (discard)
 *
 *       CBC Encryption                    CBC Decryption
 *
 * NOTE that any ciphering involved in key exchange (e.g. with RSA) is
 * handled separately.
 *
 * @author David Brownell
 * @author Andreas Sterbenz
 */
final class CipherBox {

    // A CipherBox that implements the identity operation
    static final CipherBox NULL = new CipherBox();

    /* Class and subclass dynamic debugging support */
    private static final Debug debug = Debug.getInstance("ssl");

    // the protocol version this cipher conforms to
    private final ProtocolVersion protocolVersion;

    // cipher object
    private final Cipher cipher;

    /**
     * secure random
     */
    private SecureRandom random;

    /**
     * fixed IV, the implicit nonce of AEAD cipher suite, only apply to
     * AEAD cipher suites
     */
    private final byte[] fixedIv;

    /**
     * the key, reserved only for AEAD cipher initialization
     */
    private final Key key;

    /**
     * the operation mode, reserved for AEAD cipher initialization
     */
    private final int mode;

    /**
     * the authentication tag size, only apply to AEAD cipher suites
     */
    private final int tagSize;

    /**
     * the record IV length, only apply to AEAD cipher suites
     */
    private final int recordIvSize;

    /**
     * cipher type
     */
    private final CipherType cipherType;

    /**
     * Fixed masks of various block size, as the initial decryption IVs
     * for TLS 1.1 or later.
     *
     * For performance, we do not use random IVs. As the initial decryption
     * IVs will be discarded by TLS decryption processes, so the fixed masks
     * do not hurt cryptographic strength.
     */
    private static Hashtable<Integer, IvParameterSpec> masks;

    /**
     * NULL cipherbox. Identity operation, no encryption.
     */
    private CipherBox() {
        this.protocolVersion = ProtocolVersion.DEFAULT_TLS;
        this.cipher = null;
        this.cipherType = NULL_CIPHER;
        this.fixedIv = new byte[0];
        this.key = null;
        this.mode = Cipher.ENCRYPT_MODE;    // choose at random
        this.random = null;
        this.tagSize = 0;
        this.recordIvSize = 0;
    }

    /**
     * Construct a new CipherBox using the cipher transformation.
     *
     * @exception NoSuchAlgorithmException if no appropriate JCE Cipher
     * implementation could be found.
     */
    private CipherBox(ProtocolVersion protocolVersion, BulkCipher bulkCipher,
            SecretKey key, IvParameterSpec iv, SecureRandom random,
            boolean encrypt) throws NoSuchAlgorithmException {
        try {
            this.protocolVersion = protocolVersion;
            this.cipher = JsseJce.getCipher(bulkCipher.transformation);
            this.mode = encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;

            if (random == null) {
                random = JsseJce.getSecureRandom();
            }
            this.random = random;
            this.cipherType = bulkCipher.cipherType;

            /*
             * RFC 4346 recommends two algorithms used to generated the
             * per-record IV. The implementation uses the algorithm (2)(b),
             * as described at section 6.2.3.2 of RFC 4346.
             *
             * As we don't care about the initial IV value for TLS 1.1 or
             * later, so if the "iv" parameter is null, we use the default
             * value generated by Cipher.init() for encryption, and a fixed
             * mask for decryption.
             */
            if (iv == null && bulkCipher.ivSize != 0 &&
                    mode == Cipher.DECRYPT_MODE &&
                    protocolVersion.useTLS11PlusSpec()) {
                iv = getFixedMask(bulkCipher.ivSize);
            }

            if (cipherType == AEAD_CIPHER) {
                // AEAD must completely initialize the cipher for each packet,
                // and so we save initialization parameters for packet
                // processing time.

                // Set the tag size for AEAD cipher
                tagSize = bulkCipher.tagSize;

                // Reserve the key for AEAD cipher initialization
                this.key = key;

                fixedIv = iv.getIV();
                if (fixedIv == null ||
                        fixedIv.length != bulkCipher.fixedIvSize) {
                    throw new RuntimeException("Improper fixed IV for AEAD");
                }

                // Set the record IV length for AEAD cipher
                recordIvSize = bulkCipher.ivSize - bulkCipher.fixedIvSize;

                // DON'T initialize the cipher for AEAD!
            } else {
                // CBC only requires one initialization during its lifetime
                // (future packets/IVs set the proper CBC state), so we can
                // initialize now.

                // Zeroize the variables that only apply to AEAD cipher
                this.tagSize = 0;
                this.fixedIv = new byte[0];
                this.recordIvSize = 0;
                this.key = null;

                // Initialize the cipher
                cipher.init(mode, key, iv, random);
            }
        } catch (NoSuchAlgorithmException e) {
            throw e;
        } catch (Exception e) {
            throw new NoSuchAlgorithmException
                    ("Could not create cipher " + bulkCipher, e);
        } catch (ExceptionInInitializerError e) {
            throw new NoSuchAlgorithmException
                    ("Could not create cipher " + bulkCipher, e);
        }
    }

    /*
     * Factory method to obtain a new CipherBox object.
     */
    static CipherBox newCipherBox(ProtocolVersion version, BulkCipher cipher,
            SecretKey key, IvParameterSpec iv, SecureRandom random,
            boolean encrypt) throws NoSuchAlgorithmException {
        if (cipher.allowed == false) {
            throw new NoSuchAlgorithmException("Unsupported cipher " + cipher);
        }

        if (cipher == BulkCipher.B_NULL) {
            return NULL;
        } else {
            return new CipherBox(version, cipher, key, iv, random, encrypt);
        }
    }

    /*
     * Get a fixed mask, as the initial decryption IVs for TLS 1.1 or later.
     */
    private static IvParameterSpec getFixedMask(int ivSize) {
        if (masks == null) {
            masks = new Hashtable<Integer, IvParameterSpec>(5);
        }

        IvParameterSpec iv = masks.get(ivSize);
        if (iv == null) {
            iv = new IvParameterSpec(new byte[ivSize]);
            masks.put(ivSize, iv);
        }

        return iv;
    }

    /*
     * Encrypts a block of data, returning the size of the
     * resulting block.
     */
    int encrypt(byte[] buf, int offset, int len) {
        if (cipher == null) {
            return len;
        }

        try {
            int blockSize = cipher.getBlockSize();
            if (cipherType == BLOCK_CIPHER) {
                len = addPadding(buf, offset, len, blockSize);
            }

            if (debug != null && Debug.isOn("plaintext")) {
                try {
                    HexDumpEncoder hd = new HexDumpEncoder();

                    System.out.println(
                        "Padded plaintext before ENCRYPTION:  len = "
                        + len);
                    hd.encodeBuffer(
                        new ByteArrayInputStream(buf, offset, len),
                        System.out);
                } catch (IOException e) { }
            }


            if (cipherType == AEAD_CIPHER) {
                try {
                    return cipher.doFinal(buf, offset, len, buf, offset);
                } catch (IllegalBlockSizeException | BadPaddingException ibe) {
                    // unlikely to happen
                    throw new RuntimeException(
                        "Cipher error in AEAD mode in JCE provider " +
                        cipher.getProvider().getName(), ibe);
                }
            } else {
                int newLen = cipher.update(buf, offset, len, buf, offset);
                if (newLen != len) {
                    // catch BouncyCastle buffering error
                    throw new RuntimeException("Cipher buffering error " +
                        "in JCE provider " + cipher.getProvider().getName());
                }
                return newLen;
            }
        } catch (ShortBufferException e) {
            // unlikely to happen, we should have enough buffer space here
            throw new ArrayIndexOutOfBoundsException(e.toString());
        }
    }

    /*
     * Encrypts a ByteBuffer block of data, returning the size of the
     * resulting block.
     *
     * The byte buffers position and limit initially define the amount
     * to encrypt.  On return, the position and limit are
     * set to last position padded/encrypted.  The limit may have changed
     * because of the added padding bytes.
     */
    int encrypt(ByteBuffer bb, int outLimit) {

        int len = bb.remaining();

        if (cipher == null) {
            bb.position(bb.limit());
            return len;
        }

        int pos = bb.position();

        int blockSize = cipher.getBlockSize();
        if (cipherType == BLOCK_CIPHER) {
            // addPadding adjusts pos/limit
            len = addPadding(bb, blockSize);
            bb.position(pos);
        }

        if (debug != null && Debug.isOn("plaintext")) {
            try {
                HexDumpEncoder hd = new HexDumpEncoder();

                System.out.println(
                    "Padded plaintext before ENCRYPTION:  len = "
                    + len);
                hd.encodeBuffer(bb.duplicate(), System.out);

            } catch (IOException e) { }
        }

        /*
         * Encrypt "in-place".  This does not add its own padding.
         */
        ByteBuffer dup = bb.duplicate();
        if (cipherType == AEAD_CIPHER) {
            try {
                int outputSize = cipher.getOutputSize(dup.remaining());
                if (outputSize > bb.remaining()) {
                    // need to expand the limit of the output buffer for
                    // the authentication tag.
                    //
                    // DON'T worry about the buffer's capacity, we have
                    // reserved space for the authentication tag.
                    if (outLimit < pos + outputSize) {
                        // unlikely to happen
                        throw new ShortBufferException(
                                    "need more space in output buffer");
                    }
                    bb.limit(pos + outputSize);
                }
                int newLen = cipher.doFinal(dup, bb);
                if (newLen != outputSize) {
                    throw new RuntimeException(
                            "Cipher buffering error in JCE provider " +
                            cipher.getProvider().getName());
                }
                return newLen;
            } catch (IllegalBlockSizeException |
                           BadPaddingException | ShortBufferException ibse) {
                // unlikely to happen
                throw new RuntimeException(
                        "Cipher error in AEAD mode in JCE provider " +
                        cipher.getProvider().getName(), ibse);
            }
        } else {
            int newLen;
            try {
                newLen = cipher.update(dup, bb);
            } catch (ShortBufferException sbe) {
                // unlikely to happen
                throw new RuntimeException("Cipher buffering error " +
                    "in JCE provider " + cipher.getProvider().getName());
            }

            if (bb.position() != dup.position()) {
                throw new RuntimeException("bytebuffer padding error");
            }

            if (newLen != len) {
                // catch BouncyCastle buffering error
                throw new RuntimeException("Cipher buffering error " +
                    "in JCE provider " + cipher.getProvider().getName());
            }
            return newLen;
        }
    }


    /*
     * Decrypts a block of data, returning the size of the
     * resulting block if padding was required.
     *
     * For SSLv3 and TLSv1.0, with block ciphers in CBC mode the
     * Initialization Vector (IV) for the first record is generated by
     * the handshake protocol, the IV for subsequent records is the
     * last ciphertext block from the previous record.
     *
     * From TLSv1.1, the implicit IV is replaced with an explicit IV to
     * protect against CBC attacks.
     *
     * Differentiating between bad_record_mac and decryption_failed alerts
     * may permit certain attacks against CBC mode. It is preferable to
     * uniformly use the bad_record_mac alert to hide the specific type of
     * the error.
     */
    int decrypt(byte[] buf, int offset, int len,
            int tagLen) throws BadPaddingException {
        if (cipher == null) {
            return len;
        }

        try {
            int newLen;
            if (cipherType == AEAD_CIPHER) {
                try {
                    newLen = cipher.doFinal(buf, offset, len, buf, offset);
                } catch (IllegalBlockSizeException ibse) {
                    // unlikely to happen
                    throw new RuntimeException(
                        "Cipher error in AEAD mode in JCE provider " +
                        cipher.getProvider().getName(), ibse);
                }
            } else {
                newLen = cipher.update(buf, offset, len, buf, offset);
                if (newLen != len) {
                    // catch BouncyCastle buffering error
                    throw new RuntimeException("Cipher buffering error " +
                        "in JCE provider " + cipher.getProvider().getName());
                }
            }
            if (debug != null && Debug.isOn("plaintext")) {
                try {
                    HexDumpEncoder hd = new HexDumpEncoder();

                    System.out.println(
                        "Padded plaintext after DECRYPTION:  len = "
                        + newLen);
                    hd.encodeBuffer(
                        new ByteArrayInputStream(buf, offset, newLen),
                        System.out);
                } catch (IOException e) { }
            }

            if (cipherType == BLOCK_CIPHER) {
                int blockSize = cipher.getBlockSize();
                newLen = removePadding(
                    buf, offset, newLen, tagLen, blockSize, protocolVersion);

                if (protocolVersion.useTLS11PlusSpec()) {
                    if (newLen < blockSize) {
                        throw new BadPaddingException("invalid explicit IV");
                    }
                }
            }
            return newLen;
        } catch (ShortBufferException e) {
            // unlikely to happen, we should have enough buffer space here
            throw new ArrayIndexOutOfBoundsException(e.toString());
        }
    }


    /*
     * Decrypts a block of data, returning the size of the
     * resulting block if padding was required.  position and limit
     * point to the end of the decrypted/depadded data.  The initial
     * limit and new limit may be different, given we may
     * have stripped off some padding bytes.
     *
     *  @see decrypt(byte[], int, int)
     */
    int decrypt(ByteBuffer bb, int tagLen) throws BadPaddingException {

        int len = bb.remaining();

        if (cipher == null) {
            bb.position(bb.limit());
            return len;
        }

        try {
            /*
             * Decrypt "in-place".
             */
            int pos = bb.position();
            ByteBuffer dup = bb.duplicate();
            int newLen;
            if (cipherType == AEAD_CIPHER) {
                try {
                    newLen = cipher.doFinal(dup, bb);
                } catch (IllegalBlockSizeException ibse) {
                    // unlikely to happen
                    throw new RuntimeException(
                        "Cipher error in AEAD mode \"" + ibse.getMessage() +
                        " \"in JCE provider " + cipher.getProvider().getName());
                }
            } else {
                newLen = cipher.update(dup, bb);
                if (newLen != len) {
                    // catch BouncyCastle buffering error
                    throw new RuntimeException("Cipher buffering error " +
                        "in JCE provider " + cipher.getProvider().getName());
                }
            }

            // reset the limit to the end of the decryted data
            bb.limit(pos + newLen);

            if (debug != null && Debug.isOn("plaintext")) {
                try {
                    HexDumpEncoder hd = new HexDumpEncoder();

                    System.out.println(
                        "Padded plaintext after DECRYPTION:  len = "
                        + newLen);

                    hd.encodeBuffer(
                        bb.duplicate().position(pos), System.out);
                } catch (IOException e) { }
            }

            /*
             * Remove the block padding.
             */
            if (cipherType == BLOCK_CIPHER) {
                int blockSize = cipher.getBlockSize();
                bb.position(pos);
                newLen = removePadding(bb, tagLen, blockSize, protocolVersion);

                // check the explicit IV of TLS v1.1 or later
                if (protocolVersion.useTLS11PlusSpec()) {
                    if (newLen < blockSize) {
                        throw new BadPaddingException("invalid explicit IV");
                    }

                    // reset the position to the end of the decrypted data
                    bb.position(bb.limit());
                }
            }
            return newLen;
        } catch (ShortBufferException e) {
            // unlikely to happen, we should have enough buffer space here
            throw new ArrayIndexOutOfBoundsException(e.toString());
        }
    }

    private static int addPadding(byte[] buf, int offset, int len,
            int blockSize) {
        int     newlen = len + 1;
        byte    pad;
        int     i;

        if ((newlen % blockSize) != 0) {
            newlen += blockSize - 1;
            newlen -= newlen % blockSize;
        }
        pad = (byte) (newlen - len);

        if (buf.length < (newlen + offset)) {
            throw new IllegalArgumentException("no space to pad buffer");
        }

        /*
         * TLS version of the padding works for both SSLv3 and TLSv1
         */
        for (i = 0, offset += len; i < pad; i++) {
            buf [offset++] = (byte) (pad - 1);
        }
        return newlen;
    }

    /*
     * Apply the padding to the buffer.
     *
     * Limit is advanced to the new buffer length.
     * Position is equal to limit.
     */
    private static int addPadding(ByteBuffer bb, int blockSize) {

        int     len = bb.remaining();
        int     offset = bb.position();

        int     newlen = len + 1;
        byte    pad;
        int     i;

        if ((newlen % blockSize) != 0) {
            newlen += blockSize - 1;
            newlen -= newlen % blockSize;
        }
        pad = (byte) (newlen - len);

        /*
         * Update the limit to what will be padded.
         */
        bb.limit(newlen + offset);

        /*
         * TLS version of the padding works for both SSLv3 and TLSv1
         */
        for (i = 0, offset += len; i < pad; i++) {
            bb.put(offset++, (byte) (pad - 1));
        }

        bb.position(offset);
        bb.limit(offset);

        return newlen;
    }

    /*
     * A constant-time check of the padding.
     *
     * NOTE that we are checking both the padding and the padLen bytes here.
     *
     * The caller MUST ensure that the len parameter is a positive number.
     */
    private static int[] checkPadding(
            byte[] buf, int offset, int len, byte pad) {

        if (len <= 0) {
            throw new RuntimeException("padding len must be positive");
        }

        // An array of hits is used to prevent Hotspot optimization for
        // the purpose of a constant-time check.
        int[] results = {0, 0};    // {missed #, matched #}
        for (int i = 0; i <= 256;) {
            for (int j = 0; j < len && i <= 256; j++, i++) {     // j <= i
                if (buf[offset + j] != pad) {
                    results[0]++;       // mismatched padding data
                } else {
                    results[1]++;       // matched padding data
                }
            }
        }

        return results;
    }

    /*
     * A constant-time check of the padding.
     *
     * NOTE that we are checking both the padding and the padLen bytes here.
     *
     * The caller MUST ensure that the bb parameter has remaining.
     */
    private static int[] checkPadding(ByteBuffer bb, byte pad) {

        if (!bb.hasRemaining()) {
            throw new RuntimeException("hasRemaining() must be positive");
        }

        // An array of hits is used to prevent Hotspot optimization for
        // the purpose of a constant-time check.
        int[] results = {0, 0};    // {missed #, matched #}
        bb.mark();
        for (int i = 0; i <= 256; bb.reset()) {
            for (; bb.hasRemaining() && i <= 256; i++) {
                if (bb.get() != pad) {
                    results[0]++;       // mismatched padding data
                } else {
                    results[1]++;       // matched padding data
                }
            }
        }

        return results;
    }

    /*
     * Typical TLS padding format for a 64 bit block cipher is as follows:
     *   xx xx xx xx xx xx xx 00
     *   xx xx xx xx xx xx 01 01
     *   ...
     *   xx 06 06 06 06 06 06 06
     *   07 07 07 07 07 07 07 07
     * TLS also allows any amount of padding from 1 and 256 bytes as long
     * as it makes the data a multiple of the block size
     */
    private static int removePadding(byte[] buf, int offset, int len,
            int tagLen, int blockSize,
            ProtocolVersion protocolVersion) throws BadPaddingException {

        // last byte is length byte (i.e. actual padding length - 1)
        int padOffset = offset + len - 1;
        int padLen = buf[padOffset] & 0xFF;

        int newLen = len - (padLen + 1);
        if ((newLen - tagLen) < 0) {
            // If the buffer is not long enough to contain the padding plus
            // a MAC tag, do a dummy constant-time padding check.
            //
            // Note that it is a dummy check, so we won't care about what is
            // the actual padding data.
            checkPadding(buf, offset, len, (byte)(padLen & 0xFF));

            throw new BadPaddingException("Invalid Padding length: " + padLen);
        }

        // The padding data should be filled with the padding length value.
        int[] results = checkPadding(buf, offset + newLen,
                        padLen + 1, (byte)(padLen & 0xFF));
        if (protocolVersion.useTLS10PlusSpec()) {
            if (results[0] != 0) {          // padding data has invalid bytes
                throw new BadPaddingException("Invalid TLS padding data");
            }
        } else { // SSLv3
            // SSLv3 requires 0 <= length byte < block size
            // some implementations do 1 <= length byte <= block size,
            // so accept that as well
            // v3 does not require any particular value for the other bytes
            if (padLen > blockSize) {
                throw new BadPaddingException("Invalid SSLv3 padding");
            }
        }
        return newLen;
    }

    /*
     * Position/limit is equal the removed padding.
     */
    private static int removePadding(ByteBuffer bb,
            int tagLen, int blockSize,
            ProtocolVersion protocolVersion) throws BadPaddingException {

        int len = bb.remaining();
        int offset = bb.position();

        // last byte is length byte (i.e. actual padding length - 1)
        int padOffset = offset + len - 1;
        int padLen = bb.get(padOffset) & 0xFF;

        int newLen = len - (padLen + 1);
        if ((newLen - tagLen) < 0) {
            // If the buffer is not long enough to contain the padding plus
            // a MAC tag, do a dummy constant-time padding check.
            //
            // Note that it is a dummy check, so we won't care about what is
            // the actual padding data.
            checkPadding(bb.duplicate(), (byte)(padLen & 0xFF));

            throw new BadPaddingException("Invalid Padding length: " + padLen);
        }

        // The padding data should be filled with the padding length value.
        int[] results = checkPadding(
                bb.duplicate().position(offset + newLen),
                (byte)(padLen & 0xFF));
        if (protocolVersion.useTLS10PlusSpec()) {
            if (results[0] != 0) {          // padding data has invalid bytes
                throw new BadPaddingException("Invalid TLS padding data");
            }
        } else { // SSLv3
            // SSLv3 requires 0 <= length byte < block size
            // some implementations do 1 <= length byte <= block size,
            // so accept that as well
            // v3 does not require any particular value for the other bytes
            if (padLen > blockSize) {
                throw new BadPaddingException("Invalid SSLv3 padding");
            }
        }

        /*
         * Reset buffer limit to remove padding.
         */
        bb.position(offset + newLen);
        bb.limit(offset + newLen);

        return newLen;
    }

    /*
     * Dispose of any intermediate state in the underlying cipher.
     * For PKCS11 ciphers, this will release any attached sessions, and
     * thus make finalization faster.
     */
    void dispose() {
        try {
            if (cipher != null) {
                // ignore return value.
                cipher.doFinal();
            }
        } catch (Exception e) {
            // swallow all types of exceptions.
        }
    }

    /*
     * Does the cipher use CBC mode?
     *
     * @return true if the cipher use CBC mode, false otherwise.
     */
    boolean isCBCMode() {
        return cipherType == BLOCK_CIPHER;
    }

    /*
     * Does the cipher use AEAD mode?
     *
     * @return true if the cipher use AEAD mode, false otherwise.
     */
    boolean isAEADMode() {
        return cipherType == AEAD_CIPHER;
    }

    /*
     * Is the cipher null?
     *
     * @return true if the cipher is null, false otherwise.
     */
    boolean isNullCipher() {
        return cipher == null;
    }

    /*
     * Gets the explicit nonce/IV size of the cipher.
     *
     * The returned value is the SecurityParameters.record_iv_length in
     * RFC 4346/5246.  It is the size of explicit IV for CBC mode, and the
     * size of explicit nonce for AEAD mode.
     *
     * @return the explicit nonce size of the cipher.
     */
    int getExplicitNonceSize() {
        switch (cipherType) {
            case BLOCK_CIPHER:
                // For block ciphers, the explicit IV length is of length
                // SecurityParameters.record_iv_length, which is equal to
                // the SecurityParameters.block_size.
                if (protocolVersion.useTLS11PlusSpec()) {
                    return cipher.getBlockSize();
                }
                break;
            case AEAD_CIPHER:
                return recordIvSize;
                        // It is also the length of sequence number, which is
                        // used as the nonce_explicit for AEAD cipher suites.
        }

        return 0;
    }

    /*
     * Applies the explicit nonce/IV to this cipher. This method is used to
     * decrypt an SSL/TLS input record.
     *
     * The returned value is the SecurityParameters.record_iv_length in
     * RFC 4346/5246.  It is the size of explicit IV for CBC mode, and the
     * size of explicit nonce for AEAD mode.
     *
     * @param  authenticator the authenticator to get the additional
     *         authentication data
     * @param  contentType the content type of the input record
     * @param  bb the byte buffer to get the explicit nonce from
     *
     * @return the explicit nonce size of the cipher.
     */
    int applyExplicitNonce(Authenticator authenticator, byte contentType,
            ByteBuffer bb, byte[] sequence) throws BadPaddingException {
        switch (cipherType) {
            case BLOCK_CIPHER:
                // sanity check length of the ciphertext
                int tagLen = (authenticator instanceof MAC) ?
                                    ((MAC)authenticator).MAClen() : 0;
                if (tagLen != 0) {
                    if (!sanityCheck(tagLen, bb.remaining())) {
                        throw new BadPaddingException(
                                "ciphertext sanity check failed");
                    }
                }

                // For block ciphers, the explicit IV length is of length
                // SecurityParameters.record_iv_length, which is equal to
                // the SecurityParameters.block_size.
                if (protocolVersion.useTLS11PlusSpec()) {
                    return cipher.getBlockSize();
                }
                break;
            case AEAD_CIPHER:
                if (bb.remaining() < (recordIvSize + tagSize)) {
                    throw new BadPaddingException(
                                        "invalid AEAD cipher fragment");
                }

                // initialize the AEAD cipher for the unique IV
                byte[] iv = Arrays.copyOf(fixedIv,
                                    fixedIv.length + recordIvSize);
                bb.get(iv, fixedIv.length, recordIvSize);
                bb.position(bb.position() - recordIvSize);
                GCMParameterSpec spec = new GCMParameterSpec(tagSize * 8, iv);
                try {
                    cipher.init(mode, key, spec, random);
                } catch (InvalidKeyException |
                            InvalidAlgorithmParameterException ikae) {
                    // unlikely to happen
                    throw new RuntimeException(
                                "invalid key or spec in GCM mode", ikae);
                }

                // update the additional authentication data
                byte[] aad = authenticator.acquireAuthenticationBytes(
                        contentType, bb.remaining() - recordIvSize - tagSize,
                        sequence);
                cipher.updateAAD(aad);

                return recordIvSize;
                        // It is also the length of sequence number, which is
                        // used as the nonce_explicit for AEAD cipher suites.
        }

       return 0;
    }

    /*
     * Creates the explicit nonce/IV to this cipher. This method is used to
     * encrypt an SSL/TLS output record.
     *
     * The size of the returned array is the SecurityParameters.record_iv_length
     * in RFC 4346/5246.  It is the size of explicit IV for CBC mode, and the
     * size of explicit nonce for AEAD mode.
     *
     * @param  authenticator the authenticator to get the additional
     *         authentication data
     * @param  contentType the content type of the input record
     * @param  fragmentLength the fragment length of the output record, it is
     *         the TLSCompressed.length in RFC 4346/5246.
     *
     * @return the explicit nonce of the cipher.
     */
    byte[] createExplicitNonce(Authenticator authenticator,
            byte contentType, int fragmentLength) {

        byte[] nonce = new byte[0];
        switch (cipherType) {
            case BLOCK_CIPHER:
                if (protocolVersion.useTLS11PlusSpec()) {
                    // For block ciphers, the explicit IV length is of length
                    // SecurityParameters.record_iv_length, which is equal to
                    // the SecurityParameters.block_size.
                    //
                    // Generate a random number as the explicit IV parameter.
                    nonce = new byte[cipher.getBlockSize()];
                    random.nextBytes(nonce);
                }
                break;
            case AEAD_CIPHER:
                // To be unique and aware of overflow-wrap, sequence number
                // is used as the nonce_explicit of AEAD cipher suites.
                nonce = authenticator.sequenceNumber();

                // initialize the AEAD cipher for the unique IV
                byte[] iv = Arrays.copyOf(fixedIv,
                                            fixedIv.length + nonce.length);
                System.arraycopy(nonce, 0, iv, fixedIv.length, nonce.length);
                GCMParameterSpec spec = new GCMParameterSpec(tagSize * 8, iv);
                try {
                    cipher.init(mode, key, spec, random);
                } catch (InvalidKeyException |
                            InvalidAlgorithmParameterException ikae) {
                    // unlikely to happen
                    throw new RuntimeException(
                                "invalid key or spec in GCM mode", ikae);
                }

                // Update the additional authentication data, using the
                // implicit sequence number of the authenticator.
                byte[] aad = authenticator.acquireAuthenticationBytes(
                                        contentType, fragmentLength, null);
                cipher.updateAAD(aad);
                break;
        }

        return nonce;
    }

    // See also CipherSuite.calculatePacketSize().
    int calculatePacketSize(int fragmentSize, int macLen, int headerSize) {
        int packetSize = fragmentSize;
        if (cipher != null) {
            int blockSize = cipher.getBlockSize();
            switch (cipherType) {
                case BLOCK_CIPHER:
                    packetSize += macLen;
                    packetSize += 1;        // 1 byte padding length field
                    packetSize +=           // use the minimal padding
                            (blockSize - (packetSize % blockSize)) % blockSize;
                    if (protocolVersion.useTLS11PlusSpec()) {
                        packetSize += blockSize;        // explicit IV
                    }

                    break;
                case AEAD_CIPHER:
                    packetSize += recordIvSize;
                    packetSize += tagSize;

                    break;
                default:    // NULL_CIPHER or STREAM_CIPHER
                    packetSize += macLen;
            }
        }

        return packetSize + headerSize;
    }

    // See also CipherSuite.calculateFragSize().
    int calculateFragmentSize(int packetLimit, int macLen, int headerSize) {
        int fragLen = packetLimit - headerSize;
        if (cipher != null) {
            int blockSize = cipher.getBlockSize();
            switch (cipherType) {
                case BLOCK_CIPHER:
                    if (protocolVersion.useTLS11PlusSpec()) {
                        fragLen -= blockSize;           // explicit IV
                    }
                    fragLen -= (fragLen % blockSize);   // cannot hold a block
                    // No padding for a maximum fragment.
                    fragLen -= 1;       // 1 byte padding length field: 0x00
                    fragLen -= macLen;

                    break;
                case AEAD_CIPHER:
                    fragLen -= recordIvSize;
                    fragLen -= tagSize;

                    break;
                default:    // NULL_CIPHER or STREAM_CIPHER
                    fragLen -= macLen;
            }
        }

        return fragLen;
    }

    // Estimate the maximum fragment size of a received packet.
    int estimateFragmentSize(int packetSize, int macLen, int headerSize) {
        int fragLen = packetSize - headerSize;
        if (cipher != null) {
            int blockSize = cipher.getBlockSize();
            switch (cipherType) {
                case BLOCK_CIPHER:
                    if (protocolVersion.useTLS11PlusSpec()) {
                        fragLen -= blockSize;       // explicit IV
                    }
                    // No padding for a maximum fragment.
                    fragLen -= 1;       // 1 byte padding length field: 0x00
                    fragLen -= macLen;

                    break;
                case AEAD_CIPHER:
                    fragLen -= recordIvSize;
                    fragLen -= tagSize;

                    break;
                default:    // NULL_CIPHER or STREAM_CIPHER
                    fragLen -= macLen;
            }
        }

        return fragLen;
    }

    /**
     * Sanity check the length of a fragment before decryption.
     *
     * In CBC mode, check that the fragment length is one or multiple times
     * of the block size of the cipher suite, and is at least one (one is the
     * smallest size of padding in CBC mode) bigger than the tag size of the
     * MAC algorithm except the explicit IV size for TLS 1.1 or later.
     *
     * In non-CBC mode, check that the fragment length is not less than the
     * tag size of the MAC algorithm.
     *
     * @return true if the length of a fragment matches above requirements
     */
    private boolean sanityCheck(int tagLen, int fragmentLen) {
        if (!isCBCMode()) {
            return fragmentLen >= tagLen;
        }

        int blockSize = cipher.getBlockSize();
        if ((fragmentLen % blockSize) == 0) {
            int minimal = tagLen + 1;
            minimal = (minimal >= blockSize) ? minimal : blockSize;
            if (protocolVersion.useTLS11PlusSpec()) {
                minimal += blockSize;   // plus the size of the explicit IV
            }

            return (fragmentLen >= minimal);
        }

        return false;
    }

}
