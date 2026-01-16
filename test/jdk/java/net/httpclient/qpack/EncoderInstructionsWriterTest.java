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

import jdk.internal.net.http.qpack.DynamicTable;
import jdk.internal.net.http.qpack.HeaderField;
import jdk.internal.net.http.qpack.QPACK;
import jdk.internal.net.http.qpack.StaticTable;
import jdk.internal.net.http.qpack.TableEntry;
import jdk.internal.net.http.qpack.readers.EncoderInstructionsReader;
import jdk.internal.net.http.qpack.readers.EncoderInstructionsReader.Callback;
import jdk.internal.net.http.qpack.readers.IntegerReader;
import jdk.internal.net.http.qpack.writers.EncoderInstructionsWriter;
import jdk.test.lib.RandomFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static jdk.internal.net.http.qpack.TableEntry.EntryType.NAME;
import static org.junit.jupiter.api.Assertions.*;

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
 * @run junit/othervm -Djdk.internal.httpclient.qpack.log.level=NORMAL EncoderInstructionsWriterTest
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EncoderInstructionsWriterTest {

    @RepeatedTest(10)
    public void tableCapacityInstructionTest() throws Exception {
        // Get test-case specific logger and a dynamic table instance
        QPACK.Logger logger = QPACK.getLogger()
                .subLogger("tableCapacityInstructionTest");
        var dynamicTable = new DynamicTable(logger);
        // Generate random capacity value
        long capacity = RANDOM.nextLong(IntegerReader.QPACK_MAX_INTEGER_VALUE);
        logger.log(System.Logger.Level.TRACE, "Capacity value = " + capacity);

        // Initial dynamic table capacity - required to check for
        // writer changing the capacity once the instruction is written.
        long initialTableCapacity = capacity == 1234L ? 1235L : 1234L;

        // Create and configure encoder instruction writer for writing the table
        // capacity update instruction
        var encoderInstructionsWriter = new EncoderInstructionsWriter();
        int calculatedInstructionSize =
                encoderInstructionsWriter.configureForTableCapacityUpdate(capacity);

        // Set max capacity to maximum possible value
        dynamicTable.setMaxTableCapacity(IntegerReader.QPACK_MAX_INTEGER_VALUE);

        // Create dynamic table with initial capacity
        dynamicTable.setCapacity(initialTableCapacity);

        // Perform write operation and then read the capacity update instruction
        var callback = new TestEncoderInstructionsCallback(dynamicTable);

        int bytesWritten = writeThenReadInstruction(encoderInstructionsWriter,
                callback, -1, dynamicTable,
                (dt) -> dt.capacity() == initialTableCapacity,
                logger);

        // We expect here to get a callback with the capacity value supplied
        // to the instruction writer
        assertEquals(capacity, callback.capacityFromCallback.get());

        // We don't expect dynamic table capacity to be updated by the encoder
        // instruction reader
        assertNotEquals(capacity, dynamicTable.capacity());

        // Check if size calculated by the EncoderInstructionsWriter matches
        // the number of bytes written to the byte buffers
        assertEquals(calculatedInstructionSize, bytesWritten);
    }

    @ParameterizedTest
    @MethodSource("nameReferenceInsertSource")
    public void insertWithNameReferenceInstructionTest(boolean referencingStatic,
                                                       long nameIndex, int byteBufferSize)
            throws Exception {
        // Get test-case specific logger and a dynamic table instance
        QPACK.Logger logger = QPACK.getLogger()
                .subLogger("insertWithNameReferenceInstructionTest");
        var dynamicTable = dynamicTable(logger);

        // generate random value String
        String value = randomString();
        TableEntry entry = new TableEntry(referencingStatic, nameIndex, "", value, NAME);

        // Create and configure encoder instruction writer for writing
        // the "Insert With Name Reference" instruction
        var writer = new EncoderInstructionsWriter();
        int calculatedInstructionSize = writer.configureForEntryInsertion(entry);

        // Perform write operation and then read back the insert entry instruction
        var callback = new TestEncoderInstructionsCallback(dynamicTable);
        int bytesWritten = writeThenReadInstruction(writer, callback, byteBufferSize,
                dynamicTable, (dt) -> true, logger);

        // Check that reader callback values match values supplied to the writer
        assertEquals(nameIndex, callback.lastNameInsert.index());
        assertEquals(value, callback.lastNameInsert.value());
        assertEquals(referencingStatic, callback.lastNameInsert.isStaticTable());

        // Check if size calculated by the EncoderInstructionsWriter matches
        // the number of bytes written to the byte buffers
        assertEquals(calculatedInstructionSize, bytesWritten);
    }

    private static Stream<Arguments> nameReferenceInsertSource() {
        Stream<Arguments> staticTableCases =
                RANDOM.longs(10, 0,
                                StaticTable.HTTP3_HEADER_FIELDS.size())
                        .boxed()
                        .map(index -> Arguments.of(true, index,
                                RANDOM.nextInt(1, 65)));
        Stream<Arguments> dynamicTableCases =
                RANDOM.longs(10, 0,
                                DT_NAMES.size())
                        .boxed()
                        .map(index -> Arguments.of(false, index,
                                RANDOM.nextInt(1, 65)));
        return Stream.concat(staticTableCases, dynamicTableCases);
    }

    @RepeatedTest(10)
    public void insertWithLiteralInstructionTest() throws Exception {
        // Get test-case specific logger and a dynamic table instance
        QPACK.Logger logger = QPACK.getLogger()
                .subLogger("insertWithLiteralInstructionTest");
        var dynamicTable = dynamicTable(logger);

        // Generate random strings for name:value entry
        String name = randomString();
        String value = randomString();
        var tableEntry = new TableEntry(name, value);
        // Create and configure encoder instruction writer for writing the "Insert With Literal Name"
        // instruction
        var writer = new EncoderInstructionsWriter();
        int calculatedInstructionSize = writer.configureForEntryInsertion(tableEntry);

        var callback = new TestEncoderInstructionsCallback(dynamicTable);
        int writtenBytes = writeThenReadInstruction(writer, callback, -1,
                dynamicTable, (dt) -> true, logger);

        // Check that reader callback values match values supplied to the writer
        assertEquals(name, callback.lastLiteralInsert.name());
        assertEquals(value, callback.lastLiteralInsert.value());
        // Check if size calculated by the EncoderInstructionsWriter matches the number of
        // bytes written to the byte buffers
        assertEquals(calculatedInstructionSize, writtenBytes);
    }

    @RepeatedTest(10)
    public void duplicateInstructionTest() throws Exception {
        // Get test-case specific logger and a dynamic table instance
        QPACK.Logger logger = QPACK.getLogger()
                .subLogger("duplicateInstructionTest");
        var dynamicTable = dynamicTable(logger);
        // Absolute id to duplicate
        // size() - 1 is used to not duplicate the last entry, otherwise the
        // DynamicTable.duplicate(relativeIndex) checks below will fail
        long idToDuplicate = RANDOM.nextLong(0, DT_NAMES.size() - 1);
        // Create and configure encoder instruction writer for writing the "Duplicate"
        // instruction
        var writer = new EncoderInstructionsWriter();
        // insert count - 1 is the head element index
        long relativeIndex = dynamicTable.insertCount() - 1 - idToDuplicate;
        int calculatedInstructionSize = writer.configureForEntryDuplication(relativeIndex);
        var callback = new TestEncoderInstructionsCallback(dynamicTable);
        int writtenBytes = writeThenReadInstruction(writer, callback, -1,
                dynamicTable, (dt) -> true, logger);
        HeaderField original = dynamicTable.get(idToDuplicate);
        HeaderField head = dynamicTable.getRelative(0);

        // Check that reader callback values match values supplied to the writer
        assertEquals(relativeIndex, callback.duplicateIdFromCallback.get());
        // Check that DynamicTable.duplicate(relativeIndex) properly recreates
        // the referenced entry
        assertEquals(head.name(), original.name());
        assertEquals(head.value(), original.value());
        // Check if size calculated by the EncoderInstructionsWriter matches the number
        // of bytes written to the byte buffers
        assertEquals(calculatedInstructionSize, writtenBytes);

    }

    // Test runner method that writes an instruction with the supplied encoder
    // instructions writer pre-configured for writing of a specific instruction.
    private static int writeThenReadInstruction(
            EncoderInstructionsWriter writer, TestEncoderInstructionsCallback callback,
            int bufferSize, DynamicTable dynamicTable,
            Function<DynamicTable, Boolean> partialWriteCheck,
            QPACK.Logger logger) throws Exception {
        var buffers = new ArrayList<ByteBuffer>();
        boolean writeDone = false;
        int writtenBytes = 0;
        // Write instruction to the list of byte buffers
        while (!writeDone) {
            int allocSize = bufferSize == -1 ? RANDOM.nextInt(1, 65) : bufferSize;
            var buffer = ByteBuffer.allocate(allocSize);
            writeDone = writer.write(buffer);
            writtenBytes += buffer.position();
            if (!writeDone && !partialWriteCheck.apply(dynamicTable)) {
                Assert.fail("Wrong dynamic table state after partial write");
            }
            buffer.flip();
            buffers.add(buffer);
        }

        // Read back the data from byte buffers
        var encoderInstructionReader = new EncoderInstructionsReader(callback, logger);

        // Read out an instruction and return the callback instance
        for (var bb : buffers) {
            encoderInstructionReader.read(bb, -1);
        }
        return writtenBytes;
    }

    private static final Random RANDOM = RandomFactory.getRandom();
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

    private static DynamicTable dynamicTable(QPACK.Logger logger) {
        var dt = new DynamicTable(logger.subLogger("dynamicTable"));
        dt.setMaxTableCapacity(4096);
        dt.setCapacity(4096);
        for (var name : DT_NAMES) {
            dt.insert(name, randomString());
        }
        return dt;
    }

    private static String randomString() {
        int lower = RANDOM.nextInt(LOREM.length() - TEST_STR_MAX_LENGTH);
        return LOREM.substring(lower, 1 + lower + RANDOM.nextInt(TEST_STR_MAX_LENGTH));
    }

    private static class TestEncoderInstructionsCallback implements Callback {

        final DynamicTable dynamicTable;
        public TestEncoderInstructionsCallback(DynamicTable dynamicTable) {
            this.dynamicTable = dynamicTable;
        }

        record LiteralInsert(String name, String value) {
        }

        record IndexedNameInsert(boolean isStaticTable, long index, String value) {
        }

        final AtomicLong capacityFromCallback = new AtomicLong(-1L);
        final AtomicLong duplicateIdFromCallback = new AtomicLong(-1L);
        LiteralInsert lastLiteralInsert;
        IndexedNameInsert lastNameInsert;

        @Override
        public void onCapacityUpdate(long capacity) {
            capacityFromCallback.set(capacity);
        }

        @Override
        public void onInsert(String name, String value) {
            lastLiteralInsert = new LiteralInsert(name, value);
        }

        @Override
        public void onInsertIndexedName(boolean indexInStaticTable, long nameIndex, String valueString) {
            lastNameInsert = new IndexedNameInsert(indexInStaticTable, nameIndex, valueString);
        }

        @Override
        public void onDuplicate(long l) {
            dynamicTable.duplicate(l);
            duplicateIdFromCallback.set(l);
        }
    }
}
