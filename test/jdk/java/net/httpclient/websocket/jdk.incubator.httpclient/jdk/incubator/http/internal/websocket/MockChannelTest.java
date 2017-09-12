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

import org.testng.annotations.Test;
import jdk.incubator.http.internal.websocket.Frame.Opcode;

import java.io.IOException;
import jdk.incubator.http.internal.websocket.TestSupport.AssertionFailedException;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static jdk.incubator.http.internal.websocket.TestSupport.assertThrows;
import static jdk.incubator.http.internal.websocket.TestSupport.checkExpectations;
import static jdk.incubator.http.internal.websocket.Frame.MAX_HEADER_SIZE_BYTES;

public final class MockChannelTest {

    // TODO: tests for read (stubbing)

    @Test
    public void testPass01() {
        MockChannel ch = new MockChannel.Builder().build();
        checkExpectations(1, TimeUnit.SECONDS, ch);
    }

    @Test
    public void testPass02() throws IOException {
        int len = 8;
        ByteBuffer header = ByteBuffer.allocate(MAX_HEADER_SIZE_BYTES);
        ByteBuffer data = ByteBuffer.allocate(len);
        new Frame.HeaderWriter()
                .fin(true).opcode(Opcode.PONG).payloadLen(len).mask(0x12345678)
                .write(header);
        header.flip();
        MockChannel ch = new MockChannel.Builder()
                .expectPong(bb -> bb.remaining() == len)
                .build();
        ch.write(new ByteBuffer[]{header, data}, 0, 2);
        checkExpectations(1, TimeUnit.SECONDS, ch);
    }

    @Test
    public void testPass03() throws IOException {
        int len = 8;
        ByteBuffer header = ByteBuffer.allocate(MAX_HEADER_SIZE_BYTES);
        ByteBuffer data = ByteBuffer.allocate(len - 2); // not all data is written
        new Frame.HeaderWriter()
                .fin(true).opcode(Opcode.PONG).payloadLen(len).mask(0x12345678)
                .write(header);
        header.flip();
        MockChannel ch = new MockChannel.Builder().build(); // expected no invocations
        ch.write(new ByteBuffer[]{header, data}, 0, 2);
        checkExpectations(1, TimeUnit.SECONDS, ch);
    }

    @Test
    public void testFail01() {
        MockChannel ch = new MockChannel.Builder()
                .expectClose((code, reason) -> code == 1002 && reason.isEmpty())
                .build();
        assertThrows(AssertionFailedException.class,
                () -> checkExpectations(1, TimeUnit.SECONDS, ch));
    }

    @Test
    public void testFail02() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(MAX_HEADER_SIZE_BYTES);
        new Frame.HeaderWriter()
                .fin(true).opcode(Opcode.CLOSE).payloadLen(2).mask(0x12345678)
                .write(header);
        header.flip();
        ByteBuffer data = ByteBuffer.allocate(2).putChar((char) 1004).flip();
        MockChannel ch = new MockChannel.Builder()
                .expectClose((code, reason) -> code == 1002 && reason.isEmpty())
                .build();
        ch.write(new ByteBuffer[]{header, data}, 0, 2);
        assertThrows(AssertionFailedException.class,
                () -> checkExpectations(1, TimeUnit.SECONDS, ch));
    }
}
