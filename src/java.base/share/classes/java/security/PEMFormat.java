package java.security;

import sun.nio.cs.ISO_8859_1;
import sun.security.pkcs.PKCS8Key;
import sun.security.util.KeyUtil;
import sun.security.x509.X509Key;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;


/**
 * jfsdakl
 */
public class PEMFormat {

    /*
    class PEMData {
        final byte[] encodedBytes;

        PEMData(byte[] data) {
            encodedBytes = data.clone();
        }

    }

     */
    /**
     * The Dashs.
     */
    private static final byte[] DASHES = "-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] STARTHEADER = "-----BEGIN ".getBytes(ISO_8859_1.INSTANCE);

    /**
     * The Pubheader.
     */
    static final byte[] PUBHEADER = "-----BEGIN PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);;
    /**
     * The Pubfooter.
     */
    static final byte[] PUBFOOTER = "-----END PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);;

    /**
     * The Pkcs 8 header.
     */
    static final byte[] PKCS8HEADER = "-----BEGIN PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;
    /**
     * The Pkcs 8 footer.
     */
    static final byte[] PKCS8FOOTER = "-----END PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;

    /**
     * The Pkcs 8 encheader.
     */
    static final byte[] PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;
    /**
     * The Pkcs 8 encfooter.
     */
    static final byte[] PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;

    /**
     * The Pkcs 8 echeader.
     */
    static final byte[] PKCS8ECHEADER = "-----BEGIN BEGIN EC PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;
    /**
     * The Pkcs 8 ecfooter.
     */
    static final byte[] PKCS8ECFOOTER = "-----END EC PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;

    private byte[] encodedBytes = null;
    private EncodedKeySpec eks;
    private Key key = null;  // See constructor for why this exists
    private static final String algorithm = "PBEWithHmacSHA256AndAES_128";
    //private AlgorithmParameters algoParams;

    enum KeyType {UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE};
    KeyType keyType = KeyType.UNKNOWN;


    /**
     * Constructor for
     *
     * @param p the p
     */
    PEMFormat(PEMFormat p) {
        this.key = p.key;
        this.encodedBytes = p.encodedBytes;
    }

    /**
     * From pem format.
     *
     * @param in the in
     * @return the pem format
     * @throws IOException the io exception
     */
    public static PEMFormat from(InputStream in) throws IOException {
        return new PEMFormat(in.readAllBytes());
    }

    /**
     * From pem format.
     *
     * @param eks the data
     * @return the pem format
     * @throws IOException the io exception
     */
    public static PEMFormat from(byte[] eks) throws IOException {
        return new PEMFormat(eks);
    }

    /**
     * From pem format.
     *
     * @param data the data
     * @return the pem format
     * @throws IOException the io exception
     */
    public static PEMFormat from(String data) throws IOException {
        if (data.charAt(0) != '-') {
            throw new IOException("PEM format not detected, lacks header");
        }
        return new PEMFormat(data.getBytes().clone());
    }

    /**
     * Parse from key encoding.
     *
     * @param key the key
     * @return the pem format
     * @throws IOException the io exception
     */
    public static PEMFormat from(Key key) throws IOException {
        return new PEMFormat(key);
    }

    /**
     * From pem format.
     *
     * @param privKey the priv key
     * @param pubKey  the pub key
     * @return the pem format
     */
    public static PEMFormat from(PrivateKey privKey, PublicKey pubKey) {
        return new PEMFormat(privKey, pubKey);
    }

    /**
     * From pem format.
     *
     * @param keyPair the key pair
     * @return the pem format
     */
    public static PEMFormat from(KeyPair keyPair) {
        return new PEMFormat(keyPair.getPrivate(), keyPair.getPublic());
    }

    /**
     * Instantiates a new Pem format.
     *
     * @param data the data
     * @throws IOException the io exception
     */
    PEMFormat(byte[] data) throws IOException {
        encodedBytes = data;
    }

    /**
     * Instantiates a new Pem format.
     *
     * @param data    the data
     * @param keyType the key type
     * @throws IOException the io exception
     */
    PEMFormat(byte[] data, KeyType keyType) throws IOException {
        encodedBytes = data;
        this.keyType = keyType;
    }

    /**
     * Instantiates a new Pem format.
     *
     * @param eks the eks
     * @throws IOException the io exception
     */
    PEMFormat(EncodedKeySpec eks) throws IOException {
        this.eks = eks;

        if (eks instanceof X509EncodedKeySpec) {
            keyType = KeyType.PUBLIC;
            encodedBytes = eks.getEncoded();
        } else if (eks instanceof PKCS8EncodedKeySpec) {
            keyType = KeyType.PRIVATE;
            encodedBytes = eks.getEncoded();
        } else {
            throw new IOException("Unknown EncodedKeySpec");
        }
    }

    /**
     * Instantiates a new Pem format.
     *
     * The reason we are keeping 'key' in this case is because
     * EncryptedPrivateKeyInfo(byte[]) is only for decoding.  I haven't found
     * a way to use it for encoding without cause an incompatible change when
     * decoding
     *
     * @param key the key
     */
    PEMFormat(Key key) throws IOException {
        this.key = key;
        encodedBytes = key.getEncoded();
        if (key instanceof PrivateKey) {
            keyType = KeyType.PRIVATE;
        } else {
            keyType = KeyType.PUBLIC;
        }
    }

    /**
     * Instantiates a new Pem format.
     *
     * @param privateKey the private key
     * @param publicKey  the public key
     */
    PEMFormat(PrivateKey privateKey, PublicKey publicKey) {
        // eks = new OASEncodedKeySpec()
    }

    private byte[] decodeEncrypted(byte[] encodedBytes, char[] password) throws IOException {

        // Find the PEM between the header and footer
        int endHeader = find(encodedBytes, STARTHEADER.length, DASHES);
        int startFooter = findReverse(encodedBytes, encodedBytes.length - 6, DASHES);
        if (endHeader == -1 || startFooter == -1) {
            return null;
        }

        if (Arrays.compare(encodedBytes, 0, endHeader,
            PKCS8ENCHEADER, 0, PKCS8ENCHEADER.length) == 0 &&
            Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                PKCS8ENCFOOTER, 0, PKCS8ENCFOOTER.length) == 0) {

            ByteArrayOutputStream pembuf = new ByteArrayOutputStream(100);
            byte[] data = Base64.getMimeDecoder().decode(
                Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
            data = p8Decrypt(data, password);
            // Rewrite as an unencrypted private key
            pembuf.write(PKCS8HEADER);
            pembuf.write(0x0d); // /r
            pembuf.write(0x0a); // /n
            pembuf.write(Base64.getMimeEncoder().encode(data));
            pembuf.write(0x0d); // /r
            pembuf.write(0x0a); // /n
            pembuf.write(PKCS8FOOTER);

            return pembuf.toByteArray();
        }
        throw new IOException("Invalid header or footer");
    }

    /**
     * With the given PEM encodedBytes, decode and return a EncodedKeySpec that matches decoded data format.  If
     * the decoded PEM format is not recognized an exception will be thrown.
     *
     * @return the EncodedKeySpec
     * @throws IOException On format error or unrecognized formats
     */
    private EncodedKeySpec decode(byte[] encodedBytes) throws IOException {

        // Find the PEM between the header and footer
        int endHeader = find(encodedBytes, STARTHEADER.length, DASHES);
        int startFooter = findReverse(encodedBytes, encodedBytes.length - 6, DASHES);
        if (endHeader == -1 || startFooter == -1) {
            throw new IOException("Invalid PEM format");
        }

        if (Arrays.compare(encodedBytes, 0, endHeader,
                PKCS8HEADER, 0, PKCS8HEADER.length) == 0 &&
                Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                        PKCS8FOOTER, 0, PKCS8FOOTER.length) == 0) {
            encodedBytes = Base64.getMimeDecoder().decode(
                    Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
            return new PKCS8EncodedKeySpec(encodedBytes,
                    KeyUtil.getAlgorithm(encodedBytes).getName());

        } else if (Arrays.compare(encodedBytes, 0, endHeader,
            PKCS8ENCHEADER, 0, PKCS8ENCHEADER.length) == 0 &&
            Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                PKCS8ENCFOOTER, 0, PKCS8ENCFOOTER.length) == 0) {
            throw new IOException("Encrypted Private key must be decrypted");
            /*
            encodedBytes = Base64.getMimeDecoder().decode(
                    Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
            return p8Decrypt(encodedBytes);

         */

        } else if (Arrays.compare(encodedBytes, 0, endHeader,
                PUBHEADER, 0, PUBHEADER.length) == 0 &&
                Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                        PUBFOOTER, 0, PUBFOOTER.length) == 0) {
            encodedBytes = Base64.getMimeDecoder().decode(
                    Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
            return new X509EncodedKeySpec(encodedBytes,
                    KeyUtil.getAlgorithm(encodedBytes).getName());
        }

            throw new IOException("No supported format found");
    }

    private EncodedKeySpec decode(String data) throws IOException {
        return decode(data.getBytes(ISO_8859_1.INSTANCE));
    }


    /**
     * Returns the encoded key.
     *
     * @return the encoded key. Returns a new array each time
     * this method is called.
     */

    // XXX Can OAS being done with interface methods, or does OAS need to have special methods
    private byte[] encode(EncodedKeySpec eks, char[] password, String algorithm,
        AlgorithmParameterSpec aps) throws IOException {

        Base64.Encoder encoder = Base64.getMimeEncoder();
        ByteArrayOutputStream pembuf = new ByteArrayOutputStream(100);
        byte[] footer;
        byte[] data;

        // Header
        if (keyType == KeyType.ENCRYPTED_PRIVATE) {
            pembuf.write(PKCS8ENCHEADER);
            footer = PKCS8ENCFOOTER;
        } else if (keyType == KeyType.PRIVATE) {
            pembuf.write(PKCS8HEADER);
            footer = PKCS8FOOTER;
        } else {
            pembuf.write(PUBHEADER);
            footer = PUBFOOTER;
        }

        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(encoder.encode(encodedBytes));
        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(footer);


        return pembuf.toByteArray();
    }

    // XXX Can OAS being done with interface methods, or does OAS need to have special methods
    private byte[] encode() throws IOException {

        Base64.Encoder encoder = Base64.getMimeEncoder();
        ByteArrayOutputStream pembuf = new ByteArrayOutputStream(100);
        byte[] footer;

        // Header
        if (keyType == KeyType.ENCRYPTED_PRIVATE) {
            pembuf.write(PKCS8ENCHEADER);
            footer = PKCS8ENCFOOTER;
        } else if (keyType == KeyType.PRIVATE) {
            pembuf.write(PKCS8HEADER);
            footer = PKCS8FOOTER;
        } else {
            pembuf.write(PUBHEADER);
            footer = PUBFOOTER;
        }

        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(encoder.encode(encodedBytes));
        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(footer);


        return pembuf.toByteArray();
    }
/*

// REMOVED:  Because encoding a DER byte[] doesn't seem necessary.  Users should
// be using a Key.  It also complicates the code more that necessary
    private byte[] encode(byte[] data) throws IOException {

        Base64.Encoder encoder = Base64.getMimeEncoder();
        ByteArrayOutputStream pembuf = new ByteArrayOutputStream(100);
        byte[] footer;

        // Header
        pembuf.write(PKCS8ENCHEADER);
        footer = PKCS8ENCFOOTER;
        // XXX do encryption


        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(encoder.encode(data));
        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(footer);


        return pembuf.toByteArray();
    }
*/
    /**
     * Gets PEM encoding
     *
     * @return the key spec
     * @throws IOException the io exception
     */
    public byte[] getEncoded() throws IOException {
        /*
        if (key == null) {
            throw new IOException("Encoding requires a Key");
            //return encode(encodedBytes);
        }

         */
        return encode();
    }

    @Override
    public String toString() {
        try {
            return new String(getEncoded());
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Gets key.
     *
     * @param p the p
     * @return the key
     * @throws IOException the io exception
     */
    public Key getKey(Provider p) throws IOException {
        EncodedKeySpec eks = (key == null ? decode(encodedBytes) : decode(key.getEncoded()));
        try {
            KeyFactory kf;
            if (p == null) {
                kf = KeyFactory.getInstance(eks.getAlgorithm());
            } else {
                kf = KeyFactory.getInstance(eks.getAlgorithm(), p);
            }
            if (eks instanceof PKCS8EncodedKeySpec) {
                return kf.generatePrivate(eks);
            } else {
                return kf.generatePublic(eks);
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Gets key.
     *
     * @return the key
     * @throws IOException the io exception
     */
    public Key getKey() throws IOException {
        return getKey(null);
    }


    /**
     * Decrypt pem format.
     *
     * @param password the password
     * @return the pem format
     * @throws IOException the io exception
     */
    public PEMFormat decrypt(char[] password) throws IOException {
        return new PEMFormat(decodeEncrypted(encodedBytes, password), KeyType.PRIVATE);
    }

    /**
     * Encrypt pem format.
     *
     * @param password the password
     * @return the pem format
     * @throws IOException the io exception
     */
    public PEMFormat encrypt(char[] password) throws IOException {
        return encrypt(password, algorithm, null);
    }

    /**
     * Encrypt pem format.
     *
     * @param password  the password
     * @param algorithm the algorithm
     * @param aps       the aps
     * @return the pem format
     * @throws IOException the io exception
     */
    public PEMFormat encrypt(char[] password, String algorithm,
        AlgorithmParameterSpec aps) throws IOException {
        if (keyType != KeyType.PRIVATE) {
            throw new IOException("Encryption can only happen on Private Keys");
        }
        byte[] encoded = new EncryptedPrivateKeyInfo(key).
            encryptKey(algorithm, password, aps);
        return new PEMFormat(encoded, KeyType.ENCRYPTED_PRIVATE);
    }

    private byte[] p8Decrypt(byte[] data, char[] password) throws IOException {
        try {
            EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(data);
            var ap = epki.getAlgParameters();
            //Base64.getMimeDecoder().decode(data));
            PBEKeySpec pks = new PBEKeySpec(password);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(epki.getAlgName());
            SecretKey sk = skf.generateSecret(pks);
            PKCS8EncodedKeySpec keySpec = epki.getKeySpec(sk);
            return keySpec.getEncoded();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Find int.
     *
     * @param a      the a
     * @param offset the offset
     * @param d      the d
     * @return the int
     */
    private static int find(byte[] a, int offset, byte[] d) {
        int index = offset;
        int dindex = 0;
        while (index < a.length) {
            while (a[index] == d[dindex]) {
                index++;
                if (dindex == d.length - 1) {
                    return index;
                }
                dindex++;
            }
            dindex = 0;
            index++;
        }
        return -1;
    }

    /**
     * Find reverse int.
     *
     * @param a      the a
     * @param offset the offset
     * @param d      the d
     * @return the int
     */
    private static int findReverse(byte[] a, int offset, byte[] d) {
        int index = offset;
        int dindex = d.length - 1;
        while (index > 0) {
            while (a[index] == d[dindex]) {
                if (dindex == 0) {
                    return index;
                }
                index--;
                dindex--;
            }
            dindex = d.length - 1;
            index--;
        }
        return -1;
    }

    class EP8EncodedKeySpec extends EncodedKeySpec {
        EP8EncodedKeySpec(byte[] data) {
            super(data);
        }

        @Override
        public String getFormat() {
            return null;
        }
    }

}
