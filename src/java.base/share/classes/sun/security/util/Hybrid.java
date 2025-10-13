/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.util;

import com.sun.crypto.provider.DH;
import sun.security.x509.X509Key;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyFactorySpi;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Locale;

public class Hybrid {

    public record SecretKeyImpl(SecretKey k1, SecretKey k2) implements SecretKey {
        @Override
        public String getAlgorithm() {
            return "Hybrid";
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }
    }

    private static AlgorithmParameterSpec getSpec(String name) {
        if (APS.isGenericEC(name)) {
            return new ECGenParameterSpec(name);
        } else {
            return new NamedParameterSpec(name);
        }
    }

    private static KeyPairGenerator getKeyPairGenerator(String name) throws
            NoSuchAlgorithmException {
        if (APS.isGenericEC(name)) {
            name = "EC";
        }
        return KeyPairGenerator.getInstance(name);
    }

    private static KeyFactory getKeyFactory(String name) throws
            NoSuchAlgorithmException {
        if (APS.isGenericEC(name)) {
            name = "EC";
        }
        return KeyFactory.getInstance(name);
    }

    /**
     * Returns a KEM instance for each side of the hybrid algorithm.
     * For traditional key exchange algorithms, we use the DH-based KEM
     * implementation provided by DH.PROVIDER.
     * For ML-KEM post-quantum algorithms, we obtain a KEM instance
     * using the given algorithm name.
     */
    private static KEM getKEM(String name) throws NoSuchAlgorithmException {
        if (APS.isGenericEC(name) || APS.isXDH(name)) {
            return KEM.getInstance("DH", DH.PROVIDER);
        } else {
            return KEM.getInstance(name);
        }
    }

    public static class KeyPairGeneratorImpl extends KeyPairGeneratorSpi {
        private final KeyPairGenerator left;
        private final KeyPairGenerator right;
        private final AlgorithmParameterSpec leftSpec;
        private final AlgorithmParameterSpec rightSpec;
        private boolean initialized = false;

        public KeyPairGeneratorImpl(String left, String right)
                throws NoSuchAlgorithmException  {
            this.left = getKeyPairGenerator(left);
            this.right = getKeyPairGenerator(right);
            leftSpec = getSpec(left);
            rightSpec = getSpec(right);
        }

        @Override
        public void initialize(int keysize, SecureRandom random) {
            try {
                left.initialize(leftSpec, random);
                right.initialize(rightSpec, random);
                initialized = true;
            } catch (Exception e) {
                throw new InvalidParameterException(e);
            }
        }

        @Override
        public KeyPair generateKeyPair() {
            if (!initialized) {
                try {
                    left.initialize(leftSpec);
                    right.initialize(rightSpec);
                    initialized = true;
                } catch (Exception e) {
                    throw new ProviderException(e);
                }
            }
            var kp1 = left.generateKeyPair();
            var kp2 = right.generateKeyPair();
            return new KeyPair(
                    new PublicKeyImpl("Hybrid", kp1.getPublic(), kp2.getPublic()),
                    new PrivateKeyImpl("Hybrid", kp1.getPrivate(), kp2.getPrivate()));
        }
    }

    public static class KeyFactoryImpl extends KeyFactorySpi {
        private final KeyFactory left;
        private final KeyFactory right;
        private final int leftlen;
        private final String leftname;
        private final String rightname;

        public KeyFactoryImpl(String left, String right)
                throws NoSuchAlgorithmException {
            this.left = getKeyFactory(left);
            this.right = getKeyFactory(right);
            this.leftlen = leftPublicLength(left);
            this.leftname = left;
            this.rightname = right;
        }

        @Override
        protected PublicKey engineGeneratePublic(KeySpec keySpec)
                throws InvalidKeySpecException {
            if (keySpec instanceof RawKeySpec rks) {
                byte[] key = rks.getKeyArr();
                byte[] leftKeyBytes = Arrays.copyOfRange(key, 0, leftlen);
                byte[] rightKeyBytes = Arrays.copyOfRange(key, leftlen,
                        key.length);
                PublicKey leftKey, rightKey;

                try {
                    if (APS.isGenericEC(leftname)) {
                        var curve = CurveDB.lookup(leftname);
                        var ecSpec = new ECPublicKeySpec(
                                ECUtil.decodePoint(leftKeyBytes,
                                curve.getCurve()), curve);
                        leftKey = left.generatePublic(ecSpec);
                    } else if (leftname.startsWith("ML-KEM")) {
                        try {
                            leftKey = left.generatePublic(new RawKeySpec(
                                    leftKeyBytes));
                        } catch (Exception e) {
                            leftKey = left.generatePublic(new X509EncodedKeySpec(
                                    leftKeyBytes));
                        }
                    } else {
                        throw new InvalidKeySpecException("Unsupported left" +
                                " algorithm" + leftname);
                    }

                    if (APS.isXDH(rightname)) {
                        ArrayUtil.reverse(rightKeyBytes);
                        var xecSpec = new XECPublicKeySpec(
                                new NamedParameterSpec(rightname),
                                new BigInteger(1, rightKeyBytes));
                        rightKey = right.generatePublic(xecSpec);
                    } else if (rightname.startsWith("ML-KEM")) {
                        try {
                            rightKey = right.generatePublic(new RawKeySpec(
                                    rightKeyBytes));
                        } catch (Exception e) {
                            rightKey = right.generatePublic(new X509EncodedKeySpec(
                                    rightKeyBytes));
                        }
                    } else {
                        throw new InvalidKeySpecException("Unsupported right" +
                                " algorithm: " + rightname);
                    }

                    return new PublicKeyImpl("Hybrid", leftKey, rightKey);
                } catch (Exception e) {
                    throw new InvalidKeySpecException("Failed to decode hybrid" +
                            " key", e);
                }
            }

            throw new InvalidKeySpecException(keySpec.toString());

        }

        private static int leftPublicLength(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "secp256r1" -> 65;
                case "secp384r1" -> 97;
                case "ml-kem-768" -> 1184;
                default -> throw new IllegalArgumentException(
                        "Unknown named group: " + name);
            };
        }

        @Override
        protected PrivateKey engineGeneratePrivate(KeySpec keySpec) throws
                InvalidKeySpecException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec)
                throws InvalidKeySpecException {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Key engineTranslateKey(Key key) throws InvalidKeyException {
            throw new UnsupportedOperationException();
        }
    }

    public static class KEMImpl implements KEMSpi {
        private final KEM left;
        private final KEM right;

        public KEMImpl(String left, String right) throws NoSuchAlgorithmException {
            this.left = getKEM(left);
            this.right = getKEM(right);
        }

        @Override
        public EncapsulatorSpi engineNewEncapsulator(PublicKey publicKey,
                AlgorithmParameterSpec spec, SecureRandom secureRandom) throws
                InvalidAlgorithmParameterException, InvalidKeyException {
            if (publicKey instanceof PublicKeyImpl pk) {
                return new Handler(left.newEncapsulator(pk.left, secureRandom),
                        right.newEncapsulator(pk.right, secureRandom), null, null);
            }
            throw new InvalidKeyException();
        }

        @Override
        public DecapsulatorSpi engineNewDecapsulator(PrivateKey privateKey,
                AlgorithmParameterSpec spec) throws InvalidAlgorithmParameterException,
                InvalidKeyException {
            if (privateKey instanceof PrivateKeyImpl pk) {
                return new Handler(null, null, left.newDecapsulator(pk.left),
                        right.newDecapsulator(pk.right));
            }
            throw new InvalidKeyException();
        }
    }

    private static byte[] concat(byte[]... inputs) {
        int outLen = 0;
        for (byte[] in : inputs) {
            outLen += in.length;
        }
        byte[] out = new byte[outLen];
        int pos = 0;
        for (byte[] in : inputs) {
            System.arraycopy(in, 0, out, pos, in.length);
            pos += in.length;
        }
        return out;
    }

    private record Handler(KEM.Encapsulator le, KEM.Encapsulator re,
            KEM.Decapsulator ld, KEM.Decapsulator rd)
            implements KEMSpi.EncapsulatorSpi, KEMSpi.DecapsulatorSpi {
        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to,
                String algorithm) {
            var left = le.encapsulate();
            var right = re.encapsulate();
            if (from == 0 && to == le.secretSize() + re.secretSize()) {
                return new KEM.Encapsulated(
                        new SecretKeyImpl(left.key(), right.key()),
                        concat(left.encapsulation(), right.encapsulation()),
                        null);
            } else {
                throw new IllegalArgumentException(
                        "Invalid range for encapsulation: from = " + from +
                        " to = " + to + ", expected total secret size = " +
                        (le.secretSize() + re.secretSize()));
            }
        }

        @Override
        public int engineSecretSize() {
            if (le != null) {
                return le.secretSize() + re.secretSize();
            } else {
                return ld.secretSize() + rd.secretSize();
            }
        }

        @Override
        public int engineEncapsulationSize() {
            if (le != null) {
                return le.encapsulationSize() + re.encapsulationSize();
            } else {
                return ld.encapsulationSize() + rd.encapsulationSize();
            }
        }

        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation, int from,
                int to, String algorithm) throws DecapsulateException {
            var left = Arrays.copyOf(encapsulation, ld.encapsulationSize());
            var right = Arrays.copyOfRange(encapsulation, ld.encapsulationSize(),
                    encapsulation.length);
            if (from == 0 && ld.secretSize() + rd.secretSize() == to) {
                return new SecretKeyImpl(ld.decapsulate(left),
                        rd.decapsulate(right));
            } else {
                throw new IllegalArgumentException(
                        "Invalid range for decapsulation: from = " + from +
                        " to = " + to + ", expected total secret size = " +
                        (ld.secretSize() + rd.secretSize()));
            }
        }
    }

    /**
     * Hybrid public key combines two underlying public keys (left and right).
     * Public keys can be transmitted/encoded because the hybrid protocol
     * requires the public component to be sent.
     */
    public record PublicKeyImpl(String algorithm, PublicKey left,
            PublicKey right) implements PublicKey {
        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        // getFormat() returns "RAW" if both keys are X509Key; otherwise null.
        @Override
        public String getFormat() {
            if (left instanceof X509Key && right instanceof X509Key) {
                return "RAW";
            } else {
                return null;
            }
        }

        // getEncoded() returns the concatenation of the encoded bytes of the
        // left and right public keys only if both are X509Key types.
        @Override
        public byte[] getEncoded() {
            if (left instanceof X509Key xk1 && right instanceof X509Key xk2) {
                return concat(xk1.getKeyAsBytes(), xk2.getKeyAsBytes());
            } else {
                return null;
            }
        }
    }

    /**
     * Hybrid private key combines two underlying private keys (left and right).
     * It is for internal use only. The private keys should never be exported.
     */
    public record PrivateKeyImpl(String algorithm, PrivateKey left,
            PrivateKey right) implements PrivateKey {

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        // getFormat() returns null because there is no standard
        // format for a hybrid private key.
        @Override
        public String getFormat() {
            return null;
        }

        // getEncoded() returns an empty byte array because there is no
        // standard encoding format for a hybrid private key.
        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }
    }

    private static final class APS {
        static boolean isGenericEC(String name) {
            return name != null && name.startsWith("secp");
        }

        static boolean isXDH(String name) {
            return name != null && name.equals("X25519");
        }
    }
}
