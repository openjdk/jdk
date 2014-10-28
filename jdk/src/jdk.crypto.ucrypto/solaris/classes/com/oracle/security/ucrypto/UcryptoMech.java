/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.security.ucrypto;

import java.util.HashMap;

/**
 * Enum for representing the ucrypto mechanisms.
 *
 * @since 1.9
 */
// Check /usr/include/libsoftcrypto.h for updates
public enum UcryptoMech {
    CRYPTO_AES_ECB(1, new String[]
        { "Cipher.AES/ECB/NoPadding;com.oracle.security.ucrypto.NativeCipher$AesEcbNoPadding",
          "Cipher.AES/ECB/PKCS5Padding;com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesEcbPKCS5",
          "Cipher.AES_128/ECB/NoPadding;com.oracle.security.ucrypto.NativeCipher$Aes128EcbNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.1;AES_128/ECB/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.1;AES_128/ECB/NoPadding",
          "Cipher.AES_192/ECB/NoPadding;com.oracle.security.ucrypto.NativeCipher$Aes192EcbNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.21;AES_192/ECB/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.21;AES_192/ECB/NoPadding",
          "Cipher.AES_256/ECB/NoPadding;com.oracle.security.ucrypto.NativeCipher$Aes256EcbNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.41;AES_256/ECB/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.41;AES_256/ECB/NoPadding"
        }),
    CRYPTO_AES_CBC(2, new String[]
        { "Cipher.AES/CBC/NoPadding;com.oracle.security.ucrypto.NativeCipher$AesCbcNoPadding",
          "Cipher.AES/CBC/PKCS5Padding;com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesCbcPKCS5",
          "Cipher.AES_128/CBC/NoPadding;com.oracle.security.ucrypto.NativeCipher$Aes128CbcNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.2;AES_128/CBC/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.2;AES_128/CBC/NoPadding",
          "Cipher.AES_192/CBC/NoPadding;com.oracle.security.ucrypto.NativeCipher$Aes192CbcNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.22;AES_192/CBC/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.22;AES_192/CBC/NoPadding",
          "Cipher.AES_256/CBC/NoPadding;com.oracle.security.ucrypto.NativeCipher$Aes256CbcNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.42;AES_256/CBC/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.42;AES_256/CBC/NoPadding"
        }),
    CRYPTO_AES_CBC_PAD(3, null), // No support from Solaris yet
    CRYPTO_AES_CTR(4, new String[]
        { "Cipher.AES/CTR/NoPadding;com.oracle.security.ucrypto.NativeCipher$AesCtrNoPadding" }),
    CRYPTO_AES_CCM(5, null), // Cannot support due to lack of Java API which corresponds to CK_AES_CCM_PARAMS
    CRYPTO_AES_GCM(6, new String[]
        { "Cipher.AES/GCM/NoPadding;com.oracle.security.ucrypto.NativeGCMCipher$AesGcmNoPadding",
          "Cipher.AES_128/GCM/NoPadding;com.oracle.security.ucrypto.NativeGCMCipher$Aes128GcmNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.6;AES_128/GCM/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.6;AES_128/GCM/NoPadding",
          "Cipher.AES_192/GCM/NoPadding;com.oracle.security.ucrypto.NativeGCMCipher$Aes192GcmNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.26;AES_192/GCM/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.26;AES_192/GCM/NoPadding",
          "Cipher.AES_256/GCM/NoPadding;com.oracle.security.ucrypto.NativeGCMCipher$Aes256GcmNoPadding",
          "Alg.Alias.Cipher.2.16.840.1.101.3.4.1.46;AES_256/GCM/NoPadding",
          "Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.46;AES_256/GCM/NoPadding",
        }),
    CRYPTO_AES_GMAC(7, null), // No support from Solaris yet
    CRYPTO_AES_CFB128(8, new String[]
        { "Cipher.AES/CFB128/NoPadding;com.oracle.security.ucrypto.NativeCipher$AesCfb128NoPadding",
          "Cipher.AES/CFB128/PKCS5Padding;com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesCfb128PKCS5" }),
    CRYPTO_RSA_PKCS(31, new String[]
        { "Cipher.RSA/ECB/PKCS1Padding;com.oracle.security.ucrypto.NativeRSACipher$PKCS1Padding" }),
    CRYPTO_RSA_X_509(32, new String[]
        { "Cipher.RSA/ECB/NoPadding;com.oracle.security.ucrypto.NativeRSACipher$NoPadding" }),
    CRYPTO_MD5_RSA_PKCS(33, new String[]
        { "Signature.MD5withRSA;com.oracle.security.ucrypto.NativeRSASignature$MD5",
          "Alg.Alias.Signature.1.2.840.113549.1.1.4;MD5withRSA",
          "Alg.Alias.Signature.OID.1.2.840.113549.1.1.4;MD5withRSA" }),
    CRYPTO_SHA1_RSA_PKCS(34, new String[]
        { "Signature.SHA1withRSA;com.oracle.security.ucrypto.NativeRSASignature$SHA1",
          "Alg.Alias.Signature.1.2.840.113549.1.1.5;SHA1withRSA",
          "Alg.Alias.Signature.OID.1.2.840.113549.1.1.5;SHA1withRSA",
          "Alg.Alias.Signature.1.3.14.3.2.29;SHA1withRSA" }),
    CRYPTO_SHA256_RSA_PKCS(35, new String[]
        { "Signature.SHA256withRSA;com.oracle.security.ucrypto.NativeRSASignature$SHA256",
          "Alg.Alias.Signature.1.2.840.113549.1.1.11;SHA256withRSA",
          "Alg.Alias.Signature.OID.1.2.840.113549.1.1.11;SHA256withRSA" }),
    CRYPTO_SHA384_RSA_PKCS(36, new String[]
        { "Signature.SHA384withRSA;com.oracle.security.ucrypto.NativeRSASignature$SHA384",
          "Alg.Alias.Signature.1.2.840.113549.1.1.12;SHA384withRSA",
          "Alg.Alias.Signature.OID.1.2.840.113549.1.1.12;SHA384withRSA" }),
    CRYPTO_SHA512_RSA_PKCS(37, new String[]
        { "Signature.SHA512withRSA;com.oracle.security.ucrypto.NativeRSASignature$SHA512",
          "Alg.Alias.Signature.1.2.840.113549.1.1.13;SHA512withRSA",
          "Alg.Alias.Signature.OID.1.2.840.113549.1.1.13;SHA512withRSA" });

    private int mech;
    private String[] jceProps;

    UcryptoMech(int mech, String[] jceProps) {
        this.mech = mech;
        this.jceProps = jceProps;
    }

    public int value() { return mech; }
    public String[] jceProperties() { return jceProps; }
}
