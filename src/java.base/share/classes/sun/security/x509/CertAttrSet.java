/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.x509;

import sun.security.util.DerOutputStream;

import java.io.IOException;
import java.security.cert.CertificateException;

/**
 * This interface defines the methods required of a certificate attribute.
 * Examples of X.509 certificate attributes are Validity, Issuer_Name, and
 * Subject Name. A CertAttrSet may comprise one attribute or many
 * attributes.
 * <p>
 * A CertAttrSet itself can also be comprised of other sub-sets.
 * In the case of X.509 V3 certificates, for example, the "extensions"
 * attribute has subattributes, such as those for KeyUsage and
 * AuthorityKeyIdentifier.
 *
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 * @see CertificateException
 */
public interface CertAttrSet<T> {

    /**
     * Encodes the attribute to the output stream in a format
     * that can be parsed by the <code>decode</code> method.
     *
     * @param out the DerOutputStream to encode the attribute to.
     *
     * @exception CertificateException on encoding or validity errors.
     * @exception IOException on other errors.
     */
    void encode(DerOutputStream out)
        throws CertificateException, IOException;

    /**
     * Sets an attribute value within this CertAttrSet.
     *
     * @param name the name of the attribute (e.g. "x509.info.key")
     * @param obj the attribute object.
     *
     * @exception CertificateException on attribute handling errors.
     * @exception IOException on other errors.
     */
    void set(String name, Object obj)
        throws CertificateException, IOException;

    /**
     * Gets an attribute value for this CertAttrSet.
     *
     * @param name the name of the attribute to return.
     *
     * @exception CertificateException on attribute handling errors.
     * @exception IOException on other errors.
     */
    Object get(String name)
        throws CertificateException, IOException;
}
