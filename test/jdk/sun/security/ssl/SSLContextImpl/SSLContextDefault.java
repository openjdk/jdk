/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

//
// SunJSSE does not support dynamic system properties, no way to re-use
// system properties in samevm/agentvm mode.
//

/*
 * @test
 * @bug 8202343 8256660
 * @summary Check that SSLv3, TLSv1, TLSv1.1, and DTLSv1.0 are disabled
 *          by default
 * @run main/othervm SSLContextDefault
 */

import java.util.List;
import javax.net.ssl.*;

public class SSLContextDefault {

    private static final String[] tlsProtocols = {
        "", "SSL", "TLS", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"
    };

    private static final String[] dtlsProtocols = {
        "DTLS", "DTLSv1.0", "DTLSv1.2"
    };

    private static final List<String> disabledTlsProtocols = List.<String>of(
        "SSLv3", "TLSv1", "TLSv1.1"
    );

    private static final List<String> disabledDtlsProtocols = List.<String>of(
        "DTLSv1.0"
    );

    public static void main(String[] args) throws Exception {
        for (String tlsProtocol : tlsProtocols) {
            testProtocol(tlsProtocol, disabledTlsProtocols);
        }
        for (String dtlsProtocol : dtlsProtocols) {
            testProtocol(dtlsProtocol, disabledDtlsProtocols);
        }
    }

    private static void testProtocol(String protocol,
            List<String> disabledProtocols) throws Exception {
        System.out.println("//");
        System.out.println("// " + "Testing for SSLContext of " +
                (protocol.isEmpty() ? "<default>" : protocol));
        System.out.println("//");
        checkForProtocols(protocol, disabledProtocols);
        System.out.println();
    }

    private static void checkForProtocols(String protocol,
            List<String> disabledProtocols) throws Exception {
        SSLContext context;
        if (protocol.isEmpty()) {
            context = SSLContext.getDefault();
        } else {
            context = SSLContext.getInstance(protocol);
            context.init(null, null, null);
        }

        // check for the presence of supported protocols of SSLContext
        SSLParameters parameters = context.getSupportedSSLParameters();
        checkProtocols(parameters.getProtocols(), disabledProtocols,
                "Supported protocols in SSLContext", false);

        // check for the presence of default protocols of SSLContext
        parameters = context.getDefaultSSLParameters();
        checkProtocols(parameters.getProtocols(), disabledProtocols,
                "Enabled protocols in SSLContext", true);

        // check for the presence of supported protocols of SSLEngine
        SSLEngine engine = context.createSSLEngine();
        checkProtocols(engine.getSupportedProtocols(), disabledProtocols,
                "Supported protocols in SSLEngine", false);

        // Check for the presence of default protocols of SSLEngine
        checkProtocols(engine.getEnabledProtocols(), disabledProtocols,
                "Enabled protocols in SSLEngine", true);

        if (protocol.startsWith("DTLS")) {
            return;
        }

        SSLSocketFactory factory = context.getSocketFactory();
        try (SSLSocket socket = (SSLSocket)factory.createSocket()) {
            // check for the presence of supported protocols of SSLSocket
            checkProtocols(socket.getSupportedProtocols(), disabledProtocols,
                "Supported cipher suites in SSLSocket", false);

            // Check for the presence of default protocols of SSLSocket
            checkProtocols(socket.getEnabledProtocols(), disabledProtocols,
                "Enabled protocols in SSLSocket", true);
        }

        SSLServerSocketFactory serverFactory = context.getServerSocketFactory();
        try (SSLServerSocket serverSocket =
                (SSLServerSocket)serverFactory.createServerSocket()) {
            // check for the presence of supported protocols of SSLServerSocket
            checkProtocols(serverSocket.getSupportedProtocols(),
                disabledProtocols, "Supported cipher suites in SSLServerSocket",
                false);

            // Check for the presence of default protocols of SSLServerSocket
            checkProtocols(serverSocket.getEnabledProtocols(),
                disabledProtocols, "Enabled protocols in SSLServerSocket",
                true);
        }
    }

    private static void checkProtocols(String[] protocols,
            List<String> disabledProtocols, String title, boolean disabled)
            throws Exception {
        showProtocols(protocols, title);

        if (disabled) {
            for (String protocol : protocols ) {
                if (disabledProtocols.contains(protocol)) {
                    throw new Exception(protocol +
                                        " should not be enabled by default");
                }
            }
        } else {
            for (String disabledProtocol : disabledProtocols) {
                if (!List.of(protocols).contains(disabledProtocol)) {
                    throw new Exception(disabledProtocol +
                                        " should be supported by default");
                }
            }
        }
    }

    private static void showProtocols(String[] protocols, String title) {
        System.out.println(title + "[" + protocols.length + "]:");
        for (String protocol : protocols) {
            System.out.println("  " + protocol);
        }
    }
}
