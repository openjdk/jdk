/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider.certpath.ssl;

import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.X509CertSelector;
import java.security.cert.X509CRLSelector;
import java.util.Collection;
import javax.security.auth.x500.X500Principal;

import sun.security.provider.certpath.CertStoreHelper;

/**
 * SSL implementation of CertStoreHelper.
 */
public final class SSLServerCertStoreHelper extends CertStoreHelper {

    @Override
    public CertStore getCertStore(URI uri)
        throws NoSuchAlgorithmException, InvalidAlgorithmParameterException
    {
        return SSLServerCertStore.getInstance(uri);
    }

    @Override
    public X509CertSelector wrap(X509CertSelector selector,
                                 X500Principal certSubject,
                                 String ldapDN)
        throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public X509CRLSelector wrap(X509CRLSelector selector,
                                Collection<X500Principal> certIssuers,
                                String ldapDN)
        throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCausedByNetworkIssue(CertStoreException e) {
        Throwable t = e.getCause();
        return (t != null && t instanceof IOException);
    }
}
