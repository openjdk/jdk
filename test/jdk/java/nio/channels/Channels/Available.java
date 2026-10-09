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
 * @bug 6884800 8392974
 * @summary Test Channels.newInputStream available method
 * @run junit ${test.main.class}
 */

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SeekableByteChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class Available {

    /**
     * Test InputStream.available when input stream reads from a SeekableByteChannel.
     */
    void testSeekableByteChannel(SeekableByteChannel ch) throws Exception {
        InputStream in = Channels.newInputStream(ch);
        long size = ch.size();
        long[] positions = new long[] {
                0L,
                size - 1L,
                size,
                size + 1L,
                Integer.MAX_VALUE - size - 1L,
                Integer.MAX_VALUE - size,
                Integer.MAX_VALUE - size + 1L,
                Integer.MAX_VALUE - 1L,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE + 1L,
                Long.MAX_VALUE - size - 1L,
                Long.MAX_VALUE - size,
                Long.MAX_VALUE - size + 1L,
                Long.MAX_VALUE - 1L,
                Long.MAX_VALUE
        };
        for (long pos : positions) {
            if (pos >= 0) {
                ch.position(pos);
                int expectedAvailable = Math.clamp(size - pos, 0, Integer.MAX_VALUE);
                int available = in.available();
                System.err.format("  position = %d, available = %d%n", pos, available);
                assertEquals(expectedAvailable, available);
            }
        }
    }

    /**
     * Test InputStream.available when input stream reads from a FileChannel.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 100 })
    void testFileChannel(int size) throws Exception {
        Path file = Files.createTempFile(Path.of("."), "blah", "dat");
        try {
            Files.write(file, new byte[size]);
            try (FileChannel fc = FileChannel.open(file)) {
                testSeekableByteChannel(fc);
            }
        } finally {
            Files.delete(file);
        }
    }

    /**
     * Test InputStream.available when input stream reads from a SeekableByteChannel.
     */
    @ParameterizedTest
    @ValueSource(ints = { 0, 100 })
    void testSeekableByteChannel(int size) throws Exception {
        var ch = new SeekableByteChannel() {
            long position;
            @Override
            public int read(ByteBuffer dst) throws IOException {
                throw new IOException();
            }
            @Override
            public int write(ByteBuffer src) throws IOException {
                throw new IOException();
            }
            @Override
            public long position() {
                return position;
            }
            @Override
            public SeekableByteChannel position(long newPosition) {
                if (newPosition < 0)
                    throw new IllegalArgumentException();
                position = newPosition;
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
        testSeekableByteChannel(ch);
    }

    /**
     * Test InputStream.available when input stream reads from a ReadableByteChannel.
     */
    @Test
    void testReadableByteChannel() throws Exception {
        var ch = new ReadableByteChannel() {
            @Override
            public int read(ByteBuffer dst) throws IOException {
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
        InputStream in = Channels.newInputStream(ch);
        assertEquals(0, in.available());
    }

    /**
     * Test InputStream.available when input stream reads from a socket.
     * @param in input stream that reads from a socket
     * @param peer output stream of connected peer
     */
    void testSocketInputStream(InputStream in, OutputStream peer) throws Exception {
        assertEquals(0, in.available());

        for (int i = 0; i < 5; i++) {
            final int count = 10;
            peer.write("X".repeat(count).getBytes("UTF-8"));

            // available should return count when all bytes available to read
            while (in.available() < count) {
                Thread.sleep(10);
            }
            assertEquals(count, in.available());

            // read one byte until all bytes are read
            int rem = count;
            while (rem > 0) {
                int aByte = in.read();
                assertEquals('X', aByte);
                rem--;
                assertEquals(rem, in.available());
            }
        }

        // available should return 0 when all bytes are read and peer has closed connection
        peer.close();
        assertEquals(0, in.available());
    }

    /**
     * Test InputStream.available for input stream that reads from a socket channel.
     */
    @Test
    void testSocketChannel() throws Exception {
        try (var connection = new Connection()) {
            InputStream in = Channels.newInputStream(connection.channel1());
            OutputStream out = Channels.newOutputStream(connection.channel2());
            testSocketInputStream(in, out);
        }
    }

    /**
     * Test InputStream.available for input stream that reads from a socket associated
     * a socket channel.
     */
    @Test
    void testSocketChannelAdaptor() throws Exception {
        try (var connection = new Connection()) {
            Socket s1 = connection.channel1().socket();
            Socket s2 = connection.channel2().socket();
            testSocketInputStream(s1.getInputStream(), s2.getOutputStream());
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
