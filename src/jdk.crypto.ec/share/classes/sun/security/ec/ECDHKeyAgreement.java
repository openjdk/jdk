/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.math.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Optional;

import javax.crypto.*;
import javax.crypto.spec.*;

import sun.security.util.ArrayUtil;
import sun.security.util.CurveDB;
import sun.security.util.ECUtil;
import sun.security.util.NamedCurve;
import sun.security.util.math.*;
import sun.security.ec.point.*;

/**
 * KeyAgreement implementation for ECDH.
 *
 * @since   1.7
 */
public final class ECDHKeyAgreement extends KeyAgreementSpi {

    // private key, if initialized
    private ECPrivateKey privateKey;

    // public key, non-null between doPhase() & generateSecret() only
    private ECPublicKey publicKey;

    // length of the secret to be derived
    private int secretLen;

    /**
     * Constructs a new ECDHKeyAgreement.
     */
    public ECDHKeyAgreement() {
    }

    // see JCE spec
    @Override
    protected void engineInit(Key key, SecureRandom random)
            throws InvalidKeyException {
        if (!(key instanceof PrivateKey)) {
            throw new InvalidKeyException
                        ("Key must be instance of PrivateKey");
        }
        privateKey = (ECPrivateKey) ECKeyFactory.toECKey(key);
        publicKey = null;
    }

    // see JCE spec
    @Override
    protected void engineInit(Key key, AlgorithmParameterSpec params,
            SecureRandom random) throws InvalidKeyException,
            InvalidAlgorithmParameterException {
        if (params != null) {
            throw new InvalidAlgorithmParameterException
                        ("Parameters not supported");
        }
        engineInit(key, random);
    }

    // see JCE spec
    @Override
    protected Key engineDoPhase(Key key, boolean lastPhase)
            throws InvalidKeyException, IllegalStateException {
        if (privateKey == null) {
            throw new IllegalStateException("Not initialized");
        }
        if (publicKey != null) {
            throw new IllegalStateException("Phase already executed");
        }
        if (!lastPhase) {
            throw new IllegalStateException
                ("Only two party agreement supported, lastPhase must be true");
        }
        if (!(key instanceof ECPublicKey)) {
            throw new InvalidKeyException
                ("Key must be a PublicKey with algorithm EC");
        }

        this.publicKey = (ECPublicKey) key;

        ECParameterSpec params = publicKey.getParams();
        int keyLenBits = params.getCurve().getField().getFieldSize();
        secretLen = (keyLenBits + 7) >> 3;

        return null;
    }

    private static void validateCoordinate(BigInteger c, BigInteger mod) {
        if (c.compareTo(BigInteger.ZERO) < 0) {
            throw new ProviderException("invalid coordinate");
        }

        if (c.compareTo(mod) >= 0) {
            throw new ProviderException("invalid coordinate");
        }
    }

    /*
     * Check whether a public key is valid. Throw ProviderException
     * if it is not valid or could not be validated.
     */
    private static void validate(ECOperations ops, ECPublicKey key) {

        // ensure that integers are in proper range
        BigInteger x = key.getW().getAffineX();
        BigInteger y = key.getW().getAffineY();

        BigInteger p = ops.getField().getSize();
        validateCoordinate(x, p);
        validateCoordinate(y, p);

        // ensure the point is on the curve
        EllipticCurve curve = key.getParams().getCurve();
        BigInteger rhs = x.modPow(BigInteger.valueOf(3), p).add(curve.getA()
            .multiply(x)).add(curve.getB()).mod(p);
        BigInteger lhs = y.modPow(BigInteger.valueOf(2), p).mod(p);
        if (!rhs.equals(lhs)) {
            throw new ProviderException("point is not on curve");
        }

        // check the order of the point
        ImmutableIntegerModuloP xElem = ops.getField().getElement(x);
        ImmutableIntegerModuloP yElem = ops.getField().getElement(y);
        AffinePoint affP = new AffinePoint(xElem, yElem);
        byte[] order = key.getParams().getOrder().toByteArray();
        ArrayUtil.reverse(order);
        Point product = ops.multiply(affP, order);
        if (!ops.isNeutral(product)) {
            throw new ProviderException("point has incorrect order");
        }

    }

    // see JCE spec
    @Override
    protected byte[] engineGenerateSecret() throws IllegalStateException {
        if ((privateKey == null) || (publicKey == null)) {
            throw new IllegalStateException("Not initialized correctly");
        }
        byte[] result;
        Optional<byte[]> resultOpt = deriveKeyImpl(privateKey, publicKey);
        if (resultOpt.isPresent()) {
            result = resultOpt.get();
        } else {
            if (SunEC.isNativeDisabled()) {
                NamedCurve privNC = CurveDB.lookup(privateKey.getParams());
                NamedCurve pubNC = CurveDB.lookup(publicKey.getParams());
                throw new IllegalStateException(
                        new InvalidAlgorithmParameterException("Legacy SunEC " +
                                "curve disabled, one or both keys:  " +
                                "Private: " + ((privNC != null) ?
                                privNC.toString() : " unknown") +
                                ", PublicKey:" + ((pubNC != null) ?
                                pubNC.toString() : " unknown")));
            }
            result = deriveKeyNative(privateKey, publicKey);
        }
        publicKey = null;
        return result;
    }

    // see JCE spec
    @Override
    protected int engineGenerateSecret(byte[] sharedSecret, int
            offset) throws IllegalStateException, ShortBufferException {
        if (secretLen > sharedSecret.length - offset) {
            throw new ShortBufferException("Need " + secretLen
                + " bytes, only " + (sharedSecret.length - offset)
                + " available");
        }
        byte[] secret = engineGenerateSecret();
        System.arraycopy(secret, 0, sharedSecret, offset, secret.length);
        return secret.length;
    }

    // see JCE spec
    @Override
    protected SecretKey engineGenerateSecret(String algorithm)
            throws IllegalStateException, NoSuchAlgorithmException,
            InvalidKeyException {
        if (algorithm == null) {
            throw new NoSuchAlgorithmException("Algorithm must not be null");
        }
        if (!(algorithm.equals("TlsPremasterSecret"))) {
            throw new NoSuchAlgorithmException
                ("Only supported for algorithm TlsPremasterSecret");
        }
        return new SecretKeySpec(engineGenerateSecret(), "TlsPremasterSecret");
    }

    private static
    Optional<byte[]> deriveKeyImpl(ECPrivateKey priv, ECPublicKey pubKey) {

        ECParameterSpec ecSpec = priv.getParams();
        EllipticCurve curve = ecSpec.getCurve();
        Optional<ECOperations> opsOpt = ECOperations.forParameters(ecSpec);
        if (opsOpt.isEmpty()) {
            return Optional.empty();
        }
        ECOperations ops = opsOpt.get();
        if (! (priv instanceof ECPrivateKeyImpl)) {
            return Optional.empty();
        }
        ECPrivateKeyImpl privImpl = (ECPrivateKeyImpl) priv;
        byte[] sArr = privImpl.getArrayS();

        // to match the native implementation, validate the public key here
        // and throw ProviderException if it is invalid
        validate(ops, pubKey);

        IntegerFieldModuloP field = ops.getField();
        // convert s array into field element and multiply by the cofactor
        MutableIntegerModuloP scalar = field.getElement(sArr).mutable();
        SmallValue cofactor =
            field.getSmallValue(priv.getParams().getCofactor());
        scalar.setProduct(cofactor);
        int keySize = (curve.getField().getFieldSize() + 7) / 8;
        byte[] privArr = scalar.asByteArray(keySize);

        ImmutableIntegerModuloP x =
            field.getElement(pubKey.getW().getAffineX());
        ImmutableIntegerModuloP y =
            field.getElement(pubKey.getW().getAffineY());
        AffinePoint affPub = new AffinePoint(x, y);
        Point product = ops.multiply(affPub, privArr);
        if (ops.isNeutral(product)) {
            throw new ProviderException("Product is zero");
        }
        AffinePoint affProduct = product.asAffine();

        byte[] result = affProduct.getX().asByteArray(keySize);
        ArrayUtil.reverse(result);

        return Optional.of(result);
    }

    private static
    byte[] deriveKeyNative(ECPrivateKey privateKey, ECPublicKey publicKey) {

        ECParameterSpec params = privateKey.getParams();
        byte[] s = privateKey.getS().toByteArray();
        byte[] encodedParams =                   // DER OID
            ECUtil.encodeECParameterSpec(null, params);

        byte[] publicValue;
        if (publicKey instanceof ECPublicKeyImpl) {
            ECPublicKeyImpl ecPub = (ECPublicKeyImpl) publicKey;
            publicValue = ecPub.getEncodedPublicValue();
        } else { // instanceof ECPublicKey
            publicValue =
                ECUtil.encodePoint(publicKey.getW(), params.getCurve());
        }

        try {
            return deriveKey(s, publicValue, encodedParams);

        } catch (GeneralSecurityException e) {
            throw new ProviderException("Could not derive key", e);
        }
    }


    /**
     * Generates a secret key using the public and private keys.
     *
     * @param s the private key's S value.
     * @param w the public key's W point (in uncompressed form).
     * @param encodedParams the curve's DER encoded object identifier.
     *
     * @return byte[] the secret key.
     */
    private static native byte[] deriveKey(byte[] s, byte[] w,
        byte[] encodedParams) throws GeneralSecurityException;
}
