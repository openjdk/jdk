/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import static javax.crypto.Cipher.PRIVATE_KEY;
import static javax.crypto.Cipher.PUBLIC_KEY;
import jdk.testlibrary.RandomFactory;

/**
 * @test
 * @bug 8044199
 * @summary Create a signature for RSA and get its signed data. re-initiate
 *          the signature with the public key. The signature can be verified
 *          by acquired signed data.
 * @key randomness
 * @library ../../../lib/testlibrary
 * @run main SignatureTest MD2withRSA 512
 * @run main SignatureTest MD5withRSA 512
 * @run main SignatureTest SHA1withRSA 512
 * @run main SignatureTest SHA256withRSA 512
 * @run main SignatureTest MD2withRSA 768
 * @run main SignatureTest MD5withRSA 768
 * @run main SignatureTest SHA1withRSA 768
 * @run main SignatureTest SHA256withRSA 768
 * @run main SignatureTest MD2withRSA 1024
 * @run main SignatureTest MD5withRSA 1024
 * @run main SignatureTest SHA1withRSA 1024
 * @run main SignatureTest SHA256withRSA 1024
 * @run main SignatureTest MD2withRSA 2048
 * @run main SignatureTest MD5withRSA 2048
 * @run main SignatureTest SHA1withRSA 2048
 * @run main SignatureTest SHA256withRSA 2048
 * @run main/timeout=240 SignatureTest MD2withRSA 4096
 * @run main/timeout=240 SignatureTest MD5withRSA 4096
 * @run main/timeout=240 SignatureTest SHA1withRSA 4096
 * @run main/timeout=240 SignatureTest SHA256withRSA 4096
 * @run main/timeout=240 SignatureTest MD2withRSA 5120
 * @run main/timeout=240 SignatureTest MD5withRSA 5120
 * @run main/timeout=240 SignatureTest SHA1withRSA 5120
 * @run main/timeout=240 SignatureTest SHA256withRSA 5120
 * @run main/timeout=240 SignatureTest MD2withRSA 6144
 * @run main/timeout=240 SignatureTest MD5withRSA 6144
 * @run main/timeout=240 SignatureTest SHA1withRSA 6144
 * @run main/timeout=240 SignatureTest SHA256withRSA 6144
 */
public class SignatureTest {
    /**
     * ALGORITHM name, fixed as RSA.
     */
    private static final String KEYALG = "RSA";

    /**
     * JDK default RSA Provider.
     */
    private static final String PROVIDER = "SunRsaSign";

    /**
     * How much times signature updated.
     */
    private static final int UPDATE_TIMES_FIFTY = 50;

    /**
     * How much times signature initial updated.
     */
    private static final int UPDATE_TIMES_HUNDRED = 100;

    public static void main(String[] args) throws Exception {
        String testAlg = args[0];
        int testSize = Integer.parseInt(args[1]);

        byte[] data = new byte[100];
        RandomFactory.getRandom().nextBytes(data);

        // create a key pair
        KeyPair kpair = generateKeys(KEYALG, testSize);
        Key[] privs = manipulateKey(PRIVATE_KEY, kpair.getPrivate());
        Key[] pubs = manipulateKey(PUBLIC_KEY, kpair.getPublic());
        // For signature algorithm, create and verify a signature

        Arrays.stream(privs).forEach(priv
                -> Arrays.stream(pubs).forEach(pub -> {
                    try {
                        checkSignature(data, (PublicKey) pub, (PrivateKey) priv,
                                testAlg);
                    } catch (NoSuchAlgorithmException | InvalidKeyException
                            | SignatureException | NoSuchProviderException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                ));

    }

    private static KeyPair generateKeys(String keyalg, int size)
            throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(keyalg);
        kpg.initialize(size);
        return kpg.generateKeyPair();
    }

    private static Key[] manipulateKey(int type, Key key)
            throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
        KeyFactory kf = KeyFactory.getInstance(KEYALG, PROVIDER);

        switch (type) {
            case PUBLIC_KEY:
                try {
                    kf.getKeySpec(key, RSAPrivateKeySpec.class);
                    throw new RuntimeException("Expected InvalidKeySpecException "
                            + "not thrown");
                } catch (InvalidKeySpecException expected) {
                }

                return new Key[]{
                    kf.generatePublic(kf.getKeySpec(key, RSAPublicKeySpec.class)),
                    kf.generatePublic(new X509EncodedKeySpec(key.getEncoded())),
                    kf.generatePublic(new RSAPublicKeySpec(
                    ((RSAPublicKey) key).getModulus(),
                    ((RSAPublicKey) key).getPublicExponent()))
                };
            case PRIVATE_KEY:
                try {
                    kf.getKeySpec(key, RSAPublicKeySpec.class);
                    throw new RuntimeException("Expected InvalidKeySpecException"
                            + " not thrown");
                } catch (InvalidKeySpecException expected) {
                }
                return new Key[]{
                    kf.generatePrivate(kf.getKeySpec(key,
                    RSAPrivateKeySpec.class)),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(
                    key.getEncoded())),
                    kf.generatePrivate(new RSAPrivateKeySpec(((RSAPrivateKey) key).getModulus(),
                    ((RSAPrivateKey) key).getPrivateExponent()))
                };
        }
        throw new RuntimeException("We shouldn't reach here");
    }

    private static void checkSignature(byte[] data, PublicKey pub,
            PrivateKey priv, String sigalg) throws NoSuchAlgorithmException,
            InvalidKeyException, SignatureException, NoSuchProviderException {
        Signature sig = Signature.getInstance(sigalg, PROVIDER);
        sig.initSign(priv);
        for (int i = 0; i < UPDATE_TIMES_HUNDRED; i++) {
            sig.update(data);
        }
        byte[] signedData = sig.sign();

        // Make sure signature verifies with original data
        sig.initVerify(pub);
        for (int i = 0; i < UPDATE_TIMES_HUNDRED; i++) {
            sig.update(data);
        }
        if (!sig.verify(signedData)) {
            throw new RuntimeException("Failed to verify " + sigalg
                    + " signature");
        }

        // Make sure signature does NOT verify when the original data
        // has changed
        sig.initVerify(pub);
        for (int i = 0; i < UPDATE_TIMES_FIFTY; i++) {
            sig.update(data);
        }

        if (sig.verify(signedData)) {
            throw new RuntimeException("Failed to detect bad " + sigalg
                    + " signature");
        }
    }
}
