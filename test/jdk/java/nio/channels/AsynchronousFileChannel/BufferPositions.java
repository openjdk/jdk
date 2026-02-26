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
 * @bug 8374382
 * @summary Test AsynchronousFileChannel.read/write with ByteBuffers and varied buffer positions
 * @run junit/othervm BufferPositions
 */

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import static java.nio.file.StandardOpenOption.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class BufferPositions {

    private static final int BUF_SIZE = 32;

    /**
     * The buffers to test.
     */
    static Stream<ByteBuffer> buffers() {
        List<ByteBuffer> buffers = List.of(
                ByteBuffer.allocate(BUF_SIZE),
                ByteBuffer.allocateDirect(BUF_SIZE),
                ByteBuffer.wrap(new byte[BUF_SIZE]),
                Arena.global().allocate(BUF_SIZE).asByteBuffer(),
                Arena.ofAuto().allocate(BUF_SIZE).asByteBuffer(),
                Arena.ofShared().allocate(BUF_SIZE).asByteBuffer()
        );
        Stream<ByteBuffer> slices = buffers.stream()
                .map(bb -> bb.slice(1, bb.capacity() - 1));
        return Stream.concat(buffers.stream(), slices);
    }

    /**
     * Test using an AsynchronousFileChannel to read bytes into the given buffer.
     */
    @ParameterizedTest
    @MethodSource("buffers")
    void testRead(ByteBuffer bb) throws Exception {
        Path file = Files.createTempFile(Path.of("."), "test", "dat");

        // populate temp file
        int size = bb.capacity();
        byte[] contents = new byte[size];
        for (int i = 0; i < size; i++) {
            contents[i] = (byte)(i + 1);
        }
        Files.write(file, contents);

        // read bytes using the buffer as the destination and all possible buffer positions
        try (AsynchronousFileChannel ch = AsynchronousFileChannel.open(file, READ)) {
            for (int filePosition = 0; filePosition < size; filePosition++) {
                for (int bufPos = 0; bufPos < size; bufPos++) {
                    // read one byte
                    bb.position(bufPos);
                    bb.limit(bufPos + 1);
                    int n = read(ch, filePosition, bb);
                    assertEquals(1, n);
                    assertEquals(bufPos + 1, bb.position());
                    assertEquals(contents[filePosition], bb.get(bufPos));
                }
            }
        }
    }

    /**
     * Test using an AsynchronousFileChannel to write bytes from the given buffer.
     */
    @ParameterizedTest
    @MethodSource("buffers")
    void testWrite(ByteBuffer bb) throws Exception {
        Path file = Files.createTempFile(Path.of("."), "test", "dat");

        // populate temp file, all zeros
        int size = bb.capacity();
        byte[] contents = new byte[size];
        Files.write(file, contents);

        // write bytes using the buffer as the source and all possible buffer positions
        try (AsynchronousFileChannel ch = AsynchronousFileChannel.open(file, READ, WRITE)) {
            for (int filePosition = 0; filePosition < size; filePosition++) {
                for (int bufPos = 0; bufPos < size; bufPos++) {
                    // write one byte
                    byte b = (byte) ThreadLocalRandom.current().nextInt();
                    bb.position(bufPos);
                    bb.limit(bufPos + 1);
                    bb.put(bufPos, b);
                    int n = write(ch, filePosition, bb);
                    assertEquals(1, n);
                    assertEquals(bufPos + 1, bb.position());
                    assertEquals(b, bb.get(bufPos));

                    // check byte was written at the expected file position
                    ByteBuffer dst = ByteBuffer.allocate(1);
                    int nread = ch.read(dst, filePosition).get();
                    assertEquals(1, nread);
                    assertEquals(b, dst.get(0));
                }
            }
        }
    }

    /**
     * Reads a byte from a channel into the given buffer, at the given file position.
     */
    private int read(AsynchronousFileChannel ch, long position, ByteBuffer bb) throws Exception {
        if (ThreadLocalRandom.current().nextBoolean()) {
            return ch.read(bb, position).get();
        } else {
            var handler = new Handler<Integer>();
            ch.read(bb, position, null, handler);
            return handler.result();
        }
    }

    /**
     * Writes a byte to a channel from the given buffer, at the given file position.
     */
    private int write(AsynchronousFileChannel ch, long position, ByteBuffer bb) throws Exception {
        if (ThreadLocalRandom.current().nextBoolean()) {
            return ch.write(bb, position).get();
        } else {
            var handler = new Handler<Integer>();
            ch.write(bb, position, null, handler);
            return handler.result();
        }
    }

    /**
     * CompletionHandler that defines a method to await the result of an I/O operation.
     */
    private static class Handler<T> implements CompletionHandler<T, Void> {
        T result;
        Throwable ex;
        final CountDownLatch done = new CountDownLatch(1);

        @Override
        public void completed(T result, Void att) {
            this.result = result;
            done.countDown();
        }

        @Override
        public void failed(Throwable ex, Void att) {
            this.ex = ex;
            done.countDown();
        }

        T result() throws InterruptedException {
            done.await();
            assertNull(ex);
            return result;
        }
    }
}
