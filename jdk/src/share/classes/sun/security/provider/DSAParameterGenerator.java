/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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
import java.security.AlgorithmParameterGeneratorSpi;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.InvalidParameterException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.DSAParameterSpec;

/**
 * This class generates parameters for the DSA algorithm. It uses a default
 * prime modulus size of 1024 bits, which can be overwritten during
 * initialization.
 *
 * @author Jan Luehe
 *
 *
 * @see java.security.AlgorithmParameters
 * @see java.security.spec.AlgorithmParameterSpec
 * @see DSAParameters
 *
 * @since 1.2
 */

public class DSAParameterGenerator extends AlgorithmParameterGeneratorSpi {

    // the modulus length
    private int modLen = 1024; // default

    // the source of randomness
    private SecureRandom random;

    // useful constants
    private static final BigInteger ZERO = BigInteger.valueOf(0);
    private static final BigInteger ONE = BigInteger.valueOf(1);
    private static final BigInteger TWO = BigInteger.valueOf(2);

    // Make a SHA-1 hash function
    private SHA sha;

    public DSAParameterGenerator() {
        this.sha = new SHA();
    }

    /**
     * Initializes this parameter generator for a certain strength
     * and source of randomness.
     *
     * @param strength the strength (size of prime) in bits
     * @param random the source of randomness
     */
    protected void engineInit(int strength, SecureRandom random) {
        /*
         * Bruce Schneier, "Applied Cryptography", 2nd Edition,
         * Description of DSA:
         * [...] The algorithm uses the following parameter:
         * p=a prime number L bits long, when L ranges from 512 to 1024 and is
         * a multiple of 64. [...]
         */
        if ((strength < 512) || (strength > 1024) || (strength % 64 != 0)) {
            throw new InvalidParameterException
                ("Prime size must range from 512 to 1024 "
                 + "and be a multiple of 64");
        }
        this.modLen = strength;
        this.random = random;
    }

    /**
     * Initializes this parameter generator with a set of
     * algorithm-specific parameter generation values.
     *
     * @param params the set of algorithm-specific parameter generation values
     * @param random the source of randomness
     *
     * @exception InvalidAlgorithmParameterException if the given parameter
     * generation values are inappropriate for this parameter generator
     */
    protected void engineInit(AlgorithmParameterSpec genParamSpec,
                              SecureRandom random)
        throws InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException("Invalid parameter");
    }

    /**
     * Generates the parameters.
     *
     * @return the new AlgorithmParameters object
     */
    protected AlgorithmParameters engineGenerateParameters() {
        AlgorithmParameters algParams = null;
        try {
            if (this.random == null) {
                this.random = new SecureRandom();
            }

            BigInteger[] pAndQ = generatePandQ(this.random, this.modLen);
            BigInteger paramP = pAndQ[0];
            BigInteger paramQ = pAndQ[1];
            BigInteger paramG = generateG(paramP, paramQ);

            DSAParameterSpec dsaParamSpec = new DSAParameterSpec(paramP,
                                                                 paramQ,
                                                                 paramG);
            algParams = AlgorithmParameters.getInstance("DSA", "SUN");
            algParams.init(dsaParamSpec);
        } catch (InvalidParameterSpecException e) {
            // this should never happen
            throw new RuntimeException(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            // this should never happen, because we provide it
            throw new RuntimeException(e.getMessage());
        } catch (NoSuchProviderException e) {
            // this should never happen, because we provide it
            throw new RuntimeException(e.getMessage());
        }

        return algParams;
    }

    /*
     * Generates the prime and subprime parameters for DSA,
     * using the provided source of randomness.
     * This method will generate new seeds until a suitable
     * seed has been found.
     *
     * @param random the source of randomness to generate the
     * seed
     * @param L the size of <code>p</code>, in bits.
     *
     * @return an array of BigInteger, with <code>p</code> at index 0 and
     * <code>q</code> at index 1.
     */
    BigInteger[] generatePandQ(SecureRandom random, int L) {
        BigInteger[] result = null;
        byte[] seed = new byte[20];

        while(result == null) {
            for (int i = 0; i < 20; i++) {
                seed[i] = (byte)random.nextInt();
            }
            result = generatePandQ(seed, L);
        }
        return result;
    }

    /*
     * Generates the prime and subprime parameters for DSA.
     *
     * <p>The seed parameter corresponds to the <code>SEED</code> parameter
     * referenced in the FIPS specification of the DSA algorithm,
     * and L is the size of <code>p</code>, in bits.
     *
     * @param seed the seed to generate the parameters
     * @param L the size of <code>p</code>, in bits.
     *
     * @return an array of BigInteger, with <code>p</code> at index 0,
     * <code>q</code> at index 1, the seed at index 2, and the counter value
     * at index 3, or null if the seed does not yield suitable numbers.
     */
    BigInteger[] generatePandQ(byte[] seed, int L) {

        /* Useful variables */
        int g = seed.length * 8;
        int n = (L - 1) / 160;
        int b = (L - 1) % 160;

        BigInteger SEED = new BigInteger(1, seed);
        BigInteger TWOG = TWO.pow(2 * g);

        /* Step 2 (Step 1 is getting seed). */
        byte[] U1 = SHA(seed);
        byte[] U2 = SHA(toByteArray((SEED.add(ONE)).mod(TWOG)));

        xor(U1, U2);
        byte[] U = U1;

        /* Step 3: For q by setting the msb and lsb to 1 */
        U[0] |= 0x80;
        U[19] |= 1;
        BigInteger q = new BigInteger(1, U);

        /* Step 5 */
         if (!q.isProbablePrime(80)) {
             return null;

         } else {
             BigInteger V[] = new BigInteger[n + 1];
             BigInteger offset = TWO;

             /* Step 6 */
             for (int counter = 0; counter < 4096; counter++) {

                 /* Step 7 */
                 for (int k = 0; k <= n; k++) {
                     BigInteger K = BigInteger.valueOf(k);
                     BigInteger tmp = (SEED.add(offset).add(K)).mod(TWOG);
                     V[k] = new BigInteger(1, SHA(toByteArray(tmp)));
                 }

                 /* Step 8 */
                 BigInteger W = V[0];
                 for (int i = 1; i < n; i++) {
                     W = W.add(V[i].multiply(TWO.pow(i * 160)));
                 }
                 W = W.add((V[n].mod(TWO.pow(b))).multiply(TWO.pow(n * 160)));

                 BigInteger TWOLm1 = TWO.pow(L - 1);
                 BigInteger X = W.add(TWOLm1);

                 /* Step 9 */
                 BigInteger c = X.mod(q.multiply(TWO));
                 BigInteger p = X.subtract(c.subtract(ONE));

                 /* Step 10 - 13 */
                 if (p.compareTo(TWOLm1) > -1 && p.isProbablePrime(80)) {
                     BigInteger[] result = {p, q, SEED,
                                            BigInteger.valueOf(counter)};
                     return result;
                 }
                 offset = offset.add(BigInteger.valueOf(n)).add(ONE);
             }
             return null;
         }
    }

    /*
     * Generates the <code>g</code> parameter for DSA.
     *
     * @param p the prime, <code>p</code>.
     * @param q the subprime, <code>q</code>.
     *
     * @param the <code>g</code>
     */
    BigInteger generateG(BigInteger p, BigInteger q) {
        BigInteger h = ONE;
        BigInteger pMinusOneOverQ = (p.subtract(ONE)).divide(q);
        BigInteger g = ONE;
        while (g.compareTo(TWO) < 0) {
            g = h.modPow(pMinusOneOverQ, p);
            h = h.add(ONE);
        }
        return g;
    }

    /*
     * Returns the SHA-1 digest of some data
     */
    private byte[] SHA(byte[] array) {
        sha.engineReset();
        sha.engineUpdate(array, 0, array.length);
        return sha.engineDigest();
    }

    /*
     * Converts the result of a BigInteger.toByteArray call to an exact
     * signed magnitude representation for any positive number.
     */
    private byte[] toByteArray(BigInteger bigInt) {
        byte[] result = bigInt.toByteArray();
        if (result[0] == 0) {
            byte[] tmp = new byte[result.length - 1];
            System.arraycopy(result, 1, tmp, 0, tmp.length);
            result = tmp;
        }
        return result;
    }

    /*
     * XORs U2 into U1
     */
    private void xor(byte[] U1, byte[] U2) {
        for (int i = 0; i < U1.length; i++) {
            U1[i] ^= U2[i];
        }
    }
}
