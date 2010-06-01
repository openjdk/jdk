/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;

import sun.security.jca.JCAUtil;

/**
 * RSA keypair generation. Standard algorithm, minimum key length 512 bit.
 * We generate two random primes until we find two where phi is relative
 * prime to the public exponent. Default exponent is 65537. It has only bit 0
 * and bit 4 set, which makes it particularly efficient.
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class RSAKeyPairGenerator extends KeyPairGeneratorSpi {

    // public exponent to use
    private BigInteger publicExponent;

    // size of the key to generate, >= RSAKeyFactory.MIN_MODLEN
    private int keySize;

    // PRNG to use
    private SecureRandom random;

    public RSAKeyPairGenerator() {
        // initialize to default in case the app does not call initialize()
        initialize(1024, null);
    }

    // initialize the generator. See JCA doc
    public void initialize(int keySize, SecureRandom random) {

        // do not allow unreasonably small or large key sizes,
        // probably user error
        try {
            RSAKeyFactory.checkKeyLengths(keySize, RSAKeyGenParameterSpec.F4,
                512, 64 * 1024);
        } catch (InvalidKeyException e) {
            throw new InvalidParameterException(e.getMessage());
        }

        this.keySize = keySize;
        this.random = random;
        this.publicExponent = RSAKeyGenParameterSpec.F4;
    }

    // second initialize method. See JCA doc.
    public void initialize(AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidAlgorithmParameterException {

        if (params instanceof RSAKeyGenParameterSpec == false) {
            throw new InvalidAlgorithmParameterException
                ("Params must be instance of RSAKeyGenParameterSpec");
        }

        RSAKeyGenParameterSpec rsaSpec = (RSAKeyGenParameterSpec)params;
        int tmpKeySize = rsaSpec.getKeysize();
        BigInteger tmpPublicExponent = rsaSpec.getPublicExponent();

        if (tmpPublicExponent == null) {
            tmpPublicExponent = RSAKeyGenParameterSpec.F4;
        } else {
            if (tmpPublicExponent.compareTo(RSAKeyGenParameterSpec.F0) < 0) {
                throw new InvalidAlgorithmParameterException
                        ("Public exponent must be 3 or larger");
            }
            if (tmpPublicExponent.bitLength() > tmpKeySize) {
                throw new InvalidAlgorithmParameterException
                        ("Public exponent must be smaller than key size");
            }
        }

        // do not allow unreasonably large key sizes, probably user error
        try {
            RSAKeyFactory.checkKeyLengths(tmpKeySize, tmpPublicExponent,
                512, 64 * 1024);
        } catch (InvalidKeyException e) {
            throw new InvalidAlgorithmParameterException(
                "Invalid key sizes", e);
        }

        this.keySize = tmpKeySize;
        this.publicExponent = tmpPublicExponent;
        this.random = random;
    }

    // generate the keypair. See JCA doc
    public KeyPair generateKeyPair() {
        // accomodate odd key sizes in case anybody wants to use them
        int lp = (keySize + 1) >> 1;
        int lq = keySize - lp;
        if (random == null) {
            random = JCAUtil.getSecureRandom();
        }
        BigInteger e = publicExponent;
        while (true) {
            // generate two random primes of size lp/lq
            BigInteger p = BigInteger.probablePrime(lp, random);
            BigInteger q, n;
            do {
                q = BigInteger.probablePrime(lq, random);
                // convention is for p > q
                if (p.compareTo(q) < 0) {
                    BigInteger tmp = p;
                    p = q;
                    q = tmp;
                }
                // modulus n = p * q
                n = p.multiply(q);
                // even with correctly sized p and q, there is a chance that
                // n will be one bit short. re-generate the smaller prime if so
            } while (n.bitLength() < keySize);

            // phi = (p - 1) * (q - 1) must be relative prime to e
            // otherwise RSA just won't work ;-)
            BigInteger p1 = p.subtract(BigInteger.ONE);
            BigInteger q1 = q.subtract(BigInteger.ONE);
            BigInteger phi = p1.multiply(q1);
            // generate new p and q until they work. typically
            // the first try will succeed when using F4
            if (e.gcd(phi).equals(BigInteger.ONE) == false) {
                continue;
            }

            // private exponent d is the inverse of e mod phi
            BigInteger d = e.modInverse(phi);

            // 1st prime exponent pe = d mod (p - 1)
            BigInteger pe = d.mod(p1);
            // 2nd prime exponent qe = d mod (q - 1)
            BigInteger qe = d.mod(q1);

            // crt coefficient coeff is the inverse of q mod p
            BigInteger coeff = q.modInverse(p);

            try {
                PublicKey publicKey = new RSAPublicKeyImpl(n, e);
                PrivateKey privateKey =
                        new RSAPrivateCrtKeyImpl(n, e, d, p, q, pe, qe, coeff);
                return new KeyPair(publicKey, privateKey);
            } catch (InvalidKeyException exc) {
                // invalid key exception only thrown for keys < 512 bit,
                // will not happen here
                throw new RuntimeException(exc);
            }
        }
    }

}
