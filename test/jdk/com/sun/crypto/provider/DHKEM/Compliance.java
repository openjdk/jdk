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
 * @summary Key Encapsulation Mechanism API
 * @library /test/lib
 * @modules java.base/com.sun.crypto.provider
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

import com.sun.crypto.provider.DHKEM;

public class Compliance {

    public static void main(String[] args) throws Exception {
        basic();
        conform();
        try {
            Security.insertProviderAt(new ProviderImpl(), 1);
            delayed();
            badprovider();
        } finally {
            Security.removeProvider("XP");
        }
    }

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

        // Cannot detect invalid params before provider selection
//        Utils.runAndCheckException(
//                () -> KEM.getInstance("DHKEM", new KEMParameterSpec() {}),
//                InvalidAlgorithmParameterException.class);

        Utils.runAndCheckException(
                () -> kem.newEncapsulator(null),
                NullPointerException.class);
        Utils.runAndCheckException(
                () -> kem.newDecapsulator(null),
                NullPointerException.class);

        // Still an EC key, rejected by implementation
        Utils.runAndCheckException(
                () -> kem.newEncapsulator(badECKey()),
                ExChecker.of(InvalidKeyException.class).by(DHKEM.class));

        // Not an EC key at all, rejected by framework coz SupportedClasses
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

    public static class ProviderImpl extends Provider {
        ProviderImpl() {
            super("XP", "1", "XP");
            put("KEM.DHKEM", "Compliance$KEMImpl");
            put("KEM.BAD", "Compliance$BadKEMImpl");
        }
    }

    static boolean isEven(Key k) {
        return Arrays.hashCode(k.getEncoded()) % 2 == 0;
    }

    public static class KEMImpl extends DHKEM {

        @Override
        public EncapsulatorSpi engineNewEncapsulator(PublicKey pk, AlgorithmParameterSpec spec, SecureRandom secureRandom)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            if (!isEven(pk)) throw new InvalidKeyException("Only accept even keys");
            return super.engineNewEncapsulator(pk, spec, secureRandom);
        }

        @Override
        public DecapsulatorSpi engineNewDecapsulator(PrivateKey sk, AlgorithmParameterSpec spec)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            if (!isEven(sk)) throw new InvalidKeyException("Only accept even keys");
            return super.engineNewDecapsulator(sk, spec);
        }
    }

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
        Asserts.assertEQ(eodd.provider().getName(), "SunJCE");
        Asserts.assertEQ(eeven.provider().getName(), "XP");
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

    static class BadKEMParameterSpec implements AlgorithmParameterSpec {
        final int ss;
        final int es;
        final SecretKey sk;
        final byte[] encap;
        final boolean noenc;

        public BadKEMParameterSpec(int ss, int es, SecretKey sk, byte[] encap, boolean noenc) {
            this.ss = ss;
            this.es = es;
            this.sk = sk;
            this.encap = encap;
            this.noenc = noenc;
        }

        BadKEMParameterSpec ss(int ss) {
            return new BadKEMParameterSpec(ss, es, sk, encap, noenc);
        }

        BadKEMParameterSpec es(int es) {
            return new BadKEMParameterSpec(ss, es, sk, encap, noenc);
        }

        BadKEMParameterSpec sk(SecretKey sk) {
            return new BadKEMParameterSpec(ss, es, sk, encap, noenc);
        }

        BadKEMParameterSpec encap(byte[] encap) {
            return new BadKEMParameterSpec(ss, es, sk, encap, noenc);
        }

        BadKEMParameterSpec noenc(boolean noenc) {
            return new BadKEMParameterSpec(ss, es, sk, encap, noenc);
        }
    }

    static class BadHandler implements KEMSpi.DecapsulatorSpi, KEMSpi.EncapsulatorSpi {
        final BadKEMParameterSpec spec;
        BadHandler(BadKEMParameterSpec spec) {
            this.spec = spec;
        }
        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation, int from, int to, String algorithm) throws DecapsulateException {
            return spec.sk;
        }

        @Override
        public int engineSecretSize() {
            return spec.ss;
        }

        @Override
        public int engineEncapsulationSize() {
            return spec.es;
        }

        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to, String algorithm) {
            return spec.noenc ? null : new KEM.Encapsulated(spec.sk, spec.encap, null);
        }
    }

    public static class BadKEMImpl implements KEMSpi {

        @Override
        public EncapsulatorSpi engineNewEncapsulator(PublicKey pk, AlgorithmParameterSpec spec, SecureRandom secureRandom) {
            return spec == null ? null : new BadHandler((BadKEMParameterSpec) spec);
        }

        @Override
        public DecapsulatorSpi engineNewDecapsulator(PrivateKey sk, AlgorithmParameterSpec spec) {
            return spec == null ? null : new BadHandler((BadKEMParameterSpec) spec);
        }
    }

    static void badprovider() throws Exception {
        KeyPair kpX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        SecretKeySpec k = new SecretKeySpec(new byte[10], "AES");
        Utils.runAndCheckException(() -> KEM.getInstance("BAD").newEncapsulator(kpX.getPublic()),
                ExChecker.of(AssertionError.class).by(KEM.Encapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD").newDecapsulator(kpX.getPrivate()),
                ExChecker.of(AssertionError.class).by(KEM.Decapsulator.class));

        BadKEMParameterSpec good = new BadKEMParameterSpec(10, 10, k, new byte[10], false);
        KEM kem = KEM.getInstance("BAD");
        KEM.Encapsulator e = kem.newEncapsulator(kpX.getPublic(), good, null);
        Asserts.assertEQ(e.secretSize(), 10);
        Asserts.assertEQ(e.encapsulationSize(), 10);
        Asserts.assertTrue(e.encapsulate().key() != null);
        Asserts.assertTrue(e.encapsulate().encapsulation() != null);
        KEM.Decapsulator d = kem.newDecapsulator(kpX.getPrivate(), good);
        Asserts.assertEQ(d.secretSize(), 10);
        Asserts.assertEQ(d.encapsulationSize(), 10);
        Asserts.assertTrue(d.decapsulate(new byte[0]) != null);

        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newEncapsulator(kpX.getPublic(), good.noenc(true), null).encapsulate(),
                ExChecker.of(AssertionError.class).by(KEM.Encapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newEncapsulator(kpX.getPublic(), good.ss(-1), null).secretSize(),
                ExChecker.of(AssertionError.class).by(KEM.Encapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newEncapsulator(kpX.getPublic(), good.ss(Integer.MAX_VALUE), null).secretSize(),
                ExChecker.of(AssertionError.class).by(KEM.Encapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newEncapsulator(kpX.getPublic(), good.es(-1), null).encapsulationSize(),
                ExChecker.of(AssertionError.class).by(KEM.Encapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newEncapsulator(kpX.getPublic(), good.es(Integer.MAX_VALUE), null).encapsulationSize(),
                ExChecker.of(AssertionError.class).by(KEM.Encapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newEncapsulator(kpX.getPublic(), good.sk(null), null).encapsulate(),
                ExChecker.of(NullPointerException.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newEncapsulator(kpX.getPublic(), good.encap(null), null).encapsulate(),
                ExChecker.of(NullPointerException.class));

        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newDecapsulator(kpX.getPrivate(), good.ss(-1)).secretSize(),
                ExChecker.of(AssertionError.class).by(KEM.Decapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newDecapsulator(kpX.getPrivate(), good.ss(Integer.MAX_VALUE)).secretSize(),
                ExChecker.of(AssertionError.class).by(KEM.Decapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newDecapsulator(kpX.getPrivate(), good.es(-1)).encapsulationSize(),
                ExChecker.of(AssertionError.class).by(KEM.Decapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newDecapsulator(kpX.getPrivate(), good.es(Integer.MAX_VALUE)).encapsulationSize(),
                ExChecker.of(AssertionError.class).by(KEM.Decapsulator.class));
        Utils.runAndCheckException(() -> KEM.getInstance("BAD")
                        .newDecapsulator(kpX.getPrivate(), good.sk(null)).decapsulate(new byte[1]),
                ExChecker.of(AssertionError.class).by(KEM.Decapsulator.class));
    }
}
