/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.mscapi;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.ProviderException;
import java.util.HashMap;
import java.util.Map;

import sun.security.action.PutAllAction;


/**
 * A Cryptographic Service Provider for the Microsoft Crypto API.
 *
 * @since 1.6
 */

public final class SunMSCAPI extends Provider {

    private static final long serialVersionUID = 8622598936488630849L; //TODO

    private static final String INFO = "Sun's Microsoft Crypto API provider";

    static {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                System.loadLibrary("sunmscapi");
                return null;
            }
        });
    }

    public SunMSCAPI() {
        super("SunMSCAPI", 1.7d, INFO);

        // if there is no security manager installed, put directly into
        // the provider. Otherwise, create a temporary map and use a
        // doPrivileged() call at the end to transfer the contents
        final Map map = (System.getSecurityManager() == null)
                        ? (Map)this : new HashMap();

        /*
         * Secure random
         */
        map.put("SecureRandom.Windows-PRNG", "sun.security.mscapi.PRNG");

        /*
         * Key store
         */
        map.put("KeyStore.Windows-MY", "sun.security.mscapi.KeyStore$MY");
        map.put("KeyStore.Windows-ROOT", "sun.security.mscapi.KeyStore$ROOT");

        /*
         * Signature engines
         */
        // NONEwithRSA must be supplied with a pre-computed message digest.
        // Only the following digest algorithms are supported: MD5, SHA-1,
        // SHA-256, SHA-384, SHA-512 and a special-purpose digest algorithm
        // which is a concatenation of SHA-1 and MD5 digests.
        map.put("Signature.NONEwithRSA",
            "sun.security.mscapi.RSASignature$Raw");
        map.put("Signature.SHA1withRSA",
            "sun.security.mscapi.RSASignature$SHA1");
        map.put("Signature.SHA256withRSA",
            "sun.security.mscapi.RSASignature$SHA256");
        map.put("Signature.SHA384withRSA",
            "sun.security.mscapi.RSASignature$SHA384");
        map.put("Signature.SHA512withRSA",
            "sun.security.mscapi.RSASignature$SHA512");
        map.put("Signature.MD5withRSA",
            "sun.security.mscapi.RSASignature$MD5");
        map.put("Signature.MD2withRSA",
            "sun.security.mscapi.RSASignature$MD2");

        // supported key classes
        map.put("Signature.NONEwithRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.SHA1withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.SHA256withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.SHA384withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.SHA512withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.MD5withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.MD2withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");

        /*
         * Key Pair Generator engines
         */
        map.put("KeyPairGenerator.RSA",
            "sun.security.mscapi.RSAKeyPairGenerator");
        map.put("KeyPairGenerator.RSA KeySize", "1024");

        /*
         * Cipher engines
         */
        map.put("Cipher.RSA", "sun.security.mscapi.RSACipher");
        map.put("Cipher.RSA/ECB/PKCS1Padding",
            "sun.security.mscapi.RSACipher");
        map.put("Cipher.RSA SupportedModes", "ECB");
        map.put("Cipher.RSA SupportedPaddings", "PKCS1PADDING");
        map.put("Cipher.RSA SupportedKeyClasses", "sun.security.mscapi.Key");

        if (map != this) {
            AccessController.doPrivileged(new PutAllAction(this, map));
        }
    }
}
