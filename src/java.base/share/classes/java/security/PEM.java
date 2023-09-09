package java.security;

import sun.security.pkcs.PKCS8Key;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.Encoder;
import sun.security.util.Decoder;
import sun.security.util.Pem;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X509Key;

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
public class PEM {

    private enum KeyType {
        UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE, CERTIFICATE, CRL
    }

    private PEM() {}

    /**
     * Gets encoder.
     *
     * @return the encoder
     */
    public static PEMEncoder getEncoder() {
        return new PEMEncoder();
    }

    /**
     * Gets decoder.
     *
     * @return the decoder
     */
    public static PEMDecoder getDecoder() {
        return new PEMDecoder();
    }

    /**
     * The type Pem encoder.
     */
    static class PEMEncoder implements Encoder<SecurityObject> {
        // XXX This is in java.security file
        // private static final String DEFAULT_ALGO = Security.getProperty("jdk.epkcs8.defaultAlgorithm");
        /**
         * The Cipher.
         */
        protected static final String DEFAULT_ALGO = "PBEWithHmacSHA256AndAES_128";

        /**
         * The Cipher.
         */
        final Cipher cipher;
        /**
         * The Algid.
         */
        final AlgorithmId algid;

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
         */
        PEMEncoder() {
            this(null,null);
        }

        /**
         * Encode string.
         *
         * @param k the k
         * @return the string
         * @throws IOException the io exception
         */
        public String encode(Key k) throws IOException {return null;};

        String pemEncoded(KeyType keyType, byte[] encoded) {
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

        public String encode(Class<SecurityObject> tClass) throws IOException {
            Objects.requireNonNull(tClass);
            return switch (tClass) {
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
                    yield pemEncoded(KeyType.ENCRYPTED_PRIVATE, e.getEncoded());
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
        }

        /**
         * Encrypt encoder.
         *
         * @param password the password
         * @return the encoder
         * @throws IOException the io exception
         */
        public PEMEncoder withEncryption(char[] password) throws IOException {
            if (cipher != null) {
                throw new IOException("Encryption cannot be used more than once");
            }
            AlgorithmId algid;
            Cipher c;
            try {
                var spec = new PBEKeySpec(password);
                // PBEKeySpec clones password
                SecretKeyFactory factory;
                factory = SecretKeyFactory.getInstance(DEFAULT_ALGO);
                c = Cipher.getInstance(DEFAULT_ALGO);
                var skey = factory.generateSecret(spec);
                c.init(Cipher.ENCRYPT_MODE, skey);
                algid = new AlgorithmId(Pem.getPBEID(DEFAULT_ALGO),
                    c.getParameters());
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
        public String build(byte[] privateBytes, byte[] publicBytes)
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
                return pemEncoded(KeyType.ENCRYPTED_PRIVATE, encoded);
            }

            // X509 only
            if (publicBytes != null && privateBytes == null) {
                return pemEncoded(KeyType.PUBLIC, publicBytes);
            }
            // PKCS8 only
            if (publicBytes == null && privateBytes != null) {
                return pemEncoded(KeyType.PRIVATE, privateBytes);
            }
            // OAS
            return pemEncoded(KeyType.PRIVATE, PKCS8Key.getEncoded(publicBytes,
                privateBytes));
        }
    }


    /**
     * PEM Decoder
     */
    static final class PEMDecoder implements Decoder<SecurityObject> {
        private static final String STARTHEADER = "-----BEGIN ";
        private static final String ENDFOOTER = "-----END ";
        final Provider factory;
        final char[] password;

        private PEMDecoder() {
            super();
            factory = null;
            password = null;
        }

        private PEMDecoder(Provider withFactory, char[] withPassword) {
            super();
            factory = withFactory;
            password = withPassword;
        }

        private SecurityObject decode(String data, String header,
            String footer) throws IOException {
            KeyType keyType;

            if (header.equalsIgnoreCase(Pem.PUBHEADER) &&
                footer.equalsIgnoreCase(Pem.PUBFOOTER)) {
                keyType = KeyType.PUBLIC;
            } else if (header.startsWith(Pem.PKCS8HEADER) &&
                footer.equalsIgnoreCase(Pem.PKCS8FOOTER)) {
                keyType = KeyType.PRIVATE;
            } else if (header.startsWith(Pem.PKCS8ENCHEADER) &&
                footer.equalsIgnoreCase(Pem.PKCS8ENCFOOTER)) {
                keyType = KeyType.ENCRYPTED_PRIVATE;
            } else if (header.startsWith(Pem.CERTHEADER) &&
                footer.equalsIgnoreCase(Pem.CERTFOOTER)) {
                keyType = KeyType.CERTIFICATE;
            } else if (header.startsWith(Pem.CRLHEADER) &&
                footer.equalsIgnoreCase(Pem.CRLFOOTER)) {
                keyType = KeyType.CRL;
            } else {
                return null;
            }

            if (password != null) {
                if (keyType != KeyType.ENCRYPTED_PRIVATE) {
                    throw new IOException("Decoder configured only for " +
                        "encrypted PEM.");
                }
            }

            Base64.Decoder decoder = Base64.getDecoder();

            switch (keyType) {
                case PUBLIC -> {
                        return X509Key.parseKey(decoder.decode(data));
                }
                case PRIVATE -> {
                    try {
                        PKCS8Key p8key = new PKCS8Key(decoder.decode(data));
                        if (p8key.getPubKeyEncoded() != null) {
                            return new KeyPair(
                                X509Key.parseKey(p8key.getPubKeyEncoded()),
                                PKCS8Key.parseKey(p8key.getEncoded()));
                        }
                        return PKCS8Key.parseKey(p8key.getEncoded());
                    } catch (InvalidKeyException e) {
                        throw new IOException(e);
                    }
                }
                case ENCRYPTED_PRIVATE -> {
                    if (password == null) {
                        return new EncryptedPrivateKeyInfo(decoder.decode(data));
                    }
                    return new EncryptedPrivateKeyInfo(decoder.decode(data)).
                        getKey(password);
                }
                case CERTIFICATE -> {
                    try {
                        var cf = CertificateFactory.getInstance("X509");
                        return cf.generateCertificate(
                            new ByteArrayInputStream((header + "\n" + data +
                                footer + "\n").getBytes()));
                    } catch (CertificateException e) {
                        throw new IOException(e);
                    }
                }
                case CRL -> {
                    try {
                        var cf = CertificateFactory.getInstance("X509");
                        return cf.generateCRL(new ByteArrayInputStream((header +
                            "\n" + data + footer + "\n").getBytes()));
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                }
                default -> throw new IOException("Unsupported type or not " +
                    "properly formatted PEM");
            }
        }

        /**
         * Creates a Decoder that only uses a Key, Certificate, or CRL factory
         * from the given Provider.
         *
         * @param provider the Factory provider this decoder will only use.
         * @return the decoder
         */
        public PEMDecoder withFactoryFrom(Provider provider) {
            return new PEMDecoder(provider, password);
        }

        /**
         * With password decoder.
         *
         * @param password the password
         * @return the decoder
         */
        public PEMDecoder withDecryption(char[] password) {
            return new PEMDecoder(factory, password);
        }

        /**
         * Decode PEM data.
         *
         * @param str PEM data in a String
         * @return an Object that is contained in the PEM data
         * @throws IOException the io exception
         */
        @Override
        public SecurityObject decode(String str) throws IOException {
            return decode(new StringReader(str));
        }

        /**
         * Decode PEM data.
         *
         * @param reader PEM data from a Reader
         * @return an Object that is contained in the PEM data
         * @throws IOException the io exception
         */
        @Override
        public SecurityObject decode(Reader reader) throws IOException {
            BufferedReader br = new BufferedReader(reader);
            StringBuilder sb = new StringBuilder(64);
            char c;
            int hyphen = 0;


            // Find starting hyphens
            while (hyphen != 5) {
                if ((char) br.read() == '-') {
                    hyphen++;
                } else {
                    hyphen = 0;
                }
            }
            hyphen = 0;
            sb.append("-----");

            // Complete header by looking for the end of the hyphens
            while (hyphen != 5) {
                if ((c = (char) br.read()) == '-') {
                    hyphen++;
                } else {
                    hyphen = 0;
                }
                sb.append(c);
            }

            hyphen = 0;
            String header = sb.toString();
            if (!header.startsWith(STARTHEADER)) {
                throw new IOException("Proper header not found.");
            }
            sb = new StringBuilder(1024);
            // Read data until we find the hyphens for END
            while (hyphen != 5) {
                c = (char) br.read();
                if (c == '\n' || c =='\r') {
                    continue;
                }
                if (c == '-') {
                    hyphen++;
                } else {
                    hyphen = 0;
                }
                sb.append(c);
            }

            hyphen = 0;
            String data = sb.substring(0, sb.length() - 5);
            sb = new StringBuilder(80);
            sb.append("-----");
            // Complete header by looking for the end of the hyphens
            while (hyphen != 5) {
                if ((c = (char) br.read()) == '-') {
                    hyphen++;
                } else {
                    hyphen = 0;
                }
                sb.append(c);
            }

            String footer = sb.toString();
            if (!footer.startsWith(ENDFOOTER)) {
                throw new IOException("Proper header not found.");
            }
            return decode(data, header, footer);
        }

        @Override
        public SecurityObject decode(String string,
            Class<SecurityObject> tClass) throws IOException {
            return tClass.cast(decode(new StringReader(string)));
        }

        @Override
        public SecurityObject decode(Reader reader,
            Class<SecurityObject> tClass) throws IOException {
            return tClass.cast(decode(reader));
        }

/*
        /**
         * Decode object.
         *
         * @param data   the data
         * @param header the header
         * @param footer the footer
         * @return the object
         * @throws IOException the io exception

        protected SecurityObject decode(String data, String header, String footer) throws IOException {
            KeyType keyType;

            if (header.equalsIgnoreCase(Pem.PUBHEADER) ||
                header.startsWith(Pem.PKCS8HEADER) ||
                header.startsWith(Pem.PKCS8ENCHEADER) ||
                header.startsWith(Pem.CERTHEADER) ||
                header.startsWith(Pem.CRLHEADER)) {
                return new PEMDecoder().decode(data, header, footer);
            }

            throw new IOException("Unknown header format");
        }
*/
    }
}