/*
 * Copyright 2001-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
 * This class specifies an RSA multi-prime private key, as defined in the
 * PKCS#1 v2.1, using the Chinese Remainder Theorem (CRT) information
 * values for efficiency.
 *
 * @author Valerie Peng
 *
 *
 * @see java.security.Key
 * @see java.security.KeyFactory
 * @see KeySpec
 * @see PKCS8EncodedKeySpec
 * @see RSAPrivateKeySpec
 * @see RSAPublicKeySpec
 * @see RSAOtherPrimeInfo
 *
 * @since 1.4
 */

public class RSAMultiPrimePrivateCrtKeySpec extends RSAPrivateKeySpec {

    private final BigInteger publicExponent;
    private final BigInteger primeP;
    private final BigInteger primeQ;
    private final BigInteger primeExponentP;
    private final BigInteger primeExponentQ;
    private final BigInteger crtCoefficient;
    private final RSAOtherPrimeInfo otherPrimeInfo[];

   /**
    * Creates a new <code>RSAMultiPrimePrivateCrtKeySpec</code>
    * given the modulus, publicExponent, privateExponent,
    * primeP, primeQ, primeExponentP, primeExponentQ,
    * crtCoefficient, and otherPrimeInfo as defined in PKCS#1 v2.1.
    *
    * <p>Note that the contents of <code>otherPrimeInfo</code>
    * are copied to protect against subsequent modification when
    * constructing this object.
    *
    * @param modulus the modulus n.
    * @param publicExponent the public exponent e.
    * @param privateExponent the private exponent d.
    * @param primeP the prime factor p of n.
    * @param primeQ the prime factor q of n.
    * @param primeExponentP this is d mod (p-1).
    * @param primeExponentQ this is d mod (q-1).
    * @param crtCoefficient the Chinese Remainder Theorem
    * coefficient q-1 mod p.
    * @param otherPrimeInfo triplets of the rest of primes, null can be
    * specified if there are only two prime factors (p and q).
    * @exception NullPointerException if any of the parameters, i.e.
    * <code>modulus</code>,
    * <code>publicExponent</code>, <code>privateExponent</code>,
    * <code>primeP</code>, <code>primeQ</code>,
    * <code>primeExponentP</code>, <code>primeExponentQ</code>,
    * <code>crtCoefficient</code>, is null.
    * @exception IllegalArgumentException if an empty, i.e. 0-length,
    * <code>otherPrimeInfo</code> is specified.
    */
    public RSAMultiPrimePrivateCrtKeySpec(BigInteger modulus,
                                BigInteger publicExponent,
                                BigInteger privateExponent,
                                BigInteger primeP,
                                BigInteger primeQ,
                                BigInteger primeExponentP,
                                BigInteger primeExponentQ,
                                BigInteger crtCoefficient,
                                RSAOtherPrimeInfo[] otherPrimeInfo) {
        super(modulus, privateExponent);
        if (modulus == null) {
            throw new NullPointerException("the modulus parameter must be " +
                                            "non-null");
        }
        if (publicExponent == null) {
            throw new NullPointerException("the publicExponent parameter " +
                                            "must be non-null");
        }
        if (privateExponent == null) {
            throw new NullPointerException("the privateExponent parameter " +
                                            "must be non-null");
        }
        if (primeP == null) {
            throw new NullPointerException("the primeP parameter " +
                                            "must be non-null");
        }
        if (primeQ == null) {
            throw new NullPointerException("the primeQ parameter " +
                                            "must be non-null");
        }
        if (primeExponentP == null) {
            throw new NullPointerException("the primeExponentP parameter " +
                                            "must be non-null");
        }
        if (primeExponentQ == null) {
            throw new NullPointerException("the primeExponentQ parameter " +
                                            "must be non-null");
        }
        if (crtCoefficient == null) {
            throw new NullPointerException("the crtCoefficient parameter " +
                                            "must be non-null");
        }
        this.publicExponent = publicExponent;
        this.primeP = primeP;
        this.primeQ = primeQ;
        this.primeExponentP = primeExponentP;
        this.primeExponentQ = primeExponentQ;
        this.crtCoefficient = crtCoefficient;
        if (otherPrimeInfo == null)  {
            this.otherPrimeInfo = null;
        } else if (otherPrimeInfo.length == 0) {
            throw new IllegalArgumentException("the otherPrimeInfo " +
                                                "parameter must not be empty");
        } else {
            this.otherPrimeInfo = otherPrimeInfo.clone();
        }
    }

    /**
     * Returns the public exponent.
     *
     * @return the public exponent.
     */
    public BigInteger getPublicExponent() {
        return this.publicExponent;
    }

    /**
     * Returns the primeP.
     *
     * @return the primeP.
     */
    public BigInteger getPrimeP() {
        return this.primeP;
    }

    /**
     * Returns the primeQ.
     *
     * @return the primeQ.
     */
    public BigInteger getPrimeQ() {
        return this.primeQ;
    }

    /**
     * Returns the primeExponentP.
     *
     * @return the primeExponentP.
     */
    public BigInteger getPrimeExponentP() {
        return this.primeExponentP;
    }

    /**
     * Returns the primeExponentQ.
     *
     * @return the primeExponentQ.
     */
    public BigInteger getPrimeExponentQ() {
        return this.primeExponentQ;
    }

    /**
     * Returns the crtCoefficient.
     *
     * @return the crtCoefficient.
     */
    public BigInteger getCrtCoefficient() {
        return this.crtCoefficient;
    }

    /**
     * Returns a copy of the otherPrimeInfo or null if there are
     * only two prime factors (p and q).
     *
     * @return the otherPrimeInfo. Returns a new array each
     * time this method is called.
     */
    public RSAOtherPrimeInfo[] getOtherPrimeInfo() {
        if (otherPrimeInfo == null) return null;
        return otherPrimeInfo.clone();
    }
}
