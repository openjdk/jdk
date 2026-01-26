/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestExchange;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestHandler;
import jdk.httpclient.test.lib.common.HttpServerAdapters.HttpTestServer;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.httpclient.test.lib.quic.QuicServer;
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.internal.net.http.quic.QuicTransportParameters;
import jdk.internal.net.http.quic.QuicTransportParameters.ParameterId;
import jdk.test.lib.net.SimpleSSLContext;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_3;
import static java.net.http.HttpOption.H3_DISCOVERY;

/*
 * @test
 * @summary verifies that when the Quic stream limit is reached
 *          then HTTP3 requests are retried on newer connection.
 *          This test uses an HTTP/3 only test server, which is
 *          configured to allow the test to control when a new
 *          MAX_STREAMS frames is sent to the client.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 *        jdk.httpclient.test.lib.http3.Http3TestServer
 * @run testng/othervm -Djdk.internal.httpclient.debug=true StreamLimitTest
 */
public class StreamLimitTest {

    private static final SSLContext sslContext = SimpleSSLContext.findSSLContext();
    private HttpTestServer server;
    private QuicServer quicServer;
    private URI requestURI;
    private volatile QuicServerConnection latestServerConn;

    private final class Handler implements HttpTestHandler {

        @Override
        public void handle(HttpTestExchange exchange) throws IOException {
            final String handledBy = latestServerConn.logTag();
            System.out.println(handledBy + " handling request " + exchange.getRequestURI());
            final byte[] respBody;
            if (handledBy == null) {
                respBody = new byte[0];
            } else {
                respBody = handledBy.getBytes(StandardCharsets.UTF_8);
            }
            exchange.sendResponseHeaders(200, respBody.length == 0 ? -1 : respBody.length);
            // write out the server's connection id as a response
            if (respBody.length > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(respBody);
                }
            }
            exchange.close();
        }
    }

    @BeforeClass
    public void beforeClass() throws Exception {
        quicServer = Http3TestServer.quicServerBuilder().sslContext(sslContext).build();
        final Http3TestServer h3Server = new Http3TestServer(quicServer) {
            @Override
            public boolean acceptIncoming(SocketAddress source, QuicServerConnection quicConn) {
                final boolean accepted = super.acceptIncoming(source, quicConn);
                if (accepted) {
                    // keep track of the latest server connection
                    latestServerConn = quicConn;
                }
                return accepted;
            }
        };
        server = HttpTestServer.of(h3Server);
        server.addHandler(new Handler(), "/foo");
        server.start();
        System.out.println("Server started at " + server.serverAuthority());
        requestURI = new URI("https://" + server.serverAuthority() + "/foo");
    }

    @AfterClass
    public void afterClass() throws Exception {
        latestServerConn = null;
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Configures different limits for max bidi stream creation by HTTP client and then verifies
     * the expected behaviour by sending HTTP3 requests
     */
    @Test
    public void testBidiMaxStreamLimit() throws Exception {
        final QuicTransportParameters transportParameters = new QuicTransportParameters();
        final int intialMaxStreamLimit = 3;
        final AtomicInteger maxStreamLimit = new AtomicInteger(intialMaxStreamLimit);
        transportParameters.setIntParameter(ParameterId.initial_max_streams_bidi,
                maxStreamLimit.get());
        // set the limit so that any new server connections created with advertise this limit
        // to the peer (client)
        quicServer.setTransportParameters(transportParameters);
        // also set a MAX_STREAMS limit computer for this server so that the created
        // connections use this computer for deciding MAX_STREAMS limit
        quicServer.setMaxStreamLimitComputer((ignore) -> maxStreamLimit.longValue());
        final HttpClient client = HttpServerAdapters.createClientBuilderForH3()
                .proxy(NO_PROXY)
                .sslContext(sslContext)
                .build();
        final HttpRequest req = HttpRequest.newBuilder().version(HTTP_3)
                .GET().uri(requestURI)
                .setOption(H3_DISCOVERY, server.h3DiscoveryConfig())
                .build();
        String requestHandledBy = null;
        System.out.println("Server has been configured with a limit for max" +
                " bidi streams: " + intialMaxStreamLimit);
        // issue N number of requests where N == the max bidi stream creation limit that is
        // advertised to the peer. All these N requests are expected to be handled by the same
        // server connection
        for (int i = 1; i <= intialMaxStreamLimit; i++) {
            System.out.println("Sending request " + i + " to " + requestURI);
            final HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(resp.version(), HTTP_3, "Unexpected response version");
            Assert.assertEquals(resp.statusCode(), 200, "Unexpected response code");
            final String respBody = resp.body();
            System.out.println("Request " + i + " was handled by server connection: " + respBody);
            if (i == 1) {
                // first request; keep track the server connection id which responded
                // to this request
                requestHandledBy = respBody;
            } else {
                Assert.assertEquals(respBody, requestHandledBy, "Request was handled by an" +
                        " unexpected server connection");
            }
        }
        // at this point the limit for bidi stream creation has reached on the client.
        // now issue a request so that:
        // - the HTTP client implementation notices that it has hit the limit
        // - HTTP client sends a STREAMS_BLOCKED frame and waits for a while (timeout derived
        //   internally based on request timeout) for server to increase the limit. But this server
        //   connection will not send any MAX_STREAMS frame upon receipt of STREAMS_BLOCKED frame
        // - client notices that server connection hasn't increased the stream limit, so internally
        //   retries the request, which should trigger a new server connection and thus this
        //   request should be handled by a different server connection than the last N requests
        final HttpRequest reqWithTimeout = HttpRequest.newBuilder().version(HTTP_3)
                .GET().uri(requestURI)
                .setOption(H3_DISCOVERY, server.h3DiscoveryConfig())
                .timeout(Duration.of(10, ChronoUnit.SECONDS))
                .build();
        for (int i = 1; i <= intialMaxStreamLimit; i++) {
            System.out.println("Sending request " + i + " (configured with timeout) to "
                    + requestURI);
            final HttpResponse<String> resp = client.send(reqWithTimeout,
                    HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(resp.version(), HTTP_3, "Unexpected response version");
            Assert.assertEquals(resp.statusCode(), 200, "Unexpected response code");
            final String respBody = resp.body();
            System.out.println("Request " + i + " was handled by server connection: " + respBody);
            if (i == 1) {
                // first request after the limit was hit.
                // verify that it was handled by a new connection and not the one that handled
                // the previous N requests
                Assert.assertNotEquals(respBody, requestHandledBy, "Request was expected to be" +
                        " handled by a new server connection, but wasn't");
                // keep track this new server connection id which responded to this request
                requestHandledBy = respBody;
            } else {
                Assert.assertEquals(respBody, requestHandledBy, "Request was handled by an" +
                        " unexpected server connection");
            }
        }
        // at this point the limit for bidi stream creation has reached on the client, for
        // this new server connection. we now configure this current server connection to
        // increment the limit to new higher limit
        maxStreamLimit.set(intialMaxStreamLimit + 2);
        System.out.println("Server connection " + latestServerConn + " has now been configured to" +
                " increment the bidi stream creation limit to " + maxStreamLimit.get());
        // we now issue new requests, with timeout specified
        // - the HTTP client implementation notices that it has hit the limit
        // - HTTP client sends a STREAMS_BLOCKED frame and waits for a while (timeout derived
        //   internally based on request timeout) for server to increase the limit. This server
        //   connection, since it is configured to increment the stream limit, will send
        //   MAX_STREAMS frame upon receipt of STREAMS_BLOCKED frame
        // - client receives the MAX_STREAMS frame (hopefully within the timeout) and
        //   notices that server connection has increased the stream limit, so opens a new bidi
        //   stream and lets the request move forward (on the same server connection)
        final int numNewRequests = maxStreamLimit.get() - intialMaxStreamLimit;
        for (int i = 1; i <= numNewRequests; i++) {
            System.out.println("Sending request " + i + " (after stream limit has been increased)" +
                    " to " + requestURI);
            final HttpResponse<String> resp = client.send(reqWithTimeout,
                    HttpResponse.BodyHandlers.ofString());
            Assert.assertEquals(resp.version(), HTTP_3, "Unexpected response version");
            Assert.assertEquals(resp.statusCode(), 200, "Unexpected response code");
            final String respBody = resp.body();
            System.out.println("Request " + i + " was handled by server connection: " + respBody);
            // all these requests should be handled by the same server connection which handled
            // the previous requests
            Assert.assertEquals(respBody, requestHandledBy, "Request was handled by an" +
                    " unexpected server connection");
        }
        // at this point the newer limit for bidi stream creation has reached on the client.
        // we now issue a new request without any timeout configured on the request, so that the
        // client internally just immediately retries and uses a different connection on noticing
        // that the stream creation limit for the current server connection has been reached.
        System.out.println("Server connection " + latestServerConn + " has now been configured to" +
                " not increase max stream limit for bidi streams created by the client");
        final HttpRequest finalReq = HttpRequest.newBuilder()
                .version(HTTP_3)
                .setOption(H3_DISCOVERY, server.h3DiscoveryConfig())
                .GET().uri(requestURI)
                .build();
        System.out.println("Sending request, without timeout, to " + requestURI);
        final HttpResponse<String> finalResp = client.send(finalReq,
                HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(finalResp.version(), HTTP_3, "Unexpected response version");
        Assert.assertEquals(finalResp.statusCode(), 200, "Unexpected response code");
        final String finalRespBody = finalResp.body();
        System.out.println("Request was handled by server connection: " + finalRespBody);
        // this request should have been handled by a new server connection
        Assert.assertNotEquals(finalRespBody, requestHandledBy, "Request was handled by an" +
                " unexpected server connection");
    }
}
