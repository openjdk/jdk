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
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.Pem;
import sun.security.x509.AlgorithmId;

import javax.crypto.*;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@code PEMEncoder} implements an encoder for Privacy-Enhanced Mail (PEM)
 * data.  PEM is a textual encoding used to store and transfer security
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRL).  It is defined in RFC 1421 and RFC 7468.  PEM consists of a
 * Base64-formatted binary encoding enclosed by a type-identifying header
 * and footer.
 *
 * <p> Encoding may be performed on Java API cryptographic objects that
 * implement {@link DEREncodable}. The {@link #encode(DEREncodable)}
 * and {@link #encodeToString(DEREncodable)} methods encode a DEREncodable
 * into PEM and return the data in a byte array or String.
 *
 * <p> Private keys can be encrypted and encoded by configuring a
 * {@code PEMEncoder} with the {@linkplain #withEncryption(char[])} method,
 * which takes a password and returns a new {@code PEMEncoder} instance
 * configured to encrypt the key with that password. Alternatively, a
 * private key encrypted as an {@code EncryptedKeyInfo} object can be encoded
 * directly to PEM by passing it to the {@code encode} or
 * {@code encodeToString} methods.
 *
 * <p> PKCS #8 2.0 defines the ASN.1 OneAsymmetricKey structure, which may
 * contain both private and public keys.
 * {@link KeyPair} objects passed to the {@code encode} or
 * {@code encodeToString} methods are encoded as a
 * OneAsymmetricKey structure using the "PRIVATE KEY" type.
 *
 * <p> When encoding a {@link PEMRecord}, the API surrounds the
 * {@linkplain PEMRecord#content()} with the PEM header and footer
 * from {@linkplain PEMRecord#type()}. {@linkplain PEMRecord#leadingData()} is
 * not included in the encoding.  {@code PEMRecord} will not perform
 * validity checks on the data.
 *
 * <p>The following lists the supported {@code DEREncodable} classes and
 * the PEM types that each are encoded as:
 *
 * <ul>
 *  <li>{@code X509Certificate} : CERTIFICATE</li>
 *  <li>{@code X509CRL} : X509 CRL</li>
 *  <li>{@code PublicKey}: PUBLIC KEY</li>
 *  <li>{@code PrivateKey} : PRIVATE KEY</li>
 *  <li>{@code PrivateKey} (if configured with encryption):
 *  ENCRYPTED PRIVATE KEY</li>
 *  <li>{@code EncryptedPrivateKeyInfo} : ENCRYPTED PRIVATE KEY</li>
 *  <li>{@code KeyPair} : PRIVATE KEY</li>
 *  <li>{@code X509EncodedKeySpec} : PUBLIC KEY</li>
 *  <li>{@code PKCS8EncodedKeySpec} : PRIVATE KEY</li>
 *  <li>{@code PEMRecord} : {@code PEMRecord.type()}</li>
 *  </ul>
 *
 * <p> This class is immutable and thread-safe.
 *
 * <p> Here is an example of encoding a {@code PrivateKey} object:
 * {@snippet lang = java:
 *     PEMEncoder pe = PEMEncoder.of();
 *     byte[] pemData = pe.encode(privKey);
 * }
 *
 * <p> Here is an example that encrypts and encodes a private key using the
 * specified password:
 * {@snippet lang = java:
 *     PEMEncoder pe = PEMEncoder.of().withEncryption(password);
 *     byte[] pemData = pe.encode(privKey);
 * }
 *
 * @implNote An implementation may support other PEM types and
 * {@code DEREncodable} objects.
 *
 *
 * @see PEMDecoder
 * @see PEMRecord
 * @see EncryptedPrivateKeyInfo
 *
 * @spec https://www.rfc-editor.org/info/rfc1421
 *       RFC 1421: Privacy Enhancement for Internet Electronic Mail
 * @spec https://www.rfc-editor.org/info/rfc7468
 *       RFC 7468: Textual Encodings of PKIX, PKCS, and CMS Structures
 *
 * @since 25
 */
@PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
public final class PEMEncoder {

    // Singleton instance of PEMEncoder
    private static final PEMEncoder PEM_ENCODER = new PEMEncoder(null);

    // Stores the password for an encrypted encoder that isn't setup yet.
    private PBEKeySpec keySpec;
    // Stores the key after the encoder is ready to encrypt.  The prevents
    // repeated SecretKeyFactory calls if the encoder is used on multiple keys.
    private SecretKey key;
    // Makes SecretKeyFactory generation thread-safe.
    private final ReentrantLock lock;

    /**
     * Instantiate a {@code PEMEncoder} for Encrypted Private Keys.
     *
     * @param pbe contains the password spec used for encryption.
     */
    private PEMEncoder(PBEKeySpec pbe) {
        keySpec = pbe;
        key = null;
        lock = new ReentrantLock();
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
     * Encodes the specified {@code DEREncodable} and returns a PEM encoded
     * string.
     *
     * @param de the {@code DEREncodable} to be encoded
     * @return a {@code String} containing the PEM encoded data
     * @throws IllegalArgumentException if the {@code DEREncodable} cannot be
     * encoded
     * @throws NullPointerException if {@code de} is {@code null}
     * @see #withEncryption(char[])
     */
    public String encodeToString(DEREncodable de) {
        Objects.requireNonNull(de);
        return switch (de) {
            case PublicKey pu -> buildKey(null, pu.getEncoded());
            case PrivateKey pr -> buildKey(pr.getEncoded(), null);
            case KeyPair kp -> {
                if (kp.getPublic() == null) {
                    throw new IllegalArgumentException("KeyPair does not " +
                        "contain PublicKey.");
                }
                if (kp.getPrivate() == null) {
                    throw new IllegalArgumentException("KeyPair does not " +
                        "contain PrivateKey.");
                }
                yield buildKey(kp.getPrivate().getEncoded(),
                    kp.getPublic().getEncoded());
            }
            case X509EncodedKeySpec x ->
                buildKey(null, x.getEncoded());
            case PKCS8EncodedKeySpec p ->
                buildKey(p.getEncoded(), null);
            case EncryptedPrivateKeyInfo epki -> {
                try {
                    yield Pem.pemEncoded(Pem.ENCRYPTED_PRIVATE_KEY,
                        epki.getEncoded());
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            case X509Certificate c -> {
                try {
                    if (isEncrypted()) {
                        throw new IllegalArgumentException("Certificates " +
                            "cannot be encrypted");
                    }
                    yield Pem.pemEncoded(Pem.CERTIFICATE, c.getEncoded());
                } catch (CertificateEncodingException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            case X509CRL crl -> {
                try {
                    if (isEncrypted()) {
                        throw new IllegalArgumentException("CRLs cannot be " +
                            "encrypted");
                    }
                    yield Pem.pemEncoded(Pem.X509_CRL, crl.getEncoded());
                } catch (CRLException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            case PEMRecord rec -> {
                if (isEncrypted()) {
                    throw new IllegalArgumentException("PEMRecord cannot be " +
                        "encrypted");
                }
                yield Pem.pemEncoded(rec);
            }

            default -> throw new IllegalArgumentException("PEM does not " +
                "support " + de.getClass().getCanonicalName());
        };
    }

    /**
     * Encodes the specified {@code DEREncodable} and returns the PEM encoding
     * in a byte array.
     *
     * @param de the {@code DEREncodable} to be encoded
     * @return a PEM encoded byte array
     * @throws IllegalArgumentException if the {@code DEREncodable} cannot be
     * encoded
     * @throws NullPointerException if {@code de} is {@code null}
     * @see #withEncryption(char[])
     */
    public byte[] encode(DEREncodable de) {
        return encodeToString(de).getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Returns a new {@code PEMEncoder} instance configured for encryption
     * with the default algorithm and a given password.
     *
     * <p> Only {@link PrivateKey} objects can be encrypted with this newly
     * configured instance.  Encoding other {@link DEREncodable} objects will
     * throw an {@code IllegalArgumentException}.
     *
     * @implNote
     * The default password-based encryption algorithm is defined
     * by the {@code jdk.epkcs8.defaultAlgorithm} security property and
     * uses the default encryption parameters of the provider that is selected.
     * For greater flexibility with encryption options and parameters, use
     * {@link EncryptedPrivateKeyInfo#encryptKey(PrivateKey, Key,
     * String, AlgorithmParameterSpec, Provider, SecureRandom)} and use the
     * returned object with {@link #encode(DEREncodable)}.
     *
     * @param password the encryption password.  The array is cloned and
     *                stored in the new instance.
     * @return a new {@code PEMEncoder} instance configured for encryption
     * @throws NullPointerException when password is {@code null}
     */
    public PEMEncoder withEncryption(char[] password) {
        // PBEKeySpec clones the password
        Objects.requireNonNull(password, "password cannot be null.");
        return new PEMEncoder(new PBEKeySpec(password));
    }

    /**
     * Build PEM encoding.
     */
    private String buildKey(byte[] privateBytes, byte[] publicBytes) {
        DerOutputStream out = new DerOutputStream();
        Cipher cipher;

        if (privateBytes == null && publicBytes == null) {
            throw new IllegalArgumentException("No encoded data given by the " +
                "DEREncodable.");
        }

        // If `keySpec` is non-null, then `key` hasn't been established.
        // Setting a `key` prevents repeated key generation operations.
        // withEncryption() is a configuration method and cannot throw an
        // exception; therefore generation is delayed.
        if (keySpec != null) {
            // For thread safety
            lock.lock();
            if (key == null) {
                try {
                    key = SecretKeyFactory.getInstance(Pem.DEFAULT_ALGO).
                        generateSecret(keySpec);
                    keySpec.clearPassword();
                    keySpec = null;
                } catch (GeneralSecurityException e) {
                    throw new IllegalArgumentException("Security property " +
                        "\"jdk.epkcs8.defaultAlgorithm\" may not specify a " +
                        "valid algorithm.  Operation cannot be performed.", e);
                } finally {
                    lock.unlock();
                }
            } else {
                lock.unlock();
            }
        }

        // If `key` is non-null, this is an encoder ready to encrypt.
        if (key != null) {
            if (privateBytes == null || publicBytes != null) {
                throw new IllegalArgumentException("Can only encrypt a " +
                    "PrivateKey.");
            }

            try {
                cipher = Cipher.getInstance(Pem.DEFAULT_ALGO);
                cipher.init(Cipher.ENCRYPT_MODE, key);
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("Security property " +
                    "\"jdk.epkcs8.defaultAlgorithm\" may not specify a " +
                    "valid algorithm.  Operation cannot be performed.", e);
            }

            try {
                new AlgorithmId(Pem.getPBEID(Pem.DEFAULT_ALGO),
                    cipher.getParameters()).encode(out);
                out.putOctetString(cipher.doFinal(privateBytes));
                return Pem.pemEncoded(Pem.ENCRYPTED_PRIVATE_KEY,
                    DerValue.wrap(DerValue.tag_Sequence, out).toByteArray());
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException(e);
            }
        }

        // X509 only
        if (publicBytes != null && privateBytes == null) {
            if (publicBytes.length == 0) {
                throw new IllegalArgumentException("No public key encoding " +
                    "given by the DEREncodable.");
            }

            return Pem.pemEncoded(Pem.PUBLIC_KEY, publicBytes);
        }

        // PKCS8 only
        if (publicBytes == null && privateBytes != null) {
            if (privateBytes.length == 0) {
                throw new IllegalArgumentException("No private key encoding " +
                    "given by the DEREncodable.");
            }

            return Pem.pemEncoded(Pem.PRIVATE_KEY, privateBytes);
        }

        // OneAsymmetricKey
        if (privateBytes.length == 0) {
            throw new IllegalArgumentException("No private key encoding " +
                "given by the DEREncodable.");
        }

        if (publicBytes.length == 0) {
            throw new IllegalArgumentException("No public key encoding " +
                "given by the DEREncodable.");
        }
        try {
            return Pem.pemEncoded(Pem.PRIVATE_KEY,
                PKCS8Key.getEncoded(publicBytes, privateBytes));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private boolean isEncrypted() {
        return (key != null || keySpec != null);
    }
}
