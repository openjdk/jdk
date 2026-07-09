/*
 * Copyright (c) 2026, IBM Corporation. All rights reserved.
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
 * @bug 8387124
 * @summary Test TLS cipher suite disabling via jdk.tls.disabledAlgorithms,
 *          including matching on bulk cipher components, covering both
 *          visibility and handshake behavior.
 * @library /test/lib
 *          /javax/net/ssl/TLSCommon
 *          /javax/net/ssl/templates
 * @run main/othervm BulkCipherDisabledAlgorithms visibility
 * @run main/othervm BulkCipherDisabledAlgorithms handshake
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.*;

import jdk.test.lib.process.Proc;

import java.security.NoSuchAlgorithmException;
import java.security.Security;

public class BulkCipherDisabledAlgorithms {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new RuntimeException("Missing mode argument");
        }

        String mode = args[0];
        boolean isVisibilityTest = "visibility".equals(mode);
        boolean isHandshakeTest = "handshake".equals(mode);

        if (args.length == 1) {
            List<String[]> tests = buildTests(isVisibilityTest);

            for (String[] test : tests) {
                String suite = test[0];
                String disabled = test[1];
                String expected = test[2];

                System.out.println("=================================================");
                System.out.println("Testing: " + mode +
                        ", suite=" + suite +
                        ", disabled=" + disabled +
                        ", expected=" + expected);

                Proc p = Proc.create(
                        BulkCipherDisabledAlgorithms.class.getName())
                        .args(mode, suite, expected)
                        .secprop("jdk.tls.disabledAlgorithms", disabled)
                        .inheritIO();

                p.start().waitFor(0);
            }

            System.out.println("TEST PASS - OK");
            return;
        }

        String suite = args[1];
        String expected = args[2];
        boolean expectedDisabled = "disabled".equals(expected);

        if (isVisibilityTest) {
            testCipherSuiteVisibility(suite, expectedDisabled);
        }

        if (isHandshakeTest) {
            testHandshake(suite, expectedDisabled);
        }
    }

    // Returns cipher suites for testing.
    // - true: use all supported suites (independent of disabledAlgorithms)
    // - false: use default enabled suites (candidates for handshake)
    private static CipherSuite[] getCipherSuites(boolean useSupportedSuites)
            throws NoSuchAlgorithmException {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        String[] suites = useSupportedSuites
                ? engine.getSupportedCipherSuites()
                : engine.getEnabledCipherSuites();

        return Arrays.stream(suites)
                .map(CipherSuite::cipherSuite)
                .filter(cs -> cs != CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV)
                .toArray(CipherSuite[]::new);
    }

    private static List<String[]> buildTests(boolean useSupportedSuites)
            throws NoSuchAlgorithmException {
        if (useSupportedSuites) {
            // disabledAlgorithms limits supported suites; clear to list all
            Security.setProperty("jdk.tls.disabledAlgorithms", "");
        }

        List<String[]> tests = new ArrayList<>();
        CipherSuite[] suites = getCipherSuites(useSupportedSuites);

        for (CipherSuite suite : suites) {
            String suiteName = suite.name();
            String bulk = extractBulkCipher(suiteName);

            tests.add(new String[] { suiteName, suiteName, "disabled" });
            tests.add(new String[] { suiteName, bulk, "disabled" });

            for (CipherSuite other : suites) {
                // Negative test case: disable a different bulk cipher than the one
                // used by the current suite. This ensures that the suite remains
                // enabled and a successful TLS handshake can still be negotiated.
                if (other == suite) {
                    continue;
                }

                String otherBulk = extractBulkCipher(other.name());

                if (!bulk.equals(otherBulk)
                        && !suiteName.contains(otherBulk)) {
                    tests.add(new String[] { suiteName, otherBulk, "enabled" });
                    break;
                }
            }
        }

        return tests;
    }

    /**
     * Separator used in TLS cipher suite names to mark the start of
     * the bulk cipher component (e.g. TLS_RSA_WITH_AES_128_CBC_SHA).
     */
    private static final String WITH = "_WITH_";

    private static String extractBulkCipher(String suite) {
        if (suite.contains(WITH)) {
            String after = suite.substring(suite.indexOf(WITH) + WITH.length());
            int last = after.lastIndexOf('_');
            return after.substring(0, last);
        } else {
            int first = suite.indexOf('_');
            int last = suite.lastIndexOf('_');
            return suite.substring(first + 1, last);
        }
    }

    private static void testCipherSuiteVisibility(String suite, boolean expectedDisabled)
            throws NoSuchAlgorithmException {
        boolean visible = Arrays.asList(getCipherSuites(true))
                .contains(CipherSuite.cipherSuite(suite));

        if (!expectedDisabled && !visible) {
            throw new RuntimeException(
                    "Cipher suite '" + suite + "' not visible but expected to be enabled");
        } else if (expectedDisabled && visible) {
            throw new RuntimeException(
                    "Cipher suite '" + suite + "' visible but expected to be disabled");
        }
    }

    private static void testHandshake(String suite, boolean expectedDisabled) throws Exception {
        try {
            new TLSHandshakeTest(suite).run();

            if (expectedDisabled) {
                throw new RuntimeException(
                        "Handshake succeeded but should fail: " + suite);
            }
        } catch (SSLHandshakeException e) {
            if (!expectedDisabled) {
                throw new RuntimeException(
                        "Handshake failed unexpectedly: " + suite, e);
            }
        }
    }

    private static class TLSHandshakeTest extends SSLSocketTemplate {
        private final String suite;

        TLSHandshakeTest(String suite) {
            this.suite = suite;
        }

        @Override
        protected void configureClientSocket(SSLSocket socket) {
            socket.setEnabledCipherSuites(new String[] { suite });
        }

        @Override
        protected void configureServerSocket(SSLServerSocket socket) {
            socket.setEnabledCipherSuites(new String[] { suite });
        }
    }
}
