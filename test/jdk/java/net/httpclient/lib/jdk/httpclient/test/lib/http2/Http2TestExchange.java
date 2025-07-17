/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import javax.net.ssl.SSLSession;
import jdk.internal.net.http.common.HttpHeadersBuilder;
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

    void serverPush(URI uri, HttpHeaders headers, InputStream content);

    default void sendFrames(List<Http2Frame> frames) throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * Send a PING on this exchanges connection, and completes the returned CF
     * with the number of milliseconds it took to get a valid response.
     * It may also complete exceptionally
     */
    CompletableFuture<Long> sendPing();

    /**
     * {@return the identification of the connection on which this exchange is being
     * processed}
     */
    String getConnectionKey();
}
