package java.security.spec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

import sun.nio.cs.ISO_8859_1;
import sun.security.util.*;
import sun.security.x509.AlgorithmId;

/**
 * The type Pem encoded key spec to and from pkcs8/x509
 */
public class PEMEncodedKeySpec extends EncodedKeySpec {
    /**
     * Instantiates a new Pem encoded key spec.
     *
     * @param encodedKey the encoded key
     */

    private EncodedKeySpec eks;
    private byte[] pemEncoding;
    /**
     * Instantiates a new Pem encoded key spec.
     *
     * @param encodedKey the encoded key
     * @throws IOException the io exception
     */
    public PEMEncodedKeySpec(byte[] encodedKey) throws IOException{
        super(encodedKey);
        eks = decode(encodedKey);
    }

    /**
     * Instantiates a new Pem encoded key spec.
     *
     * @param pem the pem
     * @throws IOException the io exception
     */
    public PEMEncodedKeySpec(String pem) throws IOException {
        this(pem.getBytes());
    }

    /**
     * Instantiates a new Pem encoded key spec.
     *
     * @param key the key
     * @throws IOException the io exception
     */
    public PEMEncodedKeySpec(Key key) throws IOException {
        super(encode(key), key.getAlgorithm());
        eks = null;
    }

    /**
     * Returns the name of the encoding format associated with this
     * key specification.
     *
     * @return a string representation of the encoding format.
     */
    @Override
    public String getFormat() {
        return "PEM";
    }

    /**
     * Returns the encoded key.
     *
     * @return the encoded key. Returns a new array each time
     * this method is called.
     */

    // XXX Can OAS being done with interface methods, or does OAS need to have special methods
    static byte[] encode(Key key) throws IOException {
        Base64.Encoder encoder = Base64.getMimeEncoder();
        ByteArrayOutputStream pembuf = new ByteArrayOutputStream(100);
        if (key instanceof PrivateKey) {
            pembuf.write(PKCS8HEADER);
        } else if (key instanceof PublicKey) {
            pembuf.write(PUBHEADER);
        } else {
            throw new IOException("invalid key: " + key);
        }

        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        pembuf.write(encoder.encode(key.getEncoded()));
        pembuf.write(0x0d); // /r
        pembuf.write(0x0a); // /n
        if (key instanceof PrivateKey) {
            pembuf.write(PKCS8FOOTER);
        } else if (key instanceof PublicKey) {
            pembuf.write(PUBFOOTER);
        }
        //pembuf.write(0x0d); // /r
        //pembuf.write(0x0a); // /n

        return pembuf.toByteArray();
    }

    @Override
    public byte[] getEncoded() {
        if (eks == null) {
            return super.getEncoded();
        }
        return eks.getEncoded();
    }



    // For encoding?? decoding??-- Max's idea/comment
    //public static PEMEncodedKeySpec from(EncodedKeySpec eks) {;}

    private static final byte[] DASHES = "-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] STARTHEADER = "-----BEGIN ".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PUBHEADER = "-----BEGIN PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PUBFOOTER = "-----END PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8HEADER = "-----BEGIN PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8FOOTER = "-----END PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8ECHEADER = "-----BEGIN EC PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8ECFOOTER = "-----END EC PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);

    private static Base64.Decoder decoder = null;

    /**
     * With the given PEM encodedBytes, decode and return a EncodedKeySpec that matches decoded data format.  If
     * the decoded PEM format is not recognized an exception will be thrown.
     *
     * @param encodedBytes the encoded bytes
     * @return the EncodedKeySpec
     * @throws IOException On format error or unrecognized formats
     */
    @Override
    public EncodedKeySpec decode(byte[] encodedBytes) throws IOException {

        byte[] data = super.getEncoded();

        // Find the PEM between the header and footer
        int endHeader = find(data, STARTHEADER.length, DASHES);
        int startFooter = findReverse(data, data.length - 6, DASHES);
        if (endHeader == -1 || startFooter == -1) {
            throw new IOException("Invalid PEM format");
        }

        decoder = Base64.getMimeDecoder();

        if (Arrays.compare(data, 0, endHeader,
                PKCS8HEADER, 0, PKCS8HEADER.length) == 0 &&
                Arrays.compare(data, startFooter, data.length,
                        PKCS8FOOTER, 0, PKCS8FOOTER.length) == 0) {
            data = decoder.decode(Arrays.copyOfRange(data, endHeader, startFooter));
            return new PKCS8EncodedKeySpec(data, KeyUtil.getAlgorithm(data).getName());

        } else if (Arrays.compare(data, 0, endHeader,
                PKCS8ENCHEADER, 0, PKCS8ENCHEADER.length) == 0 &&
                Arrays.compare(data, startFooter, data.length,
                        PKCS8ENCFOOTER, 0, PKCS8ENCFOOTER.length) == 0) {
            data = decoder.decode(Arrays.copyOfRange(data, endHeader, startFooter));
            return new PKCS8EncodedKeySpec(data, KeyUtil.getAlgorithm(data).getName());

        } else if (Arrays.compare(data, 0, endHeader,
                PUBHEADER, 0, PUBHEADER.length) == 0 &&
                Arrays.compare(data, startFooter, data.length,
                        PUBFOOTER, 0, PUBFOOTER.length) == 0) {
            data = decoder.decode(Arrays.copyOfRange(data, endHeader, startFooter));
            return new X509EncodedKeySpec(data, KeyUtil.getAlgorithm(data).getName());
        } else {
            throw new IOException("No supported format found");
        }
    }

    Key getKey(EncodedKeySpec spec) throws IOException {
      try {
          KeyFactory kf = KeyFactory.getInstance(getAlgorithm());
          return kf.generatePublic(spec);
      } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
          throw new IOException(e);
      }
    }

    /**
     * Find the occurrence of 'd' in 'a'
     *
     * @param a      the a
     * @param offset the offset
     * @param d      the d
     * @return the array index following the end of 'd'
     */
    static int find(byte[] a, int offset, byte[] d) {
        int index = offset;
        int dindex = 0;
        while (index < a.length) {
            while (a[index] == d[dindex]) {
                index++;
                if (dindex == d.length - 1) {
 //                   while (a[index] == 0x0d || a[index] == 0x0a) {
 //                       index++;
 //                   }
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
    static int findReverse(byte[] a, int offset, byte[] d) {
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

    @Override
    public String getAlgorithm() {
        if (eks == null) {
            return super.getAlgorithm();
        }
        return eks.getAlgorithm();
    }

}
