/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHGenParameterSpec;

/*
 * This class generates parameters for the Diffie-Hellman algorithm.
 * The parameters are a prime, a base, and optionally the length in bits of
 * the private value.
 *
 * <p>The Diffie-Hellman parameter generation accepts the size in bits of the
 * prime modulus and the size in bits of the random exponent as input.
 * The size of the prime modulus defaults to 1024 bits.
 *
 * @author Jan Luehe
 *
 *
 * @see java.security.AlgorithmParameters
 * @see java.security.spec.AlgorithmParameterSpec
 * @see DHParameters
 */
public final class DHParameterGenerator
extends AlgorithmParameterGeneratorSpi {

    // The size in bits of the prime modulus
    private int primeSize = 1024;

    // The size in bits of the random exponent (private value)
    private int exponentSize = 0;

    // The source of randomness
    private SecureRandom random = null;

    /**
     * Initializes this parameter generator for a certain keysize
     * and source of randomness.
     * The keysize is specified as the size in bits of the prime modulus.
     *
     * @param keysize the keysize (size of prime modulus) in bits
     * @param random the source of randomness
     */
    protected void engineInit(int keysize, SecureRandom random) {
        if ((keysize < 512) || (keysize > 1024) || (keysize % 64 != 0)) {
            throw new InvalidParameterException("Keysize must be multiple "
                                                + "of 64, and can only range "
                                                + "from 512 to 1024 "
                                                + "(inclusive)");
        }
        this.primeSize = keysize;
        this.random = random;
    }

    /**
     * Initializes this parameter generator with a set of parameter
     * generation values, which specify the size of the prime modulus and
     * the size of the random exponent, both in bits.
     *
     * @param params the set of parameter generation values
     * @param random the source of randomness
     *
     * @exception InvalidAlgorithmParameterException if the given parameter
     * generation values are inappropriate for this parameter generator
     */
    protected void engineInit(AlgorithmParameterSpec genParamSpec,
                              SecureRandom random)
        throws InvalidAlgorithmParameterException {
            if (!(genParamSpec instanceof DHGenParameterSpec)) {
                throw new InvalidAlgorithmParameterException
                    ("Inappropriate parameter type");
            }

            DHGenParameterSpec dhParamSpec = (DHGenParameterSpec)genParamSpec;

            primeSize = dhParamSpec.getPrimeSize();
            if ((primeSize<512) || (primeSize>1024) || (primeSize%64 != 0)) {
                throw new InvalidAlgorithmParameterException
                    ("Modulus size must be multiple of 64, and can only range "
                     + "from 512 to 1024 (inclusive)");
            }

            exponentSize = dhParamSpec.getExponentSize();
            if (exponentSize <= 0) {
                throw new InvalidAlgorithmParameterException
                    ("Exponent size must be greater than zero");
            }

            // Require exponentSize < primeSize
            if (exponentSize >= primeSize) {
                throw new InvalidAlgorithmParameterException
                    ("Exponent size must be less than modulus size");
            }
    }

    /**
     * Generates the parameters.
     *
     * @return the new AlgorithmParameters object
     */
    protected AlgorithmParameters engineGenerateParameters() {
        AlgorithmParameters algParams = null;

        if (this.exponentSize == 0) {
            this.exponentSize = this.primeSize - 1;
        }

        if (this.random == null)
            this.random = SunJCE.RANDOM;

        try {
            AlgorithmParameterGenerator paramGen;
            DSAParameterSpec dsaParamSpec;

            paramGen = AlgorithmParameterGenerator.getInstance("DSA");
            paramGen.init(this.primeSize, random);
            algParams = paramGen.generateParameters();
            dsaParamSpec = (DSAParameterSpec)
                algParams.getParameterSpec(DSAParameterSpec.class);

            DHParameterSpec dhParamSpec;
            if (this.exponentSize > 0) {
                dhParamSpec = new DHParameterSpec(dsaParamSpec.getP(),
                                                  dsaParamSpec.getG(),
                                                  this.exponentSize);
            } else {
                dhParamSpec = new DHParameterSpec(dsaParamSpec.getP(),
                                                  dsaParamSpec.getG());
            }
            algParams = AlgorithmParameters.getInstance("DH", "SunJCE");
            algParams.init(dhParamSpec);
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
}
