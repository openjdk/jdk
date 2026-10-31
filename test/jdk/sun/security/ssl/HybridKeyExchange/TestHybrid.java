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
 * @summary Test expected DecapsulateException thrown by Hybrid KEM implementation
 * @modules java.base/sun.security.ssl
 * @run main/othervm TestHybrid
*/
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Provider;
import java.security.Security;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import javax.crypto.DecapsulateException;
import javax.crypto.KEM;

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

        for (HybridGroup group : HYBRID_GROUPS) {
            testKPG(p, group);
        }

        testKEM(p, HYBRID_GROUPS[0]);
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
            System.out.println("Rejected incorrect parameters for " +
                    group.name);
        }
        kpg.initialize(256, null);
        kp = kpg.generateKeyPair();
        if (kp.getPublic().getEncoded().length != group.publicKeyLength) {
            throw new RuntimeException("Unexpected public key length after " +
                    "key size initialization for " + group.name);
        }
    }

    private static void testKEM(Provider provider, HybridGroup group)
            throws Exception {
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
        byte[] ciphertext = enc.encapsulation();

        byte[] badCiphertext = Arrays.copyOf(ciphertext,
                ciphertext.length - 1);
        try {
            d.decapsulate(badCiphertext, 0, secretSize, "Generic");
            throw new RuntimeException(
                    "Expected DecapsulateException not thrown");
        } catch (DecapsulateException expected) {
            System.out.println("Expected DecapsulateException thrown");
        }

        try {
            kem.newEncapsulator(kp.getPublic(),
                    new NamedParameterSpec(group.name), null);
            throw new RuntimeException("Accepted KEM parameters");
        } catch (InvalidAlgorithmParameterException expected) {
            System.out.println("Rejected KEM parameters");
        }

        KEM otherKem = KEM.getInstance(HYBRID_GROUPS[1].name, provider);
        try {
            otherKem.newEncapsulator(kp.getPublic());
            throw new RuntimeException("Accepted a key for another hybrid group");
        } catch (InvalidKeyException expected) {
            System.out.println("Rejected a key for another hybrid group");
        }
    }
}
