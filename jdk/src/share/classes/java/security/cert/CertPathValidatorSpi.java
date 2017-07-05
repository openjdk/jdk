/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

package java.security.cert;

import java.security.InvalidAlgorithmParameterException;

/**
 *
 * The <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the {@link CertPathValidator CertPathValidator} class. All
 * <code>CertPathValidator</code> implementations must include a class (the
 * SPI class) that extends this class (<code>CertPathValidatorSpi</code>)
 * and implements all of its methods. In general, instances of this class
 * should only be accessed through the <code>CertPathValidator</code> class.
 * For details, see the Java Cryptography Architecture.
 * <p>
 * <b>Concurrent Access</b>
 * <p>
 * Instances of this class need not be protected against concurrent
 * access from multiple threads. Threads that need to access a single
 * <code>CertPathValidatorSpi</code> instance concurrently should synchronize
 * amongst themselves and provide the necessary locking before calling the
 * wrapping <code>CertPathValidator</code> object.
 * <p>
 * However, implementations of <code>CertPathValidatorSpi</code> may still
 * encounter concurrency issues, since multiple threads each
 * manipulating a different <code>CertPathValidatorSpi</code> instance need not
 * synchronize.
 *
 * @since       1.4
 * @author      Yassir Elley
 */
public abstract class CertPathValidatorSpi {

    /**
     * The default constructor.
     */
    public CertPathValidatorSpi() {}

    /**
     * Validates the specified certification path using the specified
     * algorithm parameter set.
     * <p>
     * The <code>CertPath</code> specified must be of a type that is
     * supported by the validation algorithm, otherwise an
     * <code>InvalidAlgorithmParameterException</code> will be thrown. For
     * example, a <code>CertPathValidator</code> that implements the PKIX
     * algorithm validates <code>CertPath</code> objects of type X.509.
     *
     * @param certPath the <code>CertPath</code> to be validated
     * @param params the algorithm parameters
     * @return the result of the validation algorithm
     * @exception CertPathValidatorException if the <code>CertPath</code>
     * does not validate
     * @exception InvalidAlgorithmParameterException if the specified
     * parameters or the type of the specified <code>CertPath</code> are
     * inappropriate for this <code>CertPathValidator</code>
     */
    public abstract CertPathValidatorResult
        engineValidate(CertPath certPath, CertPathParameters params)
        throws CertPathValidatorException, InvalidAlgorithmParameterException;
}
