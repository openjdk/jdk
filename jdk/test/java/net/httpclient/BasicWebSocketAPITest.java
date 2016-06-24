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

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.CloseCode;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/*
 * @test
 * @bug 8087113
 * @build TestKit
 * @run testng/othervm BasicWebSocketAPITest
 */
public class BasicWebSocketAPITest {

    @Test
    public void webSocket() throws Exception {
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(IllegalArgumentException.class,
                                "(?i).*\\bnegative\\b.*",
                                () -> ws.request(-1))
        );
        checkAndClose((ws) ->
                TestKit.assertNotThrows(() -> ws.request(0))
        );
        checkAndClose((ws) ->
                TestKit.assertNotThrows(() -> ws.request(1))
        );
        checkAndClose((ws) ->
                TestKit.assertNotThrows(() -> ws.request(Long.MAX_VALUE))
        );
        checkAndClose((ws) ->
                TestKit.assertNotThrows(ws::isClosed)
        );
        checkAndClose((ws) ->
                TestKit.assertNotThrows(ws::getSubprotocol)
        );
        checkAndClose(
                (ws) -> {
                    try {
                        ws.abort();
                    } catch (IOException ignored) { }
                    // No matter what happens during the first abort invocation,
                    // other invocations must return normally
                    TestKit.assertNotThrows(ws::abort);
                    TestKit.assertNotThrows(ws::abort);
                }
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(NullPointerException.class,
                                "message",
                                () -> ws.sendBinary(null, true))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(IllegalArgumentException.class,
                                ".*message.*",
                                () -> ws.sendPing(ByteBuffer.allocate(126)))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(NullPointerException.class,
                                "message",
                                () -> ws.sendPing(null))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(IllegalArgumentException.class,
                                ".*message.*",
                                () -> ws.sendPong(ByteBuffer.allocate(126)))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(NullPointerException.class,
                                "message",
                                () -> ws.sendPong(null))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(NullPointerException.class,
                                "message",
                                () -> ws.sendText(null, true))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(NullPointerException.class,
                                "message",
                                () -> ws.sendText(null))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(IllegalArgumentException.class,
                                "(?i).*reason.*",
                                () -> ws.sendClose(CloseCode.NORMAL_CLOSURE, CharBuffer.allocate(124)))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(NullPointerException.class,
                                "code",
                                () -> ws.sendClose(null, ""))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertNotThrows(
                                () -> ws.sendClose(CloseCode.NORMAL_CLOSURE, ""))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(NullPointerException.class,
                                "reason",
                                () -> ws.sendClose(CloseCode.NORMAL_CLOSURE, null))
        );
        checkAndClose(
                (ws) ->
                        TestKit.assertThrows(NullPointerException.class,
                                "code|reason",
                                () -> ws.sendClose(null, null))
        );
    }

    @Test
    public void builder() {
        URI ws = URI.create("ws://localhost:9001");
        // FIXME: check all 24 cases:
        // {null, ws, wss, incorrect} x {null, HttpClient.getDefault(), custom} x {null, listener}
        //
        // if (any null) or (any incorrect)
        //     NPE or IAE is thrown
        // else
        //     builder is created
        TestKit.assertThrows(NullPointerException.class,
                "uri",
                () -> WebSocket.newBuilder(null, defaultListener())
        );
        TestKit.assertThrows(NullPointerException.class,
                "listener",
                () -> WebSocket.newBuilder(ws, null)
        );
        URI uri = URI.create("ftp://localhost:9001");
        TestKit.assertThrows(IllegalArgumentException.class,
                "(?i).*\\buri\\b\\s+\\bscheme\\b.*",
                () -> WebSocket.newBuilder(uri, defaultListener())
        );
        TestKit.assertNotThrows(
                () -> WebSocket.newBuilder(ws, defaultListener())
        );
        URI uri1 = URI.create("wss://localhost:9001");
        TestKit.assertNotThrows(
                () -> WebSocket.newBuilder(uri1, defaultListener())
        );
        URI uri2 = URI.create("wss://localhost:9001#a");
        TestKit.assertThrows(IllegalArgumentException.class,
                "(?i).*\\bfragment\\b.*",
                () -> WebSocket.newBuilder(uri2, HttpClient.getDefault(), defaultListener())
        );
        TestKit.assertThrows(NullPointerException.class,
                "uri",
                () -> WebSocket.newBuilder(null, HttpClient.getDefault(), defaultListener())
        );
        TestKit.assertThrows(NullPointerException.class,
                "client",
                () -> WebSocket.newBuilder(ws, null, defaultListener())
        );
        TestKit.assertThrows(NullPointerException.class,
                "listener",
                () -> WebSocket.newBuilder(ws, HttpClient.getDefault(), null)
        );
        // FIXME: check timeout works
        // (i.e. it directly influences the time WebSocket waits for connection + opening handshake)
        TestKit.assertNotThrows(
                () -> WebSocket.newBuilder(ws, defaultListener()).connectTimeout(Duration.ofSeconds(1))
        );
        // FIXME: check these headers are actually received by the server
        TestKit.assertNotThrows(
                () -> WebSocket.newBuilder(ws, defaultListener()).header("a", "b")
        );
        TestKit.assertNotThrows(
                () -> WebSocket.newBuilder(ws, defaultListener()).header("a", "b").header("a", "b")
        );
        // FIXME: check all 18 cases:
        // {null, websocket(7), custom} x {null, custom}
        WebSocket.Builder builder2 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(NullPointerException.class,
                "name",
                () -> builder2.header(null, "b")
        );
        WebSocket.Builder builder3 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(NullPointerException.class,
                "value",
                () -> builder3.header("a", null)
        );
        WebSocket.Builder builder4 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder4.header("Sec-WebSocket-Accept", "")
        );
        WebSocket.Builder builder5 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder5.header("Sec-WebSocket-Extensions", "")
        );
        WebSocket.Builder builder6 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder6.header("Sec-WebSocket-Key", "")
        );
        WebSocket.Builder builder7 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder7.header("Sec-WebSocket-Protocol", "")
        );
        WebSocket.Builder builder8 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder8.header("Sec-WebSocket-Version", "")
        );
        WebSocket.Builder builder9 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder9.header("Connection", "")
        );
        WebSocket.Builder builder10 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder10.header("Upgrade", "")
        );
        // FIXME: check 3 cases (1 arg):
        // {null, incorrect, custom}
        // FIXME: check 12 cases (2 args):
        // {null, incorrect, custom} x {(String) null, (String[]) null, incorrect, custom}
        // FIXME: check 27 cases (3 args) (the interesting part in null inside var-arg):
        // {null, incorrect, custom}^3
        // FIXME: check the server receives them in the order listed
        TestKit.assertThrows(NullPointerException.class,
                "mostPreferred",
                () -> WebSocket.newBuilder(ws, defaultListener()).subprotocols(null)
        );
        TestKit.assertThrows(NullPointerException.class,
                "lesserPreferred",
                () -> WebSocket.newBuilder(ws, defaultListener()).subprotocols("a", null)
        );
        TestKit.assertThrows(NullPointerException.class,
                "lesserPreferred\\[0\\]",
                () -> WebSocket.newBuilder(ws, defaultListener()).subprotocols("a", null, "b")
        );
        TestKit.assertThrows(NullPointerException.class,
                "lesserPreferred\\[1\\]",
                () -> WebSocket.newBuilder(ws, defaultListener()).subprotocols("a", "b", null)
        );
        TestKit.assertNotThrows(
                () -> WebSocket.newBuilder(ws, defaultListener()).subprotocols("a")
        );
        TestKit.assertNotThrows(
                () -> WebSocket.newBuilder(ws, defaultListener()).subprotocols("a", "b", "c")
        );
        WebSocket.Builder builder11 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder11.subprotocols("")
        );
        WebSocket.Builder builder12 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder12.subprotocols("a", "a")
        );
        WebSocket.Builder builder13 = WebSocket.newBuilder(ws, defaultListener());
        TestKit.assertThrows(IllegalArgumentException.class,
                () -> builder13.subprotocols("a" + ((char) 0x7f))
        );
    }

    private static WebSocket.Listener defaultListener() {
        return new WebSocket.Listener() { };
    }

    //
    // Automatically closes everything after the check has been performed
    //
    private static void checkAndClose(Consumer<? super WebSocket> c) {
        HandshakePhase HandshakePhase
                = new HandshakePhase(new InetSocketAddress("127.0.0.1", 0));
        URI serverURI = HandshakePhase.getURI();
        CompletableFuture<SocketChannel> cfc = HandshakePhase.afterHandshake();
        WebSocket.Builder b = WebSocket.newBuilder(serverURI, defaultListener());
        CompletableFuture<WebSocket> cfw = b.buildAsync();

        try {
            WebSocket ws;
            try {
                ws = cfw.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            c.accept(ws);
        } finally {
            try {
                SocketChannel now = cfc.getNow(null);
                if (now != null) {
                    now.close();
                }
            } catch (Throwable ignored) { }
        }
    }
}
