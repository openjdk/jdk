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

package jdk.jfr.event.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.net.SimpleSSLContext;

/*
 * @test
 * @bug 8391863
 * @summary Test that TLS 1.2 handshake events are recorded properly for both
 *          full and abbreviated handshakes.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.security.TestTLS12HandshakeEvent
 */

public class TestTLS12HandshakeEvent {

    private static final String PROTOCOL = "TLSv1.2";
    private static final int CONNECTION_COUNT = 2;

    public static void main(String[] args) throws Exception {
        // Use stateful resumption for a simple resumption assertion
        System.setProperty("jdk.tls.client.enableSessionTicketExtension", "false");
        System.setProperty("jdk.tls.server.enableSessionTicketExtension", "false");

        SSLContext serverContext = SimpleSSLContext.findSSLContext(PROTOCOL);
        SSLContext clientContext = SimpleSSLContext.findSSLContext(PROTOCOL);

        try (SSLServerSocket serverSocket = (SSLServerSocket) serverContext
                .getServerSocketFactory().createServerSocket(0)) {

            serverSocket.setEnabledProtocols(new String[] { PROTOCOL });
            int serverPort = serverSocket.getLocalPort();
            CompletableFuture<Void> server = CompletableFuture.runAsync(
                    () -> serve(serverSocket));

            try (Recording recording = new Recording()) {
                recording.enable(EventNames.TLSHandshake);
                recording.start();

                SSLSession initialSession = connect(clientContext, serverPort, 0);
                SSLSession resumedSession = connect(clientContext, serverPort, 1);
                server.join();

                recording.stop();

                // Assert resumption
                Asserts.assertTrue(Arrays.equals(initialSession.getId(),
                        resumedSession.getId()),
                        "The second connection did not resume the TLS session");

                // Assert JFR events
                assertEvents(Events.fromRecording(recording), serverPort);
            }
        }
    }

    private static SSLSession connect(SSLContext context, int port, int value)
            throws IOException {
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                .createSocket("localhost", port)) {
            socket.setEnabledProtocols(new String[] { PROTOCOL });
            socket.getOutputStream().write(value);
            socket.getOutputStream().flush();
            Asserts.assertEquals(socket.getInputStream().read(), value,
                    "Unexpected response from server");
            return socket.getSession();
        }
    }

    private static void serve(SSLServerSocket serverSocket) {
        try {
            for (int i = 0; i < CONNECTION_COUNT; i++) {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                    int value = socket.getInputStream().read();
                    socket.getOutputStream().write(value);
                    socket.getOutputStream().flush();
                }
            }
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    private static void assertEvents(List<RecordedEvent> events, int serverPort) {
        System.out.println(events);
        Asserts.assertEquals(events.size(), CONNECTION_COUNT * 2,
                "Expected one TLS handshake event from each peer");

        long clientEvents = events.stream()
                .filter(e -> e.getInt("peerPort") == serverPort)
                .count();
        Asserts.assertEquals(clientEvents, (long) CONNECTION_COUNT,
                "Incorrect number of client TLS handshake events");

        for (RecordedEvent event : events) {
            Events.assertField(event, "protocolVersion").equal(PROTOCOL);
        }
    }
}
