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
 * @summary Verify that disabling bulk cipher algorithms in
 *          jdk.tls.disabledAlgorithms disables associated TLS cipher suites.
 * @library /test/lib
 *          /javax/net/ssl/TLSCommon
 *          /javax/net/ssl/templates
 */

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import jdk.test.lib.process.Proc;

/*
 * Each test case is executed in a separate JVM because
 * jdk.tls.disabledAlgorithms is evaluated during JSSE initialization and
 * cannot be reliably reconfigured within the same VM.
 */
public class BulkCipherDisabledAlgorithms {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // Sanity check: all enabled cipher suites should work before
            // applying any jdk.tls.disabledAlgorithms restrictions.
            testAllCipherSuitesEnabled();

            // Verify that disabling a bulk cipher algorithm disables cipher
            // suites that use that algorithm.
            Map<String, List<String>> cipherSuitesByBulkCipher = groupCipherSuitesByBulkCipher();

            for (Map.Entry<String, List<String>> entry : cipherSuitesByBulkCipher.entrySet()) {
                String disabledBulkCipher = entry.getKey();
                List<String> disabledCipherSuites = entry.getValue();

                // Verify that all cipher suites associated with the disabled
                // bulk cipher become unavailable.
                Proc p = Proc.create(
                        BulkCipherDisabledAlgorithms.class.getName())
                        .args(disabledBulkCipher,
                                String.join(",", disabledCipherSuites))
                        .secprop("jdk.tls.disabledAlgorithms", disabledBulkCipher)
                        .inheritIO();

                p.start().waitFor(0);
            }

            System.out.println("TEST PASS - OK");
            return;
        }

        String disabledBulkCipher = args[0];
        List<String> disabledCipherSuites = Arrays.asList(args[1].split(","));

        for (String disabledCipherSuite : disabledCipherSuites) {
            System.out.println("=================================================");
            System.out.println("Testing: suite=" + disabledCipherSuite +
                    ", disabled bulk cipher=" + disabledBulkCipher);

            testCipherSuiteDisabled(disabledCipherSuite);
            testHandshake(disabledCipherSuite, true);
        }
    }

    private static void testAllCipherSuitesEnabled() throws Exception {
        CipherSuite[] suites = getCipherSuites();

        for (CipherSuite suite : suites) {
            testHandshake(suite.name(), false);
        }
    }

    private static CipherSuite[] getCipherSuites() throws NoSuchAlgorithmException {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        String[] suites = engine.getEnabledCipherSuites();
        return Arrays.stream(suites)
                .map(CipherSuite::cipherSuite)
                .filter(cs -> cs != CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV)
                .toArray(CipherSuite[]::new);
    }

    private static Map<String, List<String>> groupCipherSuitesByBulkCipher() throws NoSuchAlgorithmException {
        Map<String, List<String>> cipherSuitesByBulkCipher = new LinkedHashMap<>();
        CipherSuite[] suites = getCipherSuites();

        for (CipherSuite suite : suites) {
            String suiteName = suite.name();
            String bulkCipher = extractBulkCipher(suiteName);
            List<String> suitesForBulk = cipherSuitesByBulkCipher.get(bulkCipher);

            if (suitesForBulk == null) {
                suitesForBulk = new ArrayList<>();
                cipherSuitesByBulkCipher.put(bulkCipher, suitesForBulk);
            }

            suitesForBulk.add(suiteName);
        }

        return cipherSuitesByBulkCipher;
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

    private static void testCipherSuiteDisabled(String suite) throws NoSuchAlgorithmException {
        boolean visible = Arrays.asList(getCipherSuites())
                .contains(CipherSuite.cipherSuite(suite));

        if (visible) {
            throw new RuntimeException(
                    "Cipher suite '" + suite + "' visible but expected to be disabled");
        }
    }

    private static void testHandshake(String cipherSuite, boolean expectedDisabled) throws Exception {
        try {
            new TLSHandshakeTest(cipherSuite).run();

            if (expectedDisabled) {
                throw new RuntimeException(
                        "Handshake succeeded but should fail: " + cipherSuite);
            }
        } catch (SSLHandshakeException e) {
            if (!expectedDisabled) {
                throw new RuntimeException(
                        "Handshake failed unexpectedly: " + cipherSuite, e);
            }
        }
    }

    private static class TLSHandshakeTest extends SSLSocketTemplate {
        private final String cipherSuite;

        TLSHandshakeTest(String cipherSuite) {
            this.cipherSuite = cipherSuite;
        }

        @Override
        protected void configureClientSocket(SSLSocket socket) {
            socket.setEnabledCipherSuites(new String[] { cipherSuite });
        }

        @Override
        protected void configureServerSocket(SSLServerSocket socket) {
            socket.setEnabledCipherSuites(new String[] { cipherSuite });
        }
    }
}
