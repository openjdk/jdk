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

import org.testng.SkipException;
import org.testng.annotations.Test;
import jdk.incubator.http.internal.websocket.Frame.Opcode;

import java.io.IOException;
import java.net.ProtocolException;
import jdk.incubator.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static jdk.incubator.http.internal.websocket.TestSupport.Expectation.ifExpect;
import static jdk.incubator.http.internal.websocket.TestSupport.assertCompletesExceptionally;
import static jdk.incubator.http.internal.websocket.TestSupport.checkExpectations;
import static org.testng.Assert.assertSame;

/*
 * Examines sendPing/onPing contracts
 */
public final class PingTest {

    /*
     * sendPing(message) is invoked. If the `message` argument is illegal, then
     * the method must throw an appropriate exception. Otherwise no exception
     * must be thrown.
     */
//    @Test(dataProvider = "outgoingData", dataProviderClass = DataProviders.class)
    public void testSendPingArguments(ByteBuffer message) {
        WebSocket ws = newWebSocket();
        ifExpect(
                message == null,
                NullPointerException.class::isInstance)
        .orExpect(
                message != null && message.remaining() > 125,
                IllegalArgumentException.class::isInstance)
        .assertThrows(
                () -> ws.sendPing(message)
        );
    }

    /*
     * sendPing(message) with a legal argument has been invoked, then:
     *
     * 1. A Ping message with the same payload appears on the wire
     * 2. The CF returned from the method completes normally with the same
     *    WebSocket that sendPing has been called on
     */
    @Test(dataProvider = "outgoingData", dataProviderClass = DataProviders.class)
    public void testSendPingWysiwyg(ByteBuffer message) throws ExecutionException, InterruptedException {
        if (message == null || message.remaining() > 125) {
            return;
        }
        ByteBuffer snapshot = copy(message);
        MockChannel channel = new MockChannel.Builder()
                .expectPing(snapshot::equals)
                .build();
        WebSocket ws = newWebSocket(channel);
        CompletableFuture<WebSocket> cf = ws.sendPing(message);
        WebSocket ws1 = cf.join();
        assertSame(ws1, ws); // (2)
        checkExpectations(channel); // (1)
    }

    /*
     * If an I/O error occurs while Ping messages is being sent, then:
     *
     * 1. The CF returned from sendPing completes exceptionally with this I/O
     *    error as the cause
     */
//    @Test
    public void testSendPingIOException() {
        MockChannel ch = new MockChannel.Builder()
//                .provideWriteException(IOException::new)
                .build();
        WebSocket ws = newWebSocket(ch);
        CompletableFuture<WebSocket> cf = ws.sendPing(ByteBuffer.allocate(16));
        assertCompletesExceptionally(IOException.class, cf);
    }

    /*
     * If an incorrect Ping frame appears on the wire, then:
     *
     * 1. onError with the java.net.ProtocolException is invoked
     * 1. A Close frame with status code 1002 appears on the wire
     */
//    @Test(dataProvider = "incorrectFrame", dataProviderClass = DataProviders.class)
    public void testOnPingIncorrect(boolean fin, boolean rsv1, boolean rsv2,
                                    boolean rsv3, ByteBuffer data) {
        if (fin && !rsv1 && !rsv2 && !rsv3 && data.remaining() <= 125) {
            throw new SkipException("Correct frame");
        }
        CompletableFuture<WebSocket> webSocket = new CompletableFuture<>();
        MockChannel channel = new MockChannel.Builder()
                .provideFrame(fin, rsv1, rsv2, rsv3, Opcode.PING, data)
                .expectClose((code, reason) ->
                        Integer.valueOf(1002).equals(code) && "".equals(reason))
                .build();
        MockListener listener = new MockListener.Builder()
                .expectOnOpen((ws) -> true)
                .expectOnError((ws, error) -> error instanceof ProtocolException)
                .build();
        webSocket.complete(newWebSocket(channel, listener));
        checkExpectations(500, TimeUnit.MILLISECONDS, channel, listener);
    }

    /*
     * If a Ping message has been read off the wire, then:
     *
     * 1. onPing is invoked with the data and the WebSocket the listener has
     *    been attached to
     * 2. A Pong message with the same contents will be sent in reply
     */
    @Test(dataProvider = "incomingData", dataProviderClass = DataProviders.class)
    public void testOnPingReply(ByteBuffer data) {
        CompletableFuture<WebSocket> webSocket = new CompletableFuture<>();
        MockChannel channel = new MockChannel.Builder()
                .provideFrame(true, false, false, false, Opcode.PING, data)
                .expectPong(data::equals)
                .build();
        MockListener listener = new MockListener.Builder()
                .expectOnOpen((ws) -> true) // maybe should capture with a CF?
                .expectOnPing((ws, bb) -> data.equals(bb))
                .build();
        webSocket.complete(newWebSocket(channel, listener));
        checkExpectations(500, TimeUnit.MILLISECONDS, channel, listener);
    }

    /*
     * If onPing throws an exception or CS returned from it completes
     * exceptionally, then:
     *
     * 1. onError is invoked with this particular exception as the cause and the
     *    WebSocket the listener has been attached to
     */
    public void testOnPingExceptions() {
    }

    /*
     * If a Ping message has been read off the wire and an I/O error occurs
     * while WebSocket sends a Pong reply to it, then:
     *
     * 1. onError is invoked with this error as the cause and the WebSocket this
     *    listener has been attached to
     */
    public void testOnPingReplyIOException() {
    }

    private WebSocket newWebSocket() {
        return newWebSocket(new MockChannel.Builder().build());
    }

    private WebSocket newWebSocket(RawChannel ch) {
        return newWebSocket(ch, new WebSocket.Listener() { });
    }

    private WebSocket newWebSocket(RawChannel ch, WebSocket.Listener l) {
//        WebSocketImpl ws = new WebSocketImpl("", ch, l, Executors.newCachedThreadPool());
//        ws.();
//        ws.request(Long.MAX_VALUE);
        return null; // FIXME
    }

    public static ByteBuffer copy(ByteBuffer src) {
        int pos = src.position();
        ByteBuffer b = ByteBuffer.allocate(src.remaining()).put(src).flip();
        src.position(pos);
        return b;
    }
}
