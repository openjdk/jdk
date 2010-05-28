/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;

/**
 * Parameters used as input for the Collection <code>CertStore</code>
 * algorithm.
 * <p>
 * This class is used to provide necessary configuration parameters
 * to implementations of the Collection <code>CertStore</code>
 * algorithm. The only parameter included in this class is the
 * <code>Collection</code> from which the <code>CertStore</code> will
 * retrieve certificates and CRLs.
 * <p>
 * <b>Concurrent Access</b>
 * <p>
 * Unless otherwise specified, the methods defined in this class are not
 * thread-safe. Multiple threads that need to access a single
 * object concurrently should synchronize amongst themselves and
 * provide the necessary locking. Multiple threads each manipulating
 * separate objects need not synchronize.
 *
 * @since       1.4
 * @author      Steve Hanna
 * @see         java.util.Collection
 * @see         CertStore
 */
public class CollectionCertStoreParameters
    implements CertStoreParameters {

    private Collection<?> coll;

    /**
     * Creates an instance of <code>CollectionCertStoreParameters</code>
     * which will allow certificates and CRLs to be retrieved from the
     * specified <code>Collection</code>. If the specified
     * <code>Collection</code> contains an object that is not a
     * <code>Certificate</code> or <code>CRL</code>, that object will be
     * ignored by the Collection <code>CertStore</code>.
     * <p>
     * The <code>Collection</code> is <b>not</b> copied. Instead, a
     * reference is used. This allows the caller to subsequently add or
     * remove <code>Certificates</code> or <code>CRL</code>s from the
     * <code>Collection</code>, thus changing the set of
     * <code>Certificates</code> or <code>CRL</code>s available to the
     * Collection <code>CertStore</code>. The Collection <code>CertStore</code>
     * will not modify the contents of the <code>Collection</code>.
     * <p>
     * If the <code>Collection</code> will be modified by one thread while
     * another thread is calling a method of a Collection <code>CertStore</code>
     * that has been initialized with this <code>Collection</code>, the
     * <code>Collection</code> must have fail-fast iterators.
     *
     * @param collection a <code>Collection</code> of
     *        <code>Certificate</code>s and <code>CRL</code>s
     * @exception NullPointerException if <code>collection</code> is
     * <code>null</code>
     */
    public CollectionCertStoreParameters(Collection<?> collection) {
        if (collection == null)
            throw new NullPointerException();
        coll = collection;
    }

    /**
     * Creates an instance of <code>CollectionCertStoreParameters</code> with
     * the default parameter values (an empty and immutable
     * <code>Collection</code>).
     */
    public CollectionCertStoreParameters() {
        coll = Collections.EMPTY_SET;
    }

    /**
     * Returns the <code>Collection</code> from which <code>Certificate</code>s
     * and <code>CRL</code>s are retrieved. This is <b>not</b> a copy of the
     * <code>Collection</code>, it is a reference. This allows the caller to
     * subsequently add or remove <code>Certificates</code> or
     * <code>CRL</code>s from the <code>Collection</code>.
     *
     * @return the <code>Collection</code> (never null)
     */
    public Collection<?> getCollection() {
        return coll;
    }

    /**
     * Returns a copy of this object. Note that only a reference to the
     * <code>Collection</code> is copied, and not the contents.
     *
     * @return the copy
     */
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            /* Cannot happen */
            throw new InternalError(e.toString());
        }
    }

    /**
     * Returns a formatted string describing the parameters.
     *
     * @return a formatted string describing the parameters
     */
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("CollectionCertStoreParameters: [\n");
        sb.append("  collection: " + coll + "\n");
        sb.append("]");
        return sb.toString();
    }
}
