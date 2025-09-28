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
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=NORMAL EncoderInstructionsReaderTest
 */

import jdk.internal.net.http.hpack.QuickHuffman;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.readers.IntegerReader;
import java.nio.ByteBuffer;
import jdk.internal.net.http.qpack.writers.IntegerWriter;
import jdk.internal.net.http.qpack.writers.StringWriter;
import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jdk.internal.net.http.qpack.readers.EncoderInstructionsReader;
import jdk.internal.net.http.qpack.readers.EncoderInstructionsReader.Callback;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public class EncoderInstructionsReaderTest {

    EncoderInstructionsReader encoderInstructionsReader;
    private static final Random RANDOM = RandomFactory.getRandom();

    @RepeatedTest(5)
    public void testCapacity() {

        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 1 |   Capacity (5+)   |
        // +---+---+---+-------------------+

        // create logger and callback
        QPACK.Logger logger = QPACK.getLogger().subLogger("testCapacity");
        TestCallback callback = new TestCallback();

        // create a random value to be assigned as capacity
        Long expectedCapacity = RANDOM.nextLong(IntegerReader.QPACK_MAX_INTEGER_VALUE);

        // create integerWriter, set expected size for the bytebuffer and write to it
        IntegerWriter integerWriter = new IntegerWriter();
        int bufferSize = requiredBufferSize(5, expectedCapacity);
        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        int payload = 0b0010_0000;
        integerWriter.configure(expectedCapacity, 5, payload);
        boolean result = integerWriter.write(byteBuffer);

        // assert that the writer finished and isn't expecting another bytebuffer
        assert result;

        byteBuffer.flip();

        // use EncoderInstructionReader and check it successfully reads the input
        encoderInstructionsReader = new EncoderInstructionsReader(callback, logger);
        encoderInstructionsReader.read(byteBuffer, -1);

        long actualCapacity = callback.capacity.get();
        assertEquals(expectedCapacity, actualCapacity, "expected capacity differed from actual result");
    }

    @RepeatedTest(10)
    public void testInsertWithNameReference() {

        //    0   1   2   3   4   5   6   7
        //  +---+---+---+---+---+---+---+---+
        //  | 1 | T |    Name Index (6+)    |
        //  +---+---+-----------------------+
        //  | H |     Value Length (7+)     |
        //  +---+---------------------------+
        //  |  Value String (Length bytes)  |
        //  +-------------------------------+

        QPACK.Logger logger = QPACK.getLogger()
                .subLogger("testInsertWithNameReference");
        TestCallback callback = new TestCallback();

        // Needs both an integer and String writer
        IntegerWriter integerWriter = new IntegerWriter();
        StringWriter stringWriter = new StringWriter();
        boolean huffman = RANDOM.nextBoolean();
        boolean staticTable = RANDOM.nextBoolean();

        int payload;
        if (staticTable) {
            payload = 0b1100_0000;
        } else {
            payload = 0b1000_0000;
        }

        // get a random member of the dynamic table and create a random string to update it with
        long index = RANDOM.nextLong(IntegerReader.QPACK_MAX_INTEGER_VALUE);
        String value = randomString();

        // calculate the size of the byteBuffer
        int bufferSize = requiredBufferSize(6, index);
        bufferSize += requiredBufferSize(7, value.length());

        if (huffman) {
            bufferSize += QuickHuffman.lengthOf(value);
        } else {
            bufferSize += value.length();
        }

        integerWriter.configure(index, 6, payload);
        stringWriter.configure(value, huffman);

        boolean intWriterFinished = false;
        boolean stringWriterFinished = false;
        List<ByteBuffer> byteBufferList = new ArrayList<>();

        // Feed the writers with bytebuffers of random size until they total bufferSize,
        // once each bytebuffer is full add it to byteBufferList to be read later
        while (!stringWriterFinished) {
            int randomSize = RANDOM.nextInt(0, bufferSize + 1);
            bufferSize -= randomSize;
            ByteBuffer byteBuffer = ByteBuffer.allocate(randomSize);

            if (!intWriterFinished) {
                intWriterFinished = integerWriter.write(byteBuffer);
                if (!intWriterFinished) {
                    // writer not finished, add bytebuffer to list of full bytebuffers
                    // then loop
                    byteBuffer.flip();
                    byteBufferList.add(byteBuffer);
                    continue;
                }
            }

            // this stage should only be reached if the intWriter is finished
            stringWriterFinished = stringWriter.write(byteBuffer);
            byteBuffer.flip();
            byteBufferList.add(byteBuffer);
        }

        encoderInstructionsReader = new EncoderInstructionsReader(callback, logger);
        for (var byteBuffer : byteBufferList) {
            encoderInstructionsReader.read(byteBuffer, -1);
        }

        assertEquals(index, callback.indexInsert.nameIndex);
        assertEquals(value, callback.indexInsert.value);
        assertEquals(staticTable, callback.indexInsert.staticTable);
    }

    @RepeatedTest(10)
    public void testInsertWithLiteralName() {

        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 1 | H | Name Length (5+)  |
        // +---+---+---+-------------------+
        // |  Name String (Length bytes)   |
        // +---+---------------------------+
        // | H |     Value Length (7+)     |
        // +---+---------------------------+
        // |  Value String (Length bytes)  |
        // +-------------------------------+

        QPACK.Logger logger = QPACK.getLogger()
                .subLogger("testInsertWithLiteralName");
        TestCallback callback = new TestCallback();

        StringWriter stringWriter = new StringWriter();
        boolean huffman = RANDOM.nextBoolean();

        int payload;
        if (huffman) {
            payload = 0b0110_0000; // static table = true
        } else {
            payload = 0b0100_0000; // static table = false
        }

        //generate name and value then calculate the size required for the bytebuffer
        String name = randomString();
        String value = randomString();

        int bufferSize = requiredBufferSize(5, name.length());
        bufferSize += requiredBufferSize(7, value.length());
        bufferSize += value.length() + name.length();

        List<ByteBuffer> byteBuffers = new ArrayList<>();

        // configure the stringWriter to take name
        stringWriter.configure(name, 5, payload, huffman);

        boolean firstStringWriterFinished = false;
        boolean secondStringWriterFinished = false;

        // Feed the writers with bytebuffers of random size until they total bufferSize,
        // once each bytebuffer is full add it to byteBufferList to be read later
        while (!secondStringWriterFinished) {
            int randomSize = RANDOM.nextInt(0, bufferSize + 1);
            bufferSize -= randomSize;

            ByteBuffer byteBuffer = ByteBuffer.allocate(randomSize);

            if (!firstStringWriterFinished) {
                firstStringWriterFinished = stringWriter.write(byteBuffer);

                if (!firstStringWriterFinished) {
                    // writer not finished, add bytebuffer to array of full bytebuffers
                    // then loop
                    byteBuffer.flip();
                    byteBuffers.add(byteBuffer);
                    continue;
                } else {
                    // if the name has been written then reset the stringWriter
                    // so that it can be reused for value
                    stringWriter.reset();
                    stringWriter.configure(value, huffman);
                }
            }

            // this stage should only be reached if the name has already been written
            secondStringWriterFinished = stringWriter.write(byteBuffer);
            byteBuffer.flip();
            byteBuffers.add(byteBuffer);
        }

        System.err.println(name + " Attempting to insert value: " + value);

        encoderInstructionsReader = new EncoderInstructionsReader(callback, logger);

        for (var byteBuffer : byteBuffers) {
            encoderInstructionsReader.read(byteBuffer, -1);
        }

        assertEquals(name, callback.lastInsert.name);
        assertEquals(value, callback.lastInsert.value);
    }

    @RepeatedTest(5)
    public void testDuplicate() {
        //
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 0 |    Index (5+)     |
        // +---+---+---+-------------------+
        //

        QPACK.Logger logger = QPACK.getLogger()
                .subLogger("testDuplicate");
        TestCallback callback = new TestCallback();

        long index = RANDOM.nextLong(0, DT_NAMES.size());

        IntegerWriter integerWriter = new IntegerWriter();
        int bufferSize = requiredBufferSize(5, index);
        ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
        int payload = 0b0000_0000;
        integerWriter.configure(index, 5, payload);
        boolean result = integerWriter.write(byteBuffer);
        assert result;

        byteBuffer.flip();

        encoderInstructionsReader = new EncoderInstructionsReader(callback, logger);
        encoderInstructionsReader.read(byteBuffer, -1);

        assertEquals(index, callback.duplicate.get());
    }

    private static void checkPrefix(int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException("1 <= N <= 8: N= " + N);
        }
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

    private static final int TEST_STR_MAX_LENGTH = 20;
    private static final String LOREM = """
            Lorem ipsum dolor sit amet, consectetur adipiscing
            elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
            Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris
            nisi ut aliquip ex ea commodo consequat.Duis aute irure dolor in
            reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla
            pariatur.Excepteur sint occaecat cupidatat non proident, sunt in
            culpa qui officia deserunt mollit anim id est laborum."""
            .replaceAll(" ", "")
            .replaceAll("\\W", "");

    private static final List<String> DT_NAMES = generateTableNames(40);

    private static List<String> generateTableNames(int count) {
        return IntStream.range(0, count)
                .boxed()
                .map(i -> randomString())
                .toList();
    }

    private static String randomString() {
        int lower = RANDOM.nextInt(LOREM.length() - TEST_STR_MAX_LENGTH);
        return LOREM.substring(lower, 1 + lower + RANDOM.nextInt(TEST_STR_MAX_LENGTH));
    }

    private static class TestCallback implements Callback {

        record LiteralInsert(String name, String value) {
        }

        record IndexedInsert(boolean staticTable, Long nameIndex, String value) {
        }

        final AtomicLong capacity = new AtomicLong(-1L);
        final AtomicLong duplicate = new AtomicLong(-1L);
        LiteralInsert lastInsert;

        IndexedInsert indexInsert;

        @Override
        public void onCapacityUpdate(long capacity) {
            this.capacity.set(capacity);
        }

        @Override
        public void onInsert(String name, String value) {
            lastInsert = new LiteralInsert(name, value);
        }

        @Override
        public void onInsertIndexedName(boolean indexInStaticTable, long nameIndex, String valueString) {
            indexInsert = new IndexedInsert(indexInStaticTable, nameIndex, valueString);
        }

        @Override
        public void onDuplicate(long duplicateValue) {
            this.duplicate.set(duplicateValue);
        }
    }
}
