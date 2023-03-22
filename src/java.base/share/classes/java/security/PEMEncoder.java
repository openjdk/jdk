package java.security;

import sun.security.pkcs.PKCS8Key;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Objects;

public class PEMEncoder {
    enum KeyType {UNKNOWN, PRIVATE, PUBLIC, ENCRYPTED_PRIVATE}
    String encoding;

    PEMEncoder(KeyType keyType, byte[] bytes){
        StringBuilder sb = new StringBuilder(100);
        switch (keyType) {
            case PUBLIC -> {
                sb.append(PEMFormat.PUBHEADER);
                sb.append(bytes);
                sb.append(PEMFormat.PUBFOOTER);
            }
            case PRIVATE -> {
                sb.append(PEMFormat.PKCS8HEADER);
                sb.append(bytes);
                sb.append(PEMFormat.PKCS8FOOTER);
            }
            case ENCRYPTED_PRIVATE -> {
                sb.append(PEMFormat.PKCS8ENCHEADER);
                sb.append(bytes);
                sb.append(PEMFormat.PKCS8ENCFOOTER);
            }
        }
        encoding = sb.toString();
    }

    @Override
    public String toString() {
        return encoding;
    }

    public byte[] getEncoded() {
        return encoding.getBytes();
    }
}

class Builder {
    private static final String DEFAULT_ALGO = "PBEWithHmacSHA256AndAES_128";
    byte[] privateBytes;
    byte[] publicBytes;
    // temp
    Cipher cipher = null;
    String pbeAlgo;
    char[] password = null;
    AlgorithmParameterSpec encAPS = null;
    Provider encProvider = null;

    // PKCS8 or X509
    Builder(Key k) throws IOException {
        if (k instanceof PrivateKey) {
            privateBytes = getKeyEncoding(k);
        } else if (k instanceof PublicKey) {
            publicBytes = getKeyEncoding(k);
        } else {
            throw new IOException("Invalid Key type used.");
        }
    }

    // OAS
    Builder(KeyPair kp) throws IOException {
        Objects.requireNonNull(kp);
        Key k = kp.getPrivate();
        if (k == null) {
            throw new IOException("KeyPair does not contain PrivateKey.");
        }
        privateBytes = getKeyEncoding(k);
        k = kp.getPublic();
        if (k == null) {
            throw new IOException("KeyPair does not contain PublicKey.");
        }
        publicBytes = getKeyEncoding(k);
    }

    // PKCS8 or X509
    Builder(EncodedKeySpec eks) throws IOException {
        if (eks instanceof PKCS8EncodedKeySpec) {
            privateBytes = eks.getEncoded();
        } else if (eks instanceof X509EncodedKeySpec) {
            publicBytes = eks.getEncoded();
        } else {
            throw new IOException("PKCS8EncodedKeySpec or X509EncodedKeySpec" +
                " required.");
        }
    }

    // Check for nulls
    private static byte[] getKeyEncoding(Key k) throws IOException {
        byte[] bytes = k.getEncoded();
        if (bytes == null) {
            throw new IOException("Key object does not contain encoding");
        }
        return bytes;
    }

    public Builder encrypt(char[] password) throws IOException {
        this.password = password.clone();
        this.pbeAlgo = DEFAULT_ALGO;
        return this;
    }

    public Builder encrypt(char[] password, String pbeAlgo, AlgorithmParameterSpec aps, Provider p) throws IOException {
        this.password = password.clone();
        this.pbeAlgo = pbeAlgo;
        this.encAPS = aps;
        this.encProvider = p;
        return this;
    }

    public PEMEncoder build() throws IOException {
        DerOutputStream out = new DerOutputStream();
        PEMEncoder.KeyType keyType = PEMEncoder.KeyType.UNKNOWN;

        // Encrypted PKCS8
        if (cipher != null) {
            if (privateBytes != null) {
                throw new IOException("No PrivateKey defined in the Builder.");
            }
            try {
                var spec = new PBEKeySpec(password);
                // PBEKeySpec clones password
                Arrays.fill(password, (char) 0x0);
                SecretKeyFactory factory;
                if (encProvider == null) {
                    factory = SecretKeyFactory.getInstance(pbeAlgo);
                    cipher = Cipher.getInstance(pbeAlgo);
                } else {
                    factory = SecretKeyFactory.getInstance(pbeAlgo, encProvider);
                    cipher = Cipher.getInstance(pbeAlgo, encProvider);
                }
                var skey = factory.generateSecret(spec);
                cipher.init(Cipher.ENCRYPT_MODE, skey, encAPS);
                AlgorithmId algid = new AlgorithmId(getPBEID(pbeAlgo),
                    cipher.getParameters());
                algid.encode(out);
                out.putOctetString(cipher.doFinal(privateBytes));
                privateBytes = DerValue.wrap(DerValue.tag_Sequence, out).toByteArray());
            } catch (Exception e) {
                throw new IOException(e);
            }
            if (publicBytes == null) {
                return new PEMEncoder(PEMEncoder.KeyType.ENCRYPTED_PRIVATE, privateBytes);
            }
            keyType = PEMEncoder.KeyType.ENCRYPTED_PRIVATE;
        }
        // PKCS8 only
        if (privateBytes != null && publicBytes == null) {
            return new PEMEncoder(PEMEncoder.KeyType.PRIVATE, privateBytes);
        }
        // X509 only
        if (privateBytes == null && publicBytes != null) {
            return new PEMEncoder(PEMEncoder.KeyType.PUBLIC, DerValue.wrap(DerValue.tag_Sequence, out).toByteArray());
        }
        // OAS
        return new PEMEncoder(PEMEncoder.KeyType.PRIVATE, PKCS8Key.getEncoded(privateBytes, publicBytes));
    }


    // Sorta hack to get the right OID for PBBS2
    private static ObjectIdentifier getPBEID(String algorithm) throws IOException {
        try {
            if (algorithm.contains("AES")) {
                return AlgorithmId.get("PBES2").getOID();
            } else {
                return AlgorithmId.get(algorithm).getOID();
            }
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}