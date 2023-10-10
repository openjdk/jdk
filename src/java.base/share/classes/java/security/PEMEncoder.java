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
import sun.security.util.Encoder;
import sun.security.util.Pem;
import sun.security.x509.AlgorithmId;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * The type Pem.
 */
final class PEMEncoder implements Encoder<SecurityObject> {

    // XXX This is in java.security file
    /**
     * The Cipher.
     */
    //protected static final String DEFAULT_ALGO = "PBEWithHmacSHA256AndAES_128";

    /**
     * The Cipher.
     */
    Cipher cipher;
    /**
     * The Algid.
     */
    AlgorithmId algid;

    /**
     * Instantiates a new Encoder.
     *
     * @param c the c
     * @param a the a
     */
    PEMEncoder(Cipher c, AlgorithmId a) {
        cipher = c;
        algid = a;
    }

    /**
     * Instantiates a new Encoder.
     *
     * @return the pem encoder
     */
    PEMEncoder() {
        new PEMEncoder(null,null);
    }

    /**
     * Pem encoded string.
     *
     * @param keyType the key type
     * @param encoded the encoded
     * @return the string
     */
    String pemEncoded(Pem.KeyType keyType, byte[] encoded) {
        StringBuilder sb = new StringBuilder(100);
        Base64.Encoder e = Base64.getEncoder();
        switch (keyType) {
            case PUBLIC -> {
                sb.append(Pem.PUBHEADER);
                sb.append(e.encodeToString(encoded));
                sb.append(Pem.PUBFOOTER);
            }
            case PRIVATE -> {
                sb.append(Pem.PKCS8HEADER);
                sb.append(e.encodeToString(encoded));
                sb.append(Pem.PKCS8FOOTER);
            }
            case ENCRYPTED_PRIVATE -> {
                sb.append(Pem.PKCS8ENCHEADER);
                sb.append(e.encodeToString(encoded));
                sb.append(Pem.PKCS8ENCFOOTER);
            }
            default -> {
                return "";
            }
        }
        return sb.toString();
    }

    @Override
    public String encode(SecurityObject so) throws IOException {
        Objects.requireNonNull(so);
        if (so instanceof PublicKey)
            return build(null, ((PublicKey) so).getEncoded());
        else if (so instanceof PrivateKey)
            return build(((PrivateKey)so).getEncoded(), null);
        else if (so instanceof KeyPair) {
            KeyPair kp = (KeyPair)so;
            if (kp.getPublic() == null) {
                throw new IOException("KeyPair does not contain PublicKey.");
            }

            if (kp.getPrivate() == null) {
                throw new IOException("KeyPair does not contain PrivateKey.");
            }
            return build(kp.getPrivate().getEncoded(),
                kp.getPublic().getEncoded());
        }
        else if (so instanceof X509EncodedKeySpec)
            return build(null, ((X509EncodedKeySpec)so).getEncoded());
        else if (so instanceof PKCS8EncodedKeySpec)
            return build(null, ((PKCS8EncodedKeySpec)so).getEncoded());
        else if (so instanceof EncryptedPrivateKeyInfo)
            return build(((EncryptedPrivateKeyInfo)so).getEncoded(), null);
        else if (so instanceof EncryptedPrivateKeyInfo) {
            if (cipher != null) {
                throw new IOException("encrypt was incorrectly used");
            }
            return pemEncoded(Pem.KeyType.ENCRYPTED_PRIVATE, ((EncryptedPrivateKeyInfo)so).getEncoded());
        }
        else if (so instanceof Certificate) {
            StringBuffer sb = new StringBuffer(512);
            sb.append(Pem.CERTHEADER);
            try {
                sb.append(Base64.getEncoder().encode(((Certificate)so).getEncoded()));
            } catch (CertificateException e) {
                throw new IOException(e);
            }
            sb.append(Pem.CERTFOOTER);
            return sb.toString();
        }
        else if (so instanceof CRL) {
            X509CRL xcrl = (X509CRL)so;
            StringBuffer sb = new StringBuffer(512);
            sb.append(Pem.CRLHEADER);
            try {
                sb.append(Base64.getEncoder().encode(xcrl.getEncoded()));
            } catch (CRLException e) {
                throw new IOException(e);
            }
            sb.append(Pem.CRLFOOTER);
            return sb.toString();
        }
        else {
            throw new IOException("PEM does not support " +
                so.getClass().getCanonicalName());
        }
        /*
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
                build(kp.getPrivate().getEncoded(),
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
                    sb.append(Base64.getEncoder().encode(c.getEncoded()));
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
                    sb.append(Base64.getEncoder().encode(xcrl.getEncoded()));
                } catch (CRLException e) {
                    throw new IOException(e);
                }
                sb.append(Pem.CRLFOOTER);
                yield sb.toString();
            }
            default -> throw new IOException("PEM does not support " +
                tClass.getCanonicalName());
        };
         */
    }

    /**
     * Encrypt encoder.
     *
     * @param password the password
     * @return the encoder
     * @throws IOException the io exception
     */
    public PEMEncoder withEncryption(char[] password) throws IOException {
        Objects.requireNonNull(password);
        if (cipher != null) {
            throw new IOException("Encryption cannot be used more than once");
        }

        AlgorithmId algid;
        Cipher c;
        try {
            var spec = new PBEKeySpec(password);
            // PBEKeySpec clones password
            SecretKeyFactory factory;
            factory = SecretKeyFactory.getInstance(Pem.DEFAULT_ALGO);
            c = Cipher.getInstance(Pem.DEFAULT_ALGO);
            var skey = factory.generateSecret(spec);
            c.init(Cipher.ENCRYPT_MODE, skey);
            algid = new AlgorithmId(Pem.getPBEID(Pem.DEFAULT_ALGO),
                c.getParameters());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Security property " +
                "\"jdk.epkcs8.defaultAlgorithm\" may not specify a " +
                "valid algorithm.", e);
        } catch (Exception e) {
            throw new IOException(e);
        }

        return new PEMEncoder(c, algid);
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
                algid.encode(out);
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
