package java.security;

import jdk.internal.vm.annotation.ForceInline;
import sun.security.pkcs.PKCS8Key;
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
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * The type Pem.
 */
public class PEM {


    private PEM() {}

    /**
     * Gets encoder.
     *
     * @return the encoder
     */
    public static Encoder getEncoder() {
        return new Encoder();
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
     * fdsa
     */
    public static class Encoder {

        private enum KeyType {
            UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE, CERTIFICATE, CRL
        }

        // XXX This is in java.security file
        // private static final String DEFAULT_ALGO = Security.getProperty("jdk.epkcs8.defaultAlgorithm");
        private static final String DEFAULT_ALGO = "PBEWithHmacSHA256AndAES_128";

        /**
         * The Cipher.
         */
        Cipher cipher = null;
        /**
         * The Algid.
         */
        AlgorithmId algid = null;

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
        }

        /**
         * Encode string.
         *
         * @param k the k
         * @return the string
         * @throws IOException the io exception
         */
// PKCS8 or X509
        public String encode(Key k) throws IOException {
            Objects.requireNonNull(k);
            if (k instanceof PublicKey) {
                return build(k.getEncoded(), null);
            }
            if (k instanceof PrivateKey) {
                return build(null, k.getEncoded());
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
        public Encoder encrypt(char[] password) throws IOException {
            return encrypt(password, DEFAULT_ALGO, null, null);
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
        public Encoder encrypt(char[] password, String pbeAlgo, AlgorithmParameterSpec aps, Provider p) throws IOException {
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

            return new Encoder(cipher, algid);
        }

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
                pemEncoded(KeyType.PRIVATE, privateBytes);
            }
            // OAS
            return pemEncoded(KeyType.PRIVATE, PKCS8Key.getEncoded(publicBytes, privateBytes));
        }

        private String pemEncoded(KeyType keyType, byte[] encoded) {
            StringBuilder sb = new StringBuilder(100);
            switch (keyType) {
                case PUBLIC -> {
                    sb.append(Pem.PUBHEADER);
                    sb.append(encoded);
                    sb.append(Pem.PUBFOOTER);
                }
                case PRIVATE -> {
                    sb.append(Pem.PKCS8HEADER);
                    sb.append(encoded);
                    sb.append(Pem.PKCS8FOOTER);
                }
                case ENCRYPTED_PRIVATE -> {
                    sb.append(Pem.PKCS8ENCHEADER);
                    sb.append(encoded);
                    sb.append(Pem.PKCS8ENCFOOTER);
                }
                default -> {
                    return "";
                }
            }
            return sb.toString();
        }
    }

    /**
     * The type Decoder.
     */
    public static class Decoder {
        private static final String STARTHEADER = "-----BEGIN ";
        private static final String ENDFOOTER = "-----END ";

        /**
         * The enum Key type.
         */
        private enum KeyType {
            UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE, CERTIFICATE, CRL
        }

        private Decoder() {}

        /**
         * Decode PEM data.
         *
         * @param str PEM data in a String
         * @return an Object that is contained in the PEM data
         * @throws IOException the io exception
         */
        public Object decode(String str) throws IOException {
                return decode(new StringReader(str));
            }

        /**
         * Decode PEM data.
         *
         * @param reader PEM data from a Reader000
         * @return an Object that is contained in the PEM data
         * @throws IOException the io exception
         */
        public Object decode(Reader reader) throws IOException {
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
                if ((c = (char) br.read()) == '-') {
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

            private Object decode(String data, String header, String footer) throws IOException {
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
                        return new EncryptedPrivateKeyInfo(decoder.decode(data));
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
         * Decode t.
         *
         * @param <T>    the type parameter
         * @param str    the str
         * @param tClass the t class
         * @return the t
         * @throws IOException the io exception
         */
        public <T> T decode(String str, Class <T> tClass) throws IOException {
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
        public <T> T decode(Reader reader, Class <T> tClass) throws IOException {
            return tClass.cast(decode(reader));
        }
    }
}