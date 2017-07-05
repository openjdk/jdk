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
 * The <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the {@link CertPathBuilder CertPathBuilder} class. All
 * <code>CertPathBuilder</code> implementations must include a class (the
 * SPI class) that extends this class (<code>CertPathBuilderSpi</code>) and
 * implements all of its methods. In general, instances of this class should
 * only be accessed through the <code>CertPathBuilder</code> class. For
 * details, see the Java Cryptography Architecture.
 * <p>
 * <b>Concurrent Access</b>
 * <p>
 * Instances of this class need not be protected against concurrent
 * access from multiple threads. Threads that need to access a single
 * <code>CertPathBuilderSpi</code> instance concurrently should synchronize
 * amongst themselves and provide the necessary locking before calling the
 * wrapping <code>CertPathBuilder</code> object.
 * <p>
 * However, implementations of <code>CertPathBuilderSpi</code> may still
 * encounter concurrency issues, since multiple threads each
 * manipulating a different <code>CertPathBuilderSpi</code> instance need not
 * synchronize.
 *
 * @since       1.4
 * @author      Sean Mullan
 */
public abstract class CertPathBuilderSpi {

    /**
     * The default constructor.
     */
    public CertPathBuilderSpi() { }

    /**
     * Attempts to build a certification path using the specified
     * algorithm parameter set.
     *
     * @param params the algorithm parameters
     * @return the result of the build algorithm
     * @throws CertPathBuilderException if the builder is unable to construct
     * a certification path that satisfies the specified parameters
     * @throws InvalidAlgorithmParameterException if the specified parameters
     * are inappropriate for this <code>CertPathBuilder</code>
     */
    public abstract CertPathBuilderResult engineBuild(CertPathParameters params)
        throws CertPathBuilderException, InvalidAlgorithmParameterException;
}
