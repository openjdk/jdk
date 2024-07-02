/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8333849
 * @summary Test ByteChannel implementations of read and write with ByteBuffers that are
 *    backed by MemorySegments allocated from an Arena
 * @run junit MemorySegments
 */

import java.io.IOException;
import java.lang.foreign.Arena;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static java.nio.file.StandardOpenOption.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class MemorySegments {
    private static final int SIZE = 100;   // buffer size used by tests

    /**
     * Return a stream of suppliers for each Arena type. A supplier is used to avoid JUnit
     * closing the Arena and failing (as some Arenas are not closable).
     */
    static Stream<Supplier<Arena>> arenaSuppliers() {
        return Stream.of(Arena::global, Arena::ofAuto, Arena::ofConfined, Arena::ofShared);
    }

    /**
     * SocketChannel read/write(ByteBuffer).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testSocketChannelReadWrite(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
            ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            try (SocketChannel sc1 = SocketChannel.open(ssc.getLocalAddress());
                 SocketChannel sc2 = ssc.accept()) {

                // write
                ByteBuffer src = arena.allocate(SIZE).asByteBuffer();
                fillRandom(src);
                int nwritten = sc1.write(src);
                assertTrue(nwritten > 0);
                assertTrue(nwritten <= SIZE);
                assertEquals(nwritten, src.position());

                // read
                ByteBuffer dst = arena.allocate(SIZE + 100).asByteBuffer();
                int nread = sc2.read(dst);
                assertTrue(nread > 0);
                assertTrue(nread <= nwritten);
                assertEquals(nread, dst.position());

                // check contents
                dst.flip();
                assertEquals(src.slice(0, nread), dst);
            }

        } finally {
            tryClose(arena);
        }
    }

    /**
     * SocketChannel write(ByteBuffer[]).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testSocketChannelGatheringWrite(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
            ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            try (SocketChannel sc1 = SocketChannel.open(ssc.getLocalAddress());
                 SocketChannel sc2 = ssc.accept()) {

                // gathering write
                ByteBuffer src = arena.allocate(SIZE * 2).asByteBuffer();
                fillRandom(src);
                ByteBuffer src1 = src.slice(0, SIZE);
                ByteBuffer src2 = src.slice(SIZE, SIZE);
                var srcs = new ByteBuffer[] { src1, src2 };
                int nwritten = (int) sc1.write(srcs);
                assertTrue(nwritten > 0);
                assertEquals(Math.min(nwritten, SIZE), src1.position());
                assertEquals(nwritten, src1.position() + src2.position());

                // read
                ByteBuffer dst = arena.allocate(SIZE * 2 + 50).asByteBuffer();
                int nread = sc2.read(dst);
                assertTrue(nread > 0);
                assertTrue(nread <= nwritten);
                assertEquals(nread, dst.position());

                // check contents
                dst.flip();
                assertEquals(src.slice(0, nread), dst);
            }

        } finally {
            tryClose(arena);
        }
    }

    /**
     * SocketChannel read(ByteBuffer[]).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testSocketChannelScatteringRead(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        try (ServerSocketChannel ssc = ServerSocketChannel.open()) {
            ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            try (SocketChannel sc1 = SocketChannel.open(ssc.getLocalAddress());
                 SocketChannel sc2 = ssc.accept()) {

                // write
                ByteBuffer src = arena.allocate(SIZE).asByteBuffer();
                fillRandom(src);
                int nwritten = sc1.write(src);
                assertTrue(nwritten > 0);
                assertTrue(nwritten <= SIZE);
                assertEquals(nwritten, src.position());

                // scattering read
                ByteBuffer dst = arena.allocate(SIZE + 50).asByteBuffer();
                ByteBuffer dst1 = dst.slice(0, 50);
                ByteBuffer dst2 = dst.slice(50, dst.capacity() - 50);
                var dsts = new ByteBuffer[]{ dst1, dst2 };
                int nread = (int) sc2.read(dsts);
                assertTrue(nread > 0);
                assertTrue(nread <= nwritten);
                assertTrue(dst1.position() > 0);
                assertEquals(nread, dst1.position() + dst2.position());

                // check contents
                src.flip();
                assertEquals(src, dst.slice(0, nread));
            }

        } finally {
            tryClose(arena);
        }
    }

    /**
     * DatagramChannel send/receive(ByteBuffer).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testDatagramChannelSendReceive(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        try (DatagramChannel dc = DatagramChannel.open()) {
            dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            SocketAddress target = dc.getLocalAddress();

            // send
            ByteBuffer src = arena.allocate(SIZE).asByteBuffer();
            fillRandom(src);
            int n = dc.send(src, target);
            assertEquals(SIZE, n);
            assertFalse(src.hasRemaining());

            // receive
            ByteBuffer dst = arena.allocate(SIZE + 100).asByteBuffer();
            SocketAddress remote = dc.receive(dst);
            assertEquals(remote, target);
            assertEquals(SIZE, dst.position());

            // check contents
            src.clear();
            dst.flip();
            assertEquals(src, dst);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * DatagramChannel read/write(ByteBuffer).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testDatagramChannelReadWrite(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        try (DatagramChannel dc = DatagramChannel.open()) {
            dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            SocketAddress target = dc.getLocalAddress();
            dc.connect(target);

            // write
            ByteBuffer src = arena.allocate(SIZE).asByteBuffer();
            fillRandom(src);
            int n = dc.write(src);
            assertEquals(SIZE, n);
            assertFalse(src.hasRemaining());

            // read
            ByteBuffer dst = arena.allocate(SIZE + 100).asByteBuffer();
            n = dc.read(dst);
            assertEquals(SIZE, n);
            assertEquals(SIZE, dst.position());

            // check contents
            src.clear();
            dst.flip();
            assertEquals(src, dst);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * DatagramChannel write(ByteBuffer[]).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testDatagramChannelGatheringWrite(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        try (DatagramChannel dc = DatagramChannel.open()) {
            dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            SocketAddress target = dc.getLocalAddress();
            dc.connect(target);

            // gathering write
            ByteBuffer src1 = arena.allocate(SIZE).asByteBuffer();
            ByteBuffer src2 = arena.allocate(SIZE).asByteBuffer();
            fillRandom(src1);
            fillRandom(src2);
            var srcs = new ByteBuffer[] { src1, src2 };
            int nwritten = (int) dc.write(srcs);
            assertEquals(SIZE*2, nwritten);
            assertFalse(src1.hasRemaining());
            assertFalse(src2.hasRemaining());

            // read
            ByteBuffer dst = arena.allocate(SIZE*2 + 50).asByteBuffer();
            int nread = dc.read(dst);
            assertEquals(SIZE*2, nread);
            assertEquals(SIZE*2, dst.position());

            // check contents
            src1.flip();
            src2.flip();
            assertEquals(src1, dst.slice(0, SIZE));
            assertEquals(src2, dst.slice(SIZE, SIZE));
        } finally {
            tryClose(arena);
        }
    }

    /**
     * DatagramChannel read(ByteBuffer[]).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testDatagramChannelScatteringRead(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        try (DatagramChannel dc = DatagramChannel.open()) {
            dc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            SocketAddress target = dc.getLocalAddress();
            dc.connect(target);

            // write
            ByteBuffer src = arena.allocate(SIZE*2).asByteBuffer();
            fillRandom(src);
            int nwritten = dc.write(src);
            assertEquals(SIZE*2, nwritten);
            assertEquals(nwritten, src.position());

            // scattering read
            ByteBuffer dst1 = arena.allocate(SIZE).asByteBuffer();
            ByteBuffer dst2 = arena.allocate(SIZE + 50).asByteBuffer();
            var dsts = new ByteBuffer[] { dst1, dst2 };
            int nread = (int) dc.read(dsts);
            assertEquals(SIZE*2, nread);
            assertEquals(nread, dst1.position() + dst2.position());

            // check contents
            dst1.flip();
            assertEquals(src.slice(0, SIZE), dst1);
            dst2.flip();
            assertEquals(src.slice(SIZE, SIZE), dst2);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * FileChannel read/write(ByteBuffer).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testFileChannelReadWrite(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        Path file = Files.createTempFile(Path.of("."), "test", "dat");
        try (FileChannel fc = FileChannel.open(file, READ, WRITE)) {

            // write
            ByteBuffer src = arena.allocate(SIZE).asByteBuffer();
            fillRandom(src);
            int nwritten = fc.write(src);
            assertTrue(nwritten > 0);
            assertTrue(nwritten <= SIZE);
            assertEquals(nwritten, src.position());
            assertEquals(nwritten, (int) fc.position());
            assertEquals(nwritten, (int) fc.size());

            // read
            ByteBuffer dst = arena.allocate(SIZE + 100).asByteBuffer();
            fc.position(0);
            int nread = fc.read(dst);
            assertTrue(nread > 0);
            assertTrue(nread <= nwritten);
            assertEquals(nread, dst.position());
            assertEquals(nread, (int) fc.position());

            // check contents
            dst.flip();
            assertEquals(src.slice(0, nread), dst);

            // reset
            fc.truncate(0L);
            src.clear();
            dst.clear();

            // write with position
            nwritten = fc.write(src, 10L);
            assertTrue(nwritten > 0);
            assertTrue(nwritten <= SIZE);
            assertEquals(nwritten, src.position());
            assertEquals(0, (int) fc.position());
            assertEquals(nwritten + 10, (int) fc.size());

            // read with position
            nread = fc.read(dst, 10L);
            assertTrue(nread > 0);
            assertTrue(nread <= nwritten);
            assertEquals(nread, dst.position());
            assertEquals(0, (int)fc.position());

            // check contents
            dst.flip();
            assertEquals(src.slice(0, nread), dst);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * FileChannel write(ByteBuffer[]).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testFileChannelGatheringWrite(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        Path file = Files.createTempFile(Path.of(""), "test", "dat");
        try (FileChannel fc = FileChannel.open(file, READ, WRITE)) {

            // gathering write
            ByteBuffer src = arena.allocate(SIZE * 2).asByteBuffer();
            fillRandom(src);
            ByteBuffer src1 = src.slice(0, SIZE);
            ByteBuffer src2 = src.slice(SIZE, SIZE);
            var srcs = new ByteBuffer[] { src1, src2 };
            int nwritten = (int) fc.write(srcs);
            assertTrue(nwritten > 0);
            assertEquals(Math.min(nwritten, SIZE), src1.position());
            assertEquals(nwritten, src1.position() + src2.position());
            assertEquals(nwritten, (int) fc.position());
            assertEquals(nwritten, (int) fc.size());

            // read
            ByteBuffer dst = arena.allocate(SIZE*2 + 50).asByteBuffer();
            fc.position(0);
            int nread = fc.read(dst);
            assertTrue(nread > 0);
            assertTrue(nread <= nwritten);
            assertEquals(nread, dst.position());
            assertEquals(nread, (int) fc.position());

            // check contents
            dst.flip();
            assertEquals(src.slice(0, nread), dst);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * FileChannel read(ByteBuffer[]).
     */
    @ParameterizedTest
    @MethodSource("arenaSuppliers")
    void testFileChannelScatteringRead(Supplier<Arena> arenaSupplier) throws IOException {
        Arena arena = arenaSupplier.get();
        Path file = Files.createTempFile(Path.of(""), "test", "dat");
        try (FileChannel fc = FileChannel.open(file, READ, WRITE)) {

            // write
            ByteBuffer src = arena.allocate(SIZE).asByteBuffer();
            fillRandom(src);
            int nwritten = fc.write(src);
            assertTrue(nwritten > 0);
            assertTrue(nwritten <= SIZE);
            assertEquals(nwritten, src.position());
            assertEquals(nwritten, (int) fc.position());
            assertEquals(nwritten, (int) fc.size());

            // scattering read
            ByteBuffer dst = arena.allocate(SIZE + 50).asByteBuffer();
            ByteBuffer dst1 = dst.slice(0, 50);
            ByteBuffer dst2 = dst.slice(50, dst.capacity() - 50);
            var dsts = new ByteBuffer[] { dst1, dst2 };
            fc.position(0);
            int nread = (int) fc.read(dsts);
            assertTrue(nread > 0);
            assertTrue(nread <= nwritten);
            assertTrue(dst1.position() > 0);
            assertEquals(nread, dst1.position() + dst2.position());
            assertEquals(nread, (int) fc.position());

            // check contents
            dst.limit(nread);
            assertEquals(src.slice(0, nread), dst);
        } finally {
            tryClose(arena);
        }
    }

    /**
     * Fill the buffer with random bytes.
     */
    private void fillRandom(ByteBuffer bb) {
        Random r = new Random();
        int pos = bb.position();
        while (bb.hasRemaining()) {
            bb.put((byte) r.nextInt(256));
        }
        bb.position(pos);
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
