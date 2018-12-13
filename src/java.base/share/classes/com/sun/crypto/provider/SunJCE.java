/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.List;
import static sun.security.util.SecurityConstants.PROVIDER_VER;
import static sun.security.provider.SunEntries.createAliases;
import static sun.security.provider.SunEntries.createAliasesWithOid;

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
 * - ChaCha20 (Stream cipher only and in AEAD mode with Poly1305)
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
    + "Diffie-Hellman, HMAC, ChaCha20)";

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

    private void ps(String type, String algo, String cn,
            List<String> aliases, HashMap<String, String> attrs) {
        putService(new Provider.Service(this, type, algo, cn, aliases, attrs));
    }

    public SunJCE() {
        /* We are the "SunJCE" provider */
        super("SunJCE", PROVIDER_VER, info);

        // if there is no security manager installed, put directly into
        // the provider
        if (System.getSecurityManager() == null) {
            putEntries();
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    putEntries();
                    return null;
                }
            });
        }
        if (instance == null) {
            instance = this;
        }
    }

    void putEntries() {
        // common aliases and oids
        List<String> aesAliases = createAliases("Rijndael");
        List<String> desEdeAliases = createAliases("TripleDES");
        List<String> arcFourAliases = createAliases("RC4");
        List<String> sunTlsMSAliases = createAliases(
            "SunTls12MasterSecret", "SunTlsExtendedMasterSecret"
        );
        List<String> sunTlsKMAliases = createAliases("SunTls12KeyMaterial");
        List<String> sunTlsRsaPMSAliases = createAliases("SunTls12RsaPremasterSecret");

        String aes128Oid = "2.16.840.1.101.3.4.1.";
        String aes192Oid = "2.16.840.1.101.3.4.1.2";
        String aes256Oid = "2.16.840.1.101.3.4.1.4";

        List<String> pkcs12RC4_128Aliases =
            createAliasesWithOid("1.2.840.113549.1.12.1.1");

        List<String> pkcs12RC4_40Aliases =
            createAliasesWithOid("1.2.840.113549.1.12.1.2");

        List<String> pkcs12DESedeAliases =
            createAliasesWithOid("1.2.840.113549.1.12.1.3");

        List<String> pkcs12RC2_128Aliases =
            createAliasesWithOid("1.2.840.113549.1.12.1.5");

        List<String> pkcs12RC2_40Aliases =
            createAliasesWithOid("1.2.840.113549.1.12.1.6");

        List<String> pkcs5MD5_DESAliases =
            createAliasesWithOid("1.2.840.113549.1.5.3", "PBE");

        List<String> pkcs5PBKDF2Aliases =
            createAliasesWithOid("1.2.840.113549.1.5.12");

        List<String> pkcs5PBES2Aliases =
            createAliasesWithOid("1.2.840.113549.1.5.13");

        List<String> diffieHellmanAliases =
            createAliasesWithOid("1.2.840.113549.1.3.1", "DH");

        List<String> chachaPolyAliases =
            createAliasesWithOid("1.2.840.113549.1.9.16.3.18");

        String macOidBase = "1.2.840.113549.2.";
        List<String> macSHA1Aliases = createAliasesWithOid(macOidBase + "7");
        List<String> macSHA224Aliases = createAliasesWithOid(macOidBase + "8");
        List<String> macSHA256Aliases = createAliasesWithOid(macOidBase + "9");
        List<String> macSHA384Aliases = createAliasesWithOid(macOidBase + "10");
        List<String> macSHA512Aliases = createAliasesWithOid(macOidBase + "11");

        // reuse attribute map and reset before each reuse
        HashMap<String, String> attrs = new HashMap<>(3);
        attrs.put("SupportedModes", "ECB");
        attrs.put("SupportedPaddings", "NOPADDING|PKCS1PADDING|OAEPPADDING"
                + "|OAEPWITHMD5ANDMGF1PADDING"
                + "|OAEPWITHSHA1ANDMGF1PADDING"
                + "|OAEPWITHSHA-1ANDMGF1PADDING"
                + "|OAEPWITHSHA-224ANDMGF1PADDING"
                + "|OAEPWITHSHA-256ANDMGF1PADDING"
                + "|OAEPWITHSHA-384ANDMGF1PADDING"
                + "|OAEPWITHSHA-512ANDMGF1PADDING"
                + "|OAEPWITHSHA-512/224ANDMGF1PADDING"
                + "|OAEPWITHSHA-512/256ANDMGF1PADDING");
        attrs.put("SupportedKeyClasses",
                "java.security.interfaces.RSAPublicKey" +
                "|java.security.interfaces.RSAPrivateKey");
        ps("Cipher", "RSA",
                "com.sun.crypto.provider.RSACipher", null, attrs);

        // common block cipher modes, pads
        final String BLOCK_MODES = "ECB|CBC|PCBC|CTR|CTS|CFB|OFB" +
            "|CFB8|CFB16|CFB24|CFB32|CFB40|CFB48|CFB56|CFB64" +
            "|OFB8|OFB16|OFB24|OFB32|OFB40|OFB48|OFB56|OFB64";
        final String BLOCK_MODES128 = BLOCK_MODES +
            "|GCM|CFB72|CFB80|CFB88|CFB96|CFB104|CFB112|CFB120|CFB128" +
            "|OFB72|OFB80|OFB88|OFB96|OFB104|OFB112|OFB120|OFB128";
        final String BLOCK_PADS = "NOPADDING|PKCS5PADDING|ISO10126PADDING";

        attrs.clear();
        attrs.put("SupportedModes", BLOCK_MODES);
        attrs.put("SupportedPaddings", BLOCK_PADS);
        attrs.put("SupportedKeyFormats", "RAW");
        ps("Cipher", "DES",
                "com.sun.crypto.provider.DESCipher", null, attrs);
        ps("Cipher", "DESede", "com.sun.crypto.provider.DESedeCipher",
                desEdeAliases, attrs);
        ps("Cipher", "Blowfish",
                "com.sun.crypto.provider.BlowfishCipher", null, attrs);

        ps("Cipher", "RC2",
                "com.sun.crypto.provider.RC2Cipher", null, attrs);

        attrs.clear();
        attrs.put("SupportedModes", BLOCK_MODES128);
        attrs.put("SupportedPaddings", BLOCK_PADS);
        attrs.put("SupportedKeyFormats", "RAW");
        ps("Cipher", "AES", "com.sun.crypto.provider.AESCipher$General",
                aesAliases, attrs);

        attrs.clear();
        attrs.put("SupportedKeyFormats", "RAW");
        ps("Cipher", "AES_128/ECB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES128_ECB_NoPadding",
                createAliasesWithOid(aes128Oid+"1"), attrs);
        ps("Cipher", "AES_128/CBC/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES128_CBC_NoPadding",
                createAliasesWithOid(aes128Oid+"2"), attrs);
        ps("Cipher", "AES_128/OFB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES128_OFB_NoPadding",
                createAliasesWithOid(aes128Oid+"3"), attrs);
        ps("Cipher", "AES_128/CFB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES128_CFB_NoPadding",
                createAliasesWithOid(aes128Oid+"4"), attrs);
        ps("Cipher", "AES_128/GCM/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES128_GCM_NoPadding",
                createAliasesWithOid(aes128Oid+"6"), attrs);

        ps("Cipher", "AES_192/ECB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES192_ECB_NoPadding",
                createAliasesWithOid(aes192Oid+"1"), attrs);
        ps("Cipher", "AES_192/CBC/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES192_CBC_NoPadding",
                createAliasesWithOid(aes192Oid+"2"), attrs);
        ps("Cipher", "AES_192/OFB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES192_OFB_NoPadding",
                createAliasesWithOid(aes192Oid+"3"), attrs);
        ps("Cipher", "AES_192/CFB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES192_CFB_NoPadding",
                createAliasesWithOid(aes192Oid+"4"), attrs);
        ps("Cipher", "AES_192/GCM/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES192_GCM_NoPadding",
                createAliasesWithOid(aes192Oid+"6"), attrs);

        ps("Cipher", "AES_256/ECB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES256_ECB_NoPadding",
                createAliasesWithOid(aes256Oid+"1"), attrs);
        ps("Cipher", "AES_256/CBC/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES256_CBC_NoPadding",
                createAliasesWithOid(aes256Oid+"2"), attrs);
        ps("Cipher", "AES_256/OFB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES256_OFB_NoPadding",
                createAliasesWithOid(aes256Oid+"3"), attrs);
        ps("Cipher", "AES_256/CFB/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES256_CFB_NoPadding",
                createAliasesWithOid(aes256Oid+"4"), attrs);
        ps("Cipher", "AES_256/GCM/NoPadding",
                "com.sun.crypto.provider.AESCipher$AES256_GCM_NoPadding",
                createAliasesWithOid(aes256Oid+"6"), attrs);

        attrs.clear();
        attrs.put("SupportedModes", "CBC");
        attrs.put("SupportedPaddings", "NOPADDING");
        attrs.put("SupportedKeyFormats", "RAW");
        ps("Cipher", "DESedeWrap",
                "com.sun.crypto.provider.DESedeWrapCipher", null, attrs);

        attrs.clear();
        attrs.put("SupportedModes", "ECB");
        attrs.put("SupportedPaddings", "NOPADDING");
        attrs.put("SupportedKeyFormats", "RAW");
        ps("Cipher", "ARCFOUR", "com.sun.crypto.provider.ARCFOURCipher",
                arcFourAliases, attrs);
        ps("Cipher", "AESWrap", "com.sun.crypto.provider.AESWrapCipher$General",
                null, attrs);
        ps("Cipher", "AESWrap_128",
                "com.sun.crypto.provider.AESWrapCipher$AES128",
                createAliasesWithOid(aes128Oid+"5"), attrs);
        ps("Cipher", "AESWrap_192",
                "com.sun.crypto.provider.AESWrapCipher$AES192",
                createAliasesWithOid(aes192Oid+"5"), attrs);
        ps("Cipher", "AESWrap_256",
                "com.sun.crypto.provider.AESWrapCipher$AES256",
                createAliasesWithOid(aes256Oid+"5"), attrs);

        attrs.clear();
        attrs.put("SupportedKeyFormats", "RAW");
        ps("Cipher",  "ChaCha20",
                "com.sun.crypto.provider.ChaCha20Cipher$ChaCha20Only",
                null, attrs);
        ps("Cipher",  "ChaCha20-Poly1305",
                "com.sun.crypto.provider.ChaCha20Cipher$ChaCha20Poly1305",
                chachaPolyAliases, attrs);

        // PBES1
        ps("Cipher", "PBEWithMD5AndDES",
                "com.sun.crypto.provider.PBEWithMD5AndDESCipher",
                pkcs5MD5_DESAliases, null);
        ps("Cipher", "PBEWithMD5AndTripleDES",
                "com.sun.crypto.provider.PBEWithMD5AndTripleDESCipher",
                null, null);
        ps("Cipher", "PBEWithSHA1AndDESede",
                "com.sun.crypto.provider.PKCS12PBECipherCore$PBEWithSHA1AndDESede",
                pkcs12DESedeAliases, null);
        ps("Cipher", "PBEWithSHA1AndRC2_40",
                "com.sun.crypto.provider.PKCS12PBECipherCore$PBEWithSHA1AndRC2_40",
                pkcs12RC2_40Aliases, null);
        ps("Cipher", "PBEWithSHA1AndRC2_128",
                "com.sun.crypto.provider.PKCS12PBECipherCore$PBEWithSHA1AndRC2_128",
                pkcs12RC2_128Aliases, null);
        ps("Cipher", "PBEWithSHA1AndRC4_40",
                "com.sun.crypto.provider.PKCS12PBECipherCore$PBEWithSHA1AndRC4_40",
                pkcs12RC4_40Aliases, null);

        ps("Cipher", "PBEWithSHA1AndRC4_128",
                "com.sun.crypto.provider.PKCS12PBECipherCore$PBEWithSHA1AndRC4_128",
                pkcs12RC4_128Aliases, null);

        // PBES2
        ps("Cipher", "PBEWithHmacSHA1AndAES_128",
                "com.sun.crypto.provider.PBES2Core$HmacSHA1AndAES_128",
                null, null);

        ps("Cipher", "PBEWithHmacSHA224AndAES_128",
                "com.sun.crypto.provider.PBES2Core$HmacSHA224AndAES_128",
                null, null);

        ps("Cipher", "PBEWithHmacSHA256AndAES_128",
                "com.sun.crypto.provider.PBES2Core$HmacSHA256AndAES_128",
                null, null);

        ps("Cipher", "PBEWithHmacSHA384AndAES_128",
                "com.sun.crypto.provider.PBES2Core$HmacSHA384AndAES_128",
                null, null);

        ps("Cipher", "PBEWithHmacSHA512AndAES_128",
                "com.sun.crypto.provider.PBES2Core$HmacSHA512AndAES_128",
                null, null);

        ps("Cipher", "PBEWithHmacSHA1AndAES_256",
                "com.sun.crypto.provider.PBES2Core$HmacSHA1AndAES_256",
                null, null);

        ps("Cipher", "PBEWithHmacSHA224AndAES_256",
                "com.sun.crypto.provider.PBES2Core$HmacSHA224AndAES_256",
                null, null);

        ps("Cipher", "PBEWithHmacSHA256AndAES_256",
                "com.sun.crypto.provider.PBES2Core$HmacSHA256AndAES_256",
                null, null);

        ps("Cipher", "PBEWithHmacSHA384AndAES_256",
                "com.sun.crypto.provider.PBES2Core$HmacSHA384AndAES_256",
                null, null);

        ps("Cipher", "PBEWithHmacSHA512AndAES_256",
                "com.sun.crypto.provider.PBES2Core$HmacSHA512AndAES_256",
                null, null);

        /*
         * Key(pair) Generator engines
         */
        ps("KeyGenerator", "DES",
                "com.sun.crypto.provider.DESKeyGenerator",
                null, null);
        ps("KeyGenerator", "DESede",
                "com.sun.crypto.provider.DESedeKeyGenerator",
                desEdeAliases, null);
        ps("KeyGenerator", "Blowfish",
                "com.sun.crypto.provider.BlowfishKeyGenerator",
                null, null);
        ps("KeyGenerator", "AES",
                "com.sun.crypto.provider.AESKeyGenerator",
                aesAliases, null);
        ps("KeyGenerator", "RC2",
                "com.sun.crypto.provider.KeyGeneratorCore$RC2KeyGenerator",
                null, null);
        ps("KeyGenerator", "ARCFOUR",
                "com.sun.crypto.provider.KeyGeneratorCore$ARCFOURKeyGenerator",
                arcFourAliases, null);
        ps("KeyGenerator", "ChaCha20",
                "com.sun.crypto.provider.KeyGeneratorCore$ChaCha20KeyGenerator",
                null, null);
        ps("KeyGenerator", "HmacMD5",
                "com.sun.crypto.provider.HmacMD5KeyGenerator",
                null, null);

        ps("KeyGenerator", "HmacSHA1",
                "com.sun.crypto.provider.HmacSHA1KeyGenerator",
                macSHA1Aliases, null);
        ps("KeyGenerator", "HmacSHA224",
                "com.sun.crypto.provider.KeyGeneratorCore$HmacSHA2KG$SHA224",
                macSHA224Aliases, null);
        ps("KeyGenerator", "HmacSHA256",
                "com.sun.crypto.provider.KeyGeneratorCore$HmacSHA2KG$SHA256",
                macSHA256Aliases, null);
        ps("KeyGenerator", "HmacSHA384",
                "com.sun.crypto.provider.KeyGeneratorCore$HmacSHA2KG$SHA384",
                macSHA384Aliases, null);
        ps("KeyGenerator", "HmacSHA512",
                "com.sun.crypto.provider.KeyGeneratorCore$HmacSHA2KG$SHA512",
                macSHA512Aliases, null);

        ps("KeyPairGenerator", "DiffieHellman",
                "com.sun.crypto.provider.DHKeyPairGenerator",
                diffieHellmanAliases, null);

        /*
         * Algorithm parameter generation engines
         */
        ps("AlgorithmParameterGenerator",
                "DiffieHellman", "com.sun.crypto.provider.DHParameterGenerator",
                diffieHellmanAliases, null);

        /*
         * Key Agreement engines
         */
        attrs.clear();
        attrs.put("SupportedKeyClasses", "javax.crypto.interfaces.DHPublicKey" +
                        "|javax.crypto.interfaces.DHPrivateKey");
        ps("KeyAgreement", "DiffieHellman",
                "com.sun.crypto.provider.DHKeyAgreement",
                diffieHellmanAliases, attrs);

        /*
         * Algorithm Parameter engines
         */
        ps("AlgorithmParameters", "DiffieHellman",
                "com.sun.crypto.provider.DHParameters",
                diffieHellmanAliases, null);

        ps("AlgorithmParameters", "DES",
                "com.sun.crypto.provider.DESParameters",
                null, null);

        ps("AlgorithmParameters", "DESede",
                "com.sun.crypto.provider.DESedeParameters",
                desEdeAliases, null);

        ps("AlgorithmParameters", "PBEWithMD5AndDES",
                "com.sun.crypto.provider.PBEParameters",
                pkcs5MD5_DESAliases, null);

        ps("AlgorithmParameters", "PBEWithMD5AndTripleDES",
                "com.sun.crypto.provider.PBEParameters",
                null, null);

        ps("AlgorithmParameters", "PBEWithSHA1AndDESede",
                "com.sun.crypto.provider.PBEParameters",
                pkcs12DESedeAliases, null);

        ps("AlgorithmParameters", "PBEWithSHA1AndRC2_40",
                "com.sun.crypto.provider.PBEParameters",
                pkcs12RC2_40Aliases, null);

        ps("AlgorithmParameters", "PBEWithSHA1AndRC2_128",
                "com.sun.crypto.provider.PBEParameters",
                pkcs12RC2_128Aliases, null);

        ps("AlgorithmParameters", "PBEWithSHA1AndRC4_40",
                "com.sun.crypto.provider.PBEParameters",
                pkcs12RC4_40Aliases, null);

        ps("AlgorithmParameters", "PBEWithSHA1AndRC4_128",
                "com.sun.crypto.provider.PBEParameters",
                pkcs12RC4_128Aliases, null);

        ps("AlgorithmParameters", "PBES2",
                "com.sun.crypto.provider.PBES2Parameters$General",
                pkcs5PBES2Aliases, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA1AndAES_128",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA1AndAES_128",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA224AndAES_128",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA224AndAES_128",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA256AndAES_128",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA256AndAES_128",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA384AndAES_128",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA384AndAES_128",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA512AndAES_128",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA512AndAES_128",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA1AndAES_256",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA1AndAES_256",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA224AndAES_256",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA224AndAES_256",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA256AndAES_256",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA256AndAES_256",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA384AndAES_256",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA384AndAES_256",
                null, null);

        ps("AlgorithmParameters", "PBEWithHmacSHA512AndAES_256",
                "com.sun.crypto.provider.PBES2Parameters$HmacSHA512AndAES_256",
                null, null);

        ps("AlgorithmParameters", "Blowfish",
                "com.sun.crypto.provider.BlowfishParameters",
                null, null);

        ps("AlgorithmParameters", "AES",
                "com.sun.crypto.provider.AESParameters",
                aesAliases, null);

        ps("AlgorithmParameters", "GCM",
                "com.sun.crypto.provider.GCMParameters",
                null, null);

        ps("AlgorithmParameters", "RC2",
                "com.sun.crypto.provider.RC2Parameters",
                null, null);

        ps("AlgorithmParameters", "OAEP",
                "com.sun.crypto.provider.OAEPParameters",
                null, null);

        ps("AlgorithmParameters", "ChaCha20-Poly1305",
                "com.sun.crypto.provider.ChaCha20Poly1305Parameters",
                chachaPolyAliases, null);

        /*
         * Key factories
         */
        ps("KeyFactory", "DiffieHellman",
                "com.sun.crypto.provider.DHKeyFactory",
                diffieHellmanAliases, null);

        /*
         * Secret-key factories
         */
        ps("SecretKeyFactory", "DES",
                "com.sun.crypto.provider.DESKeyFactory",
                null, null);

        ps("SecretKeyFactory", "DESede",
                "com.sun.crypto.provider.DESedeKeyFactory",
                desEdeAliases, null);

        ps("SecretKeyFactory", "PBEWithMD5AndDES",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithMD5AndDES",
                pkcs5MD5_DESAliases, null);

        /*
         * Internal in-house crypto algorithm used for
         * the JCEKS keystore type.  Since this was developed
         * internally, there isn't an OID corresponding to this
         * algorithm.
         */
        ps("SecretKeyFactory", "PBEWithMD5AndTripleDES",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithMD5AndTripleDES",
                null, null);

        ps("SecretKeyFactory", "PBEWithSHA1AndDESede",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndDESede",
                pkcs12DESedeAliases, null);

        ps("SecretKeyFactory", "PBEWithSHA1AndRC2_40",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndRC2_40",
                pkcs12RC2_40Aliases, null);

        ps("SecretKeyFactory", "PBEWithSHA1AndRC2_128",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndRC2_128",
                pkcs12RC2_128Aliases, null);

        ps("SecretKeyFactory", "PBEWithSHA1AndRC4_40",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndRC4_40",
                pkcs12RC4_40Aliases,null);

        ps("SecretKeyFactory", "PBEWithSHA1AndRC4_128",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithSHA1AndRC4_128",
                pkcs12RC4_128Aliases, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA1AndAES_128",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA1AndAES_128",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA224AndAES_128",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA224AndAES_128",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA256AndAES_128",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA256AndAES_128",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA384AndAES_128",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA384AndAES_128",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA512AndAES_128",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA512AndAES_128",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA1AndAES_256",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA1AndAES_256",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA224AndAES_256",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA224AndAES_256",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA256AndAES_256",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA256AndAES_256",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA384AndAES_256",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA384AndAES_256",
                null, null);

        ps("SecretKeyFactory", "PBEWithHmacSHA512AndAES_256",
                "com.sun.crypto.provider.PBEKeyFactory$PBEWithHmacSHA512AndAES_256",
                null, null);

        // PBKDF2
        ps("SecretKeyFactory", "PBKDF2WithHmacSHA1",
                "com.sun.crypto.provider.PBKDF2Core$HmacSHA1",
                pkcs5PBKDF2Aliases, null);
        ps("SecretKeyFactory", "PBKDF2WithHmacSHA224",
                "com.sun.crypto.provider.PBKDF2Core$HmacSHA224",
                null, null);
        ps("SecretKeyFactory", "PBKDF2WithHmacSHA256",
                "com.sun.crypto.provider.PBKDF2Core$HmacSHA256",
                null, null);
        ps("SecretKeyFactory", "PBKDF2WithHmacSHA384",
                "com.sun.crypto.provider.PBKDF2Core$HmacSHA384",
                null, null);
        ps("SecretKeyFactory", "PBKDF2WithHmacSHA512",
                "com.sun.crypto.provider.PBKDF2Core$HmacSHA512",
                null, null);

        /*
         * MAC
         */
        attrs.clear();
        attrs.put("SupportedKeyFormats", "RAW");
        ps("Mac", "HmacMD5", "com.sun.crypto.provider.HmacMD5", null, attrs);
        ps("Mac", "HmacSHA1", "com.sun.crypto.provider.HmacSHA1",
                macSHA1Aliases, attrs);
        ps("Mac", "HmacSHA224", "com.sun.crypto.provider.HmacCore$HmacSHA224",
                macSHA224Aliases, attrs);
        ps("Mac", "HmacSHA256", "com.sun.crypto.provider.HmacCore$HmacSHA256",
                macSHA256Aliases, attrs);
        ps("Mac", "HmacSHA384", "com.sun.crypto.provider.HmacCore$HmacSHA384",
                macSHA384Aliases, attrs);
        ps("Mac", "HmacSHA512", "com.sun.crypto.provider.HmacCore$HmacSHA512",
                macSHA512Aliases, attrs);
        // TODO: aliases with OIDs
        ps("Mac", "HmacSHA512/224",
                "com.sun.crypto.provider.HmacCore$HmacSHA512_224",
                null, attrs);
        ps("Mac", "HmacSHA512/256",
                "com.sun.crypto.provider.HmacCore$HmacSHA512_256",
                null, attrs);
        ps("Mac", "HmacPBESHA1",
                "com.sun.crypto.provider.HmacPKCS12PBECore$HmacPKCS12PBE_SHA1",
                null, attrs);
        ps("Mac", "HmacPBESHA224",
                "com.sun.crypto.provider.HmacPKCS12PBECore$HmacPKCS12PBE_SHA224",
                null, attrs);
        ps("Mac", "HmacPBESHA256",
                "com.sun.crypto.provider.HmacPKCS12PBECore$HmacPKCS12PBE_SHA256",
                null, attrs);
        ps("Mac", "HmacPBESHA384",
                "com.sun.crypto.provider.HmacPKCS12PBECore$HmacPKCS12PBE_SHA384",
                null, attrs);
        ps("Mac", "HmacPBESHA512",
                "com.sun.crypto.provider.HmacPKCS12PBECore$HmacPKCS12PBE_SHA512",
                null, attrs);
        ps("Mac", "HmacPBESHA512/224",
                "com.sun.crypto.provider.HmacPKCS12PBECore$HmacPKCS12PBE_SHA512_224",
                null, attrs);
        ps("Mac", "HmacPBESHA512/256",
                "com.sun.crypto.provider.HmacPKCS12PBECore$HmacPKCS12PBE_SHA512_256",
                null, attrs);


        // PBMAC1
        ps("Mac", "PBEWithHmacSHA1",
                "com.sun.crypto.provider.PBMAC1Core$HmacSHA1", null, attrs);
        ps("Mac", "PBEWithHmacSHA224",
                "com.sun.crypto.provider.PBMAC1Core$HmacSHA224", null, attrs);
        ps("Mac", "PBEWithHmacSHA256",
                "com.sun.crypto.provider.PBMAC1Core$HmacSHA256", null, attrs);
        ps("Mac", "PBEWithHmacSHA384",
                "com.sun.crypto.provider.PBMAC1Core$HmacSHA384", null, attrs);
        ps("Mac", "PBEWithHmacSHA512",
                "com.sun.crypto.provider.PBMAC1Core$HmacSHA512", null, attrs);
        ps("Mac", "SslMacMD5",
                "com.sun.crypto.provider.SslMacCore$SslMacMD5", null, attrs);
        ps("Mac", "SslMacSHA1",
                "com.sun.crypto.provider.SslMacCore$SslMacSHA1", null, attrs);

        /*
         * KeyStore
         */
        ps("KeyStore", "JCEKS",
                "com.sun.crypto.provider.JceKeyStore",
                null, null);

        /*
         * SSL/TLS mechanisms
         *
         * These are strictly internal implementations and may
         * be changed at any time.  These names were chosen
         * because PKCS11/SunPKCS11 does not yet have TLS1.2
         * mechanisms, and it will cause calls to come here.
         */
        ps("KeyGenerator", "SunTlsPrf",
                "com.sun.crypto.provider.TlsPrfGenerator$V10",
                null, null);
        ps("KeyGenerator", "SunTls12Prf",
                "com.sun.crypto.provider.TlsPrfGenerator$V12",
                null, null);

        ps("KeyGenerator", "SunTlsMasterSecret",
                "com.sun.crypto.provider.TlsMasterSecretGenerator",
                createAliases("SunTls12MasterSecret",
                    "SunTlsExtendedMasterSecret"), null);

        ps("KeyGenerator", "SunTlsKeyMaterial",
                "com.sun.crypto.provider.TlsKeyMaterialGenerator",
                createAliases("SunTls12KeyMaterial"), null);

        ps("KeyGenerator", "SunTlsRsaPremasterSecret",
                "com.sun.crypto.provider.TlsRsaPremasterSecretGenerator",
                createAliases("SunTls12RsaPremasterSecret"), null);
    }

    // Return the instance of this class or create one if needed.
    static SunJCE getInstance() {
        if (instance == null) {
            return new SunJCE();
        }
        return instance;
    }
}
