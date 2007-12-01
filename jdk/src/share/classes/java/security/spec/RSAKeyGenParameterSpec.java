/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.security.spec.AlgorithmParameterSpec;

/**
 * This class specifies the set of parameters used to generate an RSA
 * key pair.
 *
 * @author Jan Luehe
 *
 * @see java.security.KeyPairGenerator#initialize(java.security.spec.AlgorithmParameterSpec)
 *
 * @since 1.3
 */

public class RSAKeyGenParameterSpec implements AlgorithmParameterSpec {

    private int keysize;
    private BigInteger publicExponent;

    /**
     * The public-exponent value F0 = 3.
     */
    public static final BigInteger F0 = BigInteger.valueOf(3);

    /**
     * The public exponent-value F4 = 65537.
     */
    public static final BigInteger F4 = BigInteger.valueOf(65537);

    /**
     * Constructs a new <code>RSAParameterSpec</code> object from the
     * given keysize and public-exponent value.
     *
     * @param keysize the modulus size (specified in number of bits)
     * @param publicExponent the public exponent
     */
    public RSAKeyGenParameterSpec(int keysize, BigInteger publicExponent) {
        this.keysize = keysize;
        this.publicExponent = publicExponent;
    }

    /**
     * Returns the keysize.
     *
     * @return the keysize.
     */
    public int getKeysize() {
        return keysize;
    }

    /**
     * Returns the public-exponent value.
     *
     * @return the public-exponent value.
     */
    public BigInteger getPublicExponent() {
        return publicExponent;
    }
}
