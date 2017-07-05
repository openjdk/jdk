/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
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


package sun.security.ssl;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

import java.nio.*;

import sun.security.ssl.CipherSuite.*;
import static sun.security.ssl.CipherSuite.*;

import sun.misc.HexDumpEncoder;


/**
 * This class handles bulk data enciphering/deciphering for each SSLv3
 * message.  This provides data confidentiality.  Stream ciphers (such
 * as RC4) don't need to do padding; block ciphers (e.g. DES) need it.
 *
 * Individual instances are obtained by calling the static method
 * newCipherBox(), which should only be invoked by BulkCipher.newCipher().
 *
 * NOTE that any ciphering involved in key exchange (e.g. with RSA) is
 * handled separately.
 *
 * @author David Brownell
 * @author Andreas Sterbenz
 */
final class CipherBox {

    // A CipherBox that implements the identity operation
    final static CipherBox NULL = new CipherBox();

    /* Class and subclass dynamic debugging support */
    private static final Debug debug = Debug.getInstance("ssl");

    // the protocol version this cipher conforms to
    private final ProtocolVersion protocolVersion;

    // cipher object
    private final Cipher cipher;

    /**
     * Cipher blocksize, 0 for stream ciphers
     */
    private int blockSize;

    /**
     * NULL cipherbox. Identity operation, no encryption.
     */
    private CipherBox() {
        this.protocolVersion = ProtocolVersion.DEFAULT;
        this.cipher = null;
    }

    /**
     * Construct a new CipherBox using the cipher transformation.
     *
     * @exception NoSuchAlgorithmException if no appropriate JCE Cipher
     * implementation could be found.
     */
    private CipherBox(ProtocolVersion protocolVersion, BulkCipher bulkCipher,
            SecretKey key,  IvParameterSpec iv, boolean encrypt)
            throws NoSuchAlgorithmException {
        try {
            this.protocolVersion = protocolVersion;
            this.cipher = JsseJce.getCipher(bulkCipher.transformation);
            int mode = encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
            cipher.init(mode, key, iv);
            // do not call getBlockSize until after init()
            // otherwise we would disrupt JCE delayed provider selection
            blockSize = cipher.getBlockSize();
            // some providers implement getBlockSize() incorrectly
            if (blockSize == 1) {
                blockSize = 0;
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
            SecretKey key, IvParameterSpec iv, boolean encrypt)
            throws NoSuchAlgorithmException {
        if (cipher.allowed == false) {
            throw new NoSuchAlgorithmException("Unsupported cipher " + cipher);
        }
        if (cipher == B_NULL) {
            return NULL;
        } else {
            return new CipherBox(version, cipher, key, iv, encrypt);
        }
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
            if (blockSize != 0) {
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
            int newLen = cipher.update(buf, offset, len, buf, offset);
            if (newLen != len) {
                // catch BouncyCastle buffering error
                throw new RuntimeException("Cipher buffering error " +
                    "in JCE provider " + cipher.getProvider().getName());
            }
            return newLen;
        } catch (ShortBufferException e) {
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
    int encrypt(ByteBuffer bb) {

        int len = bb.remaining();

        if (cipher == null) {
            bb.position(bb.limit());
            return len;
        }

        try {
            int pos = bb.position();

            if (blockSize != 0) {
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
                    hd.encodeBuffer(bb, System.out);

                } catch (IOException e) { }
                /*
                 * reset back to beginning
                 */
                bb.position(pos);
            }

            /*
             * Encrypt "in-place".  This does not add its own padding.
             */
            ByteBuffer dup = bb.duplicate();
            int newLen = cipher.update(dup, bb);

            if (bb.position() != dup.position()) {
                throw new RuntimeException("bytebuffer padding error");
            }

            if (newLen != len) {
                // catch BouncyCastle buffering error
                throw new RuntimeException("Cipher buffering error " +
                    "in JCE provider " + cipher.getProvider().getName());
            }
            return newLen;
        } catch (ShortBufferException e) {
            RuntimeException exc = new RuntimeException(e.toString());
            exc.initCause(e);
            throw exc;
        }
    }


    /*
     * Decrypts a block of data, returning the size of the
     * resulting block if padding was required.
     */
    int decrypt(byte[] buf, int offset, int len) throws BadPaddingException {
        if (cipher == null) {
            return len;
        }
        try {
            int newLen = cipher.update(buf, offset, len, buf, offset);
            if (newLen != len) {
                // catch BouncyCastle buffering error
                throw new RuntimeException("Cipher buffering error " +
                    "in JCE provider " + cipher.getProvider().getName());
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
            if (blockSize != 0) {
                newLen = removePadding(buf, offset, newLen,
                             blockSize, protocolVersion);
            }
            return newLen;
        } catch (ShortBufferException e) {
            throw new ArrayIndexOutOfBoundsException(e.toString());
        }
    }


    /*
     * Decrypts a block of data, returning the size of the
     * resulting block if padding was required.  position and limit
     * point to the end of the decrypted/depadded data.  The initial
     * limit and new limit may be different, given we may
     * have stripped off some padding bytes.
     */
    int decrypt(ByteBuffer bb) throws BadPaddingException {

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
            int newLen = cipher.update(dup, bb);
            if (newLen != len) {
                // catch BouncyCastle buffering error
                throw new RuntimeException("Cipher buffering error " +
                    "in JCE provider " + cipher.getProvider().getName());
            }

            if (debug != null && Debug.isOn("plaintext")) {
                bb.position(pos);
                try {
                    HexDumpEncoder hd = new HexDumpEncoder();

                    System.out.println(
                        "Padded plaintext after DECRYPTION:  len = "
                        + newLen);

                    hd.encodeBuffer(bb, System.out);
                } catch (IOException e) { }
            }

            /*
             * Remove the block padding.
             */
            if (blockSize != 0) {
                bb.position(pos);
                newLen = removePadding(bb, blockSize, protocolVersion);
            }
            return newLen;
        } catch (ShortBufferException e) {
            RuntimeException exc = new RuntimeException(e.toString());
            exc.initCause(e);
            throw exc;
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
            int blockSize, ProtocolVersion protocolVersion)
            throws BadPaddingException {
        // last byte is length byte (i.e. actual padding length - 1)
        int padOffset = offset + len - 1;
        int pad = buf[padOffset] & 0x0ff;

        int newlen = len - (pad + 1);
        if (newlen < 0) {
            throw new BadPaddingException("Padding length invalid: " + pad);
        }

        if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
            for (int i = 1; i <= pad; i++) {
                int val = buf[padOffset - i] & 0xff;
                if (val != pad) {
                    throw new BadPaddingException
                                        ("Invalid TLS padding: " + val);
                }
            }
        } else { // SSLv3
            // SSLv3 requires 0 <= length byte < block size
            // some implementations do 1 <= length byte <= block size,
            // so accept that as well
            // v3 does not require any particular value for the other bytes
            if (pad > blockSize) {
                throw new BadPaddingException("Invalid SSLv3 padding: " + pad);
            }
        }
        return newlen;
    }

    /*
     * Position/limit is equal the removed padding.
     */
    private static int removePadding(ByteBuffer bb,
            int blockSize, ProtocolVersion protocolVersion)
            throws BadPaddingException {

        int len = bb.remaining();
        int offset = bb.position();

        // last byte is length byte (i.e. actual padding length - 1)
        int padOffset = offset + len - 1;
        int pad = bb.get(padOffset) & 0x0ff;

        int newlen = len - (pad + 1);
        if (newlen < 0) {
            throw new BadPaddingException("Padding length invalid: " + pad);
        }

        /*
         * We could zero the padding area, but not much useful
         * information there.
         */
        if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
            bb.put(padOffset, (byte)0);         // zero the padding.
            for (int i = 1; i <= pad; i++) {
                int val = bb.get(padOffset - i) & 0xff;
                if (val != pad) {
                    throw new BadPaddingException
                                        ("Invalid TLS padding: " + val);
                }
            }
        } else { // SSLv3
            // SSLv3 requires 0 <= length byte < block size
            // some implementations do 1 <= length byte <= block size,
            // so accept that as well
            // v3 does not require any particular value for the other bytes
            if (pad > blockSize) {
                throw new BadPaddingException("Invalid SSLv3 padding: " + pad);
            }
        }

        /*
         * Reset buffer limit to remove padding.
         */
        bb.position(offset + newlen);
        bb.limit(offset + newlen);

        return newlen;
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
        } catch (GeneralSecurityException e) {
            // swallow for now.
        }
    }

}
