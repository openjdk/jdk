package com.sun.crypto.provider;

import sun.security.jca.JCAUtil;
import sun.security.pkcs.NamedPKCS8Key;
import sun.security.provider.NamedKEM;
import sun.security.provider.SHA3;
import sun.security.util.ArrayUtil;
import sun.security.x509.NamedX509Key;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;

public final class XWing extends NamedKEM {

    // \./
    // /^\
    // https://datatracker.ietf.org/doc/html/draft-connolly-cfrg-xwing-kem-07#section-5.3
    private static final byte[] X_WING_LABEL = {0x5c, 0x2e, 0x2f, 0x2f, 0x5e, 0x5c};

    public XWing() {
        super("X-Wing", "X-Wing");
    }

    @Override
    protected byte[][] implEncapsulate(String name, byte[] pk, Object pk2, SecureRandom sr) {
        if (!(pk2 instanceof XWingPublicKey parsedPk)) {
            throw new IllegalStateException("Invalid X-Wing public key type");
        }

        if (sr == null) {
            sr = JCAUtil.getDefSecureRandom();
        }

        // ML-KEM:
        var pkMKey = parsedPk.getMLKemPublicKey();
        KEM.Encapsulated encapsulated;
        try {
            KEM kem = KEM.getInstance("ML-KEM", SunJCE.getInstance());
            KEM.Encapsulator enc = kem.newEncapsulator(pkMKey, sr);
            encapsulated = enc.encapsulate();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SunJCE known to support ML-KEM", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid ML-KEM key part of X-Wing public key", e);
        }
        var ssM = encapsulated.key().getEncoded();
        var ctM = encapsulated.encapsulation();

        // X25519:
        var pkX = parsedPk.pkX();
        var pkXKey = parsedPk.getX25519PublicKey();
        byte[] ekX = new byte[32];
        sr.nextBytes(ekX);
        var ctX = X25519.dh(X25519.privateKey(ekX), X25519.basePoint());
        var ssX = X25519.dh(X25519.privateKey(ekX), pkXKey);

        // Combine:
        var ss = combiner(ssM, ssX, ctX, pkX);
        byte[] ct = new byte[ctM.length + ctX.length];
        System.arraycopy(ctM, 0, ct, 0, ctM.length);
        System.arraycopy(ctX, 0, ct, ctM.length, ctX.length);
        try {
            return new byte[][]{ct, ss}; // Return encapsulated key and shared secret
        } finally {
            Arrays.fill(ssM, (byte) 0x00);
            Arrays.fill(ssX, (byte) 0x00);
            Arrays.fill(ekX, (byte) 0x00);
        }
    }

    @Override
    protected byte[] implDecapsulate(String name, byte[] sk, Object sk2, byte[] encap) throws DecapsulateException {
        if (encap.length < 1088 + 32) {
            throw new DecapsulateException("Invalid encapsulation length");
        }
        if (!(sk2 instanceof XWingKeyPair keys)) {
            throw new IllegalStateException("Invalid X-Wing private key type");
        }

        // ML-KEM:
        var skM = keys.getMLKemPrivateKey();
        var ctM = Arrays.copyOfRange(encap, 0, 1088);
        byte[] ssM;
        try {
            KEM kem = KEM.getInstance("ML-KEM", SunJCE.getInstance());
            KEM.Decapsulator dec = kem.newDecapsulator(skM);
            ssM = dec.decapsulate(ctM).getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SunJCE known to support ML-KEM", e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid ML-KEM key part of X-Wing public key", e);
        } finally {
            destroyQuietly(skM);
        }

        // X25519:
        var skX = keys.getX25519PrivateKey();
        var ctX = Arrays.copyOfRange(encap, 1088, 1120);
        var pkX = keys.pkX();
        byte[] ssX;
        try {
            ssX = X25519.dh(skX, X25519.publicKey(ctX));
        } finally {
            destroyQuietly(skX);
            destroyQuietly(keys);
        }

        // Combine:
        try {
            return combiner(ssM, ssX, ctX, pkX);
        } finally {
            Arrays.fill(ssM, (byte) 0x00);
            Arrays.fill(ssX, (byte) 0x00);
        }
    }

    @Override
    protected int implSecretSize(String name) {
        return 32;
    }

    @Override
    protected int implEncapsulationSize(String name) {
        return 1088 + 32; // 1088 bytes for ML-KEM encapsulation + 32 bytes for X25519 encapsulation
    }

    @Override
    protected Object implCheckPublicKey(String name, byte[] pk) throws InvalidKeyException {
        // X-Wing public key is a concatenation of ML-KEM-768 public key (1184 bytes) + X25519 public key (32 bytes)
        if (pk == null || pk.length != 1184 + 32) {
            throw new InvalidKeyException("Invalid X-Wing public key length");
        }
        var pkM = Arrays.copyOfRange(pk, 0, 1184);
        var pkX = Arrays.copyOfRange(pk, 1184, 1216);
        record XWingPublicKeyImpl(byte[] pkM, byte[] pkX) implements XWingPublicKey {}
        return new XWingPublicKeyImpl(pkM, pkX);
    }

    @Override
    protected Object implCheckPrivateKey(String name, byte[] sk) throws InvalidKeyException {
        // X-Wing private key is a 32-byte secret key that is expanded to ML-KEM and X25519
        if (sk == null || sk.length != 32) {
            throw new InvalidKeyException("Invalid X-Wing private key length");
        }
        return expandDecapsulationKey(sk);
    }

    interface XWingPublicKey {

        /// returns the ML-KEM public key part (`pk_M = pk[0:1184]`)
        /// @return subkey bytes of length 1184
        byte[] pkM();

        /// returns the ML-KEM public key part (`pk_M = pk[0:1184]`)
        /// @return a new {@link PublicKey} instance constructed from {@link #pkM()}
        default PublicKey getMLKemPublicKey() {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("ML-KEM", SunJCE.getInstance());
                PublicKey key = new NamedX509Key("ML-KEM", "ML-KEM-768", pkM().clone()); // get translatable key from raw bytes
                return (PublicKey) keyFactory.translateKey(key);
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError("SunJCE known to support ML-KEM", e);
            } catch (InvalidKeyException e) {
                throw new IllegalStateException("Implementation-internal key invalid", e);
            }
        }

        /// returns the X25519 public key part (`pk_X = pk[1184:1216]`)
        /// @return subkey bytes of length 32
        byte[] pkX();

        /// returns the X25519 public key part (`pk_X = pk[1184:1216]`)
        /// @return a new {@link PublicKey} instance constructed from {@link #pkX()}
        default PublicKey getX25519PublicKey() {
            return X25519.publicKey(pkX());
        }
    }

    record XWingKeyPair(ML_KEM.ML_KEM_KeyPair m, KeyPair x) implements XWingPublicKey, Destroyable {
        @Override
        public byte[] pkM() {
            return m.encapsulationKey().keyBytes();
        }

        public byte[] skM() {
            return m.decapsulationKey().keyBytes();
        }

        public PrivateKey getMLKemPrivateKey() {
            try {
                KeyFactory keyFactory = KeyFactory.getInstance("ML-KEM", SunJCE.getInstance());
                PrivateKey key = new NamedPKCS8Key("ML-KEM", "ML-KEM-768", skM().clone()); // get translatable key from raw bytes
                return (PrivateKey) keyFactory.translateKey(key);
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError("SunJCE known to support ML-KEM", e);
            } catch (InvalidKeyException e) {
                throw new IllegalStateException("Implementation-internal key invalid", e);
            }
        }

        @Override
        public byte[] pkX() {
            if (!(x.getPublic() instanceof XECPublicKey p)) {
                throw new IllegalStateException("Unexpected type of X25519 public key: " + x.getPublic().getClass());
            }

            BigInteger u = p.getU();
            byte[] uBytes = u.toByteArray();
            ArrayUtil.reverse(uBytes);

            return Arrays.copyOf(uBytes, 32); // Ensure it is 32 bytes long
        }

        /// returns the X25519 private key
        /// @return the contained {@link PrivateKey} instance
        public PrivateKey getX25519PrivateKey() {
            return x.getPrivate();
        }

        @Override
        public void destroy() {
            Arrays.fill(m.decapsulationKey().keyBytes(), (byte) 0);
            destroyQuietly(x.getPrivate());
        }
    }

    // https://www.ietf.org/archive/id/draft-connolly-cfrg-xwing-kem-07.html#section-5.2
    static XWingKeyPair expandDecapsulationKey(byte[] sk) {
        assert sk.length == 32;

        var expanded = shake256(sk, 96);
        byte[] d = Arrays.copyOfRange(expanded, 0, 32);
        byte[] z = Arrays.copyOfRange(expanded, 32, 64);
        byte[] skX = Arrays.copyOfRange(expanded, 64, 96);
        Arrays.fill(expanded, (byte) 0);

        ML_KEM.ML_KEM_KeyPair m;
        try {
            m = new ML_KEM("ML-KEM-768").generateKemKeyPair(d, z);
        } finally {
            Arrays.fill(d, (byte) 0);
            Arrays.fill(z, (byte) 0);
        }

        KeyPair x;
        try {
            x = deriveX25519KeyPair(skX);
        } finally {
            Arrays.fill(skX, (byte) 0);
        }
        return new XWingKeyPair(m, x);
    }

    // https://datatracker.ietf.org/doc/html/draft-connolly-cfrg-xwing-kem-07#section-5.3
    private static byte[] combiner(byte[] ssM, byte[] ssX, byte[] ctX, byte[] pkX) {
        var input = new byte[ssM.length + ssX.length + ctX.length + pkX.length + X_WING_LABEL.length];
        System.arraycopy(ssM, 0, input, 0, ssM.length);
        System.arraycopy(ssX, 0, input, ssM.length, ssX.length);
        System.arraycopy(ctX, 0, input, ssM.length + ssX.length, ctX.length);
        System.arraycopy(pkX, 0, input, ssM.length + ssX.length + ctX.length, pkX.length);
        System.arraycopy(X_WING_LABEL, 0, input, ssM.length + ssX.length + ctX.length + pkX.length, X_WING_LABEL.length);
        try {
            MessageDigest sha3 = MessageDigest.getInstance("SHA3-256");
            return sha3.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("JVM does not support SHA3-256", e);
        }
    }

    private static KeyPair deriveX25519KeyPair(byte[] sk) {
        assert sk.length == 32;

        var privateKey = XWing.X25519.privateKey(sk);

        // applying x25519 on secret key and base point yields the public key:
        byte[] pk = X25519.dh(privateKey, X25519.basePoint());

        // turn public key bytes into an object:
        var publicKey = X25519.publicKey(pk);
        return new KeyPair(publicKey, privateKey);
    }

    private static byte[] shake256(byte[] input, int byteLength) {
        var digest = new SHA3.SHAKE256(byteLength);
        digest.update(input);
        return digest.digest();
    }

    private static void destroyQuietly(Destroyable destroyable) {
        if (destroyable != null) {
            try {
                destroyable.destroy();
            } catch (DestroyFailedException e) {
                // Ignore
            }
        }
    }

    /**
     * Utility class for X25519 operations.
     */
    private static class X25519 {

        private X25519() {}

        public static XECPublicKey basePoint() {
            record Holder() {
                static final XECPublicKey INSTANCE = publicKey(BigInteger.valueOf(9L)); // base point 9 (see RFC 7748)
            }
            return Holder.INSTANCE;
        }

        public static XECPublicKey publicKey(byte[] littleEndianU) {
            if (littleEndianU == null || littleEndianU.length != 32) {
                throw new IllegalArgumentException("Public key must be 32 bytes long");
            }
            byte[] bigEndianU = littleEndianU.clone();
            ArrayUtil.reverse(bigEndianU);
            return publicKey(new BigInteger(1, bigEndianU));
        }

        public static XECPublicKey publicKey(BigInteger u) {
            try {
                return (XECPublicKey) keyFactory().generatePublic(new XECPublicKeySpec(NamedParameterSpec.X25519, u));
            } catch (InvalidKeySpecException e) {
                throw new AssertionError("Key spec created in-place", e);
            }
        }

        public static XECPrivateKey privateKey(byte[] sk) {
            try {
                return (XECPrivateKey) keyFactory().generatePrivate(new XECPrivateKeySpec(NamedParameterSpec.X25519, sk));
            } catch (InvalidKeySpecException e) {
                throw new IllegalArgumentException("Internal implementation passed unsuitable key", e);
            }
        }

        public static byte[] dh(PrivateKey privateKey, PublicKey publicKey) {
            try {
                var keyAgreement = KeyAgreement.getInstance("X25519");
                keyAgreement.init(privateKey);
                keyAgreement.doPhase(publicKey, true);
                return keyAgreement.generateSecret();
            } catch (NoSuchAlgorithmException e) {
                throw new UnsupportedOperationException("JVM does not support X25519", e);
            } catch (InvalidKeyException e) {
                throw new IllegalArgumentException("Internal implementation passed unsuitable key", e);
            }
        }

        private static KeyFactory keyFactory() {
            record Holder() {
                static final KeyFactory INSTANCE = createKeyFactory();
            }
            return Holder.INSTANCE;
        }

        private static KeyFactory createKeyFactory() {
            try {
                return KeyFactory.getInstance("X25519");
            } catch (NoSuchAlgorithmException e) {
                throw new UnsupportedOperationException("JVM does not support X25519", e);
            }
        }

    }

}
