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
 * @bug 8389135
 * @summary Verifies TLS 1.2 Extended Master Secret negotiation using SunPKCS11
 * @library /test/lib ..
 * @modules java.base/sun.security.internal.spec
 *
 * @run main/othervm
 *      -Djdk.tls.useExtendedMasterSecret=true
 *      TestExtendedMasterSecretHandshake true
 *
 * @run main/othervm
 *      -Djdk.tls.useExtendedMasterSecret=false
 *      TestExtendedMasterSecretHandshake false
 */

import jdk.test.lib.security.SSLSocketTest;
import jtreg.SkippedException;
import sun.security.internal.spec.TlsMasterSecretParameterSpec;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.security.Provider;
import java.security.Security;

public class TestExtendedMasterSecretHandshake extends PKCS11Test {

    private static boolean useExtendedMasterSecret;

    public static void main(String[] args) throws Exception {
        useExtendedMasterSecret = Boolean.parseBoolean(args[0]);

        main(new TestExtendedMasterSecretHandshake(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        initialize(p);

        if (!shouldRun(p)) {
            throw new SkippedException(
                    "Test skipped: SunTlsExtendedMasterSecret not supported by provider " + p.getName());
        }

        if (useExtendedMasterSecret) {
            verifyDirectEMSGeneration(p);
        }

        System.setProperty("javax.net.debug", "ssl,handshake");

        String log = runHandshake();

        if (!log.contains("TLS handshake and application data exchange succeeded")) {
            throw new RuntimeException("Handshake failed");
        }

        if (useExtendedMasterSecret) {
            if (countOccurrences(log, "Consumed extension: extended_master_secret") != 2 ||
                    countOccurrences(log, "Ignore unavailable extension: extended_master_secret") != 0) {
                throw new RuntimeException("EMS extension not negotiated on both sides");
            }

            if (!log.contains("TLS handshake and application data exchange succeeded")) {
                throw new RuntimeException("Handshake did not complete");
            }

            System.out.println("Verified EMS derivation through PKCS11 enabled");
        } else {
            if (countOccurrences(log, "Consumed extension: extended_master_secret") != 0 ||
                    countOccurrences(log, "Ignore unavailable extension: extended_master_secret") < 2) {
                throw new RuntimeException("EMS extension negotiated although jdk.tls.useExtendedMasterSecret=false");
            }

            System.out.println("Verified EMS derivation through PKCS11 disabled");
        }
    }

    private static void initialize(Provider p) {
        Security.insertProviderAt(p, 1);
    }

    private static boolean shouldRun(Provider p) {
        return p.getService("KeyGenerator", "SunTlsExtendedMasterSecret") != null;
    }

    private static void verifyDirectEMSGeneration(Provider p) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("SunTlsExtendedMasterSecret", p);
        if (kg.getProvider() != p) {
            throw new RuntimeException("Unexpected provider: " + kg.getProvider().getName());
        }

        byte[] premaster = new byte[48];
        byte[] sessionHash = new byte[32];
        SecretKey premasterKey = new SecretKeySpec(premaster, "TlsPremasterSecret");

        TlsMasterSecretParameterSpec spec = new TlsMasterSecretParameterSpec(
                premasterKey,
                3, 3,                    // TLS 1.2
                sessionHash,
                "SHA-256",
                32,
                64);
        kg.init(spec);
        SecretKey key = kg.generateKey();

        if (key == null) {
            throw new RuntimeException("Generated EMS key is null");
        }

        if (!"TlsMasterSecret".equals(key.getAlgorithm())) {
            throw new RuntimeException("Unexpected algorithm: " + key.getAlgorithm());
        }
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String runHandshake() throws Exception {
        ByteArrayOutputStream byteLogOutput = new ByteArrayOutputStream();

        PrintStream ps = new PrintStream(byteLogOutput, true);

        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;

        try {
            System.setOut(ps);
            System.setErr(ps);

            new HandshakeTest().run();

        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }

        return byteLogOutput.toString();
    }

    private static class HandshakeTest extends SSLSocketTest {
        private SSLSession clientSession;
        private SSLSession serverSession;

        @Override
        protected void configureServerSocket(SSLServerSocket socket) {
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});
        }

        @Override
        protected void runServerApplication(SSLSocket socket) throws Exception {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            int value = in.read();
            if (value != 42) {
                throw new RuntimeException("Unexpected client value: " + value);
            }

            out.write(99);
            out.flush();

            serverSession = socket.getSession();
        }

        @Override
        protected void runClientApplication(SSLSocket socket) throws Exception {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write(42);
            out.flush();

            int response = in.read();
            if (response != 99) {
                throw new RuntimeException("Unexpected server value: " + response);
            }

            clientSession = socket.getSession();
        }

        @Override
        public void run() throws Exception {
            super.run();

            if (clientSession == null) {
                throw new RuntimeException("Client session missing");
            }
            if (serverSession == null) {
                throw new RuntimeException("Server session missing");
            }

            if (!"TLSv1.2".equals(clientSession.getProtocol())) {
                throw new RuntimeException("Unexpected protocol: " + clientSession.getProtocol());
            }

            if (!"TLSv1.2".equals(serverSession.getProtocol())) {
                throw new RuntimeException("Unexpected protocol: " + serverSession.getProtocol());
            }

            System.out.println("TLS handshake and application data exchange succeeded");
        }
    }
}