/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.net.http;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.lang.System.Logger.Level.TRACE;
import static java.net.http.WSUtils.logger;
import static java.net.http.WSUtils.webSocketSpecViolation;

final class WSOpeningHandshake {

    private static final String HEADER_CONNECTION = "Connection";
    private static final String HEADER_UPGRADE = "Upgrade";
    private static final String HEADER_ACCEPT = "Sec-WebSocket-Accept";
    private static final String HEADER_EXTENSIONS = "Sec-WebSocket-Extensions";
    private static final String HEADER_KEY = "Sec-WebSocket-Key";
    private static final String HEADER_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String HEADER_VERSION = "Sec-WebSocket-Version";
    private static final String VALUE_VERSION = "13"; // WebSocket's lucky number

    private static final SecureRandom srandom = new SecureRandom();

    private final MessageDigest sha1;

    {
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            // Shouldn't happen:
            // SHA-1 must be available in every Java platform implementation
            throw new InternalError("Minimum platform requirements are not met", e);
        }
    }

    private final HttpRequest request;
    private final Collection<String> subprotocols;
    private final String nonce;

    WSOpeningHandshake(WSBuilder b) {
        URI httpURI = createHttpUri(b.getUri());
        HttpRequest.Builder requestBuilder = b.getClient().request(httpURI);
        Duration connectTimeout = b.getConnectTimeout();
        if (connectTimeout != null) {
            requestBuilder.timeout(
                    TimeUnit.of(ChronoUnit.MILLIS),
                    connectTimeout.get(ChronoUnit.MILLIS)
            );
        }
        Collection<String> s = b.getSubprotocols();
        if (!s.isEmpty()) {
            String p = s.stream().collect(Collectors.joining(", "));
            requestBuilder.header(HEADER_PROTOCOL, p);
        }
        requestBuilder.header(HEADER_VERSION, VALUE_VERSION);
        this.nonce = createNonce();
        requestBuilder.header(HEADER_KEY, this.nonce);
        this.request = requestBuilder.GET();
        HttpRequestImpl r = (HttpRequestImpl) this.request;
        r.isWebSocket(true);
        r.setSystemHeader(HEADER_UPGRADE, "websocket");
        r.setSystemHeader(HEADER_CONNECTION, "Upgrade");
        this.subprotocols = s;
    }

    private URI createHttpUri(URI webSocketUri) {
        // FIXME: check permission for WebSocket URI and translate it into http/https permission
        logger.log(TRACE, "->createHttpUri(''{0}'')", webSocketUri);
        String httpScheme = webSocketUri.getScheme().equalsIgnoreCase("ws")
                ? "http"
                : "https";
        try {
            URI uri = new URI(httpScheme,
                    webSocketUri.getUserInfo(),
                    webSocketUri.getHost(),
                    webSocketUri.getPort(),
                    webSocketUri.getPath(),
                    webSocketUri.getQuery(),
                    null);
            logger.log(TRACE, "<-createHttpUri: ''{0}''", uri);
            return uri;
        } catch (URISyntaxException e) {
            // Shouldn't happen: URI invariant
            throw new InternalError("Error translating WebSocket URI to HTTP URI", e);
        }
    }

    CompletableFuture<Result> performAsync() {
        // The whole dancing with thenCompose instead of thenApply is because
        // WebSocketHandshakeException is a checked exception
        return request.responseAsync()
                .thenCompose(response -> {
                    try {
                        Result result = handleResponse(response);
                        return CompletableFuture.completedFuture(result);
                    } catch (WebSocketHandshakeException e) {
                        return CompletableFuture.failedFuture(e);
                    } catch (UncheckedIOException ee) {
                        return CompletableFuture.failedFuture(ee.getCause());
                    }
                });
    }

    private Result handleResponse(HttpResponse response) throws WebSocketHandshakeException {
        // By this point all redirects, authentications, etc. (if any) must have
        // been done by the httpClient used by the WebSocket; so only 101 is
        // expected
        int statusCode = response.statusCode();
        if (statusCode != 101) {
            String m = webSocketSpecViolation("1.3.",
                    "Unable to complete handshake; HTTP response status code "
                            + statusCode
            );
            throw new WebSocketHandshakeException(m, response);
        }
        HttpHeaders h = response.headers();
        checkHeader(h, response, HEADER_UPGRADE, v -> v.equalsIgnoreCase("websocket"));
        checkHeader(h, response, HEADER_CONNECTION, v -> v.equalsIgnoreCase("Upgrade"));
        checkVersion(response, h);
        checkAccept(response, h);
        checkExtensions(response, h);
        String subprotocol = checkAndReturnSubprotocol(response, h);
        RawChannel channel = null;
        try {
            channel = ((HttpResponseImpl) response).rawChannel();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Result(subprotocol, channel);
    }

    private void checkExtensions(HttpResponse response, HttpHeaders headers)
            throws WebSocketHandshakeException {
        List<String> ext = headers.allValues(HEADER_EXTENSIONS);
        if (!ext.isEmpty()) {
            String m = webSocketSpecViolation("4.1.",
                    "Server responded with extension(s) though none were requested "
                            + Arrays.toString(ext.toArray())
            );
            throw new WebSocketHandshakeException(m, response);
        }
    }

    private String checkAndReturnSubprotocol(HttpResponse response, HttpHeaders headers)
            throws WebSocketHandshakeException {
        assert response.statusCode() == 101 : response.statusCode();
        List<String> sp = headers.allValues(HEADER_PROTOCOL);
        int size = sp.size();
        if (size == 0) {
            // In this case the subprotocol requested (if any) by the client
            // doesn't matter. If there is no such header in the response, then
            // the server doesn't want to use any subprotocol
            return null;
        } else if (size > 1) {
            // We don't know anything about toString implementation of this
            // list, so let's create an array
            String m = webSocketSpecViolation("4.1.",
                    "Server responded with multiple subprotocols: "
                            + Arrays.toString(sp.toArray())
            );
            throw new WebSocketHandshakeException(m, response);
        } else {
            String selectedSubprotocol = sp.get(0);
            if (this.subprotocols.contains(selectedSubprotocol)) {
                return selectedSubprotocol;
            } else {
                String m = webSocketSpecViolation("4.1.",
                        format("Server responded with a subprotocol " +
                                        "not among those requested: '%s'",
                                selectedSubprotocol));
                throw new WebSocketHandshakeException(m, response);
            }
        }
    }

    private void checkAccept(HttpResponse response, HttpHeaders headers)
            throws WebSocketHandshakeException {
        assert response.statusCode() == 101 : response.statusCode();
        String x = nonce + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        sha1.update(x.getBytes(StandardCharsets.ISO_8859_1));
        String expected = Base64.getEncoder().encodeToString(sha1.digest());
        checkHeader(headers, response, HEADER_ACCEPT, actual -> actual.trim().equals(expected));
    }

    private void checkVersion(HttpResponse response, HttpHeaders headers)
            throws WebSocketHandshakeException {
        assert response.statusCode() == 101 : response.statusCode();
        List<String> versions = headers.allValues(HEADER_VERSION);
        if (versions.isEmpty()) { // That's normal and expected
            return;
        }
        String m = webSocketSpecViolation("4.4.",
                "Server responded with version(s) "
                        + Arrays.toString(versions.toArray()));
        throw new WebSocketHandshakeException(m, response);
    }

    //
    // Checks whether there's only one value for the header with the given name
    // and the value satisfies the predicate.
    //
    private static void checkHeader(HttpHeaders headers,
                                    HttpResponse response,
                                    String headerName,
                                    Predicate<? super String> valuePredicate)
            throws WebSocketHandshakeException {
        assert response.statusCode() == 101 : response.statusCode();
        List<String> values = headers.allValues(headerName);
        if (values.isEmpty()) {
            String m = webSocketSpecViolation("4.1.",
                    format("Server response field '%s' is missing", headerName)
            );
            throw new WebSocketHandshakeException(m, response);
        } else if (values.size() > 1) {
            String m = webSocketSpecViolation("4.1.",
                    format("Server response field '%s' has multiple values", headerName)
            );
            throw new WebSocketHandshakeException(m, response);
        }
        if (!valuePredicate.test(values.get(0))) {
            String m = webSocketSpecViolation("4.1.",
                    format("Server response field '%s' is incorrect", headerName)
            );
            throw new WebSocketHandshakeException(m, response);
        }
    }

    private static String createNonce() {
        byte[] bytes = new byte[16];
        srandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    static final class Result {

        final String subprotocol;
        final RawChannel channel;

        private Result(String subprotocol, RawChannel channel) {
            this.subprotocol = subprotocol;
            this.channel = channel;
        }
    }
}
