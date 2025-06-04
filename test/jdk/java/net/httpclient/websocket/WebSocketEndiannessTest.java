/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8351339
 * @summary Verify the intact transmission of the binary payload regardless of its endianness
 * @modules java.net.http/jdk.internal.net.http.websocket
 * @library /test/lib
 * @build DummyWebSocketServer
 *        jdk.test.lib.Asserts
 * @run main WebSocketEndiannessTest
 */

import jdk.internal.net.http.websocket.Frame;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.Supplier;

import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertEqualsByteArray;
import static jdk.test.lib.Asserts.assertTrue;

public class WebSocketEndiannessTest {

    public static void main(String[] args) throws Exception {
        assertEndiannessAgnosticTransfer();
        assertSuccessfulMasking();
    }

    private static void assertEndiannessAgnosticTransfer() throws Exception {
        Supplier<ByteBuffer> bufferSupplier = () -> ByteBuffer
                .wrap(new byte[]{
                        // 4-byte words
                        0x00, 0x01, 0x02, 0x03,
                        0x04, 0x05, 0x06, 0x07,
                        0x08, 0x09, 0x0a, 0x0b,
                        0x0c, 0x0d, 0x0e, 0x0f,
                        // 2-byte words
                        0x10, 0x11,
                        0x12, 0x13,
                        0x14, 0x15,
                        0x16, 0x17,
                        0x18, 0x19,
                        0x1a, 0x1b,
                        0x1c, 0x1d,
                        0x1e, 0x1f,
                        // negative ones
                        -1, -2, -3, 4,
                        -5, -6
                });
        assertEndiannessAgnosticTransfer(bufferSupplier.get().order(LITTLE_ENDIAN));
        assertEndiannessAgnosticTransfer(bufferSupplier.get().order(BIG_ENDIAN));
    }

    private static void assertEndiannessAgnosticTransfer(ByteBuffer data) throws Exception {
        List<DummyWebSocketServer.DecodedFrame> frames = sendDataAndReadFrames(data);
        assertEquals(frames.size(), 1);
        DummyWebSocketServer.DecodedFrame frame = frames.getFirst();
        assertEquals(frame.opcode(), Frame.Opcode.BINARY);
        assertTrue(frame.last());
        assertEqualsByteArray(data.array(), frame.data().array());
    }

    private static List<DummyWebSocketServer.DecodedFrame> sendDataAndReadFrames(ByteBuffer data) throws Exception {
        try (var server = new DummyWebSocketServer();
             var client = HttpClient.newBuilder().proxy(NO_PROXY).build()) {
            server.open();
            WebSocket webSocket = client
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() {})
                    .join();
            try {
                webSocket.sendBinary(data, true).join();
            } finally {
                webSocket.abort();
            }
            return server.readFrames();
        }
    }

    private static void assertSuccessfulMasking() {
        assertSuccessfulMasking(LITTLE_ENDIAN, LITTLE_ENDIAN);
        assertSuccessfulMasking(LITTLE_ENDIAN, BIG_ENDIAN);
        assertSuccessfulMasking(BIG_ENDIAN, LITTLE_ENDIAN);
        assertSuccessfulMasking(BIG_ENDIAN, BIG_ENDIAN);
    }

    private static void assertSuccessfulMasking(ByteOrder srcOrder, ByteOrder dstOrder) {

        // Create the masker
        Frame.Masker masker = new Frame.Masker()
                // `0xB0` and `0xD0` is used instead of `0x0B` and `0x0D` to cover the negative `byte` range:
                //
                //     (byte) 0x0A ->  10
                //     (byte) 0xB0 -> -80
                //     (byte) 0x0C ->  12
                //     (byte) 0xD0 -> -48
                .setMask(0x0AB00CD0);

        // Perform dummy masking to advance `Frame::offset` 1 byte, and effectively make it non-zero.
        // A non-zero `Frame::offset` will trigger `Frame::initVectorMask` invocation.
        masker.applyMask(ByteBuffer.wrap(new byte[1]), ByteBuffer.wrap(new byte[1]));

        // Perform the actual masking
        ByteBuffer src = ByteBuffer
                .wrap(new byte[]{
                        // `initVectorMask` will mask 3 bytes to position the `offset` back to 0.
                        // It is 3 bytes, because of the 1 byte dummy advancement above.
                        -0x1, -0x2, 0x3,
                        // `applyVectorMask` will make a single 8-byte pass
                        0x1, 0x2, 0x3, 0x4, -0x5, 0x6, -0x7, -0x8,
                        // `applyPlainMask` will mask 3 bytes
                        0x1, -0x2, -0x3
                        // Note minus signs sprinkled above to cover the negative `byte` range in a certain structure:
                        // Some will get masked with a positive number, some with a negative.
                        // For instance, for `applyPlainMask`:
                        // - `0x1` will be masked with `0xA`
                        // - `-0x2` will be masked with `0xB0`
                })
                .order(srcOrder);
        ByteBuffer dst = ByteBuffer.allocate(src.capacity()).order(dstOrder);
        masker.applyMask(src, dst);

        // Verify the masking
        assertEqualsByteArray(
                new byte[]{
                        // 3 bytes for `initVectorMask`.
                        // Remember 0xA is consumed by the initial dummy masking.
                        (byte) (-0x1 ^ 0xB0),
                        -0x2 ^ 0xC,
                        (byte) (0x3 ^ 0xD0),
                        // 8 bytes for `applyVectorMask`
                        0x1 ^ 0xA,
                        (byte) (0x2 ^ 0xB0),
                        0x3 ^ 0xC,
                        (byte) (0x4 ^ 0xD0),
                        -0x5 ^ 0xA,
                        (byte) (0x6 ^ 0xB0),
                        -0x7 ^ 0xC,
                        (byte) (-0x8 ^ 0xD0),
                        // 3 bytes for `applyPlainMask`
                        0x1 ^ 0xA,
                        (byte) (-0x2 ^ 0xB0),
                        -0x3 ^ 0xC
                },
                dst.array());

    }

}
