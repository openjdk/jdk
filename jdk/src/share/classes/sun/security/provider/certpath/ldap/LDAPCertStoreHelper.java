/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath.ldap;

import java.net.URI;
import java.util.Collection;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.CertStore;
import java.security.cert.X509CertSelector;
import java.security.cert.X509CRLSelector;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;

import sun.security.provider.certpath.CertStoreHelper;

/**
 * LDAP implementation of CertStoreHelper.
 */

public class LDAPCertStoreHelper
    implements CertStoreHelper
{
    public LDAPCertStoreHelper() { }

    @Override
    public CertStore getCertStore(URI uri)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException
    {
        return LDAPCertStore.getInstance(LDAPCertStore.getParameters(uri));
    }

    @Override
    public X509CertSelector wrap(X509CertSelector selector,
                                 X500Principal certSubject,
                                 String ldapDN)
        throws IOException
    {
        return new LDAPCertStore.LDAPCertSelector(selector, certSubject, ldapDN);
    }

    @Override
    public X509CRLSelector wrap(X509CRLSelector selector,
                                Collection<X500Principal> certIssuers,
                                String ldapDN)
        throws IOException
    {
        return new LDAPCertStore.LDAPCRLSelector(selector, certIssuers, ldapDN);
    }
}
