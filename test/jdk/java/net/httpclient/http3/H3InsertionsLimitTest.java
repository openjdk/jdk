/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.httpclient.test.lib.common.HttpServerAdapters;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.httpclient.test.lib.quic.QuicServer;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.qpack.TableEntry;
import jdk.test.lib.Utils;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;

/*
 * @test
 * @summary Verifies that the HTTP client respects the maxLiteralWithIndexing
 *          system property value.
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @library ../access
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @build java.net.http/jdk.internal.net.http.Http3ConnectionAccess
 * @run testng/othervm -Djdk.httpclient.qpack.encoderTableCapacityLimit=4096
 *                     -Djdk.internal.httpclient.qpack.allowBlockingEncoding=true
 *                     -Djdk.httpclient.qpack.decoderMaxTableCapacity=4096
 *                     -Djdk.httpclient.qpack.decoderBlockedStreams=1024
 *                     -Dhttp3.test.server.encoderAllowedHeaders=*
 *                     -Dhttp3.test.server.decoderMaxTableCapacity=4096
 *                     -Dhttp3.test.server.encoderTableCapacityLimit=4096
 *                     -Djdk.httpclient.maxLiteralWithIndexing=32
 *                     -Djdk.internal.httpclient.qpack.log.level=EXTRA
 *                     H3InsertionsLimitTest
 */
public class H3InsertionsLimitTest implements HttpServerAdapters {

    private static final long HEADER_SIZE_LIMIT_BYTES = 8192;
    private static final long MAX_SERVER_DT_CAPACITY = 4096;
    private SSLContext sslContext;
    private HttpTestServer h3Server;
    private String requestURIBase;
    public static final long MAX_LITERALS_WITH_INDEXING = 32L;
    private static final CountDownLatch WAIT_FOR_FAILURE = new CountDownLatch(1);

    private static void handle(HttpTestExchange exchange) throws IOException {
        String handlerMsg = "Server handler: " + exchange.getRequestURI();
        System.out.println(handlerMsg);
        System.err.println(handlerMsg);
        Encoder encoder = exchange.qpackEncoder();
        long unusedStreamID = 1111;
        // Mimic entry insertions on server-side
        try (Encoder.EncodingContext context =
                     encoder.newEncodingContext(unusedStreamID, 0, encoder.newHeaderFrameWriter())) {
            for (int i = 0; i <= MAX_LITERALS_WITH_INDEXING; i++) {
                var entry = new TableEntry("n" + i, "v" + i);
                var insertedEntry = context.tryInsertEntry(entry);
                if (insertedEntry.index() == -1L) {
                    throw new RuntimeException("Test issue: cannot insert" +
                            " entry to the encoder dynamic table");
                }
            }
        }
        try {
            WAIT_FOR_FAILURE.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("Test Issue: handler interrupted", e);
        }
        // Send a response
        exchange.sendResponseHeaders(200, 0);
    }

    @BeforeClass
    public void beforeClass() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        final QuicServer quicServer = Http3TestServer.quicServerBuilder()
                .bindAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .sslContext(sslContext)
                .build();
        var http3TestServer = new Http3TestServer(quicServer)
                .setConnectionSettings(new ConnectionSettings(
                        HEADER_SIZE_LIMIT_BYTES, MAX_SERVER_DT_CAPACITY, 0));

        h3Server = HttpTestServer.of(http3TestServer);
        h3Server.addHandler(H3InsertionsLimitTest::handle, "/insertions");
        h3Server.start();
        System.out.println("Server started at " + h3Server.getAddress());
        requestURIBase = URIBuilder.newBuilder().scheme("https").loopback()
                .port(h3Server.getAddress().getPort()).build().toString();
    }

    @AfterClass
    public void afterClass() throws Exception {
        if (h3Server != null) {
            System.out.println("Stopping server " + h3Server.getAddress());
            h3Server.stop();
        }
    }

    @Test
    public void multipleTableInsertions() throws Exception {
        final HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(Version.HTTP_3)
                // the server drops 1 packet out of two!
                .connectTimeout(Duration.ofSeconds(Utils.adjustTimeout(10)))
                .sslContext(sslContext).build();
        final URI reqURI = new URI(requestURIBase + "/insertions");
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(reqURI)
                .version(Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        final HttpRequest request =
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString("Hello")).build();
        System.out.println("Issuing request to " + reqURI);
        try {
            client.send(request, BodyHandlers.discarding());
            Assert.fail("IOException expected");
        } catch (IOException ioe) {
            System.out.println("Got IOException: " + ioe);
            Assert.assertTrue(ioe.getMessage()
                                 .contains("Too many literal with indexing"));
        } finally {
            WAIT_FOR_FAILURE.countDown();
        }
    }
}
