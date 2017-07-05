/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Enum for representing the ucrypto mechanisms.
 *
 * @since 9
 */
// Check /usr/include/libsoftcrypto.h for updates
public enum UcryptoMech {

    CRYPTO_AES_ECB(new ServiceDesc[]
        { sd("Cipher", "AES/ECB/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesEcbNoPadding"),
          sd("Cipher", "AES/ECB/PKCS5Padding", "com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesEcbPKCS5",
             "AES"),
          sd("Cipher", "AES_128/ECB/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesEcbNoPadding",
             "2.16.840.1.101.3.4.1.1", "OID.2.16.840.1.101.3.4.1.1"),
          sd("Cipher", "AES_192/ECB/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesEcbNoPadding",
             "2.16.840.1.101.3.4.1.21", "OID.2.16.840.1.101.3.4.1.21"),
          sd("Cipher", "AES_256/ECB/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesEcbNoPadding",
             "2.16.840.1.101.3.4.1.41", "OID.2.16.840.1.101.3.4.1.41")
        }),
    CRYPTO_AES_CBC(new ServiceDesc[]
        { sd("Cipher", "AES/CBC/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCbcNoPadding"),
          sd("Cipher", "AES/CBC/PKCS5Padding", "com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesCbcPKCS5"),
          sd("Cipher", "AES_128/CBC/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCbcNoPadding",
             "2.16.840.1.101.3.4.1.2", "OID.2.16.840.1.101.3.4.1.2"),
          sd("Cipher", "AES_192/CBC/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCbcNoPadding",
             "2.16.840.1.101.3.4.1.22", "OID.2.16.840.1.101.3.4.1.22"),
          sd("Cipher", "AES_256/CBC/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCbcNoPadding",
             "2.16.840.1.101.3.4.1.42", "OID.2.16.840.1.101.3.4.1.42")
        }),
//  CRYPTO_AES_CBC_PAD(null), // Support added since S11.1; however we still use CRYPTO_AES_CBC due to known bug
    CRYPTO_AES_CTR(new ServiceDesc[]
        { sd("Cipher", "AES/CTR/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCtrNoPadding") }),
//  CRYPTO_AES_CCM(null), // Need Java API for CK_AES_CCM_PARAMS
    CRYPTO_AES_GCM(new ServiceDesc[]
        { sd("Cipher", "AES/GCM/NoPadding", "com.oracle.security.ucrypto.NativeGCMCipher$AesGcmNoPadding"),
          sd("Cipher", "AES_128/GCM/NoPadding", "com.oracle.security.ucrypto.NativeGCMCipher$AesGcmNoPadding",
             "2.16.840.1.101.3.4.1.6", "OID.2.16.840.1.101.3.4.1.6"),
          sd("Cipher", "AES_192/GCM/NoPadding", "com.oracle.security.ucrypto.NativeGCMCipher$AesGcmNoPadding",
             "2.16.840.1.101.3.4.1.26", "OID.2.16.840.1.101.3.4.1.26"),
          sd("Cipher", "AES_256/GCM/NoPadding", "com.oracle.security.ucrypto.NativeGCMCipher$AesGcmNoPadding",
             "2.16.840.1.101.3.4.1.46", "OID.2.16.840.1.101.3.4.1.46")
        }),
//  CRYPTO_AES_GMAC(null), // No support from Solaris
    CRYPTO_AES_CFB128(new ServiceDesc[]
        { sd("Cipher", "AES/CFB128/NoPadding", "com.oracle.security.ucrypto.NativeCipher$AesCfb128NoPadding"),
          sd("Cipher", "AES/CFB128/PKCS5Padding", "com.oracle.security.ucrypto.NativeCipherWithJavaPadding$AesCfb128PKCS5")
        }),

    CRYPTO_RSA_PKCS(new ServiceDesc[]
        { sd("Cipher", "RSA/ECB/PKCS1Padding", "com.oracle.security.ucrypto.NativeRSACipher$PKCS1Padding",
             "RSA")
        }),
    CRYPTO_RSA_X_509(new ServiceDesc[]
        { sd("Cipher", "RSA/ECB/NoPadding", "com.oracle.security.ucrypto.NativeRSACipher$NoPadding") }),
    CRYPTO_MD5_RSA_PKCS(new ServiceDesc[]
        { sd("Signature", "MD5withRSA", "com.oracle.security.ucrypto.NativeRSASignature$MD5",
             "1.2.840.113549.1.1.4", "OID.1.2.840.113549.1.1.4")
        }),
    CRYPTO_SHA1_RSA_PKCS(new ServiceDesc[]
        { sd("Signature", "SHA1withRSA", "com.oracle.security.ucrypto.NativeRSASignature$SHA1",
             "1.2.840.113549.1.1.5", "OID.1.2.840.113549.1.1.5",
             "1.3.14.3.2.29")
        }),
    CRYPTO_SHA256_RSA_PKCS(new ServiceDesc[]
        { sd("Signature", "SHA256withRSA", "com.oracle.security.ucrypto.NativeRSASignature$SHA256",
             "1.2.840.113549.1.1.11", "OID.1.2.840.113549.1.1.11")
        }),
    CRYPTO_SHA384_RSA_PKCS(new ServiceDesc[]
        { sd("Signature", "SHA384withRSA", "com.oracle.security.ucrypto.NativeRSASignature$SHA384",
             "1.2.840.113549.1.1.12", "OID.1.2.840.113549.1.1.12")
        }),
    CRYPTO_SHA512_RSA_PKCS(new ServiceDesc[]
        { sd("Signature", "SHA512withRSA", "com.oracle.security.ucrypto.NativeRSASignature$SHA512",
             "1.2.840.113549.1.1.13", "OID.1.2.840.113549.1.1.13")
        }),

    CRYPTO_MD5(new ServiceDesc[]
        { sd("MessageDigest", "MD5", "com.oracle.security.ucrypto.NativeDigest$MD5") }),
    CRYPTO_SHA1(new ServiceDesc[]
        { sd("MessageDigest", "SHA", "com.oracle.security.ucrypto.NativeDigest$SHA1", "SHA-1", "SHA1") }),
    CRYPTO_SHA224(new ServiceDesc[]
        { sd("MessageDigest", "SHA-224", "com.oracle.security.ucrypto.NativeDigest$SHA224",
             "2.16.840.1.101.3.4.2.4", "OID.2.16.840.1.101.3.4.2.4")
    }),
    CRYPTO_SHA256(new ServiceDesc[]
        { sd("MessageDigest", "SHA-256", "com.oracle.security.ucrypto.NativeDigest$SHA256",
             "2.16.840.1.101.3.4.2.1", "OID.2.16.840.1.101.3.4.2.1")
    }),
    CRYPTO_SHA384(new ServiceDesc[]
        { sd("MessageDigest", "SHA-384", "com.oracle.security.ucrypto.NativeDigest$SHA384",
             "2.16.840.1.101.3.4.2.2", "OID.2.16.840.1.101.3.4.2.2")
    }),
    CRYPTO_SHA512(new ServiceDesc[]
        { sd("MessageDigest", "SHA-512", "com.oracle.security.ucrypto.NativeDigest$SHA512",
             "2.16.840.1.101.3.4.2.3", "OID.2.16.840.1.101.3.4.2.3")
    }),
    CRYPTO_SHA3_224(new ServiceDesc[]
        { sd("MessageDigest", "SHA3-224", "com.oracle.security.ucrypto.NativeDigest$SHA3_224",
             "2.16.840.1.101.3.4.2.7", "OID.2.16.840.1.101.3.4.2.7")
    }),
    CRYPTO_SHA3_256(new ServiceDesc[]
        { sd("MessageDigest", "SHA3-256", "com.oracle.security.ucrypto.NativeDigest$SHA3_256",
             "2.16.840.1.101.3.4.2.8", "OID.2.16.840.1.101.3.4.2.8")
    }),
    CRYPTO_SHA3_384(new ServiceDesc[]
        { sd("MessageDigest", "SHA3-384", "com.oracle.security.ucrypto.NativeDigest$SHA3_384",
             "2.16.840.1.101.3.4.2.9", "OID.2.16.840.1.101.3.4.2.9")
    }),
    CRYPTO_SHA3_512(new ServiceDesc[]
        { sd("MessageDigest", "SHA3-512", "com.oracle.security.ucrypto.NativeDigest$SHA3_512",
             "2.16.840.1.101.3.4.2.10", "OID.2.16.840.1.101.3.4.2.10")
    });

    private int mech = 0;
    private final ServiceDesc[] serviceDescs;

    private static ServiceDesc sd(String type, String algo, String cn, String... aliases) {
        return new ServiceDesc(type, algo, cn, aliases);
    }

    UcryptoMech(ServiceDesc[] serviceDescs) {
        this.serviceDescs = serviceDescs;
    }

    public void setValue(int nativeMechValue) {
        this.mech = nativeMechValue;
    }

    public int value() { return mech; }
    public ServiceDesc[] getServiceDescriptions() { return serviceDescs; }
}
