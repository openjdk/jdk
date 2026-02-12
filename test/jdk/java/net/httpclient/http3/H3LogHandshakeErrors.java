/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpOption;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Handler;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.internal.net.http.quic.QuicConnectionImpl;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static org.testng.Assert.*;

/*
 * @test
 * @bug 8371471
 * @summary Verify that HTTP/3 handshake failures are logged if
 *          logging errors is enabled
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @run testng/othervm
 *              -Djdk.httpclient.HttpClient.log=errors
 *              H3LogHandshakeErrors
 */
// -Djava.security.debug=all
public class H3LogHandshakeErrors implements HttpServerAdapters {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private HttpTestServer h3Server;
    private ServerSocket tcpServerSocket = null;
    private Thread tcpServerThread = null;
    private String requestURI;
    private static Logger clientLogger;

    @BeforeClass
    public void beforeClass() throws Exception {
        // create an H3 only server
        h3Server = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext);
        h3Server.addHandler((exchange) -> exchange.sendResponseHeaders(200, 0), "/hello");
        h3Server.start();
        System.out.println("Server started at " + h3Server.getAddress());
        requestURI = "https://" + h3Server.serverAuthority() + "/hello";

        // Attempts to bind a TCP server socket on the same port
        // That server will just accept and immediately close
        // any connection. This is to make sure that we won't target
        // another server when falling back to TCP.
        tcpServerSocket = new ServerSocket();
        try {
            tcpServerSocket.bind(h3Server.getAddress());
            tcpServerThread = Thread.ofPlatform().daemon().start(() -> {
                System.out.println("tcpServerThread started");
                while (true) {
                    try (var accepted = tcpServerSocket.accept()) {
                        // close immediately
                    } catch (IOException x) {
                        System.out.println("tcpServerSocket: " + x);
                        break;
                    }
                }
                System.out.println("tcpServerThread stopped");
            });
        } catch (BindException x) {
            tcpServerSocket.close();
            // if tcpServerSocket is null we will use
            // HTTP3_URI_ONLY for the request
            tcpServerSocket = null;
        }
    }

    @AfterClass
    public void afterClass() throws Exception {
        if (h3Server != null) {
            System.out.println("Stopping server " + h3Server.getAddress());
            h3Server.stop();
        }
        if (tcpServerSocket != null) {
            tcpServerSocket.close();
            tcpServerThread.join();
        }
    }

    static String format(LogRecord record) {
        String thrown = Optional.ofNullable(record.getThrown())
                .map(t -> ": " + t).orElse("");
        return "\n  \"%s: %s %s: %s%s\"".formatted(
                record.getLevel(),
                record.getSourceClassName(),
                record.getSourceMethodName(),
                record.getMessage(),
                thrown);
    }

    /**
     * Issues a GET HTTP3 requests and verifies that the
     * expected exception is logged.
     */
    @Test
    public void testErrorLogging() throws Exception {
        // jdk.httpclient.HttpClient.log=errors must be enabled
        // for this test
        String logging = System.getProperty("jdk.httpclient.HttpClient.log", "");
        var categories = Arrays.asList(logging.split(","));
        assertTrue(categories.contains("errors"),
                "'errors' not found in " + categories);

        // create a client without the test specific SSLContext
        // so that the client doesn't have the server's
        // certificate
        final HttpClient client = newClientBuilderForH3()
                .proxy(NO_PROXY)
                .build();
        final URI reqURI = new URI(requestURI);
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(reqURI)
                .version(HTTP_3);
        if (tcpServerSocket == null) {
            // could not open ServerSocket on same port, only use HTTP/3
            reqBuilder.setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        }
        clientLogger = Logger.getLogger("jdk.httpclient.HttpClient");

        CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                // forces the LogRecord to evaluate the caller
                // while in the publish() method to make sure
                // the source class name and source method name
                // are correctly evaluated.
                record.getSourceClassName();
                records.add(record);
            }
            @Override public void flush() {}
            @Override public void close() {
            }
        };
        clientLogger.addHandler(handler);
        try {
            final HttpRequest req1 = reqBuilder.copy().GET().build();
            System.out.println("Issuing request: " + req1);
            final HttpResponse<Void> resp1 = client.send(req1, BodyHandlers.discarding());
            fail("Unexpected response from server: " + resp1);
        } catch (IOException io) {
            System.out.println("Got expected exception: " + io);
        } finally {
            LogRecord expected = null;
            // this is a bit fragile and may need to be updated if the
            // place where we log the exception from changes.
            String expectedClassName = QuicConnectionImpl.class.getName()
                    + "$HandshakeFlow";
            for (var record : records) {
                if (record.getLevel() != Level.INFO) continue;
                if (!record.getMessage().contains("ERROR:")) continue;
                if (record.getMessage().contains("client peer")) continue;
                var expectedThrown = record.getThrown();
                if (expectedThrown == null) continue;
                if (!record.getSourceClassName().equals(expectedClassName)) continue;
                if (expectedThrown.getMessage().contains("client peer")) continue;
                expected = record;
                break;
            }
            assertNotNull(expected, "No throwable for "
                    + expectedClassName + " found in "
                    + records.stream().map(H3LogHandshakeErrors::format).toList()
                    + "\n ");
            System.out.printf("Found expected exception: %s%n\t logged at: %s %s%n",
                    expected.getThrown(),
                    expected.getSourceClassName(),
                    expected.getSourceMethodName());
        }
    }
}
