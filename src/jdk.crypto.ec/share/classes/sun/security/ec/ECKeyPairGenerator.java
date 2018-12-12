/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.*;
import java.util.Optional;

import sun.security.jca.JCAUtil;
import sun.security.util.ECUtil;
import sun.security.util.math.*;
import sun.security.ec.point.*;
import static sun.security.util.SecurityProviderConstants.DEF_EC_KEY_SIZE;
import static sun.security.ec.ECOperations.IntermediateValueException;

/**
 * EC keypair generator.
 * Standard algorithm, minimum key length is 112 bits, maximum is 571 bits.
 *
 * @since 1.7
 */
public final class ECKeyPairGenerator extends KeyPairGeneratorSpi {

    private static final int KEY_SIZE_MIN = 112; // min bits (see ecc_impl.h)
    private static final int KEY_SIZE_MAX = 571; // max bits (see ecc_impl.h)

    // used to seed the keypair generator
    private SecureRandom random;

    // size of the key to generate, KEY_SIZE_MIN <= keySize <= KEY_SIZE_MAX
    private int keySize;

    // parameters specified via init, if any
    private AlgorithmParameterSpec params = null;

    /**
     * Constructs a new ECKeyPairGenerator.
     */
    public ECKeyPairGenerator() {
        // initialize to default in case the app does not call initialize()
        initialize(DEF_EC_KEY_SIZE, null);
    }

    // initialize the generator. See JCA doc
    @Override
    public void initialize(int keySize, SecureRandom random) {

        checkKeySize(keySize);
        this.params = ECUtil.getECParameterSpec(null, keySize);
        if (params == null) {
            throw new InvalidParameterException(
                "No EC parameters available for key size " + keySize + " bits");
        }
        this.random = random;
    }

    // second initialize method. See JCA doc
    @Override
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {

        ECParameterSpec ecSpec = null;

        if (params instanceof ECParameterSpec) {
            ECParameterSpec ecParams = (ECParameterSpec) params;
            ecSpec = ECUtil.getECParameterSpec(null, ecParams);
            if (ecSpec == null) {
                throw new InvalidAlgorithmParameterException(
                    "Unsupported curve: " + params);
            }
        } else if (params instanceof ECGenParameterSpec) {
            String name = ((ECGenParameterSpec) params).getName();
            ecSpec = ECUtil.getECParameterSpec(null, name);
            if (ecSpec == null) {
                throw new InvalidAlgorithmParameterException(
                    "Unknown curve name: " + name);
            }
        } else {
            throw new InvalidAlgorithmParameterException(
                "ECParameterSpec or ECGenParameterSpec required for EC");
        }

        // Not all known curves are supported by the native implementation
        ensureCurveIsSupported(ecSpec);
        this.params = ecSpec;

        this.keySize = ecSpec.getCurve().getField().getFieldSize();
        this.random = random;
    }

    private static void ensureCurveIsSupported(ECParameterSpec ecSpec)
        throws InvalidAlgorithmParameterException {

        AlgorithmParameters ecParams = ECUtil.getECParameters(null);
        byte[] encodedParams;
        try {
            ecParams.init(ecSpec);
            encodedParams = ecParams.getEncoded();
        } catch (InvalidParameterSpecException ex) {
            throw new InvalidAlgorithmParameterException(
                "Unsupported curve: " + ecSpec.toString());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        if (!isCurveSupported(encodedParams)) {
            throw new InvalidAlgorithmParameterException(
                "Unsupported curve: " + ecParams.toString());
        }
    }

    // generate the keypair. See JCA doc
    @Override
    public KeyPair generateKeyPair() {

        if (random == null) {
            random = JCAUtil.getSecureRandom();
        }

        try {
            Optional<KeyPair> kp = generateKeyPairImpl(random);
            if (kp.isPresent()) {
                return kp.get();
            }
            return generateKeyPairNative(random);
        } catch (Exception ex) {
            throw new ProviderException(ex);
        }
    }

    private byte[] generatePrivateScalar(SecureRandom random,
        ECOperations ecOps, int seedSize) {
        // Attempt to create the private scalar in a loop that uses new random
        // input each time. The chance of failure is very small assuming the
        // implementation derives the nonce using extra bits
        int numAttempts = 128;
        byte[] seedArr = new byte[seedSize];
        for (int i = 0; i < numAttempts; i++) {
            random.nextBytes(seedArr);
            try {
                return ecOps.seedToScalar(seedArr);
            } catch (IntermediateValueException ex) {
                // try again in the next iteration
            }
        }

        throw new ProviderException("Unable to produce private key after "
                                         + numAttempts + " attempts");
    }

    private Optional<KeyPair> generateKeyPairImpl(SecureRandom random)
        throws InvalidKeyException {

        ECParameterSpec ecParams = (ECParameterSpec) params;

        Optional<ECOperations> opsOpt = ECOperations.forParameters(ecParams);
        if (opsOpt.isEmpty()) {
            return Optional.empty();
        }
        ECOperations ops = opsOpt.get();
        IntegerFieldModuloP field = ops.getField();
        int numBits = ecParams.getOrder().bitLength();
        int seedBits = numBits + 64;
        int seedSize = (seedBits + 7) / 8;
        byte[] privArr = generatePrivateScalar(random, ops, seedSize);

        ECPoint genPoint = ecParams.getGenerator();
        ImmutableIntegerModuloP x = field.getElement(genPoint.getAffineX());
        ImmutableIntegerModuloP y = field.getElement(genPoint.getAffineY());
        AffinePoint affGen = new AffinePoint(x, y);
        Point pub = ops.multiply(affGen, privArr);
        AffinePoint affPub = pub.asAffine();

        PrivateKey privateKey = new ECPrivateKeyImpl(privArr, ecParams);

        ECPoint w = new ECPoint(affPub.getX().asBigInteger(),
            affPub.getY().asBigInteger());
        PublicKey publicKey = new ECPublicKeyImpl(w, ecParams);

        return Optional.of(new KeyPair(publicKey, privateKey));
    }

    private KeyPair generateKeyPairNative(SecureRandom random)
        throws Exception {

        ECParameterSpec ecParams = (ECParameterSpec) params;
        byte[] encodedParams = ECUtil.encodeECParameterSpec(null, ecParams);

        // seed is twice the key size (in bytes) plus 1
        byte[] seed = new byte[(((keySize + 7) >> 3) + 1) * 2];
        random.nextBytes(seed);
        Object[] keyBytes = generateECKeyPair(keySize, encodedParams, seed);

        // The 'params' object supplied above is equivalent to the native
        // one so there is no need to fetch it.
        // keyBytes[0] is the encoding of the native private key
        BigInteger s = new BigInteger(1, (byte[]) keyBytes[0]);

        PrivateKey privateKey = new ECPrivateKeyImpl(s, ecParams);

        // keyBytes[1] is the encoding of the native public key
        byte[] pubKey = (byte[]) keyBytes[1];
        ECPoint w = ECUtil.decodePoint(pubKey, ecParams.getCurve());
        PublicKey publicKey = new ECPublicKeyImpl(w, ecParams);

        return new KeyPair(publicKey, privateKey);
    }

    private void checkKeySize(int keySize) throws InvalidParameterException {
        if (keySize < KEY_SIZE_MIN) {
            throw new InvalidParameterException
                ("Key size must be at least " + KEY_SIZE_MIN + " bits");
        }
        if (keySize > KEY_SIZE_MAX) {
            throw new InvalidParameterException
                ("Key size must be at most " + KEY_SIZE_MAX + " bits");
        }
        this.keySize = keySize;
    }

    /**
     * Checks whether the curve in the encoded parameters is supported by the
     * native implementation. Some curve operations will be performed by the
     * Java implementation, but not all of them. So native support is still
     * required for all curves.
     *
     * @param encodedParams encoded parameters in the same form accepted
     *    by generateECKeyPair
     * @return true if and only if generateECKeyPair will succeed for
     *    the supplied parameters
     */
    private static native boolean isCurveSupported(byte[] encodedParams);

    /*
     * Generates the keypair and returns a 2-element array of encoding bytes.
     * The first one is for the private key, the second for the public key.
     */
    private static native Object[] generateECKeyPair(int keySize,
        byte[] encodedParams, byte[] seed) throws GeneralSecurityException;
}
