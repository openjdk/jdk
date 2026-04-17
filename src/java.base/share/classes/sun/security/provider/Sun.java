/*
 * Copyright (c) 1996, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.provider;

import java.util.*;
import java.security.*;

import static sun.security.util.SecurityConstants.PROVIDER_VER;


/**
 * The SUN Security Provider.
 *
 */
public final class Sun extends Provider {

    @java.io.Serial
    private static final long serialVersionUID = 6440182097568097204L;

    private static final String INFO = "SUN " +
    "(DSA key/parameter generation; DSA signing; SHA-1, MD5 digests; " +
    "SecureRandom; X.509 certificates; PKCS12, JKS & DKS keystores; " +
    "PKIX CertPathValidator; " +
    "PKIX CertPathBuilder; LDAP, Collection CertStores, JavaPolicy Policy; " +
    "JavaLoginConfig Configuration)";

    private static String INFO_NO_CRYPTO = "SUN no_crypto " +
    "(X.509 certificates; PKCS12, JKS & DKS keystores; " +
    "PKIX CertPathValidator; " +
    "PKIX CertPathBuilder; LDAP, Collection CertStores, JavaPolicy Policy; " +
    "JavaLoginConfig Configuration)";

    // Additional JCE crypto services, e.g. Cipher, KDF, are not included
    // in this list since SUN provider does not support them
    private static final Set<String> CRYPTO_SERVICE_TYPES = Set.of(
            "AlgorithmParameterGenerator",
            "AlgorithmParameters",
            "KeyFactory",
            "KeyPairGenerator",
            "MessageDigest",
            "SecureRandom",
            "Signature"
    );

    private transient String info = INFO;

    public Sun() {
        /* We are the SUN provider */
        super("SUN", PROVIDER_VER, INFO);

        Provider p = this;
        Iterator<Provider.Service> serviceIter = new SunEntries(p).iterator();

        while (serviceIter.hasNext()) {
            putService(serviceIter.next());
        }
    }

    @Override
    public Provider configure(String option) {
        if ("no_crypto".equalsIgnoreCase(option)) {
            getServices().stream()
                    .filter(s -> CRYPTO_SERVICE_TYPES.contains(s.getType()))
                    .forEach(this::removeService);
            info = INFO_NO_CRYPTO;
        } else {
            throw new ProviderException("Unsupported configuration option " +
                    option);
        }
        return this;
    }

    @Override
    public String getInfo() {
        return info;
    }
}
