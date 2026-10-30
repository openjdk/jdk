/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import sun.security.util.ArrayUtil;
import sun.security.util.CurveDB;
import sun.security.util.ECUtil;
import sun.security.util.KeyUtil;
import sun.security.x509.X509Key;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Locale;

// The Hybrid class contains internal implementations for the TLS 1.3
// hybrid key exchange named groups: X25519MLKEM768, SecP256r1MLKEM768,
// and SecP384r1MLKEM1024. It wraps two underlying algorithms (left and
// right sides) in a single TLS hybrid named group.
//
// These hybrid key exchange algorithms are registered in SunJCE so that
// SunJSSE can select them. They are not general purpose KEM algorithms.
// They are exposed through the JCA KeyPairGenerator and KEM APIs so that
// SunJSSE can use them. Applications must not use them as general purpose
// KEM algorithms.
//
// Their public key encoding is the TLS 1.3 key_share encoding, not an
// X.509 SubjectPublicKeyInfo encoding, and the algorithms have no object
// identifiers. Hence, the public key’s getFormat() method must return "RAW",
// and getEncoded() must return the raw format of key_share encoding.
//
// For the negotiated shared secret represented as a SecretKey, getFormat()
// must return "RAW", and getEncoded() must return the combined key material
// as a byte array.

public class Hybrid {

    public static final NamedParameterSpec X25519_MLKEM768 =
            new NamedParameterSpec("X25519MLKEM768");

    public static final NamedParameterSpec SECP256R1_MLKEM768 =
            new NamedParameterSpec("SecP256r1MLKEM768");

    public static final NamedParameterSpec SECP384R1_MLKEM1024 =
            new NamedParameterSpec("SecP384r1MLKEM1024");

    private static AlgorithmParameterSpec getSpec(String name) {
        if (name.startsWith("secp")) {
            return new ECGenParameterSpec(name);
        } else {
            return new NamedParameterSpec(name);
        }
    }

    private static KeyPairGenerator getKeyPairGenerator(String name,
            Provider provider) throws NoSuchAlgorithmException {
        if (name.startsWith("secp")) {
            name = "EC";
        }
        if (name.startsWith("ML-KEM")) {
            // This internal hybrid implementation must use SunJCE's
            // ML-KEM service.
            return KeyPairGenerator.getInstance(name, provider);
        }
        return KeyPairGenerator.getInstance(name);
    }

    private static KeyFactory getKeyFactory(String name, Provider provider)
            throws NoSuchAlgorithmException {
        if (name.startsWith("secp")) {
            name = "EC";
        }
        if (name.startsWith("ML-KEM")) {
            // See getKeyPairGenerator(): ML-KEM is part of this SunJCE
            // implementation, and the classical component uses normal
            // provider selection.
            return KeyFactory.getInstance(name, provider);
        }
        return KeyFactory.getInstance(name);
    }

    private static KEM getKEM(String name, Provider provider)
            throws NoSuchAlgorithmException {
        if (name.startsWith("secp") || name.equals("X25519")) {
            return KEM.getInstance("DH", HybridProvider.PROVIDER);
        }
        return KEM.getInstance("ML-KEM", provider);
    }

    /**
     * A KeyPairGenerator implementation for a TLS 1.3 hybrid key exchange
     * named group.
     *
     * This implementation is exposed through the JCA KeyPairGenerator API
     * only so that TLS can obtain a key pair for a hybrid key_share. It is
     * not a general purpose keypair generator. The generated public key has
     * RAW format and its encoding is the TLS 1.3 key_share value. It is not
     * an X.509 SubjectPublicKeyInfo value.
     */
    public static class KeyPairGeneratorImpl extends KeyPairGeneratorSpi {
        private final KeyPairGenerator left;
        private final KeyPairGenerator right;
        private final AlgorithmParameterSpec leftSpec;
        private final AlgorithmParameterSpec rightSpec;

        public KeyPairGeneratorImpl(Provider provider, String leftAlg,
                String rightAlg)
                throws NoSuchAlgorithmException  {
            // The provider is SunJCE. It is used for ML-KEM only, and
            // ECDH/XDH services are selected because they are implemented
            // by a classical provider.
            left = getKeyPairGenerator(leftAlg, provider);
            right = getKeyPairGenerator(rightAlg, provider);
            leftSpec = getSpec(leftAlg);
            rightSpec = getSpec(rightAlg);
        }

        @Override
        public void initialize(AlgorithmParameterSpec params,
                SecureRandom random)
                throws InvalidAlgorithmParameterException {
            left.initialize(leftSpec, random);
            right.initialize(rightSpec, random);
        }

        @Override
        public void initialize(int keysize, SecureRandom random) {
            // NO-OP (do nothing)
        }

        @Override
        public KeyPair generateKeyPair() {
            var kp1 = left.generateKeyPair();
            var kp2 = right.generateKeyPair();
            return new KeyPair(
                    new PublicKeyImpl("Hybrid", kp1.getPublic(),
                            kp2.getPublic()),
                    new PrivateKeyImpl("Hybrid", kp1.getPrivate(),
                            kp2.getPrivate()));
        }
    }

    // Decodes the RAW TLS key_share accepted by the built-in hybrid KEM.
    // This is not a JCA KeyFactory service.
    private static final class RawPublicKeyDecoder {
        private final KeyFactory left;
        private final KeyFactory right;
        private final int leftlen;
        private final String leftname;
        private final String rightname;

        RawPublicKeyDecoder(Provider provider, String left, String right)
                throws NoSuchAlgorithmException {
            this.left = getKeyFactory(left, provider);
            this.right = getKeyFactory(right, provider);
            this.leftlen = leftPublicLength(left);
            this.leftname = left;
            this.rightname = right;
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

        PublicKey decode(PublicKey inKey) throws InvalidKeyException {
            if (inKey == null) {
                throw new InvalidKeyException("key must not be null");
            }

            if ("RAW".equalsIgnoreCase(inKey.getFormat())) {
                byte[] key = inKey.getEncoded();
                if (key == null) {
                    throw new InvalidKeyException(
                            "Key contains null key data");
                }
                if (key.length <= leftlen) {
                    throw new InvalidKeyException(
                            "Hybrid key length " + key.length +
                                    " is too short and its left key length is " +
                                    leftlen);
                }

                byte[] leftKeyBytes = Arrays.copyOfRange(key, 0, leftlen);
                byte[] rightKeyBytes = Arrays.copyOfRange(key, leftlen,
                        key.length);
                PublicKey leftKey, rightKey;

                try {
                    if (leftname.startsWith("secp")) {
                        var curve = CurveDB.lookup(leftname);
                        var ecSpec = new ECPublicKeySpec(
                                ECUtil.decodePoint(leftKeyBytes,
                                        curve.getCurve()), curve);
                        leftKey = left.generatePublic(ecSpec);
                    } else if (leftname.startsWith("ML-KEM")) {
                        try {
                            leftKey = (PublicKey) left.translateKey(KeyUtil
                                    .newRawPublicKey(leftname, leftKeyBytes));
                        } catch (InvalidKeyException e) {
                            // Fallback to X.509 encoding if ML-KEM impl
                            // does not support translating from RAW
                            leftKey = left.generatePublic(new X509EncodedKeySpec(
                                    KeyUtil.rawToX509(leftname, leftKeyBytes)));
                        }
                    } else {
                        throw new InvalidKeySpecException("Unsupported left" +
                                " algorithm" + leftname);
                    }

                    if (rightname.equals("X25519")) {
                        ArrayUtil.reverse(rightKeyBytes);
                        var xecSpec = new XECPublicKeySpec(
                                new NamedParameterSpec(rightname),
                                new BigInteger(1, rightKeyBytes));
                        rightKey = right.generatePublic(xecSpec);
                    } else if (rightname.startsWith("ML-KEM")) {
                        try {
                            rightKey = (PublicKey) right.translateKey(KeyUtil
                                    .newRawPublicKey(rightname, rightKeyBytes));
                        } catch (InvalidKeyException e) {
                            // Fallback to X.509 encoding if ML-KEM impl
                            // does not support translating from RAW
                            rightKey = right.generatePublic(new X509EncodedKeySpec(
                                    KeyUtil.rawToX509(rightname, rightKeyBytes)));
                        }
                    } else {
                        throw new InvalidKeySpecException("Unsupported right" +
                                " algorithm: " + rightname);
                    }

                    return new PublicKeyImpl("Hybrid", leftKey, rightKey);
                } catch (Exception e) {
                    throw new InvalidKeyException("Failed to decode " +
                            "hybrid key", e);
                }
            } else {
                throw new InvalidKeyException("Unknown key "
                        + inKey.getClass().getName() + " in "
                        + inKey.getFormat());
            }
        }
    }

    /**
     * A KEM implementation for a TLS 1.3 hybrid key exchange named group.
     *
     * This implementation is exposed through the JCA KEM API only so
     * that TLS can perform hybrid key exchange.  It is not a general
     * purpose KEM.  Its encapsulator accepts a public key with RAW
     * format containing the TLS 1.3 key_share value, and it does not
     * accept an X.509 SubjectPublicKeyInfo encoding.
     */
    public static class KEMImpl implements KEMSpi {
        private final KEM left;
        private final KEM right;
        private final RawPublicKeyDecoder keyDecoder;

        public KEMImpl(Provider provider, String left, String right)
                throws NoSuchAlgorithmException {
            this.left = getKEM(left, provider);
            this.right = getKEM(right, provider);
            this.keyDecoder = new RawPublicKeyDecoder(provider, left, right);
        }

        @Override
        public EncapsulatorSpi engineNewEncapsulator(PublicKey publicKey,
                AlgorithmParameterSpec spec, SecureRandom secureRandom) throws
                InvalidAlgorithmParameterException, InvalidKeyException {
            if (!(publicKey instanceof PublicKeyImpl)) {
                publicKey = keyDecoder.decode(publicKey);
            }
            if (publicKey instanceof PublicKeyImpl pk) {
                return new Handler(left.newEncapsulator(pk.left, secureRandom),
                        right.newEncapsulator(pk.right, secureRandom),
                        null, null);
            }
            throw new InvalidKeyException();
        }

        @Override
        public DecapsulatorSpi engineNewDecapsulator(PrivateKey privateKey,
                AlgorithmParameterSpec spec)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
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
            int expectedSecretSize = engineSecretSize();
            if (!(from == 0 && to == expectedSecretSize)) {
                throw new IllegalArgumentException(
                        "Invalid range for encapsulation: from = " + from +
                        " to = " + to + ", expected total secret size = " +
                        expectedSecretSize);
            }

            var left  = le.encapsulate();
            var right = re.encapsulate();
            return new KEM.Encapsulated(
                    new SecretKeyImpl(left.key(), right.key()),
                    concat(left.encapsulation(), right.encapsulation()),
                    null);
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
            int expectedEncSize = engineEncapsulationSize();
            if (encapsulation.length != expectedEncSize) {
                throw new DecapsulateException(
                        "Invalid key encapsulation message length: " +
                        encapsulation.length +
                        ", expected = " + expectedEncSize);
            }

            int expectedSecretSize = engineSecretSize();
            if (!(from == 0 && to == expectedSecretSize)) {
                throw new IllegalArgumentException(
                        "Invalid range for decapsulation: from = " + from +
                        " to = " + to + ", expected total secret size = " +
                        expectedSecretSize);
            }

            var left = Arrays.copyOf(encapsulation, ld.encapsulationSize());
            var right = Arrays.copyOfRange(encapsulation,
                    ld.encapsulationSize(), encapsulation.length);
            return new SecretKeyImpl(
                    ld.decapsulate(left, 0, ld.secretSize(), "Generic"),
                    rd.decapsulate(right, 0, rd.secretSize(), "Generic")
            );
        }
    }

    // Package-private
    static final class SecretKeyImpl implements SecretKey {
        private static final long serialVersionUID = 1L;

        final SecretKey k1;
        final SecretKey k2;
        private boolean destroyed;

        SecretKeyImpl(SecretKey k1, SecretKey k2) {
            this.k1 = k1;
            this.k2 = k2;
        }

        @Override
        public String getAlgorithm() {
            return "Generic";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            if (destroyed) {
                throw new IllegalStateException("Key has been destroyed");
            }
            // The component secrets are passed independently to HKDF so that
            // a provider can keep either component opaque.
            return null;
        }

        @Override
        public void destroy() {
            if (!destroyed) {
                KeyUtil.destroySecretKeys(k1, k2);
                destroyed = true;
            }
        }

        @Override
        public boolean isDestroyed() {
            return destroyed;
        }
    }

    /**
     * Hybrid public key combines two underlying public keys (left and right).
     * Public keys can be transmitted/encoded because the hybrid protocol
     * requires the public component to be sent.
     */
    // Package-private
    record PublicKeyImpl(String algorithm, PublicKey left,
            PublicKey right) implements PublicKey {
        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        // getFormat() returns "RAW" as hybrid key uses RAW concatenation
        // of underlying encodings.
        @Override
        public String getFormat() {
            return "RAW";
        }

        // getEncoded() returns the concatenation of the encoded bytes of the
        // left and right public keys.
        @Override
        public byte[] getEncoded() {
            return concat(onlyKey(left), onlyKey(right));
        }

        static byte[] onlyKey(PublicKey key) {
            if (key instanceof X509Key xk) {
                return xk.getKeyAsBytes();
            }

            // Fallback for 3rd-party providers
            if (!"X.509".equalsIgnoreCase(key.getFormat())) {
                throw new ProviderException("Invalid public key encoding " +
                        "format");
            }
            var xk = new X509Key();
            try {
                xk.decode(key.getEncoded());
            } catch (InvalidKeyException e) {
                throw new ProviderException("Invalid public key encoding", e);
            }
            return xk.getKeyAsBytes();
        }
    }

    /**
     * Hybrid private key combines two underlying private keys (left and right).
     * It is for internal use only. The private keys should never be exported.
     */
    private static final class PrivateKeyImpl implements PrivateKey {
        private static final long serialVersionUID = 1L;

        private final String algorithm;
        private final PrivateKey left;
        private final PrivateKey right;

        PrivateKeyImpl(String algorithm, PrivateKey left, PrivateKey right) {
            this.algorithm = algorithm;
            this.left = left;
            this.right = right;
        }

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
            return null;
        }
    }
}
