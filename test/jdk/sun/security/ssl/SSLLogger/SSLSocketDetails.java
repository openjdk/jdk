/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8329253
 * @summary Verify socket endpoint details in JSSE lifecycle diagnostics
 * @library /test/lib /javax/net/ssl/templates
 * @run main/othervm SSLSocketDetails
 */

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SSLSocketDetails extends SSLContextTemplate {
    private static final String HOST = "socket-debug.example.test";
    private static final String UNAVAILABLE = "<unavailable>";
    private static final List<String> EVENTS = List.of(
            "SSLSocket connected", "Starting SSL handshake",
            "Starting SSL key update", "SSL handshake completed",
            "duplex close of SSLSocket");

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            new SSLSocketDetails().exercise();
            return;
        }

        Path config = Path.of("socket-logging.properties");
        Files.writeString(config, """
                .level=OFF
                javax.net.ssl.level=ALL
                handlers=java.util.logging.ConsoleHandler
                java.util.logging.ConsoleHandler.level=ALL
                """);
        for (String option : List.of("ssl,handshake", "ssl,handshake,expand",
                "", "ssl,record", "disabled")) {
            List<String> command = new ArrayList<>();
            if (!option.equals("disabled")) {
                command.add("-Djavax.net.debug=" + option);
            }
            command.add("-Djava.util.logging.config.file=" + config.toAbsolutePath());
            command.add("SSLSocketDetails");
            command.add("child");
            OutputAnalyzer output = ProcessTools.executeTestJava(command);
            output.shouldHaveExitValue(0);
            String stderr = output.getStderr();
            if (option.equals("disabled")) {
                if (stderr.contains("SSLSocket[peerHost=")) {
                    throw new RuntimeException("Socket debug output when disabled");
                }
                continue;
            }
            // The child obtains expected endpoints from the public socket API.
            // Check stderr only, so the expectations on stdout cannot satisfy
            // assertions about debug output.
            for (String line : output.getStdout().lines().toList()) {
                boolean exactlyOnce = line.startsWith("EXPECT_ONCE:");
                if (line.startsWith("EXPECT:") || exactlyOnce) {
                    String[] expected = line.substring(line.indexOf(':') + 1).split("\t", 2);
                    String event = expected[0];
                    if (option.equals("ssl,record") &&
                            (event.startsWith("Starting SSL ") ||
                             event.equals("SSL handshake completed"))) {
                        continue;
                    }
                    // Parameters follow the message in compact, expanded,
                    // and System.Logger output. Do not accept details from
                    // an unrelated event elsewhere in the log.
                    String regex = Pattern.quote(event) +
                            "[^\\r\\n]*\\R(?:[^\\r\\n]*\\R){0,2}[^\\r\\n]*" +
                            Pattern.quote(expected[1]);
                    long count = Pattern.compile(regex).matcher(stderr).results().count();
                    if (count == 0 || (exactlyOnce && count != 1)) {
                        throw new RuntimeException("Expected " +
                                (exactlyOnce ? "exactly one" : "at least one") +
                                " matching event, found " + count + ": " + line);
                    }
                }
            }
            for (String event : EVENTS) {
                boolean handshake = event.startsWith("Starting SSL ") ||
                        event.equals("SSL handshake completed");
                if (option.equals("ssl,record") && handshake) {
                    if (stderr.contains(event)) {
                        throw new RuntimeException("Handshake filter ignored: " + event);
                    }
                } else {
                    require(stderr, event);
                }
            }
            require(stderr, "Fatal (");
            if (!option.equals("ssl,record")) {
                require(stderr, "protocol=TLSv1.2, cipherSuite=");
                require(stderr, "protocol=TLSv1.3, cipherSuite=");
            }
        }
    }

    private static void require(String output, String expected) {
        if (!output.contains(expected)) {
            throw new RuntimeException("Missing debug output: " + expected);
        }
    }

    private void exercise() throws Exception {
        SSLContext clientContext = createClientSSLContext();
        SSLContext serverContext = createServerSSLContext();
        // Closing an unconnected socket must not try to negotiate a session.
        try (SSLSocket socket = (SSLSocket)
                clientContext.getSocketFactory().createSocket()) {
            expect(socket, UNAVAILABLE, "duplex close of SSLSocket");
        }
        // Also exercise a bound socket with no peer.
        try (SSLSocket socket = (SSLSocket)
                clientContext.getSocketFactory().createSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            expect(socket, UNAVAILABLE, "duplex close of SSLSocket");
        }
        closeUnderlyingSocket(clientContext);
        for (String protocol : List.of("TLSv1.2", "TLSv1.3")) {
            connect(clientContext, serverContext, protocol, false, false);
            connect(clientContext, serverContext, protocol, true, false);
        }
        connect(clientContext, serverContext, "TLSv1.2", false, true);
    }

    private static void closeUnderlyingSocket(SSLContext context) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (var listener = new ServerSocket(0, 50, loopback);
             var raw = new Socket(loopback, listener.getLocalPort());
             var peer = listener.accept();
             var socket = (SSLSocket) context.getSocketFactory().createSocket(
                     raw, HOST, listener.getLocalPort(), true)) {
            // Capture the actual endpoint before Socket.getLocalAddress()
            // starts returning the wildcard address. The SSL close diagnostic
            // must retain that endpoint after the underlying socket closes.
            expectOnce(socket, HOST, "duplex close of SSLSocket");
            raw.close();
        }
    }

    private void connect(SSLContext clientContext, SSLContext serverContext,
            String protocol, boolean layered, boolean fail) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (SSLServerSocket listener = (SSLServerSocket)
                serverContext.getServerSocketFactory()
                        .createServerSocket(0, 50, loopback);
             var executor = Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(10_000);
            listener.setEnabledProtocols(new String[]{fail ? "TLSv1.3" : protocol});
            var server = executor.submit(() -> {
                try (SSLSocket socket = (SSLSocket) listener.accept()) {
                    socket.setSoTimeout(10_000);
                    // No hostname is supplied for an accepted numeric peer.
                    expectConnection(socket, socket.getInetAddress().getHostAddress(), fail);
                    handshake(socket, fail);
                    if (!fail) {
                        socket.getOutputStream().write(socket.getInputStream().read());
                    }
                }
                return null;
            });
            // Associate an original hostname with the numeric address without
            // doing a DNS lookup. The layered variant supplies its TLS peer
            // hostname independently of the underlying TCP socket.
            InetAddress named = InetAddress.getByAddress(HOST, loopback.getAddress());
            try (SSLSocket socket = layered ?
                    (SSLSocket) clientContext.getSocketFactory().createSocket(
                            new Socket(loopback, listener.getLocalPort()),
                            HOST, listener.getLocalPort(), true) :
                    (SSLSocket) clientContext.getSocketFactory().createSocket(
                            named, listener.getLocalPort())) {
                socket.setSoTimeout(10_000);
                socket.setEnabledProtocols(new String[]{protocol});
                expectConnection(socket, HOST, fail);
                handshake(socket, fail);
                if (!fail) {
                    if (protocol.equals("TLSv1.3")) {
                        expectOnce(socket, HOST, "Starting SSL key update");
                        // On an established TLS 1.3 connection, this sends a
                        // KeyUpdate rather than starting another handshake.
                        socket.startHandshake();
                    }
                    socket.getOutputStream().write(42);
                    if (socket.getInputStream().read() != 42) {
                        throw new RuntimeException("Application data exchange failed");
                    }
                }
            }
            server.get(20, TimeUnit.SECONDS);
        }
    }

    private static void handshake(SSLSocket socket, boolean fail) throws Exception {
        try {
            socket.startHandshake();
            if (fail) {
                throw new RuntimeException("Expected protocol mismatch");
            }
            System.out.println("EXPECT:SSL handshake completed\tprotocol=" +
                    socket.getSession().getProtocol() + ", cipherSuite=" +
                    socket.getSession().getCipherSuite());
        } catch (SSLException expected) {
            if (!fail) {
                throw expected;
            }
        }
    }

    private static void expectConnection(SSLSocket socket, String host, boolean fail) {
        expect(socket, host, "SSLSocket connected");
        expectOnce(socket, host, "Starting SSL handshake");
        expectOnce(socket, host, fail ? "Fatal (" : "SSL handshake completed");
        // A fatal alert closes the transport before application close(),
        // which then returns without attempting another duplex shutdown.
        expect(socket, host, fail ? "close the SSL connection" :
                "duplex close of SSLSocket");
    }

    private static void expect(SSLSocket socket, String host, String event) {
        System.out.println("EXPECT:" + event + "\t" + details(socket, host));
    }

    private static void expectOnce(SSLSocket socket, String host, String event) {
        System.out.println("EXPECT_ONCE:" + event + "\t" + details(socket, host));
    }

    private static String details(SSLSocket socket, String host) {
        InetAddress remote = socket.getInetAddress();
        InetAddress local = socket.isBound() ? socket.getLocalAddress() : null;
        return "SSLSocket[peerHost=" + host +
                ", remoteAddress=" + (remote == null ? UNAVAILABLE : remote.getHostAddress()) +
                ", remotePort=" + (remote == null ? UNAVAILABLE : socket.getPort()) +
                ", localAddress=" + (local == null ? UNAVAILABLE : local.getHostAddress()) +
                ", localPort=" + (local == null ? UNAVAILABLE : socket.getLocalPort()) +
                ", mode=" + (socket.getUseClientMode() ? "client" : "server") + "]";
    }
}
