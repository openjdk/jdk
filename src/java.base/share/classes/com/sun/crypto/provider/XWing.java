package com.sun.crypto.provider;

import sun.security.jca.JCAUtil;
import sun.security.provider.NamedKEM;
import sun.security.util.ArrayUtil;

import javax.crypto.DecapsulateException;
import javax.crypto.KeyAgreement;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.Destroyable;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
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
    // https://datatracker.ietf.org/doc/html/draft-connolly-cfrg-xwing-kem-08#section-5.3
    private static final byte[] X_WING_LABEL = {0x5c, 0x2e, 0x2f, 0x2f, 0x5e, 0x5c};

    public XWing() {
        super("X-Wing", new XWingKeyFactory());
    }

    @Override
    protected byte[][] implEncapsulate(String name, byte[] pk, Object pk2, SecureRandom sr) {
        if (!(pk2 instanceof XWingKeyFactory.XWingPublicKey parsedPk)) {
            throw new IllegalStateException("Invalid X-Wing public key type");
        }

        if (sr == null) {
            sr = JCAUtil.getDefSecureRandom();
        }

        // ML-KEM:
		byte[] seed = new byte[32];
		sr.nextBytes(seed);
		ML_KEM.ML_KEM_EncapsulateResult mlKemEncapsulateResult;
		try {
			mlKemEncapsulateResult = new ML_KEM("ML-KEM-768").encapsulate(new ML_KEM.ML_KEM_EncapsulationKey(parsedPk.m()), seed);
		} finally {
			Arrays.fill(seed, (byte) 0);
		}
		var ssM = mlKemEncapsulateResult.sharedSecret();
		var ctM = mlKemEncapsulateResult.cipherText().encryptedBytes();

        // X25519:
        var pkX = parsedPk.x();
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
        if (!(sk2 instanceof XWingKeyFactory.XWingPrivateKey parsedSk)) {
            throw new IllegalStateException("Invalid X-Wing private key type");
        }

        // ML-KEM:
        var ctM = Arrays.copyOfRange(encap, 0, 1088);
		var ssM = new ML_KEM("ML-KEM-768").decapsulate(new ML_KEM.ML_KEM_DecapsulationKey(parsedSk.m()), new ML_KEM.K_PKE_CipherText(ctM));

        // X25519:
        var skX = parsedSk.getX25519PrivateKey();
        var ctX = Arrays.copyOfRange(encap, 1088, 1120);
        var pkX = parsedSk.derivePublicKey().x();
        byte[] ssX;
        try {
            ssX = X25519.dh(skX, X25519.publicKey(ctX));
        } finally {
            destroyQuietly(skX);
            destroyQuietly(parsedSk);
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
        return XWingKeyFactory.XWingPublicKey.of(pkM, pkX);
    }

    @Override
    protected Object implCheckPrivateKey(String name, byte[] sk) throws InvalidKeyException {
        // expanded X-Wing private key is a concatenation of ML-KEM-768 private key (1184 bytes) + X25519 private key (32 bytes)
        if (sk == null || sk.length != 2400 + 32) {
            throw new InvalidKeyException("Invalid X-Wing private key length");
        }
        var skM = Arrays.copyOfRange(sk, 0, 2400);
        var skX = Arrays.copyOfRange(sk, 2400, 2432);
        return XWingKeyFactory.XWingPrivateKey.of(skM, skX);
    }

    // https://www.ietf.org/archive/id/draft-connolly-cfrg-xwing-kem-08.html#section-5.3
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
    static class X25519 {

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
