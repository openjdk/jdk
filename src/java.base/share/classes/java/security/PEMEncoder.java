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
import sun.security.util.KeyUtil;
import sun.security.util.Pem;

import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

/**
 * {@code PEMEncoder} implements an encoder for Privacy-Enhanced Mail (PEM)
 * data.  PEM is a textual encoding used to store and transfer cryptographic
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRLs).  It is defined in RFC 1421 and RFC 7468.  PEM consists of a
 * Base64-encoded binary encoding enclosed by a type-identifying header
 * and footer.
 *
 * <p> Encoding can be performed on cryptographic objects that
 * implement {@link DEREncodable}. The {@link #encode(DEREncodable)}
 * and {@link #encodeToString(DEREncodable)} methods encode a {@code DEREncodable}
 * into PEM and return the data in a byte array or {@code String}.
 *
 * <p> Private keys can be encrypted and encoded by configuring a
 * {@code PEMEncoder} with the {@link #withEncryption(char[])} method,
 * which takes a password and returns a new {@code PEMEncoder} instance
 * configured to encrypt the key with that password. Alternatively, a
 * private key encrypted as an {@link EncryptedPrivateKeyInfo} object can be encoded
 * directly to PEM by passing it to the {@code encode} or
 * {@code encodeToString} methods.
 *
 * <p> PKCS #8 v2.0 defines the ASN.1 OneAsymmetricKey structure, which may
 * contain both private and public keys.
 * {@code KeyPair} objects passed to the {@code encode} or
 * {@code encodeToString} methods are encoded as a
 * OneAsymmetricKey structure using the "PRIVATE KEY" type.
 *
 * <p> When encoding a {@link PEM} object, the API surrounds
 * {@link PEM#content()} with a PEM header and footer based on
 * {@link PEM#type()}. The value returned by {@link PEM#leadingData()} is not
 * included in the output.
 *
 * <p> The following lists the supported {@code DEREncodable} classes and
 * the PEM types they encode as:
 * <ul>
 *   <li>{@link X509Certificate} : CERTIFICATE</li>
 *   <li>{@link X509CRL} : X509 CRL</li>
 *   <li>{@link PublicKey} : PUBLIC KEY</li>
 *   <li>{@link PrivateKey} : PRIVATE KEY</li>
 *   <li>{@link EncryptedPrivateKeyInfo} : ENCRYPTED PRIVATE KEY</li>
 *   <li>{@link KeyPair} : PRIVATE KEY</li>
 *   <li>{@link X509EncodedKeySpec} : PUBLIC KEY</li>
 *   <li>{@link PKCS8EncodedKeySpec} : PRIVATE KEY</li>
 *   <li>{@link PEM} : {@code PEM.type()}</li>
 * </ul>
 * <p> When used with a {@code PEMEncoder} instance configured for encryption:
 * <ul>
 *   <li>{@link PrivateKey} : ENCRYPTED PRIVATE KEY</li>
 *   <li>{@link KeyPair} : ENCRYPTED PRIVATE KEY</li>
 *   <li>{@link PKCS8EncodedKeySpec} : ENCRYPTED PRIVATE KEY</li>
 * </ul>
 *
 * <p> This class is immutable and thread-safe.
 *
 * <p> Example: encode a private key:
 * {@snippet lang = java:
 *     PEMEncoder pe = PEMEncoder.of();
 *     byte[] pemData = pe.encode(privKey);
 * }
 *
 * <p> Example: encrypt and encode a private key using a password:
 * {@snippet lang = java:
 *     PEMEncoder pe = PEMEncoder.of().withEncryption(password);
 *     byte[] pemData = pe.encode(privKey);
 * }
 *
 * @implNote Implementations may support additional PEM types.
 *
 *
 * @see PEMDecoder
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
public final class PEMEncoder {

    // Singleton instance of PEMEncoder
    private static final PEMEncoder PEM_ENCODER = new PEMEncoder(null);
    // PBE key for encryption
    private final Key key;

    /**
     * Create an encrypted {@code PEMEncoder} instance.
     */
    private PEMEncoder(PBEKeySpec keySpec) {
        if (keySpec != null) {
            try {
                key = SecretKeyFactory.getInstance(Pem.DEFAULT_ALGO).
                    generateSecret(keySpec);
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("Operation failed: " +
                    "unable to generate key or locate a valid algorithm. " +
                    "Check the jdk.epkcs8.defaultAlgorithm security " +
                    "property for a valid configuration.", e);
            }
        } else {
            key = null;
        }
    }

    /**
     * Returns an instance of {@code PEMEncoder}.
     *
     * @return a {@code PEMEncoder}
     */
    public static PEMEncoder of() {
        return PEM_ENCODER;
    }

    /**
     * Encodes the specified {@code DEREncodable} and returns a PEM-encoded
     * string.
     *
     * @param de the {@code DEREncodable} to be encoded
     * @return a {@code String} containing the PEM-encoded data
     * @throws IllegalArgumentException if the {@code DEREncodable} cannot be encoded
     * @throws NullPointerException if {@code de} is {@code null}
     * @see #withEncryption(char[])
     */
    public String encodeToString(DEREncodable de) {
        Objects.requireNonNull(de);
        return switch (de) {
            case PublicKey pu -> buildKey(pu.getEncoded(), null);
            case PrivateKey pr -> {
                byte[] encoding = pr.getEncoded();
                try {
                    yield buildKey(null, encoding);
                } finally {
                    KeyUtil.clear(encoding);
                }
            }
            case KeyPair kp -> {
                byte[] encoding = null;
                try {
                    if (kp.getPublic() == null) {
                        throw new IllegalArgumentException("KeyPair does not " +
                            "contain PublicKey.");
                    }
                    if (kp.getPrivate() == null) {
                        throw new IllegalArgumentException("KeyPair does not " +
                            "contain PrivateKey.");
                    }
                    encoding = kp.getPrivate().getEncoded();
                    if (encoding == null || encoding.length == 0) {
                        throw new IllegalArgumentException("PrivateKey is " +
                            "null or has no encoding.");
                    }
                    yield buildKey(kp.getPublic().getEncoded(), encoding);
                } finally {
                    KeyUtil.clear(encoding);
                }
            }
            case X509EncodedKeySpec x -> buildKey(x.getEncoded(), null);
            case PKCS8EncodedKeySpec p -> buildKey(null, p.getEncoded());
            case EncryptedPrivateKeyInfo epki -> {
                byte[] encoding = null;
                if (key != null) {
                    throw new IllegalArgumentException(
                        "EncryptedPrivateKeyInfo cannot be encrypted");
                }
                try {
                    encoding = epki.getEncoded();
                    yield Pem.pemEncoded(Pem.ENCRYPTED_PRIVATE_KEY, encoding);
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                } finally {
                    KeyUtil.clear(encoding);
                }
            }
            case X509Certificate c -> {
                if (key != null) {
                    throw new IllegalArgumentException("Certificates " +
                        "cannot be encrypted");
                }
                try {
                    yield Pem.pemEncoded(Pem.CERTIFICATE, c.getEncoded());
                } catch (CertificateEncodingException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            case X509CRL crl -> {
                if (key != null) {
                    throw new IllegalArgumentException("CRLs cannot be " +
                        "encrypted");
                }
                try {
                    yield Pem.pemEncoded(Pem.X509_CRL, crl.getEncoded());
                } catch (CRLException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            case PEM rec -> {
                if (key != null) {
                    throw new IllegalArgumentException("PEM cannot be " +
                        "encrypted");
                }
                yield Pem.pemEncoded(rec);
            }

            default -> throw new IllegalArgumentException("PEM does not " +
                "support " + de.getClass().getCanonicalName());
        };
    }

    /**
     * Encodes the specified {@code DEREncodable} and returns a PEM-encoded
     * byte array.
     *
     * @param de the {@code DEREncodable} to be encoded
     * @return a PEM-encoded byte array
     * @throws IllegalArgumentException if the {@code DEREncodable} cannot be encoded
     * @throws NullPointerException if {@code de} is {@code null}
     * @see #withEncryption(char[])
     */
    public byte[] encode(DEREncodable de) {
        return encodeToString(de).getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Returns a copy of this PEMEncoder that encrypts and encodes
     * using the specified password and default encryption algorithm.
     *
     * <p> Only {@code PrivateKey}, {@code KeyPair}, and
     * {@code PKCS8EncodedKeySpec} objects can be encoded with this newly
     * configured instance.  Encoding other {@code DEREncodable} objects will
     * throw an {@code IllegalArgumentException}.
     *
     * @implNote The {@code jdk.epkcs8.defaultAlgorithm} security property
     * defines the default encryption algorithm. The {@code AlgorithmParameterSpec}
     * defaults are determined by the provider. To use non-default encryption
     * parameters, or to encrypt with a different encryption provider, use
     * {@link EncryptedPrivateKeyInfo#encrypt(DEREncodable, Key,
     * String, AlgorithmParameterSpec, Provider, SecureRandom)} and use the
     * returned object with {@link #encode(DEREncodable)}.
     *
     * @param password the encryption password.  The array is cloned and
     *                 stored in the new instance.
     * @return a new {@code PEMEncoder} instance configured for encryption
     * @throws NullPointerException if password is {@code null}
     * @throws IllegalArgumentException if generating the encryption key fails
     */
    public PEMEncoder withEncryption(char[] password) {
        Objects.requireNonNull(password, "password cannot be null.");
        PBEKeySpec keySpec = new PBEKeySpec(password);
        try {
            return new PEMEncoder(keySpec);
        } finally {
            keySpec.clearPassword();
        }
    }

    /**
     * Build PEM encoding.
     *
     * privateKeyEncoding will be zeroed when the method returns
     */
    private String buildKey(byte[] publicEncoding, byte[] privateEncoding) {
        if (publicEncoding == null && privateEncoding == null) {
            throw new IllegalArgumentException("No encoded data given by the " +
                "DEREncodable.");
        }

        if (publicEncoding != null && publicEncoding.length == 0) {
            throw new IllegalArgumentException("Public key has no " +
                "encoding");
        }

        if (privateEncoding != null && privateEncoding.length == 0) {
            throw new IllegalArgumentException("Private key has no " +
                "encoding");
        }

        if (key != null && privateEncoding == null) {
            throw new IllegalArgumentException("This DEREncodable cannot " +
                "be encrypted.");
        }

        // X509 only
        if (publicEncoding != null && privateEncoding == null) {
            return Pem.pemEncoded(Pem.PUBLIC_KEY, publicEncoding);
        }

        byte[] encoding = null;
        PKCS8EncodedKeySpec p8KeySpec = null;
        try {
            if (publicEncoding == null) {
                encoding = privateEncoding;
            } else {
                encoding = PKCS8Key.getEncoded(publicEncoding,
                    privateEncoding);
            }
            if (key != null) {
                p8KeySpec = new PKCS8EncodedKeySpec(encoding);
                encoding = EncryptedPrivateKeyInfo.encrypt(p8KeySpec, key,
                    Pem.DEFAULT_ALGO, null, null, null).
                    getEncoded();
            }
            if (encoding.length == 0) {
                throw new IllegalArgumentException("No private key encoding " +
                    "given by the DEREncodable.");
            }
            return Pem.pemEncoded(
                (key == null ? Pem.PRIVATE_KEY : Pem.ENCRYPTED_PRIVATE_KEY),
                encoding);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error while encoding", e);
        } finally {
            KeyUtil.clear(encoding, p8KeySpec);
        }
    }
}
