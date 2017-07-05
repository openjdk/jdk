/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
        map.put("Signature.SHA1withRSA",
            "sun.security.mscapi.RSASignature$SHA1");
        map.put("Signature.MD5withRSA",
            "sun.security.mscapi.RSASignature$MD5");
        map.put("Signature.MD2withRSA",
            "sun.security.mscapi.RSASignature$MD2");

        // supported key classes
        map.put("Signature.SHA1withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.MD5withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.MD2withRSA SupportedKeyClasses",
            "sun.security.mscapi.Key");
        map.put("Signature.NONEwithRSA SupportedKeyClasses",
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
