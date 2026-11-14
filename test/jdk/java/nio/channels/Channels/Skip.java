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
 * @bug 8394316
 * @summary Test Channels.newInputStream skip method
 * @run junit ${test.main.class}
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class Skip {

    /**
     * Supplies (file) sizes, positions and skip values to test.
     */
    static Stream<Arguments> testCases() {
        return IntStream.of(0, 1, 100)    // sizes
                .boxed()
                .flatMap(size -> {
                    long[] positions = {
                            0,
                            1,
                            2,
                            size - 1L,
                            size,
                            size + 1L,
                            Integer.MAX_VALUE - 1,
                            Integer.MAX_VALUE
                    };
                    long[] skips = {
                            Long.MIN_VALUE,
                            Long.MIN_VALUE + 1,
                            -size - 1,
                            -size,
                            -size + 1,
                            -2,
                            -1,
                            0,
                            1,
                            2,
                            size - 1,
                            size,
                            size + 1,
                            Long.MAX_VALUE - 1,
                            Long.MAX_VALUE
                    };
                    return Arrays.stream(positions)
                            .filter(pos -> pos >= 0)
                            .distinct()
                            .boxed()
                            .flatMap(position -> Arrays.stream(skips)
                                    .distinct()
                                    .mapToObj(skip -> Arguments.of(size, position, skip)));
                });
    }


    /**
     * Test InputStream.skip when input stream reads from a SeekableByteChannel.
     */
    private static void testSkip(SeekableByteChannel ch, long skip) throws Exception {
        InputStream in = Channels.newInputStream(ch);
        long pos = ch.position();
        long expectSkip = (skip >= 0)
                ? Long.min(skip, Long.max(ch.size() - pos, 0L))
                : Long.max(-pos, skip);
        assertEquals(expectSkip, in.skip(skip), "skip");
        assertEquals(pos + expectSkip, ch.position(), "position");
    }

    /**
     * Test InputStream.skip when input stream reads from a FileChannel.
     */
    @ParameterizedTest
    @MethodSource("testCases")
    void testFileChannel(int size, long pos, long skip) throws Exception {
        Path file = Files.createTempFile(Path.of("."), "blah", "dat");
        try {
            Files.write(file, new byte[size]);
            try (FileChannel fc = FileChannel.open(file)) {
                fc.position(pos);
                testSkip(fc, skip);
            }
        } finally {
            Files.delete(file);
        }
    }

    /**
     * Test InputStream.skip when input stream reads from a SeekableByteChannel.
     */
    @ParameterizedTest
    @MethodSource("testCases")
    void testSeekableByteChannel(int size, long pos, long skip) throws Exception {
        byte[] bytes = "X".repeat(size).getBytes("UTF-8");

        var ch = new SeekableByteChannel() {
            int offset;
            @Override
            public int read(ByteBuffer dst) {
                if (!dst.hasRemaining())
                    return 0;
                if (offset >= size)
                    return -1;
                int n = (int) Long.min(dst.remaining(), size - offset);
                dst.put(bytes, offset, n);
                offset += n;
                return n;
            }
            @Override
            public int write(ByteBuffer src) {
                throw new NonWritableChannelException();
            }
            @Override
            public long position() {
                return offset;
            }
            @Override
            public SeekableByteChannel position(long newPosition) {
                if (newPosition < 0 || newPosition > Integer.MAX_VALUE)
                    throw new IllegalArgumentException();
                offset = (int) newPosition;
                return this;
            }
            @Override
            public long size() {
                return size;
            }
            @Override
            public SeekableByteChannel truncate(long size) throws IOException {
                throw new IOException();
            }
            @Override
            public boolean isOpen() {
                return true;
            }
            @Override
            public void close() throws IOException {
                throw new IOException();
            }
        };

        ch.position(pos);
        testSkip(ch, skip);
    }

    /**
     * Test InputStream.skip when input stream reads from a ReadableByteChannel.
     */
    @Test
    void testReadableByteChannel() throws Exception {
        final int size = 10;
        byte[] bytes = "X".repeat(size).getBytes("UTF-8");

        var ch = new ReadableByteChannel() {
            private final ByteBuffer src = ByteBuffer.wrap(bytes);
            @Override
            public int read(ByteBuffer dst) {
                if (!dst.hasRemaining())
                    return 0;
                if (!src.hasRemaining())
                    return -1;
                int n = Math.min(src.remaining(), dst.remaining());
                dst.put(src.array(), src.position(), n);
                src.position(src.position() + n);
                return n;
            }
            @Override
            public boolean isOpen() {
                return true;
            }
            @Override
            public void close() throws IOException {
                throw new IOException();
            }
        };

        InputStream in = Channels.newInputStream(ch);
        assertEquals(0L, in.skip(-1));
        assertEquals(0L, in.skip(0));

        int remaining = size;
        for (int n = 1; n <= 3; n++) {
            assertEquals(n, in.skip(n));
            remaining -= n;
        }
        assertTrue(remaining >= 0);
        assertEquals(remaining, in.skip(100));
        assertEquals(0, in.skip(100));
    }

    /**
     * Test InputStream.skip when input stream reads from a socket.
     * @param in input stream that reads from a socket
     * @param peer output stream of connected peer
     */
    void testSocketInputStreamSkip(InputStream in, OutputStream peer) throws Exception {
        assertEquals(0L, in.skip(-1));
        assertEquals(0L, in.skip(0));

        final int count = 10;
        peer.write("X".repeat(count).getBytes("UTF-8"));

        assertEquals(0L, in.skip(-1));
        assertEquals(0L, in.skip(0));

        // consume bytes
        int remaining = count;
        for (int n = 1; n <= 3; n++) {
            assertEquals(n, in.skip(n));
            remaining -= n;
        }
        assertTrue(remaining >= 0);

        assertEquals(0L, in.skip(0));

        peer.close();

        assertEquals(remaining, in.skip(100));
        assertEquals(0, in.skip(100));
    }

    /**
     * Test InputStream.skip for input stream that reads from a socket channel.
     */
    @Test
    void testSocketChannel() throws Exception {
        try (var connection = new Connection()) {
            InputStream in = Channels.newInputStream(connection.channel1());
            OutputStream out = Channels.newOutputStream(connection.channel2());
            testSocketInputStreamSkip(in, out);
        }
    }

    /**
     * Test InputStream.skip for input stream that reads from a socket associated
     * with a socket channel.
     */
    @Test
    void testSocketChannelAdaptor() throws Exception {
        try (var connection = new Connection()) {
            Socket s1 = connection.channel1().socket();
            Socket s2 = connection.channel2().socket();
            testSocketInputStreamSkip(s1.getInputStream(), s2.getOutputStream());
        }
    }

    /**
     * A loopback connection.
     */
    private static class Connection implements Closeable {
        private final SocketChannel sc1;
        private final SocketChannel sc2;
        Connection() throws IOException {
            var lh = InetAddress.getLoopbackAddress();
            try (var listener = ServerSocketChannel.open()) {
                listener.bind(new InetSocketAddress(lh, 0));
                SocketChannel sc1 = SocketChannel.open();
                SocketChannel sc2 = null;
                try {
                    sc1.socket().connect(listener.getLocalAddress());
                    sc2 = listener.accept();
                } catch (IOException ioe) {
                    sc1.close();
                    throw ioe;
                }
                this.sc1 = sc1;
                this.sc2 = sc2;
            }
        }
        SocketChannel channel1() {
            return sc1;
        }
        SocketChannel channel2() {
            return sc2;
        }
        @Override
        public void close() throws IOException {
            sc1.close();
            sc2.close();
        }
    }
}
