/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.quic.frames.CryptoFrame;
import jdk.internal.net.http.quic.streams.CryptoWriterQueue;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;

import static org.testng.Assert.*;

/**
 * @test
 * @summary Tests jdk.internal.net.http.quic.streams,CryptoWriterQueue
 * @modules java.net.http/jdk.internal.net.http.quic.streams
 * java.net.http/jdk.internal.net.http.quic.frames
 * @run testng CryptoWriterQueueTest
 */
public class CryptoWriterQueueTest {

    /**
     * {@link CryptoWriterQueue#enqueue(ByteBuffer) enqueues} data from multiple ByteBuffer
     * instances and then expects the {@link CryptoWriterQueue#produceFrame(int)} to process
     * the enqueued data correctly.
     */
    @Test
    public void testProduceFrame() throws Exception {
        final CryptoWriterQueue writerQueue = new CryptoWriterQueue();
        final ByteBuffer buff1 = createByteBuffer(83);
        final ByteBuffer buff2 = createByteBuffer(1429);
        final ByteBuffer buff3 = createByteBuffer(4);
        // enqueue them
        writerQueue.enqueue(buff1);
        writerQueue.enqueue(buff2);
        writerQueue.enqueue(buff3);
        final int expectedRemaining = buff1.remaining() + buff2.remaining() + buff3.remaining();
        assertEquals(writerQueue.remaining(), expectedRemaining,
                "Unexpected remaining bytes in CryptoWriterQueue");
        // create frame(s) from the enqueued buffers
        final int maxPayloadSize = 1134;
        while (writerQueue.remaining() > 0) {
            final CryptoFrame frame = writerQueue.produceFrame(maxPayloadSize);
            assertNotNull(frame, "Crypto frame is null");
            assertTrue(frame.size() <= maxPayloadSize, "Crypto size " + frame.size()
                    + " exceeds max payload size of " + maxPayloadSize);
        }
    }

    private static ByteBuffer createByteBuffer(final int numBytes) {
        return ByteBuffer.wrap(new byte[numBytes]);
    }

}
