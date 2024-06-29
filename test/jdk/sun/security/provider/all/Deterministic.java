/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8325506
 * @library /test/lib
 * @modules java.base/sun.security.util
 * @run main/othervm Deterministic
 * @summary confirm the output of random calculations are determined
 *          by the SecureRandom parameters
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.security.SeededSecureRandom;
import sun.security.util.SignatureUtil;

import javax.crypto.Cipher;
import javax.crypto.KEM;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.DSAParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.Objects;

public class Deterministic {

    private static final long SEED = SeededSecureRandom.seed();
    private static int hash = 0;

    public static void main(String[] args) throws Exception {

        for (var p : Security.getProviders()) {
            var name = p.getName();
            if (name.equals("SunMSCAPI") || name.startsWith("SunPKCS11")) {
                System.out.println("Skipped native provider " + name);
                continue;
            }
            for (var s : p.getServices()) {
                switch (s.getType()) {
                    case "KeyPairGenerator" -> testKeyPairGenerator(s);
                    case "KeyGenerator" -> testKeyGenerator(s);
                    case "Signature" -> testSignature(s);
                    case "KEM" -> testKEM(s);
                    case "KeyAgreement" -> testKeyAgreement(s);
                    case "Cipher" -> testCipher(s);
                    case "AlgorithmParameterGenerator" -> testAlgorithmParameterGenerator(s);
                }
            }
        }
        // Run twice and this value should be the same for the same SEED
        System.out.println("Final hash: " + hash);
    }

    static void testCipher(Provider.Service s) throws Exception {
        var alg = s.getAlgorithm();
        System.out.println(s.getProvider().getName()
                + " " + s.getType() + "." + alg);
        if (alg.contains("Wrap") || alg.contains("KW")) {
            System.out.println("    Ignored");
            return;
        }
        Key key;
        AlgorithmParameterSpec spec;
        if (alg.startsWith("PBE")) {
            key = new SecretKeySpec("isthisakey".getBytes(StandardCharsets.UTF_8), "PBE");
            // Some cipher requires salt to be 8 byte long
            spec = new PBEParameterSpec("saltsalt".getBytes(StandardCharsets.UTF_8), 100);
        } else {
            key = generateKey(alg.split("/")[0], s.getProvider());
            if (!alg.contains("/") || alg.contains("/ECB/")) {
                spec = null;
            } else {
                if (alg.contains("/GCM/")) {
                    spec = new GCMParameterSpec(128, new SeededSecureRandom(SEED + 1).generateSeed(16));
                } else if (alg.equals("ChaCha20")) {
                    spec = new ChaCha20ParameterSpec(new SeededSecureRandom(SEED + 2).generateSeed(12), 128);
                } else if (alg.contains("ChaCha20")) {
                    spec = new IvParameterSpec(new SeededSecureRandom(SEED + 3).generateSeed(12));
                } else {
                    spec = new IvParameterSpec(new SeededSecureRandom(SEED + 4).generateSeed(16));
                }
            }
        }
        var c = Cipher.getInstance(alg, s.getProvider());
        c.init(Cipher.ENCRYPT_MODE, key, spec, new SeededSecureRandom(SEED));
        // Some cipher requires plaintext to be 16 byte long
        var ct1 = c.doFinal("asimpleplaintext".getBytes(StandardCharsets.UTF_8));
        // Some cipher requires IV to be different, so re-instantiate a cipher
        c = Cipher.getInstance(alg, s.getProvider());
        c.init(Cipher.ENCRYPT_MODE, key, spec, new SeededSecureRandom(SEED));
        var ct2 = c.doFinal("asimpleplaintext".getBytes(StandardCharsets.UTF_8));
        Asserts.assertEqualsByteArray(ct1, ct2);
        hash = Objects.hash(hash, Arrays.hashCode(ct1));
        System.out.println("    Passed");
    }

    static void testAlgorithmParameterGenerator(Provider.Service s) throws Exception {
        System.out.println(s.getProvider().getName()
                + " " + s.getType() + "." + s.getAlgorithm());
        var apg = AlgorithmParameterGenerator.getInstance(s.getAlgorithm(), s.getProvider());
        apg.init(1024, new SeededSecureRandom(SEED));
        var p1 = apg.generateParameters().getParameterSpec(AlgorithmParameterSpec.class);
        apg.init(1024, new SeededSecureRandom(SEED));
        var p2 = apg.generateParameters().getParameterSpec(AlgorithmParameterSpec.class);
        if (p1 instanceof DSAParameterSpec d1 && p2 instanceof DSAParameterSpec d2) {
            Asserts.assertEQ(d1.getG(), d2.getG());
            Asserts.assertEQ(d1.getP(), d2.getP());
            Asserts.assertEQ(d1.getQ(), d2.getQ());
            hash = Objects.hash(hash, d1.getG(), d1.getP(), d1.getQ());
        } else if (p1 instanceof DHParameterSpec d1 && p2 instanceof DHParameterSpec d2){
            Asserts.assertEQ(d1.getG(), d2.getG());
            Asserts.assertEQ(d1.getP(), d2.getP());
            Asserts.assertEQ(d1.getL(), d2.getL());
            hash = Objects.hash(hash, d1.getG(), d1.getP(), d1.getL());
        } else {
            Asserts.assertEQ(p1, p2);
            hash = Objects.hash(hash, p1);
        }
        System.out.println("    Passed");
    }

    private static void testSignature(Provider.Service s) throws Exception {
        System.out.println(s.getProvider().getName()
                + " " + s.getType() + "." + s.getAlgorithm());
        String keyAlg = SignatureUtil.extractKeyAlgFromDwithE(s.getAlgorithm());
        if (keyAlg == null) {
            if (s.getAlgorithm().equals("HSS/LMS")) {
                // We don't support HSS/LMS key generation and signing
                System.out.println("    Ignored: HSS/LMS");
                return;
            } else {
                keyAlg = s.getAlgorithm(); // EdDSA etc
            }
        }
        var sk = generateKeyPair(keyAlg, 0).getPrivate();
        var sig = Signature.getInstance(s.getAlgorithm(), s.getProvider());
        try {
            if (keyAlg.equals("RSASSA-PSS")) {
                sig.setParameter(PSSParameterSpec.DEFAULT);
            }
            sig.initSign(sk, new SeededSecureRandom(SEED));
            sig.update(new byte[20]);
            var s1 = sig.sign();
            sig.initSign(sk, new SeededSecureRandom(SEED));
            sig.update(new byte[20]);
            var s2 = sig.sign();
            Asserts.assertEqualsByteArray(s1, s2);
            hash = Objects.hash(hash, Arrays.hashCode(s1));
            System.out.println("    Passed");
        } catch (InvalidKeyException ike) {
            System.out.println("    Ignored: " + ike.getMessage());
        }
    }

    static void testKeyPairGenerator(Provider.Service s) throws Exception {
        System.out.println(s.getProvider().getName()
                + " " + s.getType() + "." + s.getAlgorithm());
        var kp1 = generateKeyPair(s.getAlgorithm(), 0);
        var kp2 = generateKeyPair(s.getAlgorithm(), 0);
        Asserts.assertEqualsByteArray(
                kp1.getPrivate().getEncoded(), kp2.getPrivate().getEncoded());
        Asserts.assertEqualsByteArray(
                kp1.getPublic().getEncoded(), kp2.getPublic().getEncoded());
        hash = Objects.hash(hash,
                Arrays.hashCode(kp1.getPrivate().getEncoded()),
                Arrays.hashCode(kp1.getPublic().getEncoded()));
        System.out.println("    Passed");
    }

    static KeyPair generateKeyPair(String alg, int offset) throws Exception {
        var g = KeyPairGenerator.getInstance(alg);
        var size = switch (g.getAlgorithm()) {
            case "RSA", "RSASSA-PSS", "DSA", "DiffieHellman" -> 1024;
            case "EC" -> 256;
            case "EdDSA", "Ed25519", "XDH", "X25519" -> 255;
            case "Ed448", "X448" -> 448;
            default -> throw new UnsupportedOperationException(alg);
        };
        g.initialize(size, new SeededSecureRandom(SEED + offset));
        return g.generateKeyPair();
    }

    static void testKeyGenerator(Provider.Service s) throws Exception {
        System.out.println(s.getProvider().getName()
                + " " + s.getType() + "." + s.getAlgorithm());
        if (s.getAlgorithm().startsWith("SunTls")) {
            System.out.println("    Ignored");
            return;
        }
        var k1 = generateKey(s.getAlgorithm(), s.getProvider());
        var k2 = generateKey(s.getAlgorithm(), s.getProvider());
        Asserts.assertEqualsByteArray(k1.getEncoded(), k2.getEncoded());
        hash = Objects.hash(hash,
                Arrays.hashCode(k1.getEncoded()));
        System.out.println("    Passed");
    }

    static Key generateKey(String s, Provider p) throws Exception {
        if (s.startsWith("AES_")) {
            var g = KeyGenerator.getInstance("AES", p);
            g.init(Integer.parseInt(s.substring(4)), new SeededSecureRandom(SEED + 1));
            return g.generateKey();
        } if (s.startsWith("ChaCha")) {
            var g = KeyGenerator.getInstance("ChaCha20", p);
            g.init(new SeededSecureRandom(SEED + 2));
            return g.generateKey();
        } if (s.equals("RSA")) {
            return generateKeyPair("RSA", 3).getPublic();
        } else {
            var g = KeyGenerator.getInstance(s, p);
            g.init(new SeededSecureRandom(SEED + 4));
            return g.generateKey();
        }
    }

    static void testKEM(Provider.Service s) throws Exception {
        System.out.println(s.getProvider().getName()
                + " " + s.getType() + "." + s.getAlgorithm());
        String keyAlg = getKeyAlgFromKEM(s.getAlgorithm());
        var kp = generateKeyPair(keyAlg, 10);
        var kem = KEM.getInstance(s.getAlgorithm(), s.getProvider());
        var e1 = kem.newEncapsulator(kp.getPublic(), null, new SeededSecureRandom(SEED));
        var enc1 = e1.encapsulate();
        var e2 = kem.newEncapsulator(kp.getPublic(), null, new SeededSecureRandom(SEED));
        var enc2 = e2.encapsulate();
        Asserts.assertEqualsByteArray(enc1.encapsulation(), enc2.encapsulation());
        Asserts.assertEqualsByteArray(enc1.key().getEncoded(), enc2.key().getEncoded());
        hash = Objects.hash(hash, Arrays.hashCode(enc1.encapsulation()),
                Arrays.hashCode(enc1.key().getEncoded()));
        System.out.println("    Passed");
    }

    static void testKeyAgreement(Provider.Service s) throws Exception {
        System.out.println(s.getProvider().getName()
                + " " + s.getType() + "." + s.getAlgorithm());
        String keyAlg = getKeyAlgFromKEM(s.getAlgorithm());
        var kpS = generateKeyPair(keyAlg, 11);
        var kpR = generateKeyPair(keyAlg, 12);
        var ka = KeyAgreement.getInstance(s.getAlgorithm(), s.getProvider());
        ka.init(kpS.getPrivate(), new SeededSecureRandom(SEED));
        ka.doPhase(kpR.getPublic(), true);
        var sc1 = ka.generateSecret();
        ka.init(kpS.getPrivate(), new SeededSecureRandom(SEED));
        ka.doPhase(kpR.getPublic(), true);
        var sc2 = ka.generateSecret();

        Asserts.assertEqualsByteArray(sc1, sc2);
        hash = Objects.hash(hash, Arrays.hashCode(sc1));
        System.out.println("    Passed");
    }

    static String getKeyAlgFromKEM(String algorithm) {
        return switch (algorithm) {
            case "DHKEM" -> "X25519";
            case "ECDH" -> "EC";
            default -> algorithm;
        };
    }
}
