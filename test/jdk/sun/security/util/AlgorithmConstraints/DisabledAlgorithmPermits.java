/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.util.DisabledAlgorithmConstraints;

import java.security.CryptoPrimitive;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/*
 * @test
 * @bug 8346129
 * @modules java.base/sun.security.util
 * @summary Check DisabledAlgorithmConstraints using EdDSA & XDH against
 * permit(Set<CryptoPrimitive>, String, AP) and
 * permit(Set<CryptoPrimitive>, Key).  Results will differ based
 * on method used.  The first method only can compare the String with the
 * algorithms.  The second can check the algorithm and NamedParameterSpec
 * while results in more 'false' cases.
 *
 * @run main/othervm DisabledAlgorithmPermits Ed25519
 * @run main/othervm DisabledAlgorithmPermits Ed448
 * @run main/othervm DisabledAlgorithmPermits EdDSA
 * @run main/othervm DisabledAlgorithmPermits X25519
 * @run main/othervm DisabledAlgorithmPermits X448
 * @run main/othervm DisabledAlgorithmPermits XDH
 */

public class DisabledAlgorithmPermits {
    public static void main(String[] args) throws Exception {
        String algorithm = args[0];
        Security.setProperty("x", algorithm);

        // Expected table are the expected results from the test
        List<TestCase> expected = switch (algorithm) {
            case "Ed25519" ->
                Arrays.asList(
                    new TestCase("EdDSA", true),
                    new TestCase("Ed25519", false),
                    new TestCase("Ed448", true),
                    new TestCase("X448", true),
                    new TestCase(1,"EdDSA", false),
                    new TestCase(1,"Ed25519", false),
                    new TestCase(1,"Ed448", true),
                    new TestCase(1,"X448", true));
            case "Ed448" ->
                Arrays.asList(
                    new TestCase("EdDSA", true),
                    new TestCase("Ed25519", true),
                    new TestCase("Ed448", false),
                    new TestCase("X448", true),
                    new TestCase(1,"EdDSA", true),
                    new TestCase(1,"Ed25519", true),
                    new TestCase(1,"Ed448", false),
                    new TestCase(1,"X448", true));
            case "EdDSA" ->
                Arrays.asList(
                    new TestCase("EdDSA", false),
                    new TestCase("Ed25519", true),
                    new TestCase("Ed448", true),
                    new TestCase("X448", true),
                    new TestCase(1,"EdDSA", false),
                    new TestCase(1,"Ed25519", false),
                    new TestCase(1,"Ed448", false),
                    new TestCase(1,"X448", true));
            case "X25519" ->
                Arrays.asList(
                    new TestCase("XDH", true),
                    new TestCase("X25519", false),
                    new TestCase("X448", true),
                    new TestCase("Ed448", true),
                    new TestCase(1, "XDH", false),
                    new TestCase(1, "X25519", false),
                    new TestCase(1, "X448", true),
                    new TestCase(1, "Ed448", true));
            case "X448" ->
                Arrays.asList(
                    new TestCase("XDH", true),
                    new TestCase("X25519", true),
                    new TestCase("X448", false),
                    new TestCase("Ed448", true),
                    new TestCase(1, "XDH", true),
                    new TestCase(1, "X25519", true),
                    new TestCase(1, "X448", false),
                    new TestCase(1, "Ed448", true));
            case "XDH" ->
                Arrays.asList(
                    new TestCase("XDH", false),
                    new TestCase("X25519", true),
                    new TestCase("X448", true),
                    new TestCase("Ed448", true),
                    new TestCase(1, "XDH", false),
                    new TestCase(1, "X25519", false),
                    new TestCase(1, "X448", false),
                    new TestCase(1, "Ed448", true));
            default -> null;
        };

        Objects.requireNonNull(expected, "algorithm being tested " +
            algorithm + " not in expected table");
        System.out.println("---");
        var dac = new DisabledAlgorithmConstraints("x");
        System.out.println("disabled algorithms = " + Security.getProperty("x"));

        // Using only testType 0, this tests that permit(Set<>, String, null)
        // will check only the algorithm against the disabled list
        expected.stream().filter(n->n.testType == 0).forEach(tc -> {
            boolean r = dac.permits(Set.of(CryptoPrimitive.SIGNATURE),
                    tc.testAlgo, null);
            System.out.print("\tpermits(Set.of(CryptoPrimitive.SIGNATURE), \"" +
                tc.testAlgo + "\", null): " + r + " : " );
            if (r != tc.expected) {
                System.out.println("failed.");
                throw new AssertionError("failed.  Expected " +
                    tc.expected);
            }
            System.out.println("pass");
        });

        // Using only testType 1, this tests permit(Set<>, Key) that will look
        // at both the key.getAlgorithm() and the key.getParams().getName()
        // against the disabled list
        expected.stream().filter(n->n.testType == 1).forEach(tc -> {
            PrivateKey k;
            try {
                k = KeyPairGenerator.getInstance(tc.testAlgo).generateKeyPair().
                    getPrivate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            boolean r = dac.permits(Set.of(CryptoPrimitive.SIGNATURE), k);
            System.out.print("\tpermits(Set.of(CryptoPrimitive.SIGNATURE), " +
                tc.testAlgo + " privkey): " + r + " : " );
            if (r != tc.expected) {
                System.out.println("failed.");
                throw new AssertionError("failed.  Expected " +
                    tc.expected);
            }
            System.out.println("pass");
        });
        System.out.println("---");
    }

    record TestCase(int testType, String testAlgo, boolean expected) {
        TestCase(String testAlgo, boolean expected) {
            this(0, testAlgo, expected);
        }
    }
}

