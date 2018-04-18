/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 * @library /lib/testlibrary server
 * @build Http2TestServer
 * @build jdk.testlibrary.SimpleSSLContext
 * @run testng/othervm -Djdk.internal.httpclient.debug=true BadHeadersTest
 */

import jdk.internal.net.http.common.HttpHeadersImpl;
import jdk.internal.net.http.common.Pair;
import jdk.internal.net.http.frame.ContinuationFrame;
import jdk.internal.net.http.frame.HeaderFrame;
import jdk.internal.net.http.frame.HeadersFrame;
import jdk.internal.net.http.frame.Http2Frame;
import jdk.testlibrary.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static java.util.List.of;
import static jdk.internal.net.http.common.Pair.pair;
import static org.testng.Assert.assertThrows;

// Code copied from ContinuationFrameTest
public class BadHeadersTest {

    private static final List<List<Pair<String, String>>> BAD_HEADERS = of(
            of(pair(":status", "200"),  pair(":hello", "GET")),                      // Unknown pseudo-header
            of(pair(":status", "200"),  pair("hell o", "value")),                    // Space in the name
            of(pair(":status", "200"),  pair("hello", "line1\r\n  line2\r\n")),      // Multiline value
            of(pair(":status", "200"),  pair("hello", "DE" + ((char) 0x7F) + "L")),  // Bad byte in value
            of(pair("hello", "world!"), pair(":status", "200"))                      // Pseudo header is not the first one
    );

    SSLContext sslContext;
    Http2TestServer http2TestServer;   // HTTP/2 ( h2c )
    Http2TestServer https2TestServer;  // HTTP/2 ( h2  )
    String http2URI;
    String https2URI;

    /**
     * A function that returns a list of 1) a HEADERS frame ( with an empty
     * payload ), and 2) a CONTINUATION frame with the actual headers.
     */
    static BiFunction<Integer,List<ByteBuffer>,List<Http2Frame>> oneContinuation =
            (Integer streamid, List<ByteBuffer> encodedHeaders) -> {
                List<ByteBuffer> empty =  of(ByteBuffer.wrap(new byte[0]));
                HeadersFrame hf = new HeadersFrame(streamid, 0, empty);
                ContinuationFrame cf = new ContinuationFrame(streamid,
                                                             HeaderFrame.END_HEADERS,
                                                             encodedHeaders);
                return of(hf, cf);
            };

    /**
     * A function that returns a list of a HEADERS frame followed by a number of
     * CONTINUATION frames. Each frame contains just a single byte of payload.
     */
    static BiFunction<Integer,List<ByteBuffer>,List<Http2Frame>> byteAtATime =
            (Integer streamid, List<ByteBuffer> encodedHeaders) -> {
                assert encodedHeaders.get(0).hasRemaining();
                List<Http2Frame> frames = new ArrayList<>();
                ByteBuffer hb = ByteBuffer.wrap(new byte[] {encodedHeaders.get(0).get()});
                HeadersFrame hf = new HeadersFrame(streamid, 0, hb);
                frames.add(hf);
                for (ByteBuffer bb : encodedHeaders) {
                    while (bb.hasRemaining()) {
                        List<ByteBuffer> data = of(ByteBuffer.wrap(new byte[] {bb.get()}));
                        ContinuationFrame cf = new ContinuationFrame(streamid, 0, data);
                        frames.add(cf);
                    }
                }
                frames.get(frames.size() - 1).setFlag(HeaderFrame.END_HEADERS);
                return frames;
            };

    @DataProvider(name = "variants")
    public Object[][] variants() {
        return new Object[][] {
                { http2URI,  false, oneContinuation },
                { https2URI, false, oneContinuation },
                { http2URI,  true,  oneContinuation },
                { https2URI, true,  oneContinuation },

                { http2URI,  false, byteAtATime },
                { https2URI, false, byteAtATime },
                { http2URI,  true,  byteAtATime },
                { https2URI, true,  byteAtATime },
        };
    }


    @Test(dataProvider = "variants")
    void test(String uri,
              boolean sameClient,
              BiFunction<Integer,List<ByteBuffer>,List<Http2Frame>> headerFramesSupplier)
            throws Exception
    {
        CFTHttp2TestExchange.setHeaderFrameSupplier(headerFramesSupplier);

        HttpClient client = null;
        for (int i=0; i< BAD_HEADERS.size(); i++) {
            if (!sameClient || client == null)
                client = HttpClient.newBuilder().sslContext(sslContext).build();

            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .POST(BodyPublishers.ofString("Hello there!"))
                    .build();
            final HttpClient cc = client;
            if (i % 2 == 0) {
                assertThrows(IOException.class, () -> cc.send(request, BodyHandlers.ofString()));
            } else {
                Throwable t = null;
                try {
                    cc.sendAsync(request, BodyHandlers.ofString()).join();
                } catch (Throwable t0) {
                    t = t0;
                }
                if (t == null) {
                    throw new AssertionError("An exception was expected");
                }
                if (t instanceof CompletionException) {
                    Throwable c = t.getCause();
                    if (!(c instanceof IOException)) {
                        throw new AssertionError("Unexpected exception", c);
                    }
                } else if (!(t instanceof IOException)) {
                    throw new AssertionError("Unexpected exception", t);
                }
            }
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        http2TestServer = new Http2TestServer("localhost", false, 0) {
            @Override
            protected Http2TestServerConnection createConnection(Http2TestServer http2TestServer,
                                                                 Socket socket,
                                                                 Http2TestExchangeSupplier exchangeSupplier)
                    throws IOException {
                return new Http2TestServerConnection(http2TestServer, socket, exchangeSupplier) {
                    @Override
                    protected HttpHeadersImpl createNewResponseHeaders() {
                        return new OrderedHttpHeaders();
                    }
                };
            }
        };
        http2TestServer.addHandler(new Http2EchoHandler(), "/http2/echo");
        int port = http2TestServer.getAddress().getPort();
        http2URI = "http://localhost:" + port + "/http2/echo";

        https2TestServer = new Http2TestServer("localhost", true, 0){
            @Override
            protected Http2TestServerConnection createConnection(Http2TestServer http2TestServer,
                                                                 Socket socket,
                                                                 Http2TestExchangeSupplier exchangeSupplier)
                    throws IOException {
                return new Http2TestServerConnection(http2TestServer, socket, exchangeSupplier) {
                    @Override
                    protected HttpHeadersImpl createNewResponseHeaders() {
                        return new OrderedHttpHeaders();
                    }
                };
            }
        };
        https2TestServer.addHandler(new Http2EchoHandler(), "/https2/echo");
        port = https2TestServer.getAddress().getPort();
        https2URI = "https://localhost:" + port + "/https2/echo";

        // Override the default exchange supplier with a custom one to enable
        // particular test scenarios
        http2TestServer.setExchangeSupplier(CFTHttp2TestExchange::new);
        https2TestServer.setExchangeSupplier(CFTHttp2TestExchange::new);

        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        http2TestServer.stop();
        https2TestServer.stop();
    }

    static class Http2EchoHandler implements Http2Handler {

        private final AtomicInteger requestNo = new AtomicInteger();

        @Override
        public void handle(Http2TestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                int i = requestNo.incrementAndGet();
                List<Pair<String, String>> p = BAD_HEADERS.get(i % BAD_HEADERS.size());
                p.forEach(h -> t.getResponseHeaders().addHeader(h.first, h.second));
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }

    // A custom Http2TestExchangeImpl that overrides sendResponseHeaders to
    // allow headers to be sent with a number of CONTINUATION frames.
    static class CFTHttp2TestExchange extends Http2TestExchangeImpl {
        static volatile BiFunction<Integer,List<ByteBuffer>,List<Http2Frame>> headerFrameSupplier;

        static void setHeaderFrameSupplier(BiFunction<Integer,List<ByteBuffer>,List<Http2Frame>> hfs) {
            headerFrameSupplier = hfs;
        }

        CFTHttp2TestExchange(int streamid, String method, HttpHeadersImpl reqheaders,
                             HttpHeadersImpl rspheaders, URI uri, InputStream is,
                             SSLSession sslSession, BodyOutputStream os,
                             Http2TestServerConnection conn, boolean pushAllowed) {
            super(streamid, method, reqheaders, rspheaders, uri, is, sslSession,
                  os, conn, pushAllowed);

        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
            List<ByteBuffer> encodeHeaders = conn.encodeHeaders(rspheaders);
            List<Http2Frame> headerFrames = headerFrameSupplier.apply(streamid, encodeHeaders);
            assert headerFrames.size() > 0;  // there must always be at least 1

            if (responseLength < 0) {
                headerFrames.get(headerFrames.size() -1).setFlag(HeadersFrame.END_STREAM);
                os.closeInternal();
            }

            for (Http2Frame f : headerFrames)
                conn.outputQ.put(f);

            os.goodToGo();
            System.err.println("Sent response headers " + rCode);
        }
    }

    /*
     * Use carefully. This class might not be suitable outside this test's
     * context. Pay attention working with multi Map view returned from map().
     * The reason is that header names must be lower-cased prior to any
     * operation that depends on whether or not the map contains a specific
     * element.
     */
    private static class OrderedHttpHeaders extends HttpHeadersImpl {

        private final Map<String, List<String>> map = new LinkedHashMap<>();

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name.toLowerCase(Locale.ROOT), value);
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name.toLowerCase(Locale.ROOT), value);
        }

        @Override
        protected Map<String, List<String>> headersMap() {
            return map;
        }

        @Override
        protected HttpHeadersImpl newDeepCopy() {
            return new OrderedHttpHeaders();
        }
    }
}
