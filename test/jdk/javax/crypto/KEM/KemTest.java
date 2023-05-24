/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297878
 * @summary KEM API test
 * @library /test/lib
 * @run main/othervm -Djava.security.egd=file:/dev/urandom KemTest
 */
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.crypto.KEM;
import javax.crypto.SecretKey;
import jdk.test.lib.Asserts;

public class KemTest {

    private static final int THREAD_COUNT = 100;
    private static final int THREAD_POOL_SIZE = 20;
    private static final String ALGO = "DHKEM";
    private static final String PROVIDER = "SunJCE";

    public static void main(String[] args) throws Exception {
        KEM kem = KEM.getInstance(ALGO, PROVIDER);
        Asserts.assertEQ(kem.getAlgorithm(), ALGO);
        testSize(kem);
        testParallelEncapsulator(kem, "EC", "secp256r1");
        testParallelEncapsulate(kem, "EC", "secp256r1");
        testParallelDecapsulator(kem, "EC", "secp256r1");
        testParallelDecapsulate(kem, "EC", "secp256r1");
    }

    @FunctionalInterface
    interface GenKeyPair<A, C, K> {

        K gen(A a, C c);
    }
    private static final GenKeyPair<String, String, KeyPair> keyPair = (algo, curveId) -> {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(algo);
            if (curveId != null) {
                kpg.initialize(new ECGenParameterSpec(curveId));
            }
            return kpg.generateKeyPair();
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    };

    /*
     * As per https://www.rfc-editor.org/rfc/rfc9180#name-key-encapsulation-mechanism
     * Nsecret: The length in bytes of a KEM shared secret produced by this KEM.
     * Nenc: The length in bytes of an encapsulated key produced by this KEM.
     */
    private static void testSize(KEM kem) throws Exception {

        @FunctionalInterface
        interface LengthTest<A, C, S, E> {

            void test(A a, C c, S s, E e);
        }
        LengthTest<String, String, Integer, Integer> secretLen = (algo, curveId, nSecret, nEnc) -> {
            try {
                KeyPair kp = keyPair.gen(algo, curveId);
                KEM.Encapsulator encT = kem.newEncapsulator(kp.getPublic());
                Asserts.assertEQ(encT.providerName(), PROVIDER);
                KEM.Encapsulated enc = encT.encapsulate();
                KEM.Encapsulated enc1 = encT.encapsulate();

                KEM kem1 = KEM.getInstance(ALGO, PROVIDER);
                KEM.Encapsulator encT2 = kem1.newEncapsulator(kp.getPublic());
                KEM.Encapsulated enc2 = encT2.encapsulate();

                Asserts.assertEQ(enc.key().getEncoded().length, nSecret);
                Asserts.assertEQ(enc.encapsulation().length, nEnc);

                Asserts.assertEQ(enc.key(), enc.key());
                Asserts.assertTrue(Arrays.equals(enc.key().getEncoded(), enc.key().getEncoded()));
                Asserts.assertTrue(Arrays.equals(enc.encapsulation(), enc.encapsulation()));

                Asserts.assertNE(enc.key(), enc1.key());
                Asserts.assertFalse(Arrays.equals(enc.key().getEncoded(), enc1.key().getEncoded()));
                Asserts.assertFalse(Arrays.equals(enc.encapsulation(), enc1.encapsulation()));

                Asserts.assertNE(enc.key(), enc2.key());
                Asserts.assertFalse(Arrays.equals(enc.key().getEncoded(), enc2.key().getEncoded()));
                Asserts.assertFalse(Arrays.equals(enc.encapsulation(), enc2.encapsulation()));

                SecretKey sk = enc.key();
                KEM.Decapsulator decT = kem.newDecapsulator(kp.getPrivate());
                SecretKey dsk = decT.decapsulate(enc.encapsulation());
                Asserts.assertEQ(decT.providerName(), PROVIDER);
                Asserts.assertEQ(sk, dsk);
                Asserts.assertTrue(Arrays.equals(sk.getEncoded(), dsk.getEncoded()));
                Asserts.assertEQ(encT.encapsulationSize(), decT.encapsulationSize());
                Asserts.assertEQ(encT.secretSize(), decT.secretSize());

                KEM.Encapsulated enc3 = encT.encapsulate(0, encT.secretSize(), "AES");
                KEM.Decapsulator decT1 = kem.newDecapsulator(kp.getPrivate());
                SecretKey dsk1 = decT1.decapsulate(
                        enc3.encapsulation(), 0, decT1.secretSize(), "AES");
                Asserts.assertEQ(dsk1, enc3.key());
                Asserts.assertTrue(Arrays.equals(dsk1.getEncoded(), enc3.key().getEncoded()));

                System.out.println("KEM Secret length:" + algo + ":" + curveId
                        + ":nSecret:" + nSecret + ":nEnc:" + nEnc);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        // Secret length in bytes.
        secretLen.test("EC", "secp256r1", 32, 65);
        secretLen.test("EC", "secp384r1", 48, 97);
        secretLen.test("EC", "secp521r1", 64, 133);
        secretLen.test("X25519", null, 32, 32);
        secretLen.test("X448", null, 64, 56);
        secretLen.test("XDH", null, 32, 32);
        secretLen.test("XDH", "X25519", 32, 32);
        secretLen.test("XDH", "X448", 64, 56);
        try {
            secretLen.test("Ed25519", null, 32, 32);
        } catch (Exception e) {
            if (!e.getMessage().contains("java.security.InvalidKeyException")) {
                throw e;
            }
            System.out.println("Expected Exception: Bad Key type: Ed25519");
        }
        try {
            secretLen.test("RSA", null, 256, 256);
        } catch (Exception e) {
            if (!e.getMessage().contains("java.security.InvalidKeyException")) {
                throw e;
            }
            System.out.println("Expected Exception: Bad Key type: RSA");
        }
    }

    /*
     * As per JavaDoc API,
     * A KEM object is immutable. It is safe to call multiple newEncapsulator and
     * newDecapsulator methods on the same KEM object at the same time.
     */
    private static void testParallelEncapsulator(KEM kem, String algo, String curveId)
            throws Exception {
        KeyPair kp = keyPair.gen(algo, curveId);
        ExecutorService executor = null;
        try {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CompletionService<KEM.Encapsulator> cs = new ExecutorCompletionService<>(executor);
            List<Future<KEM.Encapsulator>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                Callable<KEM.Encapsulator> task = () -> {
                    return kem.newEncapsulator(kp.getPublic());
                };
                futures.add(cs.submit(task));
            }

            KEM.Decapsulator decT = kem.newDecapsulator(kp.getPrivate());
            for (Future<KEM.Encapsulator> future : futures) {
                KEM.Encapsulated enc = future.get().encapsulate();
                Asserts.assertEQ(decT.decapsulate(enc.encapsulation()), enc.key());
            }
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
        System.out.println("Parallel Encapsulator Test: Success");
    }

    /*
     * As per JavaDoc API,
     * Encapsulator and Decapsulator objects are also immutable.
     * It is safe to invoke multiple encapsulate and decapsulate methods on the same
     * Encapsulator or Decapsulator object at the same time.
     */
    private static void testParallelEncapsulate(KEM kem, String algo, String curveId)
            throws Exception {
        KeyPair kp = keyPair.gen(algo, curveId);
        ExecutorService executor = null;
        try {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CompletionService<KEM.Encapsulated> cs = new ExecutorCompletionService<>(executor);
            List<Future<KEM.Encapsulated>> futures = new ArrayList<>();
            KEM.Encapsulator encT = kem.newEncapsulator(kp.getPublic());
            for (int i = 0; i < THREAD_COUNT; i++) {
                Callable<KEM.Encapsulated> task = () -> {
                    return encT.encapsulate();
                };
                futures.add(cs.submit(task));
            }
            KEM.Decapsulator decT = kem.newDecapsulator(kp.getPrivate());
            for (Future<KEM.Encapsulated> future : futures) {
                Asserts.assertEQ(decT.decapsulate(future.get().encapsulation()),
                        future.get().key());
            }
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
        System.out.println("Parallel Encapsulate Test: Success");
    }

    /*
     * As per JavaDoc API,
     * Encapsulator and Decapsulator objects are also immutable.
     * It is safe to invoke multiple encapsulate and decapsulate methods on the same
     * Encapsulator or Decapsulator object at the same time.
     */
    private static void testParallelDecapsulator(KEM kem, String algo, String curveId)
            throws Exception {
        KeyPair kp = keyPair.gen(algo, curveId);
        ExecutorService executor = null;
        try {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CompletionService<KEM.Decapsulator> cs = new ExecutorCompletionService<>(executor);
            List<Future<KEM.Decapsulator>> futures = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                Callable<KEM.Decapsulator> task = () -> {
                    return kem.newDecapsulator(kp.getPrivate());
                };
                futures.add(cs.submit(task));
            }

            KEM.Encapsulated enc = kem.newEncapsulator(kp.getPublic()).encapsulate();
            for (Future<KEM.Decapsulator> decT : futures) {
                Asserts.assertEQ(decT.get().decapsulate(enc.encapsulation()), enc.key());
            }
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
        System.out.println("Parallel Decapsulator Test: Success");
    }

    /*
     * As per JavaDoc API,
     * Encapsulator and Decapsulator objects are also immutable.
     * It is safe to invoke multiple encapsulate and decapsulate methods on the same
     * Encapsulator or Decapsulator object at the same time.
     */
    private static void testParallelDecapsulate(KEM kem, String algo, String curveId)
            throws Exception {
        KeyPair kp = keyPair.gen(algo, curveId);
        ExecutorService executor = null;
        try {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CompletionService<SecretKey> cs = new ExecutorCompletionService<>(executor);
            KEM.Encapsulator encT = kem.newEncapsulator(kp.getPublic());
            KEM.Encapsulated enc = encT.encapsulate();

            KEM.Decapsulator decT = kem.newDecapsulator(kp.getPrivate());
            List<Future<SecretKey>> futures = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                Callable<SecretKey> task = () -> {
                    return decT.decapsulate(enc.encapsulation());
                };
                futures.add(cs.submit(task));
            }
            for (Future<SecretKey> future : futures) {
                Asserts.assertEQ(future.get(), enc.key());
            }
        } finally {
            if (executor != null) {
                executor.shutdown();
            }
        }
        System.out.println("Parallel Decapsulate Test: Success");
    }

}
