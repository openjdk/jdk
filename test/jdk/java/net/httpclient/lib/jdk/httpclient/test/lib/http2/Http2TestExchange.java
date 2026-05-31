/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.httpclient.test.lib.http2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import javax.net.ssl.SSLSession;

import jdk.internal.net.http.common.HttpHeadersBuilder;
import jdk.internal.net.http.http3.ConnectionSettings;
import jdk.internal.net.http.qpack.Encoder;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.frame.Http2Frame;

public interface Http2TestExchange {

    HttpHeaders getRequestHeaders();

    HttpHeadersBuilder getResponseHeaders();

    URI getRequestURI();

    String getRequestMethod();

    SSLSession getSSLSession();

    void close();

    InputStream getRequestBody();

    OutputStream getResponseBody();

    void sendResponseHeaders(int rCode, long responseLength) throws IOException;

    default void sendResponseHeaders(int rCode, long responseLength,
                                     BiPredicate<CharSequence, CharSequence> insertionPolicy)
            throws IOException {
        sendResponseHeaders(rCode, responseLength);
    }

    InetSocketAddress getRemoteAddress();

    int getResponseCode();

    InetSocketAddress getLocalAddress();

    String getProtocol();

    boolean serverPushAllowed();

    default void serverPush(URI uri, HttpHeaders headers, InputStream content)
            throws IOException {
        serverPush(uri, headers, HttpHeaders.of(Map.of(), (n,v) -> true), content);
    }

    void serverPush(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream content)
            throws IOException;

    // For HTTP/3 only: send push promise + push stream,  returns pushId
    //

    /**
     * For HTTP/3 only: send push promise + push stream,  returns pushId.
     * The pushId can be promised again using {@link
     * #sendPushId(long, URI, HttpHeaders)}
     *
     * @implSpec
     * The default implementation of this method throws {@link
     * UnsupportedOperationException}
     *
     * @param uri        the push promise URI
     * @param reqHeaders the push promise request headers
     * @param rspHeaders the push promise response headers
     * @param content    the push response body
     *
     * @return          the pushId used to push the promise
     *
     * @throws IOException if an error occurs
     * @throws UnsupportedOperationException if the exchange is not {@link
     *         #getExchangeVersion() HTTP_3}
     */
    default long serverPushWithId(URI uri, HttpHeaders reqHeaders, HttpHeaders rspHeaders, InputStream content)
            throws IOException {
        throw new UnsupportedOperationException("serverPushWithId " + getExchangeVersion());
    }

    /**
     * For HTTP/3 only: only sends a push promise frame. If a positive
     * pushId is provided, uses the provided pushId and returns it.
     * Otherwise, a new pushId will be allocated and returned.
     * This allows to send an additional promise after {@linkplain
     * #serverPushWithId(URI, HttpHeaders, HttpHeaders, InputStream) sending the first},
     * or to send one or several push promise frames before {@linkplain
     * #sendPushResponse(long, URI, HttpHeaders, HttpHeaders, InputStream) sending
     * the response}.
     *
     * @implSpec
     * The default implementation of this method throws {@link
     * UnsupportedOperationException}
     *
     * @param pushId    the pushId to use, or {@code -1} if a new
     *                  pushId should be allocated.
     * @param uri       the push promise URI
     * @param headers   the push promise request headers
     *
     * @return the given pushId, if positive, otherwise the new allocated pushId
     *
     * @throws IOException if an error occurs
     * @throws UnsupportedOperationException if the exchange is not {@link
     *         #getExchangeVersion() HTTP_3}
     */
    default long sendPushId(long pushId, URI uri, HttpHeaders headers) throws IOException {
        throw new UnsupportedOperationException("sendPushId with " + getExchangeVersion());
    }

    /**
     * For HTTP/3 only: sends an HTTP/3 CANCEL_PUSH frame to cancel
     * a push that has been promised by either {@link
     * #serverPushWithId(URI, HttpHeaders, HttpHeaders, InputStream)} or {@link
     * #sendPushId(long, URI, HttpHeaders)}.
     *
     * This method just sends a CANCEL_PUSH frame.
     * Note that if the push stream has already been opened this
     * sending a CANCEL_PUSH frame may have no effect.
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
    default void cancelPushId(long pushId) throws IOException {
        throw new UnsupportedOperationException("cancelPush with " + getExchangeVersion());
    }

    /**
     * For HTTP/3 only: opens an HTTP/3 PUSH_STREAM to send a
     * push promise response headers and body.
     *
     * @apiNote
     * No check is performed on the provided pushId
     *
     * @param pushId a positive pushId obtained from {@link
     *               #sendPushId(long, URI, HttpHeaders)}
     * @param uri        the push request URI
     * @param reqHeaders the push promise request headers
     * @param rspHeaders the push promise response headers
     * @param content    the push response body
     *
     * @throws IOException if an error occurs
     * @throws UnsupportedOperationException if the exchange is not {@link
     */
    default void sendPushResponse(long pushId, URI uri,
                                  HttpHeaders reqHeaders,
                                  HttpHeaders rspHeaders,
                                  InputStream content)
            throws IOException {
        throw new UnsupportedOperationException("sendPushResponse with " + getExchangeVersion());
    }

    default void requestStopSending(long errorCode) {
        throw new UnsupportedOperationException("sendStopSendingFrame with " + getExchangeVersion());
    }

    default void sendFrames(List<Http2Frame> frames) throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * For HTTP/3 only: waits until the given {@code pushId} is allowed by
     * the HTTP/3 peer.
     *
     * @implSpec
     * The default implementation of this method returns the larger
     * possible variable length integer.
     *
     * @param pushId a pushId
     *
     * @return the upper bound pf the maximum pushId allowed (exclusive)
     *
     * @throws UnsupportedOperationException if the exchange is not {@link
     *         #getExchangeVersion() HTTP_3}
     */
    default long waitForMaxPushId(long pushId) throws InterruptedException {
        return VariableLengthEncoder.MAX_ENCODED_INTEGER;
    }

    default Encoder qpackEncoder() {
        throw new UnsupportedOperationException("QPack encoder not supported: " + getExchangeVersion());
    }

    default CompletableFuture<ConnectionSettings> clientHttp3Settings() {
        throw new UnsupportedOperationException("HTTP/3 client connection settings not supported: " + getExchangeVersion());
    }

    /**
     * Send a PING on this exchange connection, and completes the returned CF
     * with the number of milliseconds it took to get a valid response.
     * It may also complete exceptionally
     */
    CompletableFuture<Long> sendPing();

    default void close(IOException closed) throws IOException {
        close();
    }

    default Version getServerVersion() { return Version.HTTP_2; }

    default Version getExchangeVersion() { return Version.HTTP_2; }

    default void resetStream(long code) throws IOException {
        throw new UnsupportedOperationException("resetStream with " + getExchangeVersion());
    }

    /**
     * {@return the identification of the connection on which this exchange is being
     * processed}
     */
    String getConnectionKey();
}
