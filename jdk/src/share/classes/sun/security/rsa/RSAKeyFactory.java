/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.rsa;

import java.math.BigInteger;

import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

/**
 * KeyFactory for RSA keys. Keys must be instances of PublicKey or PrivateKey
 * and getAlgorithm() must return "RSA". For such keys, it supports conversion
 * between the following:
 *
 * For public keys:
 *  . PublicKey with an X.509 encoding
 *  . RSAPublicKey
 *  . RSAPublicKeySpec
 *  . X509EncodedKeySpec
 *
 * For private keys:
 *  . PrivateKey with a PKCS#8 encoding
 *  . RSAPrivateKey
 *  . RSAPrivateCrtKey
 *  . RSAPrivateKeySpec
 *  . RSAPrivateCrtKeySpec
 *  . PKCS8EncodedKeySpec
 * (of course, CRT variants only for CRT keys)
 *
 * Note: as always, RSA keys should be at least 512 bits long
 *
 * @since   1.5
 * @author  Andreas Sterbenz
 */
public final class RSAKeyFactory extends KeyFactorySpi {

    private final static Class<?> rsaPublicKeySpecClass =
                                                RSAPublicKeySpec.class;
    private final static Class<?> rsaPrivateKeySpecClass =
                                                RSAPrivateKeySpec.class;
    private final static Class<?> rsaPrivateCrtKeySpecClass =
                                                RSAPrivateCrtKeySpec.class;

    private final static Class<?> x509KeySpecClass  = X509EncodedKeySpec.class;
    private final static Class<?> pkcs8KeySpecClass = PKCS8EncodedKeySpec.class;

    // instance used for static translateKey();
    private final static RSAKeyFactory INSTANCE = new RSAKeyFactory();

    public RSAKeyFactory() {
        // empty
    }

    /**
     * Static method to convert Key into a useable instance of
     * RSAPublicKey or RSAPrivate(Crt)Key. Check the key and convert it
     * to a SunRsaSign key if necessary. If the key is not an RSA key
     * or cannot be used, throw an InvalidKeyException.
     *
     * The difference between this method and engineTranslateKey() is that
     * we do not convert keys of other providers that are already an
     * instance of RSAPublicKey or RSAPrivate(Crt)Key.
     *
     * Used by RSASignature and RSACipher.
     */
    public static RSAKey toRSAKey(Key key) throws InvalidKeyException {
        if (key instanceof RSAKey) {
            RSAKey rsaKey = (RSAKey)key;
            checkKey(rsaKey);
            return rsaKey;
        } else {
            return (RSAKey)INSTANCE.engineTranslateKey(key);
        }
    }

    /**
     * Check that the given RSA key is valid.
     */
    private static void checkKey(RSAKey key) throws InvalidKeyException {
        // check for subinterfaces, omit additional checks for our keys
        if (key instanceof RSAPublicKey) {
            if (key instanceof RSAPublicKeyImpl) {
                return;
            }
        } else if (key instanceof RSAPrivateKey) {
            if ((key instanceof RSAPrivateCrtKeyImpl)
                    || (key instanceof RSAPrivateKeyImpl)) {
                return;
            }
        } else {
            throw new InvalidKeyException("Neither a public nor a private key");
        }
        // RSAKey does not extend Key, so we need to do a cast
        String keyAlg = ((Key)key).getAlgorithm();
        if (keyAlg.equals("RSA") == false) {
            throw new InvalidKeyException("Not an RSA key: " + keyAlg);
        }
        BigInteger modulus;
        // some providers implement RSAKey for keys where the values are
        // not accessible (although they should). Detect those here
        // for a more graceful failure.
        try {
            modulus = key.getModulus();
            if (modulus == null) {
                throw new InvalidKeyException("Modulus is missing");
            }
        } catch (RuntimeException e) {
            throw new InvalidKeyException(e);
        }
        checkKeyLength(modulus);
    }

    /**
     * Check the length of the modulus of an RSA key. We only support keys
     * at least 505 bits long.
     */
    static void checkKeyLength(BigInteger modulus) throws InvalidKeyException {
        if (modulus.bitLength() < 505) {
            // some providers may generate slightly shorter keys
            // accept them if the encoding is at least 64 bytes long
            throw new InvalidKeyException
                ("RSA keys must be at least 512 bits long");
        }
    }

    /**
     * Translate an RSA key into a SunRsaSign RSA key. If conversion is
     * not possible, throw an InvalidKeyException.
     * See also JCA doc.
     */
    protected Key engineTranslateKey(Key key) throws InvalidKeyException {
        if (key == null) {
            throw new InvalidKeyException("Key must not be null");
        }
        String keyAlg = key.getAlgorithm();
        if (keyAlg.equals("RSA") == false) {
            throw new InvalidKeyException("Not an RSA key: " + keyAlg);
        }
        if (key instanceof PublicKey) {
            return translatePublicKey((PublicKey)key);
        } else if (key instanceof PrivateKey) {
            return translatePrivateKey((PrivateKey)key);
        } else {
            throw new InvalidKeyException("Neither a public nor a private key");
        }
    }

    // see JCA doc
    protected PublicKey engineGeneratePublic(KeySpec keySpec)
            throws InvalidKeySpecException {
        try {
            return generatePublic(keySpec);
        } catch (InvalidKeySpecException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new InvalidKeySpecException(e);
        }
    }

    // see JCA doc
    protected PrivateKey engineGeneratePrivate(KeySpec keySpec)
            throws InvalidKeySpecException {
        try {
            return generatePrivate(keySpec);
        } catch (InvalidKeySpecException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new InvalidKeySpecException(e);
        }
    }

    // internal implementation of translateKey() for public keys. See JCA doc
    private PublicKey translatePublicKey(PublicKey key)
            throws InvalidKeyException {
        if (key instanceof RSAPublicKey) {
            if (key instanceof RSAPublicKeyImpl) {
                return key;
            }
            RSAPublicKey rsaKey = (RSAPublicKey)key;
            try {
                return new RSAPublicKeyImpl(
                    rsaKey.getModulus(),
                    rsaKey.getPublicExponent()
                );
            } catch (RuntimeException e) {
                // catch providers that incorrectly implement RSAPublicKey
                throw new InvalidKeyException("Invalid key", e);
            }
        } else if ("X.509".equals(key.getFormat())) {
            byte[] encoded = key.getEncoded();
            return new RSAPublicKeyImpl(encoded);
        } else {
            throw new InvalidKeyException("Public keys must be instance "
                + "of RSAPublicKey or have X.509 encoding");
        }
    }

    // internal implementation of translateKey() for private keys. See JCA doc
    private PrivateKey translatePrivateKey(PrivateKey key)
            throws InvalidKeyException {
        if (key instanceof RSAPrivateCrtKey) {
            if (key instanceof RSAPrivateCrtKeyImpl) {
                return key;
            }
            RSAPrivateCrtKey rsaKey = (RSAPrivateCrtKey)key;
            try {
                return new RSAPrivateCrtKeyImpl(
                    rsaKey.getModulus(),
                    rsaKey.getPublicExponent(),
                    rsaKey.getPrivateExponent(),
                    rsaKey.getPrimeP(),
                    rsaKey.getPrimeQ(),
                    rsaKey.getPrimeExponentP(),
                    rsaKey.getPrimeExponentQ(),
                    rsaKey.getCrtCoefficient()
                );
            } catch (RuntimeException e) {
                // catch providers that incorrectly implement RSAPrivateCrtKey
                throw new InvalidKeyException("Invalid key", e);
            }
        } else if (key instanceof RSAPrivateKey) {
            if (key instanceof RSAPrivateKeyImpl) {
                return key;
            }
            RSAPrivateKey rsaKey = (RSAPrivateKey)key;
            try {
                return new RSAPrivateKeyImpl(
                    rsaKey.getModulus(),
                    rsaKey.getPrivateExponent()
                );
            } catch (RuntimeException e) {
                // catch providers that incorrectly implement RSAPrivateKey
                throw new InvalidKeyException("Invalid key", e);
            }
        } else if ("PKCS#8".equals(key.getFormat())) {
            byte[] encoded = key.getEncoded();
            return RSAPrivateCrtKeyImpl.newKey(encoded);
        } else {
            throw new InvalidKeyException("Private keys must be instance "
                + "of RSAPrivate(Crt)Key or have PKCS#8 encoding");
        }
    }

    // internal implementation of generatePublic. See JCA doc
    private PublicKey generatePublic(KeySpec keySpec)
            throws GeneralSecurityException {
        if (keySpec instanceof X509EncodedKeySpec) {
            X509EncodedKeySpec x509Spec = (X509EncodedKeySpec)keySpec;
            return new RSAPublicKeyImpl(x509Spec.getEncoded());
        } else if (keySpec instanceof RSAPublicKeySpec) {
            RSAPublicKeySpec rsaSpec = (RSAPublicKeySpec)keySpec;
            return new RSAPublicKeyImpl(
                rsaSpec.getModulus(),
                rsaSpec.getPublicExponent()
            );
        } else {
            throw new InvalidKeySpecException("Only RSAPublicKeySpec "
                + "and X509EncodedKeySpec supported for RSA public keys");
        }
    }

    // internal implementation of generatePrivate. See JCA doc
    private PrivateKey generatePrivate(KeySpec keySpec)
            throws GeneralSecurityException {
        if (keySpec instanceof PKCS8EncodedKeySpec) {
            PKCS8EncodedKeySpec pkcsSpec = (PKCS8EncodedKeySpec)keySpec;
            return RSAPrivateCrtKeyImpl.newKey(pkcsSpec.getEncoded());
        } else if (keySpec instanceof RSAPrivateCrtKeySpec) {
            RSAPrivateCrtKeySpec rsaSpec = (RSAPrivateCrtKeySpec)keySpec;
            return new RSAPrivateCrtKeyImpl(
                rsaSpec.getModulus(),
                rsaSpec.getPublicExponent(),
                rsaSpec.getPrivateExponent(),
                rsaSpec.getPrimeP(),
                rsaSpec.getPrimeQ(),
                rsaSpec.getPrimeExponentP(),
                rsaSpec.getPrimeExponentQ(),
                rsaSpec.getCrtCoefficient()
            );
        } else if (keySpec instanceof RSAPrivateKeySpec) {
            RSAPrivateKeySpec rsaSpec = (RSAPrivateKeySpec)keySpec;
            return new RSAPrivateKeyImpl(
                rsaSpec.getModulus(),
                rsaSpec.getPrivateExponent()
            );
        } else {
            throw new InvalidKeySpecException("Only RSAPrivate(Crt)KeySpec "
                + "and PKCS8EncodedKeySpec supported for RSA private keys");
        }
    }

    protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec)
            throws InvalidKeySpecException {
        try {
            // convert key to one of our keys
            // this also verifies that the key is a valid RSA key and ensures
            // that the encoding is X.509/PKCS#8 for public/private keys
            key = engineTranslateKey(key);
        } catch (InvalidKeyException e) {
            throw new InvalidKeySpecException(e);
        }
        if (key instanceof RSAPublicKey) {
            RSAPublicKey rsaKey = (RSAPublicKey)key;
            if (rsaPublicKeySpecClass.isAssignableFrom(keySpec)) {
                return (T) new RSAPublicKeySpec(
                    rsaKey.getModulus(),
                    rsaKey.getPublicExponent()
                );
            } else if (x509KeySpecClass.isAssignableFrom(keySpec)) {
                return (T) new X509EncodedKeySpec(key.getEncoded());
            } else {
                throw new InvalidKeySpecException
                        ("KeySpec must be RSAPublicKeySpec or "
                        + "X509EncodedKeySpec for RSA public keys");
            }
        } else if (key instanceof RSAPrivateKey) {
            if (pkcs8KeySpecClass.isAssignableFrom(keySpec)) {
                return (T) new PKCS8EncodedKeySpec(key.getEncoded());
            } else if (rsaPrivateCrtKeySpecClass.isAssignableFrom(keySpec)) {
                if (key instanceof RSAPrivateCrtKey) {
                    RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey)key;
                    return (T) new RSAPrivateCrtKeySpec(
                        crtKey.getModulus(),
                        crtKey.getPublicExponent(),
                        crtKey.getPrivateExponent(),
                        crtKey.getPrimeP(),
                        crtKey.getPrimeQ(),
                        crtKey.getPrimeExponentP(),
                        crtKey.getPrimeExponentQ(),
                        crtKey.getCrtCoefficient()
                    );
                } else {
                    throw new InvalidKeySpecException
                    ("RSAPrivateCrtKeySpec can only be used with CRT keys");
                }
            } else if (rsaPrivateKeySpecClass.isAssignableFrom(keySpec)) {
                RSAPrivateKey rsaKey = (RSAPrivateKey)key;
                return (T) new RSAPrivateKeySpec(
                    rsaKey.getModulus(),
                    rsaKey.getPrivateExponent()
                );
            } else {
                throw new InvalidKeySpecException
                        ("KeySpec must be RSAPrivate(Crt)KeySpec or "
                        + "PKCS8EncodedKeySpec for RSA private keys");
            }
        } else {
            // should not occur, caught in engineTranslateKey()
            throw new InvalidKeySpecException("Neither public nor private key");
        }
    }
}
