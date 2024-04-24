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
 * <br>
 * @apiNote
 * Here is an example of encoding a PrivateKey object:
 * <pre>{@code
 *     PEMEncoder pe = PEMEncoder.of();
 *     byte[] pemData = pe.encode(privKey);
 * }</pre>
 *
 */
final public class PEMEncoder {

    // Singleton instance of PEMEncoder
    final private static PEMEncoder PEM_ENCODER = new PEMEncoder(null);

    // If non-null, encoder is configured for encryption
    private Cipher cipher = null;
    private char[] password;

    /**
     * Instantiate a new PEMEncoder for Encrypted Private Keys.
     *
     * @param pwd is the password to generate the Cipher key with.
     */
    private PEMEncoder(char[] pwd) {
        password = pwd;
    }

    /**
     * Returns a instance of PEMEncoder.
     *
     * @return PEMEncoder instance
     */
    static public PEMEncoder of() {
        return PEM_ENCODER;
    }

    /**
     * Construct a String-based encoding based off the {@code keyType} given.
     *
     * @param keyType the key type
     * @param encoded the encoded
     * @return the string
     */
    private byte[] pemEncoded(Pem.KeyType keyType, byte[] encoded) {
        ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
        switch (keyType) {
            case PUBLIC -> {
                os.writeBytes(Pem.PUBHEADER);
                os.writeBytes(Pem.LINESEPARATOR);
                os.writeBytes(convertToPEM(encoded));
                os.writeBytes(Pem.PUBFOOTER);
                os.writeBytes(Pem.LINESEPARATOR);
            }
            case PRIVATE -> {
                os.writeBytes(Pem.PKCS8HEADER);
                os.writeBytes(Pem.LINESEPARATOR);
                os.writeBytes(convertToPEM(encoded));
                os.writeBytes(Pem.PKCS8FOOTER);
                os.writeBytes(Pem.LINESEPARATOR);
            }
            case ENCRYPTED_PRIVATE -> {
                os.writeBytes(Pem.PKCS8ENCHEADER);
                os.writeBytes(Pem.LINESEPARATOR);
                os.writeBytes(convertToPEM(encoded));
                os.writeBytes(Pem.PKCS8ENCFOOTER);
                os.writeBytes(Pem.LINESEPARATOR);
            }
            default -> {
                return new byte[0];
            }
        }
        return os.toByteArray();
    }

    static byte[] convertToPEM(byte[] encoding) {
        if (encoding.length == 0) {
            return new byte[0];
        }
        Base64.Encoder e = Base64.getMimeEncoder(64, Pem.LINESEPARATOR);
        ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
        /*
        byte[] pem = e.encode(encoding);
        int len = pem.length;
        int i = 0;
        while (i + 64 < len) {
            os.write(pem, i, 64);
            os.writeBytes(Pem.LINESEPARATOR);
            i += 64;
        }
        os.write(pem, i, pem.length - i);
         */
        os.writeBytes(e.encode(encoding));
        os.writeBytes(Pem.LINESEPARATOR);  // Maybe can remove if the encoder changes
        return os.toByteArray();
    }

    /**
     * Encoded a given {@code DEREncodable} and return the PEM encoding in a String
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
        return new String(encode(so), StandardCharsets.UTF_8);
    }

    /**
     * Encoded a given {@code DEREncodable} into PEM.
     *
     * @param so the object that implements DEREncodable.
     * @return a PEM encoded string of the given DEREncodable.
     * @throws IllegalArgumentException when the passed object returns a null
     * binary encoding. An exception is thrown when PEMEncoder is
     * configured for encryption while encoding a DEREncodable that does
     * not support encryption.
     * @throws NullPointerException when object passed is null.
     * @see #withEncryption(char[])
     */
    public byte[] encode(DEREncodable so) {
        Objects.requireNonNull(so);
        return switch (so) {
            case PublicKey pu -> build(null, pu.getEncoded());
            case PrivateKey pr -> build(pr.getEncoded(), null);
            case KeyPair kp -> {
                if (kp.getPublic() == null) {
                    throw new IllegalArgumentException("KeyPair does not contain PublicKey.");
                }

                if (kp.getPrivate() == null) {
                    throw new IllegalArgumentException("KeyPair does not contain PrivateKey.");
                }
                yield build(kp.getPrivate().getEncoded(),
                    kp.getPublic().getEncoded());
            }
            case X509EncodedKeySpec x -> build(null, x.getEncoded());
            case PKCS8EncodedKeySpec p -> build(p.getEncoded(), null);
            case EncryptedPrivateKeyInfo epki -> {
                if (password != null && cipher == null) {
                    // PBEKeySpec clones the password array
                    PBEKeySpec spec = new PBEKeySpec(password);
                    Arrays.fill(password, (char)0x0);
                    password = null;

                    try {
                        SecretKeyFactory factory =
                            SecretKeyFactory.getInstance(Pem.DEFAULT_ALGO);
                        Cipher c = Cipher.getInstance(Pem.DEFAULT_ALGO);
                        c.init(Cipher.ENCRYPT_MODE, factory.generateSecret(spec));
                    } catch (Exception e) {
                        throw new IllegalArgumentException(e);
                    }
                }
                try {
                    yield pemEncoded(Pem.KeyType.ENCRYPTED_PRIVATE, epki.getEncoded());
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            case Certificate c -> {
                ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
                os.writeBytes(Pem.CERTHEADER);
                try {
                    os.writeBytes(Base64.getMimeEncoder().encode(c.getEncoded()));
                } catch (CertificateEncodingException e) {
                    throw new IllegalArgumentException(e);
                }
                os.writeBytes(Pem.CERTFOOTER);
                yield os.toByteArray();
            }
            case CRL crl -> {
                X509CRL xcrl = (X509CRL)crl;
                ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
                os.writeBytes(Pem.CRLHEADER);
                try {
                    os.writeBytes(Base64.getMimeEncoder().encode(xcrl.getEncoded()));
                } catch (CRLException e) {
                    throw new IllegalArgumentException(e);
                }
                os.writeBytes(Pem.CRLFOOTER);
                yield os.toByteArray();
            }
            default -> throw new IllegalArgumentException("PEM does not support " +
                so.getClass().getCanonicalName());
        };
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
     */
    public PEMEncoder withEncryption(char[] password) {
        char[] pwd = password.clone();
        Objects.requireNonNull(pwd);
        return new PEMEncoder(pwd);
    }

    /**
     * Build PEM encoding.
     */
    private byte[] build(byte[] privateBytes, byte[] publicBytes) {
        DerOutputStream out = new DerOutputStream();

        // Encrypted PKCS8
        if (cipher != null) {
            if (privateBytes == null || publicBytes != null) {
                throw new IllegalArgumentException("Can only encrypt a PrivateKey.");
            }
            new AlgorithmId(Pem.getPBEID(Pem.DEFAULT_ALGO),
                cipher.getParameters())
                .encode(out);
            try {
                out.putOctetString(cipher.doFinal(privateBytes));
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException(e);
            }

            return pemEncoded(Pem.KeyType.ENCRYPTED_PRIVATE,
                DerValue.wrap(DerValue.tag_Sequence, out).toByteArray());
        }

        // X509 only
        if (publicBytes != null && privateBytes == null) {
            return pemEncoded(Pem.KeyType.PUBLIC, publicBytes);
        }
        // PKCS8 only
        if (publicBytes == null && privateBytes != null) {
            return pemEncoded(Pem.KeyType.PRIVATE, privateBytes);
        }
        // OAS
        try {
            return pemEncoded(Pem.KeyType.PRIVATE, PKCS8Key.getEncoded(publicBytes,
                privateBytes));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
