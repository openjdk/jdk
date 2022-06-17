package java.security;

import sun.nio.cs.ISO_8859_1;
import sun.security.pkcs.PKCS8Key;
import sun.security.x509.X509Key;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.ByteBuffer;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;


/**
 * jfsdakl
 */
public class PEMFormat {


    static final String DASHS = "-----";

    static final byte[] DASHSARRAY = DASHS.getBytes(ISO_8859_1.INSTANCE);

    static final byte[] PUBHEADER = "-----BEGIN PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);;
    static final byte[] PUBFOOTER = "-----END PUBLIC KEY-----".getBytes(ISO_8859_1.INSTANCE);;

    static final byte[] PKCS1HEADER = "-----BEGIN PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;
    static final byte[] PKCS1FOOTER = "-----END PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;

    static final byte[] PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;
    static final byte[] PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;

    static final byte[] PKCS1ECHEADER = "-----BEGIN BEGIN EC PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;
    static final byte[] PKCS1ECFOOTER = "-----END EC PRIVATE KEY-----".getBytes(ISO_8859_1.INSTANCE);;

    private final Base64.Decoder decoder;
    boolean isX509 = false;
    boolean isENCRYPTED = false;
    boolean isEC = false;
    boolean isPublic = false;

    PEMFormat(Base64.Decoder d) {
        decoder = d;
    }

    /**
     * fjkdsl
      * @return PEMFormat
     */
    public static PEMFormat getDecoder() {
        PEMFormat pem = new PEMFormat(Base64.getDecoder());
        return pem;
    }

    /**
     * fjdksal
     * @param data jfdskal
     * @return byte{}
     * @throws GeneralSecurityException jrekslw
     */
    public byte[] decode(byte[] data) throws GeneralSecurityException {
        int endHeader = find(data, 5, DASHSARRAY);
        int startFooter = findReverse(data, data.length - 6, DASHSARRAY);
        if (endHeader == -1 || startFooter == -1) {
            throw new GeneralSecurityException("Invalid PEM format");
        }
        if (Arrays.compare(data, 0, endHeader,
            PKCS1HEADER, 0, PKCS1HEADER.length) == 0 &&
            Arrays.compare(data, startFooter, data.length,
                PKCS1FOOTER, 0, PKCS1FOOTER.length) == 0) {
            isX509 = true;
        } else if (Arrays.compare(data, 0, endHeader,
            PKCS8ENCHEADER, 0, PKCS8ENCHEADER.length) == 0 &&
            Arrays.compare(data, startFooter, data.length,
                PKCS8ENCFOOTER, 0, PKCS8ENCFOOTER.length) == 0) {
            isENCRYPTED = true;
        } else if (Arrays.compare(data, 0, endHeader,
            PKCS1ECHEADER, 0, PKCS1ECHEADER.length) == 0 &&
            Arrays.compare(data, startFooter, data.length,
                PKCS1ECFOOTER, 0, PKCS1ECFOOTER.length) == 0) {
            isEC = true;
        } else if (Arrays.compare(data, 0, endHeader,
            PUBHEADER, 0, PUBHEADER.length) == 0 &&
            Arrays.compare(data, startFooter, data.length,
                PUBFOOTER, 0, PUBFOOTER.length) == 0) {
            isPublic = true;
        } else {
            throw new GeneralSecurityException("No supported format found");
        }
        ByteBuffer b = ByteBuffer.wrap(data, endHeader, startFooter - endHeader);
        // Base64.Decoder returns a byte[] ByteBuffer
        return decoder.decode(b).array();
    }

    /**
     * fjkdsla
     * @param data fjdskl
     * @return fjdskl
     * @throws GeneralSecurityException fdjskl
     */
    public byte[] decode(String data) throws GeneralSecurityException {
        return decode(data.getBytes(ISO_8859_1.INSTANCE));
    }
/*
    byte[] decode(String data) throws GeneralSecurityException {
        if (!data.startsWith(BASEHEADER)) {

        }
        int headerLen;

        if (data.regionMatches(BASEHEADER.length(), PKCS1HEADER, 0, PKCS1HEADER.length()) && data.endsWith(PKCS1FOOTER)) {
            isPKCS1 = true;
            headerLen = BASEHEADER.length() + PKCS1HEADER.length();
            decoder.decode(
                ByteBuffer.wrap(data.getBytes(ISO_8859_1.INSTANCE),
                headerLen,data.length() - headerLen - PKCS1FOOTER.length()));

        } else if (data.regionMatches(BASEHEADER.length(), PKCS1ECHEADER, 0, PKCS1ECHEADER.length()) && data.endsWith(PKCS1ECFOOTER)) {
            isEC = true;
            headerLen = BASEHEADER.length() + PKCS1ECHEADER.length();
            return decoder.decode(
                ByteBuffer.wrap(data.getBytes(ISO_8859_1.INSTANCE),
                headerLen,data.length() - headerLen - PKCS1ECFOOTER.length()));
        } else if (data.regionMatches(BASEHEADER.length(), PKCS8ENCHEADER, 0, PKCS8ENCHEADER.length()) && data.endsWith(PKCS8ENCFOOTER)) {
            isENCRYPTED = true;
            headerLen = BASEHEADER.length() + PKCS8ENCHEADER.length();
            return decoder.decode(
                ByteBuffer.wrap(data.getBytes(ISO_8859_1.INSTANCE),
                headerLen,data.length() - headerLen - PKCS8ENCFOOTER.length()));
        } else {
            throw new GeneralSecurityException("Invalid PEM format");
        }

    }
 */

     /**
     * fdsafdsa
     * @param data fdsaf
     * @return fdsaf
     * @throws GeneralSecurityException fdasfsda
     */
     //public T pkcs8(T class, byte[]  data)
    public PrivateKey pkcs8(byte[] data) throws GeneralSecurityException {
        PrivateKey p;
        try {
            p = PKCS8Key.parseKey(data);
        } catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
        return p;
    }

    /**
     * fdsafdsa
     * @param data fdsaf
     * @return fdsaf
     * @throws GeneralSecurityException fdasfsda
     */
    public PublicKey x509(byte[] data) throws GeneralSecurityException {
        PublicKey p;
        try {
            p = X509Key.parseKey(data);
        } catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
        return p;
    }

    private static String password = "foobar";

    /**
     * fdsafdsa
     * @param data fdsaf
     * @return fdsaf
     * @throws GeneralSecurityException fdasfsda
     */
    public PrivateKey epkcs8(byte[] data) throws GeneralSecurityException {
        try {
            EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(data);
            //Base64.getMimeDecoder().decode(data));
            PBEKeySpec pks = new PBEKeySpec(password.toCharArray());
            SecretKeyFactory skf = SecretKeyFactory.getInstance(epki.getAlgName());
            SecretKey sk = skf.generateSecret(pks);
            PKCS8EncodedKeySpec keySpec = epki.getKeySpec(sk);
            return pkcs8(keySpec.getEncoded());
        } catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
    }

    /**
     * fdsafdsa
     * @param key fdsaf
     * @param passwd fdsaf
     * @return fdsaf
     * @throws GeneralSecurityException fdasfsda
     */
    public byte[] encpkcs8(PrivateKey key, char[] passwd) throws GeneralSecurityException {
        String algo = "PBEWithHmacSHA512AndAES_256";
        try {
            PBEKeySpec pks = new PBEKeySpec(passwd);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(algo);
            SecretKey sk = skf.generateSecret(pks);
            Cipher c = Cipher.getInstance(algo);
            c.init(Cipher.ENCRYPT_MODE, sk);
            byte[] data = c.doFinal(key.getEncoded());
            AlgorithmParameters ap = c.getParameters();
            EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(ap, data);
            //PKCS8EncodedKeySpec keySpec = epki.getKeySpec(sk);
            return epki.getEncoded();
        } catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
    }

    static int find(byte[] a, int offset, byte[] d) {
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

}
