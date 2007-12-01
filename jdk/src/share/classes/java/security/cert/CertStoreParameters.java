/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.security.cert;

/**
 * A specification of <code>CertStore</code> parameters.
 * <p>
 * The purpose of this interface is to group (and provide type safety for)
 * all <code>CertStore</code> parameter specifications. All
 * <code>CertStore</code> parameter specifications must implement this
 * interface.
 * <p>
 * Typically, a <code>CertStoreParameters</code> object is passed as a parameter
 * to one of the {@link CertStore#getInstance CertStore.getInstance} methods.
 * The <code>getInstance</code> method returns a <code>CertStore</code> that
 * is used for retrieving <code>Certificate</code>s and <code>CRL</code>s. The
 * <code>CertStore</code> that is returned is initialized with the specified
 * parameters. The type of parameters needed may vary between different types
 * of <code>CertStore</code>s.
 *
 * @see CertStore#getInstance
 *
 * @since       1.4
 * @author      Steve Hanna
 */
public interface CertStoreParameters extends Cloneable {

    /**
     * Makes a copy of this <code>CertStoreParameters</code>.
     * <p>
     * The precise meaning of "copy" may depend on the class of
     * the <code>CertStoreParameters</code> object. A typical implementation
     * performs a "deep copy" of this object, but this is not an absolute
     * requirement. Some implementations may perform a "shallow copy" of some
     * or all of the fields of this object.
     * <p>
     * Note that the <code>CertStore.getInstance</code> methods make a copy
     * of the specified <code>CertStoreParameters</code>. A deep copy
     * implementation of <code>clone</code> is safer and more robust, as it
     * prevents the caller from corrupting a shared <code>CertStore</code> by
     * subsequently modifying the contents of its initialization parameters.
     * However, a shallow copy implementation of <code>clone</code> is more
     * appropriate for applications that need to hold a reference to a
     * parameter contained in the <code>CertStoreParameters</code>. For example,
     * a shallow copy clone allows an application to release the resources of
     * a particular <code>CertStore</code> initialization parameter immediately,
     * rather than waiting for the garbage collection mechanism. This should
     * be done with the utmost care, since the <code>CertStore</code> may still
     * be in use by other threads.
     * <p>
     * Each subclass should state the precise behavior of this method so
     * that users and developers know what to expect.
     *
     * @return a copy of this <code>CertStoreParameters</code>
     */
    Object clone();
}
