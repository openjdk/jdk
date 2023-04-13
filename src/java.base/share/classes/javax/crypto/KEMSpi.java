/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.KEM.Encapsulator;
import javax.crypto.KEM.Decapsulator;


/**
 * This class defines the Service Provider Interface (SPI)
 * for the {@link KEM} class. A security provider implements this interface
 * to implement a KEM algorithm.
 *
 * ...
 *
 * @see KEM
 * @since 21
 */
public interface KEMSpi {

    /**
     * Creates a KEM encapsulator.
     * <p>
     * An algorithm can define an {@code AlgorithmParameterSpec} child class to
     * provide extra information in this method. This is especially useful if
     * the same key can be used to derive shared secrets in different ways.
     * If any extra information inside this object needs to be transmitted along
     * with the key encapsulation message so that the receiver is able to create
     * a matching decapsulator, it will be included as a byte array in the
     * {@link Encapsulated#params} field inside the encapsulation output.
     * In this case, the security provider should provide an
     * {@code AlgorithmParameters} implementation using the same algorithm name
     * as the KEM. The receiver can initiate such an {@code AlgorithmParameters}
     * instance with the {@code params} byte array received and recover
     * an {@code AlgorithmParameterSpec} object to be used in its
     * {@link #newDecapsulator(PrivateKey, AlgorithmParameterSpec)} call.
     *
     * @param pk the receiver's public key, must not be {@code null}
     * @param spec the optional parameter, can be {@code null}
     * @param secureRandom the source of randomness for encapsulation,
     *                     If {@code null}, the implementation should provide
     *                     a default one.
     * @return the encapsulator for this key, not {@code null}
     * @throws InvalidAlgorithmParameterException if {@code spec} is invalid
     *          or one is required but {@code spec} is {@code null}
     * @throws InvalidKeyException if {@code pk} is invalid
     * @throws NullPointerException if {@code pk} is {@code null}
     */
    Encapsulator engineNewEncapsulator(PublicKey pk,
            AlgorithmParameterSpec spec, SecureRandom secureRandom)
            throws InvalidAlgorithmParameterException, InvalidKeyException;

    /**
     * Creates a KEM decapsulator.
     *
     * @param sk the receiver's private key, must not be {@code null}
     * @param spec the parameter, can be {@code null}
     * @return the decapsulator for this key, not {@code null}
     * @throws InvalidAlgorithmParameterException if {@code spec} is invalid
     *          or one is required but {@code spec} is {@code null}
     * @throws InvalidKeyException if {@code sk} is invalid
     * @throws NullPointerException if {@code sk} is {@code null}
     */
    Decapsulator engineNewDecapsulator(PrivateKey sk,
            AlgorithmParameterSpec spec)
            throws InvalidAlgorithmParameterException, InvalidKeyException;
}
