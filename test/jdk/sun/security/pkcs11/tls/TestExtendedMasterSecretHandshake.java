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
 * @run main/othervm TestExtendedMasterSecretHandshake
 */

import jdk.test.lib.security.SSLSocketTest;

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

    public static void main(String[] args) throws Exception {
        main(new TestExtendedMasterSecretHandshake(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        if (p.getService("KeyGenerator", "SunTlsExtendedMasterSecret") == null) {
            throw new RuntimeException("EMS service not available");
        }

        Security.insertProviderAt(p, 1);

        System.setProperty(
                "javax.net.debug",
                "ssl,handshake,record");

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

        String log = byteLogOutput.toString();

        if (!log.contains("Consumed extension: extended_master_secret")) {
            throw new RuntimeException("EMS was not negotiated");
        }

        if (!log.contains("\"extended_master_secret (23)\"")) {
            throw new RuntimeException("EMS extension not present");
        }

        System.out.println("Verified EMS derivation through PKCS11");
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

            System.out.println("TLS handshake and application data exchange succeeded");
        }
    }
}