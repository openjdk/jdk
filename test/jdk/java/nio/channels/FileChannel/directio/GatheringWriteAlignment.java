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
 * @summary Test alignment of gathering writes with direct I/O near WRITEV_MAX
 * @requires (os.family == "linux" | os.family == "mac")
 * @requires vm.bits == 64 & os.maxMemory >= 8G
 * @modules jdk.unsupported
 * @run junit/othervm/timeout=480 -Xmx4G -XX:MaxDirectMemorySize=3G ${test.main.class}
 */

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import static java.nio.file.StandardOpenOption.*;
import static com.sun.nio.file.ExtendedOpenOption.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GatheringWriteAlignment {

    private static Path file;
    private static int alignment;

    @BeforeAll
    static void setup() throws Exception {
        file = Files.createTempFile(Path.of("."), "test", null);
        alignment = (int) Files.getFileStore(file).getBlockSize();
    }

    @AfterAll
    static void cleanup() throws Exception {
        Files.delete(file);
    }

    /**
     * Tests that a gathering write with direct I/O writes from only the first
     * buffer when the remaining headroom is smaller than one alignment unit.
     */
    @Test
    void testOnlyFirstBufferFits() throws Exception {
        try (FileChannel fc = FileChannel.open(file, WRITE, DIRECT);
             Arena arena = Arena.ofConfined()) {

            // largest aligned length below WRITEV_MAX
            int firstBufferSize = Integer.MAX_VALUE - (Integer.MAX_VALUE % alignment);
            ByteBuffer firstBuffer = arena.allocate(firstBufferSize, alignment).asByteBuffer();

            // second buffer can not fit in the remaining headroom
            ByteBuffer secondBuffer = ByteBuffer.allocate(alignment);

            long written = fc.write(new ByteBuffer[]{firstBuffer, secondBuffer});

            assertTrue(written > 0);
            assertEquals(written, (long) firstBuffer.position());
            assertEquals(0, firstBuffer.position() % alignment);
            assertEquals(0, secondBuffer.position());
        }
    }

    /**
     * Tests that a gathering write with direct I/O limits writes from the second
     * buffer to alignment units when only part of it fits within the remaining
     * headroom.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testOnlyPartOfSecondBufferFits(boolean direct) throws Exception {
        try (FileChannel fc = FileChannel.open(file, WRITE, DIRECT);
             Arena arena = Arena.ofConfined()) {

            // leave 2 * alignment - 1 bytes of headroom
            int firstBufferSize = Integer.MAX_VALUE - (Integer.MAX_VALUE % alignment) - alignment;
            ByteBuffer firstBuffer = arena.allocate(firstBufferSize, alignment).asByteBuffer();

            // only one alignment unit of this buffer should fit
            ByteBuffer secondBuffer = direct
                    ? arena.allocate(2L * alignment, alignment).asByteBuffer()
                    : ByteBuffer.allocate(2 * alignment);

            long written = fc.write(new ByteBuffer[]{firstBuffer, secondBuffer});

            assertTrue(written > 0);
            assertEquals(0L, written % alignment);
            assertEquals(written, (long) firstBuffer.position() + secondBuffer.position());
            assertEquals((int) Math.min(written, firstBufferSize), firstBuffer.position());
            assertEquals(0, secondBuffer.position() % alignment);
            assertTrue(secondBuffer.position() <= alignment);
        }
    }

    /**
     * Tests that a gathering write with direct I/O writes from only the first two
     * buffers when the remaining headroom is smaller than one alignment unit.
     */
    @Test
    void testOnlyFirstTwoBuffersFit() throws Exception {
        try (FileChannel fc = FileChannel.open(file, WRITE, DIRECT);
             Arena arena = Arena.ofConfined()) {

            // leave room for one aligned buffer, plus alignment - 1 bytes
            int firstBufferSize = Integer.MAX_VALUE - (Integer.MAX_VALUE % alignment) - alignment;
            ByteBuffer firstBuffer = arena.allocate(firstBufferSize, alignment).asByteBuffer();
            ByteBuffer secondBuffer = ByteBuffer.allocate(alignment);
            ByteBuffer thirdBuffer = ByteBuffer.allocate(alignment);

            long written = fc.write(new ByteBuffer[]{firstBuffer, secondBuffer, thirdBuffer});

            assertTrue(written > 0);
            assertEquals(0L, written % alignment);
            assertEquals(written, (long) firstBuffer.position() + secondBuffer.position());
            assertEquals((int) Math.min(written, firstBufferSize), firstBuffer.position());
            assertEquals(0, secondBuffer.position() % alignment);
            assertEquals(0, thirdBuffer.position());
        }
    }
}
