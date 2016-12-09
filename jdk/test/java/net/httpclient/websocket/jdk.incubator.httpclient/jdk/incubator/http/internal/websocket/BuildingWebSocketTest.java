/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.websocket;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URI;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import static jdk.incubator.http.internal.websocket.TestSupport.assertCompletesExceptionally;
import static jdk.incubator.http.internal.websocket.TestSupport.assertThrows;

/*
 * In some places in this class a new String is created out of a string literal.
 * The idea is to make sure the code under test relies on something better than
 * the reference equality ( == ) for string equality checks.
 */
public class BuildingWebSocketTest {

    @Test
    public void nulls() {
        HttpClient c = HttpClient.newHttpClient();
        URI uri = URI.create("ws://websocket.example.com");

        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(null, listener()));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, null));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(null, null));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .header(null, "value"));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .header("name", null));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .header(null, null));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .subprotocols(null));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .subprotocols(null, "sub1"));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .subprotocols("sub1.example.com", (String) null));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .subprotocols("sub1.example.com", (String[]) null));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .subprotocols("sub1.example.com",
                                          "sub2.example.com",
                                          null));
        assertThrows(NullPointerException.class,
                     () -> c.newWebSocketBuilder(uri, listener())
                            .connectTimeout(null));
    }

    @Test(dataProvider = "badURIs")
    void illegalURI(String u) {
        WebSocket.Builder b = HttpClient.newHttpClient()
                .newWebSocketBuilder(URI.create(u), listener());
        assertCompletesExceptionally(IllegalArgumentException.class, b.buildAsync());
    }

    @Test
    public void illegalHeaders() {
        List<String> headers = List.of("Authorization",
                                       "Connection",
                                       "Cookie",
                                       "Content-Length",
                                       "Date",
                                       "Expect",
                                       "From",
                                       "Host",
                                       "Origin",
                                       "Proxy-Authorization",
                                       "Referer",
                                       "User-agent",
                                       "Upgrade",
                                       "Via",
                                       "Warning",
                                       "Sec-WebSocket-Accept",
                                       "Sec-WebSocket-Extensions",
                                       "Sec-WebSocket-Key",
                                       "Sec-WebSocket-Protocol",
                                       "Sec-WebSocket-Version").stream()
                .map(String::new).collect(Collectors.toList());

        Function<String, CompletionStage<?>> f =
                header -> HttpClient
                        .newHttpClient()
                        .newWebSocketBuilder(URI.create("ws://websocket.example.com"),
                                             listener())
                        .buildAsync();

        headers.forEach(h -> assertCompletesExceptionally(IllegalArgumentException.class, f.apply(h)));
    }

    // TODO: test for bad syntax headers
    // TODO: test for overwrites (subprotocols) and additions (headers)

    @Test(dataProvider = "badSubprotocols")
    public void illegalSubprotocolsSyntax(String s) {
        WebSocket.Builder b = HttpClient.newHttpClient()
                .newWebSocketBuilder(URI.create("ws://websocket.example.com"),
                                     listener());
        b.subprotocols(s);
        assertCompletesExceptionally(IllegalArgumentException.class, b.buildAsync());
    }

    @Test(dataProvider = "duplicatingSubprotocols")
    public void illegalSubprotocolsDuplicates(String mostPreferred,
                                              String[] lesserPreferred) {
        WebSocket.Builder b = HttpClient.newHttpClient()
                .newWebSocketBuilder(URI.create("ws://websocket.example.com"),
                                     listener());
        b.subprotocols(mostPreferred, lesserPreferred);
        assertCompletesExceptionally(IllegalArgumentException.class, b.buildAsync());
    }

    @Test(dataProvider = "badConnectTimeouts")
    public void illegalConnectTimeout(Duration d) {
        WebSocket.Builder b = HttpClient.newHttpClient()
                .newWebSocketBuilder(URI.create("ws://websocket.example.com"),
                                     listener());
        b.connectTimeout(d);
        assertCompletesExceptionally(IllegalArgumentException.class, b.buildAsync());
    }

    @DataProvider
    public Object[][] badURIs() {
        return new Object[][]{
                {"http://example.com"},
                {"ftp://example.com"},
                {"wss://websocket.example.com/hello#fragment"},
                {"ws://websocket.example.com/hello#fragment"},
        };
    }

    @DataProvider
    public Object[][] badConnectTimeouts() {
        return new Object[][]{
                {Duration.ofDays   ( 0)},
                {Duration.ofDays   (-1)},
                {Duration.ofHours  ( 0)},
                {Duration.ofHours  (-1)},
                {Duration.ofMinutes( 0)},
                {Duration.ofMinutes(-1)},
                {Duration.ofSeconds( 0)},
                {Duration.ofSeconds(-1)},
                {Duration.ofMillis ( 0)},
                {Duration.ofMillis (-1)},
                {Duration.ofNanos  ( 0)},
                {Duration.ofNanos  (-1)},
                {Duration.ZERO},
        };
    }

    // https://tools.ietf.org/html/rfc7230#section-3.2.6
    // https://tools.ietf.org/html/rfc20
    @DataProvider
    public static Object[][] badSubprotocols() {
        return new Object[][]{
                {new String("")},
                {"round-brackets("},
                {"round-brackets)"},
                {"comma,"},
                {"slash/"},
                {"colon:"},
                {"semicolon;"},
                {"angle-brackets<"},
                {"angle-brackets>"},
                {"equals="},
                {"question-mark?"},
                {"at@"},
                {"brackets["},
                {"backslash\\"},
                {"brackets]"},
                {"curly-brackets{"},
                {"curly-brackets}"},
                {"space "},
                {"non-printable-character " + Character.toString((char) 31)},
                {"non-printable-character " + Character.toString((char) 127)},
        };
    }

    @DataProvider
    public static Object[][] duplicatingSubprotocols() {
        return new Object[][]{
                {"a.b.c", new String[]{"a.b.c"}},
                {"a.b.c", new String[]{"x.y.z", "p.q.r", "x.y.z"}},
                {"a.b.c", new String[]{new String("a.b.c")}},
        };
    }

    private static WebSocket.Listener listener() {
        return new WebSocket.Listener() { };
    }
}
