/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ec;

import java.nio.ByteBuffer;
import java.math.BigInteger;

import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

import sun.security.jca.JCAUtil;
import sun.security.util.*;

/**
 * ECDSA signature implementation. This class currently supports the
 * following algorithm names:
 *
 *   . "NONEwithECDSA"
 *   . "SHA1withECDSA"
 *   . "SHA224withECDSA"
 *   . "SHA256withECDSA"
 *   . "SHA384withECDSA"
 *   . "SHA512withECDSA"
 *
 * @since   1.7
 */
abstract class ECDSASignature extends SignatureSpi {

    // message digest implementation we use
    private final MessageDigest messageDigest;

    // supplied entropy
    private SecureRandom random;

    // flag indicating whether the digest has been reset
    private boolean needsReset;

    // private key, if initialized for signing
    private ECPrivateKey privateKey;

    // public key, if initialized for verifying
    private ECPublicKey publicKey;

    /**
     * Constructs a new ECDSASignature. Used by Raw subclass.
     *
     * @exception ProviderException if the native ECC library is unavailable.
     */
    ECDSASignature() {
        messageDigest = null;
    }

    /**
     * Constructs a new ECDSASignature. Used by subclasses.
     */
    ECDSASignature(String digestName) {
        try {
            messageDigest = MessageDigest.getInstance(digestName);
        } catch (NoSuchAlgorithmException e) {
            throw new ProviderException(e);
        }
        needsReset = false;
    }

    // Nested class for NONEwithECDSA signatures
    public static final class Raw extends ECDSASignature {

        // the longest supported digest is 512 bits (SHA-512)
        private static final int RAW_ECDSA_MAX = 64;

        private final byte[] precomputedDigest;
        private int offset = 0;

        public Raw() {
            precomputedDigest = new byte[RAW_ECDSA_MAX];
        }

        // Stores the precomputed message digest value.
        @Override
        protected void engineUpdate(byte b) throws SignatureException {
            if (offset >= precomputedDigest.length) {
                offset = RAW_ECDSA_MAX + 1;
                return;
            }
            precomputedDigest[offset++] = b;
        }

        // Stores the precomputed message digest value.
        @Override
        protected void engineUpdate(byte[] b, int off, int len)
                throws SignatureException {
            if (offset >= precomputedDigest.length) {
                offset = RAW_ECDSA_MAX + 1;
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
            if (offset + len >= precomputedDigest.length) {
                offset = RAW_ECDSA_MAX + 1;
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
            if (offset > RAW_ECDSA_MAX) {
                throw new SignatureException("Message digest is too long");

            }
            byte[] result = new byte[offset];
            System.arraycopy(precomputedDigest, 0, result, 0, offset);
            offset = 0;

            return result;
        }
    }

    // Nested class for SHA1withECDSA signatures
    public static final class SHA1 extends ECDSASignature {
        public SHA1() {
            super("SHA1");
        }
    }

    // Nested class for SHA224withECDSA signatures
    public static final class SHA224 extends ECDSASignature {
        public SHA224() {
           super("SHA-224");
        }
    }

    // Nested class for SHA256withECDSA signatures
    public static final class SHA256 extends ECDSASignature {
        public SHA256() {
            super("SHA-256");
        }
    }

    // Nested class for SHA384withECDSA signatures
    public static final class SHA384 extends ECDSASignature {
        public SHA384() {
            super("SHA-384");
        }
    }

    // Nested class for SHA512withECDSA signatures
    public static final class SHA512 extends ECDSASignature {
        public SHA512() {
            super("SHA-512");
        }
    }

    // initialize for verification. See JCA doc
    @Override
    protected void engineInitVerify(PublicKey publicKey)
            throws InvalidKeyException {
        this.publicKey = (ECPublicKey) ECKeyFactory.toECKey(publicKey);

        // Should check that the supplied key is appropriate for signature
        // algorithm (e.g. P-256 for SHA256withECDSA)
        this.privateKey = null;
        resetDigest();
    }

    // initialize for signing. See JCA doc
    @Override
    protected void engineInitSign(PrivateKey privateKey)
            throws InvalidKeyException {
        engineInitSign(privateKey, null);
    }

    // initialize for signing. See JCA doc
    @Override
    protected void engineInitSign(PrivateKey privateKey, SecureRandom random)
            throws InvalidKeyException {
        this.privateKey = (ECPrivateKey) ECKeyFactory.toECKey(privateKey);

        // Should check that the supplied key is appropriate for signature
        // algorithm (e.g. P-256 for SHA256withECDSA)
        this.publicKey = null;
        this.random = random;
        resetDigest();
    }

    /**
     * Resets the message digest if needed.
     */
    protected void resetDigest() {
        if (needsReset) {
            if (messageDigest != null) {
                messageDigest.reset();
            }
            needsReset = false;
        }
    }

    /**
     * Returns the message digest value.
     */
    protected byte[] getDigestValue() throws SignatureException {
        needsReset = false;
        return messageDigest.digest();
    }

    // update the signature with the plaintext data. See JCA doc
    @Override
    protected void engineUpdate(byte b) throws SignatureException {
        messageDigest.update(b);
        needsReset = true;
    }

    // update the signature with the plaintext data. See JCA doc
    @Override
    protected void engineUpdate(byte[] b, int off, int len)
            throws SignatureException {
        messageDigest.update(b, off, len);
        needsReset = true;
    }

    // update the signature with the plaintext data. See JCA doc
    @Override
    protected void engineUpdate(ByteBuffer byteBuffer) {
        int len = byteBuffer.remaining();
        if (len <= 0) {
            return;
        }

        messageDigest.update(byteBuffer);
        needsReset = true;
    }

    // sign the data and return the signature. See JCA doc
    @Override
    protected byte[] engineSign() throws SignatureException {
        byte[] s = privateKey.getS().toByteArray();
        ECParameterSpec params = privateKey.getParams();
        // DER OID
        byte[] encodedParams = ECUtil.encodeECParameterSpec(null, params);
        int keySize = params.getCurve().getField().getFieldSize();

        // seed is twice the key size (in bytes) plus 1
        byte[] seed = new byte[(((keySize + 7) >> 3) + 1) * 2];
        if (random == null) {
            random = JCAUtil.getSecureRandom();
        }
        random.nextBytes(seed);

        try {

            return encodeSignature(
                signDigest(getDigestValue(), s, encodedParams, seed));

        } catch (GeneralSecurityException e) {
            throw new SignatureException("Could not sign data", e);
        }
    }

    // verify the data and return the result. See JCA doc
    @Override
    protected boolean engineVerify(byte[] signature) throws SignatureException {

        byte[] w;
        ECParameterSpec params = publicKey.getParams();
        // DER OID
        byte[] encodedParams = ECUtil.encodeECParameterSpec(null, params);

        if (publicKey instanceof ECPublicKeyImpl) {
            w = ((ECPublicKeyImpl)publicKey).getEncodedPublicValue();
        } else { // instanceof ECPublicKey
            w = ECUtil.encodePoint(publicKey.getW(), params.getCurve());
        }

        try {

            return verifySignedDigest(
                decodeSignature(signature), getDigestValue(), w, encodedParams);

        } catch (GeneralSecurityException e) {
            throw new SignatureException("Could not verify signature", e);
        }
    }

    // set parameter, not supported. See JCA doc
    @Override
    @Deprecated
    protected void engineSetParameter(String param, Object value)
            throws InvalidParameterException {
        throw new UnsupportedOperationException("setParameter() not supported");
    }

    // get parameter, not supported. See JCA doc
    @Override
    @Deprecated
    protected Object engineGetParameter(String param)
            throws InvalidParameterException {
        throw new UnsupportedOperationException("getParameter() not supported");
    }

    // Convert the concatenation of R and S into their DER encoding
    private byte[] encodeSignature(byte[] signature) throws SignatureException {

        try {

            int n = signature.length >> 1;
            byte[] bytes = new byte[n];
            System.arraycopy(signature, 0, bytes, 0, n);
            BigInteger r = new BigInteger(1, bytes);
            System.arraycopy(signature, n, bytes, 0, n);
            BigInteger s = new BigInteger(1, bytes);

            DerOutputStream out = new DerOutputStream(signature.length + 10);
            out.putInteger(r);
            out.putInteger(s);
            DerValue result =
                new DerValue(DerValue.tag_Sequence, out.toByteArray());

            return result.toByteArray();

        } catch (Exception e) {
            throw new SignatureException("Could not encode signature", e);
        }
    }

    // Convert the DER encoding of R and S into a concatenation of R and S
    private byte[] decodeSignature(byte[] signature) throws SignatureException {

        try {
            DerInputStream in = new DerInputStream(signature);
            DerValue[] values = in.getSequence(2);
            BigInteger r = values[0].getPositiveBigInteger();
            BigInteger s = values[1].getPositiveBigInteger();
            // trim leading zeroes
            byte[] rBytes = trimZeroes(r.toByteArray());
            byte[] sBytes = trimZeroes(s.toByteArray());
            int k = Math.max(rBytes.length, sBytes.length);
            // r and s each occupy half the array
            byte[] result = new byte[k << 1];
            System.arraycopy(rBytes, 0, result, k - rBytes.length,
                rBytes.length);
            System.arraycopy(sBytes, 0, result, result.length - sBytes.length,
                sBytes.length);
            return result;

        } catch (Exception e) {
            throw new SignatureException("Could not decode signature", e);
        }
    }

    // trim leading (most significant) zeroes from the result
    private static byte[] trimZeroes(byte[] b) {
        int i = 0;
        while ((i < b.length - 1) && (b[i] == 0)) {
            i++;
        }
        if (i == 0) {
            return b;
        }
        byte[] t = new byte[b.length - i];
        System.arraycopy(b, i, t, 0, t.length);
        return t;
    }

    /**
     * Signs the digest using the private key.
     *
     * @param digest the digest to be signed.
     * @param s the private key's S value.
     * @param encodedParams the curve's DER encoded object identifier.
     * @param seed the random seed.
     *
     * @return byte[] the signature.
     */
    private static native byte[] signDigest(byte[] digest, byte[] s,
        byte[] encodedParams, byte[] seed) throws GeneralSecurityException;

    /**
     * Verifies the signed digest using the public key.
     *
     * @param signedDigest the signature to be verified. It is encoded
     *        as a concatenation of the key's R and S values.
     * @param digest the digest to be used.
     * @param w the public key's W point (in uncompressed form).
     * @param encodedParams the curve's DER encoded object identifier.
     *
     * @return boolean true if the signature is successfully verified.
     */
    private static native boolean verifySignedDigest(byte[] signature,
        byte[] digest, byte[] w, byte[] encodedParams)
            throws GeneralSecurityException;
}
