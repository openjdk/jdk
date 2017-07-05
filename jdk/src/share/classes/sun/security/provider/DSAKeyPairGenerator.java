/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.math.BigInteger;

import java.security.*;
import java.security.SecureRandom;
import java.security.interfaces.DSAParams;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.DSAParameterSpec;

import sun.security.jca.JCAUtil;

/**
 * This class generates DSA key parameters and public/private key
 * pairs according to the DSS standard NIST FIPS 186. It uses the
 * updated version of SHA, SHA-1 as described in FIPS 180-1.
 *
 * @author Benjamin Renaud
 * @author Andreas Sterbenz
 *
 */
public class DSAKeyPairGenerator extends KeyPairGenerator
implements java.security.interfaces.DSAKeyPairGenerator {

    /* The modulus length */
    private int modlen;

    /* whether to force new parameters to be generated for each KeyPair */
    private boolean forceNewParameters;

    /* preset algorithm parameters. */
    private DSAParameterSpec params;

    /* The source of random bits to use */
    private SecureRandom random;

    public DSAKeyPairGenerator() {
        super("DSA");
        initialize(1024, null);
    }

    private static void checkStrength(int strength) {
        if ((strength < 512) || (strength > 1024) || (strength % 64 != 0)) {
            throw new InvalidParameterException
                ("Modulus size must range from 512 to 1024 "
                 + "and be a multiple of 64");
        }
    }

    public void initialize(int modlen, SecureRandom random) {
        checkStrength(modlen);
        this.random = random;
        this.modlen = modlen;
        this.params = null;
        this.forceNewParameters = false;
    }

    /**
     * Initializes the DSA key pair generator. If <code>genParams</code>
     * is false, a set of pre-computed parameters is used.
     */
    public void initialize(int modlen, boolean genParams, SecureRandom random) {
        checkStrength(modlen);
        if (genParams) {
            params = null;
        } else {
            params = ParameterCache.getCachedDSAParameterSpec(modlen);
            if (params == null) {
                throw new InvalidParameterException
                    ("No precomputed parameters for requested modulus size "
                     + "available");
            }
        }
        this.modlen = modlen;
        this.random = random;
        this.forceNewParameters = genParams;
    }

    /**
     * Initializes the DSA object using a DSA parameter object.
     *
     * @param params a fully initialized DSA parameter object.
     */
    public void initialize(DSAParams params, SecureRandom random) {
        if (params == null) {
            throw new InvalidParameterException("Params must not be null");
        }
        DSAParameterSpec spec = new DSAParameterSpec
                                (params.getP(), params.getQ(), params.getG());
        initialize0(spec, random);
    }

    /**
     * Initializes the DSA object using a parameter object.
     *
     * @param params the parameter set to be used to generate
     * the keys.
     * @param random the source of randomness for this generator.
     *
     * @exception InvalidAlgorithmParameterException if the given parameters
     * are inappropriate for this key pair generator
     */
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {
        if (!(params instanceof DSAParameterSpec)) {
            throw new InvalidAlgorithmParameterException
                ("Inappropriate parameter");
        }
        initialize0((DSAParameterSpec)params, random);
    }

    private void initialize0(DSAParameterSpec params, SecureRandom random) {
        int modlen = params.getP().bitLength();
        checkStrength(modlen);
        this.modlen = modlen;
        this.params = params;
        this.random = random;
        this.forceNewParameters = false;
    }

    /**
     * Generates a pair of keys usable by any JavaSecurity compliant
     * DSA implementation.
     */
    public KeyPair generateKeyPair() {
        if (random == null) {
            random = JCAUtil.getSecureRandom();
        }
        DSAParameterSpec spec;
        try {
            if (forceNewParameters) {
                // generate new parameters each time
                spec = ParameterCache.getNewDSAParameterSpec(modlen, random);
            } else {
                if (params == null) {
                    params =
                        ParameterCache.getDSAParameterSpec(modlen, random);
                }
                spec = params;
            }
        } catch (GeneralSecurityException e) {
            throw new ProviderException(e);
        }
        return generateKeyPair(spec.getP(), spec.getQ(), spec.getG(), random);
    }

    public KeyPair generateKeyPair(BigInteger p, BigInteger q, BigInteger g,
                                   SecureRandom random) {

        BigInteger x = generateX(random, q);
        BigInteger y = generateY(x, p, g);

        try {

            // See the comments in DSAKeyFactory, 4532506, and 6232513.

            DSAPublicKey pub;
            if (DSAKeyFactory.SERIAL_INTEROP) {
                pub = new DSAPublicKey(y, p, q, g);
            } else {
                pub = new DSAPublicKeyImpl(y, p, q, g);
            }
            DSAPrivateKey priv = new DSAPrivateKey(x, p, q, g);

            KeyPair pair = new KeyPair(pub, priv);
            return pair;
        } catch (InvalidKeyException e) {
            throw new ProviderException(e);
        }
    }

    /**
     * Generate the private key component of the key pair using the
     * provided source of random bits. This method uses the random but
     * source passed to generate a seed and then calls the seed-based
     * generateX method.
     */
    private BigInteger generateX(SecureRandom random, BigInteger q) {
        BigInteger x = null;
        while (true) {
            int[] seed = new int[5];
            for (int i = 0; i < 5; i++) {
                seed[i] = random.nextInt();
            }
            x = generateX(seed, q);
            if (x.signum() > 0 && (x.compareTo(q) < 0)) {
                break;
            }
        }
        return x;
    }

    /**
     * Given a seed, generate the private key component of the key
     * pair. In the terminology used in the DSA specification
     * (FIPS-186) seed is the XSEED quantity.
     *
     * @param seed the seed to use to generate the private key.
     */
    BigInteger generateX(int[] seed, BigInteger q) {

        // check out t in the spec.
        int[] t = { 0x67452301, 0xEFCDAB89, 0x98BADCFE,
                    0x10325476, 0xC3D2E1F0 };
        //

        int[] tmp = DSA.SHA_7(seed, t);
        byte[] tmpBytes = new byte[tmp.length * 4];
        for (int i = 0; i < tmp.length; i++) {
            int k = tmp[i];
            for (int j = 0; j < 4; j++) {
                tmpBytes[(i * 4) + j] = (byte) (k >>> (24 - (j * 8)));
            }
        }
        BigInteger x = new BigInteger(1, tmpBytes).mod(q);
        return x;
    }

    /**
     * Generate the public key component y of the key pair.
     *
     * @param x the private key component.
     *
     * @param p the base parameter.
     */
    BigInteger generateY(BigInteger x, BigInteger p, BigInteger g) {
        BigInteger y = g.modPow(x, p);
        return y;
    }

}
