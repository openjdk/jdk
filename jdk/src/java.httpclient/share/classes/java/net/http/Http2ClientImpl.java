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
 */
package java.net.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import static java.net.http.SettingsFrame.INITIAL_WINDOW_SIZE;
import static java.net.http.SettingsFrame.ENABLE_PUSH;
import static java.net.http.SettingsFrame.HEADER_TABLE_SIZE;
import static java.net.http.SettingsFrame.MAX_CONCURRENT_STREAMS;
import static java.net.http.SettingsFrame.MAX_FRAME_SIZE;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *  Http2 specific aspects of HttpClientImpl
 */
class Http2ClientImpl {

    final private HttpClientImpl client;

    Http2ClientImpl(HttpClientImpl client) {
        this.client = client;
    }

    /* Map key is "scheme:host:port" */
    final private Map<String,Http2Connection> connections =
            Collections.synchronizedMap(new HashMap<>());

    final private Set<String> opening = Collections.synchronizedSet(new HashSet<>());

    synchronized boolean haveConnectionFor(URI uri, InetSocketAddress proxy) {
        return connections.containsKey(Http2Connection.keyFor(uri,proxy));
    }

    /**
     * If a https request then blocks and waits until a connection is opened.
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
    Http2Connection getConnectionFor(HttpRequestImpl req)
            throws IOException, InterruptedException {
        URI uri = req.uri();
        InetSocketAddress proxy = req.proxy();
        String key = Http2Connection.keyFor(uri, proxy);
        Http2Connection connection;
        synchronized (opening) {
            while ((connection = connections.get(key)) == null) {
                if (!req.secure()) {
                    return null;
                }
                if (!opening.contains(key)) {
                    opening.add(key);
                    break;
                } else {
                    opening.wait();
                }
            }
        }
        if (connection != null) {
            return connection;
        }
        // we are opening the connection here blocking until it is done.
        connection = new Http2Connection(req);
        synchronized (opening) {
            connections.put(key, connection);
            opening.remove(key);
            opening.notifyAll();
        }
        return connection;
    }


    /*
     * TODO: If there isn't a connection to the same destination, then
     * store it. If there is already a connection, then close it
     */
    synchronized void putConnection(Http2Connection c) {
        String key = c.key();
        connections.put(key, c);
    }

    synchronized void deleteConnection(Http2Connection c) {
        String key = c.key();
        connections.remove(key);
    }

    HttpClientImpl client() {
        return client;
    }

    /** Returns the client settings as a base64 (url) encoded string */
    String getSettingsString() {
        SettingsFrame sf = getClientSettings();
        ByteBufferGenerator bg = new ByteBufferGenerator(client);
        sf.writeOutgoing(bg);
        byte[] settings = bg.asByteArray(9); // without the header
        Base64.Encoder encoder = Base64.getUrlEncoder()
                                       .withoutPadding();
        return encoder.encodeToString(settings);
    }

    private static final int K = 1024;

    SettingsFrame getClientSettings() {
        SettingsFrame frame = new SettingsFrame();
        frame.setParameter(HEADER_TABLE_SIZE, Utils.getIntegerNetProperty(
            "java.net.httpclient.hpack.maxheadertablesize", 16 * K));
        frame.setParameter(ENABLE_PUSH, Utils.getIntegerNetProperty(
            "java.net.httpclient.enablepush", 1));
        frame.setParameter(MAX_CONCURRENT_STREAMS, Utils.getIntegerNetProperty(
            "java.net.httpclient.maxstreams", 16));
        frame.setParameter(INITIAL_WINDOW_SIZE, Utils.getIntegerNetProperty(
            "java.net.httpclient.windowsize", 32 * K));
        frame.setParameter(MAX_FRAME_SIZE, Utils.getIntegerNetProperty(
            "java.net.httpclient.maxframesize", 16 * K));
        frame.computeLength();
        return frame;
    }
}
