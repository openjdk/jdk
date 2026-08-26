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

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * @build NamedGroupTestData
 *
 * @run main SSLSocketNegotiatedSupportedNamedGroup
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=true
 *      -Djdk.tls.client.enableSessionTicketExtension=true
 *      SSLSocketNegotiatedSupportedNamedGroup resume TLSv1.3
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=false
 *      -Djdk.tls.client.enableSessionTicketExtension=true
 *      SSLSocketNegotiatedSupportedNamedGroup resume TLSv1.3
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=true
 *      -Djdk.tls.client.enableSessionTicketExtension=true
 *      SSLSocketNegotiatedSupportedNamedGroup resume TLSv1.2
 * @run main/othervm -Djdk.tls.server.enableSessionTicketExtension=false
 *      -Djdk.tls.client.enableSessionTicketExtension=false
 *      SSLSocketNegotiatedSupportedNamedGroup resume TLSv1.2
 */

public class SSLSocketNegotiatedSupportedNamedGroup extends SSLSocketTemplate {

    private static final String INITIAL_GROUP = "secp256r1";
    private static final String RESUMED_GROUP = "secp384r1";
    private static final int TIMEOUT = 10000;

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
        if (args.length > 0 && args[0].equals("resume")) {
            new SSLSocketNegotiatedSupportedNamedGroup(
                    null, null, args[1]).testResumption();
            return;
        }

        runFullHandshakeTests();
    }

    private static void runFullHandshakeTests() throws Exception {
        // Check SSLServerSocket.getSupportedNamedGroups() call with default
        // configuration.
        SSLSocketTemplate defaultTest = new SSLSocketTemplate();
        SSLContext context = defaultTest.createServerSSLContext();
        SSLServerSocketFactory sslssf = context.getServerSocketFactory();
        try (SSLServerSocket sslServerSocket =
                (SSLServerSocket) sslssf.createServerSocket(
                        defaultTest.serverPort, 0, defaultTest.serverAddress)) {
            assertTrue(Arrays.equals(NamedGroupTestData.DEFAULT_SUPPORTED_NG,
                            sslServerSocket.getSupportedNamedGroups()),
                    Arrays.toString(sslServerSocket.getSupportedNamedGroups()));
        }

        // Run through test values
        for (String[] v : NamedGroupTestData.TEST_VALUES) {
            // SSLSocket doesn't support DTLS
            if (!v[2].startsWith("DTLS")) {
                System.out.println("Running with test parameters: "
                        + Arrays.toString(v));
                new SSLSocketNegotiatedSupportedNamedGroup(
                        v[0] != null ? v[0].split(",") : null,
                        v[1], v[2]).run();
            }
        }
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        SSLParameters params = socket.getSSLParameters();
        if (protocol.equals("TLSv1.2")) {
            params.setCipherSuites(new String[]{
                    NamedGroupTestData.TLS12_CIPHER_SUITE});
        }
        params.setProtocols(new String[]{protocol});
        params.setNamedGroups(inputNamedGroups);
        socket.setSSLParameters(params);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        SSLParameters params = socket.getSSLParameters();
        if (protocol.equals("TLSv1.2")) {
            params.setCipherSuites(new String[]{
                    NamedGroupTestData.TLS12_CIPHER_SUITE});
        }
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
        assertTrue(Arrays.equals(NamedGroupTestData.DEFAULT_SUPPORTED_NG,
                        socket.getSupportedNamedGroups()),
                Arrays.toString(socket.getSupportedNamedGroups()));

        // Check ExtendedSSLSession.getNegotiatedNamedGroup() call
        ExtendedSSLSession session =
                (ExtendedSSLSession) socket.getSession();
        assertEquals(negotiatedNamedGroup, session.getNegotiatedNamedGroup());
    }

    private void testResumption() throws Exception {
        // Key exchange is not performed in TLSv1.2 resumption unlike in TLSv1.3,
        // so the session.getNegotiatedNamedGroup() TLSv1.2 resumed session
        // returns null.
        String resumedNamedGroup = protocol.equals("TLSv1.3") ?
                RESUMED_GROUP : null;

        SSLContext serverContext = createServerSSLContext();
        SSLContext clientContext = createClientSSLContext();

        try (SSLServerSocket serverSocket = (SSLServerSocket)
                serverContext.getServerSocketFactory().createServerSocket(
                        0, 0, serverAddress);
                ExecutorService executor = Executors.newSingleThreadExecutor()) {

            configureResumptionServerSocket(serverSocket);
            serverSocket.setSoTimeout(TIMEOUT);

            Future<?> serverFuture = executor.submit(() -> {
                runResumptionServer(serverSocket, resumedNamedGroup);
                return null;
            });

            long initialCreationTime = runResumptionClient(
                    clientContext, serverSocket.getLocalPort(),
                    INITIAL_GROUP, INITIAL_GROUP, false);

            long resumedCreationTime = runResumptionClient(
                    clientContext, serverSocket.getLocalPort(),
                    RESUMED_GROUP, resumedNamedGroup, true);

            assertTrue(initialCreationTime == resumedCreationTime,
                    "Client session was not resumed");
            serverFuture.get();
        }
    }

    private void configureResumptionServerSocket(SSLServerSocket socket) {
        SSLParameters params = socket.getSSLParameters();
        params.setProtocols(new String[]{protocol});
        params.setNamedGroups(new String[]{INITIAL_GROUP, RESUMED_GROUP});
        if (protocol.equals("TLSv1.2")) {
            params.setCipherSuites(new String[]{
                    NamedGroupTestData.TLS12_CIPHER_SUITE});
        }
        socket.setSSLParameters(params);
    }

    private void runResumptionServer(SSLServerSocket serverSocket,
            String resumedNamedGroup) throws Exception {

        long initialCreationTime = 0;
        String[] expectedNamedGroups = {
                INITIAL_GROUP, resumedNamedGroup};

        for (int i = 0; i < expectedNamedGroups.length; i++) {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.setSoTimeout(TIMEOUT);

                if (i == 1) {
                    socket.setEnableSessionCreation(false);
                }

                socket.startHandshake();
                ExtendedSSLSession session =
                        (ExtendedSSLSession) socket.getSession();

                assertEquals(expectedNamedGroups[i],
                        session.getNegotiatedNamedGroup());

                if (i == 0) {
                    initialCreationTime = session.getCreationTime();
                } else {
                    assertTrue(initialCreationTime == session.getCreationTime(),
                            "Server session was not resumed");
                }

                socket.getInputStream().read();
                socket.getOutputStream().write(280);
                socket.getOutputStream().flush();
            }
        }
    }

    private long runResumptionClient(SSLContext context, int serverPort,
            String namedGroup, String expectedNamedGroup,
            boolean requireResumption) throws Exception {

        try (SSLSocket socket =
                (SSLSocket) context.getSocketFactory().createSocket()) {

            SSLParameters params = socket.getSSLParameters();
            params.setProtocols(new String[]{protocol});
            params.setNamedGroups(new String[]{namedGroup});

            if (protocol.equals("TLSv1.2")) {
                params.setCipherSuites(new String[]{
                        NamedGroupTestData.TLS12_CIPHER_SUITE});
            }

            socket.setSSLParameters(params);
            socket.setSoTimeout(TIMEOUT);

            if (requireResumption) {
                socket.setEnableSessionCreation(false);
            }

            socket.connect(new InetSocketAddress(serverAddress, serverPort),
                    TIMEOUT);
            socket.startHandshake();

            ExtendedSSLSession session = (ExtendedSSLSession) socket.getSession();
            assertEquals(expectedNamedGroup, session.getNegotiatedNamedGroup());

            long creationTime = session.getCreationTime();

            socket.getOutputStream().write(85);
            socket.getOutputStream().flush();
            socket.getInputStream().read();

            return creationTime;
        }
    }
}
