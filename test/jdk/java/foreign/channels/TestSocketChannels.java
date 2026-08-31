/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @modules java.base/sun.nio.ch
 * @key randomness
 * @run junit/othervm TestSocketChannels
 */

import java.lang.foreign.Arena;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import java.lang.foreign.MemorySegment;


import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests consisting of buffer views with synchronous NIO network channels.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestSocketChannels extends AbstractChannelsTest {

    static final Class<IllegalStateException> ISE = IllegalStateException.class;
    static final Class<WrongThreadException> WTE = WrongThreadException.class;

    @ParameterizedTest
    @MethodSource("closeableArenas")
    public void testBasicIOWithClosedSegment(Supplier<Arena> arenaSupplier)
        throws Exception
    {
        try (var channel = SocketChannel.open();
             var server = ServerSocketChannel.open();
             var connectedChannel = connectChannels(server, channel)) {
            Arena drop = arenaSupplier.get();
            ByteBuffer bb = segmentBufferOfSize(drop, 16);
            drop.close();
            assertMessage(assertThrows(ISE, () -> channel.read(bb)),                           "Already closed");
            assertMessage(assertThrows(ISE, () -> channel.read(new ByteBuffer[] {bb})),        "Already closed");
            assertMessage(assertThrows(ISE, () -> channel.read(new ByteBuffer[] {bb}, 0, 1)),  "Already closed");
            assertMessage(assertThrows(ISE, () -> channel.write(bb)),                          "Already closed");
            assertMessage(assertThrows(ISE, () -> channel.write(new ByteBuffer[] {bb})),       "Already closed");
            assertMessage(assertThrows(ISE, () -> channel.write(new ByteBuffer[] {bb}, 0 ,1)), "Already closed");
        }
    }

    @ParameterizedTest
    @MethodSource("closeableArenas")
    public void testScatterGatherWithClosedSegment(Supplier<Arena> arenaSupplier)
        throws Exception
    {
        try (var channel = SocketChannel.open();
             var server = ServerSocketChannel.open();
             var connectedChannel = connectChannels(server, channel)) {
            Arena drop = arenaSupplier.get();
            ByteBuffer[] buffers = segmentBuffersOfSize(8, drop, 16);
            drop.close();
            assertMessage(assertThrows(ISE, () -> channel.write(buffers)),       "Already closed");
            assertMessage(assertThrows(ISE, () -> channel.read(buffers)),        "Already closed");
            assertMessage(assertThrows(ISE, () -> channel.write(buffers, 0 ,8)), "Already closed");
            assertMessage(assertThrows(ISE, () -> channel.read(buffers, 0, 8)),  "Already closed");
        }
    }

    @ParameterizedTest
    @MethodSource("closeableArenas")
    public void testBasicIO(Supplier<Arena> arenaSupplier)
        throws Exception
    {
        Arena drop;
        try (var sc1 = SocketChannel.open();
             var ssc = ServerSocketChannel.open();
             var sc2 = connectChannels(ssc, sc1);
             var scp = drop = arenaSupplier.get()) {
            Arena scope1 = drop;
            MemorySegment segment1 = scope1.allocate(10, 1);
            Arena scope = drop;
            MemorySegment segment2 = scope.allocate(10, 1);
            for (int i = 0; i < 10; i++) {
                segment1.set(JAVA_BYTE, i, (byte) i);
            }
            ByteBuffer bb1 = segment1.asByteBuffer();
            ByteBuffer bb2 = segment2.asByteBuffer();
            assertEquals(10, sc1.write(bb1));
            assertEquals(10, sc2.read(bb2));
            assertEquals(ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}), bb2.flip());
        }
    }

    @Test
    public void testBasicHeapIOWithGlobalSession() throws Exception {
        try (var sc1 = SocketChannel.open();
             var ssc = ServerSocketChannel.open();
             var sc2 = connectChannels(ssc, sc1)) {
            var segment1 = MemorySegment.ofArray(new byte[10]);
            var segment2 = MemorySegment.ofArray(new byte[10]);
            for (int i = 0; i < 10; i++) {
                segment1.set(JAVA_BYTE, i, (byte) i);
            }
            ByteBuffer bb1 = segment1.asByteBuffer();
            ByteBuffer bb2 = segment2.asByteBuffer();
            assertEquals(10, sc1.write(bb1));
            assertEquals(10, sc2.read(bb2));
            assertEquals(ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}), bb2.flip());
        }
    }

    @ParameterizedTest
    @MethodSource("confinedArenas")
    public void testIOOnConfinedFromAnotherThread(Supplier<Arena> arenaSupplier)
        throws Exception
    {
        try (var channel = SocketChannel.open();
             var server = ServerSocketChannel.open();
             var connected = connectChannels(server, channel);
             var drop = arenaSupplier.get()) {
            Arena scope = drop;
            var segment = scope.allocate(10, 1);
            ByteBuffer bb = segment.asByteBuffer();
            List<Executable> ioOps = List.of(
                    () -> channel.write(bb),
                    () -> channel.read(bb),
                    () -> channel.write(new ByteBuffer[] {bb}),
                    () -> channel.read(new ByteBuffer[] {bb}),
                    () -> channel.write(new ByteBuffer[] {bb}, 0, 1),
                    () -> channel.read(new ByteBuffer[] {bb}, 0, 1)
            );
            for (var ioOp : ioOps) {
                AtomicReference<Exception> exception = new AtomicReference<>();
                Runnable task = () -> exception.set(assertThrows(WTE, ioOp));
                var t = new Thread(task);
                t.start();
                t.join();
                assertMessage(exception.get(), "Attempted access outside owning thread");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("closeableArenas")
    public void testScatterGatherIO(Supplier<Arena> arenaSupplier)
        throws Exception
    {
        Arena drop;
        try (var sc1 = SocketChannel.open();
             var ssc = ServerSocketChannel.open();
             var sc2 = connectChannels(ssc, sc1);
             var scp = drop = arenaSupplier.get()) {
            var writeBuffers = mixedBuffersOfSize(32, drop, 64);
            var readBuffers = mixedBuffersOfSize(32, drop, 64);
            long expectedCount = remaining(writeBuffers);
            assertEquals(expectedCount, writeNBytes(sc1, writeBuffers, 0, 32, expectedCount));
            assertEquals(expectedCount, readNBytes(sc2, readBuffers, 0, 32, expectedCount));
            assertArrayEquals(clear(writeBuffers), flip(readBuffers));
        }
    }

    @ParameterizedTest
    @MethodSource("closeableArenas")
    public void testBasicIOWithDifferentSessions(Supplier<Arena> arenaSupplier)
         throws Exception
    {
        try (var sc1 = SocketChannel.open();
             var ssc = ServerSocketChannel.open();
             var sc2 = connectChannels(ssc, sc1);
             var drop1 = arenaSupplier.get();
             var drop2 = arenaSupplier.get()) {
            var writeBuffers = Stream.of(mixedBuffersOfSize(16, drop1, 64), mixedBuffersOfSize(16, drop2, 64))
                                     .flatMap(Arrays::stream)
                                     .toArray(ByteBuffer[]::new);
            var readBuffers = Stream.of(mixedBuffersOfSize(16, drop1, 64), mixedBuffersOfSize(16, drop2, 64))
                                    .flatMap(Arrays::stream)
                                    .toArray(ByteBuffer[]::new);

            long expectedCount = remaining(writeBuffers);
            assertEquals(expectedCount, writeNBytes(sc1, writeBuffers, 0, 32, expectedCount));
            assertEquals(expectedCount, readNBytes(sc2, readBuffers, 0, 32, expectedCount));
            assertArrayEquals(clear(writeBuffers), flip(readBuffers));
        }
    }

    static SocketChannel connectChannels(ServerSocketChannel ssc, SocketChannel sc)
        throws Exception
    {
        ssc.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        sc.connect(ssc.getLocalAddress());
        return ssc.accept();
    }

    static long writeNBytes(SocketChannel channel,
                            ByteBuffer[] buffers, int offset, int len,
                            long bytes)
        throws Exception
    {
        long total = 0L;
        do {
            long n = channel.write(buffers, offset, len);
            assertTrue(n > 0, "got:" + n);
            total += n;
        } while (total < bytes);
        return total;
    }

    static long readNBytes(SocketChannel channel,
                           ByteBuffer[] buffers, int offset, int len,
                           long bytes)
        throws Exception
    {
        long total = 0L;
        do {
            long n = channel.read(buffers, offset, len);
            assertTrue(n > 0, "got:" + n);
            total += n;
        } while (total < bytes);
        return total;
    }
}

