/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;

/**
 * The <i>Service Provider Interface</i> (<b>SPI</b>)
 * for the {@link CertStore CertStore} class. All <code>CertStore</code>
 * implementations must include a class (the SPI class) that extends
 * this class (<code>CertStoreSpi</code>), provides a constructor with
 * a single argument of type <code>CertStoreParameters</code>, and implements
 * all of its methods. In general, instances of this class should only be
 * accessed through the <code>CertStore</code> class.
 * For details, see the Java Cryptography Architecture.
 * <p>
 * <b>Concurrent Access</b>
 * <p>
 * The public methods of all <code>CertStoreSpi</code> objects must be
 * thread-safe. That is, multiple threads may concurrently invoke these
 * methods on a single <code>CertStoreSpi</code> object (or more than one)
 * with no ill effects. This allows a <code>CertPathBuilder</code> to search
 * for a CRL while simultaneously searching for further certificates, for
 * instance.
 * <p>
 * Simple <code>CertStoreSpi</code> implementations will probably ensure
 * thread safety by adding a <code>synchronized</code> keyword to their
 * <code>engineGetCertificates</code> and <code>engineGetCRLs</code> methods.
 * More sophisticated ones may allow truly concurrent access.
 *
 * @since       1.4
 * @author      Steve Hanna
 */
public abstract class CertStoreSpi {

    /**
     * The sole constructor.
     *
     * @param params the initialization parameters (may be <code>null</code>)
     * @throws InvalidAlgorithmParameterException if the initialization
     * parameters are inappropriate for this <code>CertStoreSpi</code>
     */
    public CertStoreSpi(CertStoreParameters params)
    throws InvalidAlgorithmParameterException { }

    /**
     * Returns a <code>Collection</code> of <code>Certificate</code>s that
     * match the specified selector. If no <code>Certificate</code>s
     * match the selector, an empty <code>Collection</code> will be returned.
     * <p>
     * For some <code>CertStore</code> types, the resulting
     * <code>Collection</code> may not contain <b>all</b> of the
     * <code>Certificate</code>s that match the selector. For instance,
     * an LDAP <code>CertStore</code> may not search all entries in the
     * directory. Instead, it may just search entries that are likely to
     * contain the <code>Certificate</code>s it is looking for.
     * <p>
     * Some <code>CertStore</code> implementations (especially LDAP
     * <code>CertStore</code>s) may throw a <code>CertStoreException</code>
     * unless a non-null <code>CertSelector</code> is provided that includes
     * specific criteria that can be used to find the certificates. Issuer
     * and/or subject names are especially useful criteria.
     *
     * @param selector A <code>CertSelector</code> used to select which
     *  <code>Certificate</code>s should be returned. Specify <code>null</code>
     *  to return all <code>Certificate</code>s (if supported).
     * @return A <code>Collection</code> of <code>Certificate</code>s that
     *         match the specified selector (never <code>null</code>)
     * @throws CertStoreException if an exception occurs
     */
    public abstract Collection<? extends Certificate> engineGetCertificates
            (CertSelector selector) throws CertStoreException;

    /**
     * Returns a <code>Collection</code> of <code>CRL</code>s that
     * match the specified selector. If no <code>CRL</code>s
     * match the selector, an empty <code>Collection</code> will be returned.
     * <p>
     * For some <code>CertStore</code> types, the resulting
     * <code>Collection</code> may not contain <b>all</b> of the
     * <code>CRL</code>s that match the selector. For instance,
     * an LDAP <code>CertStore</code> may not search all entries in the
     * directory. Instead, it may just search entries that are likely to
     * contain the <code>CRL</code>s it is looking for.
     * <p>
     * Some <code>CertStore</code> implementations (especially LDAP
     * <code>CertStore</code>s) may throw a <code>CertStoreException</code>
     * unless a non-null <code>CRLSelector</code> is provided that includes
     * specific criteria that can be used to find the CRLs. Issuer names
     * and/or the certificate to be checked are especially useful.
     *
     * @param selector A <code>CRLSelector</code> used to select which
     *  <code>CRL</code>s should be returned. Specify <code>null</code>
     *  to return all <code>CRL</code>s (if supported).
     * @return A <code>Collection</code> of <code>CRL</code>s that
     *         match the specified selector (never <code>null</code>)
     * @throws CertStoreException if an exception occurs
     */
    public abstract Collection<? extends CRL> engineGetCRLs
            (CRLSelector selector) throws CertStoreException;
}
