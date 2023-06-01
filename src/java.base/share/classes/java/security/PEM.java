package java.security;

import sun.security.pkcs.PKCS8Key;
import sun.security.rsa.RSAPrivateCrtKeyImpl;
import sun.security.rsa.RSAUtil;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
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
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * The type Pem.
 */
public class PEM {

    private enum KeyType {
        UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE, CERTIFICATE, CRL, RSAPRIVKEY
    }

    private PEM() {}

    /**
     * Gets encoder.
     *
     * @return the encoder
     */
    public static Encoder getEncoder() {
        return new PEMEncoder();
    }

    /**
     * Gets encoder.
     *
     * @param s the s
     * @return the encoder
     */
    public static Encoder getEncoder(String s) {
        if (s == null) {
            return new PEMEncoder();
        }
        if (s.equalsIgnoreCase("OpenSSL")) {
            return new OpenSSLEncoder();
        }
        return null;
    }

    /**
     * Gets decoder.
     *
     * @return the decoder
     */
    public static Decoder getDecoder() {
        return new Decoder();
    }

    /**
     * Gets encoder.
     *
     * @param s the s
     * @return the encoder
     */
    public static Decoder getDecoder(String s) {
        if (s == null) {
            return new PEMDecoder();
        }
        if (s.equalsIgnoreCase("OpenSSL")) {
            return new OpenSSLDecoder();
        }
        return null;
    }

    /**
     * fdsa
     */
     public abstract static class Encoder {

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
        Encoder(Cipher c, AlgorithmId a) {
            cipher = c;
            algid = a;
        }

        /**
         * Instantiates a new Encoder.
         */
        private Encoder() {
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
                case RSAPRIVKEY -> {
                    sb.append(Pem.PKCS1HEADER);
                    sb.append(e.encodeToString(encoded));
                    sb.append(Pem.PKCS1FOOTER);
                }
                default -> {
                    return "";
                }
            }
            return sb.toString();
        }


        /**
         * Encrypt encoder.
         *
         * @param password the password
         * @return the encoder
         * @throws IOException if error occurs during Encoder construction
         */
        public Encoder withEncryption(char[] password) throws IOException {
            throw new IOException("Encryption not supported");
        }
    }

    /**
     * The type Pem encoder.
     */
    static class PEMEncoder extends Encoder {
        PEMEncoder() {}

        /**
         * Instantiates a new Pem encoder.
         *
         * @param cipher the cipher
         * @param algid  the algid
         */
        private PEMEncoder(Cipher cipher, AlgorithmId algid) {
            super(cipher, algid);
        }

        /**
         * Encode string.
         *
         * @param k the k
         * @return the string
         * @throws IOException the io exception
         */
// PKCS8 or X509
        @Override
        public String encode(Key k) throws IOException {
            Objects.requireNonNull(k);
            if (k instanceof PublicKey) {
                return build(null, k.getEncoded());
            }
            if (k instanceof PrivateKey) {
                return build(k.getEncoded(), null);
            }
            throw new IOException("Invalid Key type used.");
        }

        /**
         * Encode string.
         *
         * @param kp the kp
         * @return the string
         * @throws IOException the io exception
         */
// OAS
        public String encode(KeyPair kp) throws IOException {
            Objects.requireNonNull(kp);
            PublicKey pubKey = kp.getPublic();
            PrivateKey privKey = kp.getPrivate();
            if (pubKey == null) {
                throw new IOException("KeyPair does not contain PublicKey.");
            }

            if (privKey == null) {
                throw new IOException("KeyPair does not contain PrivateKey.");
            }

            return build(pubKey.getEncoded(), privKey.getEncoded());
        }

        /**
         * Encode string.
         *
         * @param eks the eks
         * @return the string
         * @throws IOException the io exception
         */
// PKCS8 or X509
        public String encode(EncodedKeySpec eks) throws IOException {
            if (eks instanceof X509EncodedKeySpec) {
                return build(null, eks.getEncoded());
            }

            if (eks instanceof PKCS8EncodedKeySpec) {
                return build(eks.getEncoded(), null);
            }

            throw new IOException("PKCS8EncodedKeySpec or X509EncodedKeySpec" +
                " required.");
        }

        /**
         * Encode string.
         *
         * @param ekpi the ekpi
         * @return the string
         * @throws IOException the io exception
         */
        public String encode(EncryptedPrivateKeyInfo ekpi) throws IOException {
            Objects.requireNonNull(ekpi);
            if (cipher != null) {
                throw new IOException("encrypt was incorrectly used");
            }
            return pemEncoded(KeyType.ENCRYPTED_PRIVATE, ekpi.getEncoded());
        }

        /**
         * Encode string.
         *
         * @param cert the cert
         * @return the string
         * @throws IOException the io exception
         */
        public String encode(Certificate cert) throws IOException {
            Objects.requireNonNull(cert);

            StringBuffer sb = new StringBuffer(512);
            sb.append(Pem.CERTHEADER);
            try {
                sb.append(Base64.getEncoder().encode(cert.getEncoded()));
            } catch (CertificateException e) {
                throw new IOException(e);
            }
            sb.append(Pem.CERTFOOTER);
            return sb.toString();
        }

        /**
         * Encode string.
         *
         * @param crl the crl
         * @return the string
         * @throws IOException the io exception
         */
        public String encode(X509CRL crl) throws IOException {
            Objects.requireNonNull(crl);

            StringBuffer sb = new StringBuffer(512);
            sb.append(Pem.CRLHEADER);
            try {
                sb.append(Base64.getEncoder().encode(crl.getEncoded()));
            } catch (CRLException e) {
                throw new IOException(e);
            }
            sb.append(Pem.CRLFOOTER);
            return sb.toString();
        }


        /**
         * Encrypt encoder.
         *
         * @param password the password
         * @return the encoder
         * @throws IOException the io exception
         */
        public Encoder withEncryption(char[] password) throws IOException {
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
         * Encrypt encoder.
         *
         * @param password the password
         * @param pbeAlgo  the pbe algo
         * @param aps      the aps
         * @param p        the p
         * @return the encoder
         * @throws IOException the io exception
         */
        /*
        public Encoder withPassword(char[] password, String pbeAlgo, AlgorithmParameterSpec aps, Provider p) throws IOException {
            if (cipher != null) {
                throw new IOException("Encryption cannot be used more than once");
            }
            AlgorithmId algid;
            try {
                var spec = new PBEKeySpec(password);
                // PBEKeySpec clones password
                SecretKeyFactory factory;
                if (p == null) {
                    factory = SecretKeyFactory.getInstance(pbeAlgo);
                    cipher = Cipher.getInstance(pbeAlgo);
                } else {
                    factory = SecretKeyFactory.getInstance(pbeAlgo, p);
                    cipher = Cipher.getInstance(pbeAlgo, p);
                }
                var skey = factory.generateSecret(spec);
                cipher.init(Cipher.ENCRYPT_MODE, skey, aps);
                algid = new AlgorithmId(Pem.getPBEID(pbeAlgo),
                    cipher.getParameters());
            } catch (Exception e) {
                throw new IOException(e);
            }

            return new PEMEncoder(cipher, algid);
        }
         */

        /**
         * Build string.
         *
         * @param privateBytes the private bytes
         * @param publicBytes  the public bytes
         * @return the string
         * @throws IOException the io exception
         */
        public String build(byte[] privateBytes, byte[] publicBytes) throws IOException {
            DerOutputStream out = new DerOutputStream();
            byte[] encoded;

            // Encrypted PKCS8
            if (cipher != null) {
                if (privateBytes == null || publicBytes != null) {
                    throw new IOException("Only a PrivateKey can be encrypted.");
                }
                try {
                    algid.encode(out);
                    out.putOctetString(cipher.doFinal(privateBytes));
                    encoded = DerValue.wrap(DerValue.tag_Sequence, out).toByteArray();
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
            return pemEncoded(KeyType.PRIVATE, PKCS8Key.getEncoded(publicBytes, privateBytes));
        }

    }


    /**
     * The type Open ssl encoder.
     */
    static class OpenSSLEncoder extends Encoder {
        /**
         * Instantiates a new Open ssl encoder.
         */
        OpenSSLEncoder() {
            super();
        }

        @Override
        public String encode(Key k) throws IOException {
            return pemEncoded(KeyType.RSAPRIVKEY,
                ((RSAPrivateCrtKeyImpl) k).getPrivKeyMaterial());
        }
/*
        private void decode(InputStream is) throws InvalidKeyException {
            DerValue val = null;
            try {
                val = new DerValue(is);
                if (val.tag != DerValue.tag_Sequence) {
                    throw new InvalidKeyException("invalid key format");
                }

                // Support check for V1, aka 0, and V2, aka 1.
                version = val.data.getInteger();
                if (version != V1) {
                    throw new InvalidKeyException("unknown version: " + version);
                }
                // Store key material for subclasses to parse
                privKeyMaterial = val.data.getOctetString();

                // PKCS8 v1 typically ends here
                if (val.data.available() == 0) {
                    return;
                }

                 // OPTIONAL Context tag 0 for Attributes for PKCS8 v1 & v2
                var result =
                    val.data.getOptionalImplicitContextSpecific(0,
                        DerValue.tag_Sequence);
                if (result.isPresent()) {
                    attributes = new DerInputStream(result.get().getDataBytes()).toByteArray();
                    //attributes = result.get().data.getSequence(0)''
                    if (val.data.available() == 0) {
                        return;
                    }
                }

                // OPTIONAL context tag 1 for Public Key for PKCS8 v2 only
                if (version == V2) {
                    System.err.println("writing pub");
                    result = val.data.getOptionalImplicitContextSpecific(1,
                        DerValue.tag_BitString);
                    if (result.isPresent()) {
                        // Store public key material for later parsing
                        pubKeyEncoded = new X509Key(algid,
                            result.get().getUnalignedBitString()).getEncoded();
                    }
                }

                if (val.data.available() != 0) {
                    throw new InvalidKeyException("Extra bytes");
                }
            } catch (Exception e) {
                throw new InvalidKeyException("IOException : " + e.getMessage());
            } finally {
                if (val != null) {
                    val.clear();
                }
            }
     }
 */
    }

    /**
     * The type Decoder.
     */
    public static class Decoder {
        static final String STARTHEADER = "-----BEGIN ";
        static final String ENDFOOTER = "-----END ";

        private Decoder() {}
        /**
         * The enum Key type.
         */
        /*
        private enum KeyType {
            UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE, CERTIFICATE, CRL
        }

         */

        /**
         * Decode PEM data.
         *
         * @param str PEM data in a String
         * @return an Object that is contained in the PEM data
         * @throws IOException the io exception
         */
        public PEMable decode(String str) throws IOException {
            return decode(new StringReader(str));
        }

        /**
         * Decode PEM data.
         *
         * @param reader PEM data from a Reader
         * @return an Object that is contained in the PEM data
         * @throws IOException the io exception
         */
        public PEMable decode(Reader reader) throws IOException {
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
            sb = new StringBuilder(4096);
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

        /**
         * Decode object.
         *
         * @param data   the data
         * @param header the header
         * @param footer the footer
         * @return the object
         * @throws IOException the io exception
         */
        protected PEMable decode(String data, String header, String footer) throws IOException {
            KeyType keyType;

            if (header.equalsIgnoreCase(Pem.PUBHEADER) ||
                header.startsWith(Pem.PKCS8HEADER) ||
                header.startsWith(Pem.PKCS8ENCHEADER) ||
                header.startsWith(Pem.CERTHEADER) ||
                header.startsWith(Pem.CRLHEADER)) {
                return new PEMDecoder().decode(data, header, footer);
            }
            if (header.equalsIgnoreCase(Pem.PKCS1HEADER)) {
                return new OpenSSLDecoder().decode(data, header, footer);
            }

            throw new IOException("Unknown header format");
        }

        /**
         * Decode t.
         *
         * @param <T>    the type parameter
         * @param str    the str
         * @param tClass the t class
         * @return the t
         * @throws IOException the io exception
         */
        public <T extends PEMable> T decode(String str, Class <T> tClass) throws IOException {
            return tClass.cast(decode(new StringReader(str)));
        }

        /**
         * Decode t.
         *
         * @param <T>    the type parameter
         * @param reader the reader
         * @param tClass the t class
         * @return the t
         * @throws IOException the io exception
         */
        public <T extends PEMable> T decode(Reader reader, Class <T> tClass) throws IOException {
            return tClass.cast(decode(reader));
        }


        /**
         * Creates a Decoder that only uses a Key, Certificate, or CRL factory from the given Provider.
         *
         * @param provider the Factory provider this decoder will only use.
         * @return the decoder
         * @throws IOException on error of Decoder creation
         */
        public Decoder withFactory(Provider provider) throws IOException {
            return null;
        }

        /**
         * With password decoder.
         *
         * @param password the password
         * @return the decoder
         * @throws IOException on error of Decoder creation
         */
        public Decoder withDecryption(char[] password) throws IOException {
            return null;
        }

    }

    /**
     * The type Open ssl decoder.
     */
    static class OpenSSLDecoder extends Decoder {
        private OpenSSLDecoder() {super();}

        @Override
        protected PEMable decode(String data, String header, String footer) throws IOException {
            KeyType keyType;
            KeyFactory kf;
            if (header.startsWith(Pem.PKCS1HEADER) && footer.equalsIgnoreCase(Pem.PKCS1FOOTER)) {
                keyType = KeyType.PRIVATE;
                try {
                    //kf = KeyFactory.getInstance("RSA");
                    return RSAPrivateCrtKeyImpl.newKey(RSAUtil.KeyType.RSA, "PKCS#1",
                        Base64.getDecoder().decode(data));
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }

            return null;
        }
    }

    /**
     * The type Pem decoder.
     */
    static final class PEMDecoder extends Decoder {

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

        protected PEMable decode(String data, String header, String footer) throws IOException {
            KeyType keyType;

            if (header.equalsIgnoreCase(Pem.PUBHEADER) && footer.equalsIgnoreCase(Pem.PUBFOOTER)) {
                keyType = KeyType.PUBLIC;
            } else if (header.startsWith(Pem.PKCS8HEADER) && footer.equalsIgnoreCase(Pem.PKCS8FOOTER)) {
                keyType = KeyType.PRIVATE;
            } else if (header.startsWith(Pem.PKCS8ENCHEADER) && footer.equalsIgnoreCase(Pem.PKCS8ENCFOOTER)) {
                keyType = KeyType.ENCRYPTED_PRIVATE;
            } else if (header.startsWith(Pem.CERTHEADER) && footer.equalsIgnoreCase(Pem.CERTFOOTER)) {
                keyType = KeyType.CERTIFICATE;
            } else if (header.startsWith(Pem.CRLHEADER) && footer.equalsIgnoreCase(Pem.CRLFOOTER)) {
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
                    return new EncryptedPrivateKeyInfo(decoder.decode(data)).getKey(password);
                }
                case CERTIFICATE -> {
                    try {
                        var cf = CertificateFactory.getInstance("X509");
                        return cf.generateCertificate(new ByteArrayInputStream((header + "\n" + data + footer + "\n").getBytes()));
                    } catch (CertificateException e) {
                        throw new IOException(e);
                    }
                }
                case CRL -> {
                    try {
                        var cf = CertificateFactory.getInstance("X509");
                        return cf.generateCRL(new ByteArrayInputStream((header + "\n" + data + footer + "\n").getBytes()));
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                }
                default -> throw new IOException("Unsupported type or not properly formatted PEM");

            }
        }

        /**
         * Creates a Decoder that only uses a Key, Certificate, or CRL factory from the given Provider.
         *
         * @param provider the Factory provider this decoder will only use.
         * @return the decoder
         */
        @Override
        public Decoder withFactoryFrom(Provider provider) throws IOException {
            return new PEMDecoder(provider, password);
        }

        /**
         * With password decoder.
         *
         * @param password the password
         * @return the decoder
         */
        @Override
        public Decoder withDecryption(char[] password) throws IOException{
            return new PEMDecoder(factory, password);
        }
    }
}