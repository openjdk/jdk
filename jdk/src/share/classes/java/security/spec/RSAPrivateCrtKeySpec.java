/*
 * Copyright 1998-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security.spec;

import java.math.BigInteger;

/**
 * This class specifies an RSA private key, as defined in the PKCS#1
 * standard, using the Chinese Remainder Theorem (CRT) information values for
 * efficiency.
 *
 * @author Jan Luehe
 *
 *
 * @see java.security.Key
 * @see java.security.KeyFactory
 * @see KeySpec
 * @see PKCS8EncodedKeySpec
 * @see RSAPrivateKeySpec
 * @see RSAPublicKeySpec
 */

public class RSAPrivateCrtKeySpec extends RSAPrivateKeySpec {

    private final BigInteger publicExponent;
    private final BigInteger primeP;
    private final BigInteger primeQ;
    private final BigInteger primeExponentP;
    private final BigInteger primeExponentQ;
    private final BigInteger crtCoefficient;



   /**
    * Creates a new <code>RSAPrivateCrtKeySpec</code>
    * given the modulus, publicExponent, privateExponent,
    * primeP, primeQ, primeExponentP, primeExponentQ, and
    * crtCoefficient as defined in PKCS#1.
    *
    * @param modulus the modulus n
    * @param publicExponent the public exponent e
    * @param privateExponent the private exponent d
    * @param primeP the prime factor p of n
    * @param primeQ the prime factor q of n
    * @param primeExponentP this is d mod (p-1)
    * @param primeExponentQ this is d mod (q-1)
    * @param crtCoefficient the Chinese Remainder Theorem
    * coefficient q-1 mod p
    */
    public RSAPrivateCrtKeySpec(BigInteger modulus,
                                BigInteger publicExponent,
                                BigInteger privateExponent,
                                BigInteger primeP,
                                BigInteger primeQ,
                                BigInteger primeExponentP,
                                BigInteger primeExponentQ,
                                BigInteger crtCoefficient) {
        super(modulus, privateExponent);
        this.publicExponent = publicExponent;
        this.primeP = primeP;
        this.primeQ = primeQ;
        this.primeExponentP = primeExponentP;
        this.primeExponentQ = primeExponentQ;
        this.crtCoefficient = crtCoefficient;
    }

    /**
     * Returns the public exponent.
     *
     * @return the public exponent
     */
    public BigInteger getPublicExponent() {
        return this.publicExponent;
    }

    /**
     * Returns the primeP.

     * @return the primeP
     */
    public BigInteger getPrimeP() {
        return this.primeP;
    }

    /**
     * Returns the primeQ.
     *
     * @return the primeQ
     */
    public BigInteger getPrimeQ() {
        return this.primeQ;
    }

    /**
     * Returns the primeExponentP.
     *
     * @return the primeExponentP
     */
    public BigInteger getPrimeExponentP() {
        return this.primeExponentP;
    }

    /**
     * Returns the primeExponentQ.
     *
     * @return the primeExponentQ
     */
    public BigInteger getPrimeExponentQ() {
        return this.primeExponentQ;
    }

    /**
     * Returns the crtCoefficient.
     *
     * @return the crtCoefficient
     */
    public BigInteger getCrtCoefficient() {
        return this.crtCoefficient;
    }
}
