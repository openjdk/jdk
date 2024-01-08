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
import sun.security.util.Pem;

import javax.crypto.EncryptedPrivateKeyInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * PEMDecoder is an immutable Privacy-Enhanced Mail (PEM) decoding class.
 * Decoding is limited to specific classes which implement
 * {@link SecurityObject}, such as:  PublicKey, PrivateKey, EncodedKeySpec,
 * EncryptedPrivateKeyInfo, Certificate, and CRL.
 * <p>
 * PEM is a textual encoding used for storing and transferring security
 * objects, such as asymmetric keys, certificates, and certificate revocation
 * lists (CRL). Defined in RFC 1421 and RFC7468, PEM consists of a
 * Base64-formatted binary encoding surrounded by a type identifying header
 * and footer.
 */
final public class PEMDecoder implements Decoder<SecurityObject> {
    final Provider factory;
    final char[] password;

    /**
     * Create an immutable instance of PEMDecoder.
     */
    public PEMDecoder() {
        factory = null;
        password = null;
    }

    /**
     * Creates a immutable instance with a specific KeyFactory and/or password.
     * @param withFactory KeyFactory provider
     * @param withPassword char[] password for EncryptedPrivateKeyInfo
     *                    decryption
     */
    private PEMDecoder(Provider withFactory, char[] withPassword) {
        super();
        factory = withFactory;
        password = withPassword;
    }

    private SecurityObject decode(String data, String header,
        String footer) throws IOException {
        Pem.KeyType keyType;

        if (header.equalsIgnoreCase(Pem.PUBHEADER) &&
            footer.equalsIgnoreCase(Pem.PUBFOOTER)) {
            keyType = Pem.KeyType.PUBLIC;
        } else if (header.startsWith(Pem.PKCS8HEADER) &&
            footer.equalsIgnoreCase(Pem.PKCS8FOOTER)) {
            keyType = Pem.KeyType.PRIVATE;
        } else if (header.startsWith(Pem.PKCS8ENCHEADER) &&
            footer.equalsIgnoreCase(Pem.PKCS8ENCFOOTER)) {
            keyType = Pem.KeyType.ENCRYPTED_PRIVATE;
        } else if (header.startsWith(Pem.CERTHEADER) &&
            footer.equalsIgnoreCase(Pem.CERTFOOTER)) {
            keyType = Pem.KeyType.CERTIFICATE;
        } else if (header.startsWith(Pem.CRLHEADER) &&
            footer.equalsIgnoreCase(Pem.CRLFOOTER)) {
            keyType = Pem.KeyType.CRL;
        } else {
            throw new IOException("Unsupported PEM header/footer");
        }

        if (password != null) {
            if (keyType != Pem.KeyType.ENCRYPTED_PRIVATE) {
                throw new IOException("Decoder configured only for " +
                    "encrypted PEM.");
            }
        }

        Base64.Decoder decoder = Base64.getDecoder();

        return switch (keyType) {
            case PUBLIC -> {
                X509EncodedKeySpec spec =
                    new X509EncodedKeySpec(decoder.decode(data));
                try {
                    yield ((KeyFactory) getFactory(keyType,
                        spec.getAlgorithm())).generatePublic(spec);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
            case PRIVATE -> {
                try {
                    PKCS8Key p8key = new PKCS8Key(decoder.decode(data));
                    PrivateKey priKey;
                    KeyFactory kf = (KeyFactory)
                        getFactory(keyType, p8key.getAlgorithm());
                    priKey = kf.generatePrivate(
                        new PKCS8EncodedKeySpec(p8key.getEncoded(),//getPrivKeyMaterial(),
                            p8key.getAlgorithm()));
                    // If there is a public key, it's an OAS.
                    if (p8key.getPubKeyEncoded() != null) {
                        X509EncodedKeySpec spec = new X509EncodedKeySpec(
                            p8key.getPubKeyEncoded(), p8key.getAlgorithm());
                        yield new KeyPair(((KeyFactory)
                                getFactory(keyType, p8key.getAlgorithm()))
                                .generatePublic(spec),
                            priKey);
                    }
                    yield priKey;
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
            case ENCRYPTED_PRIVATE -> {
                if (password == null) {
                    yield new EncryptedPrivateKeyInfo(decoder.decode(data));
                }
                yield new EncryptedPrivateKeyInfo(decoder.decode(data)).
                    getKey(password);
            }
            case CERTIFICATE -> {
                try {
                    CertificateFactory cf =
                        (CertificateFactory) getFactory(keyType, "X509");
                    /*yield cf.generateCertificate(
                        new ByteArrayInputStream((header + "\n" + data +
                            footer + "\n").getBytes()));
                     */
                    yield cf.generateCertificate(new ByteArrayInputStream(decoder.decode(data)));
                } catch (CertificateException e) {
                    throw new IOException(e);
                }
            }
            case CRL -> {
                try {
                    CertificateFactory cf =
                        (CertificateFactory) getFactory(keyType, "X509");
                    yield cf.generateCRL(new ByteArrayInputStream(decoder.decode(data)));
                    //yield cf.generateCRL(new ByteArrayInputStream((header +
                    //    "\n" + data + footer + "\n").getBytes()));
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
            default -> throw new IOException("Unsupported type or not " +
                "properly formatted PEM");
        };
    }

    // Convenience method to avoid provider getInstance checks clutter
    private Object getFactory(Pem.KeyType type, String algorithm)
        throws IOException {
        try {
            if (factory == null) {
                return switch (type) {
                    case PUBLIC, PRIVATE -> KeyFactory.getInstance(algorithm);
                    case CERTIFICATE, CRL ->
                        CertificateFactory.getInstance(algorithm);
                    default -> null;  // no possible
                };
            } else {
                return switch (type) {
                    case PUBLIC, PRIVATE ->
                        KeyFactory.getInstance(algorithm, factory);
                    case CERTIFICATE, CRL ->
                        CertificateFactory.getInstance(algorithm, factory);
                    default -> null;  // no possible
                };
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns a new PEMDecoder instance from the existing instance that only
     * generates using Key and Certificate factories from the given Provider.
     *
     * @param provider the Factory provider for the new decoder instance.
     * @return a new PEM decoder instance.
     */
    public PEMDecoder withFactoryFrom(Provider provider) {
        return new PEMDecoder(provider, password);
    }

    /**
     * Returns a new PEMDecoder instance from the existing instance that will
     * use the given password for any encrypted private keys decoded.
     * Non-encrypted PEM may still be decoded from this instance.
     *
     * @param password the password used on encrypted private keys
     * @return the decoder
     */
    public PEMDecoder withDecryption(char[] password) {
        Objects.requireNonNull(password);
        return new PEMDecoder(factory, password);
    }

    /**
     * Decodes and returns {@code SecurityObject} from the given string.
     *
     * @param str PEM data in a String.
     * @return an object that is contained in the PEM data.
     * @throws IOException on an error in decoding or if the PEM is unsupported.
     */
    @Override
    public SecurityObject decode(String str) throws IOException {
        Objects.requireNonNull(str);
        return decode(new StringReader(str));
    }

    /**
     * Decodes and returns {@code SecurityObject} from the given Reader.
     *
     * @param reader PEM data from a Reader
     * @return an object that is contained in the PEM data.
     * @throws IOException on an error in decoding or if the PEM is unsupported.
     */
    @Override
    public SecurityObject decode(Reader reader) throws IOException {
        Objects.requireNonNull(reader);
        Pem pem = Pem.readPEM(reader);
        return decode(pem.getData(), pem.getHeader(), pem.getFooter());
    }


    /**
     * Decodes and returns an object from the given Reader. The user must have prior
     * knowledge of the PEM data class type and specify the returned object's
     * class.
     *
     * @param <S>    the type parameter
     * @param reader a Reader that provides a stream of PEM data.
     * @param tClass the class instance of the returned object.  The class must implement {@code SecurityObject}.
     * @return foo s
     * @throws IOException on an error in decoding or if the PEM is unsupported.
     */
    @Override
    public <S extends SecurityObject> S decode(Reader reader,
        Class<S> tClass) throws IOException {
        Objects.requireNonNull(reader);
        Objects.requireNonNull(tClass);
        try {
            return tClass.cast(decode(reader));
        } catch (ClassCastException e) {
            throw new IOException(e);
        }
    }

    /**
     * Decodes and returns an object from the given PEM string. The user must have prior
     * knowledge of the PEM data class type and specify the returned object's
     * class.
     *
     * @param <S> Type parameter
     * @param string the String containing PEM data.
     * @param tClass the class instance of the returned object.  The class must implement {@code SecurityObject}.
     * @return The SecurityObject typecasted to tClass.
     * @throws IOException on an error in decoding or if the PEM is unsupported.
     */
    @Override
    public <S extends SecurityObject> S decode(String string,
        Class<S> tClass) throws IOException {
        Objects.requireNonNull(string);
        Objects.requireNonNull(tClass);

        // KeySpec's other that EncodedKeySpec's do not differentiate between
        // public or private key via a subclass.  Specifying each class is
        // in this the decoder error-prone and not extensible.
        if (tClass.isInstance(KeySpec.class) &&
            (!tClass.isInstance(X509EncodedKeySpec.class) &&
                !tClass.isInstance(PKCS8EncodedKeySpec.class))) {
            throw new IOException("Decoder does not support the provided " +
                "KeySpec.");
        }

        try {
            return tClass.cast(decode(new StringReader(string)));
        } catch (ClassCastException e) {
            throw new IOException(e);
        }
    }
}
