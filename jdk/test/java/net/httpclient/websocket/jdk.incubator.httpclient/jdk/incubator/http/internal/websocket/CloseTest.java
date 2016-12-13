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

import jdk.incubator.http.WebSocket;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static jdk.incubator.http.internal.websocket.TestSupport.Expectation.ifExpect;
import static jdk.incubator.http.internal.websocket.TestSupport.cartesianIterator;
import static java.util.Arrays.asList;
import static java.util.List.of;

/*
 * Tests for Close message handling: examines sendClose/onClose contracts.
 */
public final class CloseTest {

    /*
     * Verifies the domain of the arguments of sendClose(code, reason).
     */
    @Test(dataProvider = "sendClose")
    public void testSendCloseArguments(int code, String reason) {
        WebSocket ws = newWebSocket();
        ifExpect(
                reason == null,
                NullPointerException.class::isInstance)
        .orExpect(
                !isOutgoingCodeLegal(code),
                IllegalArgumentException.class::isInstance)
        .orExpect(
                !isReasonLegal(reason),
                IllegalArgumentException.class::isInstance)
        .assertThrows(() -> ws.sendClose(code, reason));
    }

    /*
     * After sendClose(code, reason) has returned normally or exceptionally, no
     * more messages can be sent. However, if the invocation has thrown IAE/NPE
     * (i.e. programming error) messages can still be sent (failure atomicity).
     */
    public void testSendClose(int code, String reason) {
        newWebSocket().sendClose(10, "");
    }

    /*
     * After sendClose() has been invoked, no more messages can be sent.
     */
    public void testSendClose() {
        WebSocket ws = newWebSocket();
        CompletableFuture<WebSocket> cf = ws.sendClose();
    }

    // TODO: sendClose can be invoked whenever is suitable without ISE
    // + idempotency

    /*
     * An invocation of sendClose(code, reason) will cause a Close message with
     * the same code and the reason to appear on the wire.
     */
    public void testSendCloseWysiwyg(int code, String reason) {

    }

    /*
     * An invocation of sendClose() will cause an empty Close message to appear
     * on the wire.
     */
    public void testSendCloseWysiwyg() {

    }

    /*
     * Automatic Closing handshake. Listener receives onClose() and returns from
     * it. WebSocket closes in accordance to the returned value.
     */
    public void testClosingHandshake1() {
        // TODO: closed if observed shortly after the returned CS completes
    }

    /*
     * sendClose is invoked from within onClose. After sendClose has returned,
     * isClosed() reports true.
     */
    public void testClosingHandshake2() {
        // 1. newWebSocket().sendClose();
        // 2. onClose return null
        // 3. isClosed() == true
    }

    /*
     * sendClose has been invoked, then onClose. Shortly after onClose has
     * returned, isClosed reports true.
     */
    public void testClosingHandshake3() {
    }

    /*
     * Return from onClose with nevercompleting CS then sendClose().
     */
    public void testClosingHandshake4() {

    }

    /*
     * Exceptions thrown from onClose and exceptions a CS returned from onClose
     * "completes exceptionally" with are ignored. In other words, they are
     * never reported to onError().
     */
    public void testOnCloseExceptions() {

    }

    /*
     * An incoming Close message on the wire will cause an invocation of onClose
     * with appropriate values. However, if this message violates the WebSocket
     * Protocol, onError is invoked instead.
     *
     * // TODO: automatic close (if error) AND isClose returns true from onError
     */
    public void testOnCloseWysiwyg() {

    }

    /*
     * Data is read off the wire. An end-of-stream has been reached while
     * reading a frame.
     *
     * onError is invoked with java.net.ProtocolException and the WebSocket this
     * listener has been attached to
     */
    public void testUnexpectedEOS() {

    }

    /*
     * Data is read off the wire. An end-of-stream has been reached in between
     * frames, and no Close frame has been received yet.
     *
     * onClose is invoked with the status code 1006 and the WebSocket this
     * listener has been attached to
     */
    public void testEOS() {

    }

    // TODO: check buffers for change

    @DataProvider(name = "sendClose")
    public Iterator<Object[]> createData() {
        List<Integer> codes = asList(
                Integer.MIN_VALUE, -1, 0, 1, 500, 998, 999, 1000, 1001, 1002,
                1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012,
                1013, 1014, 1015, 1016, 2998, 2999, 3000, 3001, 3998, 3999,
                4000, 4001, 4998, 4999, 5000, 5001, 32768, 65535, 65536,
                Integer.MAX_VALUE);
        String longReason1 = "This is a reason string. Nothing special except " +
                "its UTF-8 representation is a bit " +
                "longer than one hundred and twenty three bytes.";
        assert longReason1.getBytes(StandardCharsets.UTF_8).length > 123;

        // Russian alphabet repeated cyclically until it's enough to pass "123"
        // bytes length
        StringBuilder b = new StringBuilder();
        char c = '\u0410';
        for (int i = 0; i < 62; i++) {
            b.append(c);
            if (++c > '\u042F') {
                c = '\u0410';
            }
        }
        String longReason2 = b.toString();
        assert longReason2.length() <= 123
                && longReason2.getBytes(StandardCharsets.UTF_8).length > 123;

        String malformedReason = new String(new char[]{0xDC00, 0xD800});

        List<String> reasons = asList
                (null, "", "abc", longReason1, longReason2, malformedReason);

        return cartesianIterator(of(codes, reasons), args -> args);
    }

    private boolean isReasonLegal(String reason) {
        if (reason == null) {
            return false;
        }
        ByteBuffer result;
        try {
            result = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(reason));
        } catch (CharacterCodingException e) {
            return false;
        }
        return result.remaining() <= 123;
    }

    private static boolean isOutgoingCodeLegal(int code) {
        if (code < 1000 || code > 4999) {
            return false;
        }
        if (code < 1016) {
            return code == 1000 || code == 1001 || code == 1008 || code == 1011;
        }
        return code >= 3000;
    }

    private WebSocket newWebSocket() {
        WebSocket.Listener l = new WebSocket.Listener() { };
        return new WebSocketImpl(URI.create("ws://example.com"),
                                 "",
                                 new MockChannel.Builder().build(),
                                 l);
    }
}
