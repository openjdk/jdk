/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.net.quic
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.net.http/jdk.internal.net.http.qpack
 *          java.net.http/jdk.internal.net.http.qpack.readers
 *          java.net.http/jdk.internal.net.http.qpack.writers
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.quic
 *          java.net.http/jdk.internal.net.http.quic.streams
 *          java.net.http/jdk.internal.net.http.http3.streams
 *          java.net.http/jdk.internal.net.http.http3.frames
 *          java.net.http/jdk.internal.net.http.http3
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=NORMAL DecoderInstructionsWriterTest
 */

import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.readers.DecoderInstructionsReader;
import jdk.internal.net.http.qpack.readers.IntegerReader;
import jdk.internal.net.http.qpack.writers.DecoderInstructionsWriter;
import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DecoderInstructionsWriterTest {

    @ParameterizedTest
    @MethodSource("decoderInstructionsSource")
    public void decoderInstructionsTest(DecoderInstruction instruction, long value) throws Exception {
        testRunner(instruction, value);
    }

    private static Stream<Arguments> decoderInstructionsSource() {
        // "Section Acknowledgment"
        Stream<Arguments> sectionAck = RANDOM.longs(10,
                        0, IntegerReader.QPACK_MAX_INTEGER_VALUE)
                .boxed()
                .map(l -> Arguments.of(DecoderInstruction.SECTION_ACK, l));

        // "Stream Cancellation"
        Stream<Arguments> streamCancel = RANDOM.longs(10,
                        0, IntegerReader.QPACK_MAX_INTEGER_VALUE)
                .boxed()
                .map(l -> Arguments.of(DecoderInstruction.STREAM_CANCEL, l));

        // "Insert Count Increment"
        Stream<Arguments> insertCountInc = RANDOM.longs(10,
                        0, IntegerReader.QPACK_MAX_INTEGER_VALUE)
                .boxed()
                .map(l -> Arguments.of(DecoderInstruction.INSERT_COUNT_INC, l));

        return Stream.concat(sectionAck, Stream.concat(streamCancel, insertCountInc));
    }


    enum DecoderInstruction {
        SECTION_ACK,
        STREAM_CANCEL,
        INSERT_COUNT_INC;
    }

    private static void testRunner(DecoderInstruction instruction, long value) throws Exception {
        var writer = new DecoderInstructionsWriter();
        int calculatedInstructionSize = configureWriter(writer, instruction, value);
        var logger = QPACK.getLogger();
        var dynamicTable = new DynamicTable(logger);

        var buffers = new ArrayList<ByteBuffer>();

        boolean writeDone = false;
        int writtenBytes = 0;
        // Write instruction to a byte buffers of a random size
        while (!writeDone) {
            int allocSize = RANDOM.nextInt(1, 9);
            var buffer = ByteBuffer.allocate(allocSize);

            writeDone = writer.write(buffer);
            writtenBytes += buffer.position();
            buffer.flip();
            buffers.add(buffer);
        }
        // Check that instruction size calculated by the writer matches
        // the number of written bytes
        assertEquals(writtenBytes, calculatedInstructionSize);

        // Read back the data from byte buffers
        var callback = new TestDecoderInstructionsCallback();
        var reader = new DecoderInstructionsReader(callback, logger);
        for (var bb : buffers) {
            reader.read(bb);
        }
        // Check that reader callback values match values supplied to the writer
        long instructionValue = extractCallbackValue(instruction, callback);
        assertEquals(value, instructionValue);
    }

    private static long extractCallbackValue(DecoderInstruction instruction,
                                             TestDecoderInstructionsCallback callback) {
        return switch (instruction) {
            case SECTION_ACK -> callback.lastSectionAckStreamId.get();
            case STREAM_CANCEL -> callback.lastCancelStreamId.get();
            case INSERT_COUNT_INC -> callback.lastInsertCountInc.get();
        };
    }


    private static int configureWriter(DecoderInstructionsWriter writer,
                                       DecoderInstruction instruction,
                                       long instructionValue) {
        return switch (instruction) {
            case SECTION_ACK -> writer.configureForSectionAck(instructionValue);
            case STREAM_CANCEL -> writer.configureForStreamCancel(instructionValue);
            case INSERT_COUNT_INC -> writer.configureForInsertCountInc(instructionValue);
        };
    }

    private static final Random RANDOM = RandomFactory.getRandom();

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
