/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8392944
 * @summary Test FileChannel direct I/O with misaligned file position or buffers
 * @modules jdk.unsupported
 * @run junit ${test.main.class}
 */

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static java.nio.file.StandardOpenOption.*;
import static com.sun.nio.file.ExtendedOpenOption.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class Misaligned {

    private static Path file;
    private static int alignment;

    @BeforeAll
    static void setup() throws Exception {
        file = Files.createTempFile(Path.of("."), "test", null);
        alignment = (int) Files.getFileStore(file).getBlockSize();
        Files.write(file, new byte[alignment * 4]);
    }

    @AfterAll
    static void cleanup() throws Exception {
        Files.delete(file);
    }

    /**
     * Return a stream of suppliers for each Arena type. A supplier is used to avoid JUnit
     * closing the Arena and failing (as some Arenas are not closable).
     */
    static Stream<Supplier<Arena>> arenaSuppliers() {
        return Stream.of(Arena::global, Arena::ofAuto, Arena::ofConfined, Arena::ofShared);
    }

    /**
     * Test exception thrown when file position is misaligned.
     */
    private void testMisalignedFilePosition(ByteBuffer bb) throws Exception {
        // check buffer is not empty and is suitably aligned
        assertTrue(bb.hasRemaining());
        assertEquals(0, bb.remaining() % alignment);
        if (bb.isDirect()) {
            assertEquals(0, bb.alignmentOffset(bb.position(), alignment));
            assertEquals(0, bb.alignmentOffset(bb.limit(), alignment));
        }

        try (FileChannel fc = FileChannel.open(file, READ, WRITE, DIRECT)) {
            fc.position(1L);  // misaligned file position
            assertThrows(IOException.class, () -> fc.read(bb));
            assertThrows(IOException.class, () -> fc.write(bb));
            assertThrows(IOException.class, () -> fc.read(new ByteBuffer[]{bb}));
            assertThrows(IOException.class, () -> fc.write(new ByteBuffer[]{bb}));
            assertThrows(IOException.class, () -> fc.read(bb, 1L));
            assertThrows(IOException.class, () -> fc.write(bb, 1L));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testMisalignedFilePosition(boolean direct) throws Exception {
        ByteBuffer bb = direct
                ? ByteBuffer.allocateDirect(3 * alignment - 1).alignedSlice(alignment)
                : ByteBuffer.allocate(3 * alignment);
        testMisalignedFilePosition(bb);
    }

    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testMisalignedFilePosition(Supplier<Arena> arenaSupplier) throws Exception {
        Arena arena = arenaSupplier.get();
        try {
            ByteBuffer bb = arena.allocate(3L * alignment, alignment).asByteBuffer();
            testMisalignedFilePosition(bb);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * Test exception thrown when the buffer address is misaligned.
     */
    private void testMisalignedAddress(ByteBuffer bb) throws Exception {
        // check buffer is direct with an aligned base address
        assertTrue(bb.isDirect());
        assertEquals(0, bb.alignmentOffset(0, alignment));

        // need sufficient capacity for an aligned region starting at position 1
        assertTrue(bb.capacity() >= alignment + 1);

        try (FileChannel fc = FileChannel.open(file, READ, WRITE, DIRECT)) {
            bb.position(1);  // misaligned address
            bb.limit(alignment + 1);

            assertNotEquals(0, bb.alignmentOffset(bb.position(), alignment));
            assertEquals(0, bb.remaining() % alignment);

            assertThrows(IOException.class, () -> fc.read(bb));
            assertThrows(IOException.class, () -> fc.write(bb));
            assertThrows(IOException.class, () -> fc.read(new ByteBuffer[]{bb}));
            assertThrows(IOException.class, () -> fc.write(new ByteBuffer[]{bb}));
            assertThrows(IOException.class, () -> fc.read(bb, 0L));
            assertThrows(IOException.class, () -> fc.write(bb, 0L));
        }
    }

    @Test
    void testMisalignedAddress() throws Exception {
        ByteBuffer bb = ByteBuffer.allocateDirect(3 * alignment - 1)
                .alignedSlice(alignment);
        testMisalignedAddress(bb);
    }

    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testMisalignedAddress(Supplier<Arena> arenaSupplier) throws Exception {
        Arena arena = arenaSupplier.get();
        try {
            ByteBuffer bb = arena.allocate(3L * alignment, alignment).asByteBuffer();
            testMisalignedAddress(bb);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * Test exception thrown when the remaining length is not a multiple of the alignment.
     */
    private void testMisalignedRemaining(ByteBuffer bb) throws Exception {
        assertEquals(0, bb.position());
        assertTrue(bb.capacity() >= alignment + 1);
        if (bb.isDirect()) {
            assertEquals(0, bb.alignmentOffset(0, alignment));
        }

        try (FileChannel fc = FileChannel.open(file, READ, WRITE, DIRECT)) {
            bb.limit(alignment - 1);  // remaining is 1 byte below an alignment
            assertThrows(IOException.class, () -> fc.read(bb));
            assertThrows(IOException.class, () -> fc.write(bb));
            assertThrows(IOException.class, () -> fc.read(new ByteBuffer[]{bb}));
            assertThrows(IOException.class, () -> fc.write(new ByteBuffer[]{bb}));
            assertThrows(IOException.class, () -> fc.read(bb, 0L));
            assertThrows(IOException.class, () -> fc.write(bb, 0L));

            bb.limit(alignment + 1);  // remaining is 1 byte beyond an alignment
            assertThrows(IOException.class, () -> fc.read(bb));
            assertThrows(IOException.class, () -> fc.write(bb));
            assertThrows(IOException.class, () -> fc.read(new ByteBuffer[]{bb}));
            assertThrows(IOException.class, () -> fc.write(new ByteBuffer[]{bb}));
            assertThrows(IOException.class, () -> fc.read(bb, 0L));
            assertThrows(IOException.class, () -> fc.write(bb, 0L));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testMisalignedRemaining(boolean direct) throws Exception {
        ByteBuffer bb = direct
                ? ByteBuffer.allocateDirect(3 * alignment - 1).alignedSlice(alignment)
                : ByteBuffer.allocate(3 * alignment);
        testMisalignedRemaining(bb);
    }

    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testMisalignedRemaining(Supplier<Arena> arenaSupplier) throws Exception {
        Arena arena = arenaSupplier.get();
        try {
            ByteBuffer bb = arena.allocate(3L * alignment, alignment).asByteBuffer();
            testMisalignedRemaining(bb);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * Attempt to close the given Arena.
     */
    private boolean tryClose(Arena arena) {
        try {
            arena.close();
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }
}
