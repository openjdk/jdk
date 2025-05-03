/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.net.Socket;
import java.security.AlgorithmConstraints;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import javax.net.ssl.SSLEngine;

/**
 * An implementation of X509KeyManager backed by a KeyStore.
 *
 * The backing KeyStore is inspected when this object is constructed.
 * All key entries containing a PrivateKey and a non-empty chain of
 * X509Certificate are then copied into an internal store. This means
 * that subsequent modifications of the KeyStore have no effect on the
 * X509KeyManagerImpl object.
 *
 * Note that this class assumes that all keys are protected by the same
 * password.
 *
 * The JSSE handshake code currently calls into this class via
 * chooseClientAlias() and chooseServerAlias() to find the certificates to
 * use. As implemented here, both always return the first alias returned by
 * getClientAliases() and getServerAliases(). In turn, these methods are
 * implemented by calling getAliases(), which performs the actual lookup.
 *
 * Note that this class currently implements no checking of the local
 * certificates. In particular, it is *not* guaranteed that:
 * . the certificates are within their validity period and not revoked
 * . the signatures verify
 * . they form a PKIX compliant chain.
 * . the certificate extensions allow the certificate to be used for
 * the desired purpose.
 *
 * Chains that fail any of these criteria will probably be rejected by
 * the remote peer.
 */

final class SunX509KeyManagerImpl extends SunX509ConstraintsKeyManagerImpl {

    SunX509KeyManagerImpl(KeyStore ks, char[] password)
            throws KeyStoreException,
            NoSuchAlgorithmException, UnrecoverableKeyException {
        super(ks, password);
    }

    @Override
    public AlgorithmConstraints getAlgorithmConstraints(Socket socket) {
        return null;
    }

    @Override
    public AlgorithmConstraints getAlgorithmConstraints(SSLEngine engine) {
        return null;
    }

    @Override
    public boolean conformsToAlgorithmConstraints(
            AlgorithmConstraints constraints,
            Certificate[] chain, String variant) {
        return true;
    }
}
