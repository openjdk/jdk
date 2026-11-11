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

/*
 * @test
 * @bug 8394607
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext jdk.httpclient.test.lib.common.TestUtil
 *        jdk.httpclient.test.lib.http2.Http2TestServer
 * @run junit/othervm ${test.main.class}
 * @summary This test verifies that if a server sends a GOAWAY and/or
 *          closes the connection right after sending response body bytes,
 *          the client will still be able to pull the response bytes if
 *          they have been buffered.
 */


// Interesting additional settings for debugging and manual testing:
// -----------------------------------------------------------------
// -Djdk.httpclient.HttpClient.log=requests,errors,quic:retransmit:control,http3
// -Djdk.internal.httpclient.debug=err

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http3.Http3ServerExchange;
import jdk.internal.net.http.quic.streams.QuicSenderStream.SendingStreamState;
import jdk.test.lib.RandomFactory;
import jdk.test.lib.net.SimpleSSLContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static jdk.internal.net.http.quic.streams.QuicSenderStream.SendingStreamState.DATA_RECVD;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class H3RespondGoAwayAndClose implements HttpServerAdapters {
    private static HttpTestServer httpsServer;
    private static HttpClient client = null;
    static String httpsURIString;
    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private static final ExecutorService serverExec =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
                    .name("server-vt-worker-", 1).factory());
    private static final String RESPONSE = """
            Lorem ipsum dolor sit amet,
            caesarem legato alacrem.
            """;
    private static final Semaphore gate = new Semaphore(0);
    private static final Random random = RandomFactory.getRandom();

    private static void initialize() throws Exception {
        try {
            client = getClient();

            httpsServer = HttpTestServer.create(HTTP_3_URI_ONLY, sslContext, serverExec);
            httpsServer.addHandler(new TestHandler(), "/");
            httpsURIString = "https://" + httpsServer.serverAuthority() + "/bar/";

            httpsServer.start();
        } catch (Throwable e) {
            System.err.println("Throwing now");
            e.printStackTrace();
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        new H3RespondGoAwayAndClose().test();
    }

    @Test
    public void test() throws Exception {
        try {
            initialize();
            HttpRequest request = HttpRequest.newBuilder(URI.create(httpsURIString))
                    .version(HTTP_3)
                    .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY)
                    .GET().build();

            for (int i=0 ; i < 5; i++) {
                assertEquals(0, gate.availablePermits());
                System.out.printf("issuing request[%s]: %s%n", i, request);
                System.err.printf("issuing request[%s]: %s%n", i, request);
                var resp = client.send(request, BodyHandlers.ofInputStream());
                Assertions.assertEquals(200, resp.statusCode());
                assertEquals(HTTP_3, resp.version(), "unexpected HTTP version in response");

                System.out.println("awaiting connection termination");
                System.err.println("awaiting connection termination");
                gate.acquire();

                // At this point the server has closed the connection.
                // Wait a little bit to give a chance to the client to
                // observe connection closure, since having closed the
                // connection from the server side doesn't guarantee that
                // the ConnectionCloseFrame has reached the client side
                // and has already been processed.
                Thread.sleep(random.nextLong(100, 1000));

                // Start pulling the bytes from the input stream. The bytes
                // should have been all received and buffered, so it should
                // be possible to pull them even though the connection was closed
                System.out.println("Pulling response bytes");
                System.err.println("Pulling response bytes");
                byte[] bytes = resp.body().readAllBytes();
                assertArrayEquals(RESPONSE.getBytes(StandardCharsets.UTF_8), bytes);
                System.out.printf("Got expected response for request[%s]%n", i);
                System.err.printf("Got expected response for request[%s]%n", i);
            }
        } catch (Throwable tt) {
            System.err.println("tt caught");
            tt.printStackTrace();
            throw tt;
        } finally {
            client.close();
            httpsServer.stop();
            serverExec.close();
        }
    }

    static HttpClient createClient(SSLContext sslContext, ExecutorService clientExec) {
        var builder = HttpServerAdapters.createClientBuilderForH3()
                .sslContext(sslContext)
                .version(HTTP_3)
                .proxy(Builder.NO_PROXY);
        if (clientExec != null) {
            builder = builder.executor(clientExec);
        }
        return builder.build();
    }

    static HttpClient getClient() {
        if (client == null) {
            client = createClient(sslContext, null);
        }
        return client;
    }

    private final static byte[] RESPONSE_BYTES = RESPONSE.getBytes(StandardCharsets.UTF_8);

    private static class TestHandler implements HttpTestHandler {
        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try (var in = t.getRequestBody()) {
                byte[] input = in.readAllBytes();
                // passing -1 here ensures that each call to
                // out.write() will create a DataFrame.
                t.sendResponseHeaders(200, -1);
                assert RESPONSE_BYTES.length > 10
                        : "Insufficient body length: " + RESPONSE_BYTES.length;
                try (var out = t.getResponseBody()) {
                    for (int i=0; i<RESPONSE_BYTES.length; i+=10) {
                        int len = Math.min(10, RESPONSE_BYTES.length - i);
                        out.write(RESPONSE_BYTES, i, len);
                        out.flush();
                    }
                }
            }
            Http3ServerExchange exchange = t.getUnderlyingExchange(Http3ServerExchange.class);

            // close the exchange to ensure the sending stream reaches
            // a terminal state
            t.close();

            // wait until all body bytes have been acknowledged
            SendingStreamState state;
            while ((state = exchange.sendingStreamState()) != DATA_RECVD) {
                System.out.println("sending state is: " + state);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("sending state is: " + state);

            // send GOAWAY
            System.out.println("sending GOAWAY");
            exchange.sendGoAway();

            // Add a small delay to give a chance to the
            // GOAWAY to be processed by the client before
            // closing the connection
            try {
                Thread.sleep(random.nextLong(10, 200));
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
            }

            // Send connection close
            System.out.println("sending connection close");
            exchange.sendConnectionClose();

            // notify the client
            gate.release();
        }
    }

}
