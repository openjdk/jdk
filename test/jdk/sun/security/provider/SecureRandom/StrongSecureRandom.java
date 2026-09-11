/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6425477 8141039 8324648
 * @library /test/lib
 * @summary Better support for generation of high entropy random numbers
 * @run junit StrongSecureRandom
 */

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.SecureRandomParameters;
import java.security.Security;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * This test assumes that the standard Sun providers are installed.
 */
public class StrongSecureRandom {

    private static final String OS = System.getProperty("os.name", "unknown");

    private static Stream<String> nativePrngAlgorithms() {
        return Stream.of("NativePRNG",
                "NativePRNGNonBlocking",
                "NativePRNGBlocking");
    }

    private static Stream<Arguments> strongAlgorithmsProperties() {
        return Stream.of(Arguments.of("", false),
                Arguments.of("SHA1PRNG", true),
                Arguments.of(" SHA1PRNG", true),
                Arguments.of("SHA1PRNG ", true),
                Arguments.of(" SHA1PRNG ", true),
                Arguments.of("SHA1PRNG:SUN", true),
                Arguments.of("Sha1PRNG:SUN", true),
                Arguments.of("SHA1PRNG:Sun", false),
                Arguments.of(" SHA1PRNG:SUN", true),
                Arguments.of("SHA1PRNG:SUN ", true),
                Arguments.of(" SHA1PRNG:SUN ", true),
                Arguments.of(" SHA1PRNG:SUn", false),
                Arguments.of("SHA1PRNG:SUn ", false),
                Arguments.of(" SHA1PRNG:SUn ", false),
                Arguments.of(",,,SHA1PRNG", true),
                Arguments.of(",,, SHA1PRNG", true),
                Arguments.of(" , , ,SHA1PRNG ", true),
                Arguments.of(",,,, SHA1PRNG ,,,", true),
                Arguments.of(",,,, SHA1PRNG:SUN ,,,", true),
                Arguments.of(",,,, SHA1PRNG:SUn ,,,", false),
                Arguments.of(",,,SHA1PRNG:Sun,, SHA1PRNG:SUN", true),
                Arguments.of(",,,Sha1PRNG:Sun, SHA1PRNG:SUN", true),
                Arguments.of(" SHA1PRNG:Sun, Sha1PRNG:Sun,,,,Sha1PRNG:SUN",
                        true),
                Arguments.of(",,,SHA1PRNG:Sun,, SHA1PRNG:SUn", false),
                Arguments.of(",,,Sha1PRNG:Sun, SHA1PRNG:SUn", false),
                Arguments.of(" SHA1PRNG:Sun, Sha1PRNG:Sun,,,,Sha1PRNG:SUn",
                        false),
                Arguments.of(" @#%,%$#:!%^, NativePRNG:Sun, Sha1PRNG:Sun,,Sha1PRNG:SUN",
                        true),
                Arguments.of(" @#%,%$#!%^, NativePRNG:Sun, Sha1PRNG:Sun,,Sha1PRNG:SUn",
                        false));
    }

    @Test
    public void testDefaultEgd() {
        final String src = Security.getProperty("securerandom.source");

        System.out.println("Testing: default EGD: " + src);
        assertEquals("file:/dev/random", src,
                "Unexpected default");
    }

    @ParameterizedTest(name = "testNativePRNGImpl {0}")
    @MethodSource("nativePrngAlgorithms")
    public void testNativePRNGImpl(final String algorithm)
            throws NoSuchAlgorithmException {
        // 'assuming' in order to skip the test if needed
        assumeFalse(OS.startsWith("Windows"), "Skip Windows testing");
        assumeFalse(OS.equals("Linux") &&
                    algorithm.equals("NativePRNGBlocking"),
                "Skip Linux blocking test");


        System.out.println("Testing " + algorithm);
        final SecureRandom sr = SecureRandom.getInstance(algorithm);
        assertEquals(algorithm, sr.getAlgorithm(), "Unexpected algorithm");

        final byte[] seed = sr.generateSeed(1);
        sr.nextBytes(seed);
        sr.setSeed(seed);
    }

    @ParameterizedTest(name = "testUnsupportedParams {0}")
    @MethodSource("nativePrngAlgorithms")
    public void testUnsupportedParams(final String alg) {
        // 'assuming' in order to skip the test if needed
        assumeFalse(OS.startsWith("Windows"), "Skip Windows testing");
        assumeFalse(OS.equals("Linux") &&
                    alg.equals("NativePRNGBlocking"),
                "Skip Linux blocking test");

        System.out.println("Testing that " + alg + " does not support params");

        final NoSuchAlgorithmException exception = assertThrows(
                NoSuchAlgorithmException.class,
                () -> SecureRandom.getInstance(
                        alg, new SecureRandomParameters() {
                        })
        );

        final Throwable cause = exception.getCause();
        if (cause instanceof IllegalArgumentException) {
            assertTrue(cause.getMessage().contains("Unsupported params"),
                    "Unsupported params not recorded: " + cause.getMessage());
        } else {
            throw new AssertionError("Wrong exception ", exception);
        }
    }

    @Test
    public void testDefaultStrongInstance() {
        testStrongInstanceAvailability(true);
    }

    /*
     * This test assumes that the standard providers are installed.
     */
    @ParameterizedTest(name = "testProperties value='{0}', expected={1}")
    @MethodSource("strongAlgorithmsProperties")
    public void testProperties(final String property, final boolean expected) {
        System.out.println("Testing: '" + property + "' " + expected);
        final String origStrongAlgoProp
                = Security.getProperty("securerandom.strongAlgorithms");
        try {
            Security.setProperty("securerandom.strongAlgorithms", property);
            testStrongInstanceAvailability(expected);
        } finally {
            Security.setProperty(
                    "securerandom.strongAlgorithms", origStrongAlgoProp);
        }
    }

    private void testStrongInstanceAvailability(
            final boolean expected) {
        if (expected) {
            assertDoesNotThrow(SecureRandom::getInstanceStrong);
        } else {
            assertThrows(NoSuchAlgorithmException.class,
                    SecureRandom::getInstanceStrong);
        }
    }

    /*
     * Linux tends to block, so ignore anything that reads /dev/random.
     */
    private void handleLinuxRead(final SecureRandom sr) {
        if (OS.equals("Linux")) {
            if (!sr.getAlgorithm().equalsIgnoreCase("NativePRNGBlocking")) {
                sr.nextBytes(new byte[34]);
            }
        } else {
            sr.nextBytes(new byte[34]);
            sr.generateSeed(34);
            sr.setSeed(new byte[34]);
        }
    }

    /*
     * This is duplicating stuff above, but just iterate over all impls
     * just in case we missed something.
     */
    @Test
    public void testAllImpls() throws Exception {
        System.out.print("Testing:  AllImpls:  ");

        for (final String algorithm : Security.getAlgorithms("SecureRandom")) {
            System.out.print(" /" + algorithm);
            SecureRandom secureRandom = SecureRandom.getInstance(algorithm);
            handleLinuxRead(secureRandom);
            handleLinuxRead(secureRandom);
        }
        System.out.println("/");
    }
}
