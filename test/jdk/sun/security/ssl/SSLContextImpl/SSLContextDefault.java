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

    private final static String[] tlsProtocols = {
        "", "SSL", "TLS", "SSLv3", "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"
    };

    private final static String[] dtlsProtocols = {
        "DTLS", "DTLSv1.0", "DTLSv1.2"
    };

    private final static List<String> disabledTlsProtocols = List.<String>of(
        "SSLv3", "TLSv1", "TLSv1.1"
    );

    private final static List<String> disabledDtlsProtocols = List.<String>of(
        "DTLSv1.0"
    );

    public static void main(String[] args) throws Exception {
        for (String tlsProtocol : tlsProtocols) {
            testProtocol(tlsProtocol, false);
        }
        for (String dtlsProtocol : dtlsProtocols) {
            testProtocol(dtlsProtocol, true);
        }
    }

    private static void testProtocol(String protocol, boolean dtls)
            throws Exception {
        System.out.println("//");
        System.out.println("// " + "Testing for SSLContext of " +
                (protocol.isEmpty() ? "<default>" : protocol));
        System.out.println("//");
        checkForProtocols(protocol, dtls);
        System.out.println();
    }

    private static void checkForProtocols(String protocol, boolean dtls)
            throws Exception {
        SSLContext context;
        if (protocol.isEmpty()) {
            context = SSLContext.getDefault();
        } else {
            context = SSLContext.getInstance(protocol);
            context.init(null, null, null);
        }

        // check for the presence of supported protocols of SSLContext
        SSLParameters parameters = context.getSupportedSSLParameters();
        checkProtocols(parameters.getProtocols(),
                "Supported protocols in SSLContext", false, dtls);

        // check for the presence of default protocols of SSLContext
        parameters = context.getDefaultSSLParameters();
        checkProtocols(parameters.getProtocols(),
                "Enabled protocols in SSLContext", true, dtls);

        // check for the presence of supported protocols of SSLEngine
        SSLEngine engine = context.createSSLEngine();
        checkProtocols(engine.getSupportedProtocols(),
                "Supported protocols in SSLEngine", false, dtls);

        // Check for the presence of default protocols of SSLEngine
        checkProtocols(engine.getEnabledProtocols(),
                "Enabled protocols in SSLEngine", true, dtls);

        if (dtls) {
            return;
        }

        SSLSocketFactory factory = context.getSocketFactory();
        try (SSLSocket socket = (SSLSocket)factory.createSocket()) {
            // check for the presence of supported protocols of SSLSocket
            checkProtocols(socket.getSupportedProtocols(),
                "Supported cipher suites in SSLSocket", false, dtls);

            // Check for the presence of default protocols of SSLSocket
            checkProtocols(socket.getEnabledProtocols(),
                "Enabled protocols in SSLSocket", true, dtls);
        }

        SSLServerSocketFactory serverFactory = context.getServerSocketFactory();
        try (SSLServerSocket serverSocket =
                (SSLServerSocket)serverFactory.createServerSocket()) {
            // check for the presence of supported protocols of SSLServerSocket
            checkProtocols(serverSocket.getSupportedProtocols(),
                "Supported cipher suites in SSLServerSocket", false, dtls);

            // Check for the presence of default protocols of SSLServerSocket
            checkProtocols(serverSocket.getEnabledProtocols(),
                "Enabled protocols in SSLServerSocket", true, dtls);
        }
    }

    private static void checkProtocols(String[] protocols,
            String title, boolean disabled, boolean dtls) throws Exception {
        showProtocols(protocols, title);

        List<String> disabledProtocols
            = (dtls) ? disabledDtlsProtocols : disabledTlsProtocols;
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
