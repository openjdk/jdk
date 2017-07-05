/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;

import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

import static sun.security.pkcs11.TemplateManager.*;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

import sun.security.rsa.RSAKeyFactory;

/**
 * RSA KeyFactory implementation.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11RSAKeyFactory extends P11KeyFactory {

    P11RSAKeyFactory(Token token, String algorithm) {
        super(token, algorithm);
    }

    PublicKey implTranslatePublicKey(PublicKey key) throws InvalidKeyException {
        try {
            if (key instanceof RSAPublicKey) {
                RSAPublicKey rsaKey = (RSAPublicKey)key;
                return generatePublic(
                    rsaKey.getModulus(),
                    rsaKey.getPublicExponent()
                );
            } else if ("X.509".equals(key.getFormat())) {
                // let SunRsaSign provider parse for us, then recurse
                byte[] encoded = key.getEncoded();
                key = new sun.security.rsa.RSAPublicKeyImpl(encoded);
                return implTranslatePublicKey(key);
            } else {
                throw new InvalidKeyException("PublicKey must be instance "
                        + "of RSAPublicKey or have X.509 encoding");
            }
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("Could not create RSA public key", e);
        }
    }

    PrivateKey implTranslatePrivateKey(PrivateKey key)
            throws InvalidKeyException {
        try {
            if (key instanceof RSAPrivateCrtKey) {
                RSAPrivateCrtKey rsaKey = (RSAPrivateCrtKey)key;
                return generatePrivate(
                    rsaKey.getModulus(),
                    rsaKey.getPublicExponent(),
                    rsaKey.getPrivateExponent(),
                    rsaKey.getPrimeP(),
                    rsaKey.getPrimeQ(),
                    rsaKey.getPrimeExponentP(),
                    rsaKey.getPrimeExponentQ(),
                    rsaKey.getCrtCoefficient()
                );
            } else if (key instanceof RSAPrivateKey) {
                RSAPrivateKey rsaKey = (RSAPrivateKey)key;
                return generatePrivate(
                    rsaKey.getModulus(),
                    rsaKey.getPrivateExponent()
                );
            } else if ("PKCS#8".equals(key.getFormat())) {
                // let SunRsaSign provider parse for us, then recurse
                byte[] encoded = key.getEncoded();
                key = sun.security.rsa.RSAPrivateCrtKeyImpl.newKey(encoded);
                return implTranslatePrivateKey(key);
            } else {
                throw new InvalidKeyException("Private key must be instance "
                        + "of RSAPrivate(Crt)Key or have PKCS#8 encoding");
            }
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("Could not create RSA private key", e);
        }
    }

    // see JCA spec
    protected PublicKey engineGeneratePublic(KeySpec keySpec)
            throws InvalidKeySpecException {
        token.ensureValid();
        if (keySpec instanceof X509EncodedKeySpec) {
            try {
                byte[] encoded = ((X509EncodedKeySpec)keySpec).getEncoded();
                PublicKey key = new sun.security.rsa.RSAPublicKeyImpl(encoded);
                return implTranslatePublicKey(key);
            } catch (InvalidKeyException e) {
                throw new InvalidKeySpecException
                        ("Could not create RSA public key", e);
            }
        }
        if (keySpec instanceof RSAPublicKeySpec == false) {
            throw new InvalidKeySpecException("Only RSAPublicKeySpec and "
                + "X509EncodedKeySpec supported for RSA public keys");
        }
        try {
            RSAPublicKeySpec rs = (RSAPublicKeySpec)keySpec;
            return generatePublic(
                rs.getModulus(),
                rs.getPublicExponent()
            );
        } catch (PKCS11Exception | InvalidKeyException e) {
            throw new InvalidKeySpecException
                ("Could not create RSA public key", e);
        }
    }

    // see JCA spec
    protected PrivateKey engineGeneratePrivate(KeySpec keySpec)
            throws InvalidKeySpecException {
        token.ensureValid();
        if (keySpec instanceof PKCS8EncodedKeySpec) {
            try {
                byte[] encoded = ((PKCS8EncodedKeySpec)keySpec).getEncoded();
                PrivateKey key =
                        sun.security.rsa.RSAPrivateCrtKeyImpl.newKey(encoded);
                return implTranslatePrivateKey(key);
            } catch (GeneralSecurityException e) {
                throw new InvalidKeySpecException
                        ("Could not create RSA private key", e);
            }
        }
        try {
            if (keySpec instanceof RSAPrivateCrtKeySpec) {
                RSAPrivateCrtKeySpec rs = (RSAPrivateCrtKeySpec)keySpec;
                return generatePrivate(
                    rs.getModulus(),
                    rs.getPublicExponent(),
                    rs.getPrivateExponent(),
                    rs.getPrimeP(),
                    rs.getPrimeQ(),
                    rs.getPrimeExponentP(),
                    rs.getPrimeExponentQ(),
                    rs.getCrtCoefficient()
                );
            } else if (keySpec instanceof RSAPrivateKeySpec) {
                RSAPrivateKeySpec rs = (RSAPrivateKeySpec)keySpec;
                return generatePrivate(
                    rs.getModulus(),
                    rs.getPrivateExponent()
                );
            } else {
                throw new InvalidKeySpecException("Only RSAPrivate(Crt)KeySpec "
                    + "and PKCS8EncodedKeySpec supported for RSA private keys");
            }
        } catch (PKCS11Exception | InvalidKeyException e) {
            throw new InvalidKeySpecException
                ("Could not create RSA private key", e);
        }
    }

    private PublicKey generatePublic(BigInteger n, BigInteger e)
            throws PKCS11Exception, InvalidKeyException {
        RSAKeyFactory.checkKeyLengths(n.bitLength(), e, -1, 64 * 1024);
        CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
            new CK_ATTRIBUTE(CKA_CLASS, CKO_PUBLIC_KEY),
            new CK_ATTRIBUTE(CKA_KEY_TYPE, CKK_RSA),
            new CK_ATTRIBUTE(CKA_MODULUS, n),
            new CK_ATTRIBUTE(CKA_PUBLIC_EXPONENT, e),
        };
        attributes = token.getAttributes
                (O_IMPORT, CKO_PUBLIC_KEY, CKK_RSA, attributes);
        Session session = null;
        try {
            session = token.getObjSession();
            long keyID = token.p11.C_CreateObject(session.id(), attributes);
            return P11Key.publicKey
                (session, keyID, "RSA", n.bitLength(), attributes);
        } finally {
            token.releaseSession(session);
        }
    }

    private PrivateKey generatePrivate(BigInteger n, BigInteger d)
            throws PKCS11Exception, InvalidKeyException {
        RSAKeyFactory.checkKeyLengths(n.bitLength(), null, -1, 64 * 1024);
        CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
            new CK_ATTRIBUTE(CKA_CLASS, CKO_PRIVATE_KEY),
            new CK_ATTRIBUTE(CKA_KEY_TYPE, CKK_RSA),
            new CK_ATTRIBUTE(CKA_MODULUS, n),
            new CK_ATTRIBUTE(CKA_PRIVATE_EXPONENT, d),
        };
        attributes = token.getAttributes
                (O_IMPORT, CKO_PRIVATE_KEY, CKK_RSA, attributes);
        Session session = null;
        try {
            session = token.getObjSession();
            long keyID = token.p11.C_CreateObject(session.id(), attributes);
            return P11Key.privateKey
                (session,  keyID, "RSA", n.bitLength(), attributes);
        } finally {
            token.releaseSession(session);
        }
    }

    private PrivateKey generatePrivate(BigInteger n, BigInteger e,
            BigInteger d, BigInteger p, BigInteger q, BigInteger pe,
            BigInteger qe, BigInteger coeff) throws PKCS11Exception,
            InvalidKeyException {
        RSAKeyFactory.checkKeyLengths(n.bitLength(), e, -1, 64 * 1024);
        CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
            new CK_ATTRIBUTE(CKA_CLASS, CKO_PRIVATE_KEY),
            new CK_ATTRIBUTE(CKA_KEY_TYPE, CKK_RSA),
            new CK_ATTRIBUTE(CKA_MODULUS, n),
            new CK_ATTRIBUTE(CKA_PUBLIC_EXPONENT, e),
            new CK_ATTRIBUTE(CKA_PRIVATE_EXPONENT, d),
            new CK_ATTRIBUTE(CKA_PRIME_1, p),
            new CK_ATTRIBUTE(CKA_PRIME_2, q),
            new CK_ATTRIBUTE(CKA_EXPONENT_1, pe),
            new CK_ATTRIBUTE(CKA_EXPONENT_2, qe),
            new CK_ATTRIBUTE(CKA_COEFFICIENT, coeff),
        };
        attributes = token.getAttributes
                (O_IMPORT, CKO_PRIVATE_KEY, CKK_RSA, attributes);
        Session session = null;
        try {
            session = token.getObjSession();
            long keyID = token.p11.C_CreateObject(session.id(), attributes);
            return P11Key.privateKey
                (session, keyID, "RSA", n.bitLength(), attributes);
        } finally {
            token.releaseSession(session);
        }
    }

    <T extends KeySpec> T implGetPublicKeySpec(P11Key key, Class<T> keySpec,
            Session[] session) throws PKCS11Exception, InvalidKeySpecException {
        if (RSAPublicKeySpec.class.isAssignableFrom(keySpec)) {
            session[0] = token.getObjSession();
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_MODULUS),
                new CK_ATTRIBUTE(CKA_PUBLIC_EXPONENT),
            };
            token.p11.C_GetAttributeValue(session[0].id(), key.keyID, attributes);
            KeySpec spec = new RSAPublicKeySpec(
                attributes[0].getBigInteger(),
                attributes[1].getBigInteger()
            );
            return keySpec.cast(spec);
        } else { // X.509 handled in superclass
            throw new InvalidKeySpecException("Only RSAPublicKeySpec and "
                + "X509EncodedKeySpec supported for RSA public keys");
        }
    }

    <T extends KeySpec> T implGetPrivateKeySpec(P11Key key, Class<T> keySpec,
            Session[] session) throws PKCS11Exception, InvalidKeySpecException {
        if (RSAPrivateCrtKeySpec.class.isAssignableFrom(keySpec)) {
            session[0] = token.getObjSession();
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_MODULUS),
                new CK_ATTRIBUTE(CKA_PUBLIC_EXPONENT),
                new CK_ATTRIBUTE(CKA_PRIVATE_EXPONENT),
                new CK_ATTRIBUTE(CKA_PRIME_1),
                new CK_ATTRIBUTE(CKA_PRIME_2),
                new CK_ATTRIBUTE(CKA_EXPONENT_1),
                new CK_ATTRIBUTE(CKA_EXPONENT_2),
                new CK_ATTRIBUTE(CKA_COEFFICIENT),
            };
            token.p11.C_GetAttributeValue(session[0].id(), key.keyID, attributes);
            KeySpec spec = new RSAPrivateCrtKeySpec(
                attributes[0].getBigInteger(),
                attributes[1].getBigInteger(),
                attributes[2].getBigInteger(),
                attributes[3].getBigInteger(),
                attributes[4].getBigInteger(),
                attributes[5].getBigInteger(),
                attributes[6].getBigInteger(),
                attributes[7].getBigInteger()
            );
            return keySpec.cast(spec);
        } else if (RSAPrivateKeySpec.class.isAssignableFrom(keySpec)) {
            session[0] = token.getObjSession();
            CK_ATTRIBUTE[] attributes = new CK_ATTRIBUTE[] {
                new CK_ATTRIBUTE(CKA_MODULUS),
                new CK_ATTRIBUTE(CKA_PRIVATE_EXPONENT),
            };
            token.p11.C_GetAttributeValue(session[0].id(), key.keyID, attributes);
            KeySpec spec = new RSAPrivateKeySpec(
                attributes[0].getBigInteger(),
                attributes[1].getBigInteger()
            );
            return keySpec.cast(spec);
        } else { // PKCS#8 handled in superclass
            throw new InvalidKeySpecException("Only RSAPrivate(Crt)KeySpec "
                + "and PKCS8EncodedKeySpec supported for RSA private keys");
        }
    }

    KeyFactory implGetSoftwareFactory() throws GeneralSecurityException {
        return KeyFactory.getInstance("RSA", P11Util.getSunRsaSignProvider());
    }

}
