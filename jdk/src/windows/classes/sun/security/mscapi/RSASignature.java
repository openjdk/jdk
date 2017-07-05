/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.mscapi;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureSpi;
import java.security.SignatureException;
import java.math.BigInteger;

import sun.security.rsa.RSAKeyFactory;

/**
 * RSA signature implementation. Supports RSA signing using PKCS#1 v1.5 padding.
 *
 * Objects should be instantiated by calling Signature.getInstance() using the
 * following algorithm names:
 *
 *  . "SHA1withRSA"
 *  . "MD5withRSA"
 *  . "MD2withRSA"
 *
 * Note: RSA keys must be at least 512 bits long
 *
 * @since   1.6
 * @author  Stanley Man-Kit Ho
 */
abstract class RSASignature extends java.security.SignatureSpi
{
    // message digest implementation we use
    private final MessageDigest messageDigest;

    // flag indicating whether the digest is reset
    private boolean needsReset;

    // the signing key
    private Key privateKey = null;

    // the verification key
    private Key publicKey = null;


    RSASignature(String digestName) {

        try {
            messageDigest = MessageDigest.getInstance(digestName);

        } catch (NoSuchAlgorithmException e) {
           throw new ProviderException(e);
        }

        needsReset = false;
    }

    public static final class SHA1 extends RSASignature {
        public SHA1() {
            super("SHA1");
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

    /**
     * Initializes this signature object with the specified
     * public key for verification operations.
     *
     * @param publicKey the public key of the identity whose signature is
     * going to be verified.
     *
     * @exception InvalidKeyException if the key is improperly
     * encoded, parameters are missing, and so on.
     */
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

            publicKey = importPublicKey(keyBlob, keyBitLength);

        } else {
            publicKey = (sun.security.mscapi.RSAPublicKey) key;
        }

        if (needsReset) {
            messageDigest.reset();
            needsReset = false;
        }
    }

    /**
     * Initializes this signature object with the specified
     * private key for signing operations.
     *
     * @param privateKey the private key of the identity whose signature
     * will be generated.
     *
     * @exception InvalidKeyException if the key is improperly
     * encoded, parameters are missing, and so on.
     */
    protected void engineInitSign(PrivateKey key)
        throws InvalidKeyException
    {
        // This signature accepts only RSAPrivateKey
        if ((key instanceof sun.security.mscapi.RSAPrivateKey) == false) {
            throw new InvalidKeyException("Key type not supported");
        }
        privateKey = (sun.security.mscapi.RSAPrivateKey) key;

        // Check against the local and global values to make sure
        // the sizes are ok.  Round up to nearest byte.
        RSAKeyFactory.checkKeyLengths(((privateKey.bitLength() + 7) & ~7),
            null, RSAKeyPairGenerator.KEY_SIZE_MIN,
            RSAKeyPairGenerator.KEY_SIZE_MAX);

        if (needsReset) {
            messageDigest.reset();
            needsReset = false;
        }
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

        byte[] hash = messageDigest.digest();
        needsReset = false;

        // Sign hash using MS Crypto APIs

        byte[] result = signHash(hash, hash.length,
            messageDigest.getAlgorithm(), privateKey.getHCryptProvider(),
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
    private native static byte[] signHash(byte[] hash, int hashSize,
        String hashAlgorithm, long hCryptProv, long hCryptKey)
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
        byte[] hash = messageDigest.digest();
        needsReset = false;

        return verifySignedHash(hash, hash.length,
            messageDigest.getAlgorithm(), convertEndianArray(sigBytes),
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
    protected Object engineGetParameter(String param)
        throws InvalidParameterException
    {
        throw new InvalidParameterException("Parameter not supported");
    }

    /**
     * Generates a public-key BLOB from a key's components.
     */
    private native byte[] generatePublicKeyBlob(
        int keyBitLength, byte[] modulus, byte[] publicExponent);

    /**
     * Imports a public-key BLOB.
     */
    private native RSAPublicKey importPublicKey(byte[] keyBlob, int keySize);
}
