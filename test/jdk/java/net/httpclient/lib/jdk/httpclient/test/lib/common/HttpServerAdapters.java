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
package jdk.httpclient.test.lib.common;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import jdk.httpclient.test.lib.http2.Http2Handler;
import jdk.httpclient.test.lib.http2.Http2TestExchange;
import jdk.httpclient.test.lib.http2.Http2TestServer;
import jdk.httpclient.test.lib.http3.Http3TestServer;
import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.qpack.Encoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.io.ByteArrayInputStream;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Version;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpOption.Http3DiscoveryMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpClient.Version.HTTP_2;
import static java.net.http.HttpClient.Version.HTTP_3;
import static jdk.test.lib.Asserts.assertFileContentsEqual;

/**
 * Defines an adaptation layers so that a test server handlers and filters
 * can be implemented independently of the underlying server version.
 * <p>
 * For instance:
 * <pre>{@code
 *
 *  URI http1URI, http2URI;
 *
 *  InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
 *  HttpTestServer server1 = HttpTestServer.of(HttpServer.create(sa, 0));
 *  HttpTestContext context = server.addHandler(new HttpTestEchoHandler(), "/http1/echo");
 *  http2URI = "http://localhost:" + server1.getAddress().getPort() + "/http1/echo";
 *
 *  Http2TestServer http2TestServer = new Http2TestServer("localhost", false, 0);
 *  HttpTestServer server2 = HttpTestServer.of(http2TestServer);
 *  server2.addHandler(new HttpTestEchoHandler(), "/http2/echo");
 *  http1URI = "http://localhost:" + server2.getAddress().getPort() + "/http2/echo";
 *
 *  }</pre>
 */
public interface HttpServerAdapters {

    static final boolean PRINTSTACK = getPrintStack();
    private static boolean getPrintStack() {
        return Boolean.getBoolean("jdk.internal.httpclient.debug");
    }
    static final HexFormat HEX_FORMAT = HexFormat.ofDelimiter(":").withUpperCase();

    static void printBytes(PrintStream out, String prefix, byte[] bytes) {
        out.println(prefix + bytes.length + " " + HEX_FORMAT.formatHex(bytes));
    }

    /**
     * A version agnostic adapter class for HTTP request Headers.
     */
    public static abstract class HttpTestRequestHeaders {
        public abstract Optional<String> firstValue(String name);
        public abstract Set<String> keySet();
        public abstract Set<Map.Entry<String, List<String>>> entrySet();
        public abstract List<String> get(String name);
        public abstract boolean containsKey(String name);
        public abstract OptionalLong firstValueAsLong(String name);
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HttpTestRequestHeaders other)) return false;
            return Objects.equals(entrySet(), other.entrySet());
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(entrySet());
        }

        public static HttpTestRequestHeaders of(Headers headers) {
            return new Http1TestRequestHeaders(headers);
        }

        public static HttpTestRequestHeaders of(HttpHeaders headers) {
            return new Http2TestRequestHeaders(headers);
        }

        private static final class Http1TestRequestHeaders extends HttpTestRequestHeaders {
            private final Headers headers;
            Http1TestRequestHeaders(Headers h) { this.headers = h; }
            @Override
            public Optional<String> firstValue(String name) {
                if (headers.containsKey(name)) {
                    return Optional.ofNullable(headers.getFirst(name));
                }
                return Optional.empty();
            }
            @Override
            public Set<String> keySet() { return headers.keySet(); }
            @Override
            public Set<Map.Entry<String, List<String>>> entrySet() {
                return headers.entrySet();
            }
            @Override
            public List<String> get(String name) {
                return headers.get(name);
            }
            @Override
            public boolean containsKey(String name) {
                return headers.containsKey(name);
            }
            @Override
            public OptionalLong firstValueAsLong(String name) {
                return Optional.ofNullable(headers.getFirst(name))
                        .stream().mapToLong(Long::parseLong).findFirst();
            }
            @Override
            public String toString() {
                return String.valueOf(headers);
            }
        }
        private static final class Http2TestRequestHeaders extends HttpTestRequestHeaders {
            private final HttpHeaders headers;
            Http2TestRequestHeaders(HttpHeaders h) { this.headers = h; }
            @Override
            public Optional<String> firstValue(String name) {
                return headers.firstValue(name);
            }
            @Override
            public Set<String> keySet() { return headers.map().keySet(); }
            @Override
            public Set<Map.Entry<String, List<String>>> entrySet() {
                return headers.map().entrySet();
            }
            @Override
            public List<String> get(String name) {
                return headers.allValues(name);
            }
            @Override
            public boolean containsKey(String name) {
                return headers.firstValue(name).isPresent();
            }
            @Override
            public OptionalLong firstValueAsLong(String name) {
                return headers.firstValueAsLong(name);
            }
            @Override
            public String toString() {
                return String.valueOf(headers);
            }
        }
    }

    /**
     * A version agnostic adapter class for HTTP response Headers.
     */
    public static abstract class HttpTestResponseHeaders {
        public abstract void addHeader(String name, String value);

        public static HttpTestResponseHeaders of(Headers headers) {
            return new Http1TestResponseHeaders(headers);
        }
        public static HttpTestResponseHeaders of(HttpHeadersBuilder headersBuilder) {
            return new Http2TestResponseHeaders(headersBuilder);
        }

        private final static class Http1TestResponseHeaders extends HttpTestResponseHeaders {
            private final Headers headers;
            Http1TestResponseHeaders(Headers h) { this.headers = h; }
            @Override
            public void addHeader(String name, String value) {
                headers.add(name, value);
            }
        }
        private final static class Http2TestResponseHeaders extends HttpTestResponseHeaders {
            private final HttpHeadersBuilder headersBuilder;
            Http2TestResponseHeaders(HttpHeadersBuilder hb) { this.headersBuilder = hb; }
            @Override
            public void addHeader(String name, String value) {
                headersBuilder.addHeader(name, value);
            }
        }
    }

    /**
     * A version agnostic adapter class for HTTP Server Exchange.
     */
    public static abstract class HttpTestExchange implements AutoCloseable {
        /**
         * This constant can be passed to {@link #sendResponseHeaders(int, long)}
         * to indicate an empty response.
         */
        public static final int RSPBODY_EMPTY = 0;
        /**
         * This constant can be passed to {@link #sendResponseHeaders(int, long)}
         * to indicate that the response will be chunked.
         */
        public static final int RSPBODY_CHUNKED = -1;

        /**
         * {@return the response length to pass to {@link #sendResponseHeaders(int, long)}
         * if the response is not chunked}
         * @param bytes the response length
         */
        public static long fixedRsp(long bytes) {
            return bytes == 0 ? RSPBODY_EMPTY : bytes;
        }

        /**
         * {@return the response length to pass to {@link #sendResponseHeaders(int, long)}}
         * This is the response length when `chunked` is false, and
         * {@link #RSPBODY_CHUNKED} otherwise.
         * @param length   The number of bytes to send
         * @param chunked  Whether the response should be chunked
         */
        public static long responseLength(long length, boolean chunked) {
            return chunked ? HttpTestExchange.RSPBODY_CHUNKED : fixedRsp(length);
        }

        /**
         * {@return true if the {@linkplain #getRequestHeaders() request headers}
         * contain {@code XFixed: yes}}
         */
        public boolean rspFixedRequested() {
            return "yes".equals(getRequestHeaders()
                    .firstValue("XFixed")
                    .orElse(null));
        }

        /**
         * {@return the length to be passed to {@link #sendResponseHeaders(int, long)},
         *  taking into account whether using {@linkplain #rspFixedRequested()
         *  fixed length was requested} in the {@linkplain #getRequestHeaders()
         *  request headers}.}
         * By default, returns {@link #RSPBODY_CHUNKED} unless {@linkplain
         * #rspFixedRequested() fixed length was requested}.
         * @param length the length to use in content-length if fixed length is used.
         */
        public long responseLength(long length) {
            return responseLength(length, !rspFixedRequested());
        }

        public abstract Version getServerVersion();
        public abstract Version getExchangeVersion();
        public abstract InputStream   getRequestBody();
        public abstract OutputStream  getResponseBody();
        public abstract HttpTestRequestHeaders getRequestHeaders();
        public abstract HttpTestResponseHeaders getResponseHeaders();
        public abstract void sendResponseHeaders(int code, long contentLength) throws IOException;
        public abstract URI getRequestURI();
        public abstract String getRequestMethod();
        public abstract void close();
        public abstract InetSocketAddress getRemoteAddress();
        public abstract InetSocketAddress getLocalAddress();
        public abstract String getConnectionKey();
        public abstract SSLSession getSSLSession();
        public CompletableFuture<Long> sendPing() {
            throw new UnsupportedOperationException("sendPing not supported on "
                    + getExchangeVersion());
        }
        public void serverPush(URI uri, HttpHeaders reqHeaders, byte[] body) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            serverPush(uri, reqHeaders, bais);
        }
        public void serverPush(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, byte[] body) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            serverPush(uri, reqHeaders, rspHeaders, bais);
        }
        public void serverPush(URI uri, HttpHeaders reqHeaders, InputStream body)
                throws IOException {
            serverPush(uri, reqHeaders, HttpHeaders.of(Map.of(), (n,v) -> true), body);
        }

        public void serverPush(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream body)
                throws IOException {
            throw new UnsupportedOperationException("serverPush with " + getExchangeVersion());
        }

        public void requestStopSending(long errorCode) {
            throw new UnsupportedOperationException("sendHttp3ConnectionClose with " + getExchangeVersion());
        }

        /**
         * Sends an HTTP/3 PUSH_PROMISE frame, for the given {@code uri},
         * with the given request {@code reqHeaders}, and opens a push promise
         * stream to send the given response {@code rspHeaders} and {@code body}.
         *
         * @implSpec
         * The default implementation of this method throws {@link
         * UnsupportedOperationException}
         *
         * @param uri        the push promise URI
         * @param reqHeaders the push promise request headers
         * @param rspHeaders the push promise request headers
         * @param body       the push response body
         *
         * @return          the pushId used to push the promise
         *
         * @throws IOException if an error occurs
         * @throws UnsupportedOperationException if the exchange is not {@link
         *         #getExchangeVersion() HTTP_3}
         */
        public long http3ServerPush(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream body)
                throws IOException {
            throw new UnsupportedOperationException("serverPushWithId with " + getExchangeVersion());
        }
        /**
         * Sends an HTTP/3 PUSH_PROMISE frame, for the given {@code uri},
         * with the given request {@code headers}, and with the given
         * {@code pushId}. This method only sends the PUSH_PROMISE frame
         * and doesn't open any push stream.
         *
         * @apiNote
         * This method can be used to send a PUSH_PROMISE whose body has
         * already been promised by calling {@link
         * #http3ServerPush(URI, HttpHeaders, HttpHeaders, InputStream)}. In that case
         * the {@code pushId} returned by {@link
         * #http3ServerPush(URI, HttpHeaders, HttpHeaders, InputStream)} should be passed
         * as parameter. Otherwise, if {@code pushId=-1} is passed as parameter,
         * a new pushId will be allocated. The push response headers and body
         * can be later sent using {@link
         * #sendHttp3PushResponse(long, URI, HttpHeaders, HttpHeaders, InputStream)}.
         *
         * @implSpec
         * The default implementation of this method throws {@link
         * UnsupportedOperationException}
         *
         * @param pushId    the pushId to use, or {@code -1} if a new
         *                  pushId should be allocated.
         * @param uri       the push promise URI
         * @param headers   the push promise request headers
         * @return the given pushId, if positive, otherwise the new allocated pushId
         *
         * @throws IOException if an error occurs
         * @throws UnsupportedOperationException if the exchange is not {@link
         *         #getExchangeVersion() HTTP_3}
         */
        public long sendHttp3PushPromiseFrame(long pushId, URI uri, HttpHeaders headers)
            throws IOException {
            throw new UnsupportedOperationException("serverPushId with " + getExchangeVersion());
        }
        /**
         * Opens an HTTP/3 PUSH_STREAM to send a push promise response headers
         * and body.
         *
         * @apiNote
         * No check is performed on the provided pushId
         *
         * @param pushId a positive pushId obtained from {@link
         *               #sendHttp3PushPromiseFrame(long, URI, HttpHeaders)}
         * @param uri        the push request URI
         * @param reqHeaders the push promise request headers
         * @param rspHeaders the push promise response headers
         * @param body       the push response body
         *
         * @throws IOException if an error occurs
         * @throws UnsupportedOperationException if the exchange is not {@link
         */
        public void sendHttp3PushResponse(long pushId, URI uri,
                                          HttpHeaders reqHeaders,
                                          HttpHeaders rspHeaders,
                                          InputStream body)
            throws IOException {
            throw new UnsupportedOperationException("serverPushWithId with " + getExchangeVersion());
        }
        /**
         * Sends an HTTP/3 CANCEL_PUSH frame to cancel a push that has been
         * promised by either {@link #http3ServerPush(URI, HttpHeaders, HttpHeaders, InputStream)}
         * or {@link #sendHttp3PushPromiseFrame(long, URI, HttpHeaders)}.
         *
         * This method doesn't cancel the push stream but just sends
         * a CANCEL_PUSH frame.
         * Note that if the push stream has already been opened this
         * method may not have any effect.
         *
         * @apiNote
         * No check is performed on the provided pushId
         *
         * @implSpec
         * The default implementation of this method throws {@link
         * UnsupportedOperationException}
         *
         * @param pushId        the cancelled pushId
         *
         * @throws IOException  if an error occurs
         * @throws UnsupportedOperationException if the exchange is not {@link
         *         #getExchangeVersion() HTTP_3}
         */
        public void sendHttp3CancelPushFrame(long pushId)
            throws IOException {
            throw new UnsupportedOperationException("cancelPushId with " + getExchangeVersion());
        }
        /**
         * Waits until the given {@code pushId} is allowed by the HTTP/3 peer
         *
         * @implSpec
         * The default implementation of this method throws {@link
         * UnsupportedOperationException}
         *
         * @param pushId a pushId
         *
         * @return the maximum pushId allowed (exclusive)
         *
         * @throws UnsupportedOperationException if the exchange is not {@link
         *         #getExchangeVersion() HTTP_3}
         */
        public long waitForHttp3MaxPushId(long pushId)
                throws InterruptedException {
            throw new UnsupportedOperationException("waitForMaxPushId with " + getExchangeVersion());
        }
        public boolean serverPushAllowed() {
            return false;
        }
        public Encoder qpackEncoder() {
            throw new UnsupportedOperationException("qpackEncoder with " + getExchangeVersion());
        }
        public CompletableFuture<ConnectionSettings> clientHttp3Settings() {
            throw new UnsupportedOperationException("HTTP/3 client connection settings with "
                    + getExchangeVersion());
        }
        public static HttpTestExchange of(HttpExchange exchange) {
            return new Http1TestExchange(exchange);
        }
        public static HttpTestExchange of(Http2TestExchange exchange) {
            return new H2ExchangeImpl(exchange);
        }

        abstract void doFilter(Filter.Chain chain) throws IOException;

        public void resetStream(long code) throws IOException {
            throw new UnsupportedOperationException(String.valueOf(this.getServerVersion()));
        }

        // implementations...
        private static final class Http1TestExchange extends HttpTestExchange {
            private final HttpExchange exchange;
            Http1TestExchange(HttpExchange exch) {
                this.exchange = exch;
            }
            @Override
            public Version getServerVersion() { return HTTP_1_1; }
            @Override
            public Version getExchangeVersion() { return HTTP_1_1; }
            @Override
            public InputStream getRequestBody() {
                return exchange.getRequestBody();
            }
            @Override
            public OutputStream getResponseBody() {
                return exchange.getResponseBody();
            }
            @Override
            public HttpTestRequestHeaders getRequestHeaders() {
                return HttpTestRequestHeaders.of(exchange.getRequestHeaders());
            }
            @Override
            public HttpTestResponseHeaders getResponseHeaders() {
                return HttpTestResponseHeaders.of(exchange.getResponseHeaders());
            }
            @Override
            public void sendResponseHeaders(int code, long contentLength) throws IOException {
                if (contentLength == 0) contentLength = -1;
                else if (contentLength < 0) contentLength = 0;
                exchange.sendResponseHeaders(code, contentLength);
            }
            @Override
            void doFilter(Filter.Chain chain) throws IOException {
                chain.doFilter(exchange);
            }
            @Override
            public void close() { exchange.close(); }
            @Override
            public SSLSession getSSLSession() { return null; }
            @Override
            public InetSocketAddress getRemoteAddress() {
                return exchange.getRemoteAddress();
            }
            @Override
            public InetSocketAddress getLocalAddress() {
                return exchange.getLocalAddress();
            }
            @Override
            public URI getRequestURI() { return exchange.getRequestURI(); }
            @Override
            public String getRequestMethod() { return exchange.getRequestMethod(); }

            @Override
            public String getConnectionKey() {
                return exchange.getLocalAddress() + "->" + exchange.getRemoteAddress();
            }

            @Override
            public String toString() {
                return this.getClass().getSimpleName() + ": " + exchange.toString();
            }
        }

        private static final class H2ExchangeImpl extends HttpTestExchange {
            private final Http2TestExchange exchange;
            H2ExchangeImpl(Http2TestExchange exch) {
                this.exchange = exch;
            }
            @Override
            public Version getServerVersion() { return exchange.getServerVersion(); }
            @Override
            public Version getExchangeVersion() { return exchange.getServerVersion(); }
            @Override
            public InputStream getRequestBody() {
                return exchange.getRequestBody();
            }
            @Override
            public OutputStream getResponseBody() {
                return exchange.getResponseBody();
            }
            @Override
            public HttpTestRequestHeaders getRequestHeaders() {
                return HttpTestRequestHeaders.of(exchange.getRequestHeaders());
            }

            @Override
            public HttpTestResponseHeaders getResponseHeaders() {
                return HttpTestResponseHeaders.of(exchange.getResponseHeaders());
            }
            @Override
            public void sendResponseHeaders(int code, long contentLength) throws IOException {
                if (contentLength == 0) contentLength = -1;
                else if (contentLength < 0) contentLength = 0;
                exchange.sendResponseHeaders(code, contentLength);
            }
            @Override
            public boolean serverPushAllowed() {
                return exchange.serverPushAllowed();
            }
            @Override
            public void serverPush(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream body)
                throws IOException {
                exchange.serverPush(uri, reqHeaders, rspHeaders, body);
            }
            @Override
            public CompletableFuture<Long> sendPing() {
                return exchange.sendPing();
            }

            @Override
            public void requestStopSending(long errorCode) {
                exchange.requestStopSending(errorCode);
            }
            @Override
            public void resetStream(long code) throws IOException {
                exchange.resetStream(code);
            }

            @Override
            public long http3ServerPush(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream body) throws IOException {
                return exchange.serverPushWithId(uri, reqHeaders, rspHeaders, body);
            }
            @Override
            public long sendHttp3PushPromiseFrame(long pushId, URI uri, HttpHeaders reqHeaders) throws IOException {
               return exchange.sendPushId(pushId, uri, reqHeaders);
            }
            @Override
            public void sendHttp3CancelPushFrame(long pushId) throws IOException {
                exchange.cancelPushId(pushId);
            }
            @Override
            public void sendHttp3PushResponse(long pushId,
                                              URI uri,
                                              HttpHeaders reqHeaders,
                                              HttpHeaders rspHeaders,
                                              InputStream body) throws IOException {
                exchange.sendPushResponse(pushId, uri, reqHeaders, rspHeaders, body);
            }
            @Override
            public long waitForHttp3MaxPushId(long pushId) throws InterruptedException {
                return exchange.waitForMaxPushId(pushId);
            }
            @Override
            public Encoder qpackEncoder() {
                return exchange.qpackEncoder();
            }

            @Override
            public CompletableFuture<ConnectionSettings> clientHttp3Settings() {
                return exchange.clientHttp3Settings();
            }

            @Override
            void doFilter(Filter.Chain filter) throws IOException {
                throw new IOException("cannot use HTTP/1.1 filter with HTTP/2 server");
            }
            @Override
            public void close() { exchange.close();}
            @Override
            public SSLSession getSSLSession() { return exchange.getSSLSession();}
            @Override
            public InetSocketAddress getRemoteAddress() {
                return exchange.getRemoteAddress();
            }
            @Override
            public InetSocketAddress getLocalAddress() {
                return exchange.getLocalAddress();
            }

            @Override
            public String getConnectionKey() {
                return exchange.getConnectionKey();
            }

            @Override
            public URI getRequestURI() { return exchange.getRequestURI(); }
            @Override
            public String getRequestMethod() { return exchange.getRequestMethod(); }
            @Override
            public String toString() {
                return this.getClass().getSimpleName() + ": " + exchange.toString();
            }
        }

    }

    /**
     * An {@link HttpTestHandler} that handles only HEAD and GET
     * requests. If another method is used 405 is returned with
     * an empty body.
     * The response is always returned with fixed length.
     */
    public static class HttpHeadOrGetHandler implements HttpTestHandler {
        final String responseBody;
        public HttpHeadOrGetHandler() {
            this("pâté de tête persillé");
        }
        public HttpHeadOrGetHandler(String responseBody) {
            this.responseBody = Objects.requireNonNull(responseBody);
        }

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try (var exchg = t) {
                exchg.getRequestBody().readAllBytes();
                String method = exchg.getRequestMethod();
                switch (method) {
                    case "HEAD" -> {
                        byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
                        if (exchg.getExchangeVersion() != HTTP_1_1) {
                            // with HTTP/2 or HTTP/3 the server will not send content-length
                            exchg.getResponseHeaders()
                                    .addHeader("Content-Length", String.valueOf(resp.length));
                        }
                        exchg.sendResponseHeaders(200, resp.length);
                        exchg.getResponseBody().close();
                    }
                    case "GET" -> {
                        byte[] resp = responseBody.getBytes(StandardCharsets.UTF_8);
                        exchg.sendResponseHeaders(200, resp.length);
                        try (var os = exchg.getResponseBody()) {
                            os.write(resp);
                        }
                    }
                    default -> {
                        exchg.sendResponseHeaders(405, 0);
                        exchg.getResponseBody().close();
                    }
                }
            }
        }
    }


    /**
     * A version agnostic adapter class for HTTP Server Handlers.
     */
    public interface HttpTestHandler {
        void handle(HttpTestExchange t) throws IOException;

        default HttpHandler toHttpHandler() {
            return (t) -> doHandle(HttpTestExchange.of(t));
        }
        default Http2Handler toHttp2Handler() {
            return (t) -> doHandle(HttpTestExchange.of(t));
        }

        default void handleFailure(final HttpTestExchange exchange, Throwable failure) {
            System.out.println("WARNING: exception caught in HttpTestHandler::handle " + failure);
            System.err.println("WARNING: exception caught in HttpTestHandler::handle " + failure);
            if (PRINTSTACK && !expectException(exchange)) {
                failure.printStackTrace(System.out);
            }
        }

        private void doHandle(HttpTestExchange exchange) throws IOException {
            try {
                handle(exchange);
            } catch (Throwable failure) {
                handleFailure(exchange, failure);
                throw failure;
            }
        }
    }

    /**
     * An echo handler that can be used to transfer large amount of data, and
     * uses file on the file system to download the input. This handler honors
     * the {@code XFixed} header.
     */
    // TODO: it would be good if we could merge this with the Http2EchoHandler,
    //       from which this code was copied and adapted.
    public static class HttpTestFileEchoHandler implements HttpTestHandler {
        static final Path CWD = Paths.get(".");

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            try {
                System.err.printf("EchoHandler received request to %s from %s (version %s)%n",
                        t.getRequestURI(), t.getRemoteAddress(), t.getExchangeVersion());
                InputStream is = t.getRequestBody();
                var requestHeaders = t.getRequestHeaders();
                var responseHeaders = t.getResponseHeaders();
                responseHeaders.addHeader("X-Hello", "world");
                responseHeaders.addHeader("X-Bye", "universe");
                String fixedrequest = requestHeaders.firstValue("XFixed").orElse(null);
                File outfile = Files.createTempFile(CWD, "foo", "bar").toFile();
                //System.err.println ("QQQ = " + outfile.toString());
                FileOutputStream fos = new FileOutputStream(outfile);
                long count = is.transferTo(fos);
                System.err.printf("EchoHandler read %s bytes\n", count);
                is.close();
                fos.close();
                InputStream is1 = new FileInputStream(outfile);
                OutputStream os = null;

                Path check = requestHeaders.firstValue("X-Compare")
                        .map((String s) -> Path.of(s)).orElse(null);
                if (check != null) {
                    System.err.println("EchoHandler checking file match: " + check);
                    try {
                        assertFileContentsEqual(check, outfile.toPath());
                    } catch (Throwable x) {
                        System.err.println("Files do not match: " + x);
                        t.sendResponseHeaders(500, HttpTestExchange.RSPBODY_EMPTY);
                        outfile.delete();
                        os.close();
                        return;
                    }
                }

                // return the number of bytes received (no echo)
                String summary = requestHeaders.firstValue("XSummary").orElse(null);
                if (fixedrequest != null && summary == null) {
                    t.sendResponseHeaders(200, HttpTestExchange.fixedRsp(count));
                    os = t.getResponseBody();
                    if (!t.getRequestMethod().equals("HEAD")) {
                        long count1 = is1.transferTo(os);
                        System.err.printf("EchoHandler wrote %s bytes%n", count1);
                    } else {
                        System.err.printf("EchoHandler HEAD received, no bytes sent%n");
                    }
                } else {
                    t.sendResponseHeaders(200, HttpTestExchange.RSPBODY_CHUNKED);
                    os = t.getResponseBody();
                    if (!t.getRequestMethod().equals("HEAD")) {
                        long count1 = is1.transferTo(os);
                        System.err.printf("EchoHandler wrote %s bytes\n", count1);

                        if (summary != null) {
                            String s = Long.toString(count);
                            os.write(s.getBytes());
                        }
                    } else {
                        System.err.printf("EchoHandler HEAD received, no bytes sent%n");
                    }
                }
                outfile.delete();
                os.close();
                is1.close();
            } catch (Throwable e) {
                e.printStackTrace();
                throw new IOException(e);
            }
        }
    }

    /**
     * An echo handler that can be used to transfer small amounts of data.
     * All the request data is read in memory in a single byte array before
     * being sent back. If the handler is {@linkplain #useXFixed() configured
     * to honor the {@code XFixed} header}, and the request headers do not
     * specify {@code XFixed: yes}, the data is sent back in chunk mode.
     * Otherwise, chunked mode is not used (this is the default).
     */
    public static class HttpTestEchoHandler implements HttpTestHandler {

        private final boolean printBytes;
        public HttpTestEchoHandler() {
            this(true);
        }

        public HttpTestEchoHandler(boolean printBytes) {
            this.printBytes = printBytes;
        }

        /**
         * {@return whether the {@code XFixed} header should be
         * honored. If this method returns false, chunked mode will
         * not be used. If this method returns true, chunked mode
         * will be used unless the request headers contain
         * {@code XFixed: yes}}
         */
        protected boolean useXFixed() {
            return false;
        }

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            System.err.printf("EchoHandler received request to %s from %s (version %s)%n",
                    t.getRequestURI(), t.getRemoteAddress(), t.getExchangeVersion());
            InputStream is = null;
            OutputStream os = null;
            try {
                is = t.getRequestBody();
                os = t.getResponseBody();
                byte[] bytes = is.readAllBytes();
                if (printBytes) {
                    printBytes(System.out, "Echo server got "
                            + t.getExchangeVersion() + " bytes: ", bytes);
                }
                if (t.getRequestHeaders().firstValue("Content-type").isPresent()) {
                    t.getResponseHeaders().addHeader("Content-type",
                            t.getRequestHeaders().firstValue("Content-type").get());
                }

                long responseLength = useXFixed()
                        ? t.responseLength(bytes.length)
                        : HttpTestExchange.fixedRsp(bytes.length);
                t.sendResponseHeaders(200, responseLength);
                if (!t.getRequestMethod().equals("HEAD") && bytes.length > 0) {
                    os.write(bytes);
                }
            } finally {
                if (os != null) close(t, os);
                if (is != null) close(t, is);
            }
        }
        protected void close(OutputStream os) throws IOException {
            os.close();
        }
        protected void close(InputStream is) throws IOException {
            is.close();
        }
        protected void close(HttpTestExchange t, OutputStream os) throws IOException {
            close(os);
        }
        protected void close(HttpTestExchange t, InputStream is) throws IOException {
            close(is);
        }
    }

    public static class HttpTestRedirectHandler implements HttpTestHandler {

        final Supplier<String> supplier;

        public HttpTestRedirectHandler(Supplier<String> redirectSupplier) {
            supplier = redirectSupplier;
        }

        @Override
        public void handle(HttpTestExchange t) throws IOException {
            examineExchange(t);
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
                String location = supplier.get();
                System.err.printf("RedirectHandler request to %s from %s\n",
                        t.getRequestURI().toString(), t.getRemoteAddress().toString());
                System.err.println("Redirecting to: " + location);
                var headersBuilder = t.getResponseHeaders();
                headersBuilder.addHeader("Location", location);
                byte[] bb = getResponseBytes();
                t.sendResponseHeaders(redirectCode(), bb.length);
                OutputStream os = t.getResponseBody();
                os.write(bb);
                os.close();
                t.close();
            }
        }

        protected byte[] getResponseBytes() {
            return new byte[1024];
        }

        protected int redirectCode() {
            return 301;
        }

        // override in sub-class to examine the exchange, but don't
        // alter transaction state by reading the request body etc.
        protected void examineExchange(HttpTestExchange t) {
        }
    }

    /**
     * A simple FileServerHandler that understand "XFixed" header.
     * If the request headers contain {@code XFixed: yes}, a fixed
     * length response is sent. Otherwise, the response will be
     * chunked. Note that for directories the response is always
     * chunked.
     */
    class HttpTestFileServerHandler implements HttpTestHandler {

        String docroot;

        public HttpTestFileServerHandler(String docroot) {
            this.docroot = docroot;
        }

        public void handle(HttpTestExchange t)
                throws IOException
        {
            InputStream is = t.getRequestBody();
            var rspHeaders = t.getResponseHeaders();
            URI uri = t.getRequestURI();
            String path = uri.getPath();

            int x = 0;
            while (is.read() != -1) x++;
            is.close();
            File f = new File(docroot, path);
            if (!f.exists()) {
                notfound(t, path);
                return;
            }

            String method = t.getRequestMethod();
            if (method.equals("HEAD")) {
                rspHeaders.addHeader("Content-Length", Long.toString(f.length()));
                t.sendResponseHeaders(200, HttpTestExchange.RSPBODY_EMPTY);
                t.close();
            } else if (!method.equals("GET")) {
                t.sendResponseHeaders(405, HttpTestExchange.RSPBODY_EMPTY);
                t.close();
                return;
            }

            if (path.endsWith(".html") || path.endsWith(".htm")) {
                rspHeaders.addHeader("Content-Type", "text/html");
            } else {
                rspHeaders.addHeader("Content-Type", "text/plain");
            }
            if (f.isDirectory()) {
                if (!path.endsWith("/")) {
                    moved (t);
                    return;
                }
                rspHeaders.addHeader("Content-Type", "text/html");
                t.sendResponseHeaders(200, HttpTestExchange.RSPBODY_CHUNKED);
                String[] list = f.list();
                try (final OutputStream os = t.getResponseBody();
                     final PrintStream p = new PrintStream(os)) {
                    p.println("<h2>Directory listing for: " + path + "</h2>");
                    p.println("<ul>");
                    for (int i = 0; i < list.length; i++) {
                        p.println("<li><a href=\"" + list[i] + "\">" + list[i] + "</a></li>");
                    }
                    p.println("</ul><p><hr>");
                    p.flush();
                }
            } else {
                long clen = f.length();
                t.sendResponseHeaders(200, t.responseLength(clen));
                long count = 0;
                try (final OutputStream os = t.getResponseBody();
                     final FileInputStream fis = new FileInputStream (f)) {
                    byte[] buf = new byte [16 * 1024];
                    int len;
                    while ((len=fis.read(buf)) != -1) {
                        os.write(buf, 0, len);
                        count += len;
                    }
                    if (clen != count) {
                        System.err.println("FileServerHandler: WARNING: count of bytes sent does not match content-length");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        void moved(HttpTestExchange t) throws IOException {
            var req = t.getRequestHeaders();
            var rsp = t.getResponseHeaders();
            URI uri = t.getRequestURI();
            String host = req.firstValue("Host").get();
            String location = "http://"+host+uri.getPath() + "/";
            rsp.addHeader("Content-Type", "text/html");
            rsp.addHeader("Location", location);
            t.sendResponseHeaders(301, HttpTestExchange.RSPBODY_EMPTY);
            t.close();
        }

        void notfound(HttpTestExchange t, String p) throws IOException {
            t.getResponseHeaders().addHeader("Content-Type", "text/html");
            t.sendResponseHeaders(404, HttpTestExchange.RSPBODY_CHUNKED);
            OutputStream os = t.getResponseBody();
            String s = "<h2>File not found</h2>";
            s = s + p + "<p>";
            os.write(s.getBytes());
            os.close();
            t.close();
        }
    }

    public static boolean expectException(HttpTestExchange e) {
        HttpTestRequestHeaders h = e.getRequestHeaders();
        Optional<String> expectException = h.firstValue("X-expect-exception");
        if (expectException.isPresent()) {
            return expectException.get().equalsIgnoreCase("true");
        }
        return false;
    }

    /**
     * A version agnostic adapter class for HTTP Server Filter Chains.
     */
    public abstract class HttpChain {

        public abstract void doFilter(HttpTestExchange exchange) throws IOException;
        public static HttpChain of(Filter.Chain chain) {
            return new Http1Chain(chain);
        }

        public static HttpChain of(List<HttpTestFilter> filters, HttpTestHandler handler) {
            return new Http2Chain(filters, handler);
        }

        private static class Http1Chain extends HttpChain {
            final Filter.Chain chain;
            Http1Chain(Filter.Chain chain) {
                this.chain = chain;
            }
            @Override
            public void doFilter(HttpTestExchange exchange) throws IOException {
                try {
                    exchange.doFilter(chain);
                } catch (Throwable t) {
                    System.out.println("WARNING: exception caught in Http1Chain::doFilter " + t);
                    System.err.println("WARNING: exception caught in Http1Chain::doFilter " + t);
                    if (PRINTSTACK && !expectException(exchange)) t.printStackTrace(System.out);
                    throw t;
                }
            }
        }

        private static class Http2Chain extends HttpChain {
            ListIterator<HttpTestFilter> iter;
            HttpTestHandler handler;
            Http2Chain(List<HttpTestFilter> filters, HttpTestHandler handler) {
                this.iter = filters.listIterator();
                this.handler = handler;
            }
            @Override
            public void doFilter(HttpTestExchange exchange) throws IOException {
                try {
                    if (iter.hasNext()) {
                        iter.next().doFilter(exchange, this);
                    } else {
                        handler.handle(exchange);
                    }
                } catch (Throwable t) {
                    System.out.println("WARNING: exception caught in Http2Chain::doFilter " + t);
                    System.err.println("WARNING: exception caught in Http2Chain::doFilter " + t);
                    if (PRINTSTACK && !expectException(exchange)) t.printStackTrace(System.out);
                    throw t;
                }
            }
        }

    }

    /**
     * A version agnostic adapter class for HTTP Server Filters.
     */
    public abstract class HttpTestFilter {

        public abstract String description();

        public abstract void doFilter(HttpTestExchange exchange, HttpChain chain) throws IOException;

        public Filter toFilter() {
            return new Filter() {
                @Override
                public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
                    HttpTestFilter.this.doFilter(HttpTestExchange.of(exchange), HttpChain.of(chain));
                }
                @Override
                public String description() {
                    return HttpTestFilter.this.description();
                }
            };
        }
    }

    static String toString(HttpTestRequestHeaders headers) {
        return headers.entrySet().stream()
                .map((e) -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    abstract static class AbstractHttpAuthFilter extends HttpTestFilter {

        public static final int HTTP_PROXY_AUTH = 407;
        public static final int HTTP_UNAUTHORIZED = 401;
        public enum HttpAuthMode {PROXY, SERVER}
        final HttpAuthMode authType;
        final String type;

        public AbstractHttpAuthFilter(HttpAuthMode authType, String type) {
            this.authType = authType;
            this.type = type;
        }

        public final String type() {
            return type;
        }

        protected String getLocation() {
            return "Location";
        }
        protected String getKeepAlive() {
            return "keep-alive";
        }
        protected String getConnection() {
            return authType == HttpAuthMode.PROXY ? "Proxy-Connection" : "Connection";
        }

        protected String getAuthenticate() {
            return authType == HttpAuthMode.PROXY
                    ? "Proxy-Authenticate" : "WWW-Authenticate";
        }
        protected String getAuthorization() {
            return authType == HttpAuthMode.PROXY
                    ? "Proxy-Authorization" : "Authorization";
        }
        protected int getUnauthorizedCode() {
            return authType == HttpAuthMode.PROXY
                    ? HTTP_PROXY_AUTH
                    : HTTP_UNAUTHORIZED;
        }
        protected abstract boolean isAuthentified(HttpTestExchange he) throws IOException;
        protected abstract void requestAuthentication(HttpTestExchange he) throws IOException;
        protected void accept(HttpTestExchange he, HttpChain chain) throws IOException {
            chain.doFilter(he);
        }

        @Override
        public void doFilter(HttpTestExchange he, HttpChain chain) throws IOException {
            try {
                System.out.println(type + ": Got " + he.getRequestMethod()
                        + ": " + he.getRequestURI()
                        + "\n" + HttpServerAdapters.toString(he.getRequestHeaders()));

                // Assert only a single value for Expect. Not directly related
                // to digest authentication, but verifies good client behaviour.
                List<String> expectValues = he.getRequestHeaders().get("Expect");
                if (expectValues != null && expectValues.size() > 1) {
                    throw new IOException("Expect:  " + expectValues);
                }

                if (!isAuthentified(he)) {
                    try {
                        requestAuthentication(he);
                        he.sendResponseHeaders(getUnauthorizedCode(), -1);
                        System.out.println(type
                                + ": Sent back " + getUnauthorizedCode());
                    } finally {
                        he.close();
                    }
                } else {
                    accept(he, chain);
                }
            } catch (RuntimeException | Error | IOException t) {
                System.err.println(type
                        + ": Unexpected exception while handling request: " + t);
                t.printStackTrace(System.err);
                he.close();
                throw t;
            }
        }

    }

    public static class HttpBasicAuthFilter extends AbstractHttpAuthFilter {

            static String type(HttpAuthMode authType) {
                String type = authType == HttpAuthMode.SERVER
                        ? "BasicAuth Server Filter" : "BasicAuth Proxy Filter";
                return "["+type+"]";
            }

            final BasicAuthenticator auth;
            public HttpBasicAuthFilter(BasicAuthenticator auth) {
                this(auth, HttpAuthMode.SERVER);
            }

            public HttpBasicAuthFilter(BasicAuthenticator auth, HttpAuthMode authType) {
                this(auth, authType, type(authType));
            }

            public HttpBasicAuthFilter(BasicAuthenticator auth, HttpAuthMode authType, String typeDesc) {
                super(authType, typeDesc);
                this.auth = auth;
            }

            protected String getAuthValue() {
                return "Basic realm=\"" + auth.getRealm() + "\"";
            }

            @Override
            protected void requestAuthentication(HttpTestExchange he)
                    throws IOException
            {
                String headerName = getAuthenticate();
                String headerValue = getAuthValue();
                he.getResponseHeaders().addHeader(headerName, headerValue);
                System.out.println(type + ": Requesting Basic Authentication, "
                        + headerName + " : "+ headerValue);
            }

            @Override
            protected boolean isAuthentified(HttpTestExchange he) {
                if (he.getRequestHeaders().containsKey(getAuthorization())) {
                    List<String> authorization =
                            he.getRequestHeaders().get(getAuthorization());
                    for (String a : authorization) {
                        System.out.println(type + ": processing " + a);
                        int sp = a.indexOf(' ');
                        if (sp < 0) return false;
                        String scheme = a.substring(0, sp);
                        if (!"Basic".equalsIgnoreCase(scheme)) {
                            System.out.println(type + ": Unsupported scheme '"
                                    + scheme +"'");
                            return false;
                        }
                        if (a.length() <= sp+1) {
                            System.out.println(type + ": value too short for '"
                                    + scheme +"'");
                            return false;
                        }
                        a = a.substring(sp+1);
                        return validate(a);
                    }
                    return false;
                }
                return false;
            }

            boolean validate(String a) {
                byte[] b = Base64.getDecoder().decode(a);
                String userpass = new String (b);
                int colon = userpass.indexOf (':');
                String uname = userpass.substring (0, colon);
                String pass = userpass.substring (colon+1);
                return auth.checkCredentials(uname, pass);
            }

        @Override
        public String description() {
            return "HttpBasicAuthFilter";
        }
    }

    /**
     * A version agnostic adapter class for HTTP Server Context.
     */
    public static abstract class HttpTestContext {
        public abstract String getPath();
        public abstract void addFilter(HttpTestFilter filter);
        public abstract Version getVersion();

        // will throw UOE if the server is HTTP/2 or Authenticator is not a BasicAuthenticator
        public abstract void setAuthenticator(com.sun.net.httpserver.Authenticator authenticator);
    }

    /**
     * A version agnostic adapter class for HTTP Servers.
     */
    abstract class HttpTestServer implements AutoCloseable {
        private static final class ServerLogging {
            private static final Logger logger = Logger.getLogger("com.sun.net.httpserver");
            static void enableLogging() {
                logger.setLevel(Level.FINE);
                Stream.of(Logger.getLogger("").getHandlers())
                        .forEach(h -> h.setLevel(Level.ALL));
            }
        }

        public abstract void start();
        public abstract void stop();
        public abstract HttpTestContext addHandler(HttpTestHandler handler, String root);
        public abstract InetSocketAddress getAddress();
        public abstract Version getVersion();

        /**
         * Adds a new handler and return its context.
         * @implSpec
         * This method just returns {@link #addHandler(HttpTestHandler, String)
         * addHandler(context, root)}
         * @apiNote
         * This is a convenience method to help migrate from
         * {@link HttpServer#createContext(String, HttpHandler)}.
         * @param root     The context root
         * @param handler  The handler to attach to the context
         * @return the context to which the new handler is attached
         */
        public HttpTestContext createContext(String root, HttpTestHandler handler) {
            return addHandler(handler, root);
        }

        /**
         * {@return the HTTP3 test server which is acting as an alt-service for this server,
         * if any}
         */
        public Optional<Http3TestServer> getH3AltService() {
            return Optional.empty();
        }

        /**
         * {@return true if any HTTP3 test server is acting as an alt-service for this server and the
         *         HTTP3 test server listens on the same host and port as this server.
         *         Returns false otherwise}
         */
        public boolean supportsH3DirectConnection() {
            return false;
        }

        public Http3DiscoveryMode h3DiscoveryConfig() {
            return null;
        }

        @Override
        public String toString() {
            var conf = Optional.<Object>ofNullable(h3DiscoveryConfig()).orElse(getVersion());
            return "HttpTestServer(%s: %s)".formatted(conf, serverAuthority());
        }

        /**
         * @param version the HTTP version
         * @param more additional HTTP versions
         * {@return  true if the handlers registered with this server can be accessed (through
         * request URIs) using all of the passed HTTP versions. Returns false otherwise}
         */
        public abstract boolean canHandle(Version version, Version... more);
        public abstract void setRequestApprover(final Predicate<String> approver);

        @Override
        public void close() throws Exception {
            stop();
        }

        public String serverAuthority() {
            InetSocketAddress address = getAddress();
            String hostString = address.getHostString();
            hostString = address.getAddress().isLoopbackAddress() || hostString.equals("localhost")
                    ? address.getAddress().getHostAddress() // use the raw IP address, if loopback
                    : hostString; // use whatever host string was used to construct the address
            hostString = hostString.contains(":")
                    ? "[" + hostString + "]"
                    : hostString;
            return hostString + ":" + address.getPort();
        }

        public static HttpTestServer of(final HttpServer server) {
            Objects.requireNonNull(server);
            return new Http1TestServer(server);
        }

        public static HttpTestServer of(final HttpServer server, ExecutorService executor) {
            Objects.requireNonNull(server);
            return new Http1TestServer(server, executor);
        }

        public static HttpTestServer of(final Http2TestServer server) {
            Objects.requireNonNull(server);
            return new Http2TestServerImpl(server);
        }

        public static HttpTestServer of(final Http3TestServer server) {
            Objects.requireNonNull(server);
            return new H3ServerAdapter(server);
        }

        /**
         * Creates a {@link HttpTestServer} which supports the {@code serverVersion}. The server
         * will only be available on {@code http} protocol. {@code https} will not be supported
         * by the returned server
         *
         * @param serverVersion The HTTP version of the server
         * @return The newly created server
         * @throws IllegalArgumentException if {@code serverVersion} is not supported by this method
         * @throws IOException if any exception occurs during the server creation
         */
        public static HttpTestServer create(Version serverVersion) throws IOException {
            Objects.requireNonNull(serverVersion);
            return create(serverVersion, null);
        }

        /**
         * Creates a {@link HttpTestServer} which supports the {@code serverVersion}. If the
         * {@code sslContext} is null, then only {@code http} protocol will be supported by the
         * server. Else, the server will be configured with the {@code sslContext} and will support
         * {@code https} protocol.
         *
         * @param serverVersion The HTTP version of the server
         * @param sslContext    The SSLContext to use. Can be null
         * @return The newly created server
         * @throws IllegalArgumentException if {@code serverVersion} is not supported by this method
         * @throws IOException if any exception occurs during the server creation
         */
        public static HttpTestServer create(Version serverVersion, SSLContext sslContext)
                throws IOException {
            Objects.requireNonNull(serverVersion);
            return create(serverVersion, sslContext, null, null);
        }

        /**
         * Creates a {@link HttpTestServer} which supports the {@code serverVersion}. If the
         * {@code sslContext} is null, then only {@code http} protocol will be supported by the
         * server. Else, the server will be configured with the {@code sslContext} and will support
         * {@code https} protocol.
         *
         * @param serverVersion The HTTP version of the server
         * @param sslContext    The SSLContext to use. Can be null
         * @param executor      The executor to be used by the server. Can be null
         * @return The newly created server
         * @throws IllegalArgumentException if {@code serverVersion} is not supported by this method
         * @throws IOException if any exception occurs during the server creation
         */
        public static HttpTestServer create(Version serverVersion, SSLContext sslContext,
                                            ExecutorService executor) throws IOException {
            Objects.requireNonNull(serverVersion);
            return create(serverVersion, sslContext, null, executor);
        }

        /**
         * Creates a {@link HttpTestServer} which supports HTTP_3 version.
         *
         * @param h3DiscoveryCfg Discovery config for HTTP_3 connection creation. Can be null
         * @param sslContext     SSLContext. Cannot be null
         * @return The newly created server
         * @throws IOException if any exception occurs during the server creation
         */
        public static HttpTestServer create(Http3DiscoveryMode h3DiscoveryCfg,
                                            SSLContext sslContext)
                throws IOException {
            Objects.requireNonNull(sslContext, "SSLContext");
            return create(h3DiscoveryCfg, sslContext, null);
        }

        /**
         * Creates a {@link HttpTestServer} which supports HTTP_3 version.
         *
         * @param h3DiscoveryCfg Discovery config for HTTP_3 connection creation. Can be null
         * @param sslContext     SSLContext. Cannot be null
         * @param executor       The executor to be used by the server. Can be null
         * @return The newly created server
         * @throws IOException if any exception occurs during the server creation
         */
        public static HttpTestServer create(Http3DiscoveryMode h3DiscoveryCfg,
                                            SSLContext sslContext, ExecutorService executor)
                throws IOException {
            Objects.requireNonNull(sslContext, "SSLContext");
            return create(HTTP_3, sslContext, h3DiscoveryCfg, executor);
        }


        /**
         * Creates a {@link HttpTestServer} which supports the {@code serverVersion}. If the
         * {@code sslContext} is null, then only {@code http} protocol will be supported by the
         * server. Else, the server will be configured with the {@code sslContext} and will support
         * {@code https} protocol.
         *
         * If {@code serverVersion} is {@link Version#HTTP_3 HTTP_3}, then a {@code h3DiscoveryCfg}
         * can be passed to decide how the HTTP_3 server will be created. The following table
         * summarizes how {@code h3DiscoveryCfg} is used:
         * <ul>
         *     <li>HTTP3_ONLY - A server which only supports HTTP_3 is created</li>
         *     <li>HTTP3_ALTSVC - A HTTP_2 server is created and a HTTP_3 server is created.
         *          The HTTP_2 server advertises the HTTP_3 server as an alternate service. When
         *          creating the HTTP_3 server, an ephemeral port is used and thus the alternate
         *          service will be advertised on a different port than the HTTP_2 server's port</li>
         *      <li>ANY - A HTTP_2 server is created and a HTTP_3 server is created.
         *          The HTTP_2 server advertises the HTTP_3 server as an alternate service. When
         *          creating the HTTP_3 server, the same port as that of the HTTP_2 server is used
         *          to bind the HTTP_3 server. If that bind attempt fails, then an ephemeral port
         *          is used to bind the HTTP_3 server</li>
         * </ul>
         *
         * @param serverVersion The HTTP version of the server
         * @param sslContext    The SSLContext to use. Can be null
         * @param h3DiscoveryCfg The Http3DiscoveryMode for HTTP_3 server. Can be null,
         *                       in which case it defaults to {@code ALT_SVC} for HTTP_3
         *                       server
         * @param executor      The executor to be used by the server. Can be null
         * @return The newly created server
         * @throws IllegalArgumentException if {@code serverVersion} is not supported by this method
         * @throws IllegalArgumentException if {@code h3DiscoveryCfg} is not null when
         *                                  {@code serverVersion} is not {@code HTTP_3}
         * @throws IOException              if any exception occurs during the server creation
         */
        private static HttpTestServer create(final Version serverVersion, final SSLContext sslContext,
                                            final Http3DiscoveryMode h3DiscoveryCfg,
                                            final ExecutorService executor) throws IOException {
            Objects.requireNonNull(serverVersion);
            if (h3DiscoveryCfg != null && serverVersion != HTTP_3) {
                // Http3DiscoveryMode is only supported when version of HTTP_3
                throw new IllegalArgumentException("Http3DiscoveryMode" +
                        " isn't allowed for " + serverVersion + " version");
            }
            switch (serverVersion) {
                case HTTP_3 -> {
                    if (sslContext == null) {
                        throw new IllegalArgumentException("SSLContext cannot be null when" +
                                " constructing a HTTP_3 server");
                    }
                    final Http3DiscoveryMode effectiveDiscoveryCfg = h3DiscoveryCfg == null
                            ? Http3DiscoveryMode.ALT_SVC
                            : h3DiscoveryCfg;
                    switch (effectiveDiscoveryCfg) {
                        case HTTP_3_URI_ONLY -> {
                            // create only a HTTP3 server
                            return HttpTestServer.of(new Http3TestServer(sslContext, executor));
                        }
                        case ALT_SVC -> {
                            // create a HTTP2 server which advertises an HTTP3 alternate service.
                            // that alternate service will be using an ephemeral port for the server
                            final Http2TestServer h2WithAltService;
                            try {
                                h2WithAltService = new Http2TestServer(
                                        "localhost", true, 0, executor, sslContext)
                                        .enableH3AltServiceOnEphemeralPort();
                            } catch (Exception e) {
                                throw new IOException(e);
                            }
                            return HttpTestServer.of(h2WithAltService);
                        }
                        case ANY -> {
                            // create a HTTP2 server which advertises an HTTP3 alternate service.
                            // that alternate service will first attempt to use the same port as the
                            // HTTP2 server and if binding to that port fails, then will attempt
                            // to use a ephemeral port.
                            final Http2TestServer h2WithAltService;
                            try {
                                h2WithAltService = new Http2TestServer(
                                        "localhost", true, 0, executor, sslContext)
                                        .enableH3AltServiceOnSamePort();
                            } catch (Exception e) {
                                throw new IOException(e);
                            }
                            return HttpTestServer.of(h2WithAltService);
                        }
                        default -> throw new IllegalArgumentException("Unsupported" +
                                " Http3DiscoveryMode: " + effectiveDiscoveryCfg);
                    }
                }
                case HTTP_2 -> {
                    Http2TestServer underlying;
                    try {
                        underlying = sslContext == null
                                ? new Http2TestServer("localhost", false, 0, executor, null) // HTTP
                                : new Http2TestServer("localhost", true, 0, executor, sslContext); // HTTPS
                    } catch (IOException ioe) {
                        throw ioe;
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                    return HttpTestServer.of(underlying);
                }
                case HTTP_1_1 -> {
                    InetAddress loopback = InetAddress.getLoopbackAddress();
                    InetSocketAddress sa = new InetSocketAddress(loopback, 0);
                    HttpServer underlying;
                    if (sslContext == null) {
                        underlying = HttpServer.create(sa, 0); // HTTP
                    } else {
                        HttpsServer https = HttpsServer.create(sa, 0); // HTTPS
                        https.setHttpsConfigurator(new TestServerConfigurator(loopback, sslContext));
                        underlying = https;
                    }
                    if (executor != null) {
                        underlying.setExecutor(executor);
                    }
                    return HttpTestServer.of(underlying);
                }
                default -> throw new IllegalArgumentException("Unsupported HTTP version "
                        + serverVersion);
            }
        }

        private static class Http1TestServer extends  HttpTestServer {
            private final HttpServer impl;
            private final ExecutorService executor;
            Http1TestServer(HttpServer server) {
                this(server, null);
            }
            Http1TestServer(HttpServer server, ExecutorService executor) {
                if (executor != null) server.setExecutor(executor);
                this.executor = executor;
                this.impl = server;
            }
            @Override
            public void start() {
                System.out.println("Http1TestServer: start");
                impl.start();
            }
            @Override
            public void stop() {
                System.out.println("Http1TestServer: stop");
                try {
                    impl.stop(0);
                } finally {
                    if (executor != null) {
                        executor.shutdownNow();
                    }
                }
            }
            @Override
            public HttpTestContext addHandler(HttpTestHandler handler, String path) {
                System.out.println("Http1TestServer[" + getAddress()
                        + "]::addHandler " + handler + ", " + path);
                return new Http1TestContext(impl.createContext(path, handler.toHttpHandler()));
            }
            @Override
            public InetSocketAddress getAddress() {
                return new InetSocketAddress(InetAddress.getLoopbackAddress(),
                        impl.getAddress().getPort());
            }
            @Override
            public Version getVersion() { return HTTP_1_1; }

            @Override
            public boolean canHandle(final Version version, final Version... more) {
                if (version != HTTP_1_1) {
                    return false;
                }
                for (var v : more) {
                    if (v != HTTP_1_1) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void setRequestApprover(final Predicate<String> approver) {
                throw new UnsupportedOperationException("not supported");
            }
        }

        private static class Http1TestContext extends HttpTestContext {
            private final HttpContext context;
            Http1TestContext(HttpContext ctxt) {
                this.context = ctxt;
            }
            @Override public String getPath() {
                return context.getPath();
            }
            @Override
            public void addFilter(HttpTestFilter filter) {
                System.out.println("Http1TestContext::addFilter " + filter.description());
                context.getFilters().add(filter.toFilter());
            }
            @Override
            public void setAuthenticator(com.sun.net.httpserver.Authenticator authenticator) {
                context.setAuthenticator(authenticator);
            }
            @Override public Version getVersion() { return HTTP_1_1; }
        }

        private static class Http2TestServerImpl extends  HttpTestServer {
            private final Http2TestServer impl;
            Http2TestServerImpl(Http2TestServer server) {
                this.impl = server;
            }
            @Override
            public void start() {
                System.out.println("Http2TestServerImpl: start");
                impl.start();
            }
            @Override
            public void stop() {
                System.out.println("Http2TestServerImpl: stop");
                impl.stop();
            }

            @Override
            public void close() throws Exception {
                System.out.println("Http2TestServerImpl: close");
                impl.close();
            }

            @Override
            public HttpTestContext addHandler(HttpTestHandler handler, String path) {
                System.out.println("Http2TestServerImpl[" + getAddress()
                                   + "]::addHandler " + handler + ", " + path);
                Http2TestContext context = new Http2TestContext(handler, path);
                impl.addHandler(context.toHttp2Handler(), path);
                return context;
            }
            @Override
            public InetSocketAddress getAddress() {
                return new InetSocketAddress(InetAddress.getLoopbackAddress(),
                        impl.getAddress().getPort());
            }

            @Override
            public Optional<Http3TestServer> getH3AltService() {
                return impl.getH3AltService();
            }

            @Override
            public boolean supportsH3DirectConnection() {
                return impl.supportsH3DirectConnection();
            }

            public Http3DiscoveryMode h3DiscoveryConfig() {
                return supportsH3DirectConnection()
                        ? Http3DiscoveryMode.ANY
                        : Http3DiscoveryMode.ALT_SVC;
            }

            public Version getVersion() { return HTTP_2; }

            @Override
            public boolean canHandle(final Version version, final Version... more) {
                final Set<Version> supported = new HashSet<>();
                supported.add(HTTP_2);
                impl.getH3AltService().ifPresent((unused)->  supported.add(HTTP_3));
                if (!supported.contains(version)) {
                    return false;
                }
                for (var v : more) {
                    if (!supported.contains(v)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void setRequestApprover(final Predicate<String> approver) {
                this.impl.setRequestApprover(approver);
            }
        }

        private static class Http2TestContext
                extends HttpTestContext implements HttpTestHandler {
            private final HttpTestHandler handler;
            private final String path;
            private final List<HttpTestFilter> filters = new CopyOnWriteArrayList<>();
            Http2TestContext(HttpTestHandler hdl, String path) {
                this.handler = hdl;
                this.path = path;
            }
            @Override
            public String getPath() { return path; }
            @Override
            public void addFilter(HttpTestFilter filter) {
                System.out.println("Http2TestContext::addFilter " + filter.description());
                filters.add(filter);
            }
            @Override
            public void handle(HttpTestExchange exchange) throws IOException {
                System.out.println("Http2TestContext::handle " + exchange);
                HttpChain.of(filters, handler).doFilter(exchange);
            }
            @Override
            public void setAuthenticator(final Authenticator authenticator) {
                if (authenticator instanceof BasicAuthenticator basicAuth) {
                    addFilter(new HttpBasicAuthFilter(basicAuth));
                } else {
                    throw new UnsupportedOperationException(
                            "only BasicAuthenticator is supported on HTTP/2 context");
                }
            }
            @Override public Version getVersion() { return HTTP_2; }
        }

        private static final class H3ServerAdapter extends HttpTestServer {
            private final Http3TestServer underlyingH3Server;

            private H3ServerAdapter(final Http3TestServer server) {
                this.underlyingH3Server = server;
            }

            @Override
            public void start() {
                underlyingH3Server.start();
            }

            @Override
            public void stop() {
                underlyingH3Server.stop();
            }

            @Override
            public HttpTestContext addHandler(final HttpTestHandler handler, final String path) {
                Objects.requireNonNull(path);
                Objects.requireNonNull(handler);
                final H3RootCtx h3Ctx = new H3RootCtx(path, handler);
                this.underlyingH3Server.addHandler(path, h3Ctx::doHandle);
                return h3Ctx;
            }

            @Override
            public InetSocketAddress getAddress() {
                return underlyingH3Server.getAddress();
            }

            @Override
            public Version getVersion() {
                return HTTP_3;
            }

            @Override
            public Http3DiscoveryMode h3DiscoveryConfig() {
                return Http3DiscoveryMode.HTTP_3_URI_ONLY;
            }

            @Override
            public boolean canHandle(Version version, Version... more) {
                if (version != HTTP_3) {
                    return false;
                }
                for (var v : more) {
                    if (v != HTTP_3) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void setRequestApprover(final Predicate<String> approver) {
                underlyingH3Server.setRequestApprover(approver);
            }

        }

        private static final class H3RootCtx extends HttpTestContext implements HttpTestHandler {
            private final String path;
            private final HttpTestHandler handler;
            private final List<HttpTestFilter> filters = new CopyOnWriteArrayList<>();

            private H3RootCtx(final String path, final HttpTestHandler handler) {
                this.path = path;
                this.handler = handler;
            }

            @Override
            public String getPath() {
                return this.path;
            }

            @Override
            public void addFilter(final HttpTestFilter filter) {
                Objects.requireNonNull(filter);
                this.filters.add(filter);
            }

            @Override
            public Version getVersion() {
                return HTTP_3;
            }

            @Override
            public void setAuthenticator(final Authenticator authenticator) {
                if (authenticator instanceof BasicAuthenticator basicAuth) {
                    addFilter(new HttpBasicAuthFilter(basicAuth));
                } else {
                    throw new UnsupportedOperationException(
                            "Only BasicAuthenticator is supported on an H3 context");
                }
            }

            @Override
            public void handle(final HttpTestExchange exchange) throws IOException {
                HttpChain.of(this.filters, this.handler).doFilter(exchange);
            }

            private void doHandle(final Http2TestExchange exchange) throws IOException {
                final HttpTestExchange adapted = HttpTestExchange.of(exchange);
                try {
                    H3RootCtx.this.handle(adapted);
                } catch (Throwable failure) {
                    handleFailure(adapted, failure);
                    throw failure;
                }
            }
        }
    }

    public static void enableServerLogging() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%4$s [%1$tb %1$td, %1$tl:%1$tM:%1$tS.%1$tN] %2$s: %5$s%6$s%n");
        HttpTestServer.ServerLogging.enableLogging();
    }

    public default HttpClient.Builder newClientBuilderForH3() {
        return createClientBuilderForH3();
    }

    /**
     * {@return a client builder suitable for interacting with the specified
     * version}
     * The builder's {@linkplain HttpClient.Builder#version(Version) version},
     * {@linkplain HttpClient.Builder#proxy(ProxySelector) proxy selector}
     * and {@linkplain HttpClient.Builder#sslContext(SSLContext) SSL context}
     * are not set.
     * @apiNote This method sets the {@linkplain HttpClient.Builder#localAddress(InetAddress)
     * bind address} to the {@linkplain InetAddress#getLoopbackAddress() loopback address}
     * if version is HTTP/3, the OS is Mac, and the OS version is 10.X, in order to
     * avoid conflicting with system allocated ephemeral UDP ports.
     * @param version the highest version the client is assumed to interact with.
     */
    public static HttpClient.Builder createClientBuilderFor(Version version) {
        var builder = HttpClient.newBuilder();
        return switch (version) {
            case HTTP_3 -> configureForH3(builder);
            default -> builder;
        };
    }

    /**
     * {@return a client builder suitable for interacting with HTTP/3}
     * The builder's {@linkplain HttpClient.Builder#version(Version) version},
     * {@linkplain HttpClient.Builder#proxy(ProxySelector) proxy selector}
     * and {@linkplain HttpClient.Builder#sslContext(SSLContext) SSL context}
     * are not set.
     * @apiNote This method sets the {@linkplain HttpClient.Builder#localAddress(InetAddress)
     * bind address} to the {@linkplain InetAddress#getLoopbackAddress() loopback address}
     * if version is HTTP/3, the OS is Mac, and the OS version is 10.X, in order to
     * avoid conflicting with system allocated ephemeral UDP ports.
     * @implSpec This is identical to calling {@link #createClientBuilderFor(Version)
     * newClientBuilderFor(Version.HTTP_3)} or {@link #configureForH3(Builder)
     * configureForH3(HttpClient.newBuilder())}
     */
    public static HttpClient.Builder createClientBuilderForH3() {
        return configureForH3(HttpClient.newBuilder());
    }

    /**
     * Configure a builder to be suitable for a client that may send requests
     * through HTTP/3.
     * The builder's {@linkplain HttpClient.Builder#version(Version) version},
     * {@linkplain HttpClient.Builder#proxy(ProxySelector) proxy selector}
     * and {@linkplain HttpClient.Builder#sslContext(SSLContext) SSL context}
     * are not set.
     * @apiNote This method sets the {@linkplain HttpClient.Builder#localAddress(InetAddress)
     * bind address} to the {@linkplain InetAddress#getLoopbackAddress() loopback address}
     * if the OS is Mac, and the OS version is 10.X, in order to
     * avoid conflicting with system allocated ephemeral UDP ports.
     * @return a client builder suitable for interacting with HTTP/3
     */
    public static HttpClient.Builder configureForH3(HttpClient.Builder builder) {
        if (TestUtil.sysPortsMayConflict()) {
            return builder.localAddress(InetAddress.getLoopbackAddress());
        }
        return builder;
    }

    public static InetAddress clientLocalBindAddress() {
        if (TestUtil.sysPortsMayConflict()) {
            return InetAddress.getLoopbackAddress();
        }
        return new InetSocketAddress(0).getAddress();
    }
}
