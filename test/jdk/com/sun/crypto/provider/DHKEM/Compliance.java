/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Key Encapsulation Mechanism API
 * @library /test/lib
 * @build java.base/com.sun.crypto.provider.EvenKEMImpl
 * @modules java.base/com.sun.crypto.provider
 * @run main/othervm Compliance
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;

import com.sun.crypto.provider.DHKEM;

import static com.sun.crypto.provider.EvenKEMImpl.isEven;

public class Compliance {

    public static void main(String[] args) throws Exception {
        basic();
        conform();
        determined();
        // Patch an alternate DHKEM in SunEC which is ahead of SunJCE
        // in security provider listing.
        Security.getProvider("SunEC")
                .put("KEM.DHKEM", "com.sun.crypto.provider.EvenKEMImpl");
        delayed();
    }

    // Encapsulated conformance checks
    private static void conform() {
        new KEM.Encapsulated(new SecretKeySpec(new byte[1], "X"), new byte[0], new byte[0]);
        new KEM.Encapsulated(new SecretKeySpec(new byte[1], "X"), new byte[0], null);
        Utils.runAndCheckException(
                () -> new KEM.Encapsulated(null, new byte[0], null),
                NullPointerException.class);
        Utils.runAndCheckException(
                () -> new KEM.Encapsulated(new SecretKeySpec(new byte[1], "X"), null, null),
                NullPointerException.class);
    }

    // basic should and shouldn't behaviors
    static void basic() throws Exception {
        KeyPair kpRSA = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyPair kpX = KeyPairGenerator.getInstance("X25519").generateKeyPair();

        KeyPairGenerator ecg = KeyPairGenerator.getInstance("EC");
        ecg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kpEC = ecg.generateKeyPair();

        KEM.getInstance("DHKEM", (String) null);
        KEM.getInstance("DHKEM", (Provider) null);
        KEM kem = KEM.getInstance("DHKEM");
        Utils.runAndCheckException(
                () -> KEM.getInstance("OLALA"),
                NoSuchAlgorithmException.class);
        Utils.runAndCheckException(
                () -> KEM.getInstance("DHKEM", "NoWhere"),
                NoSuchProviderException.class);
        Utils.runAndCheckException(
                () -> KEM.getInstance("DHKEM", "SunRsaSign"),
                NoSuchAlgorithmException.class);

        Utils.runAndCheckException(
                () -> kem.newEncapsulator(null),
                InvalidKeyException.class);
        Utils.runAndCheckException(
                () -> kem.newDecapsulator(null),
                InvalidKeyException.class);

        // Still an EC key, rejected by implementation
        Utils.runAndCheckException(
                () -> kem.newEncapsulator(badECKey()),
                ExChecker.of(InvalidKeyException.class).by(DHKEM.class));

        // Not an EC key at all, rejected by framework coz it's not
        // listed in "SupportedKeyClasses" in SunJCE.java.
        Utils.runAndCheckException(
                () -> kem.newEncapsulator(kpRSA.getPublic()),
                ExChecker.of(InvalidKeyException.class).by(KEM.class.getName() + "$DelayedKEM"));

        Utils.runAndCheckException(
                () -> kem.newDecapsulator(kpRSA.getPrivate()),
                InvalidKeyException.class);

        kem.newEncapsulator(kpX.getPublic(), null);
        kem.newEncapsulator(kpX.getPublic(), null, null);
        KEM.Encapsulator e2 = kem.newEncapsulator(kpX.getPublic());
        KEM.Encapsulated enc1 = e2.encapsulate(0, e2.secretSize(), "AES");
        Asserts.assertEQ(enc1.key().getEncoded().length, e2.secretSize());
        Asserts.assertEQ(enc1.key().getAlgorithm(), "AES");

        Utils.runAndCheckException(
                () -> e2.encapsulate(-1, 12, "AES"),
                IndexOutOfBoundsException.class);
        Utils.runAndCheckException(
                () -> e2.encapsulate(0, e2.secretSize() + 1, "AES"),
                IndexOutOfBoundsException.class);
        Utils.runAndCheckException(
                () -> e2.encapsulate(0, e2.secretSize(), null),
                NullPointerException.class);

        KEM.Encapsulated enc = e2.encapsulate();
        Asserts.assertEQ(enc.key().getEncoded().length, e2.secretSize());
        Asserts.assertEQ(enc.key().getAlgorithm(), "Generic");

        kem.newDecapsulator(kpX.getPrivate(), null);
        KEM.Decapsulator d = kem.newDecapsulator(kpX.getPrivate());
        d.decapsulate(enc.encapsulation());
        SecretKey dec = d.decapsulate(enc.encapsulation());
        Asserts.assertTrue(Arrays.equals(enc.key().getEncoded(), dec.getEncoded()));

        dec = d.decapsulate(enc.encapsulation(), 0, d.secretSize(), "AES");
        Asserts.assertTrue(Arrays.equals(enc.key().getEncoded(), dec.getEncoded()));

        KEM.Encapsulated encHead = e2.encapsulate(0, 16, "AES");
        Asserts.assertEQ(encHead.key().getEncoded().length, 16);
        Asserts.assertEQ(encHead.key().getAlgorithm(), "AES");
        SecretKey decHead = d.decapsulate(encHead.encapsulation(), 0, 16, "AES");
        Asserts.assertEQ(encHead.key(), decHead);

        KEM.Encapsulated encTail = e2.encapsulate(
                e2.secretSize() - 16, e2.secretSize(), "AES");
        Asserts.assertEQ(encTail.key().getEncoded().length, 16);
        Asserts.assertEQ(encTail.key().getAlgorithm(), "AES");
        SecretKey decTail = d.decapsulate(encTail.encapsulation(),
                d.secretSize() - 16, d.secretSize(), "AES");
        Asserts.assertEQ(encTail.key(), decTail);

        Utils.runAndCheckException(
                () -> d.decapsulate(null),
                NullPointerException.class);
        Utils.runAndCheckException(
                () -> d.decapsulate(enc.encapsulation(), -1, 12, "AES"),
                IndexOutOfBoundsException.class);
        Utils.runAndCheckException(
                () -> d.decapsulate(enc.encapsulation(), 0, d.secretSize() + 1, "AES"),
                IndexOutOfBoundsException.class);
        Utils.runAndCheckException(
                () -> d.decapsulate(enc.encapsulation(), 0, d.secretSize(), null),
                NullPointerException.class);

        KEM.Encapsulator e3 = kem.newEncapsulator(kpEC.getPublic());
        KEM.Encapsulated enc2 = e3.encapsulate();
        KEM.Decapsulator d3 = kem.newDecapsulator(kpX.getPrivate());
        Utils.runAndCheckException(
                () -> d3.decapsulate(enc2.encapsulation()),
                DecapsulateException.class);

        Utils.runAndCheckException(
                () -> d3.decapsulate(new byte[100]),
                DecapsulateException.class);
    }

    static class MySecureRandom extends SecureRandom {
        final Random ran;

        MySecureRandom(long seed) {
            ran = new Random(seed);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            ran.nextBytes(bytes);
        }
    }

    // Same random should generate same key encapsulation messages
    static void determined() throws Exception {
        long seed = new Random().nextLong();
        byte[] enc1 = calcDetermined(seed);
        byte[] enc2 = calcDetermined(seed);
        Asserts.assertTrue(Arrays.equals(enc1, enc2),
                "Undetermined for " + seed);
    }

    static byte[] calcDetermined(long seed) throws Exception {
        SecureRandom random = new MySecureRandom(seed);
        KeyPairGenerator g = KeyPairGenerator.getInstance("XDH");
        g.initialize(NamedParameterSpec.X25519, random);
        PublicKey pk = g.generateKeyPair().getPublic();
        KEM kem = KEM.getInstance("DHKEM");
        kem.newEncapsulator(pk, random); // skip one
        KEM.Encapsulator e = kem.newEncapsulator(pk, random);
        byte[] enc1 = e.encapsulate().encapsulation();
        byte[] enc2 = e.encapsulate().encapsulation();
        Asserts.assertFalse(Arrays.equals(enc1, enc2));
        return enc2;
    }

    // Ensure delayed provider selection
    static void delayed() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("X25519");
        PublicKey even = null, odd = null;
        while (even == null || odd == null) {
            KeyPair kp = g.generateKeyPair();
            if (isEven(kp.getPublic())) {
                even = kp.getPublic();
            }
            if (!isEven(kp.getPublic())) {
                odd = kp.getPublic();
            }
        }
        KEM kem = KEM.getInstance("DHKEM");

        KEM.Encapsulator eodd = kem.newEncapsulator(odd);
        KEM.Encapsulator eeven = kem.newEncapsulator(even);
        Asserts.assertEQ(eodd.providerName(), "SunJCE");
        Asserts.assertEQ(eeven.providerName(), "SunEC");
    }

    static ECPublicKey badECKey() {
        return new ECPublicKey() {
            @Override
            public ECPoint getW() {
                return null;
            }

            @Override
            public String getAlgorithm() {
                return null;
            }

            @Override
            public String getFormat() {
                return null;
            }

            @Override
            public byte[] getEncoded() {
                return new byte[0];
            }

            @Override
            public ECParameterSpec getParams() {
                return null;
            }
        };
    }

    // Used by Utils.runAndCheckException. Checks for type and final thrower.
    record ExChecker(Class<? extends Throwable> ex, String caller)
            implements Consumer<Throwable> {
        ExChecker {
            Objects.requireNonNull(ex);
        }
        static ExChecker of(Class<? extends Throwable> ex) {
            return new ExChecker(ex, null);
        }
        ExChecker by(String caller) {
            return new ExChecker(ex(), caller);
        }
        ExChecker by(Class<?> caller) {
            return new ExChecker(ex(), caller.getName());
        }
        @Override
        public void accept(Throwable t) {
            if (t == null) {
                throw new AssertionError("no exception thrown");
            } else if (!ex.isAssignableFrom(t.getClass())) {
                throw new AssertionError("exception thrown is " + t.getClass());
            } else if (caller == null) {
                return;
            } else if (t.getStackTrace()[0].getClassName().equals(caller)) {
                return;
            } else {
                throw new AssertionError("thrown by " + t.getStackTrace()[0].getClassName());
            }
        }
    }
}
