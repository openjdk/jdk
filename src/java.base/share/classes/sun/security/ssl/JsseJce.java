/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import sun.security.jca.ProviderList;
import sun.security.jca.Providers;
import static sun.security.ssl.SunJSSE.cryptoProvider;
import sun.security.util.ECUtil;
import static sun.security.util.SecurityConstants.PROVIDER_VER;

/**
 * This class contains a few static methods for interaction with the JCA/JCE
 * to obtain implementations, etc.
 *
 * @author  Andreas Sterbenz
 */
final class JsseJce {
    static final boolean ALLOW_ECC =
            Utilities.getBooleanProperty("com.sun.net.ssl.enableECC", true);

    private static final ProviderList fipsProviderList;

    static {
        // force FIPS flag initialization
        // Because isFIPS() is synchronized and cryptoProvider is not modified
        // after it completes, this also eliminates the need for any further
        // synchronization when accessing cryptoProvider
        if (SunJSSE.isFIPS() == false) {
            fipsProviderList = null;
        } else {
            // Setup a ProviderList that can be used by the trust manager
            // during certificate chain validation. All the crypto must be
            // from the FIPS provider, but we also allow the required
            // certificate related services from the SUN provider.
            Provider sun = Security.getProvider("SUN");
            if (sun == null) {
                throw new RuntimeException
                    ("FIPS mode: SUN provider must be installed");
            }
            Provider sunCerts = new SunCertificates(sun);
            fipsProviderList = ProviderList.newList(cryptoProvider, sunCerts);
        }
    }

    private static final class SunCertificates extends Provider {
        private static final long serialVersionUID = -3284138292032213752L;

        SunCertificates(final Provider p) {
            super("SunCertificates", PROVIDER_VER, "SunJSSE internal");
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    // copy certificate related services from the Sun provider
                    for (Map.Entry<Object,Object> entry : p.entrySet()) {
                        String key = (String)entry.getKey();
                        if (key.startsWith("CertPathValidator.")
                                || key.startsWith("CertPathBuilder.")
                                || key.startsWith("CertStore.")
                                || key.startsWith("CertificateFactory.")) {
                            put(key, entry.getValue());
                        }
                    }
                    return null;
                }
            });
        }
    }

    /**
     * JCE transformation string for RSA with PKCS#1 v1.5 padding.
     * Can be used for encryption, decryption, signing, verifying.
     */
    static final String CIPHER_RSA_PKCS1 = "RSA/ECB/PKCS1Padding";

    /**
     * JCE transformation string for the stream cipher RC4.
     */
    static final String CIPHER_RC4 = "RC4";

    /**
     * JCE transformation string for DES in CBC mode without padding.
     */
    static final String CIPHER_DES = "DES/CBC/NoPadding";

    /**
     * JCE transformation string for (3-key) Triple DES in CBC mode
     * without padding.
     */
    static final String CIPHER_3DES = "DESede/CBC/NoPadding";

    /**
     * JCE transformation string for AES in CBC mode
     * without padding.
     */
    static final String CIPHER_AES = "AES/CBC/NoPadding";

    /**
     * JCE transformation string for AES in GCM mode
     * without padding.
     */
    static final String CIPHER_AES_GCM = "AES/GCM/NoPadding";

    /**
     * JCE transformation string for ChaCha20-Poly1305
     */
    static final String CIPHER_CHACHA20_POLY1305 = "ChaCha20-Poly1305";

    /**
     * JCA identifier string for DSA, i.e. a DSA with SHA-1.
     */
    static final String SIGNATURE_DSA = "DSA";

    /**
     * JCA identifier string for ECDSA, i.e. a ECDSA with SHA-1.
     */
    static final String SIGNATURE_ECDSA = "SHA1withECDSA";

    /**
     * JCA identifier string for Raw DSA, i.e. a DSA signature without
     * hashing where the application provides the SHA-1 hash of the data.
     * Note that the standard name is "NONEwithDSA" but we use "RawDSA"
     * for compatibility.
     */
    static final String SIGNATURE_RAWDSA = "RawDSA";

    /**
     * JCA identifier string for Raw ECDSA, i.e. a DSA signature without
     * hashing where the application provides the SHA-1 hash of the data.
     */
    static final String SIGNATURE_RAWECDSA = "NONEwithECDSA";

    /**
     * JCA identifier string for Raw RSA, i.e. a RSA PKCS#1 v1.5 signature
     * without hashing where the application provides the hash of the data.
     * Used for RSA client authentication with a 36 byte hash.
     */
    static final String SIGNATURE_RAWRSA = "NONEwithRSA";

    /**
     * JCA identifier string for the SSL/TLS style RSA Signature. I.e.
     * an signature using RSA with PKCS#1 v1.5 padding signing a
     * concatenation of an MD5 and SHA-1 digest.
     */
    static final String SIGNATURE_SSLRSA = "MD5andSHA1withRSA";

    private JsseJce() {
        // no instantiation of this class
    }

    static boolean isEcAvailable() {
        return EcAvailability.isAvailable;
    }

    /**
     * Return an JCE cipher implementation for the specified algorithm.
     */
    static Cipher getCipher(String transformation)
            throws NoSuchAlgorithmException {
        try {
            if (cryptoProvider == null) {
                return Cipher.getInstance(transformation);
            } else {
                return Cipher.getInstance(transformation, cryptoProvider);
            }
        } catch (NoSuchPaddingException e) {
            throw new NoSuchAlgorithmException(e);
        }
    }

    /**
     * Return an JCA signature implementation for the specified algorithm.
     * The algorithm string should be one of the constants defined
     * in this class.
     */
    static Signature getSignature(String algorithm)
            throws NoSuchAlgorithmException {
        if (cryptoProvider == null) {
            return Signature.getInstance(algorithm);
        } else {
            // reference equality
            if (algorithm == SIGNATURE_SSLRSA) {
                // The SunPKCS11 provider currently does not support this
                // special algorithm. We allow a fallback in this case because
                // the SunJSSE implementation does the actual crypto using
                // a NONEwithRSA signature obtained from the cryptoProvider.
                if (cryptoProvider.getService("Signature", algorithm) == null) {
                    // Calling Signature.getInstance() and catching the
                    // exception would be cleaner, but exceptions are a little
                    // expensive. So we check directly via getService().
                    try {
                        return Signature.getInstance(algorithm, "SunJSSE");
                    } catch (NoSuchProviderException e) {
                        throw new NoSuchAlgorithmException(e);
                    }
                }
            }
            return Signature.getInstance(algorithm, cryptoProvider);
        }
    }

    static KeyGenerator getKeyGenerator(String algorithm)
            throws NoSuchAlgorithmException {
        if (cryptoProvider == null) {
            return KeyGenerator.getInstance(algorithm);
        } else {
            return KeyGenerator.getInstance(algorithm, cryptoProvider);
        }
    }

    static KeyPairGenerator getKeyPairGenerator(String algorithm)
            throws NoSuchAlgorithmException {
        if (cryptoProvider == null) {
            return KeyPairGenerator.getInstance(algorithm);
        } else {
            return KeyPairGenerator.getInstance(algorithm, cryptoProvider);
        }
    }

    static KeyAgreement getKeyAgreement(String algorithm)
            throws NoSuchAlgorithmException {
        if (cryptoProvider == null) {
            return KeyAgreement.getInstance(algorithm);
        } else {
            return KeyAgreement.getInstance(algorithm, cryptoProvider);
        }
    }

    static Mac getMac(String algorithm)
            throws NoSuchAlgorithmException {
        if (cryptoProvider == null) {
            return Mac.getInstance(algorithm);
        } else {
            return Mac.getInstance(algorithm, cryptoProvider);
        }
    }

    static KeyFactory getKeyFactory(String algorithm)
            throws NoSuchAlgorithmException {
        if (cryptoProvider == null) {
            return KeyFactory.getInstance(algorithm);
        } else {
            return KeyFactory.getInstance(algorithm, cryptoProvider);
        }
    }

    static AlgorithmParameters getAlgorithmParameters(String algorithm)
            throws NoSuchAlgorithmException {
        if (cryptoProvider == null) {
            return AlgorithmParameters.getInstance(algorithm);
        } else {
            return AlgorithmParameters.getInstance(algorithm, cryptoProvider);
        }
    }

    static SecureRandom getSecureRandom() throws KeyManagementException {
        if (cryptoProvider == null) {
            return new SecureRandom();
        }
        // Try "PKCS11" first. If that is not supported, iterate through
        // the provider and return the first working implementation.
        try {
            return SecureRandom.getInstance("PKCS11", cryptoProvider);
        } catch (NoSuchAlgorithmException e) {
            // ignore
        }
        for (Provider.Service s : cryptoProvider.getServices()) {
            if (s.getType().equals("SecureRandom")) {
                try {
                    return SecureRandom.getInstance(
                            s.getAlgorithm(), cryptoProvider);
                } catch (NoSuchAlgorithmException ee) {
                    // ignore
                }
            }
        }
        throw new KeyManagementException("FIPS mode: no SecureRandom "
            + " implementation found in provider " + cryptoProvider.getName());
    }

    static MessageDigest getMD5() {
        return getMessageDigest("MD5");
    }

    static MessageDigest getSHA() {
        return getMessageDigest("SHA");
    }

    static MessageDigest getMessageDigest(String algorithm) {
        try {
            if (cryptoProvider == null) {
                return MessageDigest.getInstance(algorithm);
            } else {
                return MessageDigest.getInstance(algorithm, cryptoProvider);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException
                        ("Algorithm " + algorithm + " not available", e);
        }
    }

    static int getRSAKeyLength(PublicKey key) {
        BigInteger modulus;
        if (key instanceof RSAPublicKey) {
            modulus = ((RSAPublicKey)key).getModulus();
        } else {
            RSAPublicKeySpec spec = getRSAPublicKeySpec(key);
            modulus = spec.getModulus();
        }
        return modulus.bitLength();
    }

    static RSAPublicKeySpec getRSAPublicKeySpec(PublicKey key) {
        if (key instanceof RSAPublicKey) {
            RSAPublicKey rsaKey = (RSAPublicKey)key;
            return new RSAPublicKeySpec(rsaKey.getModulus(),
                                        rsaKey.getPublicExponent());
        }
        try {
            KeyFactory factory = JsseJce.getKeyFactory("RSA");
            return factory.getKeySpec(key, RSAPublicKeySpec.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static ECParameterSpec getECParameterSpec(String namedCurveOid) {
        return ECUtil.getECParameterSpec(cryptoProvider, namedCurveOid);
    }

    static String getNamedCurveOid(ECParameterSpec params) {
        return ECUtil.getCurveName(cryptoProvider, params);
    }

    static ECPoint decodePoint(byte[] encoded, EllipticCurve curve)
            throws java.io.IOException {
        return ECUtil.decodePoint(encoded, curve);
    }

    static byte[] encodePoint(ECPoint point, EllipticCurve curve) {
        return ECUtil.encodePoint(point, curve);
    }

    // In FIPS mode, set thread local providers; otherwise a no-op.
    // Must be paired with endFipsProvider.
    static Object beginFipsProvider() {
        if (fipsProviderList == null) {
            return null;
        } else {
            return Providers.beginThreadProviderList(fipsProviderList);
        }
    }

    static void endFipsProvider(Object o) {
        if (fipsProviderList != null) {
            Providers.endThreadProviderList((ProviderList)o);
        }
    }


    // lazy initialization holder class idiom for static default parameters
    //
    // See Effective Java Second Edition: Item 71.
    private static class EcAvailability {
        // Is EC crypto available?
        private static final boolean isAvailable;

        static {
            boolean mediator = true;
            try {
                JsseJce.getSignature(SIGNATURE_ECDSA);
                JsseJce.getSignature(SIGNATURE_RAWECDSA);
                JsseJce.getKeyAgreement("ECDH");
                JsseJce.getKeyFactory("EC");
                JsseJce.getKeyPairGenerator("EC");
                JsseJce.getAlgorithmParameters("EC");
            } catch (Exception e) {
                mediator = false;
            }

            isAvailable = mediator;
        }
    }
}
