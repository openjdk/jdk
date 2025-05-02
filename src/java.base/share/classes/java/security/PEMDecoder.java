/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import jdk.internal.javac.PreviewFeature;

import sun.security.pkcs.PKCS8Key;
import sun.security.rsa.RSAPrivateCrtKeyImpl;
import sun.security.util.Pem;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.Objects;

/**
 * {@code PEMDecoder} is an immutable class for decoding Privacy-Enhanced Mail
 * (PEM) data.  PEM is a textual encoding used to store and transfer security
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRLs).  It is defined in RFC 1421 and RFC 7468.  PEM consists of a
 * Base64-formatted binary encoding enclosed by a type-identifying header
 * and footer.
 *
 * <p> Decoding methods return an instance of a class that matches the data
 * type and implements {@link DEREncodable} unless otherwise specified. The
 * following types are decoded into Java Cryptographic Extensions (JCE) object
 * representations:
 * <pre>
 *     PRIVATE KEY, RSA PRIVATE KEY, PUBLIC KEY, CERTIFICATE,
 *     X509 CERTIFICATE, X509 CRL, and ENCRYPTED PRIVATE KEY.
 * </pre>
 *
 * A specified return class must implement {@link DEREncodable} and be an
 * appropriate JCE object class for the PEM; otherwise an
 * {@link IllegalArgumentException} is thrown.
 *
 * <p> If the PEM does not have a JCE object representation, it returns a
 * {@link PEMRecord}. Any PEM can be decoded into a {@code PEMRecord} if the
 * class is specified.
 *
 * <p> A new immutable {@code PEMDecoder} instance is created when configured
 * with {@linkplain #withFactory} and/or {@linkplain #withDecryption}.
 * Configuring an instance for decryption does not prevent decoding with
 * unencrypted PEM. Any encrypted PEM that does not use the configured password
 * will throw an {@link SecurityException}.  A decoder instance not configured
 * with decryption returns a {@link EncryptedPrivateKeyInfo} with encrypted
 * PEM.  {@code EncryptedPrivateKeyInfo} methods must be used to retrieve the
 * {@link PrivateKey}.
 *
 * <p> Byte streams consumed by methods in this class are assumed to represent
 * characters encoded in the
 * {@link java.nio.charset.StandardCharsets#ISO_8859_1 ISO-8859-1} charset.
 *
 * @apiNote
 * Here is an example of decoding a PrivateKey object:
 * <pre>
 *     PEMDecoder pd = PEMDecoder.of();
 *     PrivateKey priKey = pd.decode(priKeyPEM);
 * </pre>
 *
 * @spec https://www.rfc-editor.org/info/rfc1421
 *       RFC 1421: Privacy Enhancement for Internet Electronic Mail
 * @spec https://www.rfc-editor.org/info/rfc7468
 *       RFC 7468: Textual Encodings of PKIX, PKCS, and CMS Structures
 *
 * @since 25
 */

@PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
public final class PEMDecoder {
    private final Provider factory;
    private final PBEKeySpec password;

    // Singleton instance for PEMDecoder
    private final static PEMDecoder PEM_DECODER = new PEMDecoder(null, null);

    /**
     * Creates an immutable instance with a specific KeyFactory and/or
     * password.
     * @param withFactory KeyFactory provider
     * @param withPassword char[] password for EncryptedPrivateKeyInfo
     *                    decryption
     */
    private PEMDecoder(Provider withFactory, PBEKeySpec withPassword) {
        password = withPassword;
        factory = withFactory;
    }

    /**
     * Returns an instance of {@code PEMDecoder}.  This instance may be repeatedly used
     * to decode different PEM text.
     *
     * @return returns a {@code PEMDecoder}
     */
    public static PEMDecoder of() {
        return PEM_DECODER;
    }

    /**
     * After the header, footer, and base64 have been separated, identify the
     * header and footer and proceed with decoding the base64 for the
     * appropriate type.
     */
    private DEREncodable decode(PEMRecord pem) {
        Base64.Decoder decoder = Base64.getMimeDecoder();

        try {
            return switch (pem.type()) {
                case Pem.PUBLIC_KEY -> {
                    X509EncodedKeySpec spec =
                        new X509EncodedKeySpec(decoder.decode(pem.pem()));
                    yield (getKeyFactory(spec.getAlgorithm())).
                        generatePublic(spec);
                }
                case Pem.PRIVATE_KEY -> {
                    PKCS8Key p8key = new PKCS8Key(decoder.decode(pem.pem()));
                    String algo = p8key.getAlgorithm();
                    KeyFactory kf = getKeyFactory(algo);
                    DEREncodable d = kf.generatePrivate(
                        new PKCS8EncodedKeySpec(p8key.getEncoded(), algo));

                    // Look for a public key inside the pkcs8 encoding.
                    if (p8key.getPubKeyEncoded() != null) {
                        // Check if this is a OneAsymmetricKey encoding
                        X509EncodedKeySpec spec = new X509EncodedKeySpec(
                            p8key.getPubKeyEncoded(), algo);
                        yield new KeyPair(getKeyFactory(algo).
                            generatePublic(spec), (PrivateKey) d);

                    } else if (d instanceof PKCS8Key p8 &&
                        p8.getPubKeyEncoded() != null) {
                        // If the KeyFactory decoded an algorithm-specific
                        // encodings, look for the public key again.  This
                        // happens with EC and SEC1-v2 encoding
                        X509EncodedKeySpec spec = new X509EncodedKeySpec(
                            p8.getPubKeyEncoded(), algo);
                        yield new KeyPair(getKeyFactory(algo).
                            generatePublic(spec), p8);
                    } else {
                        // No public key, return the private key.
                        yield d;
                    }
                }
                case Pem.ENCRYPTED_PRIVATE_KEY -> {
                    if (password == null) {
                        yield new EncryptedPrivateKeyInfo(decoder.decode(
                            pem.pem()));
                    }
                    yield new EncryptedPrivateKeyInfo(decoder.decode(pem.pem())).
                        getKey(password.getPassword());
                }
                case Pem.CERTIFICATE, Pem.X509_CERTIFICATE -> {
                    CertificateFactory cf = getCertFactory("X509");
                    yield (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(decoder.decode(pem.pem())));
                }
                case Pem.X509_CRL -> {
                    CertificateFactory cf = getCertFactory("X509");
                    yield (X509CRL) cf.generateCRL(
                        new ByteArrayInputStream(decoder.decode(pem.pem())));
                }
                case Pem.RSA_PRIVATE_KEY -> {
                    KeyFactory kf = getKeyFactory("RSA");
                    yield kf.generatePrivate(
                        RSAPrivateCrtKeyImpl.getKeySpec(decoder.decode(
                            pem.pem())));
                }
                default -> pem;
            };
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Decodes and returns a {@link DEREncodable} from the given string.
     *
     * @param str a String containing PEM data.
     * @return a {@code DEREncodable} generated from the PEM data.
     * @throws IllegalArgumentException on error in decoding or if the PEM is
     * unsupported.
     * @throws NullPointerException when {@code str} is null.
     */
    public DEREncodable decode(String str) {
        Objects.requireNonNull(str);
        try {
            return decode(new ByteArrayInputStream(
                str.getBytes(StandardCharsets.ISO_8859_1)));
        } catch (IOException e) {
            // With all data contained in the String, there are no IO ops.
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Decodes and returns a {@link DEREncodable} from the given
     * {@code InputStream}.
     *
     * <p>This method will read the {@code InputStream} until PEM data is
     * found or until the end of the stream.  Non-PEM data in the
     * {@code InputStream} before the PEM header will be ignored by the decoder.
     * If only non-PEM data is found a {@link PEMRecord} is returned with that
     * data.
     *
     * @param is InputStream containing PEM data.
     * @return an {@code DEREncodable} generated from the data read.
     * @throws IOException on IO error with the InputStream.
     * @throws IllegalArgumentException on error in decoding or if the PEM is
     * unsupported.
     * @throws NullPointerException when {@code is} is null.
     */
    public DEREncodable decode(InputStream is) throws IOException {
        Objects.requireNonNull(is);
        PEMRecord pem = Pem.readPEM(is);
        return decode(pem);
    }

    /**
     * Decodes and returns the specified class for the given PEM string.
     * {@code tClass} must extend {@link DEREncodable} and be an appropriate
     * class for the PEM type.
     *
     * <p>
     * {@code tClass} can be used to change the return type instance:
     * <ul>
     * <li> Cast to a {@code DEREncodable} subclass, such
     * as an EC public key to a {@code ECPublicKey}.</li>
     * <li> Extract a key from a PEM with two keys, like taking only
     * {@code PrivateKey}</li>
     * <li> Convert to a different class, like storing the public key's
     * binary encoding in {@link X509EncodedKeySpec}.</li>
     * <li> Store the PEM a {@link PEMRecord}.</li>
     *</ul>
     * @param <S> Class type parameter that extends {@code DEREncodable}.
     * @param str the String containing PEM data.
     * @param tClass the returned object class that implements
     * {@code DEREncodable}.
     * @return a {@code DEREncodable} typecast to {@code tClass}.
     * @throws IllegalArgumentException on error in decoding.
     * @throws ClassCastException if the given class is invalid for the PEM.
     * @throws NullPointerException when any input values are null.
     */
    public <S extends DEREncodable> S decode(String str, Class<S> tClass) {
        Objects.requireNonNull(str);
        try {
            return decode(new ByteArrayInputStream(
                str.getBytes(StandardCharsets.ISO_8859_1)), tClass);
        } catch (IOException e) {
            // With all data contained in the String, there are no IO ops.
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Decodes and returns the specified class for the given
     * {@link InputStream}.  The class must extend {@link DEREncodable} and be
     * an appropriate class for the PEM type.
     *
     * @param <S> Class type parameter that extends {@code DEREncodable}.
     * @param is an InputStream containing PEM data.
     * @param tClass the returned object class that implements
     *   {@code DEREncodable}.
     * @return a {@code DEREncodable} typecast to {@code tClass}.
     * @throws IOException on IO error with the InputStream.
     * @throws IllegalArgumentException on error in decoding.
     * @throws ClassCastException if the given class is invalid for the PEM.
     * @throws NullPointerException when any input values are null.
     *
     * @see #decode(InputStream)
     * @see #decode(String, Class)
     */
    public <S extends DEREncodable> S decode(InputStream is, Class<S> tClass)
        throws IOException {
        Objects.requireNonNull(is);
        Objects.requireNonNull(tClass);
        PEMRecord pem = Pem.readPEM(is);

        if (tClass.isAssignableFrom(PEMRecord.class)) {
            return tClass.cast(pem);
        }
        DEREncodable so = decode(pem);

        /*
         * If the object is a KeyPair, check if the tClass is set to class
         * specific to a private or public key.  Because PKCS8v2 can be a
         * KeyPair, it is possible for someone to assume all their PEM private
         * keys are only PrivateKey and not KeyPair.
         */
        if (so instanceof KeyPair kp) {
            if ((PrivateKey.class).isAssignableFrom(tClass) ||
                (PKCS8EncodedKeySpec.class).isAssignableFrom(tClass)) {
                so = kp.getPrivate();
            }
            if ((PublicKey.class).isAssignableFrom(tClass) ||
                (X509EncodedKeySpec.class).isAssignableFrom(tClass)) {
                so = kp.getPublic();
            }
        }

        /*
         * KeySpec use getKeySpec after the Key has been generated.  Even though
         * returning a binary encoding after the Base64 decoding is ok when the
         * user wants PKCS8EncodedKeySpec, generating the key verifies the
         * binary encoding and allows the KeyFactory to use the provider's
         * KeySpec()
         */

        if ((EncodedKeySpec.class).isAssignableFrom(tClass) &&
            so instanceof Key key) {
            try {
                // unchecked suppressed as we know tClass comes from KeySpec
                // KeyType not relevant here.  We just want KeyFactory
                if ((PKCS8EncodedKeySpec.class).isAssignableFrom(tClass)) {
                    so = getKeyFactory(key.getAlgorithm()).
                        getKeySpec(key, PKCS8EncodedKeySpec.class);
                } else if ((X509EncodedKeySpec.class).isAssignableFrom(tClass)) {
                    so = getKeyFactory(key.getAlgorithm())
                        .getKeySpec(key, X509EncodedKeySpec.class);
                } else {
                    throw new IllegalArgumentException("Invalid KeySpec.");
                }
            } catch (InvalidKeySpecException e) {
                throw new IllegalArgumentException("Invalid KeySpec " +
                    "specified (" + tClass.getName() +") for key (" +
                    key.getClass().getName() +")", e);
            }
        }

        return tClass.cast(so);
    }

    private KeyFactory getKeyFactory(String algorithm) {
        try {
            if (factory == null) {
                return KeyFactory.getInstance(algorithm);
            }
            return KeyFactory.getInstance(algorithm, factory);
        } catch(GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // Convenience method to avoid provider getInstance checks clutter
    private CertificateFactory getCertFactory(String algorithm) {
        try {
            if (factory == null) {
                return CertificateFactory.getInstance(algorithm);
            }
            return CertificateFactory.getInstance(algorithm, factory);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Configures and returns a new {@code PEMDecoder} instance from the
     * current instance that will use KeyFactory and CertificateFactory classes
     * from the specified {@link Provider}.  Any errors using the
     * {@code provider} will occur during decoding.
     *
     * <p>If {@code provider} is {@code null}, a new instance is returned with
     * the default provider configuration.
     *
     * @param provider the Factory provider.
     * @return a new PEM decoder instance.
     */
    public PEMDecoder withFactory(Provider provider) {
        return new PEMDecoder(provider, password);
    }

    /**
     * Returns a new {@code PEMDecoder} instance from the current instance
     * configured to decrypt encrypted PEM data with given password.
     * Non-encrypted PEM may still be decoded from this instance.
     *
     * @param password the password to decrypt encrypted PEM data.  This array
     *                 is cloned and stored in the new instance.
     * @return a new PEM decoder instance.
     * @throws NullPointerException if {@code password} is null.
     */
    public PEMDecoder withDecryption(char[] password) {
        return new PEMDecoder(factory, new PBEKeySpec(password));
    }
}
