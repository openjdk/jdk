/*
 * Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.rsa;

import java.util.Map;

/**
 * Defines the entries of the SunRsaSign provider.
 *
 * @author  Andreas Sterbenz
 */
public final class SunRsaSignEntries {

    private SunRsaSignEntries() {
        // empty
    }

    public static void putEntries(Map<Object, Object> map) {

        // main algorithms

        map.put("KeyFactory.RSA",
                "sun.security.rsa.RSAKeyFactory");
        map.put("KeyPairGenerator.RSA",
                "sun.security.rsa.RSAKeyPairGenerator");
        map.put("Signature.MD2withRSA",
                "sun.security.rsa.RSASignature$MD2withRSA");
        map.put("Signature.MD5withRSA",
                "sun.security.rsa.RSASignature$MD5withRSA");
        map.put("Signature.SHA1withRSA",
                "sun.security.rsa.RSASignature$SHA1withRSA");
        map.put("Signature.SHA256withRSA",
                "sun.security.rsa.RSASignature$SHA256withRSA");
        map.put("Signature.SHA384withRSA",
                "sun.security.rsa.RSASignature$SHA384withRSA");
        map.put("Signature.SHA512withRSA",
                "sun.security.rsa.RSASignature$SHA512withRSA");

        // attributes for supported key classes

        String rsaKeyClasses = "java.security.interfaces.RSAPublicKey" +
                "|java.security.interfaces.RSAPrivateKey";
        map.put("Signature.MD2withRSA SupportedKeyClasses", rsaKeyClasses);
        map.put("Signature.MD5withRSA SupportedKeyClasses", rsaKeyClasses);
        map.put("Signature.SHA1withRSA SupportedKeyClasses", rsaKeyClasses);
        map.put("Signature.SHA256withRSA SupportedKeyClasses", rsaKeyClasses);
        map.put("Signature.SHA384withRSA SupportedKeyClasses", rsaKeyClasses);
        map.put("Signature.SHA512withRSA SupportedKeyClasses", rsaKeyClasses);

        // aliases

        map.put("Alg.Alias.KeyFactory.1.2.840.113549.1.1",     "RSA");
        map.put("Alg.Alias.KeyFactory.OID.1.2.840.113549.1.1", "RSA");

        map.put("Alg.Alias.KeyPairGenerator.1.2.840.113549.1.1",     "RSA");
        map.put("Alg.Alias.KeyPairGenerator.OID.1.2.840.113549.1.1", "RSA");

        map.put("Alg.Alias.Signature.1.2.840.113549.1.1.2",     "MD2withRSA");
        map.put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.2", "MD2withRSA");

        map.put("Alg.Alias.Signature.1.2.840.113549.1.1.4",     "MD5withRSA");
        map.put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.4", "MD5withRSA");

        map.put("Alg.Alias.Signature.1.2.840.113549.1.1.5",     "SHA1withRSA");
        map.put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.5", "SHA1withRSA");
        map.put("Alg.Alias.Signature.1.3.14.3.2.29",            "SHA1withRSA");

        map.put("Alg.Alias.Signature.1.2.840.113549.1.1.11",     "SHA256withRSA");
        map.put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.11", "SHA256withRSA");

        map.put("Alg.Alias.Signature.1.2.840.113549.1.1.12",     "SHA384withRSA");
        map.put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.12", "SHA384withRSA");

        map.put("Alg.Alias.Signature.1.2.840.113549.1.1.13",     "SHA512withRSA");
        map.put("Alg.Alias.Signature.OID.1.2.840.113549.1.1.13", "SHA512withRSA");

    }
}
