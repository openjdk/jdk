package java.security;

import sun.nio.cs.ISO_8859_1;
import sun.security.pkcs.PKCS8Key;
import sun.security.util.*;
import sun.security.x509.X509Key;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.spec.*;
import java.util.*;


/**
 * jfsdakl
 */
public class PEMFormat {

    /**
     * The Dashs.
     */
    private static final byte[] DASHES = "-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] STARTHEADER = "-----BEGIN ".getBytes(ISO_8859_1.INSTANCE);

    /**
     * Public Key PEM header & footer
     */
    static final byte[] PUBHEADER = "-----BEGIN PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);
    static final byte[] PUBFOOTER = "-----END PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);

    /**
     * Private Key PEM header & footer
     */
    static final byte[] PKCS8HEADER = "-----BEGIN PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    static final byte[] PKCS8FOOTER = "-----END PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);

    /**
     * Encrypted Private Key PEM header & footer
     */
    static final byte[] PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    static final byte[] PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);

    private static final String DEFAULT_ALGO = "PBEWithHmacSHA256AndAES_128";

    enum KeyType {UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE};

    private PEMData pemData;
    private PEMData pemDataOAS; // Only used for Public Key with OneAsymmetricKey

    /**
     * Constructor
     */

     /**
      * Create a new PEMFormat from this one
      *
      * @param p the p
      */
    PEMFormat(PEMFormat p) {
        //this.key = p.key;
        //this.encodedBytes = p.encodedBytes;
        pemData = new PEMData(p.pemData.data(), p.pemData.algorithm(), p.pemData.keyType());

    }

    /**
     * Constructor for raw bytes, PEM or DER.
     *
     * @param data byte[] could contain PEM or DER.
     * @throws IOException the io exception
     */
    PEMFormat(byte[] data) throws IOException {
        pemData = new PEMData(data, null, null);
    }

    /**
     * Constructor used internally for encrypted and decrypted PEM files
     *
     * @param data    the data
     * @param keyType the key type
     * @throws IOException the io exception
     */
    private PEMFormat(byte[] data, KeyType keyType) throws IOException {
        pemData = new PEMData(data, null, keyType);
    }

    /**
     * Construct from an EncodedKeySpec.  Can be used for encode and decode
     * situations
     *
     * @param eks the eks
     * @throws IOException the io exception
     */
    PEMFormat(EncodedKeySpec eks) throws IOException {

        if (eks instanceof X509EncodedKeySpec) {
            pemData = new PEMData(eks.getEncoded(), eks.getAlgorithm(), KeyType.PUBLIC);
        } else if (eks instanceof PKCS8EncodedKeySpec) {
            pemData = new PEMData(eks.getEncoded(), eks.getAlgorithm(), KeyType.PRIVATE);
        } else {
            throw new IOException("Unsupported EncodedKeySpec.  Only support " +
                "X509EncodedKeySpec or PKCS8EncodedKeySpec");
        }
    }

    /**
     * Constructor when passed a key.  Used for encoding.
     *
     * @param key the key
     */
    PEMFormat(Key key) throws IOException {
        if (key instanceof PrivateKey) {
            pemData = new PEMData(key.getEncoded(), key.getAlgorithm(), KeyType.PRIVATE);
        } else if (key instanceof PublicKey) {
            pemData = new PEMData(key.getEncoded(), key.getAlgorithm(), KeyType.PUBLIC);
        }
    }

    /**
     * Instantiates a new Pem format.
     *
     * @param kp the kp
     */
    PEMFormat(KeyPair kp) {
        Key key = kp.getPrivate();
        pemData = new PEMData(key.getEncoded(), key.getAlgorithm(), KeyType.PRIVATE);
        key = kp.getPublic();
        pemDataOAS = new PEMData(key.getEncoded(), key.getAlgorithm(), KeyType.PUBLIC);
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
        byte[] pembytes = data.getBytes().clone();
        if (pembytes[0] != '-') {
            throw new IOException("PEM format not detected, lacks header");
        }
        return new PEMFormat(pembytes);
    }

    /**
     * Input an encoded key spec into PEMFormat
     *
     * @param eks the data
     * @return PEMFormat
     * @throws IOException the io exception
     */
    public static PEMFormat from(EncodedKeySpec eks) throws IOException {
        return new PEMFormat(eks);
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
     * @param pubKey  the pub key
     * @param privKey the priv key
     * @return the pem format
     */
    public static PEMFormat from(PublicKey pubKey, PrivateKey privKey) {
        return new PEMFormat(new KeyPair(pubKey, privKey));
    }

    /**
     * From pem format.
     *
     * @param keyPair the key pair
     * @return the pem format
     * @throws IOException the io exception
     */
    public static PEMFormat from(KeyPair keyPair) throws IOException {
        return new PEMFormat(keyPair);
    }

    private byte[] generatePKCS8v2() throws IOException {

        DerOutputStream out = new DerOutputStream();
        boolean beginning = true;

        out.putInteger(1); // version 2
        PKCS8Key privateKey;
        try {
            privateKey = new PKCS8Key(pemData.data());
        } catch (Exception e) {
            throw new IOException(e);
        }

        if (beginning) {
            privateKey.getAlgorithmId().encode(out);
            beginning = false;
        }

        out.putOctetString(privateKey.getPrivKeyMaterial());
        byte[] attribute = privateKey.getAttributes();
        if (attribute != null) {
            out.putTag(DerValue.TAG_CONTEXT, false, (byte)0);
            out.putDerValue(new DerValue(attribute));
        }
        if (pemDataOAS != null) {
            X509Key x = (X509Key) X509Key.parseKey(pemDataOAS.data());
            //DerOutputStream keyDER = new DerOutputStream();
            //keyDER.derEncode(x.getKey().toByteArray());
            //out.writeImplicit((byte)0x01, keyDER);  // Will be 0x81
            DerOutputStream pubOut = new DerOutputStream();
            pubOut.putUnalignedBitString(x.getKey());
            out.writeImplicit(
                DerValue.createTag(DerValue.TAG_CONTEXT, false, (byte) 1),
                pubOut);
//            out.putTaggedBitString(DerValue.TAG_CONTEXT, false, (byte)1,
//                x.getKey().toByteArray());//publicKey.getEncoded());
        }

    DerValue val = DerValue.wrap(DerValue.tag_Sequence, out);
    return val.toByteArray();
}

    private KeyPair parsePKCS8v2(Provider p, byte[] encodedBytes) throws IOException {

        PKCS8Key priKey;
        try {
            priKey = new PKCS8Key(encodedBytes);
        } catch (Exception e) {
            throw new IOException(e);
        }
        pemData = new PEMData(priKey.getEncoded(), priKey.getAlgorithm(), KeyType.PRIVATE);
        return new KeyPair(
            (PublicKey) getKey(p),
            (PrivateKey) getKey(p));
    }


    /**
     * Decode encrypted PEM data
     */
    private static byte[] decodeEncrypted(byte[] encodedBytes, char[] password) throws IOException {

        // Find the PEM between the header and footer
        int endHeader = find(encodedBytes, STARTHEADER.length, DASHES);
        int startFooter = findReverse(encodedBytes, encodedBytes.length - 6, DASHES);
        if (endHeader == -1 || startFooter == -1) {
            return null;
        }

        if (encodedBytes[0] == '-') {
            if (Arrays.compare(encodedBytes, 0, endHeader,
                PKCS8ENCHEADER, 0, PKCS8ENCHEADER.length) == 0 &&
                Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                    PKCS8ENCFOOTER, 0, PKCS8ENCFOOTER.length) == 0) {

                ByteArrayOutputStream pembuf = new ByteArrayOutputStream(100);
                encodedBytes = Base64.getMimeDecoder().decode(
                    Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
            } else {
                throw new IOException("Invalid header or footer");
            }
        }
        if (encodedBytes[0] != DerValue.tag_Sequence) {
            throw new IOException("Unknown binary formatted.");
        }

        encodedBytes = p8Decrypt(encodedBytes, password);
            /*
            // Rewrite as an unencrypted private key
            pembuf.write(PKCS8HEADER);
            pembuf.write(0x0d); // /r
            pembuf.write(0x0a); // /n
            pembuf.write(Base64.getMimeEncoder().encode(data));
            pembuf.write(0x0d); // /r
            pembuf.write(0x0a); // /n
            pembuf.write(PKCS8FOOTER);

            return pembuf.toByteArray();
             */
        return encodedBytes;
    }

    /**
     * With the given PEM encodedBytes, decode and return a EncodedKeySpec that matches decoded data format.  If
     * the decoded PEM format is not recognized an exception will be thrown.
     *
     * @return the EncodedKeySpec
     * @throws IOException On format error or unrecognized formats
     */
    private static PEMData decode(PEMData pData) throws IOException {
        KeyType kt = pData.keyType();
        byte[] encodedBytes = pData.data();

        if (encodedBytes[0] == '-') {
            /*
            int anyR = Arrays.binarySearch(encodedBytes, (byte) 0x0d);
            int anyN = Arrays.binarySearch(encodedBytes, (byte) 0x0a);
            if (anyR < 0 && anyN >= 0) {
                String s = new String(encodedBytes);
                s = s.replace("\n", "\r\n");
                encodedBytes = s.getBytes();
            }
            */

            // Find the PEM between the header and footer
            int endHeader = find(encodedBytes, STARTHEADER.length, DASHES);
            int startFooter = findReverse(encodedBytes, encodedBytes.length - 6, DASHES);
            if (endHeader == -1 || startFooter == -1) {
                throw new IOException("Invalid PEM format");
            }

            if (kt == KeyType.PRIVATE ||
                (Arrays.compare(encodedBytes, 0, endHeader,
                PKCS8HEADER, 0, PKCS8HEADER.length) == 0 &&
                Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                    PKCS8FOOTER, 0, PKCS8FOOTER.length) == 0)) {
                encodedBytes = Base64.getMimeDecoder().decode(
                    Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
                //String algo = KeyUtil.getAlgorithm(encodedBytes).getName();
                //return new PKCS8EncodedKeySpec(encodedBytes, algo);
                kt = KeyType.PRIVATE;

            } else if (kt == KeyType.PUBLIC ||
                (Arrays.compare(encodedBytes, 0, endHeader,
                    PUBHEADER, 0, PUBHEADER.length) == 0 &&
                    Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                        PUBFOOTER, 0, PUBFOOTER.length) == 0)) {
                encodedBytes = Base64.getMimeDecoder().decode(
                    Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
                //String algo = KeyUtil.getAlgorithm(encodedBytes).getName();
                //return new X509EncodedKeySpec(encodedBytes, algo);
                kt = KeyType.PUBLIC;
            } else if (Arrays.compare(encodedBytes, 0, endHeader,
                PKCS8ENCHEADER, 0, PKCS8ENCHEADER.length) == 0 &&
                Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                    PKCS8ENCFOOTER, 0, PKCS8ENCFOOTER.length) == 0) {
                throw new IOException("PEM is encrypted and must me decrypted first.");
            } else {
                throw new IOException("Unknown PEM format not supported");
            }
        }

        if (kt == null) {
            throw new IOException("Unknown Key type. (Private or Public)");
        }
        if (encodedBytes[0] != DerValue.tag_Sequence) {
            throw new IOException("Unknown binary format");
        }

        String algo;
        if (pData.algorithm() == null) {
            algo = KeyUtil.getAlgorithm(encodedBytes).getName();
        } else {
            algo = pData.algorithm();
        }

        return new PEMData(encodedBytes, algo, kt);
    }


    /**
     * Returns the encoded key.
     *
     * @return the encoded key. Returns a new array each time
     * this method is called.
     */
/*
    // XXX Can OAS being done with interface methods, or does OAS need to have special methods
    private byte[] encode(EncodedKeySpec eks, char[] password, String algorithm,
        AlgorithmParameterSpec aps) throws IOException {

        Base64.Encoder encoder = Base64.getMimeEncoder();
        ByteArrayOutputStream pembuf = new ByteArrayOutputStream(100);
        byte[] footer;
        byte[] data;

        // Header
        switch (pemData.keyType()) {
            case ENCRYPTED_PRIVATE -> {
                pembuf.write(PKCS8ENCHEADER);
                footer = PKCS8ENCFOOTER;
            }
            case PRIVATE -> {
                pembuf.write(PKCS8HEADER);
                footer = PKCS8FOOTER;
            }
            case PUBLIC -> {
                pembuf.write(PUBHEADER);
                footer = PUBFOOTER;
            }
            default -> throw new IOException("Unknown Key Type");

        }

        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(encoder.encode(pemData.data()));
        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(footer);


        return pembuf.toByteArray();
    }

 */

    // XXX Can OAS being done with interface methods, or does OAS need to have special methods
    private byte[] encode() throws IOException {
        if (pemData == null) {
            throw new IOException("No encoded data provided");
        }
        if (pemDataOAS != null) {
            return generatePKCS8v2();
        }
        return pemData.data();
    }

    private String encodeString() throws IOException {

        if (pemData == null) {
            throw new IOException("No encoded data provided");
        }
        byte[] encodedBytes = pemData.data();

        if (pemDataOAS != null) {
            encodedBytes = generatePKCS8v2();
        }

        Base64.Encoder encoder = Base64.getMimeEncoder();
        StringBuilder pembuf = new StringBuilder(100);
        byte[] footer;

        // Header
        switch (pemData.keyType()) {
            case ENCRYPTED_PRIVATE -> {
                pembuf.append(new String(PKCS8ENCHEADER));
                footer = PKCS8ENCFOOTER;
            }
            case PRIVATE -> {
                pembuf.append(new String(PKCS8HEADER));
                footer = PKCS8FOOTER;
            }
            case PUBLIC -> {
                pembuf.append(new String(PUBHEADER));
                footer = PUBFOOTER;
            }
            default -> throw new IOException("Unknown Key Type");
        }

        pembuf.append("\r\n");
        String pem = new String(encoder.encode(encodedBytes));
        pembuf.append(pem);
        pembuf.append("\r\n");
        pembuf.append(new String(footer));
        return pembuf.toString();
    }

    /**
     * Gets PEM encoding
     *
     * @return the key spec
     * @throws IOException the io exception
     */
    public byte[] getEncoded() throws IOException {
        return encode();
    }

    @Override
    public String toString() {
        try {
            return encodeString();
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
        if (!pemData.isComplete()) {
            pemData = decode(pemData);
        }
        if (pemData.keyType() == KeyType.PRIVATE) {
            return getKey(p, PrivateKey.class);
        } else if (pemData.keyType() == KeyType.PUBLIC) {
            return getKey(p, PublicKey.class);
        }
        return null;

        /*
        try {
            KeyFactory kf;
            if (p == null) {
                kf = KeyFactory.getInstance(pemData.algorithm());
            } else {
                kf = KeyFactory.getInstance(pemData.algorithm(), p);
            }
            if (pemData.keyType() == KeyType.PRIVATE) {
                return kf.generatePrivate(new PKCS8EncodedKeySpec(pemData.data(), pemData.algorithm()));
            } else {
                return kf.generatePublic(new X509EncodedKeySpec(pemData.data(), pemData.algorithm()));
            }
        } catch (Exception e) {
            throw new IOException(e);
        }

         */
    }


    /**
     * Gets keys.
     *
     * @param p the p
     * @return the keys
     * @throws IOException the io exception
     */
    public KeyPair getKeys(Provider p) throws IOException {
        return parsePKCS8v2(p, pemData.data());
    }


    /**
     * Gets key.
     *
     * @param <T> the type parameter
     * @param key the key
     * @return the key
     * @throws IOException the io exception
     */
    public <T> T getKey(Class<T> key) throws IOException {
        return getKey(null, key);
    }

    /**
     * Gets key.
     *
     * @param <T> the type parameter
     * @param p   the p
     * @param key the key
     * @return the key
     * @throws IOException the io exception
     */
    public <T> T getKey(Provider p, Class<T> key) throws IOException {
        if (!pemData.isComplete()) {
            pemData = decode(pemData);
        }
        try {
            KeyFactory kf;
            if (p == null) {
                kf = KeyFactory.getInstance(pemData.algorithm());
            } else {
                kf = KeyFactory.getInstance(pemData.algorithm(), p);
            }

            if (key.isAssignableFrom(PrivateKey.class)) {
                if (pemData.keyType() == KeyType.PRIVATE) {
                    return key.cast(kf.generatePrivate(new PKCS8EncodedKeySpec(
                        pemData.data(), pemData.algorithm())));
                }
                return null;
            }
            if (key.isAssignableFrom(PublicKey.class)) {
                if (pemData.keyType() == KeyType.PUBLIC) {
                    return key.cast(kf.generatePublic(new X509EncodedKeySpec(
                        pemData.data(), pemData.algorithm())));
                }
                if (pemData.keyType() == KeyType.PRIVATE) {
                    return key.cast(kf.generatePublic(new PKCS8EncodedKeySpec(
                        pemData.data(), pemData.algorithm())));
                }
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
            return null;
        }


    /**
     * Gets keys.
     *
     * @return the keys
     * @throws IOException the io exception
     */
    KeyPair getKeys() throws IOException {
        return parsePKCS8v2(null, pemData.data());
    }

    /**
     * Gets key.
     *
     * @return the key
     * @throws IOException the io exception
     */
    public Key getKey() throws IOException {
        return getKey((Provider) null);
    }

    /**
     * Decrypt pem format.
     *
     * @param password the password
     * @return the pem format
     * @throws IOException the io exception
     */
    public PEMFormat decrypt(char[] password) throws IOException {
        return new PEMFormat(decodeEncrypted(pemData.data(), password), KeyType.PRIVATE);
    }

    /**
     * Encrypt pem format.
     *
     * @param password the password
     * @return the pem format
     * @throws IOException the io exception
     */
    public PEMFormat encrypt(char[] password) throws IOException {
        return encrypt(password, DEFAULT_ALGO, null);
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
        if (pemData.keyType() != KeyType.PRIVATE) {
            throw new IOException("Encryption can only happen on Private Keys");
        }
        byte[] encoded = EncryptedPrivateKeyInfo.encryptKey(pemData.data(), password, algorithm, aps);
        return new PEMFormat(encoded, KeyType.ENCRYPTED_PRIVATE);
    }

    private static byte[] p8Decrypt(byte[] data, char[] password) throws IOException {
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
}

record PEMData(byte[] data, String algorithm, PEMFormat.KeyType keyType) {
//record PEMData(byte[] data, String algorithm, PEMFormat.KeyType keyType, Format format) {
    //enum Format {UNKNOWN, PEM, DER};
/*
    public PEMData {
        if (format == Format.UNKNOWN) {
            switch (data[0]) {
                case 0x30 -> format = Format.DER;
                case 0x45 -> format = Format.PEM;
            }
        }
    }

    public PEMData(byte[] data, String algorithm, PEMFormat.KeyType keyType) {
        this(data, algorithm, keyType, Format.UNKNOWN);
    }
*/
    boolean isComplete() {
        if (algorithm == null || keyType == null) {
            return false;
        }
        return true;
    }

/*    String getAlgorithm() {
        if (algoId == null) {
            return "";
        }
        return algoId.getName();
    }
*/
}


