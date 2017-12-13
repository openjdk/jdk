/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http;

import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import jdk.incubator.http.internal.common.Log;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Utils;
import jdk.incubator.http.internal.frame.SettingsFrame;
import static jdk.incubator.http.internal.frame.SettingsFrame.INITIAL_WINDOW_SIZE;
import static jdk.incubator.http.internal.frame.SettingsFrame.ENABLE_PUSH;
import static jdk.incubator.http.internal.frame.SettingsFrame.HEADER_TABLE_SIZE;
import static jdk.incubator.http.internal.frame.SettingsFrame.MAX_CONCURRENT_STREAMS;
import static jdk.incubator.http.internal.frame.SettingsFrame.MAX_FRAME_SIZE;

/**
 *  Http2 specific aspects of HttpClientImpl
 */
class Http2ClientImpl {

    static final boolean DEBUG = Utils.DEBUG; // Revisit: temporary dev flag.
    final static System.Logger debug =
            Utils.getDebugLogger("Http2ClientImpl"::toString, DEBUG);

    private final HttpClientImpl client;

    Http2ClientImpl(HttpClientImpl client) {
        this.client = client;
    }

    /* Map key is "scheme:host:port" */
    private final Map<String,Http2Connection> connections = new ConcurrentHashMap<>();

    private final Set<String> opening = Collections.synchronizedSet(new HashSet<>());
    private final Map<String,Set<CompletableFuture<Http2Connection>>> waiting =
    Collections.synchronizedMap(new HashMap<>());

    private void addToWaiting(String key, CompletableFuture<Http2Connection> cf) {
        synchronized (waiting) {
            Set<CompletableFuture<Http2Connection>> waiters = waiting.get(key);
            if (waiters == null) {
                waiters = new HashSet<>();
                waiting.put(key, waiters);
            }
            waiters.add(cf);
        }
    }

    /**
     * If a https request then async waits until a connection is opened.
     * Returns null if the request is 'http' as a different (upgrade)
     * mechanism is used.
     *
     * Only one connection per destination is created. Blocks when opening
     * connection, or when waiting for connection to be opened.
     * First thread opens the connection and notifies the others when done.
     *
     * If the request is secure (https) then we open the connection here.
     * If not, then the more complicated upgrade from 1.1 to 2 happens (not here)
     * In latter case, when the Http2Connection is connected, putConnection() must
     * be called to store it.
     */
    CompletableFuture<Http2Connection> getConnectionFor(HttpRequestImpl req) {
        URI uri = req.uri();
        InetSocketAddress proxy = req.proxy();
        String key = Http2Connection.keyFor(uri, proxy);

        synchronized (opening) {
            Http2Connection connection = connections.get(key);
            if (connection != null) { // fast path if connection already exists
                return CompletableFuture.completedFuture(connection);
            }

            if (!req.secure()) {
                return MinimalFuture.completedFuture(null);
            }

            if (!opening.contains(key)) {
                debug.log(Level.DEBUG, "Opening: %s", key);
                opening.add(key);
            } else {
                CompletableFuture<Http2Connection> cf = new MinimalFuture<>();
                addToWaiting(key, cf);
                return cf;
            }
        }
        return Http2Connection
                .createAsync(req, this)
                .whenComplete((conn, t) -> {
                    debug.log(Level.DEBUG,
                            "waking up dependents with created connection");
                    synchronized (opening) {
                        Set<CompletableFuture<Http2Connection>> waiters = waiting.remove(key);
                        debug.log(Level.DEBUG, "Opening completed: %s", key);
                        opening.remove(key);
                        if (t == null && conn != null)
                            putConnection(conn);
                        final Throwable cause = Utils.getCompletionCause(t);
                        if (waiters == null) {
                            debug.log(Level.DEBUG, "no dependent to wake up");
                            return;
                        } else if (cause instanceof Http2Connection.ALPNException) {
                            waiters.forEach((cf1) -> cf1.completeAsync(() -> null,
                                    client.theExecutor()));
                        } else if (cause != null) {
                            debug.log(Level.DEBUG,
                                    () -> "waking up dependants: failed: " + cause);
                            waiters.forEach((cf1) -> cf1.completeExceptionally(cause));
                        } else  {
                            debug.log(Level.DEBUG, "waking up dependants: succeeded");
                            waiters.forEach((cf1) -> cf1.completeAsync(() -> conn,
                                    client.theExecutor()));
                        }
                    }
                });
    }

    /*
     * TODO: If there isn't a connection to the same destination, then
     * store it. If there is already a connection, then close it
     */
    void putConnection(Http2Connection c) {
        connections.put(c.key(), c);
    }

    void deleteConnection(Http2Connection c) {
        connections.remove(c.key());
    }

    void stop() {
        debug.log(Level.DEBUG, "stopping");
        connections.values().forEach(this::close);
        connections.clear();
    }

    private void close(Http2Connection h2c) {
        try { h2c.close(); } catch (Throwable t) {}
    }

    HttpClientImpl client() {
        return client;
    }

    /** Returns the client settings as a base64 (url) encoded string */
    String getSettingsString() {
        SettingsFrame sf = getClientSettings();
        byte[] settings = sf.toByteArray(); // without the header
        Base64.Encoder encoder = Base64.getUrlEncoder()
                                       .withoutPadding();
        return encoder.encodeToString(settings);
    }

    private static final int K = 1024;

    private static int getParameter(String property, int min, int max, int defaultValue) {
        int value =  Utils.getIntegerNetProperty(property, defaultValue);
        // use default value if misconfigured
        if (value < min || value > max) {
            Log.logError("Property value for {0}={1} not in [{2}..{3}]: " +
                    "using default={4}", property, value, min, max, defaultValue);
            value = defaultValue;
        }
        return value;
    }

    // used for the connection window, to have a connection window size
    // bigger than the initial stream window size.
    int getConnectionWindowSize(SettingsFrame clientSettings) {
        // Maximum size is 2^31-1. Don't allow window size to be less
        // than the stream window size. HTTP/2 specify a default of 64 * K -1,
        // but we use 2^26 by default for better performance.
        int streamWindow = clientSettings.getParameter(INITIAL_WINDOW_SIZE);

        // The default is the max between the stream window size
        // and the connection window size.
        int defaultValue = Math.min(Integer.MAX_VALUE,
                Math.max(streamWindow, K*K*32));

        return getParameter(
                "jdk.httpclient.connectionWindowSize",
                streamWindow, Integer.MAX_VALUE, defaultValue);
    }

    SettingsFrame getClientSettings() {
        SettingsFrame frame = new SettingsFrame();
        // default defined for HTTP/2 is 4 K, we use 16 K.
        frame.setParameter(HEADER_TABLE_SIZE, getParameter(
                "jdk.httpclient.hpack.maxheadertablesize",
                0, Integer.MAX_VALUE, 16 * K));
        // O: does not accept push streams. 1: accepts push streams.
        frame.setParameter(ENABLE_PUSH, getParameter(
                "jdk.httpclient.enablepush",
                0, 1, 1));
        // HTTP/2 recommends to set the number of concurrent streams
        // no lower than 100. We use 100. 0 means no stream would be
        // accepted. That would render the client to be non functional,
        // so we won't let 0 be configured for our Http2ClientImpl.
        frame.setParameter(MAX_CONCURRENT_STREAMS, getParameter(
                "jdk.httpclient.maxstreams",
                1, Integer.MAX_VALUE, 100));
        // Maximum size is 2^31-1. Don't allow window size to be less
        // than the minimum frame size as this is likely to be a
        // configuration error. HTTP/2 specify a default of 64 * K -1,
        // but we use 16 M  for better performance.
        frame.setParameter(INITIAL_WINDOW_SIZE, getParameter(
                "jdk.httpclient.windowsize",
                16 * K, Integer.MAX_VALUE, 16*K*K));
        // HTTP/2 specify a minimum size of 16 K, a maximum size of 2^24-1,
        // and a default of 16 K. We use 16 K as default.
        frame.setParameter(MAX_FRAME_SIZE, getParameter(
                "jdk.httpclient.maxframesize",
                16 * K, 16 * K * K -1, 16 * K));
        return frame;
    }
}
