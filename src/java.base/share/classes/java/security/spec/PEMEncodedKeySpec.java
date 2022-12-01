package java.security.spec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

import sun.nio.cs.ISO_8859_1;
import sun.security.util.*;

/**
 * This calls will encode PEM or decode PEM, depending on which constructor
 * has been used to create it.
 *
 * If the constructor was called with a Key, then that key will be encoded with
 * PEM and given the proper headers/footers.
 *
 * If the constructor was called with a byte[] or string, then that data
 * will be decoded and stored in its DER format.
 *
 * This class can only support PKCS8 and X509.
 *
 */
public class PEMEncodedKeySpec extends EncodedKeySpec {

    // Stores KeySpec after decoding. null after encoding
    private EncodedKeySpec eks;

    /**
     * Construct a PEMEncodedKeySpec that has decoded the provided encodedKey.
     * An IOException is thrown if the encodedKey cannot be decoded.
     *
     * @param encodedKey the encoded key
     * @throws IOException the io exception
     */
    public PEMEncodedKeySpec(byte[] encodedKey) throws IOException{
        super(encodedKey);
        eks = decode();
    }

    /**
     * Construct a PEMEncodedKeySpec that has decoded the provided String.
     * An IOException is thrown if the String cannot be decoded.
     *
     * @param pem the pem
     * @throws IOException the io exception
     */
    public PEMEncodedKeySpec(String pem) throws IOException {
        this(pem.getBytes());
    }

    /**
     * Construct a PEMEncodedKeySpec that has encoded the provided Key.
     * An IOException is thrown if the Key cannot be encoded.
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
    private static byte[] encode(Key key) throws IOException {
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

    /**
     * Return encoded data.  If this was a decoding, meaning the constructor
     * was called with a byte[] or string, then DER format of the key will
     * be returned.  If this was an encoding, meaning the constructor was
     * called with a Key, then this will return the PEM data in a byte[].
     *
     * @return Encoded bytes
     */
    @Override
    public byte[] getEncoded() {
        // eks is null during encoding, so get the encoding from EncodedKeySpec
        if (eks == null) {
            return super.getEncoded();
        }
        // Use the stored EncodedKeySpec to retrieve the decoded data
        return eks.getEncoded();
    }

    private static final byte[] DASHES = "-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] STARTHEADER = "-----BEGIN ".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PUBHEADER = "-----BEGIN PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PUBFOOTER = "-----END PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8HEADER = "-----BEGIN PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8FOOTER = "-----END PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] OPENSSLECHEADER = "-----BEGIN EC PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);
    private static final byte[] OPENSSLECFOOTER = "-----END EC PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);

    private static Base64.Decoder decoder = null;

    /**
     * With the given PEM encodedBytes, decode and return a EncodedKeySpec that matches decoded data format.  If
     * the decoded PEM format is not recognized an exception will be thrown.
     *
     * @return the EncodedKeySpec
     * @throws IOException On format error or unrecognized formats
     */

    private EncodedKeySpec decode() throws IOException {

        byte[] encodedBytes = super.getEncoded();

        // Find the PEM between the header and footer
        int endHeader = find(encodedBytes, STARTHEADER.length, DASHES);
        int startFooter = findReverse(encodedBytes, encodedBytes.length - 6, DASHES);
        if (endHeader == -1 || startFooter == -1) {
            throw new IOException("Invalid PEM format");
        }

        decoder = Base64.getMimeDecoder();

        if (Arrays.compare(encodedBytes, 0, endHeader,
                PKCS8HEADER, 0, PKCS8HEADER.length) == 0 &&
                Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                        PKCS8FOOTER, 0, PKCS8FOOTER.length) == 0) {
            encodedBytes = decoder.decode(Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
            return new PKCS8EncodedKeySpec(encodedBytes, KeyUtil.getAlgorithm(encodedBytes).getName());

        } else if (Arrays.compare(encodedBytes, 0, endHeader,
                PKCS8ENCHEADER, 0, PKCS8ENCHEADER.length) == 0 &&
                Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                        PKCS8ENCFOOTER, 0, PKCS8ENCFOOTER.length) == 0) {
            encodedBytes = decoder.decode(Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
            return new PKCS8EncodedKeySpec(encodedBytes, KeyUtil.getAlgorithm(encodedBytes).getName());

        } else if (Arrays.compare(encodedBytes, 0, endHeader,
                PUBHEADER, 0, PUBHEADER.length) == 0 &&
                Arrays.compare(encodedBytes, startFooter, encodedBytes.length,
                        PUBFOOTER, 0, PUBFOOTER.length) == 0) {
            encodedBytes = decoder.decode(Arrays.copyOfRange(encodedBytes, endHeader, startFooter));
            return new X509EncodedKeySpec(encodedBytes, KeyUtil.getAlgorithm(encodedBytes).getName());
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

    @Override
    public String getAlgorithm() {
        if (eks == null) {
            return super.getAlgorithm();
        }
        return eks.getAlgorithm();
    }

}
