/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @library /test/lib
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=NORMAL DecoderInstructionsReaderTest
 */

import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.QPackException;
import jdk.internal.net.http.qpack.readers.DecoderInstructionsReader;
import jdk.internal.net.http.qpack.readers.IntegerReader;
import jdk.internal.net.http.qpack.writers.IntegerWriter;
import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.RepeatedTest;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecoderInstructionsReaderTest {

    DecoderInstructionsReader decoderInstructionsReader;
    private static final Random RANDOM = RandomFactory.getRandom();

    @RepeatedTest(10)
    public void acknowledgementTest() {
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 1 |      Stream ID (7+)       |
        // +---+---------------------------+

        TestDecoderInstructionsCallback callback = new TestDecoderInstructionsCallback();
        decoderInstructionsReader = new DecoderInstructionsReader(callback, QPACK.getLogger());

        long streamId = RANDOM.nextLong(0, IntegerReader.QPACK_MAX_INTEGER_VALUE);
        IntegerWriter writer = new IntegerWriter();
        int bufferSize = requiredBufferSize(7, streamId);
        var payload = 0b1000_0000;

        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        writer.configure(streamId, 7, payload);
        writer.write(byteBuffer);
        byteBuffer.flip();

        decoderInstructionsReader.read(byteBuffer);
        assertEquals(streamId, callback.lastSectionAckStreamId.get());
    }

    @RepeatedTest(10)
    public void cancellationTest() {
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 1 |     Stream ID (6+)    |
        // +---+---+-----------------------+

        TestDecoderInstructionsCallback callback = new TestDecoderInstructionsCallback();
        decoderInstructionsReader = new DecoderInstructionsReader(callback, QPACK.getLogger());

        long streamId = RANDOM.nextLong(0, IntegerReader.QPACK_MAX_INTEGER_VALUE);
        IntegerWriter writer = new IntegerWriter();
        int bufferSize = requiredBufferSize(6, streamId);
        var payload = 0b0100_0000;

        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        writer.configure(streamId, 6, payload);
        writer.write(byteBuffer);
        byteBuffer.flip();

        decoderInstructionsReader.read(byteBuffer);
        assertEquals(streamId, callback.lastCancelStreamId.get());
    }

    @RepeatedTest(10)
    public void incrementTest() {
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 |     Increment (6+)    |
        // +---+---+-----------------------+

        TestDecoderInstructionsCallback callback = new TestDecoderInstructionsCallback();
        decoderInstructionsReader = new DecoderInstructionsReader(callback, QPACK.getLogger());

        long increaseCountInc = RANDOM.nextLong(0, IntegerReader.QPACK_MAX_INTEGER_VALUE);
        IntegerWriter writer = new IntegerWriter();
        int bufferSize = requiredBufferSize(6, increaseCountInc);
        var payload = 0b0000_0000;

        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        writer.configure(increaseCountInc, 6, payload);
        writer.write(byteBuffer);
        byteBuffer.flip();

        decoderInstructionsReader.read(byteBuffer);
        assertEquals(increaseCountInc, callback.lastInsertCountInc.get());

    }

    static int requiredBufferSize(int N, long value) {
        checkPrefix(N);
        int size = 1;
        int max = (2 << (N - 1)) - 1;
        if (value < max) {
            return size;
        }
        size++;
        value -= max;
        while (value >= 128) {
            value /= 128;
            size++;
        }
        return size;
    }

    private static void checkPrefix(int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException("1 <= N <= 8: N= " + N);
        }
    }

    private static class TestDecoderInstructionsCallback implements DecoderInstructionsReader.Callback {
        final AtomicLong lastSectionAckStreamId = new AtomicLong(-1L);
        final AtomicLong lastCancelStreamId = new AtomicLong(-1L);
        final AtomicLong lastInsertCountInc = new AtomicLong(-1L);

        @Override
        public void onSectionAck(long streamId) {
            lastSectionAckStreamId.set(streamId);
        }

        @Override
        public void onStreamCancel(long streamId) {
            lastCancelStreamId.set(streamId);
        }

        @Override
        public void onInsertCountIncrement(long increment) {
            lastInsertCountInc.set(increment);
        }
    }
}
