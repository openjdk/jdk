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
import java.security.cert.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.Objects;

/**
 * {@code PEMDecoder} is an immutable class for Privacy-Enhanced Mail (PEM)
 * data.  PEM is a textual encoding used to store and transfer security
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRL).  It is defined in RFC 1421 and RFC 7468.  PEM consists of a
 * Base64-formatted binary encoding enclosed by a type-identifying header
 * and footer.
 *
 * <p> Decoding methods return an instance of a class that matches the data
 * type and implements {@link DEREncodable} unless specified. The following
 * types are decoded into Java Cryptographic Extensions (JCE) object:
 * <pre>
 *     PRIVATE KEY, RSA PRIVATE KEY, PUBLIC KEY, CERTIFICATE,
 *     X509 CERTIFICATE, X509 CRL, and ENCRYPTED PRIVATE KEY.
 * </pre>
 *
 * A specified return class must extend {@link DEREncodable} and be an
 * appropriate JCE object class for the PEM; otherwise an
 * {@link IllegalArgumentException} is thrown.
 *
 * <p> If the PEM does not have a corresponding JCE object, it returns a
 * {@link PEMRecord}. Any PEM can be decoded into a {@code PEMRecord} if the
 * class is specified. Decoding a {@code PEMRecord} returns corresponding
 * JCE object or throws a {@link IllegalArgumentException} if no object is
 * available.
 *
 * <p> A new immutable {@code PEMDecoder} instance is created by using
 * {@linkplain #withFactory} and/or {@linkplain #withDecryption}.  Configuring
 * an instance for decryption does not prevent decoding with unencrypted PEM.
 * Any encrypted PEM that does not use the configured password will throw an
 * {@link SecurityException}. A decoder instance not configured with decryption
 * returns an {@link EncryptedPrivateKeyInfo} with encrypted PEM.
 * EncryptedPrivateKeyInfo methods must be used to retrieve the
 * {@link PrivateKey}.
 *
 * <p> {@code String} values returned by this class use character set
 * {@link java.nio.charset.StandardCharsets#ISO_8859_1 ISO-8859-1}
 *
 * @apiNote
 * Here is an example of encoding a PrivateKey object:
 * <pre>
 *     PEMDecoder pd = PEMDecoder.of();
 *     PrivateKey priKey = pd.decode(PriKeyPEM);
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
                    KeyFactory kf = getKeyFactory(p8key.getAlgorithm());
                    DEREncodable d;

                    d = kf.generatePrivate(
                        new PKCS8EncodedKeySpec(p8key.getEncoded(),
                            p8key.getAlgorithm()));
                    // Check if this is a OneAsymmetricKey encoding, then check
                    // if this could be a SEC1-v2 EC encoding, otherwise return
                    // the private key
                    if (p8key.getPubKeyEncoded() != null) {
                        X509EncodedKeySpec spec = new X509EncodedKeySpec(
                            p8key.getPubKeyEncoded(), p8key.getAlgorithm());
                        yield new KeyPair(getKeyFactory(p8key.getAlgorithm()).
                            generatePublic(spec), (PrivateKey) d);
                    } else if (d instanceof PKCS8Key p8 &&
                        p8.getPubKeyEncoded() != null) {
                        X509EncodedKeySpec spec = new X509EncodedKeySpec(
                            p8.getPubKeyEncoded(), p8.getAlgorithm());
                        yield new KeyPair(getKeyFactory(p8.getAlgorithm()).
                            generatePublic(spec), p8);
                    } else {
                        yield d;
                    }
                }
                case Pem.ENCRYPTED_PRIVATE_KEY -> {
                    if (password == null) {
                        yield new EncryptedPrivateKeyInfo(decoder.decode(pem.pem()));
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
                        RSAPrivateCrtKeyImpl.getKeySpec(decoder.decode(pem.pem())));
                }
                default -> pem;
            };
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Decodes and returns {@link DEREncodable} from the given string.
     *
     * @param str PEM data in a String.
     * @return an {@code DEREncodable} generated from the PEM data.
     * @throws IllegalArgumentException on error in decoding or if the PEM is
     * unsupported.
     */
    public DEREncodable decode(String str) {
        Objects.requireNonNull(str);
        try {
            return decode(new ByteArrayInputStream(str.getBytes()));
        } catch (IOException e) {
            // With all data contained in the String, there are no IO ops.
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Decodes and returns a {@link DEREncodable} from the given
     * {@code InputStream}.
     *
     * <p>The method will read the {@code InputStream} until PEM data is
     * found or until the end of the stream.  Non-PEM data in the
     * {@code InputStream} before the PEM header will be ignored by the decoder.
     * If only non-PEM data is found a {@link PEMRecord} is returned with that
     * data.
     *
     * @param is InputStream containing PEM data.
     * @return an {@code DEREncodable} generated from the data read.
     * @throws IOException on IO error with the InputStream
     * @throws IllegalArgumentException on error in decoding or if the PEM is
     * unsupported.
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
     * @param <S> Class type parameter that extends {@code DEREncodable}
     * @param string the String containing PEM data.
     * @param tClass the returned object class that implementing
     * {@code DEREncodable}.
     * @return A {@code DEREncodable} typecast to {@code tClass}.
     * @throws IllegalArgumentException on error in decoding.
     * @throws ClassCastException if the given class is invalid for the PEM.
     */
    public <S extends DEREncodable> S decode(String string, Class<S> tClass) {
        Objects.requireNonNull(string);
        try {
            return decode(new ByteArrayInputStream(string.getBytes()), tClass);
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
     * @param <S> Class type parameter that extends {@code DEREncodable}
     * @param is an InputStream containing PEM data.
     * @param tClass the returned object class that implementing
     *   {@code DEREncodable}.
     * @return A {@code DEREncodable} typecast to {@code tClass}
     * @throws IOException on IO error with the InputStream.
     * @throws IllegalArgumentException on error in decoding.
     * @throws ClassCastException if the given class is invalid for the PEM.
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
        //if (PEMRecord.class.isInstance(tClass)) {
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
        } catch(GeneralSecurityException e){
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
     * <p>If {@code params} is {@code null}, a new instance is returned with
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
     * @throws NullPointerException if password is null.
     */
    public PEMDecoder withDecryption(char[] password) {
        return new PEMDecoder(factory, new PBEKeySpec(password));
    }
}
