/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test TLS 1.3 disabled algorithms behavior
 * @modules jdk.crypto.ec
 * @library /test/lib /javax/net/ssl/templates
 * @run main/othervm -enablesystemassertions TLS13DisabledAlgorithm
 */

import java.net.InetAddress;
import java.security.Security;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import jdk.test.lib.process.Proc;

/**
 * Verifies TLS 1.3 behavior when algorithms are disabled via
 * jdk.tls.disabledAlgorithms.
 *
 * No key or trust material is configured, so all handshakes fail.
 * If a cipher suite is disabled, failure occurs early due to algorithm
 * constraints; otherwise it fails later due to missing key material.
 */
public class TLS13DisabledAlgorithm {

    private static final String[][] TEST_MATRIX = {
            // positive test:
            // check whether deactivating cipher suite works
            { "TLS_AES_128_GCM_SHA256", "TLS_AES_128_GCM_SHA256", "disabled" },
            { "TLS_AES_128_GCM_SHA256", "AES_128_GCM", "disabled" },
            { "TLS_AES_256_GCM_SHA384", "TLS_AES_256_GCM_SHA384", "disabled" },
            { "TLS_AES_256_GCM_SHA384", "AES_256_GCM", "disabled" },
            {
                    "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
                    "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
                    "disabled"
            },
            {
                    "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
                    "CHACHA20_POLY1305",
                    "disabled"
            },
            {
                    "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                    "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                    "disabled"
            },
            {
                    "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                    "CHACHA20_POLY1305",
                    "disabled"
            },
            {
                    "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                    "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                    "disabled"
            },
            {
                    "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
                    "CHACHA20_POLY1305",
                    "disabled"
            },

            // negative test:
            // check whether test behaves differently from deactivated cipher suite
            { "TLS_AES_128_GCM_SHA256", "AES_256_GCM", "enabled" },
            { "TLS_AES_256_GCM_SHA384", "AES_128_GCM", "enabled" },
            { "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", "AES_256_GCM", "enabled" },
            { "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "AES_256_GCM", "enabled" },
            { "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "AES_256_GCM", "enabled" }
    };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            for (String[] test : TEST_MATRIX) {
                String suite = test[0];
                String disabled = test[1];
                String expected = test[2];

                System.out.println("=================================================");
                System.out.println(
                        "Testing: suite=" + suite +
                                ", disabled=" + disabled +
                                ", expected=" + expected);

                Proc p = Proc.create(
                        TLS13DisabledAlgorithm.class.getName())
                        .args(suite, expected)
                        .secprop("jdk.tls.disabledAlgorithms", disabled)
                        .env("JDK_JAVA_OPTIONS", "-enablesystemassertions")
                        .inheritIO();

                p.start().waitFor(0);
            }

            System.out.println("TEST PASS - OK");
            return;
        }

        if (!args[1].equals("enabled") && !args[1].equals("disabled")) {
            throw new RuntimeException("Unknown expected state: " + args[1]);
        }

        String testedSuite = args[0];
        boolean expectedDisabled = args[1].equals("disabled");

        testCipherSuiteVisibility(testedSuite, expectedDisabled);
        testHandshake(testedSuite, expectedDisabled);

        System.out.println("TEST PASS - OK");
    }

    private static void testCipherSuiteVisibility(String suite, boolean expectedDisabled)
            throws Exception {
        SSLContext ctx = SSLContext.getDefault();
        SSLEngine engine = ctx.createSSLEngine();
        String[] enabled = engine.getEnabledCipherSuites();

        boolean visible = Arrays.asList(enabled).contains(suite);

        if (!expectedDisabled && !visible) {
            throw new RuntimeException("Suite is expected to be enabled but is not visible");
        } else if (expectedDisabled && visible) {
            throw new RuntimeException("Suite is expected to be disabled but is visible");
        }
    }

    private static void testHandshake(String suite, boolean expectedDisabled)
            throws Exception {
        String expectedErrMsg;

        if (expectedDisabled) {
            expectedErrMsg = "No appropriate protocol (protocol is disabled or " +
                    "cipher suites are inappropriate)";
        } else {
            expectedErrMsg = "(handshake_failure) Received fatal alert: handshake_failure";
        }

        String receivedErrMsg = "";

        try (SSLServer server = new SSLServer(new String[] { suite })) {
            Thread t = new Thread(server, "server");
            t.setDaemon(true);
            t.start();

            while (!server.isRunning()) {
                Thread.sleep(50);
            }

            try (SSLClient client = new SSLClient(server.getPort(), suite)) {
                try {
                    client.connect();
                } catch (SSLHandshakeException e) {
                    // expected in this test
                    receivedErrMsg = e.getMessage();
                }
            }
        }

        if (receivedErrMsg.isEmpty()) {
            throw new RuntimeException(
                    "Handshake unexpectedly succeeded for " + suite);
        }

        if (!receivedErrMsg.equals(expectedErrMsg)) {
            throw new RuntimeException(
                    "Unexpected handshake exception '" + receivedErrMsg +
                            "' for " + suite +
                            ". Expected '" + expectedErrMsg + "'");
        }
    }

    static class SSLServer implements Runnable, AutoCloseable {
        private final SSLServerSocket socket;
        private volatile boolean running = false;
        private volatile boolean stopped = false;

        SSLServer(String[] suites) throws Exception {
            SSLContext ctx = SSLContext.getDefault();
            socket = (SSLServerSocket) ctx.getServerSocketFactory()
                    .createServerSocket(0, 0, InetAddress.getLoopbackAddress());

            socket.setEnabledCipherSuites(suites);
        }

        @Override
        public void run() {
            running = true;

            while (!stopped) {
                try (SSLSocket s = (SSLSocket) socket.accept()) {
                    s.startHandshake();
                } catch (SSLHandshakeException e) {
                    // expected in this test
                } catch (Exception e) {
                    if (!stopped) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        int getPort() {
            return socket.getLocalPort();
        }

        boolean isRunning() {
            return running;
        }

        void stop() throws Exception {
            stopped = true;
            socket.close();
        }

        @Override
        public void close() throws Exception {
            stop();
        }
    }

    static class SSLClient implements AutoCloseable {
        private final SSLSocket socket;

        SSLClient(int port, String suite) throws Exception {
            SSLContext ctx = SSLContext.getDefault();
            socket = (SSLSocket) ctx.getSocketFactory()
                    .createSocket(InetAddress.getLoopbackAddress(), port);

            socket.setEnabledCipherSuites(new String[] { suite });
        }

        void connect() throws Exception {
            socket.startHandshake();
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
