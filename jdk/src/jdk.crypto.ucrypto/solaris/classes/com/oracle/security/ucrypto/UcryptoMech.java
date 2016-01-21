/**
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @since 9
 */
// Check /usr/include/libsoftcrypto.h for updates
public enum UcryptoMech {

    CRYPTO_AES_ECB(1, new ServiceDesc[]
        { sd("Cipher", "AES/ECB/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesEcbNoPadding"),
          sd("Cipher", "AES/ECB/PKCS5Padding", "com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesEcbPKCS5",
             "AES"),
          sd("Cipher", "AES_128/ECB/NoPadding", "com.oracle.security.ucrypto.NativeCipher$Aes128EcbNoPadding",
             "2.16.840.1.101.3.4.1.1", "OID.2.16.840.1.101.3.4.1.1"),
          sd("Cipher", "AES_192/ECB/NoPadding", "com.oracle.security.ucrypto.NativeCipher$Aes192EcbNoPadding",
             "2.16.840.1.101.3.4.1.21", "OID.2.16.840.1.101.3.4.1.21"),
          sd("Cipher", "AES_256/ECB/NoPadding", "com.oracle.security.ucrypto.NativeCipher$Aes256EcbNoPadding",
             "2.16.840.1.101.3.4.1.41", "OID.2.16.840.1.101.3.4.1.41")
        }),
    CRYPTO_AES_CBC(2, new ServiceDesc[]
        { sd("Cipher", "AES/CBC/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCbcNoPadding"),
          sd("Cipher", "AES/CBC/PKCS5Padding", "com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesCbcPKCS5"),
          sd("Cipher", "AES_128/CBC/NoPadding", "com.oracle.security.ucrypto.NativeCipher$Aes128CbcNoPadding",
             "2.16.840.1.101.3.4.1.2", "OID.2.16.840.1.101.3.4.1.2"),
          sd("Cipher", "AES_192/CBC/NoPadding", "com.oracle.security.ucrypto.NativeCipher$Aes192CbcNoPadding",
             "2.16.840.1.101.3.4.1.22", "OID.2.16.840.1.101.3.4.1.22"),
          sd("Cipher", "AES_256/CBC/NoPadding", "com.oracle.security.ucrypto.NativeCipher$Aes256CbcNoPadding",
             "2.16.840.1.101.3.4.1.42", "OID.2.16.840.1.101.3.4.1.42")
        }),
    CRYPTO_AES_CBC_PAD(3, null), // No support from Solaris yet
    CRYPTO_AES_CTR(4, new ServiceDesc[]
        { sd("Cipher", "AES/CTR/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCtrNoPadding") }),
    CRYPTO_AES_CCM(5, null), // Cannot support due to lack of Java API which corresponds to CK_AES_CCM_PARAMS
    CRYPTO_AES_GCM(6, new ServiceDesc[]
        { sd("Cipher", "AES/GCM/NoPadding", "com.oracle.security.ucrypto.NativeGCMCipher$AesGcmNoPadding"),
          sd("Cipher", "AES_128/GCM/NoPadding", "com.oracle.security.ucrypto.NativeGCMCipher$Aes128GcmNoPadding",
             "2.16.840.1.101.3.4.1.6", "OID.2.16.840.1.101.3.4.1.6"),
          sd("Cipher", "AES_192/GCM/NoPadding", "com.oracle.security.ucrypto.NativeGCMCipher$Aes192GcmNoPadding",
             "2.16.840.1.101.3.4.1.26", "OID.2.16.840.1.101.3.4.1.26"),
          sd("Cipher", "AES_256/GCM/NoPadding", "com.oracle.security.ucrypto.NativeGCMCipher$Aes256GcmNoPadding",
             "2.16.840.1.101.3.4.1.46", "OID.2.16.840.1.101.3.4.1.46")
        }),
    CRYPTO_AES_GMAC(7, null), // No support from Solaris yet
    CRYPTO_AES_CFB128(8, new ServiceDesc[]
        { sd("Cipher", "AES/CFB128/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCfb128NoPadding"),
          sd("Cipher", "AES/CFB128/PKCS5Padding", "com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesCfb128PKCS5") }),
    CRYPTO_RSA_PKCS(31, new ServiceDesc[]
        { sd("Cipher", "RSA/ECB/PKCS1Padding", "com.oracle.security.ucrypto.NativeRSACipher$PKCS1Padding",
             "RSA") }),
    CRYPTO_RSA_X_509(32, new ServiceDesc[]
        { sd("Cipher", "RSA/ECB/NoPadding", "com.oracle.security.ucrypto.NativeRSACipher$NoPadding") }),
    CRYPTO_MD5_RSA_PKCS(33, new ServiceDesc[]
        { sd("Signature", "MD5withRSA", "com.oracle.security.ucrypto.NativeRSASignature$MD5",
             "1.2.840.113549.1.1.4", "OID.1.2.840.113549.1.1.4") }),
    CRYPTO_SHA1_RSA_PKCS(34, new ServiceDesc[]
        { sd("Signature", "SHA1withRSA", "com.oracle.security.ucrypto.NativeRSASignature$SHA1",
             "1.2.840.113549.1.1.5", "OID.1.2.840.113549.1.1.5",
             "1.3.14.3.2.29") }),
    CRYPTO_SHA256_RSA_PKCS(35, new ServiceDesc[]
        { sd("Signature", "SHA256withRSA", "com.oracle.security.ucrypto.NativeRSASignature$SHA256",
             "1.2.840.113549.1.1.11", "OID.1.2.840.113549.1.1.11") }),
    CRYPTO_SHA384_RSA_PKCS(36, new ServiceDesc[]
        { sd("Signature", "SHA384withRSA", "com.oracle.security.ucrypto.NativeRSASignature$SHA384",
             "1.2.840.113549.1.1.12", "OID.1.2.840.113549.1.1.12") }),
    CRYPTO_SHA512_RSA_PKCS(37, new ServiceDesc[]
        { sd("Signature", "SHA512withRSA", "com.oracle.security.ucrypto.NativeRSASignature$SHA512",
             "1.2.840.113549.1.1.13", "OID.1.2.840.113549.1.1.13") });

    private final int mech;
    private final ServiceDesc[] serviceDescs;

    private static ServiceDesc sd(String type, String algo, String cn, String... aliases) {
        return new ServiceDesc(type, algo, cn, aliases);
    }

    UcryptoMech(int mech, ServiceDesc[] serviceDescs) {
        this.mech = mech;
        this.serviceDescs = serviceDescs;
    }

    public int value() { return mech; }
    public ServiceDesc[] getServiceDescriptions() { return serviceDescs; }
}
