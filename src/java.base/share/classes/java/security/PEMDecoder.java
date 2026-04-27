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

import jdk.internal.ref.CleanerFactory;
import sun.security.pkcs.PKCS8Key;
import sun.security.rsa.RSAPrivateCrtKeyImpl;
import sun.security.util.KeyUtil;
import sun.security.util.Pem;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.lang.ref.Reference;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.Objects;

/**
 * {@code PEMDecoder} implements a decoder for Privacy-Enhanced Mail (PEM) data.
 * PEM is a textual encoding used to store and transfer cryptographic
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRLs).  It is defined in RFC 1421 and RFC 7468. PEM consists of a
 * Base64-encoded binary encoding enclosed by a type-identifying header
 * and footer.
 *
 * <p>The {@link #decode(String)} and {@link #decode(InputStream)} methods
 * return an instance of a class that matches the PEM type and implements
 * {@link DEREncodable}, as follows:
 * <ul>
 *   <li>CERTIFICATE : {@link X509Certificate}</li>
 *   <li>X509 CRL : {@link X509CRL}</li>
 *   <li>PUBLIC KEY : {@link PublicKey}</li>
 *   <li>PRIVATE KEY : {@link PrivateKey} or {@link KeyPair}
 *   (if the encoding contains a public key)</li>
 *   <li>ENCRYPTED PRIVATE KEY : {@link EncryptedPrivateKeyInfo}</li>
 *   <li>Other types : {@link PEM}</li>
 * </ul>
 * When used with a {@code PEMDecoder} instance configured for decryption:
 * <ul>
 *   <li>ENCRYPTED PRIVATE KEY : {@link PrivateKey} or {@link KeyPair}
 *   (if the encoding contains a public key)</li>
 * </ul>
 *
 * <p> For {@code PublicKey} and {@code PrivateKey} types, an algorithm-specific
 * subclass is returned if the algorithm is supported. For example, an
 * {@code ECPublicKey} or an {@code ECPrivateKey} for Elliptic Curve keys.
 *
 * <p> If the PEM type does not have a corresponding class,
 * {@code decode(String)} and {@code decode(InputStream)} will return a
 * {@code PEM} object.
 *
 * <p> The {@link #decode(String, Class)} and {@link #decode(InputStream, Class)}
 * methods take a class parameter that specifies the type of {@code DEREncodable}
 * to return. These methods are useful for avoiding casts when the PEM type is
 * known, or when extracting a specific type if there is more than one option.
 * For example, if the PEM contains both a public and private key, specifying
 * {@code PrivateKey.class} returns only the private key.
 * If the class parameter specifies {@code X509EncodedKeySpec.class}, the
 * public key encoding is returned as an instance of {@code X509EncodedKeySpec}
 * class. Any type of PEM data can be decoded into a {@code PEM} object by
 * specifying {@code PEM.class}. If the class parameter does not match the PEM
 * content, a {@code ClassCastException} is thrown.
 *
 * <p> In addition to the types listed above, these methods support the
 * following PEM types and {@code DEREncodable} classes when specified as
 * parameters:
 *  <ul>
 *   <li>PUBLIC KEY : {@link X509EncodedKeySpec}</li>
 *   <li>PRIVATE KEY : {@link PKCS8EncodedKeySpec}</li>
 *   <li>PRIVATE KEY : {@link PublicKey} (if the encoding contains a public key)</li>
 *   <li>PRIVATE KEY : {@link X509EncodedKeySpec} (if the encoding contains a public key)</li>
 * </ul>
 * When used with a {@code PEMDecoder} instance configured for decryption:
 * <ul>
 *   <li>ENCRYPTED PRIVATE KEY : {@link PKCS8EncodedKeySpec}</li>
 *   <li>ENCRYPTED PRIVATE KEY : {@link PublicKey} (if the encoding contains a public key)</li>
 *   <li>ENCRYPTED PRIVATE KEY : {@link X509EncodedKeySpec} (if the encoding contains a public key)</li>
 * </ul>
 *
 * <p> A new {@code PEMDecoder} instance is created when configured
 * with {@link #withFactory(Provider)} or {@link #withDecryption(char[])}.
 * The {@link #withFactory(Provider)} method uses the specified provider
 * to produce cryptographic objects from {@link KeyFactory} and
 * {@link CertificateFactory}. The {@link #withDecryption(char[])} method configures the
 * decoder to decrypt and decode encrypted private key PEM data using the given
 * password.  If decryption fails, an {@link IllegalArgumentException} is thrown.
 * If an encrypted private key PEM is processed by a decoder not configured
 * for decryption, an {@link EncryptedPrivateKeyInfo} object is returned.
 * A {@code PEMDecoder} configured for decryption will decode unencrypted PEM.
 *
 * <p> This class is immutable and thread-safe.
 *
 * <p> Example: decode a private key:
 * {@snippet lang = java:
 *     PEMDecoder pd = PEMDecoder.of();
 *     PrivateKey priKey = pd.decode(priKeyPEM, PrivateKey.class);
 * }
 *
 * <p> Example: configure decryption and a factory provider:
 * {@snippet lang = java:
 *     PEMDecoder pd = PEMDecoder.of().withDecryption(password).
 *             withFactory(provider);
 *     DEREncodable pemData = pd.decode(privKeyPEM);
 * }
 *
 * @implNote This implementation decodes RSA PRIVATE KEY as {@code PrivateKey},
 * X509 CERTIFICATE and X.509 CERTIFICATE as {@code X509Certificate},
 * and CRL as {@code X509CRL}. Other implementations may recognize
 * additional PEM types.
 *
 * @see PEMEncoder
 * @see PEM
 * @see EncryptedPrivateKeyInfo
 *
 * @spec https://www.rfc-editor.org/info/rfc1421
 *       RFC 1421: Privacy Enhancement for Internet Electronic Mail
 * @spec https://www.rfc-editor.org/info/rfc5958
 *       RFC 5958: Asymmetric Key Packages
 * @spec https://www.rfc-editor.org/info/rfc7468
 *       RFC 7468: Textual Encodings of PKIX, PKCS, and CMS Structures
 *
 * @since 25
 */

@PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
public final class PEMDecoder {
    private final Provider factory;
    private final PBEKeySpec keySpec;

    // Singleton instance for PEMDecoder
    private final static PEMDecoder PEM_DECODER = new PEMDecoder(null, null);

    /**
     * Creates an instance with a specific KeyFactory and/or password.
     * @param withFactory KeyFactory provider
     * @param withPassword char[] password for EncryptedPrivateKeyInfo
     *                    decryption
     */
    private PEMDecoder(Provider withFactory, PBEKeySpec withPassword) {
        keySpec = withPassword;
        factory = withFactory;
        if (withPassword != null) {
            final var k = this.keySpec;
            CleanerFactory.cleaner().register(this, k::clearPassword);
        }
    }

    /**
     * Returns an instance of {@code PEMDecoder}.
     *
     * @return a {@code PEMDecoder} instance
     */
    public static PEMDecoder of() {
        return PEM_DECODER;
    }

    /**
     * After the header, footer, and base64 have been separated, identify the
     * header and footer and proceed with decoding the base64 for the
     * appropriate type.
     */
    private DEREncodable decode(PEM pem) {
        Base64.Decoder decoder = Base64.getMimeDecoder();

        try {
            return switch (pem.type()) {
                case Pem.PUBLIC_KEY -> {
                    X509EncodedKeySpec spec =
                        new X509EncodedKeySpec(decoder.decode(pem.content()));
                    yield getKeyFactory(
                        KeyUtil.getAlgorithm(spec.getEncoded())).
                        generatePublic(spec);
                }
                case Pem.PRIVATE_KEY -> {
                    DEREncodable d;
                    PKCS8Key p8key = null;
                    PKCS8EncodedKeySpec p8spec = null;
                    byte[] encoding = decoder.decode(pem.content());

                    try {
                        p8key = new PKCS8Key(encoding);
                        String algo = p8key.getAlgorithm();
                        KeyFactory kf = getKeyFactory(algo);
                        p8spec = new PKCS8EncodedKeySpec(encoding, algo);
                        d = kf.generatePrivate(p8spec);

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
                            // encodings, look for the public key again.
                            X509EncodedKeySpec spec = new X509EncodedKeySpec(
                                p8.getPubKeyEncoded(), algo);
                            yield new KeyPair(getKeyFactory(algo).
                                generatePublic(spec), (PrivateKey) d);
                        } else {
                            // No public key, return the private key.
                            yield d;
                        }
                    } finally {
                        KeyUtil.clear(encoding, p8spec, p8key);
                    }
                }
                case Pem.ENCRYPTED_PRIVATE_KEY -> {
                    byte[] p8 = null;
                    byte[] encoding = null;
                    try {
                        encoding = decoder.decode(pem.content());
                        var ekpi = new EncryptedPrivateKeyInfo(encoding);
                        if (keySpec == null) {
                            yield ekpi;
                        }
                        p8 = Pem.decryptEncoding(ekpi, keySpec);
                        yield Pem.toDEREncodable(p8, true, factory);
                    } finally {
                        Reference.reachabilityFence(this);
                        KeyUtil.clear(encoding, p8);
                    }
                }
                case Pem.CERTIFICATE, Pem.X509_CERTIFICATE,
                     Pem.X_509_CERTIFICATE -> {
                    CertificateFactory cf = getCertFactory("X509");
                    yield (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(decoder.decode(pem.content())));
                }
                case Pem.X509_CRL, Pem.CRL -> {
                    CertificateFactory cf = getCertFactory("X509");
                    yield (X509CRL) cf.generateCRL(
                        new ByteArrayInputStream(decoder.decode(pem.content())));
                }
                case Pem.RSA_PRIVATE_KEY -> {
                    KeyFactory kf = getKeyFactory("RSA");
                    yield kf.generatePrivate(
                        RSAPrivateCrtKeyImpl.getKeySpec(decoder.decode(
                            pem.content())));
                }
                default -> pem;
            };
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Decodes and returns a {@code DEREncodable} from the given {@code String}.
     *
     * <p> This method reads the {@code String} until PEM data is found
     * or the end of the {@code String} is reached.  If no PEM data is found,
     * an {@code IllegalArgumentException} is thrown.
     *
     * <p> A {@code DEREncodable} will be returned that best represents the
     * decoded data.  If the PEM type is not supported, a {@code PEM} object is
     * returned containing the type identifier, Base64-encoded data, and any
     * leading data preceding the PEM header. For {@code DEREncodable} types
     * other than {@code PEM}, leading data is ignored and not returned as part
     * of the {@code DEREncodable} object.
     *
     * <p> Input consumed by this method is read in as
     * {@link java.nio.charset.StandardCharsets#UTF_8 UTF-8}.
     *
     * @param str a {@code String} containing PEM data
     * @return a {@code DEREncodable}
     * @throws IllegalArgumentException on error in decoding or no PEM data found
     * @throws NullPointerException when {@code str} is {@code null}
     */
    public DEREncodable decode(String str) {
        Objects.requireNonNull(str);
        try {
            return decode(new ByteArrayInputStream(
                str.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            // With all data contained in the String, there are no IO ops.
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Decodes and returns a {@code DEREncodable} from the given
     * {@code InputStream}.
     *
     * <p> This method reads from the {@code InputStream} until the end of
     * a PEM footer or the end of the stream. If an I/O error occurs,
     * the read position in the stream may become inconsistent.
     * It is recommended to perform no further decoding operations
     * on the {@code InputStream}.
     *
     * <p> A {@code DEREncodable} will be returned that best represents the
     * decoded data.  If the PEM type is not supported, a {@code PEM} object is
     * returned containing the type identifier, Base64-encoded data, and any
     * leading data preceding the PEM header. For {@code DEREncodable} types
     * other than {@code PEM}, leading data is ignored and not returned as part
     * of the {@code DEREncodable} object.
     *
     * <p> If no PEM data is found, an {@code EOFException} is thrown.
     *
     * @param is {@code InputStream} containing PEM data
     * @return a {@code DEREncodable}
     * @throws IOException on IO or PEM syntax error where the
     * {@code InputStream} did not complete decoding
     * @throws EOFException no PEM data found or unexpectedly reached the
     *   end of the {@code InputStream}
     * @throws IllegalArgumentException on error in decoding
     * @throws NullPointerException when {@code is} is {@code null}
     */
    public DEREncodable decode(InputStream is) throws IOException {
        Objects.requireNonNull(is);
        PEM pem = Pem.readPEM(is);
        return decode(pem);
    }

    /**
     * Decodes and returns a {@code DEREncodable} of the specified class from
     * the given PEM string. {@code tClass} must be an appropriate class for
     * the PEM type.
     *
     * <p> This method reads the {@code String} until PEM data is found
     * or the end of the {@code String} is reached.  If no PEM data is found,
     * an {@code IllegalArgumentException} is thrown.
     *
     * <p> If the class parameter is {@code PEM.class}, a {@code PEM} object is
     * returned containing the type identifier, Base64-encoded data, and any
     * leading data preceding the PEM header. For {@code DEREncodable} types
     * other than {@code PEM}, leading data is ignored and not returned as part
     * of the {@code DEREncodable} object.
     *
     * <p> Input consumed by this method is read in as
     * {@link java.nio.charset.StandardCharsets#UTF_8 UTF-8}.
     *
     * @param <S> class type parameter that extends {@code DEREncodable}
     * @param str the {@code String} containing PEM data
     * @param tClass the returned object class that extends or implements
     *   {@code DEREncodable}
     * @return a {@code DEREncodable} specified by {@code tClass}
     * @throws IllegalArgumentException on error in decoding or no PEM data found
     * @throws ClassCastException if {@code tClass} does not represent the PEM type
     * @throws NullPointerException when any input values are {@code null}
     */
    public <S extends DEREncodable> S decode(String str, Class<S> tClass) {
        Objects.requireNonNull(str);
        try {
            return decode(new ByteArrayInputStream(
                str.getBytes(StandardCharsets.UTF_8)), tClass);
        } catch (IOException e) {
            // With all data contained in the String, there are no IO ops.
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Decodes and returns a {@code DEREncodable} of the specified class for the
     * given {@code InputStream}. {@code tClass} must be an appropriate class
     * for the PEM type.
     *
     * <p> This method reads from the {@code InputStream} until the end of
     * a PEM footer or the end of the stream. If an I/O error occurs,
     * the read position in the stream may become inconsistent.
     * It is recommended to perform no further decoding operations
     * on the {@code InputStream}.
     *
     * <p> If the class parameter is {@code PEM.class}, a {@code PEM} object is
     * returned containing the type identifier, Base64-encoded data, and any
     * leading data preceding the PEM header. For {@code DEREncodable} types
     * other than {@code PEM}, leading data is ignored and not returned as part
     * of the {@code DEREncodable} object.
     *
     * <p> If no PEM data is found, an {@code EOFException} is thrown.
     *
     * @param <S> class type parameter that extends {@code DEREncodable}
     * @param is an {@code InputStream} containing PEM data
     * @param tClass the returned object class that extends or implements
     *   {@code DEREncodable}
     * @return a {@code DEREncodable} typecast to {@code tClass}
     * @throws IOException on IO or PEM syntax error where the
     *   {@code InputStream} did not complete decoding
     * @throws EOFException no PEM data found or unexpectedly reached the
     *   end of the {@code InputStream}
     * @throws IllegalArgumentException on error in decoding
     * @throws ClassCastException if {@code tClass} does not represent the PEM type
     * @throws NullPointerException when any input values are {@code null}
     *
    * @see #decode(InputStream)
     * @see #decode(String, Class)
     */
    public <S extends DEREncodable> S decode(InputStream is, Class<S> tClass)
        throws IOException {
        Objects.requireNonNull(is);
        Objects.requireNonNull(tClass);
        PEM pem = Pem.readPEM(is);

        if (tClass.isAssignableFrom(PEM.class)) {
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
                    throw new ClassCastException("Invalid KeySpec");
                }
            } catch (InvalidKeySpecException e) {
                throw new ClassCastException("Invalid KeySpec " +
                    "specified: " + tClass.getName() + " for key " +
                    key.getClass().getName());
            }
        }

        return tClass.cast(so);
    }

    private KeyFactory getKeyFactory(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            throw new IllegalArgumentException("No algorithm found in " +
                "the encoding");
        }
        try {
            if (factory == null) {
                return KeyFactory.getInstance(algorithm);
            }
            return KeyFactory.getInstance(algorithm, factory);
        } catch (GeneralSecurityException e) {
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
     * Returns a copy of this {@code PEMDecoder} instance that uses
     * {@code KeyFactory} and {@code CertificateFactory} implementations
     * from the specified {@code Provider} to produce cryptographic objects.
     * Any errors using the {@code Provider} will occur during decoding.
     *
     * @param provider the factory provider
     * @return a new {@code PEMDecoder} instance configured with the {@code Provider}
     * @throws NullPointerException if {@code provider} is {@code null}
     */
    public PEMDecoder withFactory(Provider provider) {
        Objects.requireNonNull(provider);
        return new PEMDecoder(provider, keySpec);
    }

    /**
     * Returns a copy of this {@code PEMDecoder} that decodes and decrypts
     * encrypted private keys using the specified password.
     * Non-encrypted PEM can also be decoded from this instance.
     *
     * @param password the password to decrypt the encrypted PEM data. This array
     *                 is cloned and stored in the new instance.
     * @return a new {@code PEMDecoder} instance configured for decryption
     * @throws NullPointerException if {@code password} is {@code null}
     */
    public PEMDecoder withDecryption(char[] password) {
        Objects.requireNonNull(password);
        return new PEMDecoder(factory, new PBEKeySpec(password));
    }
}
