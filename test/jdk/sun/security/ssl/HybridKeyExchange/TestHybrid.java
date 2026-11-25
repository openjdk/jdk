/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8379433 8385968
 * @summary Test TLS 1.3 hybrid KEM key generation, encapsulation,
 *          decapsulation, validation, and secret handling.
 * @modules java.base/sun.security.ssl
 * @run main/othervm TestHybrid
*/
import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.InvalidKeyException;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.SecretKey;

public class TestHybrid {

    private record HybridGroup(String name, int publicKeyLength) { }

    private static final HybridGroup[] HYBRID_GROUPS = {
            new HybridGroup("X25519MLKEM768", 1216),
            new HybridGroup("SecP256r1MLKEM768", 1249),
            new HybridGroup("SecP384r1MLKEM1024", 1665)
    };

    public static void main(String[] args) throws Exception {

        Provider p = Security.getProvider("SunJCE");
        if (p == null) {
            throw new RuntimeException("SunJCE provider not found");
        }
        if (p.getService("KEM", "DH") != null) {
            throw new RuntimeException("SunJCE exposes internal KEM.DH");
        }

        for (int i = 0; i < HYBRID_GROUPS.length; i++) {
            HybridGroup group = HYBRID_GROUPS[i];
            HybridGroup otherGroup = HYBRID_GROUPS[
                    (i + 1) % HYBRID_GROUPS.length];
            testKPG(p, group);
            testRawKeyLen(p, group);
            testKEM(p, group, otherGroup);
        }
    }

    private static void testKPG(Provider provider,
            HybridGroup group) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(group.name,
                provider);
        KeyPair kp = kpg.generateKeyPair();

        if (!group.name.equals(kp.getPublic().getAlgorithm()) ||
                !group.name.equals(kp.getPrivate().getAlgorithm())) {
            throw new RuntimeException("Unexpected hybrid key algorithm for " +
                    group.name);
        }
        if (kp.getPublic().getEncoded().length != group.publicKeyLength) {
            throw new RuntimeException("Unexpected public key length for " +
                    group.name);
        }

        kpg.initialize(new NamedParameterSpec(group.name), null);
        try {
            kpg.initialize(new NamedParameterSpec("X448"), null);
            throw new RuntimeException("Accepted incorrect parameters for " +
                    group.name);
        } catch (InvalidAlgorithmParameterException expected) {
            checkMessage(expected, "Expected NamedParameterSpec for " +
                    group.name);
            System.out.println("Rejected incorrect parameters for " +
                    group.name);
        }
        kpg.initialize(-1, null);
        kp = kpg.generateKeyPair();
        if (kp.getPublic().getEncoded().length != group.publicKeyLength) {
            throw new RuntimeException("Unexpected public key length after " +
                    "key size initialization for " + group.name);
        }
        try {
            kpg.initialize(256, null);
            throw new RuntimeException("Accepted unsupported key size for " +
                    group.name);
        } catch (InvalidParameterException expected) {
            checkMessage(expected, "keysize not supported");
            System.out.println("Rejected unsupported key size for " +
                    group.name);
        }
    }

    private static void testKEM(Provider provider, HybridGroup group,
            HybridGroup otherGroup) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(group.name,
                provider);
        KeyPair kp = kpg.generateKeyPair();

        KEM kem = KEM.getInstance(group.name, provider);
        KEM.Encapsulator e = kem.newEncapsulator(kp.getPublic());
        KEM.Decapsulator d = kem.newDecapsulator(kp.getPrivate());

        int secretSize = e.secretSize();
        KEM.Encapsulated enc = e.encapsulate();
        if (enc.key().getFormat() != null || enc.key().getEncoded() != null) {
            throw new RuntimeException("Hybrid secret must be opaque");
        }
        testNotSerializable(kp.getPrivate(), "hybrid private key",
                "Hybrid private key must not be serialized");
        testNotSerializable(enc.key(), "hybrid shared secret",
                "Hybrid shared secret must not be serialized");
        byte[] ciphertext = enc.encapsulation();
        SecretKey decapsulated = d.decapsulate(ciphertext, 0, secretSize,
                "generic");

        if (!enc.key().equals(decapsulated) ||
                enc.key().hashCode() != decapsulated.hashCode()) {
            throw new RuntimeException("Hybrid shared secrets are not equal");
        }

        testKEMArgs(e, d, ciphertext, secretSize);

        byte[] badCiphertext = Arrays.copyOf(ciphertext,
                ciphertext.length - 1);
        try {
            d.decapsulate(badCiphertext, 0, secretSize, "Generic");
            throw new RuntimeException(
                    "Expected DecapsulateException not thrown");
        } catch (DecapsulateException expected) {
            checkMessage(expected, "Invalid key encapsulation message length: " +
                    badCiphertext.length + ", expected = " + ciphertext.length);
            System.out.println("Expected DecapsulateException thrown");
        }

        try {
            kem.newEncapsulator(kp.getPublic(),
                    new NamedParameterSpec(group.name), null);
            throw new RuntimeException("Accepted KEM parameters");
        } catch (InvalidAlgorithmParameterException expected) {
            checkMessage(expected, "KEM parameters are not supported for " +
                    group.name);
            System.out.println("Rejected KEM parameters");
        }

        KEM otherKem = KEM.getInstance(otherGroup.name, provider);
        try {
            otherKem.newEncapsulator(kp.getPublic());
            throw new RuntimeException("Accepted " + group.name +
                    " key for " + otherGroup.name);
        } catch (InvalidKeyException expected) {
            checkMessage(expected, "Incorrect public key algorithm");
            System.out.println("Rejected " + group.name +
                    " key for " + otherGroup.name);
        }
    }

    private static void testKEMArgs(KEM.Encapsulator encapsulator,
            KEM.Decapsulator decapsulator, byte[] ciphertext, int secretSize)
            throws Exception {
        try {
            encapsulator.encapsulate(-1, secretSize, "Generic");
            throw new RuntimeException("Accepted negative encapsulation range");
        } catch (IndexOutOfBoundsException expected) {
            checkMessage(expected, "Invalid secret range: from = -1, to = " +
                    secretSize + ", secret size = " + secretSize);
            System.out.println("Rejected negative encapsulation range");
        }

        try {
            decapsulator.decapsulate(ciphertext, 0, secretSize + 1, "Generic");
            throw new RuntimeException("Accepted oversized decapsulation range");
        } catch (IndexOutOfBoundsException expected) {
            checkMessage(expected, "Invalid secret range: from = 0, to = " +
                    (secretSize + 1) + ", secret size = " + secretSize);
            System.out.println("Rejected oversized decapsulation range");
        }

        try {
            encapsulator.encapsulate(0, secretSize - 1, "Generic");
            throw new RuntimeException("Accepted partial encapsulation range");
        } catch (UnsupportedOperationException expected) {
            checkMessage(expected, "Partial hybrid secrets are not supported");
            System.out.println("Rejected partial encapsulation range");
        }

        try {
            decapsulator.decapsulate(ciphertext, 0, secretSize - 1, "Generic");
            throw new RuntimeException("Accepted partial decapsulation range");
        } catch (UnsupportedOperationException expected) {
            checkMessage(expected, "Partial hybrid secrets are not supported");
            System.out.println("Rejected partial decapsulation range");
        }

        try {
            encapsulator.encapsulate(0, secretSize, null);
            throw new RuntimeException("Accepted null encapsulation algorithm");
        } catch (NullPointerException expected) {
            checkMessage(expected, "algorithm must not be null");
            System.out.println("Rejected null encapsulation algorithm");
        }

        try {
            decapsulator.decapsulate(ciphertext, 0, secretSize, null);
            throw new RuntimeException("Accepted null decapsulation algorithm");
        } catch (NullPointerException expected) {
            checkMessage(expected, "algorithm must not be null");
            System.out.println("Rejected null decapsulation algorithm");
        }
    }

    private static void testRawKeyLen(Provider provider, HybridGroup group)
            throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(group.name,
                provider);
        byte[] encoded = kpg.generateKeyPair().getPublic().getEncoded();
        KEM kem = KEM.getInstance(group.name, provider);

        testInvalidLen(kem, group.name,
                Arrays.copyOf(encoded, encoded.length - 1), encoded.length);
        testInvalidLen(kem, group.name,
                Arrays.copyOf(encoded, encoded.length + 1), encoded.length);
    }

    private static void testInvalidLen(KEM kem, String alg,
            byte[] encoded, int expectedLength) throws Exception {
        try {
            kem.newEncapsulator(new RawPublicKey(alg, encoded));
            throw new RuntimeException("Accepted invalid RAW key length for " +
                    alg);
        } catch (InvalidKeyException expected) {
            checkMessage(expected, "Invalid hybrid key length " +
                    encoded.length + ", expected " + expectedLength);
            System.out.println("Rejected invalid RAW key length for " +
                    alg);
        }
    }

    private static void testNotSerializable(Object obj, String name,
            String expectedMessage) throws Exception {
        try (var bytes = new ByteArrayOutputStream();
                var output = new ObjectOutputStream(bytes)) {
            output.writeObject(obj);
            throw new RuntimeException("Serialized " + name);
        } catch (NotSerializableException expected) {
            checkMessage(expected, expectedMessage);
            System.out.println("Rejected serialization of " + name);
        }
    }

    private static void checkMessage(Throwable exception,
            String expectedMessage) {
        String actualMessage = exception.getMessage();
        if (!expectedMessage.equals(actualMessage)) {
            throw new RuntimeException("Unexpected " +
                    exception.getClass().getSimpleName() + " message: expected \"" +
                    expectedMessage + "\", got \"" + actualMessage + "\"",
                    exception);
        }
    }

    private record RawPublicKey(String alg, byte[] encoded)
            implements PublicKey {
        @Override
        public String getAlgorithm() {
            return alg;
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return encoded.clone();
        }
    }
}
