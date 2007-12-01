/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.pkcs11;

import java.util.*;

import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import static sun.security.pkcs11.TemplateManager.*;
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

    P11SecretKeyFactory(Token token, String algorithm) {
        super();
        this.token = token;
        this.algorithm = algorithm;
    }

    private static final Map<String,Long> keyTypes;

    static {
        keyTypes = new HashMap<String,Long>();
        addKeyType("RC4",      CKK_RC4);
        addKeyType("ARCFOUR",  CKK_RC4);
        addKeyType("DES",      CKK_DES);
        addKeyType("DESede",   CKK_DES3);
        addKeyType("AES",      CKK_AES);
        addKeyType("Blowfish", CKK_BLOWFISH);

        // we don't implement RC2 or IDEA, but we want to be able to generate
        // keys for those SSL/TLS ciphersuites.
        addKeyType("RC2",      CKK_RC2);
        addKeyType("IDEA",     CKK_IDEA);

        addKeyType("TlsPremasterSecret",    PCKK_TLSPREMASTER);
        addKeyType("TlsRsaPremasterSecret", PCKK_TLSRSAPREMASTER);
        addKeyType("TlsMasterSecret",       PCKK_TLSMASTER);
        addKeyType("Generic",               CKK_GENERIC_SECRET);
    }

    private static void addKeyType(String name, long id) {
        Long l = Long.valueOf(id);
        keyTypes.put(name, l);
        keyTypes.put(name.toUpperCase(Locale.ENGLISH), l);
    }

    static long getKeyType(String algorithm) {
        Long l = keyTypes.get(algorithm);
        if (l == null) {
            algorithm = algorithm.toUpperCase(Locale.ENGLISH);
            l = keyTypes.get(algorithm);
            if (l == null) {
                if (algorithm.startsWith("HMAC")) {
                    return PCKK_HMAC;
                } else if (algorithm.startsWith("SSLMAC")) {
                    return PCKK_SSLMAC;
                }
            }
        }
        return (l != null) ? l.longValue() : -1;
    }

    /**
     * Convert an arbitrary key of algorithm into a P11Key of provider.
     * Used engineTranslateKey(), P11Cipher.init(), and P11Mac.init().
     */
    static P11Key convertKey(Token token, Key key, String algorithm)
            throws InvalidKeyException {
        token.ensureValid();
        if (key == null) {
            throw new InvalidKeyException("Key must not be null");
        }
        if (key instanceof SecretKey == false) {
            throw new InvalidKeyException("Key must be a SecretKey");
        }
        long algorithmType;
        if (algorithm == null) {
            algorithm = key.getAlgorithm();
            algorithmType = getKeyType(algorithm);
        } else {
            algorithmType = getKeyType(algorithm);
            long keyAlgorithmType = getKeyType(key.getAlgorithm());
            if (algorithmType != keyAlgorithmType) {
                if ((algorithmType == PCKK_HMAC) || (algorithmType == PCKK_SSLMAC)) {
                    // ignore key algorithm for MACs
                } else {
                    throw new InvalidKeyException
                                ("Key algorithm must be " + algorithm);
                }
            }
        }
        if (key instanceof P11Key) {
            P11Key p11Key = (P11Key)key;
            if (p11Key.token == token) {
                return p11Key;
            }
        }
        P11Key p11Key = token.secretCache.get(key);
        if (p11Key != null) {
            return p11Key;
        }
        if ("RAW".equals(key.getFormat()) == false) {
            throw new InvalidKeyException("Encoded format must be RAW");
        }
        byte[] encoded = key.getEncoded();
        p11Key = createKey(token, encoded, algorithm, algorithmType);
        token.secretCache.put(key, p11Key);
        return p11Key;
    }

    static void fixDESParity(byte[] key, int offset) {
        for (int i = 0; i < 8; i++) {
            int b = key[offset] & 0xfe;
            b |= (Integer.bitCount(b) & 1) ^ 1;
            key[offset++] = (byte)b;
        }
    }

    private static P11Key createKey(Token token, byte[] encoded,
            String algorithm, long keyType) throws InvalidKeyException {
        int n = encoded.length;
        int keyLength;
        switch ((int)keyType) {
        case (int)CKK_RC4:
            if ((n < 5) || (n > 128)) {
                throw new InvalidKeyException
                        ("ARCFOUR key length must be between 5 and 128 bytes");
            }
            keyLength = n << 3;
            break;
        case (int)CKK_DES:
            if (n != 8) {
                throw new InvalidKeyException
                        ("DES key length must be 8 bytes");
            }
            keyLength = 56;
            fixDESParity(encoded, 0);
            break;
        case (int)CKK_DES3:
            if (n == 16) {
                keyType = CKK_DES2;
            } else if (n == 24) {
                keyType = CKK_DES3;
                fixDESParity(encoded, 16);
            } else {
                throw new InvalidKeyException
                        ("DESede key length must be 16 or 24 bytes");
            }
            fixDESParity(encoded, 0);
            fixDESParity(encoded, 8);
            keyLength = n * 7;
            break;
        case (int)CKK_AES:
            if ((n != 16) && (n != 24) && (n != 32)) {
                throw new InvalidKeyException
                        ("AES key length must be 16, 24, or 32 bytes");
            }
            keyLength = n << 3;
            break;
        case (int)CKK_BLOWFISH:
            if ((n < 5) || (n > 56)) {
                throw new InvalidKeyException
                        ("Blowfish key length must be between 5 and 56 bytes");
            }
            keyLength = n << 3;
            break;
        case (int)CKK_GENERIC_SECRET:
        case (int)PCKK_TLSPREMASTER:
        case (int)PCKK_TLSRSAPREMASTER:
        case (int)PCKK_TLSMASTER:
            keyType = CKK_GENERIC_SECRET;
            keyLength = n << 3;
            break;
        case (int)PCKK_SSLMAC:
        case (int)PCKK_HMAC:
            if (n == 0) {
                throw new InvalidKeyException
                        ("MAC keys must not be empty");
            }
            keyType = CKK_GENERIC_SECRET;
            keyLength = n << 3;
            break;
        default:
            throw new InvalidKeyException("Unknown algorithm " + algorithm);
        }
        Session session = null;
        try {
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_CLASS, CKO_SECRET_KEY),
                new CK_ATTRIBUTE(CKA_KEY_TYPE, keyType),
                new CK_ATTRIBUTE(CKA_VALUE, encoded),
            };
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
        if (keySpec instanceof SecretKeySpec) {
            try {
                Key key = convertKey(token, (SecretKey)keySpec, algorithm);
                return (SecretKey)key;
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        } else if (algorithm.equalsIgnoreCase("DES")) {
            if (keySpec instanceof DESKeySpec) {
                byte[] keyBytes = ((DESKeySpec)keySpec).getKey();
                keySpec = new SecretKeySpec(keyBytes, "DES");
                return engineGenerateSecret(keySpec);
            }
        } else if (algorithm.equalsIgnoreCase("DESede")) {
            if (keySpec instanceof DESedeKeySpec) {
                byte[] keyBytes = ((DESedeKeySpec)keySpec).getKey();
                keySpec = new SecretKeySpec(keyBytes, "DESede");
                return engineGenerateSecret(keySpec);
            }
        }
        throw new InvalidKeySpecException
                ("Unsupported spec: " + keySpec.getClass().getName());
    }

    private byte[] getKeyBytes(SecretKey key) throws InvalidKeySpecException {
        try {
            key = engineTranslateKey(key);
            if ("RAW".equals(key.getFormat()) == false) {
                throw new InvalidKeySpecException
                    ("Could not obtain key bytes");
            }
            byte[] k = key.getEncoded();
            return k;
        } catch (InvalidKeyException e) {
            throw new InvalidKeySpecException(e);
        }
    }

    // see JCE spec
    protected KeySpec engineGetKeySpec(SecretKey key, Class keySpec)
            throws InvalidKeySpecException {
        token.ensureValid();
        if ((key == null) || (keySpec == null)) {
            throw new InvalidKeySpecException
                ("key and keySpec must not be null");
        }
        if (SecretKeySpec.class.isAssignableFrom(keySpec)) {
            return new SecretKeySpec(getKeyBytes(key), algorithm);
        } else if (algorithm.equalsIgnoreCase("DES")) {
            try {
                if (DESKeySpec.class.isAssignableFrom(keySpec)) {
                    return new DESKeySpec(getKeyBytes(key));
                }
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException(e);
            }
        } else if (algorithm.equalsIgnoreCase("DESede")) {
            try {
                if (DESedeKeySpec.class.isAssignableFrom(keySpec)) {
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
