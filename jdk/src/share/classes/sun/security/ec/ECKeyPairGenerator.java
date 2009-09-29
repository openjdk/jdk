/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.ec;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

import sun.security.ec.NamedCurve;
import sun.security.ec.ECParameters;
import sun.security.ec.ECPrivateKeyImpl;
import sun.security.ec.ECPublicKeyImpl;
import sun.security.jca.JCAUtil;

/**
 * EC keypair generator.
 * Standard algorithm, minimum key length is 112 bits, maximum is 571 bits.
 *
 * @since 1.7
 */
public final class ECKeyPairGenerator extends KeyPairGeneratorSpi {

    private static final int KEY_SIZE_MIN = 112; // min bits (see ecc_impl.h)
    private static final int KEY_SIZE_MAX = 571; // max bits (see ecc_impl.h)
    private static final int KEY_SIZE_DEFAULT = 256;

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
        initialize(KEY_SIZE_DEFAULT, null);
    }

    // initialize the generator. See JCA doc
    @Override
    public void initialize(int keySize, SecureRandom random) {

        checkKeySize(keySize);
        this.params = NamedCurve.getECParameterSpec(keySize);
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

        if (params instanceof ECParameterSpec) {
            this.params = ECParameters.getNamedCurve((ECParameterSpec)params);
            if (this.params == null) {
                throw new InvalidAlgorithmParameterException(
                    "Unsupported curve: " + params);
            }
        } else if (params instanceof ECGenParameterSpec) {
            String name = ((ECGenParameterSpec)params).getName();
            this.params = NamedCurve.getECParameterSpec(name);
            if (this.params == null) {
                throw new InvalidAlgorithmParameterException(
                    "Unknown curve name: " + name);
            }
        } else {
            throw new InvalidAlgorithmParameterException(
                "ECParameterSpec or ECGenParameterSpec required for EC");
        }
        this.keySize =
            ((ECParameterSpec)this.params).getCurve().getField().getFieldSize();
        this.random = random;
    }

    // generate the keypair. See JCA doc
    @Override
    public KeyPair generateKeyPair() {

        byte[] encodedParams =
            ECParameters.encodeParameters((ECParameterSpec)params);

        // seed is twice the key size (in bytes) plus 1
        byte[] seed = new byte[(((keySize + 7) >> 3) + 1) * 2];
        if (random == null) {
            random = JCAUtil.getSecureRandom();
        }
        random.nextBytes(seed);

        long[] handles = generateECKeyPair(keySize, encodedParams, seed);

        // The 'params' object supplied above is equivalent to the native one
        // so there is no need to fetch it.

        // handles[0] points to the native private key
        BigInteger s = new BigInteger(1, getEncodedBytes(handles[0]));

        try {
            PrivateKey privateKey =
                new ECPrivateKeyImpl(s, (ECParameterSpec)params);

            // handles[1] points to the native public key
            ECPoint w = ECParameters.decodePoint(getEncodedBytes(handles[1]),
                ((ECParameterSpec)params).getCurve());
            PublicKey publicKey =
                new ECPublicKeyImpl(w, (ECParameterSpec)params);

            return new KeyPair(publicKey, privateKey);

        } catch (Exception e) {
            throw new ProviderException(e);
        }
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

    /*
     * Generates the keypair and returns a 2-element array of handles.
     * The first handle points to the private key, the second to the public key.
     */
    private static native long[] generateECKeyPair(int keySize,
        byte[] encodedParams, byte[] seed);

    /*
     * Extracts the encoded key data using the supplied handle.
     */
    private static native byte[] getEncodedBytes(long handle);
}
