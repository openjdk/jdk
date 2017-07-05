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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

final class WSBuilder implements WebSocket.Builder {

    private static final Set<String> FORBIDDEN_HEADERS =
            new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        List<String> headers = List.of("Connection", "Upgrade",
                "Sec-WebSocket-Accept", "Sec-WebSocket-Extensions",
                "Sec-WebSocket-Key", "Sec-WebSocket-Protocol",
                "Sec-WebSocket-Version");
        FORBIDDEN_HEADERS.addAll(headers);
    }

    private final URI uri;
    private final HttpClient client;
    private final LinkedHashMap<String, List<String>> headers = new LinkedHashMap<>();
    private final WebSocket.Listener listener;
    private Collection<String> subprotocols = Collections.emptyList();
    private Duration timeout;

    WSBuilder(URI uri, HttpClient client, WebSocket.Listener listener) {
        checkURI(requireNonNull(uri, "uri"));
        requireNonNull(client, "client");
        requireNonNull(listener, "listener");
        this.uri = uri;
        this.listener = listener;
        this.client = client;
    }

    @Override
    public WebSocket.Builder header(String name, String value) {
        requireNonNull(name, "name");
        requireNonNull(value, "value");
        if (FORBIDDEN_HEADERS.contains(name)) {
            throw new IllegalArgumentException(
                    format("Header '%s' is used in the WebSocket Protocol", name));
        }
        List<String> values = headers.computeIfAbsent(name, n -> new LinkedList<>());
        values.add(value);
        return this;
    }

    @Override
    public WebSocket.Builder subprotocols(String mostPreferred, String... lesserPreferred) {
        requireNonNull(mostPreferred, "mostPreferred");
        requireNonNull(lesserPreferred, "lesserPreferred");
        this.subprotocols = checkSubprotocols(mostPreferred, lesserPreferred);
        return this;
    }

    @Override
    public WebSocket.Builder connectTimeout(Duration timeout) {
        this.timeout = requireNonNull(timeout, "timeout");
        return this;
    }

    @Override
    public CompletableFuture<WebSocket> buildAsync() {
        return WS.newInstanceAsync(this);
    }

    private static URI checkURI(URI uri) {
        String s = uri.getScheme();
        if (!("ws".equalsIgnoreCase(s) || "wss".equalsIgnoreCase(s))) {
            throw new IllegalArgumentException
                    ("URI scheme not ws or wss (RFC 6455 3.): " + s);
        }
        String fragment = uri.getFragment();
        if (fragment != null) {
            throw new IllegalArgumentException(format
                    ("Fragment not allowed in a WebSocket URI (RFC 6455 3.): '%s'",
                            fragment));
        }
        return uri;
    }

    URI getUri() { return uri; }

    HttpClient getClient() { return client; }

    Map<String, List<String>> getHeaders() {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>(headers.size());
        headers.forEach((name, values) -> copy.put(name, new LinkedList<>(values)));
        return copy;
    }

    WebSocket.Listener getListener() { return listener; }

    Collection<String> getSubprotocols() {
        return new ArrayList<>(subprotocols);
    }

    Duration getConnectTimeout() { return timeout; }

    private static Collection<String> checkSubprotocols(String mostPreferred,
                                                        String... lesserPreferred) {
        checkSubprotocolSyntax(mostPreferred, "mostPreferred");
        LinkedHashSet<String> sp = new LinkedHashSet<>(1 + lesserPreferred.length);
        sp.add(mostPreferred);
        for (int i = 0; i < lesserPreferred.length; i++) {
            String p = lesserPreferred[i];
            String location = format("lesserPreferred[%s]", i);
            requireNonNull(p, location);
            checkSubprotocolSyntax(p, location);
            if (!sp.add(p)) {
                throw new IllegalArgumentException(format(
                        "Duplicate subprotocols (RFC 6455 4.1.): '%s'", p));
            }
        }
        return sp;
    }

    private static void checkSubprotocolSyntax(String subprotocol, String location) {
        if (subprotocol.isEmpty()) {
            throw new IllegalArgumentException
                    ("Subprotocol name is empty (RFC 6455 4.1.): " + location);
        }
        if (!subprotocol.chars().allMatch(c -> 0x21 <= c && c <= 0x7e)) {
            throw new IllegalArgumentException
                    ("Subprotocol name contains illegal characters (RFC 6455 4.1.): "
                            + location);
        }
    }
}
