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

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * PEMEncoder is an immutable Privacy-Enhanced Mail (PEM) encoding class.
 * Encoding is limited to specific classes which implement
 * {@link SecurityObject}, such as:  PublicKey, PrivateKey, KeySpec,
 * KeyPair, EncryptedPrivateKeyInfo, Certificate, and CRL.
 * <p>
 * PEM is a textual encoding used for storing and transferring security
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRL). Defined in RFC 1421 and RFC7468, PEM consists of a
 * Base64-formatted binary encoding surrounded by a type identifying header
 * and footer.
 * <p>
 * When encrypting private key's, this class uses the Security Property
 * {@code jdk.epkcs8.defaultAlgorithm} for the default algorithm.  To configure
 * all the encryption options see {@link EncryptedPrivateKeyInfo#encryptKey(
 * PrivateKey, char[], String, AlgorithmParameterSpec, Provider)} and use the
 * returned object with {@link #encode(SecurityObject)}.
 */
final public class PEMEncoder implements Encoder<SecurityObject> {

    // If non-null, encoder is configured for encryption
    Cipher cipher;

    /**
     * Create an immutable instance of PEMEncoder.
     */
    public PEMEncoder() {
        new PEMEncoder(null);
    }

    /**
     * Instantiate a new PEMEncoder for Encrypted Private Keys.
     *
     * @param c the cipher object that will be used for encryption
     */
    private PEMEncoder(Cipher c) {
        cipher = c;
    }

    /**
     * Construct a String-based encoding based off the {@code keyType} given.
     *
     * @param keyType the key type
     * @param encoded the encoded
     * @return the string
     */
    private String pemEncoded(Pem.KeyType keyType, byte[] encoded) {
        StringBuilder sb = new StringBuilder(200);
        Base64.Encoder e = Base64.getEncoder();
        switch (keyType) {
            case PUBLIC -> {
                sb.append(Pem.PUBHEADER);
                sb.append(Pem.LINESEPARATOR);
                sb.append(e.encodeToString(encoded));
                sb.append(Pem.PUBFOOTER);
                sb.append(Pem.LINESEPARATOR);
            }
            case PRIVATE -> {
                sb.append(Pem.PKCS8HEADER);
                sb.append(Pem.LINESEPARATOR);
                sb.append(e.encodeToString(encoded));
                sb.append(Pem.PKCS8FOOTER);
                sb.append(Pem.LINESEPARATOR);
            }
            case ENCRYPTED_PRIVATE -> {
                sb.append(Pem.PKCS8ENCHEADER);
                sb.append(Pem.LINESEPARATOR);
                sb.append(e.encodeToString(encoded));
                sb.append(Pem.PKCS8ENCFOOTER);
                sb.append(Pem.LINESEPARATOR);
            }
            default -> {
                return "";
            }
        }
        return sb.toString();
    }

    /**
     * Encoded a given SecurityObject into PEM.
     *
     * @param so the object that implements SecurityObject.
     * @return a PEM encoded string of the given SecurityObject.
     * @throws IOException on any error with the object or the encoding process.
     */
    @Override
    public String encode(SecurityObject so) throws IOException {
        Objects.requireNonNull(so);
        return switch (so) {
            case PublicKey pu -> build(null, pu.getEncoded());
            case PrivateKey pr -> build(pr.getEncoded(), null);
            case KeyPair kp -> {
                if (kp.getPublic() == null) {
                    throw new IOException("KeyPair does not contain PublicKey.");
                }

                if (kp.getPrivate() == null) {
                    throw new IOException("KeyPair does not contain PrivateKey.");
                }
                yield build(kp.getPrivate().getEncoded(),
                    kp.getPublic().getEncoded());
            }
            case X509EncodedKeySpec x -> build(null, x.getEncoded());
            case PKCS8EncodedKeySpec p -> build(p.getEncoded(), null);
            case EncryptedPrivateKeyInfo e -> {
                if (cipher != null) {
                    throw new IOException("encrypt was incorrectly used");
                }
                yield pemEncoded(Pem.KeyType.ENCRYPTED_PRIVATE, e.getEncoded());
            }
            case Certificate c -> {
                StringBuffer sb = new StringBuffer(512);
                sb.append(Pem.CERTHEADER);
                try {
                    sb.append(Base64.getEncoder().encodeToString(c.getEncoded()));
                } catch (CertificateException e) {
                    throw new IOException(e);
                }
                sb.append(Pem.CERTFOOTER);
                yield sb.toString();
            }
            case CRL crl -> {
                X509CRL xcrl = (X509CRL)crl;
                StringBuffer sb = new StringBuffer(512);
                sb.append(Pem.CRLHEADER);
                try {
                    sb.append(Base64.getEncoder().encodeToString(xcrl.getEncoded()));
                } catch (CRLException e) {
                    throw new IOException(e);
                }
                sb.append(Pem.CRLFOOTER);
                yield sb.toString();
            }
            default -> throw new IOException("PEM does not support " +
                so.getClass().getCanonicalName());
        };

    }

    /**
     * Get a PEMEncoder instance configured to the default encrypt algorithm
     * and a particular password.
     * <p>
     * Default algorithm defined by Security Property {@code
     * jdk.epkcs8.defaultAlgorithm}
     * To configure more encryption options, see
     * {@link EncryptedPrivateKeyInfo}.encryptKey(), then use {@code encode()}
     * with the EncryptedPrivateKeyInfo object.
     *
     * @param password the password
     * @return a new PEMEncoder
     * @throws IOException on any encryption errors.
     */
    public PEMEncoder withEncryption(char[] password) throws IOException {
        Objects.requireNonNull(password);

        // PBEKeySpec clones password
        PBEKeySpec spec = new PBEKeySpec(password);

        try {
            SecretKeyFactory factory;
            factory = SecretKeyFactory.getInstance(Pem.DEFAULT_ALGO);
            Cipher c = Cipher.getInstance(Pem.DEFAULT_ALGO);
            c.init(Cipher.ENCRYPT_MODE, factory.generateSecret(spec));
            return new PEMEncoder(c);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Security property " +
                "\"jdk.epkcs8.defaultAlgorithm\" may not specify a " +
                "valid algorithm.", e);
        } catch (Exception e) {
            throw new IOException(e);
        }

    }


    /**
     * Build string.
     *
     * @param privateBytes the private bytes
     * @param publicBytes  the public bytes
     * @return the string
     * @throws IOException the io exception
     */
    private String build(byte[] privateBytes, byte[] publicBytes)
        throws IOException {
        DerOutputStream out = new DerOutputStream();
        byte[] encoded;

        // Encrypted PKCS8
        if (cipher != null) {
            if (privateBytes == null || publicBytes != null) {
                throw new IOException("Can only encrypt a PrivateKey.");
            }
            try {
                new AlgorithmId(Pem.getPBEID(Pem.DEFAULT_ALGO),
                    cipher.getParameters()).encode(out);
                out.putOctetString(cipher.doFinal(privateBytes));
                encoded = DerValue.wrap(DerValue.tag_Sequence, out).
                    toByteArray();
            } catch (Exception e) {
                throw new IOException(e);
            }
            return pemEncoded(Pem.KeyType.ENCRYPTED_PRIVATE, encoded);
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
        return pemEncoded(Pem.KeyType.PRIVATE, PKCS8Key.getEncoded(publicBytes,
            privateBytes));
    }
}
