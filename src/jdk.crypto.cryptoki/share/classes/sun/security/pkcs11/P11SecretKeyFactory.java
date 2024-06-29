/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.pkcs11;

import java.nio.charset.StandardCharsets;
import java.util.*;

import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.interfaces.PBEKey;
import javax.crypto.spec.*;

import static sun.security.pkcs11.TemplateManager.*;

import jdk.internal.access.SharedSecrets;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

/**
 * SecretKeyFactory implementation class. This class currently supports
 * DES, DESede, AES, ARCFOUR, and Blowfish.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11SecretKeyFactory extends SecretKeyFactorySpi {

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;

    // PBEKeyInfo if algorithm is PBE-related, otherwise null
    private final PBEKeyInfo svcPbeKi;

    P11SecretKeyFactory(Token token, String algorithm) {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.svcPbeKi = getPBEKeyInfo(algorithm);
    }

    private static final Map<String, KeyInfo> keyInfo = new HashMap<>();
    private static final KeyInfo HMAC = new KeyInfo("HMAC", PCKK_HMAC);
    private static final KeyInfo SSLMAC = new KeyInfo("SSLMAC", PCKK_SSLMAC);

    static KeyInfo getKeyInfo(String algo) {
        KeyInfo ki = keyInfo.get(algo);
        if (ki == null) {
            String algoUpper = algo.toUpperCase(Locale.ENGLISH);
            ki = keyInfo.get(algoUpper);
            if (ki == null) {
                if (algoUpper.startsWith("HMAC")) {
                    return HMAC;
                } else if (algoUpper.startsWith("SSLMAC")) {
                    return SSLMAC;
                }
            }
        }
        return ki;
    }

    static PBEKeyInfo getPBEKeyInfo(String algo) {
        if (getKeyInfo(algo) instanceof PBEKeyInfo pbeKi) {
            return pbeKi;
        }
        return null;
    }

    private static void putKeyInfo(KeyInfo ki) {
        keyInfo.put(ki.algo, ki);
        keyInfo.put(ki.algo.toUpperCase(Locale.ENGLISH), ki);
    }

    static sealed class KeyInfo permits PBEKeyInfo {
        public final String algo;
        public final long keyType;

        KeyInfo(String algo, long keyType) {
            this.algo = algo;
            this.keyType = keyType;
        }

        // The P11SecretKeyFactory::convertKey method needs to know if a service
        // type and a key are compatible. Trivial cases such as having the same
        // algorithm names are handled directly. KeyInfo::checkUse helps with
        // cases that require to retrieve the key's KeyInfo (ki), in addition to
        // the service's KeyInfo (si), to make a decision.
        static boolean checkUse(KeyInfo ki, KeyInfo si) {
            if (si instanceof PBEKeyInfo && !si.algo.equals(ki.algo)) {
                // PBE services require a PBE key of the same algorithm.
                return false;
            }
            if (ki instanceof PBKDF2KeyInfo) {
                // We cannot tell what the PBE key was derived for,
                // so any service is allowed in principle.
                return true;
            }
            // This path handles non-PBE cases where aliases are used (i.e:
            // RC4 vs ARCFOUR) and mixed PBE - non-PBE cases (i.e.: a
            // PBE-derived AES key used in an AES Cipher service).
            return ki.keyType == si.keyType;
        }
    }

    abstract static sealed class PBEKeyInfo extends KeyInfo
            permits AESPBEKeyInfo, PBKDF2KeyInfo, P12MacPBEKeyInfo {
        public static final long INVALID_PRF = -1;
        public final long kdfMech;
        public final long prfMech;
        public final int keyLen;
        public final CK_ATTRIBUTE[] extraAttrs;

        protected PBEKeyInfo(String algo, long kdfMech, long prfMech,
                long keyType, int keyLen, CK_ATTRIBUTE[] extraAttrs) {
            super(algo, keyType);
            this.kdfMech = kdfMech;
            this.prfMech = prfMech;
            this.keyLen = keyLen;
            this.extraAttrs = extraAttrs;
        }
    }

    static final class AESPBEKeyInfo extends PBEKeyInfo {
        private static final CK_ATTRIBUTE[] attr = new CK_ATTRIBUTE[] {
                CK_ATTRIBUTE.ENCRYPT_TRUE};

        AESPBEKeyInfo(String algo, long prfMech, int keyLen) {
            super(algo, CKM_PKCS5_PBKD2, prfMech, CKK_AES, keyLen, attr);
        }
    }

    static final class PBKDF2KeyInfo extends PBEKeyInfo {
        private static final CK_ATTRIBUTE[] attr = new CK_ATTRIBUTE[] {
                CK_ATTRIBUTE.ENCRYPT_TRUE, CK_ATTRIBUTE.SIGN_TRUE};

        PBKDF2KeyInfo(String algo, long prfMech) {
            super(algo, CKM_PKCS5_PBKD2, prfMech, CKK_GENERIC_SECRET, -1, attr);
        }
    }

    static final class P12MacPBEKeyInfo extends PBEKeyInfo {
        private static final CK_ATTRIBUTE[] attr = new CK_ATTRIBUTE[] {
                CK_ATTRIBUTE.SIGN_TRUE};

        P12MacPBEKeyInfo(String algo, long kdfMech, int keyLen) {
            super(algo, kdfMech, PBEKeyInfo.INVALID_PRF,
                    CKK_GENERIC_SECRET, keyLen, attr);
        }
    }

    static {
        putKeyInfo(new KeyInfo("RC4", CKK_RC4));
        putKeyInfo(new KeyInfo("ARCFOUR", CKK_RC4));
        putKeyInfo(new KeyInfo("DES", CKK_DES));
        putKeyInfo(new KeyInfo("DESede", CKK_DES3));
        putKeyInfo(new KeyInfo("AES", CKK_AES));
        putKeyInfo(new KeyInfo("Blowfish", CKK_BLOWFISH));
        putKeyInfo(new KeyInfo("ChaCha20", CKK_CHACHA20));
        putKeyInfo(new KeyInfo("ChaCha20-Poly1305", CKK_CHACHA20));

        // we don't implement RC2 or IDEA, but we want to be able to generate
        // keys for those SSL/TLS ciphersuites.
        putKeyInfo(new KeyInfo("RC2", CKK_RC2));
        putKeyInfo(new KeyInfo("IDEA", CKK_IDEA));

        putKeyInfo(new KeyInfo("TlsPremasterSecret", PCKK_TLSPREMASTER));
        putKeyInfo(new KeyInfo("TlsRsaPremasterSecret", PCKK_TLSRSAPREMASTER));
        putKeyInfo(new KeyInfo("TlsMasterSecret", PCKK_TLSMASTER));
        putKeyInfo(new KeyInfo("Generic", CKK_GENERIC_SECRET));

        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA1AndAES_128",
                CKP_PKCS5_PBKD2_HMAC_SHA1, 128));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA224AndAES_128",
                CKP_PKCS5_PBKD2_HMAC_SHA224, 128));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA256AndAES_128",
                CKP_PKCS5_PBKD2_HMAC_SHA256, 128));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA384AndAES_128",
                CKP_PKCS5_PBKD2_HMAC_SHA384, 128));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA512AndAES_128",
                CKP_PKCS5_PBKD2_HMAC_SHA512, 128));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA1AndAES_256",
                CKP_PKCS5_PBKD2_HMAC_SHA1, 256));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA224AndAES_256",
                CKP_PKCS5_PBKD2_HMAC_SHA224, 256));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA256AndAES_256",
                CKP_PKCS5_PBKD2_HMAC_SHA256, 256));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA384AndAES_256",
                CKP_PKCS5_PBKD2_HMAC_SHA384, 256));
        putKeyInfo(new AESPBEKeyInfo("PBEWithHmacSHA512AndAES_256",
                CKP_PKCS5_PBKD2_HMAC_SHA512, 256));

        putKeyInfo(new PBKDF2KeyInfo("PBKDF2WithHmacSHA1",
                CKP_PKCS5_PBKD2_HMAC_SHA1));
        putKeyInfo(new PBKDF2KeyInfo("PBKDF2WithHmacSHA224",
                CKP_PKCS5_PBKD2_HMAC_SHA224));
        putKeyInfo(new PBKDF2KeyInfo("PBKDF2WithHmacSHA256",
                CKP_PKCS5_PBKD2_HMAC_SHA256));
        putKeyInfo(new PBKDF2KeyInfo("PBKDF2WithHmacSHA384",
                CKP_PKCS5_PBKD2_HMAC_SHA384));
        putKeyInfo(new PBKDF2KeyInfo("PBKDF2WithHmacSHA512",
                CKP_PKCS5_PBKD2_HMAC_SHA512));

        putKeyInfo(new P12MacPBEKeyInfo("HmacPBESHA1",
                CKM_PBA_SHA1_WITH_SHA1_HMAC, 160));
        putKeyInfo(new P12MacPBEKeyInfo("HmacPBESHA224",
                CKM_NSS_PKCS12_PBE_SHA224_HMAC_KEY_GEN, 224));
        putKeyInfo(new P12MacPBEKeyInfo("HmacPBESHA256",
                CKM_NSS_PKCS12_PBE_SHA256_HMAC_KEY_GEN, 256));
        putKeyInfo(new P12MacPBEKeyInfo("HmacPBESHA384",
                CKM_NSS_PKCS12_PBE_SHA384_HMAC_KEY_GEN, 384));
        putKeyInfo(new P12MacPBEKeyInfo("HmacPBESHA512",
                CKM_NSS_PKCS12_PBE_SHA512_HMAC_KEY_GEN, 512));
    }

    // No pseudo key types
    static long getPKCS11KeyType(String algorithm) {
        long kt = getKeyType(algorithm);
        if (kt == -1 || kt > PCKK_ANY) {
            kt = CKK_GENERIC_SECRET;
        }
        return kt;
    }

    static long getKeyType(String algorithm) {
        KeyInfo ki = getKeyInfo(algorithm);
        return ki == null ? -1 : ki.keyType;
    }

    /**
     * Convert an arbitrary key of algorithm into a P11Key of provider.
     * Used in engineTranslateKey(), P11Cipher.init(), and P11Mac.init().
     */
    static P11Key convertKey(Token token, Key key, String svcAlgo)
            throws InvalidKeyException {
        return convertKey(token, key, svcAlgo, null);
    }

    /**
     * Convert an arbitrary key of algorithm w/ custom attributes into a
     * P11Key of provider.
     * Used in P11KeyStore.storeSkey.
     */
    static P11Key convertKey(Token token, Key key, String svcAlgo,
            CK_ATTRIBUTE[] extraAttrs) throws InvalidKeyException {
        token.ensureValid();
        if (!(key instanceof SecretKey)) {
            throw new InvalidKeyException("Key must be a SecretKey");
        }
        final String keyAlgo = key.getAlgorithm();
        if (keyAlgo == null) {
            throw new InvalidKeyException("Key must specify its algorithm");
        }
        if (svcAlgo == null) {
            svcAlgo = keyAlgo;
        }
        KeyInfo ki = null;
        KeyInfo si = getKeyInfo(svcAlgo);
        if (si == null) {
            throw new InvalidKeyException("Unknown algorithm " + svcAlgo);
        }
        // Check if the key can be used for the service.
        // Any key can be used for a MAC service.
        if (svcAlgo != keyAlgo &&
                si.keyType != PCKK_HMAC && si.keyType != PCKK_SSLMAC) {
            ki = getKeyInfo(keyAlgo);
            if (ki == null || !KeyInfo.checkUse(ki, si)) {
                throw new InvalidKeyException("Cannot use a " + keyAlgo +
                        " key for a " + svcAlgo + " service");
            }
        }
        if (key instanceof P11Key p11Key) {
            if (p11Key.token == token) {
                if (extraAttrs != null) {
                    P11Key newP11Key = null;
                    Session session = null;
                    long p11KeyID = p11Key.getKeyID();
                    try {
                        session = token.getObjSession();
                        long newKeyID = token.p11.C_CopyObject(session.id(),
                            p11KeyID, extraAttrs);
                        newP11Key = (P11Key) (P11Key.secretKey(session,
                                newKeyID, p11Key.algorithm, p11Key.keyLength,
                                extraAttrs));
                    } catch (PKCS11Exception p11e) {
                        throw new InvalidKeyException
                                ("Cannot duplicate the PKCS11 key", p11e);
                    } finally {
                        p11Key.releaseKeyID();
                        token.releaseSession(session);
                    }
                    p11Key = newP11Key;
                }
                return p11Key;
            }
        }
        P11Key p11Key = token.secretCache.get(key);
        if (p11Key != null) {
            return p11Key;
        }
        if (key instanceof PBEKey pbeKey) {
            ki = ki == null ? getKeyInfo(keyAlgo) : ki;
            if (ki instanceof PBEKeyInfo pbeKi) {
                PBEKeySpec keySpec = getPbeKeySpec(pbeKey);
                try {
                    p11Key = derivePBEKey(token, keySpec, pbeKi);
                } catch (InvalidKeySpecException e) {
                    throw new InvalidKeyException(e);
                } finally {
                    keySpec.clearPassword();
                }
            } else {
                throw new InvalidKeyException("Cannot derive unknown " +
                        keyAlgo + " algorithm");
            }
        } else {
            if (si instanceof PBEKeyInfo) {
                throw new InvalidKeyException("PBE service requires a PBE key");
            }
            if (!"RAW".equalsIgnoreCase(key.getFormat())) {
                throw new InvalidKeyException("Encoded format must be RAW");
            }
            byte[] encoded = key.getEncoded();
            try {
                p11Key = createKey(token, encoded, svcAlgo, si.keyType,
                        extraAttrs);
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        }
        token.secretCache.put(key, p11Key);
        return p11Key;
    }

    static P11Key.P11PBEKey derivePBEKey(Token token, PBEKeySpec keySpec,
            PBEKeyInfo pbeKi) throws InvalidKeySpecException {
        token.ensureValid();
        if (keySpec == null) {
            throw new InvalidKeySpecException("PBEKeySpec must not be null");
        }
        Session session = null;
        char[] password = null;
        char[] encPassword = null;
        try {
            session = token.getObjSession();
            CK_MECHANISM ckMech;
            password = keySpec.getPassword();
            byte[] salt = keySpec.getSalt();
            int itCount = keySpec.getIterationCount();
            int keySize = keySpec.getKeyLength();
            assert password != null :
                    "PBEKeySpec does not allow a null password";
            if (salt == null) {
                throw new InvalidKeySpecException("Salt not found");
            }
            assert salt.length > 0 : "PBEKeySpec does not allow an empty salt";
            if (itCount < 1) {
                throw new InvalidKeySpecException("Iteration count must be " +
                        "a non-zero positive integer");
            }
            if (pbeKi.keyLen > 0) {
                if (keySize == 0) {
                    keySize = pbeKi.keyLen;
                } else if (keySize != pbeKi.keyLen) {
                    throw new InvalidKeySpecException(
                            "Key length is invalid for " + pbeKi.algo + " (" +
                            "expecting " + pbeKi.keyLen + " but was " +
                            keySize + ")");
                }
            }
            if (keySize < 1 || keySize % 8 != 0) {
                throw new InvalidKeySpecException("Key length must be " +
                        "multiple of 8 and greater than zero");
            }

            if (pbeKi.kdfMech == CKM_PKCS5_PBKD2) {
                encPassword = P11Util.encodePassword(password,
                        StandardCharsets.UTF_8, 0);
                CK_VERSION p11Ver = token.p11.getVersion();
                if (P11Util.isNSS(token) || p11Ver.major < 2 ||
                        p11Ver.major == 2 && p11Ver.minor < 40) {
                    // NSS keeps using the old structure beyond PKCS #11 v2.40.
                    ckMech = new CK_MECHANISM(pbeKi.kdfMech,
                            new CK_PKCS5_PBKD2_PARAMS(encPassword, salt,
                                    itCount, pbeKi.prfMech));
                } else {
                    ckMech = new CK_MECHANISM(pbeKi.kdfMech,
                            new CK_PKCS5_PBKD2_PARAMS2(encPassword, salt,
                                    itCount, pbeKi.prfMech));
                }
            } else {
                /*
                 * PKCS #12 "General Method" PBKD (RFC 7292, Appendix B.2).
                 *
                 * According to PKCS #11, "password" in CK_PBE_PARAMS is of
                 * CK_UTF8CHAR_PTR type. While this suggests a UTF-8 encoding,
                 * RFC 7292 Appendix B.1 indicates that the password has to be
                 * encoded as a BMPString with a 2-bytes NULL terminator.
                 */
                encPassword = P11Util.encodePassword(password,
                        StandardCharsets.UTF_16BE, 2);
                ckMech = new CK_MECHANISM(pbeKi.kdfMech,
                        new CK_PBE_PARAMS(encPassword, salt, itCount));
            }

            CK_ATTRIBUTE[] attrs =
                    new CK_ATTRIBUTE[3 + pbeKi.extraAttrs.length];
            attrs[0] = new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY);
            attrs[1] = new CK_ATTRIBUTE(CKA_VALUE_LEN, keySize >> 3);
            attrs[2] = new CK_ATTRIBUTE(CKA_KEY_TYPE, pbeKi.keyType);
            System.arraycopy(pbeKi.extraAttrs, 0, attrs, 3,
                    pbeKi.extraAttrs.length);
            CK_ATTRIBUTE[] attr = token.getAttributes(
                    O_GENERATE, CKO_SECRET_KEY, pbeKi.keyType, attrs);
            long keyID = token.p11.C_GenerateKey(session.id(), ckMech, attr);
            return (P11Key.P11PBEKey) P11Key.pbeKey(session, keyID, pbeKi.algo,
                    keySize, attr, password, salt, itCount);
        } catch (PKCS11Exception e) {
            throw new InvalidKeySpecException("Could not create key", e);
        } finally {
            if (encPassword != null) {
                Arrays.fill(encPassword, '\0');
            }
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            token.releaseSession(session);
        }
    }

    private static PBEKeySpec getPbeKeySpec(PBEKey pbeKey) {
        int keyLength = 0;
        if ("RAW".equalsIgnoreCase(pbeKey.getFormat())) {
            byte[] encoded = pbeKey.getEncoded();
            if (encoded != null) {
                keyLength = encoded.length << 3;
                Arrays.fill(encoded, (byte) 0);
            }
        }
        int ic = pbeKey.getIterationCount();
        byte[] salt = pbeKey.getSalt();
        char[] pwd = pbeKey.getPassword();
        try {
            return keyLength == 0 ?
                    new PBEKeySpec(pwd, salt, ic) :
                    new PBEKeySpec(pwd, salt, ic, keyLength);
        } finally {
            if (pwd != null) {
                Arrays.fill(pwd, '\0');
            }
        }
    }

    static void fixDESParity(byte[] key, int offset) {
        for (int i = 0; i < 8; i++) {
            int b = key[offset] & 0xfe;
            b |= (Integer.bitCount(b) & 1) ^ 1;
            key[offset++] = (byte)b;
        }
    }

    private static P11Key createKey(Token token, byte[] encoded,
            String algorithm, long keyType, CK_ATTRIBUTE[] extraAttrs)
            throws InvalidKeyException {
        int n = encoded.length << 3;
        int keyLength = n;
        try {
            switch ((int) keyType) {
                case (int) CKK_DES -> {
                    keyLength =
                            P11KeyGenerator.checkKeySize(CKM_DES_KEY_GEN, n, token);
                    fixDESParity(encoded, 0);
                }
                case (int) CKK_DES3 -> {
                    keyLength =
                            P11KeyGenerator.checkKeySize(CKM_DES3_KEY_GEN, n, token);
                    fixDESParity(encoded, 0);
                    fixDESParity(encoded, 8);
                    if (keyLength == 112) {
                        keyType = CKK_DES2;
                    } else {
                        keyType = CKK_DES3;
                        fixDESParity(encoded, 16);
                    }
                }
                case (int) CKK_AES -> keyLength =
                        P11KeyGenerator.checkKeySize(CKM_AES_KEY_GEN, n, token);
                case (int) CKK_RC4 -> keyLength =
                        P11KeyGenerator.checkKeySize(CKM_RC4_KEY_GEN, n, token);
                case (int) CKK_BLOWFISH -> keyLength =
                        P11KeyGenerator.checkKeySize(CKM_BLOWFISH_KEY_GEN, n,
                                token);
                case (int) CKK_CHACHA20 -> keyLength = P11KeyGenerator.checkKeySize(
                        CKM_CHACHA20_KEY_GEN, n, token);
                case (int) CKK_GENERIC_SECRET, (int) PCKK_TLSPREMASTER, (int) PCKK_TLSRSAPREMASTER, (int) PCKK_TLSMASTER ->
                        keyType = CKK_GENERIC_SECRET;
                case (int) PCKK_SSLMAC, (int) PCKK_HMAC -> {
                    if (n == 0) {
                        throw new InvalidKeyException
                                ("MAC keys must not be empty");
                    }
                    keyType = CKK_GENERIC_SECRET;
                }
                default -> throw new InvalidKeyException("Unknown algorithm " +
                        algorithm);
            }
        } catch (InvalidAlgorithmParameterException iape) {
            throw new InvalidKeyException("Invalid key for " + algorithm,
                    iape);
        } catch (ProviderException pe) {
            throw new InvalidKeyException("Could not create key", pe);
        }
        Session session = null;
        try {
            CK_ATTRIBUTE[] attributes;
            if (extraAttrs != null) {
                attributes = new CK_ATTRIBUTE[3 + extraAttrs.length];
                System.arraycopy(extraAttrs, 0, attributes, 3,
                        extraAttrs.length);
            } else {
                attributes = new CK_ATTRIBUTE[3];
            }
            attributes[0] = new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY);
            attributes[1] = new CK_ATTRIBUTE(CKA_KEY_TYPE, keyType);
            attributes[2] = new CK_ATTRIBUTE(CKA_VALUE, encoded);
            attributes = token.getAttributes
                (O_IMPORT, CKO_SECRET_KEY, keyType, attributes);
            session = token.getObjSession();
            long keyID = token.p11.C_CreateObject(session.id(), attributes);
            P11Key p11Key = (P11Key)P11Key.secretKey
                (session, keyID, algorithm, keyLength, attributes);
            return p11Key;
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("Could not create key", e);
        } finally {
            token.releaseSession(session);
        }
    }

    // see JCE spec
    protected SecretKey engineGenerateSecret(KeySpec keySpec)
            throws InvalidKeySpecException {
        token.ensureValid();
        if (keySpec == null) {
            throw new InvalidKeySpecException("KeySpec must not be null");
        }
        if (keySpec instanceof SecretKeySpec secretKeySpec) {
            try {
                Key key = convertKey(token, secretKeySpec, algorithm);
                return (SecretKey)key;
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        } else if (keySpec instanceof PBEKeySpec pbeKeySpec &&
                svcPbeKi != null) {
            return derivePBEKey(token, pbeKeySpec, svcPbeKi);
        } else if (algorithm.equalsIgnoreCase("DES")) {
            if (keySpec instanceof DESKeySpec desKeySpec) {
                return generateDESSecret(desKeySpec.getKey(), "DES");
            }
        } else if (algorithm.equalsIgnoreCase("DESede")) {
            if (keySpec instanceof DESedeKeySpec desEdeKeySpec) {
                return generateDESSecret(desEdeKeySpec.getKey(), "DESede");
            }
        }
        throw new InvalidKeySpecException
                ("Unsupported spec: " + keySpec.getClass().getName());
    }

    private SecretKey generateDESSecret(byte[] keyBytes, String desAlgo)
            throws InvalidKeySpecException {
        SecretKeySpec secretKeySpec = null;
        try {
            secretKeySpec = new SecretKeySpec(keyBytes, desAlgo);
            return engineGenerateSecret(secretKeySpec);
        } finally {
            if (secretKeySpec != null) {
                SharedSecrets.getJavaxCryptoSpecAccess()
                        .clearSecretKeySpec(secretKeySpec);
            }
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    private byte[] getKeyBytes(SecretKey key) throws InvalidKeySpecException {
        try {
            key = engineTranslateKey(key);
            if (!"RAW".equalsIgnoreCase(key.getFormat())) {
                throw new InvalidKeySpecException("Could not obtain key bytes");
            }
            byte[] k = key.getEncoded();
            return k;
        } catch (InvalidKeyException e) {
            throw new InvalidKeySpecException(e);
        }
    }

    // see JCE spec
    protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpec)
            throws InvalidKeySpecException {
        token.ensureValid();
        if ((key == null) || (keySpec == null)) {
            throw new InvalidKeySpecException
                ("key and keySpec must not be null");
        }
        if (keySpec.isAssignableFrom(SecretKeySpec.class)) {
            return new SecretKeySpec(getKeyBytes(key), algorithm);
        } else if (keySpec.isAssignableFrom(PBEKeySpec.class) &&
                key instanceof PBEKey pbeKey && svcPbeKi != null) {
            return getPbeKeySpec(pbeKey);
        } else if (algorithm.equalsIgnoreCase("DES")) {
            try {
                if (keySpec.isAssignableFrom(DESKeySpec.class)) {
                    return new DESKeySpec(getKeyBytes(key));
                }
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        } else if (algorithm.equalsIgnoreCase("DESede")) {
            try {
                if (keySpec.isAssignableFrom(DESedeKeySpec.class)) {
                    return new DESedeKeySpec(getKeyBytes(key));
                }
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        }
        throw new InvalidKeySpecException
                ("Unsupported spec: " + keySpec.getName());
    }

    // see JCE spec
    protected SecretKey engineTranslateKey(SecretKey key)
            throws InvalidKeyException {
        return (SecretKey)convertKey(token, key, algorithm);
    }

}
