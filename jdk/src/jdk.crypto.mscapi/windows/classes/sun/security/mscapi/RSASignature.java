/*
 * Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.mscapi;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.MessageDigest;
import java.security.SignatureException;
import java.math.BigInteger;

import sun.security.rsa.RSAKeyFactory;

/**
 * RSA signature implementation. Supports RSA signing using PKCS#1 v1.5 padding.
 *
 * Objects should be instantiated by calling Signature.getInstance() using the
 * following algorithm names:
 *
 *  . "NONEwithRSA"
 *  . "SHA1withRSA"
 *  . "SHA256withRSA"
 *  . "SHA384withRSA"
 *  . "SHA512withRSA"
 *  . "MD5withRSA"
 *  . "MD2withRSA"
 *
 * NOTE: RSA keys must be at least 512 bits long.
 *
 * NOTE: NONEwithRSA must be supplied with a pre-computed message digest.
 *       Only the following digest algorithms are supported: MD5, SHA-1,
 *       SHA-256, SHA-384, SHA-512 and a special-purpose digest
 *       algorithm which is a concatenation of SHA-1 and MD5 digests.
 *
 * @since   1.6
 * @author  Stanley Man-Kit Ho
 */
abstract class RSASignature extends java.security.SignatureSpi
{
    // message digest implementation we use
    private final MessageDigest messageDigest;

    // message digest name
    private String messageDigestAlgorithm;

    // flag indicating whether the digest has been reset
    private boolean needsReset;

    // the signing key
    private Key privateKey = null;

    // the verification key
    private Key publicKey = null;

    /**
     * Constructs a new RSASignature. Used by Raw subclass.
     */
    RSASignature() {
        messageDigest = null;
        messageDigestAlgorithm = null;
    }

    /**
     * Constructs a new RSASignature. Used by subclasses.
     */
    RSASignature(String digestName) {

        try {
            messageDigest = MessageDigest.getInstance(digestName);
            // Get the digest's canonical name
            messageDigestAlgorithm = messageDigest.getAlgorithm();

        } catch (NoSuchAlgorithmException e) {
           throw new ProviderException(e);
        }

        needsReset = false;
    }

    // Nested class for NONEwithRSA signatures
    public static final class Raw extends RSASignature {

        // the longest supported digest is 512 bits (SHA-512)
        private static final int RAW_RSA_MAX = 64;

        private final byte[] precomputedDigest;
        private int offset = 0;

        public Raw() {
            precomputedDigest = new byte[RAW_RSA_MAX];
        }

        // Stores the precomputed message digest value.
        @Override
        protected void engineUpdate(byte b) throws SignatureException {
            if (offset >= precomputedDigest.length) {
                offset = RAW_RSA_MAX + 1;
                return;
            }
            precomputedDigest[offset++] = b;
        }

        // Stores the precomputed message digest value.
        @Override
        protected void engineUpdate(byte[] b, int off, int len)
                throws SignatureException {
            if (offset + len > precomputedDigest.length) {
                offset = RAW_RSA_MAX + 1;
                return;
            }
            System.arraycopy(b, off, precomputedDigest, offset, len);
            offset += len;
        }

        // Stores the precomputed message digest value.
        @Override
        protected void engineUpdate(ByteBuffer byteBuffer) {
            int len = byteBuffer.remaining();
            if (len <= 0) {
                return;
            }
            if (offset + len > precomputedDigest.length) {
                offset = RAW_RSA_MAX + 1;
                return;
            }
            byteBuffer.get(precomputedDigest, offset, len);
            offset += len;
        }

        @Override
        protected void resetDigest(){
            offset = 0;
        }

        // Returns the precomputed message digest value.
        @Override
        protected byte[] getDigestValue() throws SignatureException {
            if (offset > RAW_RSA_MAX) {
                throw new SignatureException("Message digest is too long");
            }

            // Determine the digest algorithm from the digest length
            if (offset == 20) {
                setDigestName("SHA1");
            } else if (offset == 36) {
                setDigestName("SHA1+MD5");
            } else if (offset == 32) {
                setDigestName("SHA-256");
            } else if (offset == 48) {
                setDigestName("SHA-384");
            } else if (offset == 64) {
                setDigestName("SHA-512");
            } else if (offset == 16) {
                setDigestName("MD5");
            } else {
                throw new SignatureException(
                    "Message digest length is not supported");
            }

            byte[] result = new byte[offset];
            System.arraycopy(precomputedDigest, 0, result, 0, offset);
            offset = 0;

            return result;
        }
    }

    public static final class SHA1 extends RSASignature {
        public SHA1() {
            super("SHA1");
        }
    }

    public static final class SHA256 extends RSASignature {
        public SHA256() {
            super("SHA-256");
        }
    }

    public static final class SHA384 extends RSASignature {
        public SHA384() {
            super("SHA-384");
        }
    }

    public static final class SHA512 extends RSASignature {
        public SHA512() {
            super("SHA-512");
        }
    }

    public static final class MD5 extends RSASignature {
        public MD5() {
            super("MD5");
        }
    }

    public static final class MD2 extends RSASignature {
        public MD2() {
            super("MD2");
        }
    }

    // initialize for signing. See JCA doc
    protected void engineInitVerify(PublicKey key)
        throws InvalidKeyException
    {
        // This signature accepts only RSAPublicKey
        if ((key instanceof java.security.interfaces.RSAPublicKey) == false) {
            throw new InvalidKeyException("Key type not supported");
        }

        java.security.interfaces.RSAPublicKey rsaKey =
            (java.security.interfaces.RSAPublicKey) key;

        if ((key instanceof sun.security.mscapi.RSAPublicKey) == false) {

            // convert key to MSCAPI format

            BigInteger modulus = rsaKey.getModulus();
            BigInteger exponent =  rsaKey.getPublicExponent();

            // Check against the local and global values to make sure
            // the sizes are ok.  Round up to the nearest byte.
            RSAKeyFactory.checkKeyLengths(((modulus.bitLength() + 7) & ~7),
                exponent, -1, RSAKeyPairGenerator.KEY_SIZE_MAX);

            byte[] modulusBytes = modulus.toByteArray();
            byte[] exponentBytes = exponent.toByteArray();

            // Adjust key length due to sign bit
            int keyBitLength = (modulusBytes[0] == 0)
                ? (modulusBytes.length - 1) * 8
                : modulusBytes.length * 8;

            byte[] keyBlob = generatePublicKeyBlob(
                keyBitLength, modulusBytes, exponentBytes);

            try {
                publicKey = importPublicKey(keyBlob, keyBitLength);

            } catch (KeyStoreException e) {
                throw new InvalidKeyException(e);
            }

        } else {
            publicKey = (sun.security.mscapi.RSAPublicKey) key;
        }

        this.privateKey = null;
        resetDigest();
    }

    // initialize for signing. See JCA doc
    protected void engineInitSign(PrivateKey key) throws InvalidKeyException
    {
        // This signature accepts only RSAPrivateKey
        if ((key instanceof sun.security.mscapi.RSAPrivateKey) == false) {
            throw new InvalidKeyException("Key type not supported");
        }
        privateKey = (sun.security.mscapi.RSAPrivateKey) key;

        // Check against the local and global values to make sure
        // the sizes are ok.  Round up to nearest byte.
        RSAKeyFactory.checkKeyLengths(((privateKey.length() + 7) & ~7),
            null, RSAKeyPairGenerator.KEY_SIZE_MIN,
            RSAKeyPairGenerator.KEY_SIZE_MAX);

        this.publicKey = null;
        resetDigest();
    }

    /**
     * Resets the message digest if needed.
     */
    protected void resetDigest() {
        if (needsReset) {
            messageDigest.reset();
            needsReset = false;
        }
    }

    protected byte[] getDigestValue() throws SignatureException {
        needsReset = false;
        return messageDigest.digest();
    }

    protected void setDigestName(String name) {
        messageDigestAlgorithm = name;
    }

    /**
     * Updates the data to be signed or verified
     * using the specified byte.
     *
     * @param b the byte to use for the update.
     *
     * @exception SignatureException if the engine is not initialized
     * properly.
     */
    protected void engineUpdate(byte b) throws SignatureException
    {
        messageDigest.update(b);
        needsReset = true;
    }

    /**
     * Updates the data to be signed or verified, using the
     * specified array of bytes, starting at the specified offset.
     *
     * @param b the array of bytes
     * @param off the offset to start from in the array of bytes
     * @param len the number of bytes to use, starting at offset
     *
     * @exception SignatureException if the engine is not initialized
     * properly
     */
    protected void engineUpdate(byte[] b, int off, int len)
        throws SignatureException
    {
        messageDigest.update(b, off, len);
        needsReset = true;
    }

    /**
     * Updates the data to be signed or verified, using the
     * specified ByteBuffer.
     *
     * @param input the ByteBuffer
     */
    protected void engineUpdate(ByteBuffer input)
    {
        messageDigest.update(input);
        needsReset = true;
    }

    /**
     * Returns the signature bytes of all the data
     * updated so far.
     * The format of the signature depends on the underlying
     * signature scheme.
     *
     * @return the signature bytes of the signing operation's result.
     *
     * @exception SignatureException if the engine is not
     * initialized properly or if this signature algorithm is unable to
     * process the input data provided.
     */
    protected byte[] engineSign() throws SignatureException {

        byte[] hash = getDigestValue();

        // Omit the hash OID when generating a Raw signature
        boolean noHashOID = this instanceof Raw;

        // Sign hash using MS Crypto APIs

        byte[] result = signHash(noHashOID, hash, hash.length,
            messageDigestAlgorithm, privateKey.getHCryptProvider(),
            privateKey.getHCryptKey());

        // Convert signature array from little endian to big endian
        return convertEndianArray(result);
    }

    /**
     * Convert array from big endian to little endian, or vice versa.
     */
    private byte[] convertEndianArray(byte[] byteArray)
    {
        if (byteArray == null || byteArray.length == 0)
            return byteArray;

        byte [] retval = new byte[byteArray.length];

        // make it big endian
        for (int i=0;i < byteArray.length;i++)
            retval[i] = byteArray[byteArray.length - i - 1];

        return retval;
    }

    /**
     * Sign hash using Microsoft Crypto API with HCRYPTKEY.
     * The returned data is in little-endian.
     */
    private native static byte[] signHash(boolean noHashOID, byte[] hash,
        int hashSize, String hashAlgorithm, long hCryptProv, long hCryptKey)
            throws SignatureException;

    /**
     * Verify a signed hash using Microsoft Crypto API with HCRYPTKEY.
     */
    private native static boolean verifySignedHash(byte[] hash, int hashSize,
        String hashAlgorithm, byte[] signature, int signatureSize,
        long hCryptProv, long hCryptKey) throws SignatureException;

    /**
     * Verifies the passed-in signature.
     *
     * @param sigBytes the signature bytes to be verified.
     *
     * @return true if the signature was verified, false if not.
     *
     * @exception SignatureException if the engine is not
     * initialized properly, the passed-in signature is improperly
     * encoded or of the wrong type, if this signature algorithm is unable to
     * process the input data provided, etc.
     */
    protected boolean engineVerify(byte[] sigBytes)
        throws SignatureException
    {
        byte[] hash = getDigestValue();

        return verifySignedHash(hash, hash.length,
            messageDigestAlgorithm, convertEndianArray(sigBytes),
            sigBytes.length, publicKey.getHCryptProvider(),
            publicKey.getHCryptKey());
    }

    /**
     * Sets the specified algorithm parameter to the specified
     * value. This method supplies a general-purpose mechanism through
     * which it is possible to set the various parameters of this object.
     * A parameter may be any settable parameter for the algorithm, such as
     * a parameter size, or a source of random bits for signature generation
     * (if appropriate), or an indication of whether or not to perform
     * a specific but optional computation. A uniform algorithm-specific
     * naming scheme for each parameter is desirable but left unspecified
     * at this time.
     *
     * @param param the string identifier of the parameter.
     *
     * @param value the parameter value.
     *
     * @exception InvalidParameterException if <code>param</code> is an
     * invalid parameter for this signature algorithm engine,
     * the parameter is already set
     * and cannot be set again, a security exception occurs, and so on.
     *
     * @deprecated Replaced by {@link
     * #engineSetParameter(java.security.spec.AlgorithmParameterSpec)
     * engineSetParameter}.
     */
    @Deprecated
    protected void engineSetParameter(String param, Object value)
        throws InvalidParameterException
    {
        throw new InvalidParameterException("Parameter not supported");
    }


    /**
     * Gets the value of the specified algorithm parameter.
     * This method supplies a general-purpose mechanism through which it
     * is possible to get the various parameters of this object. A parameter
     * may be any settable parameter for the algorithm, such as a parameter
     * size, or  a source of random bits for signature generation (if
     * appropriate), or an indication of whether or not to perform a
     * specific but optional computation. A uniform algorithm-specific
     * naming scheme for each parameter is desirable but left unspecified
     * at this time.
     *
     * @param param the string name of the parameter.
     *
     * @return the object that represents the parameter value, or null if
     * there is none.
     *
     * @exception InvalidParameterException if <code>param</code> is an
     * invalid parameter for this engine, or another exception occurs while
     * trying to get this parameter.
     *
     * @deprecated
     */
    @Deprecated
    protected Object engineGetParameter(String param)
        throws InvalidParameterException
    {
        throw new InvalidParameterException("Parameter not supported");
    }

    /**
     * Generates a public-key BLOB from a key's components.
     */
    // used by RSACipher
    static native byte[] generatePublicKeyBlob(
        int keyBitLength, byte[] modulus, byte[] publicExponent)
            throws InvalidKeyException;

    /**
     * Imports a public-key BLOB.
     */
    // used by RSACipher
    static native RSAPublicKey importPublicKey(byte[] keyBlob, int keySize)
        throws KeyStoreException;
}
