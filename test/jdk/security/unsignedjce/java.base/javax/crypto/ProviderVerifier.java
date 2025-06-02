/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.URL;
import java.security.Provider;

/**
 * This class is included here to enable testing of Delayed Provider Selection
 * by certain KDF tests. It only stubs out the necessary methods.
 *
 * @since 24
 */
final class ProviderVerifier {

    private final CryptoPermissions appPerms = null;

    /**
     * Creates a {@code ProviderVerifier} object to verify the given URL.
     *
     * @param jarURL the JAR file to be verified.
     * @param savePerms if {@code true}, save the permissions allowed by the
     *          exemption mechanism
     */
    ProviderVerifier(URL jarURL, boolean savePerms) {
        this(jarURL, null, savePerms);
    }

    /**
     * Creates a {@code ProviderVerifier} object to verify the given URL.
     *
     * @param jarURL the JAR file to be verified
     * @param provider the corresponding provider.
     * @param savePerms if {@code true}, save the permissions allowed by the
     *          exemption mechanism
     */
    ProviderVerifier(URL jarURL, Provider provider, boolean savePerms) {
        // The URL for the JAR file we want to verify.
    }

    /**
     * Only a stub is needed for the Delayed Provider Selection test.
     */
    void verify() throws IOException { return; }

    /**
     * Verify that the provided certs include the
     * framework signing certificate.
     *
     * @param certs the list of certs to be checked.
     * @throws Exception if the list of certs did not contain
     *          the framework signing certificate
     */
    static void verifyPolicySigned(java.security.cert.Certificate[] certs)
            throws Exception {
    }

    /**
     * Returns {@code true} if the given provider is JDK trusted crypto provider
     * if the implementation supports fast-path verification.
     */
    static boolean isTrustedCryptoProvider(Provider provider) {
        return false;
    }

    /**
     * Returns the permissions which are bundled with the JAR file,
     * aka the "cryptoperms" file.
     * <p>
     * NOTE: if this {@code ProviderVerifier} instance is constructed
     * with "savePerms" equal to {@code false}, then this method would always
     * return {@code null}.
     */
    CryptoPermissions getPermissions() {
        return appPerms;
    }
}
