package java.security;

import jdk.internal.util.xml.impl.Input;
import sun.nio.cs.ISO_8859_1;
import sun.security.pkcs.PKCS8Key;
import sun.security.x509.X509Key;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.Buffer;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class PEMDecoder {

    /**
     * The Dashs.
     */
    private static final byte[] DASHES = "-----".getBytes(ISO_8859_1.INSTANCE);
    private static final String STARTHEADER = "-----BEGIN ";
    private static final String ENDFOOTER = "-----END ";

    /**
     * Public Key PEM header & footer
     */
    static final String PUBHEADER = "-----BEGIN PUBLIC KEY-----";
    static final String PUBFOOTER = "-----END PUBLIC KEY-----";
    static final String PUBTAIL = "PUBLIC KEY-----";

    /**
     * Private Key PEM header & footer
     */
    static final String PKCS8HEADER = "-----BEGIN PRIVATE KEY-----";
    static final String PKCS8FOOTER = "-----END PRIVATE KEY-----";
    static final String PKCS8TAIL = "PRIVATE KEY-----");

    /**
     * Encrypted Private Key PEM header & footer
     */
    static final String PKCS8ENCHEADER = "-----BEGIN ENCRYPTED PRIVATE KEY-----";
    static final String PKCS8ENCFOOTER = "-----END ENCRYPTED PRIVATE KEY-----";
    static final String PKCS8ENCTAIL = "ENCRYPTED PRIVATE KEY-----");

    enum PEMContentType { NotSupported, PublicKey, PrivateKey, EncryptedPrivateKey, OneAsymmetricKey };

    PEMContentType keyType;
    byte[] encoded;
    PKCS8Key privateKey = null;
    X509Key publicKey = null;

    /*
    private PEMDecoder(String data, int endEncoding) throws IOException {
        int slen = STARTHEADER.length();
        int startEncoding = 0;
        if (data.startsWith(PUBTAIL, slen)) {
            keyType = PEMContentType.PublicKey;
            startEncoding = PUBHEADER.length();
            endEncoding = data.indexOf(PUBFOOTER, endEncoding);
        } else if (data.startsWith(PKCS8TAIL, slen)) {
            keyType = PEMContentType.PrivateKey;
            startEncoding = PKCS8HEADER.length();
            endEncoding = data.indexOf(PKCS8FOOTER, endEncoding);
        } else if (data.startsWith(PKCS8ENCTAIL, slen)) {
            keyType = PEMContentType.EncryptedPrivateKey;
            startEncoding = PKCS8ENCHEADER.length();
            endEncoding = data.indexOf(PKCS8ENCFOOTER, endEncoding);
        }
        if (startEncoding == 0) {
            throw new IOException("Header did not match supported types");
        }
        if (endEncoding == -1) {
            throw new IOException("Footer does to match header type.");
        }

        encoded = Base64.getMimeDecoder().decode(
            data.substring(startEncoding, endEncoding));
    }
     */

    private PEMDecoder(String data, String header, String footer) throws IOException {
        if (header.equalsIgnoreCase(PUBHEADER) && footer.equalsIgnoreCase(PUBFOOTER)) {
            keyType = PEMContentType.PublicKey;
        } else if (data.startsWith(PKCS8HEADER) && footer.equalsIgnoreCase(PKCS8FOOTER)) {
            keyType = PEMContentType.PrivateKey;
        } else if (data.startsWith(PKCS8ENCHEADER) && footer.equalsIgnoreCase(PKCS8ENCFOOTER)) {
            keyType = PEMContentType.EncryptedPrivateKey;
        }
        if (keyType == PEMContentType.NotSupported) {
            throw new IOException("Unsupported type or not properly formatted PEM");
        }

        encoded = Base64.getDecoder().decode(data);
    }

    private PEMDecoder(byte[] data, PEMContentType type) throws IOException {
        encoded = data;
        keyType = type;
        try {
            privateKey = new PKCS8Key(data);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }


    /**
     * From pem decoder.
     *
     * @param data the data
     * @return the pem decoder
     * @throws IOException the io exception
     */
    static public PEMDecoder from(String data) throws IOException {
/*        if (!data.startsWith(STARTHEADER)) {
            throw new IOException("No BEGIN header found");
        }
        int endIndex = data.lastIndexOf(ENDFOOTER);
        if (endIndex > 0) {
            throw new IOException("No END footer found");
        }
        return new PEMDecoder(data, endIndex);
 */
        return from(new StringReader(data));
    }

    static PEMDecoder from(Reader reader) throws IOException {
        BufferedReader br = new BufferedReader(reader);
        StringBuilder sb = new StringBuilder(1024);
        br.mark(8192); // Default size of Reader
        String header = br.readLine();
        String s;
        if (!header.startsWith(STARTHEADER)) {
            throw new IOException("No BEGIN header found");
        }
        int len = header.length();
        s = br.readLine();
        while (!s.startsWith(ENDFOOTER)) {
            len += s.length();
            if (len > 8100) {
                throw new IOException("No END footer found");
            }
            sb.append(s);
            s = br.readLine();
        }
        return new PEMDecoder(sb.toString(), header, s);
    }

    boolean isEncrypted() {
        return (keyType == PEMContentType.EncryptedPrivateKey);
    }

    public PEMDecoder decrypt(char[] password) throws IOException {
        return decrypt(password, null);
    }

    public PEMDecoder decrypt(char[] password, Provider provider) throws IOException {
        if (!isEncrypted()) {
            throw new IOException("Encoding not encrypted");
        }
        return new PEMDecoder(p8Decrypt(encoded, password, provider), PEMContentType.PrivateKey);
    }

    private static byte[] p8Decrypt(byte[] data, char[] password, Provider p) throws IOException {
        try {
            EncryptedPrivateKeyInfo epki = new EncryptedPrivateKeyInfo(data);
            PBEKeySpec pks = new PBEKeySpec(password);
            SecretKeyFactory skf;
            PKCS8EncodedKeySpec keySpec;
            if (p == null) {
                skf = SecretKeyFactory.getInstance(epki.getAlgName());
                keySpec = epki.getKeySpec(skf.generateSecret(pks));
            } else {
                skf = SecretKeyFactory.getInstance(epki.getAlgName(), p);
                keySpec = epki.getKeySpec(skf.generateSecret(pks), p);
            }
            return keySpec.getEncoded();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

   // <T> boolean contains(Class<T>){}
    public <T> T get(Class<T> kClass){
        if (kClass.isAssignableFrom(PrivateKey.class) || privateKey != null) {
            return (T)privateKey;
        } else if (kClass.isAssignableFrom(PublicKey.class) ||
            publicKey != null) {
            return (T)publicKey;
        }
        return null;
    }

}
