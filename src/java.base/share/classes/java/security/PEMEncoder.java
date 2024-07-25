/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * PEMEncoder is an immutable Privacy-Enhanced Mail (PEM) encoding class.
 * PEM is a textual encoding used for storing and transferring security
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRL). Defined in RFC 1421 and RFC 7468, PEM consists of a
 * Base64-formatted binary encoding surrounded by a type identifying header
 * and footer.
 * <p>
 * Encoding may be performed on objects that implement {@link DEREncodable}.
 * <p>
 * Encrypted private key PEM data can be built by calling the encode methods
 * on a PEMEncoder instance returned by {@link #withEncryption(char[])} or
 * by passing an {@link EncryptedPrivateKeyInfo} object into the encode methods.
 * <p>
 * PKCS8 v2.0 allows OneAsymmetric encoding, which is a private and public
 * key in the same PEM.  This is supported by using the {@link KeyPair} class
 * with the encode methods.
 * <p>
 * PEMEncoder supports the follow types:
 * <pre>
 *     PRIVATE KEY, PUBLIC KEY, CERTIFICATE, CRL, and ENCRYPTED PRIVATE KEY.
 * </pre>
 *
 * @apiNote
 * Here is an example of encoding a PrivateKey object:
 * <pre>
 *     PEMEncoder pe = PEMEncoder.of();
 *     byte[] pemData = pe.encode(privKey);
 * </pre>
 *
 * @since 24
 */
@PreviewFeature(feature = PreviewFeature.Feature.PEM_API)
public final class PEMEncoder {

    // Singleton instance of PEMEncoder
    private static final PEMEncoder PEM_ENCODER = new PEMEncoder(null);

    // If non-null, encoder is configured for encryption
    private Cipher cipher = null;
    private final char[] password;
    private static Base64.Encoder b64Encoder;

    /**
     * Instantiate a new PEMEncoder for Encrypted Private Keys.
     *
     * @param pwd is the password to generate the Cipher key with.
     */
    private PEMEncoder(char[] pwd) {
        password = pwd;
    }

    /**
     * Returns an instance of PEMEncoder.
     *
     * @return PEMEncoder instance
     */
    static public PEMEncoder of() {
        return PEM_ENCODER;
    }

    /**
     * Construct a String-based encoding based off the id type.
     * @return the string
     */
    private String pemEncoded(PEMRecord pem) {
        StringBuffer sb = new StringBuffer(1024);
        sb.append("-----BEGIN ").append(pem.id()).append("-----");
        sb.append(System.lineSeparator());
        if (b64Encoder == null) {
            b64Encoder = Base64.getMimeEncoder(64,
                System.lineSeparator().getBytes());
        }
        sb.append(b64Encoder.encodeToString(
            pem.pem().getBytes(StandardCharsets.ISO_8859_1)));
        sb.append(System.lineSeparator());
        sb.append("-----END ").append(pem.id()).append("-----");
        sb.append(System.lineSeparator());
        return sb.toString();
    }

    /**
     * Encoded a given {@code DEREncodable} and return the PEM encoding in a
     * String
     *
     * @param so a cryptographic object to be PEM encoded that implements
     *           DEREncodable.
     * @return PEM encoding in a String
     * @throws IllegalArgumentException when the passed object returns a null
     * binary encoding. An exception is thrown when PEMEncoder is
     * configured for encryption while encoding a DEREncodable that does
     * not support encryption.
     * @throws NullPointerException when object passed is null.
     * @see #withEncryption(char[])
     */
    public String encodeToString(DEREncodable so) {
        Objects.requireNonNull(so);
        return switch (so) {
            case PublicKey pu -> build(null, pu.getEncoded());
            case PrivateKey pr -> build(pr.getEncoded(), null);
            case KeyPair kp -> {
                if (kp.getPublic() == null) {
                    throw new IllegalArgumentException("KeyPair does not " +
                        "contain PublicKey.");
                }

                if (kp.getPrivate() == null) {
                    throw new IllegalArgumentException("KeyPair does not " +
                        "contain PrivateKey.");
                }
                yield build(kp.getPrivate().getEncoded(),
                    kp.getPublic().getEncoded());
            }
            case X509EncodedKeySpec x -> build(null, x.getEncoded());
            case PKCS8EncodedKeySpec p -> build(p.getEncoded(), null);
            case EncryptedPrivateKeyInfo epki -> {
                if (password != null) {
                    throw new IllegalArgumentException("encrypt was " +
                        "incorrectly used");
                }
                try {
                    yield pemEncoded(new PEMRecord(
                        PEMRecord.ENCRYPTED_PRIVATE_KEY, epki.getEncoded()));
                } catch (IOException e) {
                    throw new SecurityException(e);
                }
            }
            case Certificate c -> {
                try {
                    yield pemEncoded(new PEMRecord(PEMRecord.CERTIFICATE,
                        c.getEncoded()));
                } catch (CertificateEncodingException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            case CRL crl -> {
                X509CRL xcrl = (X509CRL)crl;
                try {
                    yield pemEncoded(new PEMRecord(PEMRecord.X509_CRL,
                        xcrl.getEncoded()));
                } catch (CRLException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            case PEMRecord rec -> {
                yield pemEncoded(rec);
            }
            default -> throw new IllegalArgumentException("PEM does not " +
                "support " + so.getClass().getCanonicalName());
        };
    }

    /**
     * Encoded a given {@code DEREncodable} into PEM.
     *
     * @param so the object that implements DEREncodable.
     * @return a PEM encoded byte[] of the given DEREncodable.
     * @throws IllegalArgumentException when the passed object returns a null
     * binary encoding. An exception is thrown when PEMEncoder is
     * configured for encryption while encoding a DEREncodable that does
     * not support encryption.
     * @throws NullPointerException when object passed is null.
     * @see #withEncryption(char[])
     */
    public byte[] encode(DEREncodable so) {
        return encodeToString(so).getBytes(StandardCharsets.ISO_8859_1);
    }

    /**
     * Returns a new immutable PEMEncoder instance configured to the default
     * encrypt algorithm and a given password.
     *
     * <p> Only {@link PrivateKey} will be encrypted with this newly configured
     * instance.  Other {@link DEREncodable} classes that do not support
     * encrypted PEM will cause encode() to throw an IOException.
     *
     * <p> Default algorithm defined by Security Property {@code
     * jdk.epkcs8.defaultAlgorithm}.  To configure all the encryption options
     * see {@link EncryptedPrivateKeyInfo#encryptKey(PrivateKey, char[], String,
     * AlgorithmParameterSpec, Provider)} and use the returned object with
     * {@link #encode(DEREncodable)}.
     *
     * @param password the password
     * @return a new PEMEncoder
     * @throws NullPointerException if password is null.
     */
    public PEMEncoder withEncryption(char[] password) {
        char[] pwd = password.clone();
        return new PEMEncoder(pwd);
    }

    /**
     * Build PEM encoding.
     */
    private String build(byte[] privateBytes, byte[] publicBytes) {
        DerOutputStream out = new DerOutputStream();

        // Encrypted PKCS8
        if (password != null) {
            if (privateBytes == null || publicBytes != null) {
                throw new IllegalArgumentException("Can only encrypt a " +
                    "PrivateKey.");
            }

            // PBEKeySpec clones the password array
            PBEKeySpec spec = new PBEKeySpec(password);
            Arrays.fill(password, (char)0x0);

            if (cipher == null) {
                try {
                    SecretKeyFactory factory;
                    factory = SecretKeyFactory.getInstance(Pem.DEFAULT_ALGO);
                    cipher = Cipher.getInstance(Pem.DEFAULT_ALGO);
                    cipher.init(Cipher.ENCRYPT_MODE, factory.generateSecret(spec));
                } catch (GeneralSecurityException e) {
                    throw new SecurityException("Security property " +
                        "\"jdk.epkcs8.defaultAlgorithm\" may not specify a " +
                        "valid algorithm.", e);
                }
            }

            new AlgorithmId(Pem.getPBEID(Pem.DEFAULT_ALGO),
                cipher.getParameters()).encode(out);
            try {
                out.putOctetString(cipher.doFinal(privateBytes));
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException(e);
            }

            return pemEncoded(new PEMRecord(PEMRecord.ENCRYPTED_PRIVATE_KEY,
                DerValue.wrap(DerValue.tag_Sequence, out).toByteArray()));
        }

        // X509 only
        if (publicBytes != null && privateBytes == null) {
            return pemEncoded(new PEMRecord(PEMRecord.PUBLIC_KEY, publicBytes));
        }
        // PKCS8 only
        if (publicBytes == null && privateBytes != null) {
            return pemEncoded(new PEMRecord(PEMRecord.PRIVATE_KEY,
                privateBytes));
        }
        // OAS
        try {
            return pemEncoded(new PEMRecord(PEMRecord.PRIVATE_KEY,
                PKCS8Key.getEncoded(publicBytes, privateBytes)));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
