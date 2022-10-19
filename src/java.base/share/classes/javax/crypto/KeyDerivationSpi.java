/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package javax.crypto;

import java.security.InvalidKeyException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.DerivedKeyParameterSpec;
import java.util.List;

/**
 * This class defines the <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the <code>KeyDerivation</code> class.
 * All the abstract methods in this class must be implemented by each
 * cryptographic service provider who wishes to supply the implementation
 * of a particular key derivation algorithm.
 */

public abstract class  KeyDerivationSpi {

    /**
     * Constructor for subclasses to call.
     */
    public KeyDerivationSpi() {}

    /**
     * Initializes the key derivation object with a secret
     * key and algorithm parameters.
     *
     * @param key the (secret) key
     * @param params the algorithm parameters
     *
     * @throws InvalidKeyException if the given key is inappropriate for
     * initializing this MAC.
     * @throws InvalidParameterSpecException if the given algorithm
     * parameters are inappropriate for this key derivation algorithm.
     */
    protected abstract void engineInit(SecretKey key,
            AlgorithmParameterSpec params) throws InvalidKeyException,
            InvalidParameterSpecException;

    /**
     * Derive one or more {@code SecretKey} objects using a specified
     * algorithm and parameters.
     *
     * @param params the parameters used for the key derivation.  There should
     *      be one {@code DerivedKeyParameterSpec} for each secret key to
     *      be derived by the selected KDF implementation.
     * @return a {@link List} of {@link SecretKey} objects containing key
     *      material derived from the {@code KeyDerivation} implementation.
     *      Each element of the list is an algorithm-parameter pair, in
     *      the form of a {@code Map.Entry} object.
     *
     * @throws InvalidParameterSpecException if the given algorithm
     *      parameters are inappropriate for this derived key type
     * @throws NullPointerException if either {@code params} is null, or any
     *      entry in the {@code params} list is null
     */
    protected abstract List<SecretKey> engineDeriveKeys(
            List<DerivedKeyParameterSpec> params)
            throws InvalidParameterSpecException;
}
