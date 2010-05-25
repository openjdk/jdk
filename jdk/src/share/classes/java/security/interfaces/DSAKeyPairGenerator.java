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

package java.security.interfaces;

import java.security.*;

/**
 * An interface to an object capable of generating DSA key pairs.
 *
 * <p>The <code>initialize</code> methods may each be called any number
 * of times. If no <code>initialize</code> method is called on a
 * DSAKeyPairGenerator, the default is to generate 1024-bit keys, using
 * precomputed p, q and g parameters and an instance of SecureRandom as
 * the random bit source.
 *
 * <p>Users wishing to indicate DSA-specific parameters, and to generate a key
 * pair suitable for use with the DSA algorithm typically
 *
 * <ol>
 *
 * <li>Get a key pair generator for the DSA algorithm by calling the
 * KeyPairGenerator <code>getInstance</code> method with "DSA"
 * as its argument.<p>
 *
 * <li>Initialize the generator by casting the result to a DSAKeyPairGenerator
 * and calling one of the
 * <code>initialize</code> methods from this DSAKeyPairGenerator interface.<p>
 *
 * <li>Generate a key pair by calling the <code>generateKeyPair</code>
 * method from the KeyPairGenerator class.
 *
 * </ol>
 *
 * <p>Note: it is not always necessary to do do algorithm-specific
 * initialization for a DSA key pair generator. That is, it is not always
 * necessary to call an <code>initialize</code> method in this interface.
 * Algorithm-independent initialization using the <code>initialize</code> method
 * in the KeyPairGenerator
 * interface is all that is needed when you accept defaults for algorithm-specific
 * parameters.
 *
 * @see java.security.KeyPairGenerator
 */
public interface DSAKeyPairGenerator {

    /**
     * Initializes the key pair generator using the DSA family parameters
     * (p,q and g) and an optional SecureRandom bit source. If a
     * SecureRandom bit source is needed but not supplied, i.e. null, a
     * default SecureRandom instance will be used.
     *
     * @param params the parameters to use to generate the keys.
     *
     * @param random the random bit source to use to generate key bits;
     * can be null.
     *
     * @exception InvalidParameterException if the <code>params</code>
     * value is invalid or null.
     */
   public void initialize(DSAParams params, SecureRandom random)
   throws InvalidParameterException;

    /**
     * Initializes the key pair generator for a given modulus length
     * (instead of parameters), and an optional SecureRandom bit source.
     * If a SecureRandom bit source is needed but not supplied, i.e.
     * null, a default SecureRandom instance will be used.
     *
     * <p>If <code>genParams</code> is true, this method generates new
     * p, q and g parameters. If it is false, the method uses precomputed
     * parameters for the modulus length requested. If there are no
     * precomputed parameters for that modulus length, an exception will be
     * thrown. It is guaranteed that there will always be
     * default parameters for modulus lengths of 512 and 1024 bits.
     *
     * @param modlen the modulus length in bits. Valid values are any
     * multiple of 8 between 512 and 1024, inclusive.
     *
     * @param random the random bit source to use to generate key bits;
     * can be null.
     *
     * @param genParams whether or not to generate new parameters for
     * the modulus length requested.
     *
     * @exception InvalidParameterException if <code>modlen</code> is not
     * between 512 and 1024, or if <code>genParams</code> is false and
     * there are no precomputed parameters for the requested modulus
     * length.
     */
    public void initialize(int modlen, boolean genParams, SecureRandom random)
    throws InvalidParameterException;
}
