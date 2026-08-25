/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;

import java.util.Arrays;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

/*
 * @test
 * @bug 8388519
 * @summary Verify that ExtendedSSLSession reports the negotiated named group.
 *          Verify that SSLSocket reports the supported named groups.
 * @library /javax/net/ssl/templates
 *          /test/lib
 */

public class SSLSocketNegotiatedSupportedNamedGroup extends SSLSocketTemplate {

    private static final String[] DEFAULT_SUPPORTED_NG =
            // Some algorithms not available on Windows
            System.getProperty("os.name").startsWith("Windows") ?
                    new String[]{
                            "X25519MLKEM768",
                            "x25519",
                            "secp256r1",
                            "secp384r1",
                            "secp521r1",
                            "x448",
                            "ffdhe2048",
                            "ffdhe3072",
                            "ffdhe4096",
                            "sect233k1",
                            "sect233r1",
                            "sect239k1",
                            "sect283k1",
                            "sect283r1",
                            "sect409k1",
                            "sect409r1",
                            "sect571k1",
                            "sect571r1",
                            "secp256k1",
                            "ffdhe6144",
                            "ffdhe8192",
                            "SecP256r1MLKEM768",
                            "SecP384r1MLKEM1024"
                    } :
                    new String[]{
                            "X25519MLKEM768",
                            "x25519",
                            "secp256r1",
                            "secp384r1",
                            "secp521r1",
                            "x448",
                            "ffdhe2048",
                            "ffdhe3072",
                            "ffdhe4096",
                            "sect233k1",
                            "sect233r1",
                            "sect239k1",
                            "sect283k1",
                            "sect283r1",
                            "sect409k1",
                            "sect409r1",
                            "sect571k1",
                            "sect571r1",
                            "secp224k1",
                            "secp224r1",
                            "secp256k1",
                            "ffdhe6144",
                            "ffdhe8192",
                            "SecP256r1MLKEM768",
                            "SecP384r1MLKEM1024"
                    };


    private static final String[][] TEST_VALUES = new String[][]{
            // Default named groups
            {null, "X25519MLKEM768", "TLSv1.3"},
            {null, "x25519", "TLSv1.2"},

            // Single named group
            {"secp384r1", "secp384r1", "TLSv1.3"},
            {"secp384r1", "secp384r1", "TLSv1.2"},

            // Multiple named groups
            {"secp256r1,secp384r1", "secp256r1", "TLSv1.3"},
            {"secp256r1,secp384r1", "secp256r1", "TLSv1.2"},
    };

    private final String[] inputNamedGroups;
    private final String negotiatedNamedGroup;
    private final String protocol;

    public SSLSocketNegotiatedSupportedNamedGroup(String[] inputNamedGroups,
            String negotiatedNamedGroup, String protocol) {
        this.inputNamedGroups = inputNamedGroups;
        this.negotiatedNamedGroup = negotiatedNamedGroup;
        this.protocol = protocol;
    }

    public static void main(String[] args) throws Exception {
        // Check SSLServerSocket.getSupportedNamedGroups() call with default
        // configuration.
        SSLSocketTemplate defaultTest = new SSLSocketTemplate();
        SSLContext context = defaultTest.createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        try (SSLServerSocket sslServerSocket =
                (SSLServerSocket) sslssf.createServerSocket(
                        defaultTest.serverPort, 0, defaultTest.serverAddress)) {
            assertTrue(Arrays.equals(DEFAULT_SUPPORTED_NG,
                            sslServerSocket.getSupportedNamedGroups()),
                    Arrays.toString(sslServerSocket.getSupportedNamedGroups()));
        }

        // Run through test values
        for (String[] v : TEST_VALUES) {
            System.out.println("Running with test parameters: "
                    + Arrays.toString(v));
            new SSLSocketNegotiatedSupportedNamedGroup(
                    v[0] != null ? v[0].split(",") : null,
                    v[1], v[2]).run();
        }
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        SSLParameters params = socket.getSSLParameters();
        params.setProtocols(new String[]{protocol});
        params.setNamedGroups(inputNamedGroups);
        socket.setSSLParameters(params);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters params = socket.getSSLParameters();
        params.setProtocols(new String[]{protocol});
        params.setNamedGroups(inputNamedGroups);
        socket.setSSLParameters(params);
    }

    @Override
    protected void runServerApplication(SSLSocket socket) throws Exception {
        super.runServerApplication(socket);
        checkNamedGroup(socket);
    }

    @Override
    protected void runClientApplication(SSLSocket socket) throws Exception {
        super.runClientApplication(socket);
        checkNamedGroup(socket);
    }

    private void checkNamedGroup(SSLSocket socket) {
        // Check SSLSocket.getSupportedNamedGroups() call
        assertTrue(Arrays.equals(DEFAULT_SUPPORTED_NG,
                        socket.getSupportedNamedGroups()),
                Arrays.toString(socket.getSupportedNamedGroups()));

        // Check ExtendedSSLSession.getNegotiatedNamedGroup() call
        ExtendedSSLSession session =
                (ExtendedSSLSession) socket.getSession();
        assertEquals(negotiatedNamedGroup, session.getNegotiatedNamedGroup());
    }
}