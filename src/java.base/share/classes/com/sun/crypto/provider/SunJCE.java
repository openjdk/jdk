/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.crypto.provider;

import java.security.AccessController;
import java.security.Provider;
import java.security.SecureRandom;
import static sun.security.util.SecurityConstants.PROVIDER_VER;


/**
 * The "SunJCE" Cryptographic Service Provider.
 *
 * @author Jan Luehe
 * @author Sharon Liu
 */

/**
 * Defines the "SunJCE" provider.
 *
 * Supported algorithms and their names:
 *
 * - RSA encryption (PKCS#1 v1.5 and raw)
 *
 * - DES
 *
 * - DES-EDE
 *
 * - AES
 *
 * - Blowfish
 *
 * - RC2
 *
 * - ARCFOUR (RC4 compatible)
 *
 * - Cipher modes ECB, CBC, CFB, OFB, PCBC, CTR, and CTS for all block ciphers
 *   and mode GCM for AES cipher
 *
 * - Cipher padding ISO10126Padding for non-PKCS#5 block ciphers and
 *   NoPadding and PKCS5Padding for all block ciphers
 *
 * - Password-based Encryption (PBE)
 *
 * - Diffie-Hellman Key Agreement
 *
 * - HMAC-MD5, HMAC-SHA1, HMAC-SHA-224, HMAC-SHA-256, HMAC-SHA-384, HMAC-SHA-512
 *
 */

public final class SunJCE extends Provider {

    private static final long serialVersionUID = 6812507587804302833L;

    private static final String info = "SunJCE Provider " +
    "(implements RSA, DES, Triple DES, AES, Blowfish, ARCFOUR, RC2, PBE, "
    + "Diffie-Hellman, HMAC)";

    private static final String OID_PKCS12_RC4_128 = "1.2.840.113549.1.12.1.1";
    private static final String OID_PKCS12_RC4_40 = "1.2.840.113549.1.12.1.2";
    private static final String OID_PKCS12_DESede = "1.2.840.113549.1.12.1.3";
    private static final String OID_PKCS12_RC2_128 = "1.2.840.113549.1.12.1.5";
    private static final String OID_PKCS12_RC2_40 = "1.2.840.113549.1.12.1.6";
    private static final String OID_PKCS5_MD5_DES = "1.2.840.113549.1.5.3";
    private static final String OID_PKCS5_PBKDF2 = "1.2.840.113549.1.5.12";
    private static final String OID_PKCS5_PBES2 = "1.2.840.113549.1.5.13";
    private static final String OID_PKCS3 = "1.2.840.113549.1.3.1";

    /* Are we debugging? -- for developers */
    static final boolean debug = false;

    // Instance of this provider, so we don't have to call the provider list
    // to find ourselves or run the risk of not being in the list.
    private static volatile SunJCE instance;

    // lazy initialize SecureRandom to avoid potential recursion if Sun
    // provider has not been installed yet
    private static class SecureRandomHolder {
        static final SecureRandom RANDOM = new SecureRandom();
    }
    static SecureRandom getRandom() { return SecureRandomHolder.RANDOM; }

    public SunJCE() {
        /* We are the "SunJCE" provider */
        super("SunJCE", PROVIDER_VER, info);

        final String BLOCK_MODES = "ECB|CBC|PCBC|CTR|CTS|CFB|OFB" +
            "|CFB8|CFB16|CFB24|CFB32|CFB40|CFB48|CFB56|CFB64" +
            "|OFB8|OFB16|OFB24|OFB32|OFB40|OFB48|OFB56|OFB64";
        final String BLOCK_MODES128 = BLOCK_MODES +
            "|GCM|CFB72|CFB80|CFB88|CFB96|CFB104|CFB112|CFB120|CFB128" +
            "|OFB72|OFB80|OFB88|OFB96|OFB104|OFB112|OFB120|OFB128";
        final String BLOCK_PADS = "NOPADDING|PKCS5PADDING|ISO10126PADDING";

        AccessController.doPrivileged(
            new java.security.PrivilegedAction<>() {
                public Object run() {

                    /*
                     * Cipher engines
                     */
                    put("Cipher.RSA", "com.sun.crypto.provider.RSACipher");
                    put("Cipher.RSA SupportedModes", "ECB");
                    put("Cipher.RSA SupportedPaddings",
                            "NOPADDING|PKCS1PADDING|OAEPPADDING"
                            + "|OAEPWITHMD5ANDMGF1PADDING"
                            + "|OAEPWITHSHA1ANDMGF1PADDING"
                            + "|OAEPWITHSHA-1ANDMGF1PADDING"
                            + "|OAEPWITHSHA-224ANDMGF1PADDING"
                            + "|OAEPWITHSHA-256ANDMGF1PADDING"
                            + "|OAEPWITHSHA-384ANDMGF1PADDING"
                            + "|OAEPWITHSHA-512ANDMGF1PADDING");
                    put("Cipher.RSA SupportedKeyClasses",
                            "java.security.interfaces.RSAPublicKey" +
                            "|java.security.interfaces.RSAPrivateKey");

                    put("Cipher.DES", "com.sun.crypto.provider.DESCipher");
                    put("Cipher.DES SupportedModes", BLOCK_MODES);
                    put("Cipher.DES SupportedPaddings", BLOCK_PADS);
                    put("Cipher.DES SupportedKeyFormats", "RAW");

                    put("Cipher.DESede", "com.sun.crypto.provider.DESedeCipher");
                    put("Alg.Alias.Cipher.TripleDES", "DESede");
                    put("Cipher.DESede SupportedModes", BLOCK_MODES);
                    put("Cipher.DESede SupportedPaddings", BLOCK_PADS);
                    put("Cipher.DESede SupportedKeyFormats", "RAW");

                    put("Cipher.DESedeWrap",
                        "com.sun.crypto.provider.DESedeWrapCipher");
                    put("Cipher.DESedeWrap SupportedModes", "CBC");
                    put("Cipher.DESedeWrap SupportedPaddings", "NOPADDING");
                    put("Cipher.DESedeWrap SupportedKeyFormats", "RAW");

                    // PBES1

                    put("Cipher.PBEWithMD5AndDES",
                        "com.sun.crypto.provider.PBEWithMD5AndDESCipher");
                    put("Alg.Alias.Cipher.OID."+OID_PKCS5_MD5_DES,
                        "PBEWithMD5AndDES");
                    put("Alg.Alias.Cipher."+OID_PKCS5_MD5_DES,
                        "PBEWithMD5AndDES");

                    put("Cipher.PBEWithMD5AndTripleDES",
                        "com.sun.crypto.provider.PBEWithMD5AndTripleDESCipher");

                    put("Cipher.PBEWithSHA1AndDESede",
                        "com.sun.crypto.provider.PKCS12PBECipherCore$" +
                        "PBEWithSHA1AndDESede");
                    put("Alg.Alias.Cipher.OID." + OID_PKCS12_DESede,
                        "PBEWithSHA1AndDESede");
                    put("Alg.Alias.Cipher." + OID_PKCS12_DESede,
                        "PBEWithSHA1AndDESede");

                    put("Cipher.PBEWithSHA1AndRC2_40",
                        "com.sun.crypto.provider.PKCS12PBECipherCore$" +
                        "PBEWithSHA1AndRC2_40");
                    put("Alg.Alias.Cipher.OID." + OID_PKCS12_RC2_40,
                        "PBEWithSHA1AndRC2_40");
                    put("Alg.Alias.Cipher." + OID_PKCS12_RC2_40,
                        "PBEWithSHA1AndRC2_40");

                    put("Cipher.PBEWithSHA1AndRC2_128",
                        "com.sun.crypto.provider.PKCS12PBECipherCore$" +
                        "PBEWithSHA1AndRC2_128");
                    put("Alg.Alias.Cipher.OID." + OID_PKCS12_RC2_128,
                        "PBEWithSHA1AndRC2_128");
                    put("Alg.Alias.Cipher." + OID_PKCS12_RC2_128,
                        "PBEWithSHA1AndRC2_128");

                    put("Cipher.PBEWithSHA1AndRC4_40",
                        "com.sun.crypto.provider.PKCS12PBECipherCore$" +
                        "PBEWithSHA1AndRC4_40");
                    put("Alg.Alias.Cipher.OID." + OID_PKCS12_RC4_40,
                        "PBEWithSHA1AndRC4_40");
                    put("Alg.Alias.Cipher." + OID_PKCS12_RC4_40,
                        "PBEWithSHA1AndRC4_40");

                    put("Cipher.PBEWithSHA1AndRC4_128",
                        "com.sun.crypto.provider.PKCS12PBECipherCore$" +
                        "PBEWithSHA1AndRC4_128");
                    put("Alg.Alias.Cipher.OID." + OID_PKCS12_RC4_128,
                        "PBEWithSHA1AndRC4_128");
                    put("Alg.Alias.Cipher." + OID_PKCS12_RC4_128,
                        "PBEWithSHA1AndRC4_128");

                    //PBES2

                    put("Cipher.PBEWithHmacSHA1AndAES_128",
                        "com.sun.crypto.provider.PBES2Core$HmacSHA1AndAES_128");

                    put("Cipher.PBEWithHmacSHA224AndAES_128",
                        "com.sun.crypto.provider.PBES2Core$" +
                            "HmacSHA224AndAES_128");

                    put("Cipher.PBEWithHmacSHA256AndAES_128",
                        "com.sun.crypto.provider.PBES2Core$" +
                            "HmacSHA256AndAES_128");

                    put("Cipher.PBEWithHmacSHA384AndAES_128",
                        "com.sun.crypto.provider.PBES2Core$" +
                            "HmacSHA384AndAES_128");

                    put("Cipher.PBEWithHmacSHA512AndAES_128",
                        "com.sun.crypto.provider.PBES2Core$" +
                            "HmacSHA512AndAES_128");

                    put("Cipher.PBEWithHmacSHA1AndAES_256",
                        "com.sun.crypto.provider.PBES2Core$HmacSHA1AndAES_256");

                    put("Cipher.PBEWithHmacSHA224AndAES_256",
                        "com.sun.crypto.provider.PBES2Core$" +
                            "HmacSHA224AndAES_256");

                    put("Cipher.PBEWithHmacSHA256AndAES_256",
                        "com.sun.crypto.provider.PBES2Core$" +
                            "HmacSHA256AndAES_256");

                    put("Cipher.PBEWithHmacSHA384AndAES_256",
                        "com.sun.crypto.provider.PBES2Core$" +
                            "HmacSHA384AndAES_256");

                    put("Cipher.PBEWithHmacSHA512AndAES_256",
                        "com.sun.crypto.provider.PBES2Core$" +
                            "HmacSHA512AndAES_256");

                    put("Cipher.Blowfish",
                        "com.sun.crypto.provider.BlowfishCipher");
                    put("Cipher.Blowfish SupportedModes", BLOCK_MODES);
                    put("Cipher.Blowfish SupportedPaddings", BLOCK_PADS);
                    put("Cipher.Blowfish SupportedKeyFormats", "RAW");

                    put("Cipher.AES", "com.sun.crypto.provider.AESCipher$General");
                    put("Alg.Alias.Cipher.Rijndael", "AES");
                    put("Cipher.AES SupportedModes", BLOCK_MODES128);
                    put("Cipher.AES SupportedPaddings", BLOCK_PADS);
                    put("Cipher.AES SupportedKeyFormats", "RAW");

                    put("Cipher.AES_128/ECB/NoPadding", "com.sun.crypto.provider.AESCipher$AES128_ECB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.1", "AES_128/ECB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.1", "AES_128/ECB/NoPadding");
                    put("Cipher.AES_128/CBC/NoPadding", "com.sun.crypto.provider.AESCipher$AES128_CBC_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.2", "AES_128/CBC/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.2", "AES_128/CBC/NoPadding");
                    put("Cipher.AES_128/OFB/NoPadding", "com.sun.crypto.provider.AESCipher$AES128_OFB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.3", "AES_128/OFB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.3", "AES_128/OFB/NoPadding");
                    put("Cipher.AES_128/CFB/NoPadding", "com.sun.crypto.provider.AESCipher$AES128_CFB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.4", "AES_128/CFB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.4", "AES_128/CFB/NoPadding");
                    put("Cipher.AES_128/GCM/NoPadding", "com.sun.crypto.provider.AESCipher$AES128_GCM_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.6", "AES_128/GCM/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.6", "AES_128/GCM/NoPadding");

                    put("Cipher.AES_192/ECB/NoPadding", "com.sun.crypto.provider.AESCipher$AES192_ECB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.21", "AES_192/ECB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.21", "AES_192/ECB/NoPadding");
                    put("Cipher.AES_192/CBC/NoPadding", "com.sun.crypto.provider.AESCipher$AES192_CBC_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.22", "AES_192/CBC/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.22", "AES_192/CBC/NoPadding");
                    put("Cipher.AES_192/OFB/NoPadding", "com.sun.crypto.provider.AESCipher$AES192_OFB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.23", "AES_192/OFB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.23", "AES_192/OFB/NoPadding");
                    put("Cipher.AES_192/CFB/NoPadding", "com.sun.crypto.provider.AESCipher$AES192_CFB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.24", "AES_192/CFB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.24", "AES_192/CFB/NoPadding");
                    put("Cipher.AES_192/GCM/NoPadding", "com.sun.crypto.provider.AESCipher$AES192_GCM_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.26", "AES_192/GCM/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.26", "AES_192/GCM/NoPadding");

                    put("Cipher.AES_256/ECB/NoPadding", "com.sun.crypto.provider.AESCipher$AES256_ECB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.41", "AES_256/ECB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.41", "AES_256/ECB/NoPadding");
                    put("Cipher.AES_256/CBC/NoPadding", "com.sun.crypto.provider.AESCipher$AES256_CBC_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.42", "AES_256/CBC/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.42", "AES_256/CBC/NoPadding");
                    put("Cipher.AES_256/OFB/NoPadding", "com.sun.crypto.provider.AESCipher$AES256_OFB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.43", "AES_256/OFB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.43", "AES_256/OFB/NoPadding");
                    put("Cipher.AES_256/CFB/NoPadding", "com.sun.crypto.provider.AESCipher$AES256_CFB_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.44", "AES_256/CFB/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.44", "AES_256/CFB/NoPadding");
                    put("Cipher.AES_256/GCM/NoPadding", "com.sun.crypto.provider.AESCipher$AES256_GCM_NoPadding");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.46", "AES_256/GCM/NoPadding");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.46", "AES_256/GCM/NoPadding");

                    put("Cipher.AESWrap", "com.sun.crypto.provider.AESWrapCipher$General");
                    put("Cipher.AESWrap SupportedModes", "ECB");
                    put("Cipher.AESWrap SupportedPaddings", "NOPADDING");
                    put("Cipher.AESWrap SupportedKeyFormats", "RAW");

                    put("Cipher.AESWrap_128", "com.sun.crypto.provider.AESWrapCipher$AES128");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.5", "AESWrap_128");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.5", "AESWrap_128");
                    put("Cipher.AESWrap_192", "com.sun.crypto.provider.AESWrapCipher$AES192");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.25", "AESWrap_192");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.25", "AESWrap_192");
                    put("Cipher.AESWrap_256", "com.sun.crypto.provider.AESWrapCipher$AES256");
                    put("Alg.Alias.Cipher.2.16.840.1.101.3.4.1.45", "AESWrap_256");
                    put("Alg.Alias.Cipher.OID.2.16.840.1.101.3.4.1.45", "AESWrap_256");

                    put("Cipher.RC2",
                        "com.sun.crypto.provider.RC2Cipher");
                    put("Cipher.RC2 SupportedModes", BLOCK_MODES);
                    put("Cipher.RC2 SupportedPaddings", BLOCK_PADS);
                    put("Cipher.RC2 SupportedKeyFormats", "RAW");

                    put("Cipher.ARCFOUR",
                        "com.sun.crypto.provider.ARCFOURCipher");
                    put("Alg.Alias.Cipher.RC4", "ARCFOUR");
                    put("Cipher.ARCFOUR SupportedModes", "ECB");
                    put("Cipher.ARCFOUR SupportedPaddings", "NOPADDING");
                    put("Cipher.ARCFOUR SupportedKeyFormats", "RAW");

                    /*
                     * Key(pair) Generator engines
                     */
                    put("KeyGenerator.DES",
                        "com.sun.crypto.provider.DESKeyGenerator");

                    put("KeyGenerator.DESede",
                        "com.sun.crypto.provider.DESedeKeyGenerator");
                    put("Alg.Alias.KeyGenerator.TripleDES", "DESede");

                    put("KeyGenerator.Blowfish",
                        "com.sun.crypto.provider.BlowfishKeyGenerator");

                    put("KeyGenerator.AES",
                        "com.sun.crypto.provider.AESKeyGenerator");
                    put("Alg.Alias.KeyGenerator.Rijndael", "AES");

                    put("KeyGenerator.RC2",
                        "com.sun.crypto.provider.KeyGeneratorCore$" +
                        "RC2KeyGenerator");
                    put("KeyGenerator.ARCFOUR",
                        "com.sun.crypto.provider.KeyGeneratorCore$" +
                        "ARCFOURKeyGenerator");
                    put("Alg.Alias.KeyGenerator.RC4", "ARCFOUR");

                    put("KeyGenerator.HmacMD5",
                        "com.sun.crypto.provider.HmacMD5KeyGenerator");

                    put("KeyGenerator.HmacSHA1",
                        "com.sun.crypto.provider.HmacSHA1KeyGenerator");
                    put("Alg.Alias.KeyGenerator.OID.1.2.840.113549.2.7", "HmacSHA1");
                    put("Alg.Alias.KeyGenerator.1.2.840.113549.2.7", "HmacSHA1");

                    put("KeyGenerator.HmacSHA224",
                        "com.sun.crypto.provider.KeyGeneratorCore$HmacSHA2KG$SHA224");
                    put("Alg.Alias.KeyGenerator.OID.1.2.840.113549.2.8", "HmacSHA224");
                    put("Alg.Alias.KeyGenerator.1.2.840.113549.2.8", "HmacSHA224");

                    put("KeyGenerator.HmacSHA256",
                        "com.sun.crypto.provider.KeyGeneratorCore$HmacSHA2KG$SHA256");
                    put("Alg.Alias.KeyGenerator.OID.1.2.840.113549.2.9", "HmacSHA256");
                    put("Alg.Alias.KeyGenerator.1.2.840.113549.2.9", "HmacSHA256");

                    put("KeyGenerator.HmacSHA384",
                        "com.sun.crypto.provider.KeyGeneratorCore$HmacSHA2KG$SHA384");
                    put("Alg.Alias.KeyGenerator.OID.1.2.840.113549.2.10", "HmacSHA384");
                    put("Alg.Alias.KeyGenerator.1.2.840.113549.2.10", "HmacSHA384");

                    put("KeyGenerator.HmacSHA512",
                        "com.sun.crypto.provider.KeyGeneratorCore$HmacSHA2KG$SHA512");
                    put("Alg.Alias.KeyGenerator.OID.1.2.840.113549.2.11", "HmacSHA512");
                    put("Alg.Alias.KeyGenerator.1.2.840.113549.2.11", "HmacSHA512");

                    put("KeyPairGenerator.DiffieHellman",
                        "com.sun.crypto.provider.DHKeyPairGenerator");
                    put("Alg.Alias.KeyPairGenerator.DH", "DiffieHellman");
                    put("Alg.Alias.KeyPairGenerator.OID."+OID_PKCS3,
                        "DiffieHellman");
                    put("Alg.Alias.KeyPairGenerator."+OID_PKCS3,
                        "DiffieHellman");

                    /*
                     * Algorithm parameter generation engines
                     */
                    put("AlgorithmParameterGenerator.DiffieHellman",
                        "com.sun.crypto.provider.DHParameterGenerator");
                    put("Alg.Alias.AlgorithmParameterGenerator.DH",
                        "DiffieHellman");
                    put("Alg.Alias.AlgorithmParameterGenerator.OID."+OID_PKCS3,
                        "DiffieHellman");
                    put("Alg.Alias.AlgorithmParameterGenerator."+OID_PKCS3,
                        "DiffieHellman");

                    /*
                     * Key Agreement engines
                     */
                    put("KeyAgreement.DiffieHellman",
                        "com.sun.crypto.provider.DHKeyAgreement");
                    put("Alg.Alias.KeyAgreement.DH", "DiffieHellman");
                    put("Alg.Alias.KeyAgreement.OID."+OID_PKCS3, "DiffieHellman");
                    put("Alg.Alias.KeyAgreement."+OID_PKCS3, "DiffieHellman");

                    put("KeyAgreement.DiffieHellman SupportedKeyClasses",
                        "javax.crypto.interfaces.DHPublicKey" +
                        "|javax.crypto.interfaces.DHPrivateKey");

                    /*
                     * Algorithm Parameter engines
                     */
                    put("AlgorithmParameters.DiffieHellman",
                        "com.sun.crypto.provider.DHParameters");
                    put("Alg.Alias.AlgorithmParameters.DH", "DiffieHellman");
                    put("Alg.Alias.AlgorithmParameters.OID."+OID_PKCS3,
                        "DiffieHellman");
                    put("Alg.Alias.AlgorithmParameters."+OID_PKCS3,
                        "DiffieHellman");

                    put("AlgorithmParameters.DES",
                        "com.sun.crypto.provider.DESParameters");

                    put("AlgorithmParameters.DESede",
                        "com.sun.crypto.provider.DESedeParameters");
                    put("Alg.Alias.AlgorithmParameters.TripleDES", "DESede");

                    put("AlgorithmParameters.PBE",
                        "com.sun.crypto.provider.PBEParameters");

                    put("AlgorithmParameters.PBEWithMD5AndDES",
                        "com.sun.crypto.provider.PBEParameters");
                    put("Alg.Alias.AlgorithmParameters.OID."+OID_PKCS5_MD5_DES,
                        "PBEWithMD5AndDES");
                    put("Alg.Alias.AlgorithmParameters."+OID_PKCS5_MD5_DES,
                        "PBEWithMD5AndDES");

                    put("AlgorithmParameters.PBEWithMD5AndTripleDES",
                        "com.sun.crypto.provider.PBEParameters");

                    put("AlgorithmParameters.PBEWithSHA1AndDESede",
                        "com.sun.crypto.provider.PBEParameters");
                    put("Alg.Alias.AlgorithmParameters.OID."+OID_PKCS12_DESede,
                        "PBEWithSHA1AndDESede");
                    put("Alg.Alias.AlgorithmParameters."+OID_PKCS12_DESede,
                        "PBEWithSHA1AndDESede");

                    put("AlgorithmParameters.PBEWithSHA1AndRC2_40",
                        "com.sun.crypto.provider.PBEParameters");
                    put("Alg.Alias.AlgorithmParameters.OID."+OID_PKCS12_RC2_40,
                        "PBEWithSHA1AndRC2_40");
                    put("Alg.Alias.AlgorithmParameters." + OID_PKCS12_RC2_40,
                        "PBEWithSHA1AndRC2_40");

                    put("AlgorithmParameters.PBEWithSHA1AndRC2_128",
                        "com.sun.crypto.provider.PBEParameters");
                    put("Alg.Alias.AlgorithmParameters.OID."+OID_PKCS12_RC2_128,
                        "PBEWithSHA1AndRC2_128");
                    put("Alg.Alias.AlgorithmParameters." + OID_PKCS12_RC2_128,
                        "PBEWithSHA1AndRC2_128");

                    put("AlgorithmParameters.PBEWithSHA1AndRC4_40",
                        "com.sun.crypto.provider.PBEParameters");
                    put("Alg.Alias.AlgorithmParameters.OID."+OID_PKCS12_RC4_40,
                        "PBEWithSHA1AndRC4_40");
                    put("Alg.Alias.AlgorithmParameters." + OID_PKCS12_RC4_40,
                        "PBEWithSHA1AndRC4_40");

                    put("AlgorithmParameters.PBEWithSHA1AndRC4_128",
                        "com.sun.crypto.provider.PBEParameters");
                    put("Alg.Alias.AlgorithmParameters.OID."+OID_PKCS12_RC4_128,
                        "PBEWithSHA1AndRC4_128");
                    put("Alg.Alias.AlgorithmParameters." + OID_PKCS12_RC4_128,
                        "PBEWithSHA1AndRC4_128");

                    put("AlgorithmParameters.PBES2",
                        "com.sun.crypto.provider.PBES2Parameters$General");
                    put("Alg.Alias.AlgorithmParameters.OID."+OID_PKCS5_PBES2,
                        "PBES2");
                    put("Alg.Alias.AlgorithmParameters." + OID_PKCS5_PBES2,
                        "PBES2");

                    put("AlgorithmParameters.PBEWithHmacSHA1AndAES_128",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA1AndAES_128");

                    put("AlgorithmParameters.PBEWithHmacSHA224AndAES_128",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA224AndAES_128");

                    put("AlgorithmParameters.PBEWithHmacSHA256AndAES_128",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA256AndAES_128");

                    put("AlgorithmParameters.PBEWithHmacSHA384AndAES_128",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA384AndAES_128");

                    put("AlgorithmParameters.PBEWithHmacSHA512AndAES_128",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA512AndAES_128");

                    put("AlgorithmParameters.PBEWithHmacSHA1AndAES_256",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA1AndAES_256");

                    put("AlgorithmParameters.PBEWithHmacSHA224AndAES_256",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA224AndAES_256");

                    put("AlgorithmParameters.PBEWithHmacSHA256AndAES_256",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA256AndAES_256");

                    put("AlgorithmParameters.PBEWithHmacSHA384AndAES_256",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA384AndAES_256");

                    put("AlgorithmParameters.PBEWithHmacSHA512AndAES_256",
                        "com.sun.crypto.provider.PBES2Parameters$HmacSHA512AndAES_256");

                    put("AlgorithmParameters.Blowfish",
                        "com.sun.crypto.provider.BlowfishParameters");

                    put("AlgorithmParameters.AES",
                        "com.sun.crypto.provider.AESParameters");
                    put("Alg.Alias.AlgorithmParameters.Rijndael", "AES");
                    put("AlgorithmParameters.GCM",
                        "com.sun.crypto.provider.GCMParameters");


                    put("AlgorithmParameters.RC2",
                        "com.sun.crypto.provider.RC2Parameters");

                    put("AlgorithmParameters.OAEP",
                        "com.sun.crypto.provider.OAEPParameters");

                    /*
                     * Key factories
                     */
                    put("KeyFactory.DiffieHellman",
                        "com.sun.crypto.provider.DHKeyFactory");
                    put("Alg.Alias.KeyFactory.DH", "DiffieHellman");
                    put("Alg.Alias.KeyFactory.OID."+OID_PKCS3,
                        "DiffieHellman");
                    put("Alg.Alias.KeyFactory."+OID_PKCS3, "DiffieHellman");

                    /*
                     * Secret-key factories
                     */
                    put("SecretKeyFactory.DES",
                        "com.sun.crypto.provider.DESKeyFactory");

                    put("SecretKeyFactory.DESede",
                        "com.sun.crypto.provider.DESedeKeyFactory");
                    put("Alg.Alias.SecretKeyFactory.TripleDES", "DESede");

                    put("SecretKeyFactory.PBEWithMD5AndDES",
                        "com.sun.crypto.provider.PBEKeyFactory$PBEWithMD5AndDES"
                        );
                    put("Alg.Alias.SecretKeyFactory.OID."+OID_PKCS5_MD5_DES,
                        "PBEWithMD5AndDES");
                    put("Alg.Alias.SecretKeyFactory."+OID_PKCS5_MD5_DES,
                        "PBEWithMD5AndDES");

                    put("Alg.Alias.SecretKeyFactory.PBE",
                        "PBEWithMD5AndDES");

                    /*
                     * Internal in-house crypto algorithm used for
                     * the JCEKS keystore type.  Since this was developed
                     * internally, there isn't an OID corresponding to this
                     * algorithm.
                     */
                    put("SecretKeyFactory.PBEWithMD5AndTripleDES",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithMD5AndTripleDES"
                        );

                    put("SecretKeyFactory.PBEWithSHA1AndDESede",
                        "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndDESede"
                        );
                    put("Alg.Alias.SecretKeyFactory.OID."+OID_PKCS12_DESede,
                        "PBEWithSHA1AndDESede");
                    put("Alg.Alias.SecretKeyFactory." + OID_PKCS12_DESede,
                        "PBEWithSHA1AndDESede");

                    put("SecretKeyFactory.PBEWithSHA1AndRC2_40",
                        "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndRC2_40"
                        );
                    put("Alg.Alias.SecretKeyFactory.OID." + OID_PKCS12_RC2_40,
                        "PBEWithSHA1AndRC2_40");
                    put("Alg.Alias.SecretKeyFactory." + OID_PKCS12_RC2_40,
                        "PBEWithSHA1AndRC2_40");

                    put("SecretKeyFactory.PBEWithSHA1AndRC2_128",
                        "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndRC2_128"
                        );
                    put("Alg.Alias.SecretKeyFactory.OID." + OID_PKCS12_RC2_128,
                        "PBEWithSHA1AndRC2_128");
                    put("Alg.Alias.SecretKeyFactory." + OID_PKCS12_RC2_128,
                        "PBEWithSHA1AndRC2_128");

                    put("SecretKeyFactory.PBEWithSHA1AndRC4_40",
                        "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndRC4_40"
                        );

                    put("Alg.Alias.SecretKeyFactory.OID." + OID_PKCS12_RC4_40,
                        "PBEWithSHA1AndRC4_40");
                    put("Alg.Alias.SecretKeyFactory." + OID_PKCS12_RC4_40,
                        "PBEWithSHA1AndRC4_40");

                    put("SecretKeyFactory.PBEWithSHA1AndRC4_128",
                        "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndRC4_128"
                        );

                    put("Alg.Alias.SecretKeyFactory.OID." + OID_PKCS12_RC4_128,
                        "PBEWithSHA1AndRC4_128");
                    put("Alg.Alias.SecretKeyFactory." + OID_PKCS12_RC4_128,
                        "PBEWithSHA1AndRC4_128");

                    put("SecretKeyFactory.PBEWithHmacSHA1AndAES_128",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA1AndAES_128");

                    put("SecretKeyFactory.PBEWithHmacSHA224AndAES_128",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA224AndAES_128");

                    put("SecretKeyFactory.PBEWithHmacSHA256AndAES_128",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA256AndAES_128");

                    put("SecretKeyFactory.PBEWithHmacSHA384AndAES_128",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA384AndAES_128");

                    put("SecretKeyFactory.PBEWithHmacSHA512AndAES_128",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA512AndAES_128");

                    put("SecretKeyFactory.PBEWithHmacSHA1AndAES_256",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA1AndAES_256");

                    put("SecretKeyFactory.PBEWithHmacSHA224AndAES_256",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA224AndAES_256");

                    put("SecretKeyFactory.PBEWithHmacSHA256AndAES_256",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA256AndAES_256");

                    put("SecretKeyFactory.PBEWithHmacSHA384AndAES_256",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA384AndAES_256");

                    put("SecretKeyFactory.PBEWithHmacSHA512AndAES_256",
                        "com.sun.crypto.provider.PBEKeyFactory$" +
                        "PBEWithHmacSHA512AndAES_256");

                    // PBKDF2

                    put("SecretKeyFactory.PBKDF2WithHmacSHA1",
                        "com.sun.crypto.provider.PBKDF2Core$HmacSHA1");
                    put("Alg.Alias.SecretKeyFactory.OID." + OID_PKCS5_PBKDF2,
                        "PBKDF2WithHmacSHA1");
                    put("Alg.Alias.SecretKeyFactory." + OID_PKCS5_PBKDF2,
                        "PBKDF2WithHmacSHA1");

                    put("SecretKeyFactory.PBKDF2WithHmacSHA224",
                        "com.sun.crypto.provider.PBKDF2Core$HmacSHA224");
                    put("SecretKeyFactory.PBKDF2WithHmacSHA256",
                        "com.sun.crypto.provider.PBKDF2Core$HmacSHA256");
                    put("SecretKeyFactory.PBKDF2WithHmacSHA384",
                        "com.sun.crypto.provider.PBKDF2Core$HmacSHA384");
                    put("SecretKeyFactory.PBKDF2WithHmacSHA512",
                        "com.sun.crypto.provider.PBKDF2Core$HmacSHA512");

                    /*
                     * MAC
                     */
                    put("Mac.HmacMD5", "com.sun.crypto.provider.HmacMD5");
                    put("Mac.HmacSHA1", "com.sun.crypto.provider.HmacSHA1");
                    put("Alg.Alias.Mac.OID.1.2.840.113549.2.7", "HmacSHA1");
                    put("Alg.Alias.Mac.1.2.840.113549.2.7", "HmacSHA1");
                    put("Mac.HmacSHA224",
                        "com.sun.crypto.provider.HmacCore$HmacSHA224");
                    put("Alg.Alias.Mac.OID.1.2.840.113549.2.8", "HmacSHA224");
                    put("Alg.Alias.Mac.1.2.840.113549.2.8", "HmacSHA224");
                    put("Mac.HmacSHA256",
                        "com.sun.crypto.provider.HmacCore$HmacSHA256");
                    put("Alg.Alias.Mac.OID.1.2.840.113549.2.9", "HmacSHA256");
                    put("Alg.Alias.Mac.1.2.840.113549.2.9", "HmacSHA256");
                    put("Mac.HmacSHA384",
                        "com.sun.crypto.provider.HmacCore$HmacSHA384");
                    put("Alg.Alias.Mac.OID.1.2.840.113549.2.10", "HmacSHA384");
                    put("Alg.Alias.Mac.1.2.840.113549.2.10", "HmacSHA384");
                    put("Mac.HmacSHA512",
                        "com.sun.crypto.provider.HmacCore$HmacSHA512");
                    put("Alg.Alias.Mac.OID.1.2.840.113549.2.11", "HmacSHA512");
                    put("Alg.Alias.Mac.1.2.840.113549.2.11", "HmacSHA512");

                    // TODO: aliases with OIDs
                    put("Mac.HmacSHA512/224",
                            "com.sun.crypto.provider.HmacCore$HmacSHA512_224");
                    put("Mac.HmacSHA512/256",
                            "com.sun.crypto.provider.HmacCore$HmacSHA512_256");

                    put("Mac.HmacPBESHA1",
                        "com.sun.crypto.provider.HmacPKCS12PBESHA1");

                    // PBMAC1

                    put("Mac.PBEWithHmacSHA1",
                        "com.sun.crypto.provider.PBMAC1Core$HmacSHA1");
                    put("Mac.PBEWithHmacSHA224",
                        "com.sun.crypto.provider.PBMAC1Core$HmacSHA224");
                    put("Mac.PBEWithHmacSHA256",
                        "com.sun.crypto.provider.PBMAC1Core$HmacSHA256");
                    put("Mac.PBEWithHmacSHA384",
                        "com.sun.crypto.provider.PBMAC1Core$HmacSHA384");
                    put("Mac.PBEWithHmacSHA512",
                        "com.sun.crypto.provider.PBMAC1Core$HmacSHA512");

                    put("Mac.SslMacMD5",
                        "com.sun.crypto.provider.SslMacCore$SslMacMD5");
                    put("Mac.SslMacSHA1",
                        "com.sun.crypto.provider.SslMacCore$SslMacSHA1");

                    put("Mac.HmacMD5 SupportedKeyFormats", "RAW");
                    put("Mac.HmacSHA1 SupportedKeyFormats", "RAW");
                    put("Mac.HmacSHA224 SupportedKeyFormats", "RAW");
                    put("Mac.HmacSHA256 SupportedKeyFormats", "RAW");
                    put("Mac.HmacSHA384 SupportedKeyFormats", "RAW");
                    put("Mac.HmacSHA512 SupportedKeyFormats", "RAW");
                    put("Mac.HmacPBESHA1 SupportedKeyFormats", "RAW");
                    put("Mac.PBEWithHmacSHA1 SupportedKeyFormatS", "RAW");
                    put("Mac.PBEWithHmacSHA224 SupportedKeyFormats", "RAW");
                    put("Mac.PBEWithHmacSHA256 SupportedKeyFormats", "RAW");
                    put("Mac.PBEWithHmacSHA384 SupportedKeyFormats", "RAW");
                    put("Mac.PBEWithHmacSHA512 SupportedKeyFormats", "RAW");
                    put("Mac.SslMacMD5 SupportedKeyFormats", "RAW");
                    put("Mac.SslMacSHA1 SupportedKeyFormats", "RAW");

                    /*
                     * KeyStore
                     */
                    put("KeyStore.JCEKS", "com.sun.crypto.provider.JceKeyStore");

                    /*
                     * SSL/TLS mechanisms
                     *
                     * These are strictly internal implementations and may
                     * be changed at any time.  These names were chosen
                     * because PKCS11/SunPKCS11 does not yet have TLS1.2
                     * mechanisms, and it will cause calls to come here.
                     */
                    put("KeyGenerator.SunTlsPrf",
                            "com.sun.crypto.provider.TlsPrfGenerator$V10");
                    put("KeyGenerator.SunTls12Prf",
                            "com.sun.crypto.provider.TlsPrfGenerator$V12");

                    put("KeyGenerator.SunTlsMasterSecret",
                        "com.sun.crypto.provider.TlsMasterSecretGenerator");
                    put("Alg.Alias.KeyGenerator.SunTls12MasterSecret",
                        "SunTlsMasterSecret");
                    put("Alg.Alias.KeyGenerator.SunTlsExtendedMasterSecret",
                        "SunTlsMasterSecret");

                    put("KeyGenerator.SunTlsKeyMaterial",
                        "com.sun.crypto.provider.TlsKeyMaterialGenerator");
                    put("Alg.Alias.KeyGenerator.SunTls12KeyMaterial",
                        "SunTlsKeyMaterial");

                    put("KeyGenerator.SunTlsRsaPremasterSecret",
                        "com.sun.crypto.provider.TlsRsaPremasterSecretGenerator");
                    put("Alg.Alias.KeyGenerator.SunTls12RsaPremasterSecret",
                        "SunTlsRsaPremasterSecret");

                    return null;
                }
            });

        if (instance == null) {
            instance = this;
        }
    }

    // Return the instance of this class or create one if needed.
    static SunJCE getInstance() {
        if (instance == null) {
            return new SunJCE();
        }
        return instance;
    }
}
