/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.javac.PreviewFeature;

import java.security.InvalidAlgorithmParameterException;
import java.security.KDFParameters;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;

/**
 * This class defines the <i>Service Provider Interface</i> (<b>SPI</b>) for the
 * {@code KDF} class.
 * <p>
 * All the abstract methods in this class must be implemented by each
 * cryptographic service provider who wishes to supply the implementation of a
 * particular key derivation algorithm.
 *
 * @see KDF
 * @see SecretKey
 * @since 24
 */
@PreviewFeature(feature = PreviewFeature.Feature.KEY_DERIVATION)
public abstract class KDFSpi {

    /**
     * The sole constructor.
     * <p>
     * A {@code KDFParameters} object may be specified for KDF algorithms
     * that support initialization parameters.
     *
     * @param kdfParameters
     *     the initialization parameters for the {@code KDF} algorithm (may be
     *     {@code null})
     *
     * @throws InvalidAlgorithmParameterException
     *     if the initialization parameters are inappropriate for this
     *     {@code KDFSpi}
     */
    protected KDFSpi(KDFParameters kdfParameters)
        throws InvalidAlgorithmParameterException {}


    /**
     * Derives a key, returned as a {@code SecretKey}.
     * <p>
     * The {@code engineDeriveKey} method may be called multiple times on a particular
     * {@code KDFSpi} instance.
     *
     * @param alg
     *     the algorithm of the resultant {@code SecretKey} object
     * @param kdfParameterSpec
     *     derivation parameters
     *
     * @return a {@code SecretKey} object corresponding to a key built from the
     *     KDF output and according to the derivation parameters
     *
     * @throws InvalidAlgorithmParameterException
     *     if the information contained within the {@code KDFParameterSpec} is
     *     invalid or incorrect for the type of key to be derived
     * @throws NullPointerException
     *     if {@code alg} or {@code kdfParameterSpec} is null
     */
    protected abstract SecretKey engineDeriveKey(String alg,
                                                 AlgorithmParameterSpec kdfParameterSpec)
        throws InvalidAlgorithmParameterException;

    /**
     * Obtains raw data from a key derivation function.
     * <p>
     * The {@code engineDeriveData} method may be called multiple times on a
     * particular {@code KDFSpi} instance.
     *
     * @param kdfParameterSpec
     *     derivation parameters
     *
     * @return a byte array whose length matches the specified length in the
     *     processed {@code KDFParameterSpec} and containing the output from the
     *     key derivation function
     *
     * @throws InvalidAlgorithmParameterException
     *     if the information contained within the {@code KDFParameterSpec} is
     *     invalid or incorrect for the type of key to be derived
     * @throws UnsupportedOperationException
     *     if the derived key material is not extractable
     * @throws NullPointerException
     *     if {@code kdfParameterSpec} is null
     */
    protected abstract byte[] engineDeriveData(
        AlgorithmParameterSpec kdfParameterSpec)
        throws InvalidAlgorithmParameterException;

}