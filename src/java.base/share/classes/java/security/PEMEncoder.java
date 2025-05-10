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
 * {@code PEMEncoder} is used for encoding Privacy-Enhanced Mail (PEM) data.
 * PEM is a textual encoding used to store and transfer security
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRL).  It is defined in RFC 1421 and RFC 7468.  PEM consists of a
 * Base64-formatted binary encoding enclosed by a type-identifying header
 * and footer.
 *
 * <p> Encoding may be performed on Java Cryptographic Extension (JCE) objects
 * that implement {@link DEREncodable} and support
 * {@linkplain PKCS8EncodedKeySpec PKCS#8} or
 * {@linkplain X509EncodedKeySpec X509} formats.
 *
 * <p> Encrypted private key PEM data can be built by calling the encode methods
 * on a PEMEncoder instance returned by {@link #withEncryption(char[])} or
 * by passing an {@link EncryptedPrivateKeyInfo} object into the encode methods.
 *
 * <p> PKCS8 2.0 allows OneAsymmetricKey encoding, which may contain both private
 * and public keys in the same PEM.This is supported by using the
 * {@link KeyPair} class with the encode methods.
 *
 * <p> When encoding a {@link PEMRecord}, the API surrounds the
 * {@linkplain PEMRecord#pem()} with a generated the PEM header and footer
 * from {@linkplain PEMRecord#type()}.  It will not check the validity of
 * the data.
 *
 * <p>{@code String} values returned by this class use character set
 * {@link java.nio.charset.StandardCharsets#ISO_8859_1 ISO-8859-1}.
 *
 * <p>This class is immutable and thread-safe.
 *
 * @apiNote
 * Here is an example of encoding a PrivateKey object:
 * {@snippet lang = java:
 *     PEMEncoder pe = PEMEncoder.of();
 *     byte[] pemData = pe.encode(privKey);
 * }
 *
 * @see PKCS8EncodedKeySpec
 * @see X509EncodedKeySpec
 * @see PEMDecoder
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
     * Instantiate a new PEMEncoder for Encrypted Private Keys.
     *
     * @param pbe contains the password spec used for encryption.
     */
    private PEMEncoder(PBEKeySpec pbe) {
        keySpec = pbe;
        key = null;
        lock = new ReentrantLock();
    }

    /**
     * Returns a new instance of PEMEncoder.
     *
     * @return PEMEncoder instance
     */
    static public PEMEncoder of() {
        return PEM_ENCODER;
    }

    /**
     * Encoded a given {@code DEREncodable} and return the PEM encoding in a
     * String
     *
     * @param de a cryptographic object to be PEM encoded that implements
     *           {@code DEREncodable}.
     * @return PEM encoding in a String
     * @throws IllegalArgumentException when the passed object returns a null
     * binary encoding. An exception is thrown when PEMEncoder is
     * configured for encryption while encoding a {@code DEREncodable} that does
     * not support encryption.
     * @throws NullPointerException when object passed is null.
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
            case PEMRecord rec -> Pem.pemEncoded(rec);

            default -> throw new IllegalArgumentException("PEM does not " +
                "support " + de.getClass().getCanonicalName());
        };
    }

    /**
     * Encodes a given {@code DEREncodable} into PEM.
     *
     * @param de the object that implements {@code DEREncodable}.
     * @return a PEM encoded byte[] of the given {@code DEREncodable}.
     * @throws IllegalArgumentException when the passed object returns a null
     * binary encoding. An exception is thrown when PEMEncoder is
     * configured for encryption while encoding a {@code DEREncodable} that does
     * not support encryption.
     * @throws NullPointerException when object passed is null.
     * @see #withEncryption(char[])
     */
    public byte[] encode(DEREncodable de) {
        return encodeToString(de).getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Returns a new immutable PEMEncoder instance configured to the default
     * encryption algorithm and a given password.
     *
     * <p> Only {@link PrivateKey} will be encrypted with this newly configured
     * instance.  Other {@link DEREncodable} classes that do not support
     * encrypted PEM will cause encode() to throw an IllegalArgumentException.
     *
     * @implNote Default algorithm defined by Security Property {@code
     * jdk.epkcs8.defaultAlgorithm}.  To configure all the encryption options
     * see {@link EncryptedPrivateKeyInfo#encryptKey(PrivateKey, char[], String,
     * AlgorithmParameterSpec, Provider)} and use the returned object with
     * {@link #encode(DEREncodable)}.
     *
     * @param password sets the encryption password.  The array is cloned and
     *                stored in the new instance. {@code null} is a valid entry.
     * @return a new PEMEncoder
     */
    public PEMEncoder withEncryption(char[] password) {
        // PBEKeySpec clones the password
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
        // Setting a `key' prevents repeated key generations operations.
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
                    throw new SecurityException("Security property " +
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
                throw new SecurityException("Security property " +
                    "\"jdk.epkcs8.defaultAlgorithm\" may not specify a " +
                    "valid algorithm.  Operation cannot be performed.", e);
            }

            new AlgorithmId(Pem.getPBEID(Pem.DEFAULT_ALGO),
                cipher.getParameters()).encode(out);

            try {
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
        try {
            if (privateBytes.length == 0) {
                throw new IllegalArgumentException("No private key encoding " +
                    "given by the DEREncodable.");
            }

            if (publicBytes.length == 0) {
                throw new IllegalArgumentException("No public key encoding " +
                    "given by the DEREncodable.");
            }

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
