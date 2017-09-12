/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.

/*
 * @test
 * @bug 7093640
 * @summary Enable TLS 1.1 and TLS 1.2 by default in client side of SunJSSE
 * @run main/othervm -Djdk.tls.client.protocols="TLSv1,TLSv1.1,TLSv1.2"
 *      NoOldVersionContext
 */

import javax.net.*;
import javax.net.ssl.*;
import java.util.Arrays;
import java.security.Security;

public class NoOldVersionContext {
    static enum ContextVersion {
        TLS_CV_01("SSL",
                new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"}),
        TLS_CV_02("TLS",
                new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"}),
        TLS_CV_03("SSLv3",
                new String[] {"SSLv3", "TLSv1"}),
        TLS_CV_04("TLSv1",
                new String[] {"SSLv3", "TLSv1"}),
        TLS_CV_05("TLSv1.1",
                new String[] {"SSLv3", "TLSv1", "TLSv1.1"}),
        TLS_CV_06("TLSv1.2",
                new String[] {"SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"}),
        TLS_CV_07("Default",
                new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"});

        final String contextVersion;
        final String[] enabledProtocols;
        final static String[] supportedProtocols = new String[] {
                "SSLv2Hello", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2"};

        ContextVersion(String contextVersion, String[] enabledProtocols) {
            this.contextVersion = contextVersion;
            this.enabledProtocols = enabledProtocols;
        }
    }

    private static boolean checkProtocols(String[] target, String[] expected) {
        boolean success = true;
        if (target.length == 0) {
            System.out.println("\tError: No protocols");
            success = false;
        }

        if (!Arrays.equals(target, expected)) {
            System.out.println("\tError: Expected to get protocols " +
                    Arrays.toString(expected));
            System.out.println("\tError: The actual protocols " +
                    Arrays.toString(target));
            success = false;
        }

        return success;
    }

    private static boolean checkCipherSuites(String[] target) {
        boolean success = true;
        if (target.length == 0) {
            System.out.println("\tError: No cipher suites");
            success = false;
        }

        return success;
    }

    public static void main(String[] args) throws Exception {
        // reset the security property to make sure that the algorithms
        // and keys used in this test are not disabled.
        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        boolean failed = false;
        for (ContextVersion cv : ContextVersion.values()) {
            System.out.println("Checking SSLContext of " + cv.contextVersion);
            SSLContext context = SSLContext.getInstance(cv.contextVersion);

            // Default SSLContext is initialized automatically.
            if (!cv.contextVersion.equals("Default")) {
                // Use default TK, KM and random.
                context.init((KeyManager[])null, (TrustManager[])null, null);
            }

            //
            // Check SSLContext
            //
            // Check default SSLParameters of SSLContext
            System.out.println("\tChecking default SSLParameters");
            SSLParameters parameters = context.getDefaultSSLParameters();

            String[] protocols = parameters.getProtocols();
            failed |= !checkProtocols(protocols, cv.enabledProtocols);

            String[] ciphers = parameters.getCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            // Check supported SSLParameters of SSLContext
            System.out.println("\tChecking supported SSLParameters");
            parameters = context.getSupportedSSLParameters();

            protocols = parameters.getProtocols();
            failed |= !checkProtocols(protocols, cv.supportedProtocols);

            ciphers = parameters.getCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            //
            // Check SSLEngine
            //
            // Check SSLParameters of SSLEngine
            System.out.println();
            System.out.println("\tChecking SSLEngine of this SSLContext");
            System.out.println("\tChecking SSLEngine.getSSLParameters()");
            SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(true);
            parameters = engine.getSSLParameters();

            protocols = parameters.getProtocols();
            failed |= !checkProtocols(protocols, cv.enabledProtocols);

            ciphers = parameters.getCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            System.out.println("\tChecking SSLEngine.getEnabledProtocols()");
            protocols = engine.getEnabledProtocols();
            failed |= !checkProtocols(protocols, cv.enabledProtocols);

            System.out.println("\tChecking SSLEngine.getEnabledCipherSuites()");
            ciphers = engine.getEnabledCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            System.out.println("\tChecking SSLEngine.getSupportedProtocols()");
            protocols = engine.getSupportedProtocols();
            failed |= !checkProtocols(protocols, cv.supportedProtocols);

            System.out.println(
                    "\tChecking SSLEngine.getSupportedCipherSuites()");
            ciphers = engine.getSupportedCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            //
            // Check SSLSocket
            //
            // Check SSLParameters of SSLSocket
            System.out.println();
            System.out.println("\tChecking SSLSocket of this SSLContext");
            System.out.println("\tChecking SSLSocket.getSSLParameters()");
            SocketFactory fac = context.getSocketFactory();
            SSLSocket socket = (SSLSocket)fac.createSocket();
            parameters = socket.getSSLParameters();

            protocols = parameters.getProtocols();
            failed |= !checkProtocols(protocols, cv.enabledProtocols);

            ciphers = parameters.getCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            System.out.println("\tChecking SSLEngine.getEnabledProtocols()");
            protocols = socket.getEnabledProtocols();
            failed |= !checkProtocols(protocols, cv.enabledProtocols);

            System.out.println("\tChecking SSLEngine.getEnabledCipherSuites()");
            ciphers = socket.getEnabledCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            System.out.println("\tChecking SSLEngine.getSupportedProtocols()");
            protocols = socket.getSupportedProtocols();
            failed |= !checkProtocols(protocols, cv.supportedProtocols);

            System.out.println(
                    "\tChecking SSLEngine.getSupportedCipherSuites()");
            ciphers = socket.getSupportedCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            //
            // Check SSLServerSocket
            //
            // Check SSLParameters of SSLServerSocket
            System.out.println();
            System.out.println("\tChecking SSLServerSocket of this SSLContext");
            System.out.println("\tChecking SSLServerSocket.getSSLParameters()");
            SSLServerSocketFactory sf = context.getServerSocketFactory();
            SSLServerSocket ssocket = (SSLServerSocket)sf.createServerSocket();
            parameters = ssocket.getSSLParameters();

            protocols = parameters.getProtocols();
            failed |= !checkProtocols(protocols, cv.supportedProtocols);

            ciphers = parameters.getCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            System.out.println("\tChecking SSLEngine.getEnabledProtocols()");
            protocols = ssocket.getEnabledProtocols();
            failed |= !checkProtocols(protocols, cv.supportedProtocols);

            System.out.println("\tChecking SSLEngine.getEnabledCipherSuites()");
            ciphers = ssocket.getEnabledCipherSuites();
            failed |= !checkCipherSuites(ciphers);

            System.out.println("\tChecking SSLEngine.getSupportedProtocols()");
            protocols = ssocket.getSupportedProtocols();
            failed |= !checkProtocols(protocols, cv.supportedProtocols);

            System.out.println(
                    "\tChecking SSLEngine.getSupportedCipherSuites()");
            ciphers = ssocket.getSupportedCipherSuites();
            failed |= !checkCipherSuites(ciphers);
        }

        if (failed) {
            throw new Exception("Run into problems, see log for more details");
        } else {
            System.out.println("\t... Success");
        }
    }
}
