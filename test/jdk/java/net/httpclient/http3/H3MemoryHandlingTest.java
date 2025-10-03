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
import jdk.httpclient.test.lib.quic.QuicServerConnection;
import jdk.httpclient.test.lib.quic.QuicStandaloneServer;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.TerminationCause;
import jdk.internal.net.http.quic.streams.QuicBidiStream;
import jdk.internal.net.quic.QuicVersion;
import jdk.test.lib.net.SimpleSSLContext;
import jdk.test.lib.net.URIBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.net.http.HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY;
import static java.net.http.HttpOption.H3_DISCOVERY;
import static org.testng.Assert.*;

/*
 * @test
 * @summary Verifies that the HTTP client does not buffer excessive amounts of data
 * @library /test/lib /test/jdk/java/net/httpclient/lib
 * @library ../access
 * @build jdk.test.lib.net.SimpleSSLContext
 *        jdk.httpclient.test.lib.common.HttpServerAdapters
 * @build java.net.http/jdk.internal.net.http.Http3ConnectionAccess
 * @run testng/othervm
 *              -Djdk.internal.httpclient.debug=true
 *              -Djdk.httpclient.HttpClient.log=requests,responses,errors
 *              -Djdk.httpclient.quic.maxStreamInitialData=16384
 *              -Djdk.httpclient.quic.streamBufferSize=2048 H3MemoryHandlingTest
 */
public class H3MemoryHandlingTest implements HttpServerAdapters {

    private SSLContext sslContext;
    private QuicStandaloneServer server;
    private String requestURIBase;

    @BeforeClass
    public void beforeClass() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null) {
            throw new AssertionError("Unexpected null sslContext");
        }
        server = QuicStandaloneServer.newBuilder()
                .availableVersions(new QuicVersion[]{QuicVersion.QUIC_V1})
                .sslContext(sslContext)
                .alpn("h3")
                .build();
        server.start();
        System.out.println("Server started at " + server.getAddress());
        requestURIBase = URIBuilder.newBuilder().scheme("https").loopback()
                .port(server.getAddress().getPort()).build().toString();
    }

    @AfterClass
    public void afterClass() throws Exception {
        if (server != null) {
            System.out.println("Stopping server " + server.getAddress());
            server.close();
        }
    }

    /**
     * Server sends a large response, and the user code does not read from the input stream.
     * Writing on the server side should block once the buffers are full.
     */
    @Test
    public void testOfInputStreamBlocks() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        byte[] response =  HexFormat.of().parseHex(
                "01030000"+ // headers, length 3, section prefix
                        "d9"+ // :status:200
                        "00ffffffffffffffff"); // data, 2^62 - 1 bytes
        byte[] kilo = new byte[1024];
        final CompletableFuture<Boolean> serverAllWritesDone = new CompletableFuture<>();
        server.addHandler((c,s)-> {
            // verify that the connection stays open
            completeUponTermination(c, errorCF);
            try (OutputStream outputStream = s.outputStream()) {
                outputStream.write(response);
                for (int i = 0; i < 20; i++) {
                    // 18 writes should succeed, 19th should block
                    outputStream.write(kilo);
                    System.out.println("Wrote "+(i+1)+"KB");
                }
                // all 20 writes unexpectedly completed
                serverAllWritesDone.complete(true);
            } catch(IOException ex) {
                System.out.println("Got expected exception: " + ex);
                serverAllWritesDone.complete(false);
            }
        });
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<InputStream> response1 = client.send(
                    request, BodyHandlers.ofInputStream());
            assertEquals(response1.statusCode(), 200);
            assertFalse(errorCF.isDone(), "Expected the connection to be open");
            assertFalse(serverAllWritesDone.isDone());
            response1.body().close();
            final boolean done = serverAllWritesDone.get(10, TimeUnit.SECONDS);
            assertFalse(done, "Too much data was buffered by the client");
        } finally {
            client.close();
        }
    }

    /**
     * Server sends a large response, and the user code does not read from the input stream.
     * Writing on the server side should unblock once the client starts receiving.
     */
    @Test
    public void testOfInputStreamUnblocks() throws Exception {
        CompletableFuture<TerminationCause> errorCF = new CompletableFuture<>();
        CompletableFuture<Boolean> handlerCF = new CompletableFuture<>();
        byte[] response =  HexFormat.of().parseHex(
                "01030000"+ // headers, length 3, section prefix
                        "d9"+ // :status:200
                        "0080008000"); // data, 32 KB
        byte[] kilo = new byte[1024];
        CountDownLatch writerBlocked = new CountDownLatch(1);

        server.addHandler((c,s)-> {
            // verify that the connection stays open
            completeUponTermination(c, errorCF);
            QuicBidiStream qs = s.underlyingBidiStream();

            try (OutputStream outputStream = s.outputStream()) {
                outputStream.write(response);
                for (int i = 0;i < 32;i++) {
                    // 18 writes should succeed, 19th should block
                    if (i == 18) {
                        writerBlocked.countDown();
                    }
                    outputStream.write(kilo);
                    System.out.println("Wrote "+(i+1)+"KB");
                }
                handlerCF.complete(true);
            } catch (IOException e) {
                handlerCF.completeExceptionally(e);
            }
        });
        HttpClient client = getHttpClient();
        try {
            HttpRequest request = getRequest();
            final HttpResponse<InputStream> response1 = client.send(
                            request, BodyHandlers.ofInputStream());
            assertEquals(response1.statusCode(), 200);
            assertFalse(errorCF.isDone(), "Expected the connection to be open");
            assertFalse(handlerCF.isDone());
            assertTrue(writerBlocked.await(10, TimeUnit.SECONDS),
                    "write of 18 KB should have succeeded");
            System.out.println("Wait completed, receiving response");
            byte[] receivedResponse;
            try (InputStream body = response1.body()) {
                receivedResponse = body.readAllBytes();
            }
            assertEquals(receivedResponse.length, 32768,
                    "Unexpected response length");
        } finally {
            client.close();
        }
        assertTrue(handlerCF.get(10, TimeUnit.SECONDS),
                "Unexpected result");
    }

    private HttpRequest getRequest() throws URISyntaxException {
        final URI reqURI = new URI(requestURIBase + "/hello");
        final HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(reqURI)
                .version(Version.HTTP_3)
                .setOption(H3_DISCOVERY, HTTP_3_URI_ONLY);
        return reqBuilder.build();
    }

    private HttpClient getHttpClient() {
        final HttpClient client = newClientBuilderForH3()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(Version.HTTP_3)
                .sslContext(sslContext).build();
        return client;
    }

    private static String toHexString(final Http3Error error) {
        return error.name() + "(0x" + Long.toHexString(error.code()) + ")";
    }

    private static void completeUponTermination(final QuicServerConnection serverConnection,
                                                final CompletableFuture<TerminationCause> cf) {
        serverConnection.futureTerminationCause().handle(
                (r,t) -> t != null ? cf.completeExceptionally(t) : cf.complete(r));
    }
}
